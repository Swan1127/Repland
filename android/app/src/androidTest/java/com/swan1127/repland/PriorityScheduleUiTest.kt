package com.swan1127.repland

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.schedule.TimelineDashboard
import com.swan1127.repland.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

internal object PriorityScheduleFixture {
    val day = LocalDate.of(2026, 10, 15)
    val priorities = mapOf("high" to TaskPriority.HIGH, "middle" to TaskPriority.MEDIUM, "low" to TaskPriority.LOW,
        "unknown" to TaskPriority.UNSPECIFIED)
    val entries = listOf(
        TimelineEntry("high", "完成阅读报告", TimelineKind.TASK, day, 540, 600, taskId = "high", note = "图书馆 · 先整理核心观点"),
        TimelineEntry("middle", "整理课堂笔记", TimelineKind.TASK, day, 570, 630, taskId = "middle", lane = 1, note = "主校区 3 号楼 405"),
        TimelineEntry("low", "复习英语词汇", TimelineKind.TASK, day, 570, 600, taskId = "low", lane = 2),
    )
}

class PriorityScheduleUiTest {
    @get:Rule val rule = createComposeRule()
    private val fixture = PriorityScheduleFixture

    private fun content(
        entries: List<TimelineEntry> = fixture.entries,
        completed: Set<String> = emptySet(), cancelled: Set<String> = emptySet(),
        dark: Boolean = false, large: Boolean = false, reduced: Boolean = false,
        onInspect: (String) -> Unit = {}, onOpenTask: (String) -> Unit = {},
        onUpdate: (TimelineEntry, Int, Int, () -> Unit) -> Unit = { _, _, _, _ -> },
    ) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f)) {
                ReplandTheme(darkTheme = dark) {
                    CompositionLocalProvider(LocalReducedMotion provides reduced) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            TimelineDashboard(entries, EngagementMode.GUIDED, onInspect, onOpenTask,
                                taskPriorities = fixture.priorities, completedTaskIds = completed, cancelledTaskIds = cancelled,
                                clockNow = fixture.day.atTime(12, 0), onUpdateEntryTime = onUpdate)
                        }
                    }
                }
            }
        }
    }

    @Test fun summary_preserves_every_overlap_and_orders_chips_by_priority() {
        content()
        val children = rule.onNodeWithTag("priority-slot-570").onChildren().fetchSemanticsNodes()
        val descriptions = children.map { it.config[SemanticsProperties.ContentDescription].first() }
        assertEquals(listOf("完成阅读报告", "整理课堂笔记", "复习英语词汇"), descriptions.map { it.substringBefore('，') })
        rule.onNodeWithTag("summary-570-low").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("priority-slot-600").assertExists()
    }

    @Test fun summary_opens_the_original_entry_instead_of_the_shorter_overlap_slice() {
        var inspected = ""
        var opened = ""
        content(onInspect = { inspected = it }, onOpenTask = { opened = it })
        rule.onNodeWithTag("summary-570-middle").performScrollTo().performClick()
        rule.onNodeWithTag("timeline-entry-detail-middle", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("09:30–10:30", substring = true).assertExists()
        rule.onNodeWithTag("open-task-middle", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals("middle", inspected); assertEquals("middle", opened)
    }

    @Test fun detailed_priority_order_keeps_original_times_and_unknown_last() {
        val entries = fixture.entries + TimelineEntry("unknown", "未设优先级事项", TimelineKind.TASK, fixture.day, 480, 500, taskId = "unknown")
        content(entries)
        rule.onNodeWithTag("schedule-detail-mode").performClick()
        val positions = listOf("high", "middle", "low", "unknown").map { rule.onNodeWithTag("timeline-$it").getUnclippedBoundsInRoot().top }
        assertEquals(positions.sorted(), positions)
        // Compact rows join the time range; wider rows render two labels.
        // Check each original entry so neither layout can hide a changed time.
        mapOf(
            "high" to ("09:00" to "10:00"),
            "middle" to ("09:30" to "10:30"),
            "low" to ("09:30" to "10:00"),
            "unknown" to ("08:00" to "08:20"),
        ).forEach { (id, times) ->
            rule.onNodeWithTag("timeline-$id")
                .assert(hasText(times.first, substring = true) and hasText(times.second, substring = true))
        }
        rule.onNodeWithTag("timeline-unknown").performScrollTo().assertIsDisplayed()
    }

    @Test fun completed_cancelled_and_elapsed_time_are_not_confused() {
        val day = fixture.day
        val entries = listOf(
            TimelineEntry("past", "仍需确认结果", TimelineKind.TASK, day, 480, 500, taskId = "past"),
            TimelineEntry("done", "已确认完成", TimelineKind.TASK, day, 500, 520, taskId = "done", taskClosed = true),
            TimelineEntry("cancel", "已确认取消", TimelineKind.TASK, day, 520, 540, taskId = "cancel", taskClosed = true),
            TimelineEntry("course", "过去的课程", TimelineKind.COURSE, day, 540, 560),
        )
        content(entries, completed = setOf("done"), cancelled = setOf("cancel"))
        rule.onNodeWithTag("summary-480-past").assertExists()
        listOf("已完成（1）", "已取消（1）", "已结束（1）").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithTag("schedule-group-已完成").performScrollTo().performClick()
        rule.onNodeWithTag("timeline-done").assertDoesNotExist()
        rule.onNodeWithTag("summary-480-past").assertExists()
    }

    @Test fun time_editing_from_detailed_view_still_uses_original_entry() {
        var start = -1; var end = -1
        content(onUpdate = { _, newStart, newEnd, saved -> start = newStart; end = newEnd; saved() })
        rule.onNodeWithTag("schedule-detail-mode").performClick()
        rule.onNodeWithTag("timeline-high").performScrollTo().performClick()
        rule.onNodeWithTag("timeline-time-editor-high", useUnmergedTree = true).performScrollTo().performClick()
        rule.onNodeWithTag("timeline-start-high", useUnmergedTree = true).performTextReplacement("11:00")
        rule.onNodeWithTag("timeline-end-high", useUnmergedTree = true).performTextReplacement("11:45")
        rule.onNodeWithTag("timeline-time-save-high", useUnmergedTree = true).performClick()
        rule.runOnIdle { assertEquals(660, start); assertEquals(705, end) }
    }

    @Test fun quick_mode_reversal_keeps_only_the_selected_view() {
        rule.mainClock.autoAdvance = false
        content()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("schedule-detail-mode").performClick()
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithTag("schedule-summary-mode").performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("priority-slot-570").assertExists()
        rule.onNodeWithTag("timeline-high").assertDoesNotExist()
    }

    @Test fun large_dark_reduced_motion_keeps_both_modes_and_each_task_reachable() {
        content(dark = true, large = true, reduced = true)
        rule.onNodeWithTag("schedule-detail-mode").assertIsDisplayed().performClick()
        rule.onNodeWithTag("timeline-low").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("schedule-summary-mode").performScrollTo().performClick()
        rule.onNodeWithTag("summary-570-low").performScrollTo().assertIsDisplayed()
    }

    @Test fun legacy_track_editor_and_fixed_course_are_still_available() {
        content(entries = listOf(TimelineEntry("course", "课程", TimelineKind.COURSE, fixture.day, 780, 840)))
        rule.onNodeWithTag("schedule-track-editor").performScrollTo().performClick()
        rule.onNodeWithTag("timeline-course").performScrollTo().assertExists()
        rule.onNodeWithTag("schedule-display-options").performScrollTo().performClick()
        rule.onNodeWithTag("add-track-trigger").performScrollTo().assertIsDisplayed()
    }
}
