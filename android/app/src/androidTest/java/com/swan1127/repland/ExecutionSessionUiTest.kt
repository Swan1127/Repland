package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.schedule.ExecutionSessionDialog
import com.swan1127.repland.ui.theme.ReplandTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExecutionSessionUiTest {
    @get:Rule val rule = createComposeRule()
    private val session = ExecutionSession("s", "t", "复习高数", "p", "seg", 1500, System.currentTimeMillis(), runningSinceEpochMillis = null)
    @Test fun paused_session_can_resume_and_return_without_finishing() {
        var resumed = false; var closed = false; var finished = false
        rule.setContent { ReplandTheme { ExecutionSessionDialog(session, false, null, { closed = true }, {}, { resumed = true }, { _, _ -> finished = true }) } }
        rule.onNodeWithTag("execution-pause-resume").performClick(); assertTrue(resumed)
        rule.onNodeWithText("返回页面 · 保留本轮").performScrollTo().performClick()
        assertTrue(closed); assertFalse(finished)
    }
    @Test fun partial_requires_content_and_cumulative_progress() {
        var outcome: ExecutionOutcome? = null; var feedback = TaskFeedback()
        rule.setContent { ReplandTheme { ExecutionSessionDialog(session, false, null, {}, {}, {}, { o, f -> outcome = o; feedback = f }) } }
        rule.onNodeWithText("部分完成").performScrollTo().performClick()
        rule.onNodeWithTag("execution-partial-save").assertIsNotEnabled()
        rule.onNodeWithTag("execution-progress").performScrollTo().performTextInput("40")
        rule.onNodeWithTag("execution-content").performScrollTo().performTextInput("第一章")
        rule.onNodeWithTag("execution-partial-save").performScrollTo().performClick()
        assertEquals(ExecutionOutcome.PARTIAL, outcome); assertEquals(40, feedback.progressPercent)
        assertEquals("第一章", feedback.completedContent)
    }
    @Test fun busy_disables_completion_and_saved_errors_are_visible() {
        rule.setContent { ReplandTheme { ExecutionSessionDialog(session, true, "本轮未保存，请重试", {}, {}, {}, { _, _ -> }) } }
        rule.onNodeWithTag("execution-complete").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("execution-error").assertExists()
    }
    @Test fun dark_mode_with_large_text_keeps_pause_and_end_controls_reachable() {
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)) {
                ReplandTheme(darkTheme = true) { ExecutionSessionDialog(session, false, null, {}, {}, {}, { _, _ -> }) }
            }
        }
        rule.onNodeWithTag("execution-pause-resume").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("execution-continue").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("execution-complete").performScrollTo().assertIsDisplayed()
    }
}
