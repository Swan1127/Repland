package com.swan1127.repland

import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.LocalPriorityRanker
import com.swan1127.repland.domain.model.PlanGenerator
import com.swan1127.repland.domain.model.PriorityReasonKind
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.UnscheduledReason
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPriorityRankerTest {
    private val today = LocalDate.of(2026, 9, 14)

    @Test
    fun `same inputs rank identically regardless of incoming list order`() {
        val tasks = listOf(
            task("b", TaskCategory.OFFICE, TaskPriority.HIGH, today.plusDays(3)),
            task("a", TaskCategory.OFFICE, TaskPriority.HIGH, today.plusDays(3)),
        )

        val first = LocalPriorityRanker.rank(tasks, today = today)
        val second = LocalPriorityRanker.rank(tasks.reversed(), today = today)

        assertEquals(first, second)
        assertEquals(listOf("a", "b"), first.map { it.taskId })
    }

    @Test
    fun `overdue deadline and postponement evidence raise local rank with explanations`() {
        val overdue = task("overdue", TaskCategory.COURSE, TaskPriority.MEDIUM, today.minusDays(2), postpones = 1)
        val later = task("later", TaskCategory.COURSE, TaskPriority.MEDIUM, today.plusDays(10))

        val ranked = LocalPriorityRanker.rank(listOf(later, overdue), today = today)
        val first = ranked.first()

        assertEquals("overdue", first.taskId)
        assertTrue(first.reasons.any { it.kind == PriorityReasonKind.OVERDUE })
        assertTrue(first.reasons.any { it.kind == PriorityReasonKind.POSTPONEMENTS })
        assertTrue(first.reasons.any { it.kind == PriorityReasonKind.LOCAL_AI_NEUTRAL })
    }

    @Test
    fun `category preferences affect otherwise equal ranking without modifying initial priority`() {
        val course = task("course", TaskCategory.COURSE, TaskPriority.MEDIUM, null)
        val leisure = task("leisure", TaskCategory.LEISURE, TaskPriority.MEDIUM, null)
        val preferences = CategoryPreferences.defaults +
            (TaskCategory.COURSE to 10) + (TaskCategory.LEISURE to 100)

        val ranked = LocalPriorityRanker.rank(listOf(course, leisure), preferences, today)

        assertEquals(listOf("leisure", "course"), ranked.map { it.taskId })
        assertEquals(TaskPriority.MEDIUM, course.userPriority)
        assertEquals(TaskPriority.MEDIUM, leisure.userPriority)
    }

    @Test
    fun `manual draft order takes precedence over automatic local rank`() {
        val required = task("required", TaskCategory.COURSE, TaskPriority.REQUIRED, today)
        val low = task("low", TaskCategory.COURSE, TaskPriority.LOW, today.plusDays(10))

        val ranked = LocalPriorityRanker.rank(
            tasks = listOf(required, low),
            today = today,
            manualTaskOrder = listOf("low", "required"),
        )

        assertEquals(listOf("low", "required"), ranked.map { it.taskId })
    }

    @Test
    fun `insufficient capacity retains the task with a concrete explanation`() {
        val plan = PlanGenerator.generate(
            tasks = listOf(task("task", TaskCategory.COURSE, TaskPriority.HIGH, today, duration = 180)),
            weeklyBlocks = listOf(
                WeeklyTimeBlock(
                    id = "available",
                    title = "可用",
                    kind = TimeBlockKind.AVAILABLE,
                    dayOfWeek = DayOfWeek.MONDAY,
                    startMinute = 8 * 60,
                    endMinute = 8 * 60 + 30,
                    weekPattern = null,
                    createdAtEpochMillis = 1,
                    updatedAtEpochMillis = 1,
                ),
            ),
            dateOverrides = emptyList(),
            clock = Clock.fixed(Instant.parse("2026-09-14T06:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(1, plan.segments.size)
        assertEquals(150, plan.unscheduledTasks.single().remainingMinutes)
        assertEquals(UnscheduledReason.TOTAL_CAPACITY_IN_ROLLING_WINDOW, plan.unscheduledTasks.single().reason)
    }

    @Test
    fun `planner output is identical for identical inputs`() {
        val task = task("stable", TaskCategory.COURSE, TaskPriority.HIGH, today, duration = 30)
        val available = WeeklyTimeBlock(
            id = "available-stable",
            title = "可用",
            kind = TimeBlockKind.AVAILABLE,
            dayOfWeek = DayOfWeek.MONDAY,
            startMinute = 8 * 60,
            endMinute = 9 * 60,
            weekPattern = null,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )
        val clock = Clock.fixed(Instant.parse("2026-09-14T06:00:00Z"), ZoneOffset.UTC)

        val first = PlanGenerator.generate(listOf(task), listOf(available), emptyList(), clock = clock)
        val second = PlanGenerator.generate(listOf(task), listOf(available), emptyList(), clock = clock)

        assertEquals(first, second)
    }

    @Test
    fun `confirmed partial progress schedules only the remaining work`() {
        val task = task(
            "partial",
            TaskCategory.COURSE,
            TaskPriority.HIGH,
            today,
            duration = 100,
            progress = 40,
        )
        val available = WeeklyTimeBlock(
            id = "available-partial",
            title = "可用",
            kind = TimeBlockKind.AVAILABLE,
            dayOfWeek = DayOfWeek.MONDAY,
            startMinute = 8 * 60,
            endMinute = 12 * 60,
            weekPattern = null,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )

        val plan = PlanGenerator.generate(
            listOf(task),
            listOf(available),
            emptyList(),
            clock = Clock.fixed(Instant.parse("2026-09-14T06:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(60, plan.segments.single().endMinute - plan.segments.single().startMinute)
        assertTrue(plan.unscheduledTasks.isEmpty())
    }

    @Test
    fun `unconfirmed one hundred percent feedback does not silently remove active work`() {
        val task = task(
            "unconfirmed-feedback",
            TaskCategory.COURSE,
            TaskPriority.HIGH,
            today,
            duration = 90,
            progress = 100,
        )
        val available = WeeklyTimeBlock(
            id = "available-feedback",
            title = "可用",
            kind = TimeBlockKind.AVAILABLE,
            dayOfWeek = DayOfWeek.MONDAY,
            startMinute = 8 * 60,
            endMinute = 10 * 60,
            weekPattern = null,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )

        val plan = PlanGenerator.generate(
            listOf(task),
            listOf(available),
            emptyList(),
            clock = Clock.fixed(Instant.parse("2026-09-14T06:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(90, plan.segments.sumOf { it.endMinute - it.startMinute })
    }

    private fun task(
        id: String,
        category: TaskCategory,
        priority: TaskPriority,
        dueDate: LocalDate?,
        postpones: Int = 0,
        duration: Int? = 30,
        progress: Int? = null,
    ) = Task(
        id = id,
        description = "",
        displayName = id,
        category = category,
        userPriority = priority,
        estimatedDays = 1,
        totalDurationMinutes = duration,
        dueDate = dueDate,
        status = TaskStatus.NOT_STARTED,
        completionSummary = null,
        actualDurationMinutes = null,
        progressPercent = progress,
        postponeCount = postpones,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
