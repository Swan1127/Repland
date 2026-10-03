package com.swan1127.repland.domain.model

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.ceil

data class PlannedSegment(
    val id: String = "",
    val taskId: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val isLocked: Boolean = false,
    /** Stable user-facing rhythm lane. The segment is the placement; the task remains reusable. */
    val trackId: String = "focus",
)

data class PlanDraft(
    val generatedAt: LocalDateTime,
    val segments: List<PlannedSegment>,
    val pendingTaskIds: List<String>,
    val unscheduledTasks: List<UnscheduledTask>,
    /** User-adjustable order shown in the draft and persisted with a confirmed version. */
    val orderedTaskIds: List<String> = emptyList(),
    /** Local, explainable score associated with every task in orderedTaskIds. */
    val priorityAssessments: List<LocalPriorityAssessment> = emptyList(),
    val hasManualTaskOrder: Boolean = false,
    val sourceRevision: String? = null,
    val orderOnly: Boolean = false,
)

/** The explicit request/response boundary for the local planner and a future AI suggestion adapter. */
data class PlanGenerationInput(
    val tasks: List<Task>,
    val weeklyBlocks: List<WeeklyTimeBlock>,
    val dateOverrides: List<DateOverride>,
    val semesterFirstWeekMonday: LocalDate?,
    val lockedSegments: List<PlannedSegment> = emptyList(),
    val categoryPreferences: Map<TaskCategory, Int> = CategoryPreferences.defaults,
    val manualTaskOrder: List<String> = emptyList(),
)

interface PlanDraftGenerator {
    fun generate(input: PlanGenerationInput): PlanDraft
    fun generateReplan(input: PlanGenerationInput, current: ConfirmedPlan?): PlanDraft = generate(input)
}

/**
 * Shared local guard for generated or provider-proposed work segments.  It keeps
 * the advisor boundary from treating an apparently well-formed time range as
 * valid when it falls outside the user's confirmed availability or into a course,
 * rest, or date-specific block.
 */
object PlanningConstraintValidator {
    const val QUANTUM_MINUTES = 30

    fun hasExplicitAvailability(input: PlanGenerationInput): Boolean =
        input.weeklyBlocks.any { it.kind == TimeBlockKind.AVAILABLE } ||
            input.dateOverrides.any { it.type == DateOverrideType.AVAILABLE }

    fun supportsProposal(segment: AiProposedSegment, input: PlanGenerationInput): Boolean =
        hasExplicitAvailability(input) &&
            segment.startMinute in 0 until 24 * 60 &&
            segment.endMinute in 1..24 * 60 &&
            segment.startMinute < segment.endMinute &&
            segment.startMinute % QUANTUM_MINUTES == 0 &&
            segment.endMinute % QUANTUM_MINUTES == 0 &&
            (segment.startMinute until segment.endMinute step QUANTUM_MINUTES).all { minute ->
                isAvailable(
                    date = segment.date,
                    startMinute = minute,
                    endMinute = minute + QUANTUM_MINUTES,
                    weeklyBlocks = input.weeklyBlocks,
                    dateOverrides = input.dateOverrides,
                    semesterFirstWeekMonday = input.semesterFirstWeekMonday,
                )
            }

    fun isAvailable(
        date: LocalDate,
        startMinute: Int,
        endMinute: Int,
        weeklyBlocks: List<WeeklyTimeBlock>,
        dateOverrides: List<DateOverride>,
        semesterFirstWeekMonday: LocalDate?,
    ): Boolean {
        val oneDayBlocked = dateOverrides.any { override ->
            override.date == date &&
                override.type == DateOverrideType.BLOCKED &&
                overlaps(override.startMinute, override.endMinute, startMinute, endMinute)
        }
        if (oneDayBlocked) return false

        val availableOverride = dateOverrides.any { override ->
            override.date == date &&
                override.type == DateOverrideType.AVAILABLE &&
                covers(override.startMinute, override.endMinute, startMinute, endMinute)
        }
        val weeklyBlocked = weeklyBlocks.any { block ->
            block.kind != TimeBlockKind.AVAILABLE && block.dayOfWeek == date.dayOfWeek &&
                CourseWeekPattern.appliesOn(block.weekPattern, date, semesterFirstWeekMonday) &&
                overlaps(block.startMinute, block.endMinute, startMinute, endMinute)
        }
        if (weeklyBlocked) return false
        if (availableOverride) return true
        val weeklyAvailable = weeklyBlocks.any { block ->
            block.kind == TimeBlockKind.AVAILABLE &&
                block.dayOfWeek == date.dayOfWeek &&
                CourseWeekPattern.appliesOn(block.weekPattern, date, semesterFirstWeekMonday) &&
                covers(block.startMinute, block.endMinute, startMinute, endMinute)
        }
        if (!weeklyAvailable) return false
        return weeklyBlocks.none { block ->
            block.kind != TimeBlockKind.AVAILABLE &&
                block.dayOfWeek == date.dayOfWeek &&
                CourseWeekPattern.appliesOn(block.weekPattern, date, semesterFirstWeekMonday) &&
                overlaps(block.startMinute, block.endMinute, startMinute, endMinute)
        }
    }

    private fun covers(startMinute: Int, endMinute: Int, candidateStart: Int, candidateEnd: Int): Boolean =
        startMinute <= candidateStart && endMinute >= candidateEnd

    private fun overlaps(startMinute: Int, endMinute: Int, candidateStart: Int, candidateEnd: Int): Boolean =
        startMinute < candidateEnd && endMinute > candidateStart
}

data class UnscheduledTask(
    val taskId: String,
    val remainingMinutes: Int,
    val reason: UnscheduledReason,
)

data class ConfirmedPlan(
    val id: String,
    val createdAtEpochMillis: Long,
    val isCurrent: Boolean,
    val segments: List<PlannedSegment>,
    val orderedTaskIds: List<String> = emptyList(),
    val hasManualTaskOrder: Boolean = false,
)

/**
 * User-owned draft edits. These operations never touch a confirmed plan; that only
 * happens after the user explicitly accepts the edited draft.
 */
object PlanDraftEditor {
    const val QUANTUM_MINUTES = 30

    fun moveTask(draft: PlanDraft, taskId: String, offset: Int): PlanDraft {
        val fromIndex = draft.orderedTaskIds.indexOf(taskId)
        if (fromIndex == -1) return draft
        val toIndex = (fromIndex + offset).coerceIn(0, draft.orderedTaskIds.lastIndex)
        if (fromIndex == toIndex) return draft
        return draft.copy(
            orderedTaskIds = draft.orderedTaskIds.toMutableList().also { order ->
                val moved = order.removeAt(fromIndex)
                order.add(toIndex, moved)
            },
            hasManualTaskOrder = true,
        )
    }

    fun moveSegment(draft: PlanDraft, updated: PlannedSegment): PlanDraft {
        require(isValidSegment(updated)) { "A plan segment must use a valid 30-minute time range." }
        require(draft.segments.any { it.id == updated.id }) { "The plan segment does not belong to this draft." }
        return draft.copy(segments = draft.segments.map { segment ->
            if (segment.id == updated.id) updated else segment
        }.chronological())
    }

    fun splitSegment(draft: PlanDraft, segmentId: String): PlanDraft {
        val segment = requireSegment(draft, segmentId)
        require(canSplit(segment)) { "A segment must be at least one hour to split." }
        val midpoint = segment.startMinute +
            ((segment.endMinute - segment.startMinute) / 2 / QUANTUM_MINUTES) * QUANTUM_MINUTES
        val first = segment.copy(endMinute = midpoint)
        val second = segment.copy(
            id = UUID.randomUUID().toString(),
            startMinute = midpoint,
        )
        return draft.copy(
            segments = draft.segments.map { current -> if (current.id == segmentId) first else current } + second,
        ).withChronologicalSegments()
    }

    fun mergeWithAdjacentSegment(draft: PlanDraft, segmentId: String): PlanDraft {
        val segment = requireSegment(draft, segmentId)
        val adjacent = draft.segments.firstOrNull { candidate ->
            candidate.id != segment.id &&
                candidate.taskId == segment.taskId &&
                candidate.date == segment.date &&
                candidate.isLocked == segment.isLocked &&
                candidate.trackId == segment.trackId &&
                (candidate.endMinute == segment.startMinute || candidate.startMinute == segment.endMinute)
        } ?: return draft
        val merged = segment.copy(
            startMinute = minOf(segment.startMinute, adjacent.startMinute),
            endMinute = maxOf(segment.endMinute, adjacent.endMinute),
        )
        return draft.copy(
            segments = draft.segments
                .filterNot { it.id == segment.id || it.id == adjacent.id } + merged,
        ).withChronologicalSegments()
    }

    fun setSegmentLocked(draft: PlanDraft, segmentId: String, isLocked: Boolean): PlanDraft =
        draft.copy(segments = draft.segments.map { segment ->
            if (segment.id == segmentId) segment.copy(isLocked = isLocked) else segment
        })

    fun canSplit(segment: PlannedSegment): Boolean =
        isValidSegment(segment) && segment.endMinute - segment.startMinute >= QUANTUM_MINUTES * 2

    fun canMergeWithAdjacentSegment(draft: PlanDraft, segmentId: String): Boolean {
        val segment = draft.segments.firstOrNull { it.id == segmentId } ?: return false
        return draft.segments.any { candidate ->
            candidate.id != segment.id &&
                candidate.taskId == segment.taskId &&
                candidate.date == segment.date &&
                candidate.isLocked == segment.isLocked &&
                candidate.trackId == segment.trackId &&
                (candidate.endMinute == segment.startMinute || candidate.startMinute == segment.endMinute)
        }
    }

    fun overlapsAnotherSegment(draft: PlanDraft, candidate: PlannedSegment): Boolean =
        draft.segments.any { existing ->
            existing.id != candidate.id &&
                existing.date == candidate.date &&
                existing.trackId == candidate.trackId &&
                existing.startMinute < candidate.endMinute &&
                existing.endMinute > candidate.startMinute
        }

    fun isValidSegment(segment: PlannedSegment): Boolean =
        segment.startMinute in 0 until 24 * 60 &&
            segment.endMinute in 1..24 * 60 &&
            segment.startMinute < segment.endMinute &&
            segment.startMinute % QUANTUM_MINUTES == 0 &&
            segment.endMinute % QUANTUM_MINUTES == 0

    private fun requireSegment(draft: PlanDraft, segmentId: String): PlannedSegment =
        requireNotNull(draft.segments.firstOrNull { it.id == segmentId }) {
            "The plan segment does not belong to this draft."
        }

    private fun PlanDraft.withChronologicalSegments(): PlanDraft =
        copy(segments = segments.chronological())

    private fun List<PlannedSegment>.chronological(): List<PlannedSegment> =
        sortedWith(compareBy(PlannedSegment::date, PlannedSegment::startMinute, PlannedSegment::endMinute))
}

/** A transparent, fixed-rule scheduler used until an AI suggestion service is introduced. */
object PlanGenerator : PlanDraftGenerator {
    private const val HORIZON_DAYS = 30
    private const val QUANTUM_MINUTES = 30
    private const val DAY_START_MINUTE = 8 * 60
    private const val DAY_END_MINUTE = 22 * 60

    /** Rebuild only the unprotected remainder of today, keeping all other placements. */
    fun generateToday(input: PlanGenerationInput, current: ConfirmedPlan?, clock: Clock = Clock.systemDefaultZone()): PlanDraft {
        return generateRemaining(input, current, clock, todayOnly = true)
    }

    override fun generateReplan(input: PlanGenerationInput, current: ConfirmedPlan?) = generateRemaining(input, current)

    /** Past placements are history, not proof of progress and not deductions from confirmed remaining work. */
    fun generateRemaining(input: PlanGenerationInput, current: ConfirmedPlan?, clock: Clock = Clock.systemDefaultZone(), todayOnly: Boolean = false): PlanDraft {
        val now = LocalDateTime.now(clock)
        val day = now.toLocalDate()
        val minute = now.hour * 60 + now.minute
        val activeIds = input.tasks.filter { it.status.isActive }.map { it.id }.toSet()
        fun ended(s: PlannedSegment) = s.date < day || (s.date == day && s.endMinute <= minute)
        val hasAvailability = PlanningConstraintValidator.hasExplicitAvailability(input)
        val history = current?.segments.orEmpty().filter(::ended)
        val retained = current?.segments.orEmpty().filter { s -> !ended(s) && s.taskId in activeIds &&
            (!hasAvailability || s.isLocked || (s.date == day && s.startMinute <= minute) || (todayOnly && s.date != day)) }
        val effectiveLocks = (retained + input.lockedSegments.filter { !ended(it) && it.taskId in activeIds })
            .distinctBy { listOf(it.taskId, it.date, it.startMinute, it.endMinute, it.trackId) }
        val generated = generate(input.tasks, input.weeklyBlocks, input.dateOverrides, input.semesterFirstWeekMonday,
            effectiveLocks.map { it.copy(isLocked = true) }, input.categoryPreferences, input.manualTaskOrder,
            clock, horizonDays = if (todayOnly) 1 else HORIZON_DAYS)
        val restored = generated.segments.map { s -> effectiveLocks.firstOrNull {
            it.taskId == s.taskId && it.date == s.date && it.startMinute == s.startMinute && it.endMinute == s.endMinute && it.trackId == s.trackId
        } ?: s }
        return generated.copy(segments = (history + restored).sortedWith(compareBy(PlannedSegment::date, PlannedSegment::startMinute)))
    }

    override fun generate(input: PlanGenerationInput): PlanDraft = generate(
        tasks = input.tasks,
        weeklyBlocks = input.weeklyBlocks,
        dateOverrides = input.dateOverrides,
        semesterFirstWeekMonday = input.semesterFirstWeekMonday,
        lockedSegments = input.lockedSegments,
        categoryPreferences = input.categoryPreferences,
        manualTaskOrder = input.manualTaskOrder,
    )

    fun generate(
        tasks: List<Task>,
        weeklyBlocks: List<WeeklyTimeBlock>,
        dateOverrides: List<DateOverride>,
        semesterFirstWeekMonday: LocalDate? = null,
        lockedSegments: List<PlannedSegment> = emptyList(),
        categoryPreferences: Map<TaskCategory, Int> = CategoryPreferences.defaults,
        manualTaskOrder: List<String> = emptyList(),
        clock: Clock = Clock.systemDefaultZone(),
        horizonDays: Int = HORIZON_DAYS,
    ): PlanDraft {
        require(horizonDays in 1..HORIZON_DAYS)
        val now = LocalDateTime.now(clock)
        val activeTasks = tasks.filter { it.status.isActive }
        val activeTaskIds = activeTasks.mapTo(mutableSetOf(), Task::id)
        val preservedLocks = lockedSegments.filter { segment ->
            segment.isLocked && segment.taskId in activeTaskIds
        }
        fun rankActiveTasks(): List<LocalPriorityAssessment> = LocalPriorityRanker.rank(
            tasks = activeTasks,
            categoryPreferences = categoryPreferences,
            today = now.toLocalDate(),
            manualTaskOrder = manualTaskOrder,
        )
        if (!PlanningConstraintValidator.hasExplicitAvailability(
                PlanGenerationInput(
                    tasks = tasks,
                    weeklyBlocks = weeklyBlocks,
                    dateOverrides = dateOverrides,
                    semesterFirstWeekMonday = semesterFirstWeekMonday,
                    lockedSegments = lockedSegments,
                    categoryPreferences = categoryPreferences,
                    manualTaskOrder = manualTaskOrder,
                ),
            )
        ) {
            val priorityAssessments = rankActiveTasks()
            return PlanDraft(
                generatedAt = now,
                segments = preservedLocks,
                pendingTaskIds = activeTasks.map(Task::id),
                unscheduledTasks = emptyList(),
                orderedTaskIds = priorityAssessments.map(LocalPriorityAssessment::taskId),
                priorityAssessments = priorityAssessments,
                hasManualTaskOrder = manualTaskOrder.isNotEmpty(),
            )
        }
        // Hard constraints produce the candidate slot set before any soft ranking occurs.
        val allSlots = freeSlots(now, weeklyBlocks, dateOverrides, semesterFirstWeekMonday, horizonDays)
        val assignedSlots = allSlots.filterTo(mutableSetOf()) { slot ->
            preservedLocks.any { locked -> locked.covers(slot) }
        }
        val lockedMinutesByTask = preservedLocks.groupingBy(PlannedSegment::taskId)
            .fold(0) { total, segment -> total + segment.endMinute - segment.startMinute }
        val totalRequiredSlots = activeTasks.sumOf { task ->
            val remainingMinutes = remainingWorkMinutes(task) - (lockedMinutesByTask[task.id] ?: 0)
            ceil(remainingMinutes.coerceAtLeast(0) / QUANTUM_MINUTES.toDouble()).toInt()
        }
        val hasOverallCapacityShortfall = totalRequiredSlots > allSlots.count { it !in assignedSlots }
        val priorityAssessments = rankActiveTasks()
        val tasksById = activeTasks.associateBy(Task::id)
        val orderedActiveTasks = priorityAssessments.map { assessment -> tasksById.getValue(assessment.taskId) }
        val segments = preservedLocks.toMutableList()
        val unscheduled = mutableListOf<UnscheduledTask>()
        val schedulableTasks = orderedActiveTasks.filter { it.totalDurationMinutes != null }

        schedulableTasks.forEach { task ->
            val remainingMinutes = (remainingWorkMinutes(task) - (lockedMinutesByTask[task.id] ?: 0))
                .coerceAtLeast(0)
            val requiredSlots = ceil(remainingMinutes / QUANTUM_MINUTES.toDouble()).toInt()
            val cutoff = task.dueDate ?: now.toLocalDate().plusDays(horizonDays - 1L)
            val selected = allSlots.asSequence()
                .filter { slot -> slot !in assignedSlots && !slot.date.isAfter(cutoff) }
                .take(requiredSlots)
                .toList()
            assignedSlots += selected
            segments += mergeSegments(task.id, selected)
            if (selected.size < requiredSlots) {
                unscheduled += UnscheduledTask(
                    taskId = task.id,
                    remainingMinutes = (requiredSlots - selected.size) * QUANTUM_MINUTES,
                    reason = if (hasOverallCapacityShortfall) {
                        UnscheduledReason.TOTAL_CAPACITY_IN_ROLLING_WINDOW
                    } else if (task.dueDate == null) {
                        UnscheduledReason.CAPACITY_IN_ROLLING_WINDOW
                    } else {
                        UnscheduledReason.CAPACITY_BEFORE_DUE_DATE
                    },
                )
            }
        }

        return PlanDraft(
            generatedAt = now,
            segments = segments.sortedWith(
                compareBy(PlannedSegment::date, PlannedSegment::startMinute, PlannedSegment::endMinute)
                    .thenBy(PlannedSegment::taskId)
                    .thenBy(PlannedSegment::id),
            ),
            pendingTaskIds = activeTasks.filter { it.totalDurationMinutes == null }.map(Task::id),
            unscheduledTasks = unscheduled,
            orderedTaskIds = orderedActiveTasks.map(Task::id),
            priorityAssessments = priorityAssessments,
            hasManualTaskOrder = manualTaskOrder.isNotEmpty(),
        )
    }

    private fun freeSlots(
        now: LocalDateTime,
        weeklyBlocks: List<WeeklyTimeBlock>,
        dateOverrides: List<DateOverride>,
        semesterFirstWeekMonday: LocalDate?,
        horizonDays: Int,
    ): List<Slot> = buildList {
        repeat(horizonDays) { dayOffset ->
            val date = now.toLocalDate().plusDays(dayOffset.toLong())
            val firstMinute = if (dayOffset == 0) {
                maxOf(DAY_START_MINUTE, roundUpToQuantum(now.hour * 60 + now.minute))
            } else {
                DAY_START_MINUTE
            }
            for (minute in firstMinute until DAY_END_MINUTE step QUANTUM_MINUTES) {
                val slot = Slot(date, minute, minute + QUANTUM_MINUTES)
                if (
                    PlanningConstraintValidator.isAvailable(
                        date = slot.date,
                        startMinute = slot.startMinute,
                        endMinute = slot.endMinute,
                        weeklyBlocks = weeklyBlocks,
                        dateOverrides = dateOverrides,
                        semesterFirstWeekMonday = semesterFirstWeekMonday,
                    )
                ) add(slot)
            }
        }
    }

    private fun mergeSegments(taskId: String, slots: List<Slot>): List<PlannedSegment> {
        if (slots.isEmpty()) return emptyList()
        val result = mutableListOf<PlannedSegment>()
        var current = slots.first()
        slots.drop(1).forEach { slot ->
            if (slot.date == current.date && slot.startMinute == current.endMinute) {
                current = current.copy(endMinute = slot.endMinute)
            } else {
                result += current.toSegment(taskId)
                current = slot
            }
        }
        result += current.toSegment(taskId)
        return result
    }

    private fun roundUpToQuantum(minute: Int): Int =
        ((minute + QUANTUM_MINUTES - 1) / QUANTUM_MINUTES) * QUANTUM_MINUTES

    /** Only user-confirmed progress changes the amount scheduled; it never changes task status. */
    private fun remainingWorkMinutes(task: Task): Int {
        val totalMinutes = task.totalDurationMinutes ?: return 0
        // A raw 100% feedback value is not a status transition. Only an explicitly
        // confirmed COMPLETED task leaves active planning, so an active task is never
        // silently treated as done by the planner.
        val progress = task.progressPercent?.takeIf { it in 1..99 } ?: return totalMinutes
        return ceil(totalMinutes * (100 - progress) / 100.0).toInt()
    }

    private data class Slot(
        val date: LocalDate,
        val startMinute: Int,
        val endMinute: Int,
    ) {
        fun toSegment(taskId: String): PlannedSegment = PlannedSegment(
            id = "draft:$taskId:${date.toEpochDay()}:$startMinute:$endMinute",
            taskId = taskId,
            date = date,
            startMinute = startMinute,
            endMinute = endMinute,
        )
    }

    private fun PlannedSegment.covers(slot: Slot): Boolean =
        date == slot.date && startMinute <= slot.startMinute && endMinute >= slot.endMinute
}
