package com.swan1127.repland.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

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
) = Task(
    id, "description of $id", "Task $id", category, priority, 1, durationMinutes, dueDate, status,
    null, null, progressPercent, postponeCount, createdAtEpochMillis, createdAtEpochMillis,
    scheduledForDate = scheduledForDate,
)

fun weeklyBlock(
    id: String = "block-1",
    title: String? = null,
    kind: TimeBlockKind = TimeBlockKind.AVAILABLE,
    dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    startMinute: Int,
    endMinute: Int,
    weekPattern: String? = null,
    trackId: String = "course",
    note: String? = null,
) = WeeklyTimeBlock(id, title ?: "block $id", kind, dayOfWeek, startMinute, endMinute, weekPattern, 1_000L, 1_000L, trackId, note)

fun planInput(
    tasks: List<Task>,
    weeklyBlocks: List<WeeklyTimeBlock> = emptyList(),
    dateOverrides: List<DateOverride> = emptyList(),
    semesterFirstWeekMonday: LocalDate? = null,
    lockedSegments: List<PlannedSegment> = emptyList(),
    categoryPreferences: Map<TaskCategory, Int> = CategoryPreferences.defaults,
    manualTaskOrder: List<String> = emptyList(),
) = PlanGenerationInput(tasks, weeklyBlocks, dateOverrides, semesterFirstWeekMonday, lockedSegments, categoryPreferences, manualTaskOrder)

fun confirmedPlan(id: String = "plan-1", segments: List<PlannedSegment> = emptyList()) =
    ConfirmedPlan(id, 1_000L, true, segments)
