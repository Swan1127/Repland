package com.swan1127.repland

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.swan1127.repland.ui.components.EditorSheet
import com.swan1127.repland.ui.theme.ReplandTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EditorSheetUiTest {
    @get:Rule val rule = createComposeRule()
    private fun pressSystemBack() {
        // The sheet is a separate dialog window; don't select the unfocused Activity root.
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent 4"))
            .bufferedReader().use { it.readText() }
    }

    @Test fun pending_save_blocks_back_and_drag_then_returns_normally() {
        val saving = mutableStateOf(true)
        var closes = 0
        rule.setContent { ReplandTheme {
            EditorSheet(onDismissRequest = { closes++ }, title = { Text("编辑中") },
                text = { Text("保留输入", Modifier.testTag("editor-body")) },
                confirmButton = { Button(onClick = {}, enabled = !saving.value) { Text("保存") } }, saving = saving.value)
        } }
        rule.onNodeWithTag("editor-body").assertIsDisplayed()
        pressSystemBack()
        rule.onNodeWithTag("editor-body").assertIsDisplayed()
        rule.onNodeWithTag("editor-sheet").performTouchInput { swipeDown() }
        rule.waitForIdle(); assertEquals(0, closes)
        rule.onNodeWithTag("editor-body").assertIsDisplayed()
        rule.runOnIdle { saving.value = false }
        // Native input does not wait for Compose's updated BackHandler closure.
        rule.waitForIdle()
        rule.onNodeWithText("保存").assertIsEnabled()
        pressSystemBack()
        rule.waitUntil(10_000) { closes == 1 }
    }

    @Test fun real_keyboard_back_keeps_editor_then_next_back_closes() {
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val previous = shell("settings get secure show_ime_with_hard_keyboard")
        var closes = 0
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            rule.setContent { ReplandTheme {
                EditorSheet(onDismissRequest = { closes++ }, title = { Text("编辑中") },
                    text = { OutlinedTextField("保留输入", {}, Modifier.testTag("editor-input")) },
                    confirmButton = { TextButton(onClick = {}) { Text("保存") } })
            } }
            rule.onNodeWithTag("editor-input").performClick()
            rule.waitUntil(10_000) { shell("dumpsys input_method").contains("mWindowVisible=true") }
            pressSystemBack()
            rule.waitUntil(10_000) { !shell("dumpsys input_method").contains("mWindowVisible=true") }
            rule.onNodeWithTag("editor-input").assertIsDisplayed(); assertEquals(0, closes)
            pressSystemBack(); rule.waitUntil(10_000) { closes == 1 }
        } finally {
            shell(if (previous == "null") "settings delete secure show_ime_with_hard_keyboard"
                else "settings put secure show_ime_with_hard_keyboard $previous")
        }
    }
}
