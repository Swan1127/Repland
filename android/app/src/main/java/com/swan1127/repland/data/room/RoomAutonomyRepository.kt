package com.swan1127.repland.data.room

import androidx.room.withTransaction
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.AutonomyRepository
import com.swan1127.repland.domain.ports.AutonomyState
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** Local commands and their scheduling consequences commit together with a persistent undo journal. */
class RoomAutonomyRepository(private val database: ReplandDatabase) : AutonomyRepository {
    private val workspace = database.planningWorkspaceDao()
    private val tasks = RoomTaskRepository(database)
    private val plans = RoomPlanRepository(database)

    override fun observe(): Flow<AutonomyState> = combine(
        workspace.observe(SETTINGS), workspace.observe(RECEIPT), workspace.observe(UNDO),
    ) { settings, receipt, undo ->
        AutonomyState(settings?.payload != "false", receipt?.payload, undo != null)
    }.distinctUntilChanged()

    override fun observeInputRevision(): Flow<String> {
        val taskAndTime = combine(database.taskDao().observeAll(), database.timeDao().observeWeeklyBlocks(),
            database.timeDao().observeDateOverrides(), database.timeDao().observeSemesterSettings(),
            database.categoryPreferenceDao().observeAll()) { taskRows, weekly, overrides, semester, preferences ->
            listOf(taskRows.sortedBy { it.id }, weekly, overrides, semester, preferences).toString()
        }
        val planAndExecution = combine(database.planDao().observeCurrentPlan(), workspace.observe("order"),
            workspace.observe(RoomExecutionSessionRepository.KEY), workspace.observe(WINDOWS),
            database.executionLogDao().observeAll()) { plan, order, session, windows, logs ->
            listOf(plan?.toDomain(), order?.payload, session?.payload, windows?.payload, logs).toString()
        }
        return combine(taskAndTime, planAndExecution, workspace.observe(HELD)) { first, second, held ->
            digest("${LocalDate.now()}\n$first\n$second\n${held?.payload}")
        }
            .distinctUntilChanged()
    }

    override suspend fun setEnabled(enabled: Boolean) = database.withTransaction {
        workspace.put(PlanningWorkspaceEntity(SETTINGS, enabled.toString()))
    }

    override suspend fun capture(candidates: List<ArrangementCandidate>, date: LocalDate, dateExplicit: Boolean,
        requestId: String, expectedRevision: String?, originalText: String?): String = database.withTransaction {
        duplicate(requestId)?.let { return@withTransaction it }
        require(candidates.isNotEmpty() && candidates.size <= 8) { "一次请输入 1–8 件事项。" }
        require(candidates.all { it.existingTaskId == null && it.title.isNotBlank() &&
            (it.durationMinutes == null || it.durationMinutes in 1..720) }) { "新建事项内容无效，请重新输入。" }
        val input = input()
        val current = current()
        require(expectedRevision == null || expectedRevision == PlanningRevision.of(input, current, order())) {
            "任务或时间设置已变化，请重新发送；本次没有写入。"
        }
        val before = snapshot()
        val localCandidates = originalText?.let { ArrangementAssistantInterpreter.interpret(it).candidates }.orEmpty()
        val effectiveCandidates = AutonomousCaptureFacts.reconcile(localCandidates, candidates)
        val captureOnly = AutonomousCaptureFacts.recordOnly(originalText)
        val newIds = mutableListOf<String>()
        val newWindows = windows().toMutableList()
        var estimateCount = 0
        effectiveCandidates.forEach { candidate ->
            val id = UUID.randomUUID().toString()
            val local = localCandidates.singleOrNull { normalized(cleanTitle(it.title)) == normalized(cleanTitle(candidate.title)) }
            val explicitDuration = local?.durationMinutes != null && local.durationMinutes == candidate.durationMinutes
            val duration = candidate.durationMinutes ?: 30
            if (!explicitDuration) estimateCount++
            tasks.save(TaskDraft(id = id, displayName = cleanTitle(candidate.title), description = originalText?.trim()?.takeIf { it.isNotBlank() } ?: candidate.title.trim(),
                category = candidate.category, totalDurationMinutes = duration,
                scheduledForDate = date.takeUnless { captureOnly && !dateExplicit },
                inputSources = TaskInputSources(
                    category = if (candidate.category == TaskCategory.UNSPECIFIED) TaskInputSource.UNKNOWN else TaskInputSource.PLANNING_ESTIMATE,
                    priority = TaskInputSource.UNKNOWN, days = TaskInputSource.UNKNOWN,
                    duration = if (explicitDuration) TaskInputSource.USER_INPUT else TaskInputSource.PLANNING_ESTIMATE,
                    dueDate = TaskInputSource.UNKNOWN,
                    scheduledDate = if (dateExplicit) TaskInputSource.USER_INPUT else if (captureOnly) TaskInputSource.UNKNOWN else TaskInputSource.PLANNING_ESTIMATE,
                )))
            newIds += id
            timeWindow(id, date, local, originalText, dateExplicit)?.let(newWindows::add)
        }
        putWindows(newWindows)
        if (captureOnly) workspace.put(PlanningWorkspaceEntity(HELD, JSONArray((heldTasks() + newIds).sorted()).toString()))
        val repair = if (enabled()) repair() else RepairResult(false, "自动安排已暂停，事项已保存到任务库。")
        val placedIds = current()?.segments.orEmpty().filter { it.taskId in newIds }.map { it.taskId }.distinct()
        val specifiedDayPending = if (dateExplicit && !captureOnly) newIds.count { id ->
            current()?.segments.orEmpty().none { it.taskId == id && it.date == date }
        } else 0
        val missedWindows = newWindows.filter { it.taskId in newIds && !captureOnly }.count { window ->
            val segments = current()?.segments.orEmpty().filter { it.taskId == window.taskId }.sortedBy { it.startMinute }
            segments.isEmpty() || segments.any { it.date != window.date || it.startMinute < window.startMinute || it.endMinute > window.endMinute } ||
                (window.exactStart && segments.first().startMinute != window.startMinute)
        }
        val summary = buildString {
            append("已保存 ${newIds.size} 件事项")
            if (placedIds.isNotEmpty()) append("，已安排 ${placedIds.size} 件")
            if (estimateCount > 0) append("；${estimateCount} 件时长为规划估算，可随时修改")
            if (specifiedDayPending > 0) append("；${specifiedDayPending} 件未能排入指定日期")
            if (missedWindows > 0) append("；${missedWindows} 件未能按指定时刻或时段安排，已保留待安排")
            append("。")
            if (captureOnly) append("已按要求暂不安排，可在需要时直接让我安排。")
            if (repair.note != null) append(repair.note)
        }
        finish(requestId, before, summary)
    }

    override suspend fun updateStatus(commands: List<AutonomousStatusCommand>, requestId: String,
        contextTaskId: String?): String = database.withTransaction {
        duplicate(requestId)?.let { return@withTransaction it }
        require(commands.isNotEmpty() && commands.size <= 8) { "一次请输入 1–8 项状态更新。" }
        val allTasks = database.taskDao().getAll().map { it.toDomain() }
        val resolved = commands.map { command ->
            val resolution = AutonomousTaskIntentParser.resolveTarget(command.target, allTasks, contextTaskId)
            val found = when (resolution) {
                is AutonomousTargetResolution.Found -> resolution.task
                is AutonomousTargetResolution.Ambiguous -> error("存在多个同名任务，请在任务详情中明确选择后更新。")
                is AutonomousTargetResolution.Missing -> error("没有找到对应任务，请使用任务列表中的完整名称。")
                AutonomousTargetResolution.ContextRequired -> error("还不能确定“这个任务”是哪一项，请说出完整任务名称。")
            }
            require(found.status.isActive) { "“${found.displayName}”已经结束，本次没有更新。" }
            val progressOnly = command.change is AutonomousTaskChange.Progress && command.change.percent < 100
            require(found.id != runningTaskId() || progressOnly) { "“${found.displayName}”正在专注中，请先结束本轮专注，再完成或取消任务，以保存本轮计时。" }
            command to found
        }
        require(resolved.map { it.second.id }.distinct().size == resolved.size) { "同一任务出现了多次更新，请合并成一个明确指令。" }
        val before = snapshot()
        resolved.forEach { (command, task) ->
            when (val change = command.change) {
                AutonomousTaskChange.Complete -> tasks.confirmStatus(task.id, TaskStatus.COMPLETED,
                    TaskFeedback(completedContent = command.sourceText))
                AutonomousTaskChange.Cancel -> tasks.confirmStatus(task.id, TaskStatus.CANCELLED,
                    TaskFeedback(completedContent = command.sourceText))
                is AutonomousTaskChange.Progress -> when (change.percent) {
                    100 -> tasks.confirmStatus(task.id, TaskStatus.COMPLETED,
                        TaskFeedback(progressPercent = 100, completedContent = command.sourceText))
                    0 -> tasks.recordFeedback(task.id, TaskFeedback(progressPercent = 0, completedContent = command.sourceText))
                    else -> tasks.recordPartialCompletion(task.id,
                        TaskFeedback(progressPercent = change.percent, completedContent = command.sourceText))
                }
            }
        }
        val repair = repair(scheduleNew = enabled())
        val summary = "已更新 ${resolved.size} 件任务，保留原有执行记录。" +
            if (repair.note != null) repair.note else if (repair.changed) "后续安排已同步调整。" else ""
        finish(requestId, before, summary)
    }

    override suspend fun arrange(date: LocalDate, requestId: String, startMinute: Int, endMinute: Int): String = database.withTransaction {
        duplicate(requestId)?.let { return@withTransaction it }
        require(date >= LocalDate.now()) { "不能自动重排过去的日期。" }
        val before = snapshot()
        require(startMinute in 0 until 1440 && endMinute in 1..1440 && startMinute < endMinute) { "安排时段无效。" }
        val requestedWindows = windows().groupBy { it.taskId }
        val eligibleIds = database.taskDao().getAll().map { it.toDomain() }.filter { task ->
            task.status.isActive && task.scheduledForDate?.isAfter(date) != true &&
                task.dueDate?.isBefore(date) != true &&
                requestedWindows[task.id].orEmpty().let { taskWindows ->
                    taskWindows.isEmpty() || taskWindows.any { window ->
                        window.date == date && window.startMinute < endMinute && window.endMinute > startMinute
                    }
                }
        }.mapTo(mutableSetOf()) { it.id }
        val remainingHeld = heldTasks() - eligibleIds
        if (remainingHeld.isEmpty()) workspace.remove(HELD)
        else workspace.put(PlanningWorkspaceEntity(HELD, JSONArray(remainingHeld.sorted()).toString()))
        val repair = repair(targetDate = date, targetStartMinute = startMinute, targetEndMinute = endMinute)
        val summary = if (repair.changed) "已在现有时间约束内调整后续安排。" + repair.note.orEmpty()
            else repair.note ?: "当前安排已经合适，没有需要移动的时段。"
        finish(requestId, before, summary)
    }

    override suspend fun reconcile(): String? = database.withTransaction {
        if (!enabled()) return@withTransaction null
        val revision = repairRevision()
        if (workspace.get(LAST_REVISION)?.payload == revision) return@withTransaction null
        val before = snapshot()
        val repair = repair()
        if (!repair.changed) {
            workspace.put(PlanningWorkspaceEntity(LAST_REVISION, repairRevision()))
            repair.note?.let { workspace.put(PlanningWorkspaceEntity(RECEIPT, it)) }
            return@withTransaction repair.note
        }
        finish("automatic:${UUID.randomUUID()}", before, "已根据任务和时间变化调整后续安排。" + repair.note.orEmpty())
    }

    override suspend fun undo(): String = database.withTransaction {
        val journal = workspace.get(UNDO)?.let { JSONObject(it.payload) }
            ?: error("没有可撤销的自动操作。")
        require(journal.getString("afterRevision") == guardRevision()) {
            "这次操作之后已有新的修改，整次撤销已停止，以保留你的后续修改。"
        }
        val beforeTasks = journal.getJSONArray("tasks").objects().map(::decodeTask)
        val beforeById = beforeTasks.associateBy { it.id }
        val afterIds = journal.getJSONArray("afterTaskIds").strings().toSet()
        val createdIds = afterIds - beforeById.keys
        createdIds.forEach { id ->
            require(database.executionLogDao().getForTask(id).isEmpty()) { "新任务已有执行记录，本次没有撤销。" }
        }
        val logIds = journal.getJSONArray("newLogIds").strings()
        createdIds.forEach { database.taskDao().deleteById(it) }
        beforeTasks.forEach { original ->
            if (database.taskDao().getById(original.id) != original) database.taskDao().insert(original)
        }
        // Corrections append to evidence; the original status/progress reports remain immutable.
        logIds.forEach { id ->
            val log = requireNotNull(database.executionLogDao().getById(id))
            val original = requireNotNull(beforeById[log.taskId])
            val latest = database.executionLogDao().latestCreatedAtForTask(log.taskId) ?: 0L
            database.executionLogDao().insert(ExecutionLogEntity(
                UUID.randomUUID().toString(), log.taskId, ExecutionLogEventType.CORRECTION.name, original.status,
                null, original.progressPercent, null, "用户撤销了本次状态更新，恢复操作前的任务状态。", null,
                id, null, maxOf(System.currentTimeMillis(), latest + 1),
            ))
        }
        restoreWorkspace(WINDOWS, journal.nullableString("windows"))
        restoreWorkspace(HELD, journal.nullableString("held"))
        val beforePlanId = journal.nullableString("planId")
        val actualPlan = current()
        val actualPlanId = actualPlan?.id
        if (beforePlanId != actualPlanId) {
            if (beforePlanId == null) {
                val now = LocalDateTime.now()
                require(current()?.segments.orEmpty().none { it.date < now.toLocalDate() ||
                    (it.date == now.toLocalDate() && it.startMinute <= now.hour * 60 + now.minute) }) {
                    "已有安排开始或结束，不能撤销其历史；任务和计划均已保留。"
                }
                database.planDao().archiveCurrentPlan()
            } else {
                val previous = requireNotNull(database.planDao().getPlanWithSegments(beforePlanId)).toDomain()
                try {
                    // Only placements that are still current may bypass validation. A placement
                    // reintroduced by undo must respect the latest time constraints, too.
                    persistPlan(PlanDraft(LocalDateTime.now(), previous.segments, emptyList(), emptyList(),
                        previous.orderedTaskIds, hasManualTaskOrder = previous.hasManualTaskOrder), actualPlan?.segments.orEmpty())
                } catch (invalid: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "原安排无法恢复，本次未撤销；任务、当前安排和时间设置均已保留。${invalid.message.orEmpty()}可在日程或时间设置中处理冲突。",
                        invalid,
                    )
                }
            }
        }
        restoreWorkspace("order", journal.nullableString("order"))
        restoreWorkspace("order-undo", journal.nullableString("orderUndo"))
        restoreWorkspace("draft", journal.nullableString("draft"))
        workspace.remove(UNDO)
        val summary = "已撤销整次操作，任务和安排已恢复；原始执行记录仍然保留。"
        journal.nullableString("requestId")?.let { workspace.put(PlanningWorkspaceEntity("autonomy-request:$it", "该次操作已经撤销，没有重复执行。")) }
        workspace.put(PlanningWorkspaceEntity(RECEIPT, summary))
        workspace.put(PlanningWorkspaceEntity(LAST_REVISION, repairRevision()))
        summary
    }

    private data class RepairResult(val changed: Boolean, val note: String? = null)

    private suspend fun repair(scheduleNew: Boolean = true, targetDate: LocalDate? = null,
        targetStartMinute: Int = 0, targetEndMinute: Int = 1440): RepairResult {
        val input = input()
        val current = current()
        val now = LocalDateTime.now()
        val draft = AutonomousPlanRepair.repair(input, current, now, runningTaskId(), windows(), scheduleNew, targetDate,
            targetStartMinute, targetEndMinute, excludedFromNewTaskIds = heldTasks())
        val changed = signature(draft.segments) != signature(current?.segments.orEmpty()) ||
            draft.orderedTaskIds != current?.orderedTaskIds.orEmpty()
        if (changed) {
            // This local entry point grandfathers only identical confirmed placements. New/changed work is fully checked.
            try {
                validatePlan(draft, current?.segments.orEmpty())
            } catch (invalid: IllegalArgumentException) {
                return RepairResult(false, "任务已保留；当前计划包含需要处理的固定冲突，未覆盖原计划。${invalid.message.orEmpty()}")
            }
            persistPlan(draft, current?.segments.orEmpty())
        }
        val conflicts = AutonomousPlanRepair.conflicts(input, draft, now, windows()).map { it.taskId }.distinct().size
        val pending = draft.pendingTaskIds.distinct().size
        val note = buildString {
            if (!PlanningConstraintValidator.hasExplicitAvailability(input)) append("尚未设置可用时间，事项已保留待安排；可用时间设置后会自动安排。")
            else if (pending > 0) append("${pending} 件任务尚未排完，已保留待安排，不会挤占课程或休息。")
            if (conflicts > 0) append("${conflicts} 件受保护安排存在时间冲突，请在日程中处理。")
        }.ifBlank { null }
        return RepairResult(changed, note)
    }

    private suspend fun snapshot(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("tasks", JSONArray(database.taskDao().getAll().map(::encodeTask)))
        put("planId", current()?.id ?: JSONObject.NULL)
        put("order", workspace.get("order")?.payload ?: JSONObject.NULL)
        put("orderUndo", workspace.get("order-undo")?.payload ?: JSONObject.NULL)
        put("draft", workspace.get("draft")?.payload ?: JSONObject.NULL)
        put("windows", workspace.get(WINDOWS)?.payload ?: JSONObject.NULL)
        put("held", workspace.get(HELD)?.payload ?: JSONObject.NULL)
        put("beforeLogIds", JSONArray(database.executionLogDao().getAll().map { it.id }))
    }

    private suspend fun finish(requestId: String, before: JSONObject, summary: String): String {
        val beforeIds = before.getJSONArray("beforeLogIds").strings().toSet()
        val newLogIds = database.executionLogDao().getAll().map { it.id }.filterNot { it in beforeIds }
        val changed = before.getJSONArray("tasks").toString() != JSONArray(database.taskDao().getAll().map(::encodeTask)).toString() ||
            before.nullableString("planId") != current()?.id || before.nullableString("order") != workspace.get("order")?.payload ||
            before.nullableString("windows") != workspace.get(WINDOWS)?.payload || before.nullableString("held") != workspace.get(HELD)?.payload || newLogIds.isNotEmpty()
        before.put("newLogIds", JSONArray(newLogIds))
        before.put("afterTaskIds", JSONArray(database.taskDao().getAll().map { it.id }))
        before.put("requestId", requestId)
        before.put("afterRevision", guardRevision())
        if (changed) workspace.put(PlanningWorkspaceEntity(UNDO, before.toString()))
        workspace.put(PlanningWorkspaceEntity(RECEIPT, summary))
        workspace.put(PlanningWorkspaceEntity("autonomy-request:$requestId", summary))
        workspace.put(PlanningWorkspaceEntity(LAST_REVISION, repairRevision()))
        return summary
    }

    private suspend fun duplicate(requestId: String): String? {
        require(requestId.isNotBlank() && requestId.length <= 200) { "操作标识无效。" }
        return workspace.get("autonomy-request:$requestId")?.payload
    }

    private suspend fun input(): PlanGenerationInput = PlanGenerationInput(
        database.taskDao().getAll().map { it.toDomain() },
        database.timeDao().getAllWeeklyBlocks().map { it.toDomain() },
        database.timeDao().getAllDateOverrides().map { it.toDomain() },
        database.timeDao().getSemesterSettings()?.firstWeekMondayEpochDay?.let(LocalDate::ofEpochDay),
        categoryPreferences = CategoryPreferences.normalized(database.categoryPreferenceDao().getAll().associate { it.toDomainPair() }),
        manualTaskOrder = order(),
    )
    private suspend fun current() = database.planDao().getCurrentPlanWithSegments()?.toDomain()
    private suspend fun order(): List<String> = workspace.get("order")?.let { JSONArray(it.payload).strings() }.orEmpty()
    private suspend fun enabled() = workspace.get(SETTINGS)?.payload != "false"
    private suspend fun runningTaskId(): String? = workspace.get(RoomExecutionSessionRepository.KEY)?.let { ExecutionSessionCodec.decode(it.payload).taskId }

    private suspend fun guardRevision(): String {
        return digest(listOf(PlanningRevision.of(input(), current(), order()),
            database.timeDao().getSemesterSettings(), database.executionLogDao().getAll(),
            workspace.get(RoomExecutionSessionRepository.KEY)?.payload, workspace.get(WINDOWS)?.payload, workspace.get(HELD)?.payload,
            workspace.get("draft")?.payload, workspace.get("assistant")?.payload, workspace.get("tracks")?.payload,
        ).joinToString("\n"))
    }

    private suspend fun repairRevision(): String {
        val plan = current()
        val now = LocalDateTime.now()
        val minute = now.hour * 60 + now.minute
        val timeBoundaries = plan?.segments.orEmpty().map { segment ->
            val phase = when {
                segment.date < now.toLocalDate() || (segment.date == now.toLocalDate() && segment.endMinute <= minute) -> "past"
                segment.date == now.toLocalDate() && segment.startMinute <= minute -> "started"
                else -> "future"
            }
            "${segment.id}:$phase"
        }
        return digest(listOf(guardRevision(), now.toLocalDate(), timeBoundaries).joinToString("\n"))
    }

    private suspend fun validatePlan(draft: PlanDraft, grandfathered: List<PlannedSegment>) {
        val input = input()
        val active = input.tasks.filter { it.status.isActive }.associateBy { it.id }
        val required = input.tasks.filter { it.userPriority == TaskPriority.REQUIRED }.map { it.id }.toSet()
        val running = runningTaskId()
        val requestedWindows = windows()
        val now = LocalDateTime.now()
        val minute = now.hour * 60 + now.minute
        fun same(first: PlannedSegment, second: PlannedSegment) = first.taskId == second.taskId && first.date == second.date &&
            first.startMinute == second.startMinute && first.endMinute == second.endMinute &&
            first.trackId == second.trackId && first.isLocked == second.isLocked
        current()?.segments.orEmpty().forEach { old ->
            val begun = old.date < now.toLocalDate() || (old.date == now.toLocalDate() && old.startMinute <= minute)
            val protected = begun || (old.taskId in active && (old.isLocked || old.taskId in required || old.taskId == running))
            require(!protected || draft.segments.any { same(old, it) }) { "已开始、锁定、不可避免或正在专注的安排必须保留。" }
        }
        draft.segments.forEach { segment ->
            if (grandfathered.any { same(it, segment) }) return@forEach
            require(segment.taskId in active) { "不能为已经结束或不存在的任务新增安排。" }
            val task = active.getValue(segment.taskId)
            require(task.scheduledForDate?.isAfter(segment.date) != true && task.dueDate?.isBefore(segment.date) != true) {
                "新增安排不能越过用户指定的日期或截止日期。"
            }
            val taskWindows = requestedWindows.filter { it.taskId == segment.taskId }
            require(taskWindows.isEmpty() || taskWindows.any { it.date == segment.date &&
                segment.startMinute >= it.startMinute && segment.endMinute <= it.endMinute }) { "新增安排不能越过用户指定的时段。" }
            require(segment.date > now.toLocalDate() || (segment.date == now.toLocalDate() && segment.startMinute >= minute)) { "不能新增过去的安排。" }
            PlacementValidator.requireValid(segment, draft.segments, input.weeklyBlocks, input.dateOverrides,
                input.semesterFirstWeekMonday, required)
            require((segment.startMinute until segment.endMinute).all { minuteInSegment ->
                PlanningConstraintValidator.isAvailable(segment.date, minuteInSegment, minuteInSegment + 1,
                    input.weeklyBlocks, input.dateOverrides, input.semesterFirstWeekMonday)
            }) { "新增安排必须位于已设置的可用时间内。" }
        }
    }

    private suspend fun persistPlan(draft: PlanDraft, grandfathered: List<PlannedSegment>) {
        validatePlan(draft, grandfathered)
        val id = UUID.randomUUID().toString()
        val createdAt = maxOf(System.currentTimeMillis(), (database.planDao().latestCreatedAt() ?: 0L) + 1)
        database.planDao().replaceCurrentPlan(PlanEntity(id, createdAt, true), draft.segments.map { segment ->
            PlanSegmentEntity(UUID.randomUUID().toString(), id, segment.taskId, segment.date.toEpochDay(),
                segment.startMinute, segment.endMinute, segment.isLocked, segment.trackId)
        }, draft.orderedTaskIds.distinct().mapIndexed { index, taskId -> PlanTaskOrderEntity(id, taskId, index, draft.hasManualTaskOrder) })
        plans.saveTaskOrder(draft.orderedTaskIds)
    }

    private suspend fun restoreWorkspace(key: String, value: String?) {
        if (value == null) workspace.remove(key) else workspace.put(PlanningWorkspaceEntity(key, value))
    }
    private suspend fun windows(): List<AutonomousTimeWindow> = workspace.get(WINDOWS)?.let { row ->
        JSONArray(row.payload).objects().map { j -> AutonomousTimeWindow(j.getString("task"), LocalDate.parse(j.getString("date")),
            j.getInt("start"), j.getInt("end"), j.optBoolean("exact")) }
    }.orEmpty()
    private suspend fun heldTasks(): Set<String> = workspace.get(HELD)?.let { JSONArray(it.payload).strings().toSet() }.orEmpty()
    private suspend fun putWindows(values: List<AutonomousTimeWindow>) = workspace.put(PlanningWorkspaceEntity(WINDOWS,
        JSONArray(values.map { window -> JSONObject().apply {
            put("task", window.taskId); put("date", window.date.toString()); put("start", window.startMinute)
            put("end", window.endMinute); put("exact", window.exactStart)
        } }).toString()))

    private fun timeWindow(id: String, date: LocalDate, local: ArrangementCandidate?, text: String?, dateExplicit: Boolean): AutonomousTimeWindow? {
        val hint = local?.timeHint
        val explicit = hint?.explicitStartMinute
        if (explicit != null) {
            val rangeEnd = hint.windowLabel?.let { label ->
                Regex("(?:到|至|-)\\s*(\\d{1,2})\\s*(?:点|:|：)\\s*(?:(\\d{1,2})\\s*分?)?").find(label)?.let { match ->
                    var hour = match.groupValues[1].toInt()
                    if (explicit >= 12 * 60 && hour in 1..11) hour += 12
                    hour * 60 + (match.groupValues[2].toIntOrNull() ?: 0)
                }
            }
            val end = rangeEnd ?: 1440
            require(end in (explicit + 1)..1440) { "请检查明确时段的起止时间。" }
            return AutonomousTimeWindow(id, date, explicit, end, true)
        }
        val label = hint?.windowLabel ?: text.orEmpty()
        val range = when {
            label.contains("今晚") || label.contains("晚上") -> 18 * 60 to 1440
            label.contains("下午") -> 12 * 60 to 18 * 60
            label.contains("上午") || label.contains("早上") -> 8 * 60 to 12 * 60
            label.contains("中午") -> 11 * 60 to 14 * 60
            dateExplicit -> 0 to 1440
            else -> return null
        }
        return AutonomousTimeWindow(id, date, range.first, range.second)
    }

    companion object {
        const val SETTINGS = "autonomy-settings"
        const val UNDO = "autonomy-undo"
        const val RECEIPT = "autonomy-receipt"
        const val LAST_REVISION = "autonomy-last-revision"
        const val WINDOWS = "autonomy-time-windows"
        const val HELD = "autonomy-held-tasks"
        private fun signature(segments: List<PlannedSegment>) = segments.map {
            listOf(it.taskId, it.date, it.startMinute, it.endMinute, it.isLocked, it.trackId).joinToString("|")
        }.sorted()
        private fun normalized(text: String) = text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        private fun cleanTitle(text: String): String {
            var title = text.trim().replace(Regex("^(?:只记着|只记下|只记录|仅记录|只新增|不要安排|暂不安排|先不安排)[：:]?\\s*"), "")
            val prefix = Regex("^(?:(?:请)?(?:帮我)?(?:新增|新建|添加|记下|记录)(?:一个|一项)?(?:任务|事项)?[:：]?|今天|明天|后天|今晚|下午|上午|早上|晚上|中午|(?:下周|本周|这周|周|星期)[一二三四五六日天]|(?:\\d{4}年)?\\d{1,2}月\\d{1,2}[日号]?|\\d{4}-\\d{1,2}-\\d{1,2})\\s*")
            repeat(5) { title = title.replaceFirst(prefix, "").trim() }
            return title.ifBlank { text.trim() }
        }
        private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        private fun JSONArray.strings() = (0 until length()).map(::getString)
        private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
        private fun JSONObject.nullableString(key: String) = if (isNull(key)) null else getString(key)
        private fun JSONObject.nullableInt(key: String) = if (isNull(key)) null else getInt(key)
        private fun JSONObject.nullableLong(key: String) = if (isNull(key)) null else getLong(key)
        private fun encodeTask(t: TaskEntity) = JSONObject().apply {
            put("id", t.id); put("description", t.description); put("displayName", t.displayName); put("category", t.category)
            put("priority", t.userPriority); put("days", t.estimatedDays ?: JSONObject.NULL)
            put("duration", t.totalDurationMinutes ?: JSONObject.NULL); put("due", t.dueDateEpochDay ?: JSONObject.NULL)
            put("status", t.status); put("summary", t.completionSummary ?: JSONObject.NULL)
            put("actual", t.actualDurationMinutes ?: JSONObject.NULL); put("progress", t.progressPercent ?: JSONObject.NULL)
            put("postpone", t.postponeCount); put("created", t.createdAtEpochMillis); put("updated", t.updatedAtEpochMillis)
            put("result", t.completionResult ?: JSONObject.NULL); put("scheduled", t.scheduledForEpochDay ?: JSONObject.NULL)
            put("sources", t.inputSources)
        }
        private fun decodeTask(j: JSONObject) = TaskEntity(j.getString("id"), j.getString("description"),
            j.getString("displayName"), j.getString("category"), j.getString("priority"), j.nullableInt("days"),
            j.nullableInt("duration"), j.nullableLong("due"), j.getString("status"), j.nullableString("summary"),
            j.nullableInt("actual"), j.nullableInt("progress"), j.getInt("postpone"), j.getLong("created"),
            j.getLong("updated"), j.nullableString("result"), j.nullableLong("scheduled"), j.getString("sources"))
    }
}
