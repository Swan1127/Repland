package com.swan1127.repland

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.ui.agent.AgentCenterScreen
import com.swan1127.repland.ui.theme.ReplandTheme
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.domain.model.ArrangementAssistantAdvice
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceResult
import com.swan1127.repland.domain.model.ArrangementCandidate
import com.swan1127.repland.domain.model.ArrangementPlacementSource
import com.swan1127.repland.domain.model.ArrangementTimeHint
import com.swan1127.repland.domain.model.TaskCategory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentCenterUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun provider_change_invalidates_an_inflight_reply_even_when_ai_remains_enabled() {
        val revision = androidx.compose.runtime.mutableStateOf(1L)
        val response = kotlinx.coroutines.CompletableDeferred<ArrangementAssistantAdviceResult>()
        var calls = 0
        var stored: com.swan1127.repland.domain.model.AssistantWorkspace? = null
        rule.setContent { ReplandTheme { AgentCenterScreen(
            providerRevision = revision.value, canRefineWithAi = true,
            onRefineWithAi = { _, _, _ -> calls++; response.await() }, onWorkspaceChanged = { stored = it },
            onSaveTasks = {}, onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-prompt").performTextInput("复习英语")
        rule.onNodeWithTag("agent-preview").performClick()
        rule.waitUntil { calls == 1 }
        val expected = stored
        rule.runOnIdle { revision.value = 2L }
        rule.waitForIdle()
        rule.runOnIdle { response.complete(ArrangementAssistantAdviceResult.Advice(
            ArrangementAssistantAdvice(listOf(ArrangementCandidate("旧任务", TaskCategory.COURSE, 30, ArrangementTimeHint(600), emptySet(), ArrangementPlacementSource.AI_SUGGESTED)), "旧配置"))) }
        rule.waitForIdle()
        assertEquals(expected, stored)
    }

    @Test fun missing_availability_exposes_a_real_configuration_action() {
        var opened = 0
        rule.setContent { ReplandTheme { AgentCenterScreen(
            hasAvailability = false, onConfigureAvailability = { opened++ },
            onSaveTasks = {}, onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-configure-availability").performClick()
        assertEquals(1, opened)
    }

    @Test fun assistant_text_and_proposals_restore_after_page_recreation() {
        var stored: com.swan1127.repland.domain.model.AssistantWorkspace? = null
        val visible = androidx.compose.runtime.mutableStateOf(true)
        rule.setContent { if (visible.value) ReplandTheme { AgentCenterScreen(
            initialWorkspace = stored, onWorkspaceChanged = { stored = it },
            onSaveTasks = {}, onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-prompt").performTextInput("15:00 复习英语 30 分钟")
        rule.onNodeWithTag("agent-preview").performClick()
        val id = stored!!.proposals.single().id
        rule.runOnIdle { visible.value = false }
        rule.runOnIdle { visible.value = true }
        rule.onNodeWithTag("agent-prompt").assertTextContains("15:00 复习英语 30 分钟", substring = false)
        rule.onNodeWithTag("agent-confirm-tasks").fetchSemanticsNode()
        assertEquals(id, stored!!.proposals.single().id)
    }

    @Test fun stale_restored_workspace_requires_regeneration() {
        val p = com.swan1127.repland.domain.model.AssistantTaskProposal("id", "英语", TaskCategory.COURSE, 30,
            ArrangementTimeHint(600), emptySet(), ArrangementPlacementSource.USER_EXPLICIT)
        val stored = com.swan1127.repland.domain.model.AssistantWorkspace(LocalDate.now().minusDays(1), "英语", listOf(p))
        rule.setContent { ReplandTheme { AgentCenterScreen(
            initialWorkspace = stored, onSaveTasks = {}, onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-confirm-tasks").assertIsNotEnabled()
        rule.onNodeWithTag("agent-refresh-draft").fetchSemanticsNode()
    }

    @Test fun failed_ai_refresh_does_not_validate_a_stale_workspace() {
        val p = com.swan1127.repland.domain.model.AssistantTaskProposal("id", "英语", TaskCategory.COURSE, 30,
            ArrangementTimeHint(600), emptySet(), ArrangementPlacementSource.USER_EXPLICIT)
        val stored = com.swan1127.repland.domain.model.AssistantWorkspace(LocalDate.now(), "英语", listOf(p), sourceRevision = "old")
        var calls = 0
        rule.setContent { ReplandTheme { AgentCenterScreen(
            initialWorkspace = stored, contextRevision = "new", canRefineWithAi = true,
            onRefineWithAi = { _, _, _ -> calls++; ArrangementAssistantAdviceResult.Unavailable(
                com.swan1127.repland.domain.model.AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED) },
            onSaveTasks = {}, onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-refine-with-ai").performScrollTo().performClick()
        rule.waitUntil { calls == 1 }
        rule.waitForIdle()
        rule.onNodeWithTag("agent-confirm-tasks").assertIsNotEnabled()
        rule.onNodeWithTag("agent-refresh-draft").fetchSemanticsNode()
    }

    @Test fun late_ai_response_does_not_restore_proposals_after_user_edits() {
        val response = kotlinx.coroutines.CompletableDeferred<ArrangementAssistantAdviceResult>()
        var calls = 0
        var stored: com.swan1127.repland.domain.model.AssistantWorkspace? = null
        rule.setContent { ReplandTheme { AgentCenterScreen(
            canRefineWithAi = true, onRefineWithAi = { _, _, _ -> calls++; response.await() },
            onWorkspaceChanged = { stored = it }, onSaveTasks = {}, onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-prompt").performTextInput("复习英语")
        rule.onNodeWithTag("agent-preview").performClick()
        rule.waitUntil { calls == 1 }
        rule.onNodeWithTag("agent-prompt").performTextClearance()
        rule.runOnIdle { response.complete(ArrangementAssistantAdviceResult.Advice(
            ArrangementAssistantAdvice(confidenceLabel = "旧回复", candidates = listOf(ArrangementCandidate("旧任务", TaskCategory.COURSE, 30,
                ArrangementTimeHint(600), emptySet(), ArrangementPlacementSource.AI_SUGGESTED))))) }
        rule.waitForIdle()
        assertEquals("", stored!!.prompt)
        assertEquals(0, stored!!.proposals.size)
    }

    @Test fun formulate_plan_is_independent_of_composer() {
        var calls = 0
        rule.setContent { ReplandTheme { AgentCenterScreen(
            onFormulatePlan = { calls++ }, onSaveTasks = {},
            onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-formulate-plan").performClick()
        assertEquals(1, calls)
        rule.onNodeWithTag("agent-prompt-安排今天").performClick()
        rule.onNodeWithTag("agent-prompt").assertTextContains("", substring = false)
    }

    @Test fun capture_workflow_keeps_text_and_saves_no_placements() {
        var saved = 0
        var placed = 0
        rule.setContent { ReplandTheme { AgentCenterScreen(
            onSaveTasks = { saved += it.size }, onPlaceTask = { _, _, _, _ -> placed++ }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-prompt").performTextInput("15:00 复习英语 30 分钟")
        rule.onNodeWithTag("agent-prompt-新增事项").performClick()
        rule.onNodeWithTag("agent-prompt").assertTextContains("15:00 复习英语 30 分钟", substring = false)
        rule.onNodeWithTag("agent-confirm-tasks").performScrollTo().performClick()
        assertEquals(1, saved)
        assertEquals(0, placed)
    }

    @Test fun text_is_only_saved_after_explicit_confirmation() {
        var saved = 0
        rule.setContent {
            ReplandTheme {
                AgentCenterScreen(
                    onSaveTasks = { saved += it.size },
                    onPlaceTask = { _, _, _, _ -> },
                    onOpenTimeStudio = {},
                )
            }
        }
        rule.onNodeWithTag("agent-prompt").performTextInput("下午复习数据结构 40 分钟")
        rule.onNodeWithTag("agent-preview").performClick()
        rule.onNodeWithTag("agent-confirm-tasks").fetchSemanticsNode()
        assertEquals(0, saved)
        rule.onNodeWithTag("agent-confirm-tasks").performScrollTo().performClick()
        assertEquals(1, saved)
    }

    @Test fun simultaneous_explicit_events_are_previewed_on_separate_tracks() {
        rule.setContent {
            ReplandTheme {
                AgentCenterScreen(
                    onSaveTasks = {},
                    onPlaceTask = { _, _, _, _ -> },
                    onOpenTimeStudio = {},
                )
            }
        }
        rule.onNodeWithTag("agent-prompt").performTextInput("15:00 study 90min;15:00 run 40min")
        rule.onNodeWithTag("agent-preview").performClick()
        rule.onNodeWithText("今天的安排预览").fetchSemanticsNode()
        rule.onNodeWithText("并行 2").fetchSemanticsNode()
    }

    @Test fun preview_keeps_existing_day_entries_visible_as_context() {
        rule.setContent {
            ReplandTheme {
                AgentCenterScreen(
                    activeDate = LocalDate.of(2026, 9, 27),
                    occupiedEntries = listOf(
                        TimelineEntry(
                            id = "course-1",
                            title = "离散数学",
                            kind = TimelineKind.COURSE,
                            date = LocalDate.of(2026, 9, 27),
                            startMinute = 9 * 60,
                            endMinute = 10 * 60 + 30,
                            trackId = "course",
                        ),
                    ),
                    onSaveTasks = {},
                    onPlaceTask = { _, _, _, _ -> },
                    onOpenTimeStudio = {},
                )
            }
        }
        rule.onNodeWithTag("agent-prompt").performTextInput("09:00 复习数据结构 40 分钟")
        rule.onNodeWithTag("agent-preview").performClick()
        rule.onNodeWithText("离散数学").fetchSemanticsNode()
        rule.onNodeWithText("今天的安排预览").fetchSemanticsNode()
    }

    @Test fun configured_ai_is_called_by_generate_and_can_return_two_semantic_actions() {
        var calls = 0
        rule.setContent {
            ReplandTheme {
                AgentCenterScreen(
                    canRefineWithAi = true,
                    onRefineWithAi = { _, _, _ ->
                        calls += 1
                        ArrangementAssistantAdviceResult.Advice(
                            ArrangementAssistantAdvice(
                                confidenceLabel = "已拆成两件事",
                                candidates = listOf(
                                    ArrangementCandidate("复习英语", TaskCategory.COURSE, 45, ArrangementTimeHint(10 * 60), emptySet(), ArrangementPlacementSource.AI_SUGGESTED),
                                    ArrangementCandidate("写报告", TaskCategory.OFFICE, 60, ArrangementTimeHint(11 * 60), emptySet(), ArrangementPlacementSource.AI_SUGGESTED),
                                ),
                            ),
                        )
                    },
                    onSaveTasks = {},
                    onPlaceTask = { _, _, _, _ -> },
                    onOpenTimeStudio = {},
                )
            }
        }

        rule.onNodeWithTag("agent-prompt").performTextInput("我想复习英语和写报告")
        rule.onNodeWithTag("agent-preview").performClick()

        rule.waitUntil(3_000) { calls == 1 }
        rule.onNodeWithText("复习英语").fetchSemanticsNode()
        rule.onNodeWithText("写报告").fetchSemanticsNode()
    }
}
