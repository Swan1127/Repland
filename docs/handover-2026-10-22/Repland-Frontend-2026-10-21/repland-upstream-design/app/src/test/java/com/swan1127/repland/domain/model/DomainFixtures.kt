package com.swan1127.repland.domain.model

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Shared builders for the pure-domain unit tests.
 *
 * 2026-03-02 is a Monday (verified with `date -d 2026-03-02 +%A`), which keeps every
 * weekday-dependent expectation readable and independent of the machine's locale.
 */
val TEST_MONDAY: LocalDate = LocalDate.of(2026, 3, 2)

/**
 * 07:00 UTC on [TEST_MONDAY]. Deliberately before the planner's 08:00 day start so that
 * "day 0" begins at a predictable 08:00 instead of at the wall clock.
 */
val TEST_CLOCK: Clock = Clock.fixed(Instant.parse("2026-03-02T07:00:00Z"), ZoneOffset.UTC)

fun task(
    id: String,
    durationMinutes: Int? = 60,
    dueDate: LocalDate? = null,
    status: TaskStatus = TaskStatus.NOT_STARTED,
    priority: TaskPriority = TaskPriority.MEDIUM,
    category: TaskCategory = TaskCategory.COURSE,
    progressPercent: Int? = null,
    postponeCount: Int = 0,
    createdAtEpochMillis: Long = 1_000L,
    scheduledForDate: LocalDate? = null,
    repeatRule: RepeatRule? = null,
): Task = Task(
    id = id,
    description = "description of $id",
    displayName = "Task $id",
    category = category,
    userPriority = priority,
    estimatedDays = 1,
    totalDurationMinutes = durationMinutes,
    dueDate = dueDate,
    status = status,
    completionSummary = null,
    actualDurationMinutes = null,
    progressPercent = progressPercent,
    postponeCount = postponeCount,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = createdAtEpochMillis,
    scheduledForDate = scheduledForDate,
    repeatRule = repeatRule,
)

fun weeklyBlock(
    id: String = "block-1",
    /**
     * Null keeps the original `block <id>` placeholder. Callers that care about the text — the
     * exporter, which round-trips it through a spreadsheet — pass their own.
     */
    title: String? = null,
    kind: TimeBlockKind = TimeBlockKind.AVAILABLE,
    dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    startMinute: Int,
    endMinute: Int,
    weekPattern: String? = null,
    trackId: String = "course",
    note: String? = null,
): WeeklyTimeBlock = WeeklyTimeBlock(
    id = id,
    title = title ?: "block $id",
    kind = kind,
    dayOfWeek = dayOfWeek,
    startMinute = startMinute,
    endMinute = endMinute,
    weekPattern = weekPattern,
    createdAtEpochMillis = 1_000L,
    updatedAtEpochMillis = 1_000L,
    trackId = trackId,
    note = note,
)

fun dateOverride(
    id: String = "override-1",
    type: DateOverrideType,
    date: LocalDate,
    startMinute: Int,
    endMinute: Int,
    note: String? = null,
): DateOverride = DateOverride(
    id = id,
    title = "override $id",
    type = type,
    date = date,
    startMinute = startMinute,
    endMinute = endMinute,
    createdAtEpochMillis = 1_000L,
    updatedAtEpochMillis = 1_000L,
    note = note,
)

fun planInput(
    tasks: List<Task>,
    weeklyBlocks: List<WeeklyTimeBlock> = emptyList(),
    dateOverrides: List<DateOverride> = emptyList(),
    semesterFirstWeekMonday: LocalDate? = null,
    lockedSegments: List<PlannedSegment> = emptyList(),
    categoryPreferences: Map<TaskCategory, Int> = CategoryPreferences.defaults,
    manualTaskOrder: List<String> = emptyList(),
): PlanGenerationInput = PlanGenerationInput(
    tasks = tasks,
    weeklyBlocks = weeklyBlocks,
    dateOverrides = dateOverrides,
    semesterFirstWeekMonday = semesterFirstWeekMonday,
    lockedSegments = lockedSegments,
    categoryPreferences = categoryPreferences,
    manualTaskOrder = manualTaskOrder,
)

fun segment(
    id: String,
    taskId: String = "t1",
    date: LocalDate = TEST_MONDAY,
    startMinute: Int,
    endMinute: Int,
    isLocked: Boolean = false,
    trackId: String = "focus",
): PlannedSegment = PlannedSegment(
    id = id,
    taskId = taskId,
    date = date,
    startMinute = startMinute,
    endMinute = endMinute,
    isLocked = isLocked,
    trackId = trackId,
)

fun planDraft(
    segments: List<PlannedSegment> = emptyList(),
    orderedTaskIds: List<String> = emptyList(),
    hasManualTaskOrder: Boolean = false,
): PlanDraft = PlanDraft(
    generatedAt = LocalDateTime.of(2026, 3, 2, 7, 0),
    segments = segments,
    pendingTaskIds = emptyList(),
    unscheduledTasks = emptyList(),
    orderedTaskIds = orderedTaskIds,
    priorityAssessments = emptyList(),
    hasManualTaskOrder = hasManualTaskOrder,
)

fun confirmedPlan(
    id: String = "plan-1",
    segments: List<PlannedSegment> = emptyList(),
): ConfirmedPlan = ConfirmedPlan(
    id = id,
    createdAtEpochMillis = 1_000L,
    isCurrent = true,
    segments = segments,
    orderedTaskIds = emptyList(),
    hasManualTaskOrder = false,
)

fun feedback(
    actualDurationMinutes: Int? = null,
    progressPercent: Int? = null,
    completedContent: String? = null,
    completionResult: String? = null,
    postponeReason: String? = null,
): TaskFeedback = TaskFeedback(
    actualDurationMinutes = actualDurationMinutes,
    progressPercent = progressPercent,
    completedContent = completedContent,
    completionResult = completionResult,
    postponeReason = postponeReason,
)

fun executionLog(
    id: String,
    taskId: String,
    eventType: ExecutionLogEventType = ExecutionLogEventType.FEEDBACK,
    confirmedStatus: TaskStatus = TaskStatus.IN_PROGRESS,
    feedback: TaskFeedback = TaskFeedback(),
    correctedLogId: String? = null,
    replacementTaskId: String? = null,
    createdAtEpochMillis: Long = 1_000L,
): TaskExecutionLog = TaskExecutionLog(
    id = id,
    taskId = taskId,
    eventType = eventType,
    confirmedStatus = confirmedStatus,
    feedback = feedback,
    correctedLogId = correctedLogId,
    replacementTaskId = replacementTaskId,
    createdAtEpochMillis = createdAtEpochMillis,
)
