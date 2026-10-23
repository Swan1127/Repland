package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DailyReviewSummarizer] reports only recorded evidence. It must never infer a status
 * or promote a feedback value into a lifecycle transition.
 */
class DailyReviewSummarizerTest {

    private val tomorrow = TEST_MONDAY.plusDays(1)

    private fun scheduledOn(date: java.time.LocalDate, vararg ids: Pair<String, String>) =
        ids.mapIndexed { index, (segmentId, taskId) ->
            segment(segmentId, taskId = taskId, date = date, startMinute = 9 * 60 + index * 30, endMinute = 9 * 60 + (index + 1) * 30)
        }

    @Test
    fun `only segments on the requested date are counted`() {
        val plan = confirmedPlan(
            segments = scheduledOn(TEST_MONDAY, "s1" to "t1", "s2" to "t1") +
                scheduledOn(tomorrow, "s3" to "t1"),
        )

        val summary = DailyReviewSummarizer.summarize(
            date = TEST_MONDAY,
            tasks = listOf(task("t1")),
            plan = plan,
            logs = emptyList(),
        )

        assertEquals(2, summary.plannedSegmentCount)
    }

    @Test
    fun `pending segments count only tasks that are still active`() {
        val plan = confirmedPlan(
            segments = scheduledOn(TEST_MONDAY, "s1" to "t1", "s2" to "t2"),
        )

        val summary = DailyReviewSummarizer.summarize(
            date = TEST_MONDAY,
            tasks = listOf(
                task("t1", status = TaskStatus.IN_PROGRESS),
                task("t2", status = TaskStatus.COMPLETED),
            ),
            plan = plan,
            logs = emptyList(),
        )

        assertEquals(2, summary.plannedSegmentCount)
        assertEquals(1, summary.pendingSegmentCount)
    }

    @Test
    fun `recorded durations are summed across the day's logs`() {
        val logs = listOf(
            executionLog("l1", "t1", feedback = feedback(actualDurationMinutes = 30)),
            executionLog("l2", "t1", feedback = feedback(actualDurationMinutes = 45)),
        )

        val summary = DailyReviewSummarizer.summarize(TEST_MONDAY, listOf(task("t1")), null, logs)

        assertEquals(75, summary.actualDurationMinutes)
        assertEquals(2, summary.feedbackCount)
    }

    @Test
    fun `feedback for an unknown task is counted but never turned into progress`() {
        val logs = listOf(
            executionLog("l1", "t1", feedback = feedback(progressPercent = 40)),
            executionLog("l2", "ghost", feedback = feedback(progressPercent = 90)),
        )

        val summary = DailyReviewSummarizer.summarize(TEST_MONDAY, listOf(task("t1")), null, logs)

        // feedbackCount counts raw rows; progressByTask only resolves rows whose task exists.
        assertEquals(2, summary.feedbackCount)
        assertEquals(1, summary.progressByTask.size)
        assertEquals("t1", summary.progressByTask.single().taskId)
    }

    @Test
    fun `the suggested next task prefers an active task that is not scheduled today`() {
        val plan = confirmedPlan(segments = scheduledOn(TEST_MONDAY, "s1" to "t1"))

        val summary = DailyReviewSummarizer.summarize(
            date = TEST_MONDAY,
            tasks = listOf(task("t1"), task("t2")),
            plan = plan,
            logs = emptyList(),
        )

        assertEquals("t2", summary.nextSuggestedTaskId)
    }

    @Test
    fun `when everything today is scheduled it falls back to the first active task`() {
        val plan = confirmedPlan(
            segments = scheduledOn(TEST_MONDAY, "s1" to "t1", "s2" to "t2"),
        )

        val summary = DailyReviewSummarizer.summarize(
            date = TEST_MONDAY,
            tasks = listOf(task("t1"), task("t2")),
            plan = plan,
            logs = emptyList(),
        )

        assertEquals("t1", summary.nextSuggestedTaskId)
    }

    @Test
    fun `a completed task is never suggested`() {
        val summary = DailyReviewSummarizer.summarize(
            date = TEST_MONDAY,
            tasks = listOf(task("t1", status = TaskStatus.COMPLETED)),
            plan = null,
            logs = emptyList(),
        )

        assertTrue(summary.nextSuggestedTaskId == null)
    }

    @Test
    fun `a null plan reports zero planned work but still reports the day's feedback`() {
        val logs = listOf(executionLog("l1", "t1", feedback = feedback(actualDurationMinutes = 20)))

        val summary = DailyReviewSummarizer.summarize(TEST_MONDAY, listOf(task("t1")), null, logs)

        assertEquals(0, summary.plannedSegmentCount)
        assertEquals(0, summary.pendingSegmentCount)
        assertEquals(1, summary.feedbackCount)
        assertEquals(20, summary.actualDurationMinutes)
    }

    @Test
    fun `progress entries carry the recorded feedback verbatim`() {
        val logs = listOf(
            executionLog(
                "l1",
                "t1",
                feedback = feedback(progressPercent = 65, completionResult = "finished chapter 3"),
            ),
        )

        val summary = DailyReviewSummarizer.summarize(TEST_MONDAY, listOf(task("t1")), null, logs)

        val progress = summary.progressByTask.single()
        assertEquals(65, progress.progressPercent)
        assertEquals("finished chapter 3", progress.completionResult)
        assertEquals("Task t1", progress.taskName)
    }
}
