package com.swan1127.repland

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.ui.PlanDraftDialog
import com.swan1127.repland.ui.theme.ReplandTheme
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test

class PlanDraftPreviewUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun order_only_preview_explains_that_schedule_is_unchanged() {
        val draft = PlanDraft(LocalDateTime.now(), emptyList(), emptyList(), emptyList(), listOf("a"), orderOnly = true)
        rule.setContent { ReplandTheme { PlanDraftDialog(draft, listOf(task("a", "英语")), emptyList(), emptyList(), null,
            onDismiss = {}, onUpdateDraft = {}, onAccept = {}) } }
        rule.onNodeWithText("只调整任务列表顺序，不改变日程时段。").assertExists()
        rule.onNodeWithTag("plan-draft-preview").assertDoesNotExist()
    }

    @Test fun review_discloses_old_and_new_times() {
        val old = PlannedSegment("old", "a", LocalDate.now().plusDays(1), 600, 660)
        val draft = PlanDraft(LocalDateTime.now(), listOf(old.copy(id = "new", startMinute = 660, endMinute = 720)), emptyList(), emptyList(), listOf("a"))
        rule.setContent { ReplandTheme { PlanDraftDialog(draft, listOf(task("a", "英语")), emptyList(), emptyList(), null,
            onDismiss = {}, onUpdateDraft = {}, onAccept = {},
            currentPlan = com.swan1127.repland.domain.model.ConfirmedPlan("current", 0, true, listOf(old), listOf("a")), currentTaskOrder = listOf("a")) } }
        rule.onNodeWithText("确认后：1 项日程变化，0 项顺序变化。取消不会改动当前计划。").fetchSemanticsNode()
        rule.onNodeWithTag("draft-show-changes").performScrollTo().performClick()
        rule.onNodeWithText("原：${old.date} 10:00–11:00 · 主线\n新：${old.date} 11:00–12:00 · 主线").fetchSemanticsNode()
    }

    @Test fun defer_changes_only_the_proposal_and_can_be_confirmed() {
        val segment = PlannedSegment("s", "a", LocalDate.now().plusDays(1), 600, 660)
        val state = androidx.compose.runtime.mutableStateOf(PlanDraft(LocalDateTime.now(), listOf(segment), emptyList(), emptyList(), listOf("a")))
        var accepted = 0
        rule.setContent { ReplandTheme { PlanDraftDialog(state.value, listOf(task("a", "英语")), emptyList(), emptyList(), null,
            onDismiss = {}, onUpdateDraft = { state.value = it }, onAccept = { accepted++ }) } }
        rule.onNodeWithText("候选事项顺序").performScrollTo().performClick()
        rule.onNodeWithTag("draft-defer-a").performScrollTo().performClick()
        assertTrue(state.value.segments.isEmpty())
        assertEquals(listOf("a"), state.value.pendingTaskIds)
        rule.onNodeWithTag("accept-plan-draft").performClick()
        assertEquals(1, accepted)
    }

    @Test fun locked_task_cannot_be_deferred_and_capacity_has_action() {
        val segment = PlannedSegment("s", "a", LocalDate.now().plusDays(1), 600, 660, isLocked = true)
        val draft = PlanDraft(LocalDateTime.now(), listOf(segment), listOf("b"),
            listOf(com.swan1127.repland.domain.model.UnscheduledTask("b", 60, com.swan1127.repland.domain.model.UnscheduledReason.CAPACITY_IN_ROLLING_WINDOW)), listOf("a", "b"))
        var configured = 0
        rule.setContent { ReplandTheme { PlanDraftDialog(draft, listOf(task("a", "英语"), task("b", "数学")), emptyList(), emptyList(), null,
            onDismiss = {}, onUpdateDraft = {}, onAccept = {}, onConfigureAvailability = { configured++ }) } }
        rule.onNodeWithTag("draft-capacity-add-time").performScrollTo().performClick()
        assertEquals(1, configured)
        rule.onNodeWithText("候选事项顺序").performScrollTo().performClick()
        rule.onNodeWithTag("draft-defer-a").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun preview_uses_real_time_tracks_and_opens_segment_editor() {
        val date = LocalDate.of(2026, 9, 27)
        rule.setContent {
            ReplandTheme {
                PlanDraftDialog(
                    draft = PlanDraft(
                        generatedAt = LocalDateTime.of(2026, 9, 27, 8, 0),
                        segments = listOf(
                            PlannedSegment("segment-main", "task-main", date, 9 * 60, 10 * 60, trackId = "focus"),
                            PlannedSegment("segment-parallel", "task-parallel", date, 9 * 60, 9 * 60 + 30, trackId = "parallel-2"),
                        ),
                        pendingTaskIds = emptyList(),
                        unscheduledTasks = emptyList(),
                    ),
                    tasks = listOf(task("task-main", "复习数据结构"), task("task-parallel", "跑步")),
                    weeklyBlocks = emptyList(),
                    dateOverrides = emptyList(),
                    semesterFirstWeekMonday = null,
                    onDismiss = {},
                    onUpdateDraft = {},
                    onAccept = {},
                )
            }
        }

        rule.onNodeWithTag("plan-draft-preview").fetchSemanticsNode()
        rule.onNodeWithText("主线").fetchSemanticsNode()
        rule.onNodeWithText("并行 2").fetchSemanticsNode()
        rule.onNodeWithTag("draft-segment-segment-main").performClick()
        rule.onNodeWithText("轨道").fetchSemanticsNode()
    }

    @Test fun early_and_late_explicit_segments_are_visible_in_the_preview() {
        val date = LocalDate.now().plusDays(1)
        val draft = PlanDraft(LocalDateTime.now(), listOf(
            PlannedSegment("early", "a", date, 360, 390), PlannedSegment("late", "b", date, 1410, 1440)),
            emptyList(), emptyList(), listOf("a", "b"))
        rule.setContent { ReplandTheme { PlanDraftDialog(draft, listOf(task("a", "清晨事项"), task("b", "深夜事项")),
            emptyList(), emptyList(), null, onDismiss = {}, onUpdateDraft = {}, onAccept = {}) } }
        rule.onNodeWithTag("draft-segment-early").assertExists()
        rule.onNodeWithTag("draft-segment-late").assertExists()
        rule.onNodeWithText("清晨事项 · 06:00–06:30 · 主线").assertExists()
        rule.onNodeWithText("深夜事项 · 23:30–24:00 · 主线").assertExists()
        rule.onNodeWithTag("draft-segment-summary-late").performScrollTo().performClick()
        rule.onNodeWithText("轨道").assertExists()
    }

    private fun task(id: String, title: String) = Task(
        id = id,
        description = title,
        displayName = title,
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.MEDIUM,
        estimatedDays = 1,
        totalDurationMinutes = 60,
        dueDate = null,
        status = TaskStatus.NOT_STARTED,
        completionSummary = null,
        actualDurationMinutes = null,
        progressPercent = null,
        postponeCount = 0,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
}
