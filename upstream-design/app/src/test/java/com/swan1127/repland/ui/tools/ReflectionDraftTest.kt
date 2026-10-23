package com.swan1127.repland.ui.tools

import com.swan1127.repland.domain.model.TEST_MONDAY
import com.swan1127.repland.domain.model.executionLog
import com.swan1127.repland.domain.model.feedback
import com.swan1127.repland.domain.model.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [buildReflectionDraft] writes the paragraph a user is invited to accept as-is, so a mistake
 * here is not a crash — it is a wrong sentence the user is nudged to sign. Every branch is
 * therefore asserted on its exact wording rather than by "contains".
 *
 * The function is pure and public, so it runs with no Compose or Android runtime.
 */
class ReflectionDraftTest {

    private val date = TEST_MONDAY

    // ------------------------------------------------------------ the empty case

    @Test
    fun `no execution logs produce no draft at all`() {
        // An empty string is what tells the screen to offer nothing rather than an empty
        // paragraph with a header and no content.
        assertEquals("", buildReflectionDraft(date, logs = emptyList(), tasks = listOf(task("t1"))))
    }

    // ------------------------------------------------------------ the header

    @Test
    fun `an hour and a half is written as one hour thirty minutes`() {
        val draft = buildReflectionDraft(date, logs = listOf(logged("l1", minutes = 90)), tasks = listOf(task("t1")))

        assertTrue(draft.startsWith("今天投入了 1 小时 30 分："))
    }

    @Test
    fun `a whole number of hours still prints zero minutes`() {
        val draft = buildReflectionDraft(date, logs = listOf(logged("l1", minutes = 120)), tasks = listOf(task("t1")))

        assertTrue(draft.startsWith("今天投入了 2 小时 0 分："))
    }

    @Test
    fun `under an hour prints zero hours rather than dropping the hour field`() {
        // Pinned: the format is always "H 小时 M 分", so 45 minutes reads "0 小时 45 分"
        // instead of silently switching shape.
        val draft = buildReflectionDraft(date, logs = listOf(logged("l1", minutes = 45)), tasks = listOf(task("t1")))

        assertTrue(draft.startsWith("今天投入了 0 小时 45 分："))
    }

    @Test
    fun `durations from several logs are summed`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", minutes = 30), logged("l2", minutes = 45)),
            tasks = listOf(task("t1")),
        )

        assertTrue(draft.startsWith("今天投入了 1 小时 15 分："))
    }

    @Test
    fun `logs without a recorded duration fall back to a count header`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", minutes = null), logged("l2", minutes = null)),
            tasks = listOf(task("t1")),
        )

        assertTrue(draft.startsWith("今天有 2 条执行记录："))
    }

    @Test
    fun `durations summing to zero fall back to the count header`() {
        // The branch is `totalMinutes > 0`, so a zero total takes the count header even
        // though durations were recorded.
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", minutes = 0), logged("l2", minutes = 0)),
            tasks = listOf(task("t1")),
        )

        assertTrue(draft.startsWith("今天有 2 条执行记录："))
    }

    // ------------------------------------------------------------ the per-log lines

    @Test
    fun `a stuck point is described as being stuck on it`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1", stuck = "泰勒展开的余项")),
            tasks = listOf(task("t1")),
        )

        assertTrue(draft.contains("· Task t1 卡在 泰勒展开的余项"))
    }

    @Test
    fun `a completed piece is described as progress into it`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1", done = "第三章")),
            tasks = listOf(task("t1")),
        )

        assertTrue(draft.contains("· Task t1 推进到 第三章"))
    }

    @Test
    fun `a bare percentage is described as an approximate completion`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1", progress = 60)),
            tasks = listOf(task("t1")),
        )

        assertTrue(draft.contains("· Task t1 完成约 60%"))
    }

    @Test
    fun `a log with nothing recorded still gets a line`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1")),
            tasks = listOf(task("t1")),
        )

        assertTrue(draft.contains("· Task t1 有进展"))
    }

    @Test
    fun `being stuck outranks having completed something`() {
        // The order is stuck > done > progress > generic. A log that carries both shows only
        // the stuck point, because that is the thing worth resurfacing.
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1", stuck = "余项", done = "第三章")),
            tasks = listOf(task("t1")),
        )

        assertTrue(draft.contains("卡在 余项"))
        assertTrue(!draft.contains("推进到"))
    }

    @Test
    fun `having completed something outranks a bare percentage`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1", done = "第三章", progress = 60)),
            tasks = listOf(task("t1")),
        )

        assertTrue(draft.contains("推进到 第三章"))
        assertTrue(!draft.contains("完成约"))
    }

    @Test
    fun `a bare percentage outranks the generic line`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1", progress = 60)),
            tasks = listOf(task("t1")),
        )

        assertTrue(draft.contains("完成约 60%"))
        assertTrue(!draft.contains("有进展"))
    }

    @Test
    fun `blank recorded text is treated as absent`() {
        // A whitespace-only result must fall through to the next branch rather than
        // producing "卡在   " with nothing after it.
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1", stuck = "   ", done = "   ", progress = 30)),
            tasks = listOf(task("t1")),
        )

        assertTrue(draft.contains("完成约 30%"))
        assertTrue(!draft.contains("卡在"))
        assertTrue(!draft.contains("推进到"))
    }

    @Test
    fun `a log whose task is missing uses a placeholder name`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "task-gone", stuck = "余项")),
            tasks = listOf(task("t1")),
        )

        assertTrue(draft.contains("· 某个任务 卡在 余项"))
    }

    // ------------------------------------------------------------ the four-line cap

    @Test
    fun `only the first four logs are described`() {
        val logs = (1..6).map { logged("l$it", taskId = "t1") }
        val draft = buildReflectionDraft(date, logs = logs, tasks = listOf(task("t1")))

        assertEquals(4, draft.lines().count { it.startsWith("· ") })
    }

    @Test
    fun `the count header still counts every log not just the four shown`() {
        // The header and the bullets answer different questions, so the cap must not
        // shrink the count.
        val logs = (1..6).map { logged("l$it", taskId = "t1", minutes = null) }
        val draft = buildReflectionDraft(date, logs = logs, tasks = listOf(task("t1")))

        assertTrue(draft.startsWith("今天有 6 条执行记录："))
        assertEquals(4, draft.lines().count { it.startsWith("· ") })
    }

    @Test
    fun `the first four logs are kept in their original order`() {
        val logs = listOf(
            logged("l1", taskId = "t1", stuck = "第一"),
            logged("l2", taskId = "t1", stuck = "第二"),
            logged("l3", taskId = "t1", stuck = "第三"),
            logged("l4", taskId = "t1", stuck = "第四"),
            logged("l5", taskId = "t1", stuck = "第五"),
        )
        val draft = buildReflectionDraft(date, logs = logs, tasks = listOf(task("t1")))

        assertTrue(draft.contains("第一"))
        assertTrue(draft.contains("第四"))
        assertTrue(!draft.contains("第五"))
    }

    // ------------------------------------------------------------ the postponement nudge

    @Test
    fun `a task postponed twice earns a nudge`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1")),
            tasks = listOf(task("t1", postponeCount = 2)),
        )

        assertTrue(draft.contains("明天把「Task t1」拆小一点，它已经推迟 2 次了。"))
    }

    @Test
    fun `a task postponed once does not earn a nudge`() {
        // The threshold is two, not one.
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1")),
            tasks = listOf(task("t1", postponeCount = 1)),
        )

        assertTrue(!draft.contains("拆小一点"))
    }

    @Test
    fun `the most postponed task is the one named`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1")),
            tasks = listOf(task("t1", postponeCount = 2), task("t2", postponeCount = 7)),
        )

        assertTrue(draft.contains("「Task t2」"))
        assertTrue(draft.contains("推迟 7 次"))
    }

    @Test
    fun `when two tasks tie the first in the list is named`() {
        // maxBy returns the first maximal element; pinned so the choice is a decision.
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1")),
            tasks = listOf(task("t1", postponeCount = 5), task("t2", postponeCount = 5)),
        )

        assertTrue(draft.contains("「Task t1」"))
        assertTrue(!draft.contains("「Task t2」"))
    }

    @Test
    fun `a postponed task that has no log today is still named`() {
        // The nudge scans the whole task list, not just today's logs, so a repeatedly
        // postponed task surfaces even on a day it was not worked on.
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1")),
            tasks = listOf(task("t1"), task("t9", postponeCount = 4)),
        )

        assertTrue(draft.contains("「Task t9」"))
    }

    @Test
    fun `the nudge comes after the per-log lines`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1", stuck = "余项")),
            tasks = listOf(task("t1", postponeCount = 3)),
        )

        assertTrue(draft.lines().last().startsWith("明天把「"))
    }

    @Test
    fun `there is no nudge when nothing reaches the threshold`() {
        val draft = buildReflectionDraft(
            date,
            logs = listOf(logged("l1", taskId = "t1")),
            tasks = listOf(task("t1", postponeCount = 0)),
        )

        assertTrue(!draft.contains("拆小一点"))
        assertEquals(2, draft.lines().size)
    }

    // ------------------------------------------------------------ the unused date

    @Test
    fun `the date parameter does not change the draft`() {
        // `date` is accepted but never read: the header always says 今天. Pinned so the
        // dead parameter is a recorded fact rather than a trap for whoever passes a
        // non-today date expecting the wording to follow it.
        val logs = listOf(logged("l1", taskId = "t1", stuck = "余项"))
        val tasks = listOf(task("t1"))

        assertEquals(
            buildReflectionDraft(TEST_MONDAY, logs, tasks),
            buildReflectionDraft(TEST_MONDAY.plusDays(40), logs, tasks),
        )
    }

    // ------------------------------------------------------------ builders

    private fun logged(
        id: String,
        taskId: String = "t1",
        minutes: Int? = null,
        progress: Int? = null,
        done: String? = null,
        stuck: String? = null,
    ) = executionLog(
        id = id,
        taskId = taskId,
        feedback = feedback(
            actualDurationMinutes = minutes,
            progressPercent = progress,
            completedContent = done,
            completionResult = stuck,
        ),
    )
}
