package com.swan1127.repland

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
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
    fun task_entry_feedback_plan_confirmation_and_local_controls_are_user_driven() {
        val taskName = "UI 回归 ${System.currentTimeMillis()}"
        val feedbackContent = "已完成 UI 回归反馈"

        waitForTag("add-task")
        composeRule.onNodeWithTag("add-task").performClick()
        waitForTag("task-editor-name")
        composeRule.onNodeWithTag("task-editor-name").performTextInput(taskName)
        composeRule.onNodeWithTag("task-editor-duration").performTextInput("30")
        composeRule.onNodeWithTag("task-editor-save").performClick()

        waitForTag("task-card-$taskName")
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
        composeRule.onNodeWithTag("navigation-time").performClick()
        waitForTag("generate-plan-draft")
        composeRule.onNodeWithTag("generate-plan-draft").performClick()
        waitForTag("accept-plan-draft")
        composeRule.onNodeWithTag("accept-plan-draft").performClick()

        runBlocking {
            withTimeout(10_000) {
                (composeRule.activity.application as ReplandApplication).appContainer.planRepository
                    .observeCurrentPlan()
                    .first { plan -> plan?.orderedTaskIds?.contains(task.id) == true }
            }
        }
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

        composeRule.onNodeWithTag("navigation-tasks").performClick()
        waitForTag("add-task")
        composeRule.onNodeWithTag("add-task").performClick()
        waitForTag("task-editor-name")
        composeRule.onNodeWithTag("task-editor-name").performTextInput(taskName)
        composeRule.onNodeWithTag("task-editor-save").performClick()
        waitForTag("task-card-$taskName")
        composeRule.onNodeWithTag("task-card-$taskName").performClick()
        waitForTag("task-detail-scroll")
        repeat(4) {
            composeRule.onNodeWithTag("task-detail-scroll").performTouchInput { swipeUp() }
        }
        waitForTag("request-ai-advice")
        composeRule.onNodeWithTag("request-ai-advice").performClick()
        waitForTag("ai-request-confirm")
        composeRule.onNodeWithTag("ai-request-confirm").performClick()
        waitForTag("planning-agent-outcome")
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.ai_failure_not_configured),
            useUnmergedTree = true,
        ).assertExists()

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
}
