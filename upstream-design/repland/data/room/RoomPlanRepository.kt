package com.swan1127.repland.data.room

import androidx.room.withTransaction
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlanLockedException
import com.swan1127.repland.domain.model.TaskPlacement
import com.swan1127.repland.domain.ports.PlanRepository
import java.util.UUID
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPlanRepository internal constructor(
    private val planDao: PlanDao,
    private val transaction: RoomWriteTransaction,
    private val timeDao: TimeDao? = null,
    private val taskDao: TaskDao? = null,
) : PlanRepository {
    constructor(database: ReplandDatabase) : this(
        database.planDao(),
        RoomWriteTransaction { block -> database.withTransaction { block() } },
        database.timeDao(),
        database.taskDao(),
    )

    constructor(planDao: PlanDao) : this(planDao, SerialRoomWriteTransaction())

    override fun observeCurrentPlan(): Flow<com.swan1127.repland.domain.model.ConfirmedPlan?> =
        planDao.observeCurrentPlan().map { it?.toDomain() }

    override fun observePlanHistory(): Flow<List<com.swan1127.repland.domain.model.ConfirmedPlan>> =
        planDao.observePlanHistory().map { plans -> plans.map(PlanWithSegments::toDomain) }

    override suspend fun accept(draft: PlanDraft) = transaction.run {
        CurrentPlanConstraintGuard(planDao, timeDao, taskDao).validateReplacement(draft.segments)
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

    override suspend fun restore(planId: String) = transaction.run {
        val source = requireNotNull(planDao.getPlanWithSegments(planId)) {
            "The plan version to restore does not exist."
        }
        CurrentPlanConstraintGuard(planDao, timeDao, taskDao).validateReplacement(source.toDomain().segments)
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

    override suspend fun clearCurrentPlan() = transaction.run { planDao.archiveCurrentPlan() }

    override suspend fun setSegmentLocked(segmentId: String, isLocked: Boolean) =
        transaction.run { planDao.setSegmentLocked(segmentId, isLocked) }

    override suspend fun placeTask(
        taskId: String,
        date: LocalDate,
        startMinute: Int,
        endMinute: Int,
        trackId: String,
    ) = transaction.run {
        val placement = TaskPlacement(taskId, date, startMinute, endMinute, trackId)
        CurrentPlanConstraintGuard(planDao, timeDao).validateAdditions(listOf(placement))
        appendPlacements(planDao, listOf(placement))
    }

    override suspend fun movePlacement(
        segmentId: String,
        startMinute: Int,
        endMinute: Int,
        trackId: String,
    ) = transaction.run {
        require(startMinute in 0 until 1440 && endMinute in 1..1440 && startMinute < endMinute)
        val source = planDao.getCurrentPlanWithSegments() ?: return@run
        val moving = source.segments.firstOrNull { it.id == segmentId } ?: return@run
        val placement = moving.toPlacement().copy(startMinute = startMinute, endMinute = endMinute, trackId = trackId)
        if (placement == moving.toPlacement()) return@run
        if (moving.isLocked) throw PlanLockedException()
        CurrentPlanConstraintGuard(planDao, timeDao).validateAdditions(
            listOf(placement), excludedSegmentId = segmentId, allowExistingIdentical = false,
        )
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

    override suspend fun removePlacement(segmentId: String) = transaction.run {
        val source = planDao.getCurrentPlanWithSegments() ?: return@run
        val removing = source.segments.firstOrNull { it.id == segmentId } ?: return@run
        if (removing.isLocked) throw PlanLockedException()
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

/** Caller holds the database transaction; a whole batch creates one plan version. */
internal suspend fun appendPlacements(planDao: PlanDao, placements: List<TaskPlacement>) {
    if (placements.isEmpty()) return
    placements.forEach(::validatePlacement)
    val source = planDao.getCurrentPlanWithSegments()
    val additions = placements.distinct().filterNot { placement ->
        source?.segments.orEmpty().any { existing ->
            existing.taskId == placement.taskId && existing.dateEpochDay == placement.date.toEpochDay() &&
                existing.startMinute == placement.startMinute && existing.endMinute == placement.endMinute &&
                existing.trackId == placement.trackId
        }
    }
    if (additions.isEmpty()) return
    val planId = UUID.randomUUID().toString()
    val now = maxOf(System.currentTimeMillis(), (planDao.latestCreatedAt() ?: -1L) + 1L)
    val existingOrder = source?.taskOrder?.sortedBy(PlanTaskOrderEntity::position)
        ?.map(PlanTaskOrderEntity::taskId).orEmpty()
        .ifEmpty { source?.segments.orEmpty().map(PlanSegmentEntity::taskId).distinct() }
    val orderedTaskIds = (existingOrder + additions.map(TaskPlacement::taskId)).distinct()
    planDao.replaceCurrentPlan(
        plan = PlanEntity(planId, now, true),
        segments = source?.segments.orEmpty().map { it.copy(id = UUID.randomUUID().toString(), planId = planId) } +
            additions.map { placement ->
                PlanSegmentEntity(
                    id = UUID.randomUUID().toString(),
                    planId = planId,
                    taskId = placement.taskId,
                    dateEpochDay = placement.date.toEpochDay(),
                    startMinute = placement.startMinute,
                    endMinute = placement.endMinute,
                    trackId = placement.trackId,
                )
            },
        taskOrder = orderedTaskIds.mapIndexed { position, id ->
            PlanTaskOrderEntity(planId, id, position, true)
        },
    )
}

internal fun validatePlacement(placement: TaskPlacement) {
    require(placement.taskId.isNotBlank()) { "A placement needs a task identity." }
    require(placement.startMinute in 0 until 1440 && placement.endMinute in 1..1440 &&
        placement.startMinute < placement.endMinute) { "A placement needs a valid time range." }
    require(placement.trackId.isNotBlank()) { "A placement needs a track." }
}
