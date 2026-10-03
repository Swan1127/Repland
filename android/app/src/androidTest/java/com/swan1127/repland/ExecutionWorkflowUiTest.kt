package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExecutionWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun activity_recreation_restores_round_and_finishing_offers_preview_without_completing_task() {
        val app = (rule.activity.application as ReplandApplication).appContainer
        org.junit.Assume.assumeTrue("This workflow requires a QA device without an unrelated active round.",
            runBlocking { app.executionSessionRepository.observeActive().first() == null })
        val id = "qa-execution-${System.currentTimeMillis()}"
        runBlocking {
            app.taskRepository.save(TaskDraft(id, "执行闭环测试", "", TaskCategory.COURSE, TaskPriority.HIGH, 1, 60, null))
            app.planRepository.placeTask(id, LocalDate.now().plusDays(1), 600, 630, "focus")
            val slot = app.planRepository.observeCurrentPlan().first()!!.segments.first { it.taskId == id }
            app.executionSessionRepository.start(slot.id)
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("resume-execution").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("accept-plan-draft").assertDoesNotExist()
        assertNull(runBlocking { app.planRepository.observeDraft().first() })
        rule.onNodeWithTag("resume-execution").performScrollTo().performClick()
        rule.onNodeWithTag("execution-pause-resume").performClick()
        runBlocking { withTimeout(5_000) { app.executionSessionRepository.observeActive().first { it?.isPaused == true } } }
        rule.onNodeWithText("返回页面 · 保留本轮").performScrollTo().performClick()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("resume-execution").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("resume-execution").performScrollTo().performClick()
        rule.onNodeWithText("本轮已暂停").assertExists()
        rule.onNodeWithTag("accept-plan-draft").assertDoesNotExist()
        rule.onNodeWithTag("execution-continue").performScrollTo().performClick()
        runBlocking { withTimeout(5_000) { app.executionSessionRepository.observeActive().first { it == null } } }
        assertEquals(TaskStatus.IN_PROGRESS, runBlocking { app.taskRepository.observeTasks().first().first { it.id == id }.status })
        val planBefore = runBlocking { app.planRepository.observeCurrentPlan().first()!!.id }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("execution-replan").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("execution-replan").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("accept-plan-draft").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(planBefore, runBlocking { app.planRepository.observeCurrentPlan().first()!!.id })
    }
}
