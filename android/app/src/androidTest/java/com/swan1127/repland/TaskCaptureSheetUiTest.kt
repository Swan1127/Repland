package com.swan1127.repland

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.components.TaskCaptureSheet
import com.swan1127.repland.ui.theme.ReplandTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TaskCaptureSheetUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun saving_disables_input_save_close_and_voice() {
        var saves = 0
        var closes = 0
        rule.setContent { ReplandTheme {
            TaskCaptureSheet("", { closes++ }, { saves++ }, onVoice = {},
                draft = TaskCaptureDraft(text = "已填写"), saving = true)
        } }
        listOf("task-capture-input", "task-capture-close", "task-capture-save-inbox",
            "task-capture-arrange-today", "voice-capture").forEach {
            rule.onNodeWithTag(it).assertIsNotEnabled()
        }
        assertEquals(0, saves); assertEquals(0, closes)
        rule.onNodeWithTag("task-capture-sheet").performTouchInput { swipeDown() }
        rule.waitForIdle()
        rule.onNodeWithTag("task-capture-close").assertIsDisplayed()
        assertEquals(0, closes)
    }

    @Test fun save_failure_retains_custom_input_and_exposes_retry() {
        var result: TaskDraft? = null
        rule.setContent { ReplandTheme {
            TaskCaptureSheet("", {}, { result = it }, draft = TaskCaptureDraft(text = "复习",
                stage = TaskCaptureStage.DURATION, isCustomDuration = true, customDurationText = "45"),
                saveError = "保存未成功，请重试")
        } }
        rule.onNodeWithTag("task-capture-error").assertIsDisplayed()
        rule.onNodeWithTag("task-capture-duration-custom-input").assertTextContains("45")
        rule.onNodeWithTag("task-capture-save-duration").assertIsEnabled().performClick()
        assertEquals(45, result!!.totalDurationMinutes)
        assertEquals(java.time.LocalDate.now(), result!!.scheduledForDate)
    }

    @Test fun large_text_keeps_primary_operation_visible_and_options_scrollable() {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) { ReplandTheme(darkTheme = true) {
                TaskCaptureSheet("", {}, {}, draft = TaskCaptureDraft(text = "大字号输入",
                    stage = TaskCaptureStage.DURATION))
            } }
        }
        rule.onNodeWithTag("task-capture-save-duration").assertIsDisplayed()
        rule.onNodeWithTag("task-capture-close").assertIsDisplayed()
        rule.onNodeWithTag("task-capture-duration-custom").performScrollTo().assertIsDisplayed()
    }
}
