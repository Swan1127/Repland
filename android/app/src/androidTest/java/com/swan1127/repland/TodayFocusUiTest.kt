package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.TodayFocalOverview
import com.swan1127.repland.ui.theme.ReplandTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TodayFocusUiTest {
    @get:Rule val rule = createComposeRule()
    private val now = LocalDate.of(2026, 10, 3).atTime(10, 0)
    private val entry = TimelineEntry("segment:actual-slot", "作业", TimelineKind.TASK, now.toLocalDate(), 600, 630, "task")
    private var started: String? = null
    private var schedule = 0
    private var pending = 0
    private var tasks = 0
    private fun content(item: TimelineEntry = entry, busy: Boolean = false, count: Int = 2) {
        rule.setContent { ReplandTheme {
            TodayFocalOverview(listOf(item), 3, { schedule++ }, { tasks++ }, now, busy, count, { pending++ }, { started = it })
        } }
    }
    @Test fun starts_exact_slot_and_secondary_actions_are_independent() {
        content()
        rule.onNodeWithText("当前时段").assertExists()
        rule.onNodeWithTag("today-focus-start").performClick()
        assertEquals("actual-slot", started)
        assertEquals(0, schedule)
        rule.onNodeWithTag("today-focus-pending").performClick()
        rule.onNodeWithText("待安排 3").performClick()
        rule.onNodeWithTag("today-focus-schedule").performClick()
        assertEquals(1, pending); assertEquals(1, tasks); assertEquals(1, schedule)
    }
    @Test fun course_has_no_start_action() {
        content(entry.copy(kind = TimelineKind.COURSE, taskId = null))
        rule.onNodeWithTag("today-focus-start").assertDoesNotExist()
        rule.onNodeWithTag("today-focus-schedule").assertExists()
    }
    @Test fun active_round_disables_another_start_and_zero_pending_is_disabled() {
        content(busy = true, count = 0)
        rule.onNodeWithTag("today-focus-start").assertIsNotEnabled()
        rule.onNodeWithTag("today-focus-pending").assertIsNotEnabled()
        assertNull(started)
    }
    @Test fun upcoming_task_explicitly_labels_early_start() {
        content(entry.copy(startMinute = 720, endMinute = 750))
        rule.onNodeWithText("提前开始专注").assertExists()
    }
    @Test fun dark_large_text_keeps_all_actions_reachable_in_scroll_container() {
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)) {
                ReplandTheme(darkTheme = true) {
                    androidx.compose.foundation.lazy.LazyColumn { item {
                        TodayFocalOverview(listOf(entry.copy(title = "较长任务标题，核对大字号仍然可读取和操作")), 3, { schedule++ }, { tasks++ }, now, false, 2, { pending++ }, { started = it })
                    } }
                }
            }
        }
        rule.onNodeWithTag("today-focus-start").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("today-focus-pending").performScrollTo().performClick()
        rule.onNodeWithTag("today-focus-schedule").performScrollTo().performClick()
        assertEquals(1, pending); assertEquals(1, schedule)
    }
}
