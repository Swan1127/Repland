package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.ports.PlanRepository
import java.util.UUID
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPlanRepository(
    private val planDao: PlanDao,
) : PlanRepository {
    override fun observeCurrentPlan(): Flow<com.swan1127.repland.domain.model.ConfirmedPlan?> =
        planDao.observeCurrentPlan().map { it?.toDomain() }

    override fun observePlanHistory(): Flow<List<com.swan1127.repland.domain.model.ConfirmedPlan>> =
        planDao.observePlanHistory().map { plans -> plans.map(PlanWithSegments::toDomain) }

    override suspend fun accept(draft: PlanDraft) {
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
    ) {
        require(startMinute in 0 until 1440 && endMinute in 1..1440 && startMinute < endMinute)
        val source = planDao.getCurrentPlanWithSegments()
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
    ) {
        require(startMinute in 0 until 1440 && endMinute in 1..1440 && startMinute < endMinute)
        val source = planDao.getCurrentPlanWithSegments() ?: return
        if (source.segments.none { it.id == segmentId }) return
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
