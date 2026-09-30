package com.swan1127.repland

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
