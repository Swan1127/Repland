package com.swan1127.repland.ui.tools

import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recommendation rule is the only thing standing between "five tools nobody can find" and "five
 * tools that interrupt you". Both failure directions are real and both are tested here:
 *
 *   * **Silence.** A rule that never fires is invisible — the feature would ship, the doc would say
 *     it was done, and the strip would never appear. Every rule therefore has a test that makes it
 *     fire, plus one that makes it *not* fire, so "always on" is ruled out too.
 *   * **Order.** The strip shows at most [MAX_SUGGESTIONS] rows, so the priority list is part of the
 *     specification. The order is pinned explicitly rather than inferred from the `if` order in the
 *     production file, because reordering those `if`s is exactly the edit that would silently change
 *     which two tools a user ever sees.
 *
 * Everything is driven through [recommendTools] with plain values. There is no clock, no store and
 * no Compose in this file, so a test can put the user at 09:00 on the day a chapter was read
 * without pretending to be a device.
 */
class ToolRecommendationTest {

    private val day = LocalDate.of(2026, 10, 6)

    // ------------------------------------------------------------------ fixtures

    private fun task(
        id: String,
        name: String = id,
        category: TaskCategory = TaskCategory.COURSE,
        status: TaskStatus = TaskStatus.NOT_STARTED,
        updatedAt: Long = 0L,
    ): Task = Task(
        id = id,
        description = name,
        displayName = name,
        category = category,
        userPriority = TaskPriority.MEDIUM,
        estimatedDays = 1,
        totalDurationMinutes = 60,
        dueDate = null,
        status = status,
        completionSummary = null,
        actualDurationMinutes = null,
        progressPercent = null,
        postponeCount = 0,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = updatedAt,
        completionResult = null,
        scheduledForDate = null,
    )

    private fun block(taskId: String, start: Int, end: Int): PlannedSegment =
        PlannedSegment(id = "segment-$taskId-$start", taskId = taskId, date = day, startMinute = start, endMinute = end)

    private fun situation(
        nowMinute: Int = 9 * 60,
        tasks: List<Task> = emptyList(),
        todaySegments: List<PlannedSegment> = emptyList(),
        dueReviewPointCount: Int = 0,
        hasReflection: Boolean = false,
        kwlLearnedTaskIds: Set<String> = emptySet(),
        explainedTaskIds: Set<String> = emptySet(),
    ): ToolSituation = ToolSituation(
        nowMinute = nowMinute,
        tasks = tasks,
        todaySegments = todaySegments,
        dueReviewPointCount = dueReviewPointCount,
        hasReflection = hasReflection,
        kwlLearnedTaskIds = kwlLearnedTaskIds,
        explainedTaskIds = explainedTaskIds,
    )

    private fun toolsOf(situation: ToolSituation): List<ToolId> =
        recommendTools(situation).map(ToolSuggestion::tool)

    // ------------------------------------------------------------------ R1 复习点

    @Test
    fun `a due review point is offered on its own`() {
        assertEquals(listOf(ToolId.REVIEW_POINTS), toolsOf(situation(dueReviewPointCount = 3)))
    }

    @Test
    fun `no due review point means no review suggestion`() {
        assertEquals(emptyList<ToolId>(), toolsOf(situation(dueReviewPointCount = 0)))
    }

    @Test
    fun `the review reason carries the number of due points`() {
        val suggestion = recommendTools(situation(dueReviewPointCount = 4)).single()
        assertEquals(SuggestionReason.Count(4), suggestion.reason)
    }

    // ------------------------------------------------------------------ R2 阅读脚手架

    @Test
    fun `a course block that has already ended recommends the reading scaffold`() {
        // The example the plan document names: 读完一章 → 推 SQ3R.
        val reading = task("t1", name = "线代第三章", status = TaskStatus.IN_PROGRESS)
        val result = toolsOf(situation(nowMinute = 10 * 60, tasks = listOf(reading), todaySegments = listOf(block("t1", 9 * 60, 9 * 60 + 50))))
        assertEquals(listOf(ToolId.SQ3R), result)
    }

    @Test
    fun `a course block that is still running does not recommend anything yet`() {
        val reading = task("t1", status = TaskStatus.IN_PROGRESS)
        val result = toolsOf(situation(nowMinute = 9 * 60 + 20, tasks = listOf(reading), todaySegments = listOf(block("t1", 9 * 60, 9 * 60 + 50))))
        assertEquals(emptyList<ToolId>(), result)
    }

    @Test
    fun `a block ending exactly now counts as ended`() {
        // The boundary is `endMinute <= nowMinute`. Off by one here means the scaffold appears a
        // minute late for every reading session, which no screenshot would ever show.
        val reading = task("t1", status = TaskStatus.IN_PROGRESS)
        val result = toolsOf(situation(nowMinute = 9 * 60 + 50, tasks = listOf(reading), todaySegments = listOf(block("t1", 9 * 60, 9 * 60 + 50))))
        assertEquals(listOf(ToolId.SQ3R), result)
    }

    @Test
    fun `a non-course block does not recommend the reading scaffold`() {
        val office = task("t1", category = TaskCategory.OFFICE, status = TaskStatus.IN_PROGRESS)
        val result = toolsOf(situation(nowMinute = 10 * 60, tasks = listOf(office), todaySegments = listOf(block("t1", 9 * 60, 9 * 60 + 50))))
        assertEquals(emptyList<ToolId>(), result)
    }

    @Test
    fun `a finished course block whose task is closed does not recommend the scaffold`() {
        // The status is the user's own confirmation that the reading is over, and it is what splits
        // R2 from R3. Without it the strip would offer the during-reading scaffold after the work
        // was already filed away.
        val done = task("t1", status = TaskStatus.COMPLETED)
        val result = toolsOf(situation(nowMinute = 10 * 60, tasks = listOf(done), todaySegments = listOf(block("t1", 9 * 60, 9 * 60 + 50))))
        assertEquals(listOf(ToolId.KWL, ToolId.SELF_EXPLANATION), result)
    }

    @Test
    fun `the scaffold names the reading block that ended last`() {
        val early = task("t1", name = "早读", status = TaskStatus.IN_PROGRESS)
        val late = task("t2", name = "晚读", status = TaskStatus.IN_PROGRESS)
        val result = recommendTools(
            situation(
                nowMinute = 22 * 60,
                tasks = listOf(early, late),
                todaySegments = listOf(block("t1", 8 * 60, 9 * 60), block("t2", 20 * 60, 21 * 60)),
            ),
        )
        assertEquals(listOf(ToolId.SQ3R), result.map(ToolSuggestion::tool))
        assertEquals(SuggestionReason.TaskName("晚读"), result.single().reason)
    }

    // ------------------------------------------------------------------ R3 KWL

    @Test
    fun `a completed course task with an empty KWL column recommends the KWL chart`() {
        val done = task("t1", name = "高数第四章", status = TaskStatus.COMPLETED)
        val result = recommendTools(
            situation(nowMinute = 11 * 60, tasks = listOf(done), todaySegments = listOf(block("t1", 10 * 60, 11 * 60))),
        )
        // Two rows: the KWL chart first, then the explanation for the same task. That pairing is
        // deliberate rather than an accident of the cap — see the ordering test below.
        assertEquals(listOf(ToolId.KWL, ToolId.SELF_EXPLANATION), result.map(ToolSuggestion::tool))
        assertEquals(SuggestionReason.TaskName("高数第四章"), result.first().reason)
    }

    @Test
    fun `a completed course task whose KWL column is filled does not recommend the KWL chart`() {
        val done = task("t1", status = TaskStatus.COMPLETED)
        val result = toolsOf(
            situation(
                nowMinute = 11 * 60,
                tasks = listOf(done),
                todaySegments = listOf(block("t1", 10 * 60, 11 * 60)),
                kwlLearnedTaskIds = setOf("t1"),
            ),
        )
        assertEquals(listOf(ToolId.SELF_EXPLANATION), result)
    }

    @Test
    fun `a completed task outside today is not asked for a KWL chart`() {
        // Nothing about this task belongs to the day on screen: it has no block today, so there is
        // no "just finished" to point at.
        val done = task("t1", status = TaskStatus.COMPLETED)
        assertEquals(emptyList<ToolId>(), toolsOf(situation(nowMinute = 11 * 60, tasks = listOf(done))))
    }

    // ------------------------------------------------------------------ R4 反思

    @Test
    fun `a reflection is not offered before the evening`() {
        val done = task("t1", status = TaskStatus.COMPLETED)
        val result = toolsOf(
            situation(
                nowMinute = REFLECTION_FROM_MINUTE - 1,
                tasks = listOf(done),
                todaySegments = listOf(block("t1", 10 * 60, 11 * 60)),
                kwlLearnedTaskIds = setOf("t1"),
                explainedTaskIds = setOf("t1"),
            ),
        )
        assertEquals(emptyList<ToolId>(), result)
    }

    @Test
    fun `a reflection is offered in the evening once something finished`() {
        val done = task("t1", status = TaskStatus.COMPLETED)
        val result = toolsOf(
            situation(
                nowMinute = REFLECTION_FROM_MINUTE,
                tasks = listOf(done),
                todaySegments = listOf(block("t1", 10 * 60, 11 * 60)),
                kwlLearnedTaskIds = setOf("t1"),
                explainedTaskIds = setOf("t1"),
            ),
        )
        assertEquals(listOf(ToolId.REFLECTION), result)
    }

    @Test
    fun `a saved reflection is not offered again`() {
        val done = task("t1", status = TaskStatus.COMPLETED)
        val result = toolsOf(
            situation(
                nowMinute = 22 * 60,
                tasks = listOf(done),
                todaySegments = listOf(block("t1", 10 * 60, 11 * 60)),
                hasReflection = true,
                kwlLearnedTaskIds = setOf("t1"),
                explainedTaskIds = setOf("t1"),
            ),
        )
        assertEquals(emptyList<ToolId>(), result)
    }

    @Test
    fun `an evening with nothing finished offers no reflection`() {
        // A reflection draft is built from the day's execution logs. Offering it after a day with
        // nothing in it hands the user a blank page and calls it a nudge.
        assertEquals(emptyList<ToolId>(), toolsOf(situation(nowMinute = 22 * 60)))
    }

    @Test
    fun `the reflection reason counts the tasks finished today`() {
        val first = task("t1", status = TaskStatus.COMPLETED)
        val second = task("t2", category = TaskCategory.OFFICE, status = TaskStatus.COMPLETED)
        val result = recommendTools(
            situation(
                nowMinute = 22 * 60,
                tasks = listOf(first, second),
                todaySegments = listOf(block("t1", 9 * 60, 10 * 60), block("t2", 10 * 60, 11 * 60)),
                explainedTaskIds = setOf("t1", "t2"),
                kwlLearnedTaskIds = setOf("t1"),
            ),
        )
        assertEquals(listOf(ToolId.REFLECTION), result.map(ToolSuggestion::tool))
        assertEquals(SuggestionReason.Count(2), result.single().reason)
    }

    @Test
    fun `two blocks for one task count as one finished task`() {
        // The count is in the sentence "今天已完成 N 项", so it has to be tasks. Counting segments
        // would tell a user who split one chapter across two sittings that they finished twice.
        val done = task("t1", status = TaskStatus.COMPLETED)
        val result = recommendTools(
            situation(
                nowMinute = 22 * 60,
                tasks = listOf(done),
                todaySegments = listOf(block("t1", 9 * 60, 10 * 60), block("t1", 10 * 60, 11 * 60)),
                explainedTaskIds = setOf("t1"),
                kwlLearnedTaskIds = setOf("t1"),
            ),
        )
        assertEquals(SuggestionReason.Count(1), result.single().reason)
    }

    // ------------------------------------------------------------------ R5 自我解释

    @Test
    fun `a finished task with no explanation recommends the self-explanation`() {
        val done = task("t1", name = "英语精读", category = TaskCategory.EXTRACURRICULAR, status = TaskStatus.COMPLETED)
        val result = recommendTools(
            situation(nowMinute = 12 * 60, tasks = listOf(done), todaySegments = listOf(block("t1", 11 * 60, 12 * 60))),
        )
        assertEquals(listOf(ToolId.SELF_EXPLANATION), result.map(ToolSuggestion::tool))
        assertEquals(SuggestionReason.TaskName("英语精读"), result.single().reason)
    }

    @Test
    fun `a finished task that already has an explanation is left alone`() {
        val done = task("t1", category = TaskCategory.EXTRACURRICULAR, status = TaskStatus.COMPLETED)
        val result = toolsOf(
            situation(
                nowMinute = 12 * 60,
                tasks = listOf(done),
                todaySegments = listOf(block("t1", 11 * 60, 12 * 60)),
                explainedTaskIds = setOf("t1"),
            ),
        )
        assertEquals(emptyList<ToolId>(), result)
    }

    @Test
    fun `a task that is still open is not asked for an explanation`() {
        // Explaining is something you do about work that is over. Asking mid-task is asking the
        // user to describe a moving target.
        val running = task("t1", status = TaskStatus.IN_PROGRESS)
        val result = toolsOf(
            situation(nowMinute = 12 * 60, tasks = listOf(running), todaySegments = listOf(block("t1", 11 * 60, 12 * 60))),
        )
        assertEquals(listOf(ToolId.SQ3R), result)
    }

    // ------------------------------------------------------------------ 顺序与上限

    @Test
    fun `an empty situation recommends nothing`() {
        assertEquals(emptyList<ToolId>(), toolsOf(situation()))
    }

    @Test
    fun `the strip never exceeds its cap`() {
        val reading = task("t1", status = TaskStatus.IN_PROGRESS)
        val done = task("t2", status = TaskStatus.COMPLETED)
        val result = recommendTools(
            situation(
                nowMinute = 22 * 60,
                tasks = listOf(reading, done),
                todaySegments = listOf(block("t1", 8 * 60, 9 * 60), block("t2", 9 * 60, 10 * 60)),
                dueReviewPointCount = 2,
            ),
        )
        assertEquals(MAX_SUGGESTIONS, result.size)
    }

    @Test
    fun `a due review point outranks a finished chapter`() {
        // The priority claim stated in the production file, pinned as a test: a spaced-repetition
        // interval that slips a day cannot be recovered, while a reflection can be written tomorrow.
        val reading = task("t1", status = TaskStatus.IN_PROGRESS)
        val result = toolsOf(
            situation(
                nowMinute = 10 * 60,
                tasks = listOf(reading),
                todaySegments = listOf(block("t1", 9 * 60, 9 * 60 + 50)),
                dueReviewPointCount = 1,
            ),
        )
        assertEquals(listOf(ToolId.REVIEW_POINTS, ToolId.SQ3R), result)
    }

    @Test
    fun `the KWL chart outranks the self-explanation for the same task`() {
        // Same task, two gaps. KWL first: it is the cheaper ask, and the explanation is worth more
        // once the user has just written down what they learned.
        val done = task("t1", status = TaskStatus.COMPLETED)
        val result = toolsOf(
            situation(nowMinute = 11 * 60, tasks = listOf(done), todaySegments = listOf(block("t1", 10 * 60, 11 * 60))),
        )
        assertEquals(listOf(ToolId.KWL, ToolId.SELF_EXPLANATION), result)
    }

    @Test
    fun `the scaffold and the KWL chart never fire for the same task`() {
        // They split on the task's status, which is what keeps the strip from asking the user to
        // choose between the during-reading method and the after-reading method.
        val running = task("t1", status = TaskStatus.IN_PROGRESS)
        val done = task("t2", status = TaskStatus.COMPLETED)
        val forRunning = toolsOf(
            situation(nowMinute = 22 * 60, tasks = listOf(running), todaySegments = listOf(block("t1", 8 * 60, 9 * 60))),
        )
        val forDone = toolsOf(
            situation(nowMinute = 22 * 60, tasks = listOf(done), todaySegments = listOf(block("t2", 8 * 60, 9 * 60))),
        )
        assertTrue(forRunning.contains(ToolId.SQ3R))
        assertTrue(!forRunning.contains(ToolId.KWL))
        assertTrue(forDone.contains(ToolId.KWL))
        assertTrue(!forDone.contains(ToolId.SQ3R))
    }

    @Test
    fun `a segment whose task is gone is ignored`() {
        // Segments outlive the tasks they were placed for (a task can be replaced or deleted), and
        // guessing at a name is worse than staying quiet.
        val result = toolsOf(
            situation(nowMinute = 10 * 60, tasks = emptyList(), todaySegments = listOf(block("ghost", 9 * 60, 10 * 60))),
        )
        assertEquals(emptyList<ToolId>(), result)
    }

    @Test
    fun `the pick does not depend on the order the segments arrive in`() {
        // Two blocks ending in the same minute is ordinary (a parallel track). Without the id
        // tie-break the suggestion would follow list order, so a recomposition that reordered the
        // segments would make the strip change its mind about which task it names.
        val first = task("t1", name = "并行一", status = TaskStatus.IN_PROGRESS)
        val second = task("t2", name = "并行二", status = TaskStatus.IN_PROGRESS)
        val forward = block("t1", 9 * 60, 10 * 60)
        val backward = block("t2", 9 * 60, 10 * 60)
        val one = recommendTools(
            situation(nowMinute = 11 * 60, tasks = listOf(first, second), todaySegments = listOf(forward, backward)),
        )
        val other = recommendTools(
            situation(nowMinute = 11 * 60, tasks = listOf(first, second), todaySegments = listOf(backward, forward)),
        )
        assertEquals(one, other)
    }
}
