package com.swan1127.repland

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.swan1127.repland.ui.TaskPageAppBar
import com.swan1127.repland.ui.TaskCaptureButton
import com.swan1127.repland.ui.theme.ReplandTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TaskPageAppBarUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun large_text_title_is_not_clipped_with_both_actions() {
        checkTitle(360, 2f)
    }

    @Test fun narrow_phone_large_text_keeps_title_and_actions() { checkTitle(320, 2f) }
    @Test fun standard_text_keeps_compact_title() { checkTitle(412, 1f) }

    private fun checkTitle(width: Int, fontScale: Float) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                ReplandTheme {
                    Box(Modifier.width(width.dp)) {
                        TaskPageAppBar("全部任务", true, true, true, {}, {})
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("task-page-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("Title must not overflow its measured height", layouts.single().didOverflowHeight)
        assertFalse("Title must not overflow its measured width", layouts.single().didOverflowWidth)
        rule.onNodeWithTag("undo-task-sort").assertExists()
        rule.onNodeWithTag("auto-sort-tasks").assertExists()
    }

    @Test fun actions_keep_callbacks_and_disabled_semantics() {
        var undos = 0
        rule.setContent { ReplandTheme { TaskPageAppBar("全部任务", true, true, false, { undos++ }, {}) } }
        rule.onNodeWithTag("undo-task-sort").performClick()
        assertEquals(1, undos)
        rule.onNodeWithTag("auto-sort-tasks").assertIsNotEnabled()
    }

    @Test fun capture_button_clears_horizontal_insets_without_duplicate_vertical_padding() {
        var clicks = 0
        rule.setContent { ReplandTheme {
            TaskCaptureButton({ clicks++ }, WindowInsets(left = 24, top = 80, right = 96, bottom = 160))
        } }
        val outer = rule.onNodeWithTag("task-add-safe-area").fetchSemanticsNode().boundsInRoot
        val button = rule.onNodeWithTag("add-task").fetchSemanticsNode().boundsInRoot
        assertEquals(24f, button.left - outer.left, 1f)
        assertEquals(96f, outer.right - button.right, 1f)
        assertEquals(outer.top, button.top, 1f)
        assertEquals(outer.bottom, button.bottom, 1f)
        rule.onNodeWithTag("add-task").performClick()
        assertEquals(1, clicks)
    }
}
