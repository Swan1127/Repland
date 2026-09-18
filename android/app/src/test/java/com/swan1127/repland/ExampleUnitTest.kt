package com.swan1127.repland

import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskDraftValidator
import com.swan1127.repland.domain.model.TaskName
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.ClassPeriodClock
import com.swan1127.repland.domain.model.CourseWeekPattern
import com.swan1127.repland.domain.model.ImportedCourse
import com.swan1127.repland.domain.model.PlanGenerator
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.TimeBlockValidator
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDraftValidatorTest {
    @Test
    fun `description becomes a compact display name`() {
        assertEquals("整理课程笔记", TaskName.fromDescription("  整理课程笔记\n补全例题  "))
    }

    @Test
    fun `duration remains optional but provided duration must be positive`() {
        val base = TaskDraft(
            displayName = "完成练习",
            description = "完成练习",
            category = TaskCategory.COURSE,
            userPriority = TaskPriority.MEDIUM,
            estimatedDays = 1,
            totalDurationMinutes = null,
            dueDate = LocalDate.now(),
        )

        assertTrue(TaskDraftValidator.isValid(base))
        assertFalse(TaskDraftValidator.isValid(base.copy(totalDurationMinutes = 0)))
    }

    @Test
    fun `weekly time block requires a valid non-empty time range`() {
        val valid = WeeklyTimeBlockDraft(
            title = "高等数学",
            kind = TimeBlockKind.COURSE,
            dayOfWeek = DayOfWeek.MONDAY,
            startMinute = TimeBlockValidator.parseTime("09:00")!!,
            endMinute = TimeBlockValidator.parseTime("10:30")!!,
        )

        assertTrue(TimeBlockValidator.isValid(valid))
        assertFalse(TimeBlockValidator.isValid(valid.copy(endMinute = valid.startMinute)))
        assertEquals(null, TimeBlockValidator.parseTime("25:00"))
    }

    @Test
    fun `imported course uses the documented default period clock`() {
        val draft = ClassPeriodClock.toWeeklyTimeBlockDraft(
            ImportedCourse(
                id = "course-1",
                title = "数据结构与算法",
                dayOfWeek = DayOfWeek.MONDAY,
                startPeriod = 3,
                endPeriod = 4,
                weekPattern = "1-12周",
            ),
        )

        requireNotNull(draft)
        assertEquals(10 * 60, draft.startMinute)
        assertEquals(11 * 60 + 35, draft.endMinute)
        assertEquals("1-12周", draft.weekPattern)
    }

    @Test
    fun `course week pattern recognizes ranges and alternating weeks`() {
        val firstWeekMonday = LocalDate.of(2026, 9, 7)

        assertFalse(
            CourseWeekPattern.appliesOn("3-14周", LocalDate.of(2026, 9, 14), firstWeekMonday),
        )
        assertTrue(
            CourseWeekPattern.appliesOn("3-14周", LocalDate.of(2026, 9, 21), firstWeekMonday),
        )
        assertTrue(
            CourseWeekPattern.appliesOn("1-16周（单）", LocalDate.of(2026, 9, 21), firstWeekMonday),
        )
        assertFalse(
            CourseWeekPattern.appliesOn("1-16周（单）", LocalDate.of(2026, 9, 28), firstWeekMonday),
        )
    }

    @Test
    fun `plan skips fixed class time and rounds duration to planning quantum`() {
        val task = Task(
            id = "task-1",
            description = "",
            displayName = "复习数据结构",
            category = TaskCategory.COURSE,
            userPriority = TaskPriority.HIGH,
            estimatedDays = 1,
            totalDurationMinutes = 45,
            dueDate = LocalDate.of(2026, 9, 14),
            status = TaskStatus.NOT_STARTED,
            completionSummary = null,
            actualDurationMinutes = null,
            progressPercent = null,
            postponeCount = 0,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        val classBlock = WeeklyTimeBlock(
            id = "class-1",
            title = "课程",
            kind = TimeBlockKind.COURSE,
            dayOfWeek = DayOfWeek.MONDAY,
            startMinute = 8 * 60,
            endMinute = 10 * 60,
            weekPattern = null,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        val availabilityBlock = WeeklyTimeBlock(
            id = "availability-1",
            title = "可用时间",
            kind = TimeBlockKind.AVAILABLE,
            dayOfWeek = DayOfWeek.MONDAY,
            startMinute = 8 * 60,
            endMinute = 22 * 60,
            weekPattern = null,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

        val plan = PlanGenerator.generate(
            tasks = listOf(task),
            weeklyBlocks = listOf(availabilityBlock, classBlock),
            dateOverrides = emptyList(),
            clock = Clock.fixed(Instant.parse("2026-09-14T06:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(1, plan.segments.size)
        assertEquals(10 * 60, plan.segments.single().startMinute)
        assertEquals(11 * 60, plan.segments.single().endMinute)
    }

    @Test
    fun `plan releases a course outside its configured teaching weeks`() {
        val task = Task(
            id = "task-2",
            description = "",
            displayName = "复习数据结构",
            category = TaskCategory.COURSE,
            userPriority = TaskPriority.HIGH,
            estimatedDays = 1,
            totalDurationMinutes = 30,
            dueDate = LocalDate.of(2026, 9, 14),
            status = TaskStatus.NOT_STARTED,
            completionSummary = null,
            actualDurationMinutes = null,
            progressPercent = null,
            postponeCount = 0,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        val firstWeekOnlyClass = WeeklyTimeBlock(
            id = "class-week-1",
            title = "课程",
            kind = TimeBlockKind.COURSE,
            dayOfWeek = DayOfWeek.MONDAY,
            startMinute = 8 * 60,
            endMinute = 10 * 60,
            weekPattern = "1周",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        val availabilityBlock = WeeklyTimeBlock(
            id = "availability-2",
            title = "可用时间",
            kind = TimeBlockKind.AVAILABLE,
            dayOfWeek = DayOfWeek.MONDAY,
            startMinute = 8 * 60,
            endMinute = 22 * 60,
            weekPattern = null,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

        val plan = PlanGenerator.generate(
            tasks = listOf(task),
            weeklyBlocks = listOf(availabilityBlock, firstWeekOnlyClass),
            dateOverrides = emptyList(),
            semesterFirstWeekMonday = LocalDate.of(2026, 9, 7),
            clock = Clock.fixed(Instant.parse("2026-09-14T06:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(8 * 60, plan.segments.single().startMinute)
    }
}
