package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.ArrangementConflictException
import com.swan1127.repland.domain.model.CourseWeekPattern
import com.swan1127.repland.domain.model.DateOverrideType
import com.swan1127.repland.domain.model.PlanLockedException
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.TaskPlacement
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeBlockKind
import java.time.LocalDate
import java.time.LocalDateTime

/** Every caller holds the Room write transaction while reading and checking these inputs. */
internal class CurrentPlanConstraintGuard(
    private val planDao: PlanDao,
    // The DAO-only repository constructor remains useful to pure JVM mapping tests.
    private val timeDao: TimeDao?,
    private val taskDao: TaskDao? = null,
) {
    suspend fun validateAdditions(
        placements: List<TaskPlacement>,
        excludedSegmentId: String? = null,
        allowExistingIdentical: Boolean = true,
    ) {
        if (placements.isEmpty()) return
        validateCalendar(placements)
        val current = planDao.getCurrentPlanWithSegments()?.segments.orEmpty()
            .filterNot { it.id == excludedSegmentId }
        val additions = placements.distinct()
        additions.forEach { placement ->
            if (current.any { existing ->
                val previous = existing.toPlacement()
                !(allowExistingIdentical && previous == placement) && conflicts(placement, previous)
            }) throw ArrangementConflictException()
        }
        validateWithinPlan(additions)
    }

    suspend fun validateReplacement(segments: List<PlannedSegment>) {
        val placements = segments.map(PlannedSegment::toPlacement)
        validateCalendar(placements)
        validateWithinPlan(placements)
        val now = LocalDateTime.now()
        val taskStates = taskDao?.getAll()?.associate { it.id to it.status }
        val locked = planDao.getCurrentPlanWithSegments()?.segments.orEmpty().filter { existing ->
            val date = LocalDate.ofEpochDay(existing.dateEpochDay)
            val hasNotEnded = date > now.toLocalDate() ||
                (date == now.toLocalDate() && existing.endMinute > now.hour * 60 + now.minute)
            val taskIsActive = taskStates == null || taskStates[existing.taskId]?.let { status ->
                TaskStatus.entries.firstOrNull { it.name == status }?.isActive
            } == true
            existing.isLocked && hasNotEnded && taskIsActive
        }
        if (locked.any { existing ->
            segments.none { proposed -> proposed.isLocked && proposed.toPlacement() == existing.toPlacement() }
        }) throw PlanLockedException()
    }

    private suspend fun validateCalendar(placements: List<TaskPlacement>) {
        placements.forEach(::validatePlacement)
        if (placements.isEmpty()) return
        val calendar = timeDao ?: return
        val weekly = calendar.getAllWeeklyBlocks()
        val dates = calendar.getAllDateOverrides()
        val firstMonday = calendar.getSemesterSettings()?.firstWeekMondayEpochDay?.let(LocalDate::ofEpochDay)
        if (placements.any { placement ->
            val openings = dates.filter { block ->
                block.type == DateOverrideType.AVAILABLE.name && block.dateEpochDay == placement.date.toEpochDay()
            }
            weekly.any { block ->
                block.kind != TimeBlockKind.AVAILABLE.name && block.dayOfWeek == placement.date.dayOfWeek.value &&
                    CourseWeekPattern.appliesOn(block.weekPattern, placement.date, firstMonday) &&
                    overlaps(placement.startMinute, placement.endMinute, block.startMinute, block.endMinute) &&
                    !covers(
                        maxOf(placement.startMinute, block.startMinute),
                        minOf(placement.endMinute, block.endMinute),
                        openings,
                    )
            } || dates.any { block ->
                block.type == DateOverrideType.BLOCKED.name && block.dateEpochDay == placement.date.toEpochDay() &&
                    overlaps(placement.startMinute, placement.endMinute, block.startMinute, block.endMinute)
            }
        }) throw ArrangementConflictException()
    }

    private fun validateWithinPlan(placements: List<TaskPlacement>) {
        placements.forEachIndexed { index, placement ->
            if (placements.take(index).any { previous -> conflicts(placement, previous) }) {
                throw ArrangementConflictException()
            }
        }
    }

    /** User-owned task lanes can run in parallel; fixed calendar blocks protect every lane. */
    private fun conflicts(first: TaskPlacement, second: TaskPlacement): Boolean =
        first.date == second.date && first.trackId == second.trackId &&
            overlaps(first.startMinute, first.endMinute, second.startMinute, second.endMinute)

    private fun overlaps(start: Int, end: Int, otherStart: Int, otherEnd: Int): Boolean =
        start < otherEnd && end > otherStart

    /** An explicit one-day opening can cover only the busy portion, or several adjacent portions. */
    private fun covers(start: Int, end: Int, openings: List<DateOverrideEntity>): Boolean {
        var coveredUntil = start
        openings.sortedBy(DateOverrideEntity::startMinute).forEach { opening ->
            if (opening.startMinute <= coveredUntil) coveredUntil = maxOf(coveredUntil, opening.endMinute)
        }
        return coveredUntil >= end
    }
}

internal fun PlanSegmentEntity.toPlacement(): TaskPlacement = TaskPlacement(
    taskId, LocalDate.ofEpochDay(dateEpochDay), startMinute, endMinute, trackId,
)

private fun PlannedSegment.toPlacement(): TaskPlacement = TaskPlacement(
    taskId, date, startMinute, endMinute, trackId,
)
