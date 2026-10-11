package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.ports.PlanRepository
import java.util.UUID
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import com.swan1127.repland.domain.model.PlacementValidator
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.*
import org.json.JSONArray

class RoomPlanRepository(
    private val database: ReplandDatabase,
) : PlanRepository {
    private val planDao = database.planDao()
    private val workspace = database.planningWorkspaceDao()

    override fun observeTracks(): Flow<List<RhythmTrack>> = workspace.observe("tracks").map {
        it?.let { row -> InteractionWorkspaceCodec.decodeTracks(row.payload) } ?: RhythmTracks.defaults
    }
    override suspend fun saveTracks(tracks: List<RhythmTrack>) {
        require(tracks.isNotEmpty() && tracks.all { it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 24 }) { "轨道名称需为 1–24 个字。" }
        require(tracks.map { it.id }.distinct().size == tracks.size && tracks.map { it.name }.distinct().size == tracks.size) { "轨道 ID 或名称重复。" }
        workspace.put(PlanningWorkspaceEntity("tracks", InteractionWorkspaceCodec.encodeTracks(tracks)))
    }
    override fun observeAssistantWorkspace(): Flow<AssistantWorkspace?> = workspace.observe("assistant").map {
        it?.let { row -> InteractionWorkspaceCodec.decodeAssistant(row.payload) }
    }
    override suspend fun saveAssistantWorkspace(value: AssistantWorkspace?) {
        if (value == null) workspace.remove("assistant")
        else workspace.put(PlanningWorkspaceEntity("assistant", InteractionWorkspaceCodec.encodeAssistant(value)))
    }

    override fun observeDraft(): Flow<PlanDraft?> = workspace.observe("draft").map { row ->
        row?.let { PlanDraftCodec.decode(it.payload) }
    }
    override suspend fun saveDraft(draft: PlanDraft?) {
        if (draft == null) workspace.remove("draft")
        else workspace.put(PlanningWorkspaceEntity("draft", PlanDraftCodec.encode(draft)))
    }
    override suspend fun replaceDraftIfCurrent(expected: PlanDraft, updated: PlanDraft) = database.withTransaction {
        require(updated.generatedAt == expected.generatedAt && updated.sourceRevision == expected.sourceRevision) {
            "编辑不能替换另一份预览，请重新查看当前草案。"
        }
        val actual = workspace.get("draft")?.let { PlanDraftCodec.decode(it.payload) }
        require(actual == expected) { "草案已经变化，本次未保存；请重新查看当前预览后重试。" }
        workspace.put(PlanningWorkspaceEntity("draft", PlanDraftCodec.encode(updated)))
    }
    override fun observeTaskOrder(): Flow<List<String>> = workspace.observe("order").map { row ->
        row?.let { JSONArray(it.payload).let { a -> (0 until a.length()).map(a::getString) } }.orEmpty()
    }
    override suspend fun saveTaskOrder(ids: List<String>) = database.withTransaction {
        val previous = workspace.get("order")?.payload ?: "[]"
        val updated = JSONArray(ids.distinct()).toString()
        if (previous != updated) {
            workspace.put(PlanningWorkspaceEntity("order-undo", previous))
            workspace.put(PlanningWorkspaceEntity("order", updated))
        }
    }
    override fun observeCanUndoTaskOrder(): Flow<Boolean> = workspace.observe("order-undo").map { it != null }
    override suspend fun undoTaskOrder() = database.withTransaction {
        val previous = requireNotNull(workspace.get("order-undo")) { "没有可撤销的排序。" }
        workspace.put(PlanningWorkspaceEntity("order", previous.payload))
        workspace.remove("order-undo")
    }

    private suspend fun revision(): String = PlanningRevision.of(
        PlanGenerationInput(database.taskDao().getAll().map { it.toDomain() },
            database.timeDao().getAllWeeklyBlocks().map { it.toDomain() },
            database.timeDao().getAllDateOverrides().map { it.toDomain() },
            database.timeDao().getSemesterSettings()?.firstWeekMondayEpochDay?.let(LocalDate::ofEpochDay),
            categoryPreferences = CategoryPreferences.normalized(database.categoryPreferenceDao().getAll().associate { it.toDomainPair() })),
        planDao.getCurrentPlanWithSegments()?.toDomain(),
        workspace.get("order")?.let { JSONArray(it.payload).let { a -> (0 until a.length()).map(a::getString) } }.orEmpty())

    override suspend fun saveTasksAndPlace(tasks: List<TaskDraft>, segments: List<PlannedSegment>) {
        saveAssistantChanges(tasks, segments, emptySet(), segments.firstOrNull()?.date ?: LocalDate.now())
    }

    override suspend fun saveAssistantChanges(tasks: List<TaskDraft>, segments: List<PlannedSegment>, existingTaskIds: Set<String>, date: LocalDate) = database.withTransaction {
        val assistant = workspace.get("assistant")?.let { InteractionWorkspaceCodec.decodeAssistant(it.payload) }
        val sourceRevision = assistant?.sourceRevision
        require(assistant?.followUpInstruction.isNullOrBlank()) { "补充修改尚未应用，请发送或放弃后确认。" }
        require(existingTaskIds.isEmpty() || sourceRevision != null) { "已有任务调整需要有效草案，请重新生成后确认。" }
        require(existingTaskIds.isEmpty() || (assistant?.date == date && assistant.proposals.mapNotNull { it.existingTaskId }.toSet() == existingTaskIds)) { "调整目标与草案不一致，请重新生成。" }
        require(sourceRevision == null || sourceRevision == revision()) { "任务、设置或计划已变化，请重新生成助手草案后确认。" }
        require((tasks.isNotEmpty() || existingTaskIds.isNotEmpty()) && tasks.all { TaskDraftValidator.isValid(it) && !it.id.isNullOrBlank() }) { "请检查事项名称和时长。" }
        require(tasks.map { it.id }.distinct().size == tasks.size) { "事项重复，请重新生成。" }
        val newIds = tasks.map { requireNotNull(it.id) }.toSet()
        require(newIds.intersect(existingTaskIds).isEmpty()) { "新建与已有事项引用重复。" }
        val ids = newIds + existingTaskIds
        require(existingTaskIds.all { id -> database.taskDao().getById(id)?.toDomain()?.status?.isActive == true && segments.any { it.taskId == id && it.date == date } }) { "已有任务不存在、已结束或缺少明确时段，请重新生成。" }
        require(segments.filter { it.taskId in existingTaskIds }.all { it.date == date }) { "已有任务调整只能操作选定日期。" }
        require(segments.all { it.taskId in ids }) { "安排中的事项引用无效。" }
        require(newIds.none { database.taskDao().getById(it) != null }) { "这些事项已经保存，请查看任务列表。" }
        val current = planDao.getCurrentPlanWithSegments()?.toDomain()
        val replaced = current?.segments.orEmpty().filter { it.taskId in existingTaskIds && it.date == date }
        val now = java.time.LocalDateTime.now()
        require(segments.filter { it.taskId in existingTaskIds }.all { it.date > now.toLocalDate() || (it.date == now.toLocalDate() && it.startMinute > now.hour * 60 + now.minute) }) { "助手不能把已有任务调整到过去的时段。" }
        require(existingTaskIds.all { id -> segments.count { it.taskId == id } == 1 }) { "每个已有任务本次只能指定一个时段。" }
        require(replaced.none { it.isLocked || it.date < now.toLocalDate() || (it.date == now.toLocalDate() && it.startMinute <= now.hour * 60 + now.minute) }) { "锁定或已开始的安排不能由助手替换，请先在日程中处理。" }
        val preserved = current?.segments.orEmpty() - replaced.toSet()
        for (segment in segments) validatePlacement(segment, preserved + segments)
        val taskRepository = RoomTaskRepository(database)
        tasks.forEach { taskRepository.save(it) }
        if (segments.isNotEmpty()) {
            val independentOrder = workspace.get("order")?.let { row -> JSONArray(row.payload).let { array -> (0 until array.length()).map(array::getString) } }.orEmpty()
            accept(PlanDraft(java.time.LocalDateTime.now(), preserved + segments, emptyList(), emptyList(),
                orderedTaskIds = (independentOrder + current?.orderedTaskIds.orEmpty() + ids).distinct(), hasManualTaskOrder = true))
        }
        workspace.remove("assistant")
        fun signature(items: List<PlannedSegment>) = items.map { listOf(it.startMinute, it.endMinute, it.trackId, it.isLocked) }.sortedBy { it.toString() }
        AssistantSaveResult(tasks.size, existingTaskIds.count { id -> signature(replaced.filter { it.taskId == id }) != signature(segments.filter { it.taskId == id }) }, segments.size, date)
    }

    private suspend fun validatePlacement(candidate: PlannedSegment, existing: List<PlannedSegment>, allowOrdinaryOverlap: Boolean = false) {
        PlacementValidator.requireValid(candidate, existing,
            database.timeDao().getAllWeeklyBlocks().map { it.toDomain() },
            database.timeDao().getAllDateOverrides().map { it.toDomain() },
            database.timeDao().getSemesterSettings()?.firstWeekMondayEpochDay?.let(LocalDate::ofEpochDay),
            unavoidableTaskIds = database.taskDao().getAll().filter { it.userPriority == TaskPriority.REQUIRED.name }.map { it.id }.toSet(),
            allowOrdinaryOverlap = allowOrdinaryOverlap)
    }
    override fun observeCurrentPlan(): Flow<com.swan1127.repland.domain.model.ConfirmedPlan?> =
        planDao.observeCurrentPlan().map { it?.toDomain() }

    override fun observePlanHistory(): Flow<List<com.swan1127.repland.domain.model.ConfirmedPlan>> =
        planDao.observePlanHistory().map { plans -> plans.map(PlanWithSegments::toDomain) }

    override suspend fun accept(draft: PlanDraft) = database.withTransaction {
        acceptInTransaction(draft)
    }

    private suspend fun acceptInTransaction(draft: PlanDraft, restoring: Boolean = false) {
        draft.numericProfileVersion?.let { expected ->
            require(RoomNumericProfileRepository(database).refresh().version == expected) { "画像或来源已变化，请重新生成预览；当前计划保留。" }
        }
        require(draft.sourceRevision == null || draft.sourceRevision == revision()) { "任务、设置或计划已变化，请重新生成预览后确认。" }
        if (draft.orderOnly) {
            saveTaskOrder(draft.orderedTaskIds)
            workspace.remove("draft")
            return
        }
        val current = planDao.getCurrentPlanWithSegments()?.toDomain()?.segments.orEmpty()
        val activeIds = database.taskDao().getAll().map { it.toDomain() }.filter { it.status.isActive }.map { it.id }.toSet()
        val validationTime = java.time.LocalDateTime.now()
        val unavoidableIds = database.taskDao().getAll().filter { it.userPriority == TaskPriority.REQUIRED.name }.map { it.id }.toSet()
        require(current.filter { it.taskId in unavoidableIds && it.taskId in activeIds &&
            (it.date > validationTime.toLocalDate() || (it.date == validationTime.toLocalDate() && it.endMinute > validationTime.hour * 60 + validationTime.minute))
        }.all { protected -> draft.segments.any { proposed ->
            proposed.taskId == protected.taskId && proposed.date == protected.date && proposed.startMinute == protected.startMinute &&
                proposed.endMinute == protected.endMinute && proposed.trackId == protected.trackId
        } }) { "草案不能移动或移除已确认的不可避免安排；请在日程中主动调整并确认。" }
        val runningTaskId = workspace.get(RoomExecutionSessionRepository.KEY)?.let { ExecutionSessionCodec.decode(it.payload).taskId }
        require(current.filter { it.taskId == runningTaskId && (it.date > validationTime.toLocalDate() ||
            (it.date == validationTime.toLocalDate() && it.endMinute > validationTime.hour * 60 + validationTime.minute)) }.all { executing ->
            draft.segments.any { proposed -> proposed.taskId == executing.taskId && proposed.date == executing.date &&
                proposed.startMinute == executing.startMinute && proposed.endMinute == executing.endMinute && proposed.trackId == executing.trackId }
        }) { "当前专注尚未结束，草案不能移动或删除该任务的安排；请结束本轮后重新生成。" }
        require(current.filter { it.isLocked && it.taskId in activeIds &&
            (it.date.isAfter(validationTime.toLocalDate()) || (it.date == validationTime.toLocalDate() && it.endMinute > validationTime.hour * 60 + validationTime.minute))
        }.all { locked -> draft.segments.any { proposed ->
            proposed.taskId == locked.taskId && proposed.date == locked.date && proposed.startMinute == locked.startMinute &&
                proposed.endMinute == locked.endMinute && proposed.trackId == locked.trackId && proposed.isLocked
        } }) { "草案不能覆盖已锁定的安排，请先明确解锁后重新生成。" }
        for (segment in draft.segments) {
            val preserved = current.any { it.taskId == segment.taskId && it.date == segment.date &&
                it.startMinute == segment.startMinute && it.endMinute == segment.endMinute && it.trackId == segment.trackId }
            val future = segment.date > validationTime.toLocalDate() ||
                (segment.date == validationTime.toLocalDate() && segment.endMinute > validationTime.hour * 60 + validationTime.minute)
            if (future || (!restoring && !preserved)) validatePlacement(segment, draft.segments, allowOrdinaryOverlap = preserved)
        }
        val planId = UUID.randomUUID().toString()
        val now = nextPlanCreatedAt(System.currentTimeMillis())
        planDao.replaceCurrentPlan(
            plan = PlanEntity(
                id = planId,
                createdAtEpochMillis = now,
                isCurrent = true,
            ),
            segments = draft.segments.map { segment ->
                PlanSegmentEntity(
                    id = UUID.randomUUID().toString(),
                    planId = planId,
                    taskId = segment.taskId,
                    dateEpochDay = segment.date.toEpochDay(),
                    startMinute = segment.startMinute,
                    endMinute = segment.endMinute,
                    isLocked = segment.isLocked,
                    trackId = segment.trackId,
                )
            },
            taskOrder = draft.orderedTaskIds.distinct().mapIndexed { position, taskId ->
                PlanTaskOrderEntity(
                    planId = planId,
                    taskId = taskId,
                    position = position,
                    isManual = draft.hasManualTaskOrder,
                )
            },
        )
        saveTaskOrder(draft.orderedTaskIds)
        workspace.remove("draft")
    }

    override suspend fun restore(planId: String) = database.withTransaction {
        val source = requireNotNull(planDao.getPlanWithSegments(planId)) { "要恢复的计划版本不存在。" }.toDomain()
        val now = java.time.LocalDateTime.now()
        val activeIds = database.taskDao().getAll().map { it.toDomain() }.filter { it.status.isActive }.map { it.id }.toSet()
        // Preserve past history, but never resurrect future work for closed or deleted tasks.
        val segments = source.segments.filter { it.taskId in activeIds || it.date < now.toLocalDate() ||
            (it.date == now.toLocalDate() && it.endMinute <= now.hour * 60 + now.minute) }
        acceptInTransaction(PlanDraft(now, segments, emptyList(), emptyList(),
            orderedTaskIds = source.orderedTaskIds, hasManualTaskOrder = source.hasManualTaskOrder), restoring = true)
    }

    override suspend fun clearCurrentPlan() = applyManualChange(ManualPlanChange.Clear)

    override suspend fun setSegmentLocked(segmentId: String, isLocked: Boolean) = database.withTransaction {
        val source = requireNotNull(planDao.getCurrentPlanWithSegments()) { "当前计划已不存在，请刷新。" }
        require(source.segments.any { it.id == segmentId }) { "原安排已变化，请重新打开后调整。" }
        val nextId = UUID.randomUUID().toString()
        planDao.replaceCurrentPlan(PlanEntity(nextId, nextPlanCreatedAt(System.currentTimeMillis()), true),
            source.segments.map { it.copy(id = UUID.randomUUID().toString(), planId = nextId,
                isLocked = if (it.id == segmentId) isLocked else it.isLocked) },
            source.taskOrder.map { it.copy(planId = nextId) })
    }

    override suspend fun placeTask(taskId: String, date: LocalDate, startMinute: Int, endMinute: Int, trackId: String) =
        applyManualChange(ManualPlanChange.Place(taskId, date, startMinute, endMinute, trackId))

    override suspend fun movePlacement(segmentId: String, startMinute: Int, endMinute: Int, trackId: String) =
        applyManualChange(ManualPlanChange.Move(segmentId, startMinute, endMinute, trackId))

    override suspend fun removePlacement(segmentId: String) = applyManualChange(ManualPlanChange.Remove(segmentId))

    override suspend fun applyManualChange(change: ManualPlanChange, confirmedRevision: String?) = database.withTransaction {
        val currentRevision = revision()
        require(confirmedRevision == null || confirmedRevision == currentRevision) {
            "任务、约束或计划已变化，本次确认已失效；请取消并重新调整。"
        }
        val source = planDao.getCurrentPlanWithSegments()
        val current = source?.toDomain()?.segments.orEmpty()
        val tasks = database.taskDao().getAll().map { it.toDomain() }.associateBy { it.id }
        val runningTaskId = workspace.get(RoomExecutionSessionRepository.KEY)?.let { ExecutionSessionCodec.decode(it.payload).taskId }
        val targetId = when (change) {
            is ManualPlanChange.Move -> change.segmentId
            is ManualPlanChange.Remove -> change.segmentId
            else -> null
        }
        val target = targetId?.let { id -> requireNotNull(current.find { it.id == id }) { "原安排已变化，请重新打开后调整。" } }
        val changed = if (change == ManualPlanChange.Clear) current else listOfNotNull(target)
        require(changed.none { it.isLocked }) { "请先解除锁定，再调整这个安排。" }
        require(changed.none { it.taskId == runningTaskId }) { "请先结束本轮专注，再调整安排。" }
        val candidate = when (change) {
            is ManualPlanChange.Place -> {
                require(tasks[change.taskId]?.status?.isActive == true) { "任务不存在或已结束，请刷新。" }
                PlannedSegment(taskId = change.taskId, date = change.date, startMinute = change.start, endMinute = change.end, trackId = change.track)
            }
            is ManualPlanChange.Move -> requireNotNull(target).copy(startMinute = change.start, endMinute = change.end, trackId = change.track)
            else -> null
        }
        val others = current.filterNot { it.id == targetId }
        // Hard guards always run before asking about ordinary overlap. Consent never bypasses them.
        if (candidate != null) validatePlacement(candidate, others, allowOrdinaryOverlap = true)
        val overlap = candidate != null && others.any { it.date == candidate.date && it.startMinute < candidate.endMinute && it.endMinute > candidate.startMinute }
        require(!overlap || tasks[candidate?.taskId]?.userPriority != TaskPriority.REQUIRED) { "不可避免安排不能与其他任务重叠，请先调整时间。" }
        val unavoidable = changed.any { tasks[it.taskId]?.userPriority == TaskPriority.REQUIRED }
        if (confirmedRevision == null && (overlap || unavoidable)) {
            val reason = listOfNotNull(
                if (unavoidable) "这会调整已确认的不可避免安排。" else null,
                if (overlap) "这会与普通任务重叠；重叠不代表同时执行。" else null,
            ).joinToString("\n")
            throw PlanChangeConfirmationRequired(currentRevision, reason)
        }
        val nextSegments = when (change) {
            ManualPlanChange.Clear -> emptyList()
            is ManualPlanChange.Remove -> others
            is ManualPlanChange.Move -> others + requireNotNull(candidate)
            is ManualPlanChange.Place -> current + requireNotNull(candidate)
        }
        val nextId = UUID.randomUUID().toString()
        val order = (source?.taskOrder.orEmpty().sortedBy { it.position }.map { it.taskId } +
            listOfNotNull((change as? ManualPlanChange.Place)?.taskId)).distinct()
        planDao.replaceCurrentPlan(PlanEntity(nextId, nextPlanCreatedAt(System.currentTimeMillis()), true),
            nextSegments.map { PlanSegmentEntity(UUID.randomUUID().toString(), nextId, it.taskId, it.date.toEpochDay(),
                it.startMinute, it.endMinute, it.isLocked, it.trackId) },
            order.mapIndexed { index, id -> PlanTaskOrderEntity(nextId, id, index, source?.taskOrder?.firstOrNull { it.taskId == id }?.isManual ?: true) })
    }

    /** Keeps plan-history order deterministic even when confirmations share one clock millisecond. */
    private suspend fun nextPlanCreatedAt(now: Long): Long {
        val previous = planDao.latestCreatedAt() ?: return now
        return maxOf(now, previous + 1)
    }
}
