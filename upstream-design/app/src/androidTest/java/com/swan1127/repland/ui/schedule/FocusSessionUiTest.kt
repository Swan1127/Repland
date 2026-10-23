package com.swan1127.repland.ui.schedule

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.data.FocusSessionStore
import com.swan1127.repland.data.StoredFocusSession
import com.swan1127.repland.domain.model.FocusSessionState
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.ui.theme.ReplandTheme
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FocusSessionUiTest {
    @get:Rule val compose = createComposeRule()
    private val store get() = FocusSessionStore(InstrumentationRegistry.getInstrumentation().targetContext)
    private val entry = TimelineEntry("focus-test", "复习整章", TimelineKind.TASK, LocalDate.now(), 600, 720, "task-test")

    @Before fun seed() {
        store.clear()
        store.save(StoredFocusSession(entry, FocusSessionState(1_500, 60_000, startRecorded = true)))
    }
    @After fun clean() { store.clear() }

    @Test fun recording_round_does_not_complete_task() {
        var complete: Boolean? = null
        var dismissed = false
        compose.setContent { ReplandTheme {
            FocusSessionScreen(entry, 25, {}, 0, { dismissed = true }, {}, { _, _, value -> complete = value; true })
        } }
        compose.onNodeWithText("记录本轮投入").performClick()
        compose.waitUntil { dismissed }
        assertEquals(false, complete)
        assertNull(store.load())
    }

    @Test fun task_completion_requires_explicit_confirmation() {
        var calls = 0
        var complete: Boolean? = null
        compose.setContent { ReplandTheme {
            FocusSessionScreen(entry, 25, {}, 0, {}, {}, { _, _, value -> calls++; complete = value; true })
        } }
        compose.onNodeWithText("整项任务已完成").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("确认整项任务已完成？").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("确认完成整项").performClick()
        compose.waitUntil { calls == 1 }
        assertEquals(true, complete)
    }

    @Test fun failed_round_save_keeps_resumable_session_and_screen() {
        var dismissed = false
        compose.setContent { ReplandTheme {
            FocusSessionScreen(entry, 25, {}, 0, { dismissed = true }, {}, { _, _, _ -> false })
        } }
        compose.onNodeWithText("记录本轮投入").performClick()
        compose.onNodeWithText("保存失败，本轮记录已保留，请重试。").assertIsDisplayed()
        assertFalse(dismissed)
        assertNotNull(store.load())
    }
}
