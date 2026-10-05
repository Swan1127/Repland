package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Activity -> today's adapter -> ViewModel -> Room; QA package only. */
class PlacementNumericWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun waitTag(tag: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun visible(tag: String): SemanticsNodeInteraction {
        val node = rule.onNodeWithTag(tag)
        if (rule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollBy)))
                .fetchSemanticsNodes().isNotEmpty()) node.performScrollTo()
        return node.assertIsDisplayed()
    }

    @Test fun event_chunk_shown_in_sheet_is_exactly_committed_without_rewriting_task_duration() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val app = (rule.activity.application as ReplandApplication).appContainer
        val id = "qa-placement29-${UUID.randomUUID()}"
        val date = LocalDate.now().plusDays(1)
        val before = runBlocking { app.planRepository.observeCurrentPlan().first() }
        val tasksBefore = runBlocking { app.taskRepository.observeTasks().first() }
        val entries = runBlocking { ScheduleTimeline.entries(date,
            app.timeRepository.observeWeeklyBlocks().first(), app.timeRepository.observeDateOverrides().first(),
            before?.segments.orEmpty(), tasksBefore,
            app.timeRepository.observeTimeConstraintSettings().first().semesterFirstWeekMonday) }
        val start = (0..20).map { it * 60 }.first { candidate ->
            entries.none { candidate < it.endMinute && candidate + 240 > it.startMinute }
        }
        runBlocking { app.taskRepository.save(TaskDraft(id, "本次安排验收29", "", TaskCategory.COURSE,
            TaskPriority.MEDIUM, null, 300, null)) }
        val task = runBlocking { app.taskRepository.observeTasks().first().single { it.id == id } }
        waitTag("navigation-today"); visible("navigation-today").performClick()
        waitTag("month-schedule"); visible("month-schedule").performClick()
        waitTag("month-date-$date"); visible("month-date-$date").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("empty-event-library-trigger").fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodesWithTag("event-library-trigger").fetchSemanticsNodes().isNotEmpty() }
        val trigger = if (rule.onAllNodesWithTag("event-library-trigger").fetchSemanticsNodes().isNotEmpty())
            "event-library-trigger" else "empty-event-library-trigger"
        visible(trigger).performClick(); waitTag("event-object-$id"); visible("event-object-$id").performClick()
        rule.onNodeWithText("本次安排 240 分钟").assertExists()
        visible("event-minute-input").performTextReplacement("99")
        visible("event-place-confirm").assertIsNotEnabled().performClick()
        assertEquals(before, runBlocking { app.planRepository.observeCurrentPlan().first() })
        assertEquals(task, runBlocking { app.taskRepository.observeTasks().first().single { it.id == id } })
        visible("event-hour-input").performTextReplacement((start / 60).toString())
        visible("event-minute-input").performTextReplacement("00")
        visible("event-place-confirm").assertIsEnabled().performClick()
        val saved = runBlocking { withTimeout(10_000) { app.planRepository.observeCurrentPlan().first {
            it?.segments?.any { segment -> segment.taskId == id } == true
        } } }!!
        val segment = saved.segments.single { it.taskId == id }
        assertEquals(date, segment.date); assertEquals(start, segment.startMinute)
        assertEquals(start + 240, segment.endMinute)
        assertEquals(task, runBlocking { app.taskRepository.observeTasks().first().single { it.id == id } })
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("event-place-confirm").fetchSemanticsNodes().isEmpty() }
        visible("navigation-tasks").performClick()
        waitTag("task-card-${task.displayName}")
        rule.onNodeWithTag("task-card-${task.displayName}").assertExists()
    }
}
