package com.swan1127.repland.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.ceil

data class AutonomousTimeWindow(
    val taskId: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val exactStart: Boolean = false,
)

enum class AutonomousPlanConflictReason {
    HARD_TIME_BLOCK, OUTSIDE_AVAILABILITY, BEFORE_INTENDED_DAY, AFTER_DEADLINE,
    OVERLAPPING_PROTECTED_SEGMENT, OUTSIDE_REQUESTED_WINDOW, INVALID_TIME_RANGE,
}

data class AutonomousPlanConflict(
    val segmentId: String,
    val taskId: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val reason: AutonomousPlanConflictReason,
)

object AutonomousPlanRepair {
    private const val QUANTUM = 30
    private const val HORIZON_DAYS = 30

    fun repair(
        input: PlanGenerationInput,
        current: ConfirmedPlan?,
        now: LocalDateTime,
        runningTaskId: String? = null,
        timeWindows: List<AutonomousTimeWindow> = emptyList(),
        scheduleNew: Boolean = true,
        targetDate: LocalDate? = null,
        targetStartMinute: Int = 0,
        targetEndMinute: Int = 1440,
        excludedFromNewTaskIds: Set<String> = emptySet(),
    ): PlanDraft {
        require(targetDate == null || validRange(targetStartMinute, targetEndMinute)) { "The arrangement window must have a valid minute range." }
        val active = input.tasks.filter { it.status.isActive }.associateBy(Task::id)
        val rankings = LocalPriorityRanker.rank(active.values.toList(), input.categoryPreferences, now.toLocalDate(), input.manualTaskOrder)
        val hasAvailability = PlanningConstraintValidator.hasExplicitAvailability(input)
        val availabilityCache = mutableMapOf<Triple<LocalDate, Int, Int>, Boolean>()
        fun canPlace(segment: PlannedSegment): Boolean = availabilityCache.getOrPut(
            Triple(segment.date, segment.startMinute, segment.endMinute),
        ) { available(segment, input) }
        val retained = current?.segments.orEmpty().filter { ended(it, now) }.toMutableList()
        val candidates = linkedMapOf<String, PlannedSegment>()
        (current?.segments.orEmpty() + input.lockedSegments).forEach { candidates[identity(it)] = it }
        val future = candidates.values.filter { !ended(it, now) }
        val protected = future.filter { segment ->
            started(segment, now) || (active[segment.taskId]?.let { task ->
                segment.isLocked || task.userPriority == TaskPriority.REQUIRED ||
                    task.status == TaskStatus.IN_PROGRESS || task.id == runningTaskId ||
                    (targetDate != null && (segment.date != targetDate ||
                        segment.startMinute < targetStartMinute || segment.endMinute > targetEndMinute))
            } == true)
        }
        retained += protected
        val protectedIds = protected.mapTo(mutableSetOf(), ::identity)

        future.filter { identity(it) !in protectedIds && it.taskId in active }
            .sortedWith(segmentOrder).forEach { segment ->
                val task = active.getValue(segment.taskId)
                if (!validRange(segment.startMinute, segment.endMinute) || hardBlocked(segment, input) ||
                    !withinDates(task, segment.date) ||
                    (hasAvailability && !canPlace(segment)) ||
                    !withinWindow(segment, timeWindows, retained) ||
                    retained.any { other -> identity(other) in protectedIds && overlaps(segment, other) }) return@forEach
                if (task.totalDurationMinutes == null) {
                    retained += segment
                    return@forEach
                }
                val missing = (remainingMinutes(task) - futureCapacity(task.id, retained, now)).coerceAtLeast(0)
                if (missing == 0) return@forEach
                val keep = minOf(segment.endMinute - segment.startMinute, roundUp(missing))
                retained += segment.copy(endMinute = segment.startMinute + keep)
            }

        if (hasAvailability && scheduleNew) rankings.forEach { assessment ->
            val task = active.getValue(assessment.taskId)
            if (task.id in excludedFromNewTaskIds) return@forEach
            var missing = (remainingMinutes(task) - futureCapacity(task.id, retained, now)).coerceAtLeast(0)
            if (missing == 0) return@forEach
            val selected = mutableListOf<PlannedSegment>()
            val windows = timeWindows.filter { it.taskId == task.id }
            for (offset in 0 until HORIZON_DAYS) {
                if (missing <= 0) break
                val date = now.toLocalDate().plusDays(offset.toLong())
                if (targetDate != null && date != targetDate) continue
                if (!withinDates(task, date)) continue
                val dayWindows = windows.filter { it.date == date && validRange(it.startMinute, it.endMinute) }
                if (windows.isNotEmpty() && dayWindows.isEmpty()) continue
                val starts = if (dayWindows.isEmpty()) {
                    (0 until 1440 step QUANTUM).toList()
                } else dayWindows.flatMap { window ->
                    val anchorExists = retained.any { it.taskId == task.id && it.date == date &&
                        it.startMinute == window.startMinute && !ended(it, now) }
                    val anchor = segment(task.id, date, window.startMinute, window.startMinute + QUANTUM)
                    val anchorCanStart = !beforeNow(anchor, now) && anchor.endMinute <= window.endMinute &&
                        canPlace(anchor) && retained.none { overlaps(it, anchor) }
                    if (window.exactStart && !anchorExists && !anchorCanStart) emptyList()
                    else {
                        val first = if (window.exactStart) window.startMinute else roundUp(window.startMinute)
                        (first until window.endMinute step QUANTUM).filter { it + QUANTUM <= window.endMinute }
                    }
                }.distinct().sorted()
                val preference = input.placementWindows[task.id]
                val orderedStarts = if (preference == null) starts else {
                    val (inside, outside) = starts.partition { start ->
                        start >= preference.startMinute && start + QUANTUM <= preference.endMinute
                    }
                    inside + outside
                }
                for (start in orderedStarts) {
                    if (missing <= 0) break
                    val candidate = segment(task.id, date, start, start + QUANTUM)
                    if (targetDate != null && (candidate.startMinute < targetStartMinute || candidate.endMinute > targetEndMinute)) continue
                    if (candidate.endMinute > 1440 || beforeNow(candidate, now) || !canPlace(candidate) ||
                        !withinWindow(candidate, timeWindows, retained + selected) ||
                        (retained + selected).any { overlaps(it, candidate) }) continue
                    selected += candidate
                    missing -= QUANTUM
                }
            }
            retained += merge(selected)
        }

        val preliminary = PlanDraft(now, retained.distinctBy(::identity).sortedWith(segmentOrder), emptyList(), emptyList(),
            rankings.map { it.taskId }, rankings, input.manualTaskOrder.isNotEmpty())
        val conflictIds = conflicts(input, preliminary, now, timeWindows).mapTo(mutableSetOf()) { it.taskId }
        val unscheduled = rankings.mapNotNull { assessment ->
            val task = active.getValue(assessment.taskId)
            val missing = (remainingMinutes(task) - futureCapacity(task.id, preliminary.segments, now)).coerceAtLeast(0)
            if (missing == 0) null else UnscheduledTask(task.id, missing, when {
                task.dueDate != null -> UnscheduledReason.CAPACITY_BEFORE_DUE_DATE
                else -> UnscheduledReason.CAPACITY_IN_ROLLING_WINDOW
            })
        }
        val pending = unscheduled.mapTo(mutableSetOf()) { it.taskId } + conflictIds
        return preliminary.copy(pendingTaskIds = rankings.map { it.taskId }.filter { it in pending }, unscheduledTasks = unscheduled)
    }

    
    fun conflicts(
        input: PlanGenerationInput,
        draft: PlanDraft,
        now: LocalDateTime,
        timeWindows: List<AutonomousTimeWindow> = emptyList(),
    ): List<AutonomousPlanConflict> {
        val tasks = input.tasks.associateBy(Task::id)
        val future = draft.segments.filter { !ended(it, now) }
        val hasAvailability = PlanningConstraintValidator.hasExplicitAvailability(input)
        return future.flatMap { segment ->
            val reasons = mutableSetOf<AutonomousPlanConflictReason>()
            val task = tasks[segment.taskId]
            if (!validRange(segment.startMinute, segment.endMinute)) reasons += AutonomousPlanConflictReason.INVALID_TIME_RANGE
            if (hardBlocked(segment, input)) reasons += AutonomousPlanConflictReason.HARD_TIME_BLOCK
            if (hasAvailability && !available(segment, input)) reasons += AutonomousPlanConflictReason.OUTSIDE_AVAILABILITY
            if (task?.scheduledForDate?.isAfter(segment.date) == true) reasons += AutonomousPlanConflictReason.BEFORE_INTENDED_DAY
            if (task?.dueDate?.isBefore(segment.date) == true) reasons += AutonomousPlanConflictReason.AFTER_DEADLINE
            if (!withinWindow(segment, timeWindows, future)) reasons += AutonomousPlanConflictReason.OUTSIDE_REQUESTED_WINDOW
            if (future.any { other -> identity(other) != identity(segment) && overlaps(segment, other) &&
                    (segment.isLocked || other.isLocked || task?.userPriority == TaskPriority.REQUIRED ||
                        tasks[other.taskId]?.userPriority == TaskPriority.REQUIRED || started(segment, now) || started(other, now)) }) {
                reasons += AutonomousPlanConflictReason.OVERLAPPING_PROTECTED_SEGMENT
            }
            reasons.map { AutonomousPlanConflict(segment.id, segment.taskId, segment.date, segment.startMinute, segment.endMinute, it) }
        }
    }

    private fun remainingMinutes(task: Task): Int {
        val estimate = task.totalDurationMinutes?.takeIf { it > 0 } ?: QUANTUM
        val progress = task.progressPercent?.takeIf { it in 1..99 } ?: 0
        return ceil(estimate * (100 - progress) / 100.0).toInt()
    }

    private fun futureCapacity(taskId: String, segments: List<PlannedSegment>, now: LocalDateTime): Int =
        segments.filter { it.taskId == taskId && !ended(it, now) }.groupBy { it.date }.entries.sumOf { (date, day) ->
            var end = -1
            day.sortedBy { it.startMinute }.sumOf { segment ->
                val start = maxOf(segment.startMinute, if (date == now.toLocalDate()) now.hour * 60 + now.minute else 0, end)
                val capacity = (segment.endMinute.coerceAtMost(1440) - start).coerceAtLeast(0)
                end = maxOf(end, segment.endMinute)
                capacity
            }
        }

    private fun withinDates(task: Task, date: LocalDate): Boolean =
        (task.scheduledForDate == null || !date.isBefore(task.scheduledForDate)) &&
            (task.dueDate == null || !date.isAfter(task.dueDate))

    private fun withinWindow(segment: PlannedSegment, all: List<AutonomousTimeWindow>, retained: List<PlannedSegment>): Boolean {
        val windows = all.filter { it.taskId == segment.taskId }
        if (windows.isEmpty()) return true
        return windows.any { window -> window.date == segment.date && validRange(window.startMinute, window.endMinute) &&
            segment.startMinute >= window.startMinute && segment.endMinute <= window.endMinute &&
            (!window.exactStart || segment.startMinute == window.startMinute || retained.any {
                it.taskId == segment.taskId && it.date == segment.date && it.startMinute == window.startMinute
            }) }
    }

    private fun available(segment: PlannedSegment, input: PlanGenerationInput): Boolean =
        validRange(segment.startMinute, segment.endMinute) && (segment.startMinute until segment.endMinute).all { minute ->
            PlanningConstraintValidator.isAvailable(segment.date, minute, minute + 1,
                input.weeklyBlocks, input.dateOverrides, input.semesterFirstWeekMonday)
        }

    private fun hardBlocked(segment: PlannedSegment, input: PlanGenerationInput): Boolean =
        input.dateOverrides.any { it.date == segment.date && it.type == DateOverrideType.BLOCKED &&
            it.startMinute < segment.endMinute && it.endMinute > segment.startMinute } ||
            input.weeklyBlocks.any { it.kind != TimeBlockKind.AVAILABLE && it.dayOfWeek == segment.date.dayOfWeek &&
                CourseWeekPattern.appliesOn(it.weekPattern, segment.date, input.semesterFirstWeekMonday) &&
                it.startMinute < segment.endMinute && it.endMinute > segment.startMinute }

    private fun ended(segment: PlannedSegment, now: LocalDateTime): Boolean = segment.date < now.toLocalDate() ||
        (segment.date == now.toLocalDate() && segment.endMinute <= now.hour * 60 + now.minute)

    private fun started(segment: PlannedSegment, now: LocalDateTime): Boolean = segment.date == now.toLocalDate() &&
        segment.startMinute <= now.hour * 60 + now.minute && segment.endMinute > now.hour * 60 + now.minute

    private fun beforeNow(segment: PlannedSegment, now: LocalDateTime): Boolean = segment.date < now.toLocalDate() ||
        (segment.date == now.toLocalDate() && segment.startMinute * 60 < now.toLocalTime().toSecondOfDay())

    private fun validRange(start: Int, end: Int): Boolean = start in 0 until 1440 && end in 1..1440 && start < end
    private fun roundUp(minutes: Int): Int = ((minutes + QUANTUM - 1) / QUANTUM) * QUANTUM
    private fun overlaps(a: PlannedSegment, b: PlannedSegment): Boolean = a.date == b.date && a.startMinute < b.endMinute && a.endMinute > b.startMinute
    private fun identity(segment: PlannedSegment): String = segment.id.ifBlank {
        "${segment.taskId}:${segment.date}:${segment.startMinute}:${segment.endMinute}:${segment.trackId}"
    }
    private fun segment(taskId: String, date: LocalDate, start: Int, end: Int) = PlannedSegment(
        "auto:$taskId:${date.toEpochDay()}:$start:$end", taskId, date, start, end)

    private fun merge(slots: List<PlannedSegment>): List<PlannedSegment> {
        val result = mutableListOf<PlannedSegment>()
        slots.sortedWith(segmentOrder).forEach { slot ->
            val previous = result.lastOrNull()
            if (previous != null && previous.taskId == slot.taskId && previous.date == slot.date && previous.endMinute == slot.startMinute) {
                result[result.lastIndex] = segment(slot.taskId, slot.date, previous.startMinute, slot.endMinute)
            } else result += slot
        }
        return result
    }

    private val segmentOrder = compareBy(PlannedSegment::date, PlannedSegment::startMinute, PlannedSegment::endMinute)
        .thenBy(PlannedSegment::taskId).thenBy(PlannedSegment::id)
}
