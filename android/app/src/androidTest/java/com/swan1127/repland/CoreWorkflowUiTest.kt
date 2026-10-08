package com.swan1127.repland

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.PlanningAgentState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke-tests the user-controlled path through the actual Compose activity. It uses
 * a unique task instead of clearing application storage, so the test never erases a
 * developer's local data when it is run manually on a dedicated test device.
 */
@RunWith(AndroidJUnit4::class)
class CoreWorkflowUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun staged_capture_keeps_draft_unsaved_until_duration_is_confirmed() {
        val taskName = "分步创建 ${System.currentTimeMillis()}"
        openTaskCapture()
        composeRule.onNodeWithTag("task-capture-input").performTextReplacement(taskName)
        composeRule.onNodeWithTag("task-capture-arrange-today").performClick()
        waitForTag("task-capture-choose-duration")
        composeRule.onNodeWithTag("task-capture-choose-duration").performClick()
        waitForTag("task-capture-duration-30")
        composeRule.onNodeWithTag("task-capture-duration-30").performClick()
        val repository = (composeRule.activity.application as ReplandApplication).appContainer.taskRepository
        assertEquals(false, runBlocking { repository.observeTasks().first().any { it.displayName == taskName } })
        composeRule.onNodeWithTag("task-capture-save-duration").performClick()
        val saved = runBlocking {
            withTimeout(10_000) {
                repository.observeTasks().first { tasks -> tasks.any { it.displayName == taskName } }
                    .single { it.displayName == taskName }
            }
        }
        assertEquals(30, saved.totalDurationMinutes)
        assertEquals(null, saved.dueDate)
        assertEquals(java.time.LocalDate.now(), saved.scheduledForDate)
        assertEquals(TaskStatus.NOT_STARTED, saved.status)
    }

    @Test
    fun capture_exposes_a_free_deadline_picker_beyond_shortcuts() {
        val taskName = "自由截止日 ${System.currentTimeMillis()}"
        openTaskCapture()
        composeRule.onNodeWithTag("task-capture-input").performTextReplacement(taskName)
        composeRule.onNodeWithTag("task-capture-arrange-today").performClick()
        waitForTag("task-capture-choose-deadline")
        composeRule.onNodeWithTag("task-capture-choose-deadline").performClick()
        waitForTag("task-capture-deadline-custom")
        composeRule.onNodeWithTag("task-capture-deadline-custom").performClick()
        waitForTag("task-date-picker")
        // The date picker is modal. Close the exploratory branch through a real task save so
        // this Compose activity remains in the same clean state as a person would leave it.
        composeRule.onNodeWithTag("task-date-picker-dismiss").performClick()
        composeRule.onNodeWithTag("task-capture-deadline-today").performClick()
        waitForTag("task-card-$taskName")
    }

    @Test
    fun capture_accepts_a_custom_duration_without_forcing_a_preset() {
        val taskName = "自定义时长 ${System.currentTimeMillis()}"
        openTaskCapture()
        composeRule.onNodeWithTag("task-capture-input").performTextReplacement(taskName)
        composeRule.onNodeWithTag("task-capture-arrange-today").performClick()
        waitForTag("task-capture-choose-duration")
        composeRule.onNodeWithTag("task-capture-choose-duration").performClick()
        composeRule.onNodeWithTag("task-capture-duration-custom").performClick()
        waitForTag("task-capture-duration-custom-input")
        composeRule.onNodeWithTag("task-capture-duration-custom-input").performTextInput("45")
        composeRule.onNodeWithTag("task-capture-save-duration").performClick()

        val repository = (composeRule.activity.application as ReplandApplication).appContainer.taskRepository
        val saved = runBlocking {
            withTimeout(10_000) {
                repository.observeTasks().first { tasks -> tasks.any { it.displayName == taskName } }
                    .single { it.displayName == taskName }
            }
        }
        assertEquals(45, saved.totalDurationMinutes)
        assertEquals(java.time.LocalDate.now(), saved.scheduledForDate)
        assertEquals(null, saved.dueDate)
    }

    @Test
    fun status_start_then_complete_without_timer_keeps_actual_duration_unknown() {
        val taskName = "直接完成 ${System.currentTimeMillis()}"
        openTaskCapture()
        composeRule.onNodeWithTag("task-capture-input").performTextReplacement(taskName)
        composeRule.onNodeWithTag("task-capture-arrange-today").performClick()
        waitForTag("task-capture-choose-duration")
        composeRule.onNodeWithTag("task-capture-choose-duration").performClick()
        waitForTag("task-capture-duration-15")
        composeRule.onNodeWithTag("task-capture-duration-15").performClick()
        composeRule.onNodeWithTag("task-capture-save-duration").performClick()
        waitForTag("task-card-$taskName")
        composeRule.onNodeWithTag("task-card-$taskName").performClick()
        waitForTag("task-detail-scroll")
        composeRule.onNodeWithTag("start-task").performClick()
        val repository = (composeRule.activity.application as ReplandApplication).appContainer.taskRepository
        runBlocking {
            withTimeout(10_000) {
                repository.observeTasks().first { tasks -> tasks.any { it.displayName == taskName && it.status == TaskStatus.IN_PROGRESS } }
            }
        }
        composeRule.onNodeWithTag("task-detail-scroll").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("complete-task").performClick()
        val completed = runBlocking {
            withTimeout(10_000) {
                repository.observeTasks().first { tasks -> tasks.any { it.displayName == taskName && it.status == TaskStatus.COMPLETED } }
                    .single { it.displayName == taskName }
            }
        }
        assertEquals(null, completed.completionSummary)
        assertEquals(100, completed.progressPercent)
        assertEquals(null, completed.actualDurationMinutes)
        val logs = runBlocking { repository.observeExecutionLogs(completed.id).first() }
        assertEquals(true, logs.isNotEmpty())
        assertEquals(true, logs.all { it.feedback.actualDurationMinutes == null })
    }

    @Test
    fun editable_voice_capture_can_become_a_task_draft_without_auto_saving() {
        openTaskCapture()
        waitForTag("voice-capture")
        composeRule.onNodeWithTag("voice-capture").performClick()
        waitForTag("voice-transcript")
        composeRule.onNodeWithTag("voice-transcript").performTextInput("准备英语听力")
        composeRule.onNodeWithTag("voice-to-task").performClick()
        waitForTag("task-capture-input")
        composeRule.onNodeWithTag("task-capture-input").assertTextContains("准备英语听力")
    }

    @Test
    fun task_entry_feedback_and_local_controls_are_user_driven() {
        val taskName = "UI 回归 ${System.currentTimeMillis()}"
        val feedbackContent = "已完成 UI 回归反馈"

        // Creation belongs to the task inbox; the day canvas is now for arranging objects.
        openTaskCapture()
        composeRule.onNodeWithTag("task-capture-input").performTextReplacement(taskName)
        composeRule.onNodeWithTag("task-capture-save-inbox").performClick()

        waitForTag("task-card-$taskName")
        composeRule.onNodeWithText("已保存“$taskName”到待安排。", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("task-card-$taskName").performClick()
        waitForTag("task-detail-scroll")
        repeat(4) {
            composeRule.onNodeWithTag("task-detail-scroll").performTouchInput { swipeUp() }
        }
        waitForTag("record-feedback")
        composeRule.onNodeWithTag("record-feedback").performClick()
        waitForTag("feedback-content")
        composeRule.onNodeWithTag("feedback-content").performTextInput(feedbackContent)
        composeRule.onNodeWithTag("feedback-confirm").performClick()

        val task = runBlocking {
            withTimeout(10_000) {
                (composeRule.activity.application as ReplandApplication).appContainer.taskRepository
                    .observeTasks()
                    .first { tasks ->
                        tasks.any { it.displayName == taskName && it.completionSummary == feedbackContent }
                    }
                    .single { it.displayName == taskName }
            }
        }
        // A feedback record is evidence only; it must not silently complete the task.
        assertEquals(TaskStatus.NOT_STARTED, task.status)

        composeRule.onNodeWithTag("task-detail-back").performClick()
        composeRule.onNodeWithTag("navigation-agent").performClick()
        waitForTag("agent-import-timetable")
        composeRule.onNodeWithTag("agent-import-timetable").performClick()
        // The document picker is exposed, but no PDF can write constraints before its
        // preview is explicitly confirmed (covered by PdfTimetableImporterTest).
        waitForTag("import-timetable-pdf")

        composeRule.onNodeWithTag("navigation-mine").performClick()
        waitForTag("local-reminders-switch")
    }

    @Test
    fun bounded_agent_requires_preview_then_falls_back_without_changing_the_task_or_plan() {
        val taskName = "Agent UI ${System.currentTimeMillis()}"
        composeRule.onNodeWithTag("navigation-mine").performClick()
        waitForTag("ai-advisor-switch")
        val switchNode = composeRule.onNodeWithTag("ai-advisor-switch").fetchSemanticsNode()
        if (switchNode.config[SemanticsProperties.ToggleableState] != ToggleableState.On) {
            composeRule.onNodeWithTag("ai-advisor-switch").performClick()
            composeRule.waitForIdle()
            if (composeRule.onAllNodesWithTag("ai-consent-confirm", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                composeRule.onNodeWithTag("ai-consent-confirm").performClick()
            }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onNodeWithTag("ai-advisor-switch").fetchSemanticsNode()
                    .config[SemanticsProperties.ToggleableState] == ToggleableState.On
            }
        }

        openTaskCapture()
        composeRule.onNodeWithTag("task-capture-input").performTextReplacement(taskName)
        composeRule.onNodeWithTag("task-capture-save-inbox").performClick()
        waitForTag("task-card-$taskName")
        composeRule.onNodeWithTag("task-card-$taskName").performClick()
        waitForTag("task-detail-scroll")
        repeat(4) {
            composeRule.onNodeWithTag("task-detail-scroll").performTouchInput { swipeUp() }
        }
        waitForTag("request-ai-advice")
        composeRule.onNodeWithTag("request-ai-advice").performClick()
        waitForTag("assistant-prompt")
        composeRule.onNodeWithTag("assistant-prompt").performTextInput("帮我理解这项任务")
        composeRule.onNodeWithTag("assistant-submit").performClick()
        waitForTag("ai-request-confirm")
        composeRule.onNodeWithTag("ai-request-confirm").performClick()
        val workflow = (composeRule.activity.application as ReplandApplication)
            .appContainer.planningAgentWorkflow
        composeRule.waitUntil(timeoutMillis = 15_000) {
            workflow.state.value is PlanningAgentState.FailedFallback
        }
        waitForTag("planning-agent-outcome")

        val task = runBlocking {
            withTimeout(10_000) {
                (composeRule.activity.application as ReplandApplication).appContainer.taskRepository
                    .observeTasks()
                    .first { tasks -> tasks.any { it.displayName == taskName } }
                    .single { it.displayName == taskName }
            }
        }
        assertEquals(TaskStatus.NOT_STARTED, task.status)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun capture_close_reopen_and_activity_recreation_keep_custom_duration_without_creating_task() {
        val taskName = "草稿恢复 ${System.currentTimeMillis()}"
        openTaskCapture()
        composeRule.onNodeWithTag("task-capture-input").performTextReplacement(taskName)
        composeRule.onNodeWithTag("task-capture-arrange-today").performClick()
        waitForTag("task-capture-choose-duration")
        composeRule.onNodeWithTag("task-capture-choose-duration").performClick()
        waitForTag("task-capture-duration-custom")
        composeRule.onNodeWithTag("task-capture-duration-custom").performClick()
        composeRule.onNodeWithTag("task-capture-duration-custom-input").performTextReplacement("45")
        composeRule.onNodeWithTag("task-capture-close").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add-task").performClick()
        waitForTag("task-capture-duration-custom-input")
        composeRule.onNodeWithTag("task-capture-duration-custom-input").assertTextContains("45")
        composeRule.activityRule.scenario.recreate()
        waitForTag("task-capture-duration-custom-input")
        composeRule.onNodeWithTag("task-capture-duration-custom-input").assertTextContains("45")
        val container = (composeRule.activity.application as ReplandApplication).appContainer
        assertFalse(runBlocking { container.taskRepository.observeTasks().first().any { it.displayName == taskName } })
        composeRule.onNodeWithTag("task-capture-back").performClick()
        composeRule.onNodeWithTag("task-capture-back").performClick()
        composeRule.onNodeWithTag("task-capture-input").assertTextContains(taskName)
        composeRule.onNodeWithTag("task-capture-discard").performClick()
        assertFalse(runBlocking { container.taskRepository.observeTasks().first().any { it.displayName == taskName } })
        composeRule.onNodeWithTag("task-capture-confirm-discard").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onNodeWithTag("task-capture-input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty()
        }
        composeRule.onNodeWithTag("task-capture-close").performClick()
    }

    private fun openTaskCapture() {
        waitForTag("navigation-tasks")
        composeRule.onNodeWithTag("navigation-tasks").performClick()
        waitForTag("add-task")
        composeRule.onNodeWithTag("add-task").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("task-capture-input").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("task-capture-back").fetchSemanticsNodes().isNotEmpty()
        }
        repeat(2) {
            if (composeRule.onAllNodesWithTag("task-capture-input").fetchSemanticsNodes().isEmpty()) {
                composeRule.onNodeWithTag("task-capture-back").performClick()
                composeRule.waitForIdle()
            }
        }
        waitForTag("task-capture-input")
    }
}
