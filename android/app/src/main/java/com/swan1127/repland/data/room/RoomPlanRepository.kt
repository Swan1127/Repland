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

    override suspend fun saveTasksAndPlace(tasks: List<TaskDraft>, segments: List<PlannedSegment>) = database.withTransaction {
        val sourceRevision = workspace.get("assistant")?.let { InteractionWorkspaceCodec.decodeAssistant(it.payload).sourceRevision }
        require(sourceRevision == null || sourceRevision == revision()) { "任务、设置或计划已变化，请重新生成助手草案后确认。" }
        require(tasks.isNotEmpty() && tasks.all { TaskDraftValidator.isValid(it) && !it.id.isNullOrBlank() }) { "请检查事项名称和时长。" }
        require(tasks.map { it.id }.distinct().size == tasks.size) { "事项重复，请重新生成。" }
        val ids = tasks.map { requireNotNull(it.id) }.toSet()
        require(segments.all { it.taskId in ids }) { "安排中的事项引用无效。" }
        require(ids.none { database.taskDao().getById(it) != null }) { "这些事项已经保存，请查看任务列表。" }
        val current = planDao.getCurrentPlanWithSegments()?.toDomain()
        for (segment in segments) validatePlacement(segment, current?.segments.orEmpty() + segments)
        val taskRepository = RoomTaskRepository(database)
        tasks.forEach { taskRepository.save(it) }
        if (segments.isNotEmpty()) {
            accept(PlanDraft(java.time.LocalDateTime.now(), current?.segments.orEmpty() + segments, emptyList(), emptyList(),
                orderedTaskIds = (current?.orderedTaskIds.orEmpty() + ids).distinct(), hasManualTaskOrder = true))
        }
        workspace.remove("assistant")
    }

    private suspend fun validatePlacement(candidate: PlannedSegment, existing: List<PlannedSegment>) {
        PlacementValidator.requireValid(candidate, existing,
            database.timeDao().getAllWeeklyBlocks().map { it.toDomain() },
            database.timeDao().getAllDateOverrides().map { it.toDomain() },
            database.timeDao().getSemesterSettings()?.firstWeekMondayEpochDay?.let(LocalDate::ofEpochDay))
    }
    override fun observeCurrentPlan(): Flow<com.swan1127.repland.domain.model.ConfirmedPlan?> =
        planDao.observeCurrentPlan().map { it?.toDomain() }

    override fun observePlanHistory(): Flow<List<com.swan1127.repland.domain.model.ConfirmedPlan>> =
        planDao.observePlanHistory().map { plans -> plans.map(PlanWithSegments::toDomain) }

    override suspend fun accept(draft: PlanDraft) = database.withTransaction {
        require(draft.sourceRevision == null || draft.sourceRevision == revision()) { "任务、设置或计划已变化，请重新生成预览后确认。" }
        if (draft.orderOnly) {
            saveTaskOrder(draft.orderedTaskIds)
            workspace.remove("draft")
            return@withTransaction
        }
        val current = planDao.getCurrentPlanWithSegments()?.toDomain()?.segments.orEmpty()
        val activeIds = database.taskDao().getAll().map { it.toDomain() }.filter { it.status.isActive }.map { it.id }.toSet()
        val validationTime = java.time.LocalDateTime.now()
        require(current.filter { it.isLocked && it.taskId in activeIds &&
            (it.date.isAfter(validationTime.toLocalDate()) || (it.date == validationTime.toLocalDate() && it.endMinute > validationTime.hour * 60 + validationTime.minute))
        }.all { locked -> draft.segments.any { proposed ->
            proposed.taskId == locked.taskId && proposed.date == locked.date && proposed.startMinute == locked.startMinute &&
                proposed.endMinute == locked.endMinute && proposed.trackId == locked.trackId && proposed.isLocked
        } }) { "草案不能覆盖已锁定的安排，请先明确解锁后重新生成。" }
        for (segment in draft.segments) {
            val preserved = current.any { it.taskId == segment.taskId && it.date == segment.date &&
                it.startMinute == segment.startMinute && it.endMinute == segment.endMinute && it.trackId == segment.trackId }
            if (!preserved) validatePlacement(segment, draft.segments)
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

    override suspend fun restore(planId: String) {
        val source = requireNotNull(planDao.getPlanWithSegments(planId)) {
            "The plan version to restore does not exist."
        }
        val restoredPlanId = UUID.randomUUID().toString()
        val now = nextPlanCreatedAt(System.currentTimeMillis())
        planDao.replaceCurrentPlan(
            plan = PlanEntity(
                id = restoredPlanId,
                createdAtEpochMillis = now,
                isCurrent = true,
            ),
            segments = source.segments.map { segment ->
                segment.copy(id = UUID.randomUUID().toString(), planId = restoredPlanId)
            },
            taskOrder = source.taskOrder
                .ifEmpty {
                    source.segments
                        .sortedWith(compareBy(PlanSegmentEntity::dateEpochDay, PlanSegmentEntity::startMinute))
                        .map(PlanSegmentEntity::taskId)
                        .distinct()
                        .mapIndexed { position, taskId ->
                            PlanTaskOrderEntity(planId = source.plan.id, taskId = taskId, position = position)
                        }
                }
                .map { item -> item.copy(planId = restoredPlanId) },
        )
        saveTaskOrder(source.toDomain().orderedTaskIds)
        workspace.remove("draft")
    }

    override suspend fun clearCurrentPlan() = planDao.archiveCurrentPlan()

    override suspend fun setSegmentLocked(segmentId: String, isLocked: Boolean) =
        planDao.setSegmentLocked(segmentId, isLocked)

    override suspend fun placeTask(
        taskId: String,
        date: LocalDate,
        startMinute: Int,
        endMinute: Int,
        trackId: String,
    ) = database.withTransaction {
        val source = planDao.getCurrentPlanWithSegments()
        validatePlacement(PlannedSegment(taskId = taskId, date = date, startMinute = startMinute,
            endMinute = endMinute, trackId = trackId), source?.toDomain()?.segments.orEmpty())
        val nextPlanId = UUID.randomUUID().toString()
        val now = nextPlanCreatedAt(System.currentTimeMillis())
        val existingOrder = source?.taskOrder
            ?.sortedBy(PlanTaskOrderEntity::position)
            ?.map(PlanTaskOrderEntity::taskId)
            .orEmpty()
        val orderedTaskIds = (existingOrder + taskId).distinct()
        planDao.replaceCurrentPlan(
            plan = PlanEntity(nextPlanId, now, true),
            segments = source?.segments.orEmpty().map { it.copy(id = UUID.randomUUID().toString(), planId = nextPlanId) } +
                PlanSegmentEntity(
                    id = UUID.randomUUID().toString(),
                    planId = nextPlanId,
                    taskId = taskId,
                    dateEpochDay = date.toEpochDay(),
                    startMinute = startMinute,
                    endMinute = endMinute,
                    isLocked = false,
                    trackId = trackId,
                ),
            taskOrder = orderedTaskIds.mapIndexed { position, id ->
                PlanTaskOrderEntity(nextPlanId, id, position, true)
            },
        )
    }

    override suspend fun movePlacement(
        segmentId: String,
        startMinute: Int,
        endMinute: Int,
        trackId: String,
    ) = database.withTransaction {
        val source = planDao.getCurrentPlanWithSegments() ?: return@withTransaction
        val target = source.toDomain().segments.firstOrNull { it.id == segmentId } ?: return@withTransaction
        require(!target.isLocked) { "请先解除锁定，再移动这个安排。" }
        validatePlacement(target.copy(startMinute = startMinute, endMinute = endMinute, trackId = trackId), source.toDomain().segments)
        val nextPlanId = UUID.randomUUID().toString()
        val now = nextPlanCreatedAt(System.currentTimeMillis())
        planDao.replaceCurrentPlan(
            plan = PlanEntity(nextPlanId, now, true),
            segments = source.segments.map { segment ->
                if (segment.id == segmentId) {
                    segment.copy(
                        id = UUID.randomUUID().toString(),
                        planId = nextPlanId,
                        startMinute = startMinute,
                        endMinute = endMinute,
                        trackId = trackId,
                    )
                } else segment.copy(id = UUID.randomUUID().toString(), planId = nextPlanId)
            },
            taskOrder = source.taskOrder.map { it.copy(planId = nextPlanId) },
        )
    }

    override suspend fun removePlacement(segmentId: String) {
        val source = planDao.getCurrentPlanWithSegments() ?: return
        if (source.segments.none { it.id == segmentId }) return
        val nextPlanId = UUID.randomUUID().toString()
        val now = nextPlanCreatedAt(System.currentTimeMillis())
        val remaining = source.segments.filterNot { it.id == segmentId }
        planDao.replaceCurrentPlan(
            plan = PlanEntity(nextPlanId, now, true),
            segments = remaining.map { it.copy(id = UUID.randomUUID().toString(), planId = nextPlanId) },
            taskOrder = source.taskOrder.map { it.copy(planId = nextPlanId) },
        )
    }

    /** Keeps plan-history order deterministic even when confirmations share one clock millisecond. */
    private suspend fun nextPlanCreatedAt(now: Long): Long {
        val previous = planDao.latestCreatedAt() ?: return now
        return maxOf(now, previous + 1)
    }
}
