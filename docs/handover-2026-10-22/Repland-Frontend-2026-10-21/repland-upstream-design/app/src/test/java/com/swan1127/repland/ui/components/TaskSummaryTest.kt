package com.swan1127.repland.ui.components

import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [summaryLabel] is the one line users read on every task row, and it used to be a private
 * function with no tests at all: it called `LocalDate.now()` inside its own branches and built its
 * text from string literals, so it could not be driven from a JVM test.
 *
 * It now takes the reference day, the words and the locale as parameters. Two things are worth
 * pinning here, and neither is obvious from reading the code:
 *
 *  1. **the order of the branches is the specification** — a completed task never reads as
 *     overdue, and a missed deadline outranks a plan that merely slipped;
 *  2. **every date in a row is rendered with the same `today`** — the previous version called
 *     `LocalDate.now()` once per branch, so a row composed just before midnight could disagree
 *     with itself.
 *
 * The `\$` in the fixtures is **not** a typo: `%1$s` inside a Kotlin string would be read as a
 * template referring to a variable named `s`, so the dollar has to be escaped to reach
 * `String.format` as a literal.
 */
class TaskSummaryTest {

    private val today = LocalDate.of(2026, 3, 2)

    private val labels = TaskDateLabels(
        today = "Today",
        tomorrow = "Tomorrow",
        monthDay = "MMM d",
        yearMonthDay = "MMM d, yyyy",
    )

    private val texts = TaskSummaryTexts(
        inProgress = "In progress",
        completed = "Completed",
        postponed = "Postponed",
        overdue = "Overdue · %1\$s",
        needsRescheduling = "Needs rescheduling · %1\$s",
        plannedDueMinutes = "Planned %1\$s · Due %2\$s · %3\$d min",
        plannedDue = "Planned %1\$s · Due %2\$s",
        plannedMinutes = "Planned %1\$s · %2\$d min",
        planned = "Planned %1\$s",
        dueMinutes = "Due %1\$s · %2\$d min",
        due = "Due %1\$s",
        unscheduledMinutes = "Unscheduled · %1\$d min",
        unscheduled = "Unscheduled",
    )

    private fun task(
        status: TaskStatus = TaskStatus.NOT_STARTED,
        dueDate: LocalDate? = null,
        scheduledForDate: LocalDate? = null,
        totalDurationMinutes: Int? = null,
    ) = Task(
        id = "t1",
        description = "description",
        displayName = "name",
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.MEDIUM,
        estimatedDays = 1,
        totalDurationMinutes = totalDurationMinutes,
        dueDate = dueDate,
        status = status,
        completionSummary = null,
        actualDurationMinutes = null,
        progressPercent = null,
        postponeCount = 0,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
        completionResult = null,
        scheduledForDate = scheduledForDate,
    )

    private fun summary(t: Task): String = t.summaryLabel(today, labels, texts, Locale.US)

    // ------------------------------------------------------------ status wins

    @Test
    fun `an in-progress task is described by its status`() {
        assertEquals("In progress", summary(task(status = TaskStatus.IN_PROGRESS)))
    }

    @Test
    fun `a completed task is described by its status`() {
        assertEquals("Completed", summary(task(status = TaskStatus.COMPLETED)))
    }

    @Test
    fun `a postponed task is described by its status`() {
        assertEquals("Postponed", summary(task(status = TaskStatus.POSTPONED)))
    }

    @Test
    fun `a completed task with a missed deadline is not called overdue`() {
        // The status branches come first on purpose: once a task is done, a past due date is
        // history, not a warning.
        val done = task(status = TaskStatus.COMPLETED, dueDate = today.minusDays(5))
        assertEquals("Completed", summary(done))
    }

    // ------------------------------------------------------------ overdue outranks rescheduling

    @Test
    fun `a missed deadline reads as overdue`() {
        assertEquals("Overdue · Mar 1", summary(task(dueDate = today.minusDays(1))))
    }

    @Test
    fun `a missed deadline outranks a plan that slipped`() {
        // Both conditions hold. The deadline is the more urgent fact, so "overdue" must win —
        // swapping these two branches would be invisible in a screenshot and wrong in a list.
        val t = task(dueDate = today.minusDays(1), scheduledForDate = today.minusDays(3))
        assertEquals("Overdue · Mar 1", summary(t))
    }

    @Test
    fun `a plan that slipped reads as needing rescheduling`() {
        assertEquals("Needs rescheduling · Feb 27", summary(task(scheduledForDate = today.minusDays(3))))
    }

    @Test
    fun `a deadline of today is not overdue`() {
        // `isBefore`, not `!isAfter`: a task due today is still on time.
        assertEquals("Due Today", summary(task(dueDate = today)))
    }

    @Test
    fun `a planned day of today is not flagged as slipped`() {
        assertEquals("Planned Today", summary(task(scheduledForDate = today)))
    }

    @Test
    fun `a cancelled task is not treated as active so it is not overdue`() {
        // isActive is false for CANCELLED/REPLACED, so the overdue branch must not fire.
        assertEquals("Due Mar 1", summary(task(status = TaskStatus.CANCELLED, dueDate = today.minusDays(1))))
    }

    // ------------------------------------------------------------ planned and due together

    @Test
    fun `a plan and a deadline with a duration are all reported`() {
        val t = task(dueDate = today.plusDays(7), scheduledForDate = today.plusDays(3), totalDurationMinutes = 45)
        assertEquals("Planned Mar 5 · Due Mar 9 · 45 min", summary(t))
    }

    @Test
    fun `a plan and a deadline without a duration omit the minutes`() {
        val t = task(dueDate = today.plusDays(7), scheduledForDate = today.plusDays(3))
        assertEquals("Planned Mar 5 · Due Mar 9", summary(t))
    }

    @Test
    fun `a plan with a duration reports both`() {
        val t = task(scheduledForDate = today.plusDays(3), totalDurationMinutes = 45)
        assertEquals("Planned Mar 5 · 45 min", summary(t))
    }

    @Test
    fun `a plan alone is reported alone`() {
        assertEquals("Planned Mar 5", summary(task(scheduledForDate = today.plusDays(3))))
    }

    @Test
    fun `a deadline with a duration reports both`() {
        assertEquals("Due Mar 9 · 45 min", summary(task(dueDate = today.plusDays(7), totalDurationMinutes = 45)))
    }

    @Test
    fun `a deadline alone is reported alone`() {
        assertEquals("Due Mar 9", summary(task(dueDate = today.plusDays(7))))
    }

    @Test
    fun `a duration with no dates is reported as unscheduled`() {
        assertEquals("Unscheduled · 45 min", summary(task(totalDurationMinutes = 45)))
    }

    @Test
    fun `a task with nothing set says so`() {
        assertEquals("Unscheduled", summary(task()))
    }

    // ------------------------------------------------------------ the language

    @Test
    fun `every date in the line follows the language, not just the first`() {
        // Two dates in one row: a mixed result would be the classic half-translated bug, so both
        // are asserted inside the same string.
        val t = task(dueDate = LocalDate.of(2027, 1, 5), scheduledForDate = today.plusDays(3))
        assertEquals("Planned Mar 5 · Due Jan 5, 2027", summary(t))
    }

    @Test
    fun `the same task reads as Chinese when given Chinese words and a Chinese locale`() {
        val chinese = TaskDateLabels("今天", "明天", "M月d日", "yyyy年M月d日")
        val chineseTexts = TaskSummaryTexts(
            inProgress = "正在进行",
            completed = "已完成",
            postponed = "已延期",
            overdue = "已逾期 · %1\$s",
            needsRescheduling = "待重新安排 · %1\$s",
            plannedDueMinutes = "计划 %1\$s · 截止 %2\$s · %3\$d 分钟",
            plannedDue = "计划 %1\$s · 截止 %2\$s",
            plannedMinutes = "计划 %1\$s · %2\$d 分钟",
            planned = "计划 %1\$s",
            dueMinutes = "截止 %1\$s · %2\$d 分钟",
            due = "截止 %1\$s",
            unscheduledMinutes = "待安排 · %1\$d 分钟",
            unscheduled = "待安排",
        )
        val t = task(dueDate = today.plusDays(7), scheduledForDate = today.plusDays(3), totalDurationMinutes = 45)
        assertEquals(
            "计划 3月5日 · 截止 3月9日 · 45 分钟",
            t.summaryLabel(today, chinese, chineseTexts, Locale.CHINA),
        )
    }

    @Test
    fun `no branch ever emits a format placeholder`() {
        // A `String.format` whose arguments do not line up with the template either throws or
        // leaves a literal "%2$s" on screen. Walking every branch catches both.
        val cases = listOf(
            task(),
            task(totalDurationMinutes = 45),
            task(scheduledForDate = today),
            task(dueDate = today),
            task(scheduledForDate = today, totalDurationMinutes = 45),
            task(dueDate = today, totalDurationMinutes = 45),
            task(dueDate = today, scheduledForDate = today),
            task(dueDate = today, scheduledForDate = today, totalDurationMinutes = 45),
            task(dueDate = today.minusDays(1)),
            task(scheduledForDate = today.minusDays(1)),
            task(status = TaskStatus.IN_PROGRESS),
            task(status = TaskStatus.COMPLETED),
            task(status = TaskStatus.POSTPONED),
        )
        cases.forEach { t ->
            val line = summary(t)
            assertFalse("placeholder left in \"$line\"", line.contains('%'))
            assertTrue("empty summary for ${t.status}", line.isNotBlank())
        }
    }
}
