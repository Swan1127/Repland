package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        waitTag("task-filter-INBOX")
        rule.onNodeWithTag("task-filter-INBOX").performClick()
        waitTag("task-list-scroll")
        rule.onNodeWithTag("task-list-scroll").performScrollToNode(hasTestTag("task-card-$title"))
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
