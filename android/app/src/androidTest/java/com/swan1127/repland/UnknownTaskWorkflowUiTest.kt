package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real page -> ViewModel -> Room path in the isolated QA application. */
class UnknownTaskWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun waitTag(tag: String) = rule.waitUntil(15_000) {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun minimal_capture_and_unchanged_edit_keep_optional_fields_unknown() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val container = (rule.activity.application as ReplandApplication).appContainer
        val title = "未知输入${System.currentTimeMillis()}"
        val beforePlan = runBlocking { container.planRepository.observeCurrentPlan().first() }
        waitTag("navigation-tasks")
        rule.onNodeWithTag("navigation-tasks").performClick()
        waitTag("add-task")
        rule.onNodeWithTag("add-task").performClick()
        waitTag("task-capture-input")
        rule.onNodeWithTag("task-capture-input").performTextReplacement(title)
        rule.onNodeWithTag("task-capture-save-inbox").assertIsDisplayed().performClick()
        rule.waitUntil(15_000) { runBlocking { container.taskRepository.observeTasks().first().any { it.displayName == title } } }
        val task = runBlocking { container.taskRepository.observeTasks().first().single { it.displayName == title } }
        assertEquals(TaskCategory.UNSPECIFIED, task.category)
        assertEquals(TaskPriority.UNSPECIFIED, task.userPriority)
        assertNull(task.estimatedDays); assertNull(task.totalDurationMinutes); assertNull(task.dueDate)
        assertNull(task.scheduledForDate)
        // Assert the save receipt when it arrives, before navigating / scrolling
        // a retained inbox. It is transient and may expire during that work.
        waitTag("page-save-snackbar")
        rule.onNodeWithTag("page-save-snackbar").assertIsDisplayed()
        waitTag("task-filter-INBOX")
        rule.onNodeWithTag("task-filter-INBOX").performClick()
        waitTag("task-list-scroll")
        rule.onNodeWithTag("task-list-scroll").performScrollToNode(hasTestTag("task-card-$title"))
        val cardBounds = rule.onNodeWithTag("task-card-$title").getUnclippedBoundsInRoot()
        val listBounds = rule.onNodeWithTag("task-list-scroll").getUnclippedBoundsInRoot()
        assertTrue("Task card must be fully inside the list before its real click: card=$cardBounds list=$listBounds",
            cardBounds.top >= listBounds.top && cardBounds.bottom <= listBounds.bottom)
        // Synchronize the actual draw before injecting a click after the list has scrolled.
        val image = rule.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(rule.activity.getExternalFilesDir(null), "c04-before-open-task.png").outputStream().use {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        // An outgoing Snackbar may remain in semantics after it is offscreen.
        if (rule.onNodeWithTag("page-save-snackbar").isDisplayed()) {
            val noticeBounds = rule.onNodeWithTag("page-save-snackbar").getUnclippedBoundsInRoot()
            assertTrue("The visible save receipt must not cover the task card: card=$cardBounds receipt=$noticeBounds",
                cardBounds.bottom <= noticeBounds.top)
        }
        rule.onNodeWithTag("task-card-$title").performClick()
        waitTag("task-detail-scroll")
        rule.onNodeWithTag("task-detail-scroll").performScrollToNode(hasText("编辑任务"))
        rule.onNodeWithText("编辑任务").performClick()
        waitTag("task-editor-name")
        rule.activityRule.scenario.recreate()
        waitTag("task-editor-name")
        rule.onNodeWithTag("task-editor-save").assertIsDisplayed().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("task-editor-name").fetchSemanticsNodes().isEmpty() }
        val saved = runBlocking { container.taskRepository.observeTasks().first().single { it.id == task.id } }
        assertEquals(task.inputSources, saved.inputSources)
        assertEquals(TaskPriority.UNSPECIFIED, saved.userPriority)
        assertEquals(TaskCategory.UNSPECIFIED, saved.category)
        assertNull(saved.estimatedDays); assertNull(saved.totalDurationMinutes)
        assertEquals(beforePlan, runBlocking { container.planRepository.observeCurrentPlan().first() })
    }
}
