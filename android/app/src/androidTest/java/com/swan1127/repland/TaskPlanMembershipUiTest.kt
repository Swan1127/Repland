package com.swan1127.repland

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.TasksScreen
import com.swan1127.repland.ui.theme.ReplandTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TaskPlanMembershipUiTest {
    @get:Rule val rule = createComposeRule()
    private val task = Task("a", "", "同一事项", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 45,
        null, TaskStatus.NOT_STARTED, null, null, null, 0, 0, 0)
    @Test fun confirmed_plan_updates_today_and_inbox_without_changing_task_facts() {
        val slots = mutableStateOf<List<PlannedSegment>>(emptyList())
        rule.setContent { ReplandTheme { TasksScreen(listOf(task), false, {}, {}, {}, slots.value) } }
        rule.onNodeWithText("今天", useUnmergedTree = true).performClick()
        rule.onNodeWithText("今天还没有任务").assertExists()
        rule.runOnIdle { slots.value = listOf(PlannedSegment(taskId = "a", date = LocalDate.now(), startMinute = 1200, endMinute = 1245)) }
        rule.onNodeWithTag("task-card-同一事项").assertExists()
        rule.onNodeWithText("已安排 · ${LocalDate.now().monthValue}月${LocalDate.now().dayOfMonth}日 20:00–20:45").assertExists()
        rule.onNodeWithText("待安排", useUnmergedTree = true).performClick()
        rule.onNodeWithText("收件箱是空的").assertExists()
        rule.runOnIdle { slots.value = emptyList() }
        rule.onNodeWithTag("task-card-同一事项").assertExists()
        assertNull(task.scheduledForDate)
        assertNull(task.dueDate)
        assertEquals(TaskStatus.NOT_STARTED, task.status)
    }
}
