package com.swan1127.repland.data

import com.swan1127.repland.domain.model.TEST_MONDAY
import com.swan1127.repland.domain.model.executionLog
import com.swan1127.repland.domain.model.feedback
import com.swan1127.repland.domain.model.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure models that live beside the auxiliary store.
 *
 * Both are reachable without a `Context` — `AuxiliaryStore` itself needs one, but the review
 * ladder and the appearance mapping do not, and those are exactly the two places where a
 * quiet off-by-one changes what the user sees: a review point that resurfaces on the wrong
 * day, or an app that opens in the wrong theme.
 */
class AuxiliaryModelsTest {

    // ------------------------------------------------------------ the Ebbinghaus ladder

    @Test
    fun `the ladder is one two four seven and fifteen days`() {
        assertEquals(listOf(1, 2, 4, 7, 15), ReviewPoint.EBBINGHAUS_DAYS)
    }

    @Test
    fun `each stage is due the advertised number of days after capture`() {
        ReviewPoint.EBBINGHAUS_DAYS.forEachIndexed { stage, days ->
            val point = point(stage = stage)
            assertEquals(
                "stage $stage should be due $days days after capture",
                TEST_MONDAY.plusDays(days.toLong()),
                point.dueOn,
            )
        }
    }

    @Test
    fun `a stage below the ladder is clamped to the first rung rather than throwing`() {
        // `coerceIn` means a corrupt negative stage still yields a usable date.
        assertEquals(TEST_MONDAY.plusDays(1), point(stage = -1).dueOn)
        assertEquals(TEST_MONDAY.plusDays(1), point(stage = -99).dueOn)
    }

    @Test
    fun `a stage past the ladder is clamped to the last rung rather than throwing`() {
        // Reached only for an already-graduated point, whose dueOn is no longer consulted,
        // but it must still not throw when something renders it.
        assertEquals(TEST_MONDAY.plusDays(15), point(stage = 5).dueOn)
        assertEquals(TEST_MONDAY.plusDays(15), point(stage = 99).dueOn)
    }

    @Test
    fun `a point graduates once the stage reaches the length of the ladder`() {
        assertFalse(point(stage = 0).isGraduated)
        assertFalse(point(stage = 4).isGraduated)
        assertTrue(point(stage = 5).isGraduated)
        assertTrue(point(stage = 99).isGraduated)
    }

    @Test
    fun `a negative stage is not graduated`() {
        assertFalse(point(stage = -1).isGraduated)
    }

    // ------------------------------------------------------------ due dates

    @Test
    fun `a point is due on its due date and on every later day`() {
        val point = point(stage = 0) // due TEST_MONDAY + 1

        assertFalse("the day before is not due", point.isDueOn(TEST_MONDAY))
        assertTrue("the due date itself counts as due", point.isDueOn(TEST_MONDAY.plusDays(1)))
        assertTrue("an overdue point stays due", point.isDueOn(TEST_MONDAY.plusDays(30)))
    }

    @Test
    fun `a graduated point is never due however overdue it looks`() {
        // Graduation must win over the date, otherwise a finished point would keep coming back.
        val graduated = point(stage = 5)

        assertFalse(graduated.isDueOn(TEST_MONDAY.plusDays(400)))
        assertFalse(graduated.dueWithin(365, TEST_MONDAY.plusDays(400)))
    }

    @Test
    fun `dueWithin counts the boundary day as inside the window`() {
        val point = point(stage = 2) // due TEST_MONDAY + 4

        assertTrue("four days is exactly the due date", point.dueWithin(4, TEST_MONDAY))
        assertFalse("three days is one short", point.dueWithin(3, TEST_MONDAY))
        assertTrue("a wider window includes it", point.dueWithin(5, TEST_MONDAY))
    }

    @Test
    fun `a zero-day window means due today or already overdue`() {
        val point = point(stage = 0) // due TEST_MONDAY + 1

        assertFalse(point.dueWithin(0, TEST_MONDAY))
        assertTrue(point.dueWithin(0, TEST_MONDAY.plusDays(1)))
        assertTrue(point.dueWithin(0, TEST_MONDAY.plusDays(9)))
    }

    @Test
    fun `dueWithin agrees with isDueOn at a zero-day window`() {
        // The two are the same question asked two ways, so they must not drift apart.
        listOf(-5L, 0L, 1L, 4L, 5L, 40L).forEach { offset ->
            val today = TEST_MONDAY.plusDays(offset)
            assertEquals(
                "disagreement at offset $offset",
                point(stage = 1).isDueOn(today),
                point(stage = 1).dueWithin(0, today),
            )
        }
    }

    // ------------------------------------------------------------ derived candidates

    @Test
    fun `the task's own completion result becomes a candidate`() {
        val candidates = ReviewPoint.candidatesFrom(
            task = task("t1").copy(completionResult = "泰勒展开的余项搞不清"),
            logs = emptyList(),
        )

        assertEquals(listOf("泰勒展开的余项搞不清"), candidates)
    }

    @Test
    fun `each execution log's completion result becomes a candidate too`() {
        // A partial completion carries the same signal as a full one, so both are scanned.
        val candidates = ReviewPoint.candidatesFrom(
            task = task("t1"),
            logs = listOf(
                executionLog("l1", "t1", feedback = feedback(completionResult = "第一步就不懂")),
                executionLog("l2", "t1", feedback = feedback(completionResult = "公式记不住")),
            ),
        )

        assertEquals(listOf("第一步就不懂", "公式记不住"), candidates)
    }

    @Test
    fun `the task's result comes before the log results`() {
        val candidates = ReviewPoint.candidatesFrom(
            task = task("t1").copy(completionResult = "任务级"),
            logs = listOf(executionLog("l1", "t1", feedback = feedback(completionResult = "日志级"))),
        )

        assertEquals(listOf("任务级", "日志级"), candidates)
    }

    @Test
    fun `blank and missing completion results are skipped and the rest trimmed`() {
        val candidates = ReviewPoint.candidatesFrom(
            task = task("t1").copy(completionResult = "   "),
            logs = listOf(
                executionLog("l1", "t1", feedback = feedback(completionResult = null)),
                executionLog("l2", "t1", feedback = feedback(completionResult = "  边界条件  ")),
                executionLog("l3", "t1", feedback = feedback(completionResult = "")),
            ),
        )

        assertEquals(listOf("边界条件"), candidates)
    }

    @Test
    fun `nothing to review yields no candidates`() {
        assertEquals(emptyList<String>(), ReviewPoint.candidatesFrom(task("t1"), emptyList()))
    }

    @Test
    fun `repeated wording is kept rather than deduplicated`() {
        // Pinned deliberately: the store decides what to do with repeats. Deduplicating here
        // would silently merge two separate review points that happen to share a phrase.
        val candidates = ReviewPoint.candidatesFrom(
            task = task("t1").copy(completionResult = "同上"),
            logs = listOf(executionLog("l1", "t1", feedback = feedback(completionResult = "同上"))),
        )

        assertEquals(listOf("同上", "同上"), candidates)
    }

    // ------------------------------------------------------------ what gets queued

    @Test
    fun `a sentence becomes a review point when nothing like it is queued`() {
        assertTrue(ReviewPoint.shouldSeed(emptyList(), "t1", "余项为什么是三次"))
    }

    @Test
    fun `a blank sentence never becomes a review point`() {
        assertFalse(ReviewPoint.shouldSeed(emptyList(), "t1", ""))
        assertFalse(ReviewPoint.shouldSeed(emptyList(), "t1", "   "))
    }

    @Test
    fun `the same sentence for the same task is not queued twice`() {
        // The rule that both tool entry points lean on: the self-explanation and the KWL "学到了"
        // column hand the same sentence to the queue, and saving the same tool twice must not put
        // the same question back on the review list twice.
        val queued = listOf(point(stage = 0, taskId = "t1", content = "余项为什么是三次"))
        assertFalse(ReviewPoint.shouldSeed(queued, "t1", "余项为什么是三次"))
    }

    @Test
    fun `surrounding whitespace does not make a second copy`() {
        val queued = listOf(point(stage = 0, taskId = "t1", content = "余项为什么是三次"))
        assertFalse(ReviewPoint.shouldSeed(queued, "t1", "  余项为什么是三次  "))
    }

    @Test
    fun `the same sentence for a different task is queued`() {
        // Two courses can genuinely raise the same question, so the task id is part of the identity
        // rather than the sentence being deduplicated globally.
        val queued = listOf(point(stage = 0, taskId = "t1", content = "余项为什么是三次"))
        assertTrue(ReviewPoint.shouldSeed(queued, "t2", "余项为什么是三次"))
    }

    @Test
    fun `a different sentence for the same task is queued`() {
        val queued = listOf(point(stage = 0, taskId = "t1", content = "余项为什么是三次"))
        assertTrue(ReviewPoint.shouldSeed(queued, "t1", "拉格朗日余项怎么用"))
    }

    // ------------------------------------------------------------ appearance

    @Test
    fun `an absent stored appearance falls back to following the system`() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage(null))
    }

    @Test
    fun `every appearance name round-trips through storage`() {
        AppearanceMode.entries.forEach { mode ->
            assertEquals(mode, AppearanceMode.fromStorage(mode.name))
        }
    }

    @Test
    fun `an unrecognised stored appearance falls back to the system rather than throwing`() {
        // A value written by a newer build must not crash an older one.
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage(""))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage("AUTO"))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage("SYSTEM "))
    }

    @Test
    fun `the stored appearance is matched case-sensitively`() {
        // Pinned so the matching rule is a decision: only the exact constant name is accepted.
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage("dark"))
        assertEquals(AppearanceMode.DARK, AppearanceMode.fromStorage("DARK"))
    }

    // ------------------------------------------------------------ builder

    private fun point(
        stage: Int,
        taskId: String = "task-1",
        content: String = "泰勒展开",
    ): ReviewPoint = ReviewPoint(
        id = "point-1",
        taskId = taskId,
        taskTitle = "高数",
        content = content,
        createdOn = TEST_MONDAY,
        stage = stage,
    )
}
