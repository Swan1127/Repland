package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.agent.AgentCenterScreen
import com.swan1127.repland.ui.theme.ReplandTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantReadOperationUiTest {
    @get:Rule val rule = createComposeRule()
    private val task = Task("a", "", "英语", TaskCategory.COURSE, TaskPriority.HIGH, 1, 30, null, TaskStatus.NOT_STARTED, null, null, null, 0, 0, 0)
    @Test fun offline_query_uses_local_service_without_advisor_or_capture() {
        var calls = 0
        rule.setContent { ReplandTheme { AgentCenterScreen(
            canRefineWithAi = false, onRefineWithContext = { calls++; ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.TIMEOUT) },
            onQueryTasks = { listOf(task) }, onSaveTasks = { fail("Query cannot save") }, onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-prompt").performTextInput("查询今天的任务")
        rule.onNodeWithTag("agent-preview").performScrollTo().performClick()
        rule.onNodeWithTag("agent-read-result").performScrollTo().assertExists()
        rule.onNodeWithTag("agent-confirm-tasks").assertDoesNotExist()
        assertEquals(0, calls)
    }
    @Test fun query_displays_local_facts_opens_same_task_and_never_offers_confirmation() {
        var saved = 0; var opened: String? = null
        rule.setContent { ReplandTheme { AgentCenterScreen(
            canRefineWithAi = true,
            onRefineWithContext = { request ->
                assertTrue(ArrangementAdviceOperation.QUERY_TASKS in request.allowedOperations)
                ArrangementAssistantAdviceResult.Advice(ArrangementAssistantAdvice(emptyList(), "模型说它已完成", ArrangementAdviceOperation.QUERY_TASKS, TaskQueryScope.TODAY))
            }, onQueryTasks = { listOf(task) }, onOpenTask = { opened = it },
            onSaveTasks = { saved++ }, onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-prompt").performTextInput("查询今天的任务")
        rule.onNodeWithTag("agent-preview").performScrollTo().performClick()
        rule.onNodeWithTag("agent-read-result").performScrollTo().assertExists()
        rule.onNodeWithText("今天的任务 · 1 项").assertExists()
        rule.onNodeWithText("英语 · 30 分钟").performScrollTo().performClick()
        assertEquals("a", opened); assertEquals(0, saved)
        rule.onNodeWithText("模型说它已完成").assertDoesNotExist()
        rule.onNodeWithTag("agent-confirm-tasks").assertDoesNotExist()
    }
    @Test fun explanation_uses_local_reasons_and_does_not_apply_model_scores() {
        rule.setContent { ReplandTheme { AgentCenterScreen(
            canRefineWithAi = true, existingTasks = listOf(ArrangementExistingTask("a", "英语", TaskCategory.COURSE, 30)),
            onRefineWithContext = { ArrangementAssistantAdviceResult.Advice(ArrangementAssistantAdvice(emptyList(), "模型分数999", ArrangementAdviceOperation.EXPLAIN_ORDER, taskReference = "a")) },
            onExplainOrder = { LocalPriorityRanker.rank(listOf(task), today = java.time.LocalDate.now()).single() },
            onSaveTasks = {}, onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-prompt").performTextInput("为什么英语排在这里")
        rule.onNodeWithTag("agent-preview").performScrollTo().performClick()
        rule.onNodeWithTag("agent-read-result").performScrollTo().assertExists()
        rule.onNodeWithText("英语的排序依据").assertExists()
        rule.onNodeWithText("模型分数999").assertDoesNotExist()
        rule.onNodeWithTag("agent-confirm-tasks").assertDoesNotExist()
    }
    @Test fun formulate_dispatches_to_shared_preview_and_keeps_prompt() {
        var formulated = 0
        rule.setContent { ReplandTheme { AgentCenterScreen(
            canRefineWithAi = true,
            onRefineWithContext = { request ->
                assertTrue(ArrangementAdviceOperation.FORMULATE_PLAN in request.allowedOperations)
                ArrangementAssistantAdviceResult.Advice(ArrangementAssistantAdvice(emptyList(), "", ArrangementAdviceOperation.FORMULATE_PLAN))
            }, onFormulatePlan = { formulated++ },
            onSaveTasks = {}, onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-prompt").performTextInput("帮我制定计划")
        rule.onNodeWithTag("agent-preview").performScrollTo().performClick()
        rule.waitForIdle(); assertEquals(1, formulated)
        rule.onNodeWithTag("agent-prompt").assertTextContains("帮我制定计划")
        rule.onNodeWithTag("agent-confirm-tasks").assertDoesNotExist()
    }
    @Test fun pending_model_response_disables_provisional_confirmation() {
        val response = kotlinx.coroutines.CompletableDeferred<ArrangementAssistantAdviceResult>()
        rule.setContent { ReplandTheme { AgentCenterScreen(
            canRefineWithAi = true, onRefineWithContext = { response.await() },
            onSaveTasks = {}, onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-prompt").performTextInput("英语30分钟")
        rule.onNodeWithTag("agent-preview").performScrollTo().performClick()
        rule.onNodeWithTag("agent-confirm-tasks").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("agent-preview").performScrollTo().assertIsNotEnabled()
        rule.runOnIdle { response.complete(ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.TIMEOUT)) }
        rule.waitForIdle()
    }
}
