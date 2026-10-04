package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class TaskEditorWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun waitTag(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun editing_survives_recreation_rejects_overflow_and_saves_same_identity() {
        val repo = (rule.activity.application as ReplandApplication).appContainer.taskRepository
        val id = UUID.randomUUID().toString()
        val name = "编辑恢复${System.currentTimeMillis()}"
        val due = LocalDate.now().plusDays(7)
        runBlocking { repo.save(TaskDraft(id = id, displayName = name, description = "原备注",
            category = TaskCategory.OFFICE, userPriority = TaskPriority.HIGH, estimatedDays = 2,
            totalDurationMinutes = 30, dueDate = due, scheduledForDate = LocalDate.now())) }
        val before = runBlocking { repo.observeTasks().first() }
        waitTag("navigation-tasks")
        rule.onNodeWithTag("navigation-tasks").performClick()
        waitTag("task-groups-scroll")
        rule.onNodeWithTag("task-groups-scroll").performScrollToNode(hasTestTag("task-card-$name"))
        rule.onNodeWithTag("task-card-$name").performClick()
        waitTag("task-detail-scroll")
        rule.onNodeWithTag("task-detail-scroll").performScrollToNode(hasText("编辑任务"))
        rule.onNodeWithText("编辑任务").performClick()
        waitTag("task-editor-name")
        val edited = "保留编辑${System.currentTimeMillis()}"
        rule.onNodeWithTag("task-editor-name").performTextReplacement(edited)
        rule.onNodeWithTag("task-editor-description").performTextReplacement("保留备注和日期")
        rule.onNodeWithTag("task-editor-duration").performScrollTo().performTextReplacement("99999999999999999999")
        rule.activityRule.scenario.recreate()
        waitTag("task-editor-name")
        rule.onNodeWithTag("task-editor-name").assertTextContains(edited)
        rule.onNodeWithTag("task-editor-description").performScrollTo().assertTextContains("保留备注和日期")
        rule.onNodeWithTag("task-editor-duration").performScrollTo().assertTextContains("99999999999999999999")
        rule.onNodeWithTag("task-editor-save").performClick()
        rule.waitForIdle()
        assertEquals(before, runBlocking { repo.observeTasks().first() })
        rule.onNodeWithText("时长应为 1–1440 分钟；留空表示暂不估算").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("task-editor-duration").performTextReplacement("45")
        rule.onNodeWithTag("task-editor-save").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("task-editor-name").fetchSemanticsNodes().isEmpty() }
        val after = runBlocking { repo.observeTasks().first() }
        assertEquals(before.size, after.size)
        val task = after.single { it.id == id }
        assertEquals(edited, task.displayName); assertEquals("保留备注和日期", task.description)
        assertEquals(45, task.totalDurationMinutes); assertEquals(due, task.dueDate)
        assertEquals(LocalDate.now(), task.scheduledForDate); assertEquals(2, task.estimatedDays)
        assertEquals(TaskPriority.HIGH, task.userPriority)
        rule.onNodeWithText("已保存“$edited”的任务信息；现有时段未重新安排。").assertIsDisplayed()
    }
}
