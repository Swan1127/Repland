package com.swan1127.repland

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
