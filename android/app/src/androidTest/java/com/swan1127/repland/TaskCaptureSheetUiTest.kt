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

    @Test fun custom_duration_keeps_negative_decimal_and_overflow_raw_and_requires_explicit_correction() {
        var result: TaskDraft? = null
        lateinit var capture: MutableState<TaskCaptureDraft>
        rule.setContent { ReplandTheme {
            capture = remember { mutableStateOf(TaskCaptureDraft(text = "原始时长", stage = TaskCaptureStage.DURATION,
                isCustomDuration = true, customDurationText = "45")) }
            TaskCaptureSheet("", {}, { result = it }, draft = capture.value, onDraftChange = { capture.value = it })
        } }
        listOf("-30", "3.5", "+30", "999999999999", "0", "1441").forEach { raw ->
            rule.onNodeWithTag("task-capture-duration-custom-input").performScrollTo().performTextReplacement(raw)
            rule.onNodeWithTag("task-capture-duration-custom-input").assertTextContains(raw)
            rule.onNodeWithTag("task-capture-save-duration").assertIsNotEnabled().performClick()
            rule.runOnIdle { assertEquals(raw, capture.value.customDurationText); assertNull(result) }
        }
        rule.onNodeWithTag("task-capture-duration-custom-input").performTextReplacement("45")
        rule.onNodeWithTag("task-capture-save-duration").assertIsEnabled().performClick()
        assertEquals(45, result!!.totalDurationMinutes)
    }
    @Test fun invalid_duration_correction_retains_newly_selected_deadline_without_creating_task() {
        var result: TaskDraft? = null
        lateinit var capture: MutableState<TaskCaptureDraft>
        val tomorrow = java.time.LocalDate.now().plusDays(1)
        rule.setContent { ReplandTheme {
            capture = remember { mutableStateOf(TaskCaptureDraft(text = "截止日期不丢失", stage = TaskCaptureStage.DEADLINE,
                isCustomDuration = true, customDurationText = "-30")) }
            TaskCaptureSheet("", {}, { result = it }, draft = capture.value, onDraftChange = { capture.value = it })
        } }
        rule.onNodeWithTag("task-capture-deadline-tomorrow").performScrollTo().performClick()
        rule.runOnIdle {
            assertNull(result); assertEquals(tomorrow, capture.value.dueDate)
            assertNull(capture.value.scheduledForDate)
            assertEquals(TaskCaptureStage.DURATION, capture.value.stage)
            assertEquals("-30", capture.value.customDurationText)
        }
        rule.onNodeWithTag("task-capture-save-duration").assertIsNotEnabled()
        rule.onNodeWithTag("task-capture-duration-unknown").performScrollTo().performClick()
        rule.onNodeWithTag("task-capture-save-duration").performClick()
        assertEquals(tomorrow, result!!.dueDate)
        assertNull(result!!.totalDurationMinutes)
        assertEquals(java.time.LocalDate.now(), result!!.scheduledForDate) // Explicitly labelled save-to-today action.
    }

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
    @Test fun returning_with_selected_dates_keeps_them_and_names_the_save_action_honestly() {
        val due = java.time.LocalDate.now().plusDays(7)
        var result: TaskDraft? = null
        rule.setContent { ReplandTheme {
            TaskCaptureSheet("", {}, { result = it }, draft = TaskCaptureDraft(text = "有截止日期", dueDate = due))
        } }
        rule.onNodeWithText("保存任务（保留已选日期）").assertIsDisplayed()
        rule.onNodeWithTag("task-capture-save-inbox").performClick()
        assertEquals(due, result!!.dueDate)
        assertNull(result!!.scheduledForDate)
    }
    @Test fun real_soft_keyboard_back_hides_ime_before_dismissing_capture() {
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val previous = shell("settings get secure show_ime_with_hard_keyboard")
        var dismissals = 0
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            rule.setContent { ReplandTheme {
                TaskCaptureSheet("", { dismissals++ }, {}, draft = TaskCaptureDraft(text = "键盘返回测试"))
            } }
            NativeSheetTestInput.awaitFocusedDialog(rule, "接下来要做什么？")
            rule.onNodeWithTag("task-capture-input").performClick().assertIsFocused()
            // mIsInputViewShown describes the service's input layout, not window visibility.
            rule.waitUntil(10_000) { shell("dumpsys input_method").contains("mWindowVisible=true") }
            NativeSheetTestInput.back(rule, "接下来要做什么？")
            rule.waitUntil(10_000) { !shell("dumpsys input_method").contains("mWindowVisible=true") }
            rule.waitForIdle()
            rule.onNodeWithTag("task-capture-input").assertIsDisplayed()
            assertEquals(0, dismissals)
            NativeSheetTestInput.back(rule, "接下来要做什么？")
            rule.waitUntil(10_000) { dismissals == 1 }
        } finally {
            shell(if (previous == "null") "settings delete secure show_ime_with_hard_keyboard"
                else "settings put secure show_ime_with_hard_keyboard $previous")
        }
    }
}
