package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.SemanticsMatcher.Companion.keyIsDefined
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
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val frame = java.util.concurrent.CountDownLatch(1)
        rule.runOnUiThread { rule.activity.window.decorView.postOnAnimation {
            rule.activity.window.decorView.postOnAnimation { frame.countDown() }
        } }
        check(frame.await(3, java.util.concurrent.TimeUnit.SECONDS))
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { java.io.File(rule.activity.getExternalFilesDir(null), name).outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }
    private fun waitTag(tag: String) {
        try { rule.waitUntil(10_000) {
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        } } catch (failure: Throwable) {
            screenshot("qa-placement29-navigation-failure.png")
            throw failure
        }
    }
    private fun visible(tag: String): SemanticsNodeInteraction {
        val node = rule.onNodeWithTag(tag)
        // Native IME resizing is not part of Compose's idle clock. Re-read and
        // scroll within the settled window, retaining the visibility requirement.
        rule.waitUntil(5_000) { runCatching {
            if (rule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollBy)))
                    .fetchSemanticsNodes().isNotEmpty()) node.performScrollTo()
            node.assertIsDisplayed()
            val observed = node.fetchSemanticsNode()
            val size = observed.layoutInfo.coordinates.size
            check(observed.boundsInRoot.width >= size.width - 1 && observed.boundsInRoot.height >= size.height - 1)
        }.isSuccess }
        return node.assertIsDisplayed()
    }

    @Test fun event_chunk_shown_in_sheet_is_exactly_committed_without_rewriting_task_duration() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val app = (rule.activity.application as ReplandApplication).appContainer
        val id = "qa-placement29-${UUID.randomUUID()}"
        val name = "本次安排验收29-${id.takeLast(6)}"
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
        runBlocking { app.taskRepository.save(TaskDraft(id, name, "", TaskCategory.COURSE,
            TaskPriority.MEDIUM, 1, 300, null)) }
        val task = runBlocking { app.taskRepository.observeTasks().first().single { it.id == id } }
        waitTag("navigation-today"); visible("navigation-today").performClick()
        waitTag("today-page-scroll")
        rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-schedule"))
        visible("month-schedule").performClick()
        rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-date-$date"))
        visible("month-date-$date").performClick()
        rule.onNodeWithTag("today-page-scroll").performScrollToNode(
            hasTestTag("empty-event-library-trigger") or hasTestTag("event-library-trigger"))
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("empty-event-library-trigger").fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodesWithTag("event-library-trigger").fetchSemanticsNodes().isNotEmpty() }
        val trigger = if (rule.onAllNodesWithTag("event-library-trigger").fetchSemanticsNodes().isNotEmpty())
            "event-library-trigger" else "empty-event-library-trigger"
        visible(trigger).performClick(); waitTag("event-object-$id"); visible("event-object-$id").performClick()
        rule.onNodeWithText("本次安排 240 分钟").assertExists()
        visible("event-minute-input").performTextReplacement("99")
        visible("event-place-confirm").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("event-minute-input").assertTextContains("99")
        screenshot("qa-placement29-invalid.png")
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
        waitTag("task-groups-scroll")
        rule.onNodeWithTag("task-groups-scroll").performScrollToNode(hasTestTag("task-group-更晚"))
        val expand = hasText("查看全部") and hasAnyAncestor(hasTestTag("task-group-更晚"))
        if (rule.onAllNodes(expand).fetchSemanticsNodes().isNotEmpty()) rule.onNode(expand).assertIsDisplayed().performClick()
        rule.onNodeWithTag("task-groups-scroll").performScrollToNode(hasTestTag("task-card-${task.displayName}"))
        rule.onNodeWithTag("task-card-${task.displayName}").assertExists()
    }
}
