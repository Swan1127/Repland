package com.swan1127.repland

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.swan1127.repland.domain.model.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class NativePageNavigationUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun waitTag(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun native_back_returns_to_mine_after_time_page_recreation() {
        waitTag("navigation-mine"); rule.onNodeWithTag("navigation-mine").performClick()
        waitTag("mine-time-settings"); rule.onNodeWithTag("mine-time-settings").performScrollTo().performClick()
        waitTag("time-add-weekly"); rule.activityRule.scenario.recreate(); waitTag("time-add-weekly")
        NativeSheetTestInput.back(rule, "时间设置")
        waitTag("mine-time-settings"); rule.onNodeWithTag("mine-time-settings").assertExists()
        rule.onNodeWithTag("time-add-weekly").assertDoesNotExist()
    }

    @Test fun native_back_returns_to_assistant_and_sheet_back_does_not_pop_page() {
        waitTag("navigation-agent"); rule.onNodeWithTag("navigation-agent").performClick()
        waitTag("agent-import-timetable"); rule.onNodeWithTag("agent-import-timetable").performClick()
        waitTag("time-add-weekly"); rule.onNodeWithTag("time-add-weekly").performScrollTo().performClick()
        waitTag("weekly-block-title"); rule.onNodeWithTag("weekly-block-title").performTextReplacement("未提交导航样例")
        rule.activityRule.scenario.recreate(); waitTag("weekly-block-title")
        NativeSheetTestInput.back(rule, "添加固定时间")
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("weekly-block-title").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("time-add-weekly").assertExists()
        NativeSheetTestInput.back(rule, "时间设置")
        waitTag("agent-import-timetable"); rule.onNodeWithTag("agent-import-timetable").assertExists()
        rule.onNodeWithTag("time-add-weekly").assertDoesNotExist()
    }

    @Test fun native_back_from_recreated_task_detail_returns_to_task_list() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val repo = (rule.activity.application as ReplandApplication).appContainer.taskRepository
        val id = UUID.randomUUID().toString(); val name = "QA-native22-$id"
        try {
            runBlocking { repo.save(TaskDraft(id = id, displayName = name, description = "导航样例", category = TaskCategory.COURSE,
                userPriority = TaskPriority.MEDIUM, estimatedDays = 1, totalDurationMinutes = 15, dueDate = null, scheduledForDate = LocalDate.now())) }
            waitTag("navigation-tasks"); rule.onNodeWithTag("navigation-tasks").performClick(); waitTag("task-groups-scroll")
            expandTodayTaskGroup(rule)
            rule.onNodeWithTag("task-groups-scroll").performScrollToNode(hasTestTag("task-card-$name"))
            rule.onNodeWithTag("task-card-$name").performClick(); waitTag("task-detail-scroll")
            rule.activityRule.scenario.recreate(); waitTag("task-detail-scroll")
            NativeSheetTestInput.back(rule, name)
            waitTag("task-groups-scroll"); rule.onNodeWithTag("task-detail-scroll").assertDoesNotExist()
        } finally {
            SQLiteDatabase.openDatabase(rule.activity.getDatabasePath("repland.db").absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(id))
            }
        }
    }
}
