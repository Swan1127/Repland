package com.swan1127.repland

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.swan1127.repland.domain.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScheduleNavigationStateUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before
    fun use_manual_planning_mode_for_legacy_workflow() {
        pauseAutomaticPlanningForLegacyWorkflow(rule.activity)
    }

    private fun waitTag(tag: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun selectDate(date: LocalDate) {
        waitTag("navigation-today")
        rule.onNodeWithTag("navigation-today").performClick()
        rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-schedule"))
        rule.onNodeWithTag("month-schedule").performClick()
        rule.clickScheduleDate(date)
    }

    @Test fun selected_date_header_and_day_view_survive_activity_recreation() {
        val day = LocalDate.now().plusDays(2)
        selectDate(day)
        val expected = day.format(DateTimeFormatter.ofPattern("yyyy/MM/dd E", Locale.CHINA))
        rule.onNodeWithTag("schedule-selected-date").assertTextEquals(expected)
        rule.activityRule.scenario.recreate()
        waitTag("schedule-selected-date")
        rule.onNodeWithTag("schedule-selected-date").assertTextEquals(expected)
        rule.onNodeWithTag("today-timeline").assertIsSelected()
    }

    @Test fun returning_from_task_detail_retains_selected_date_mode_and_scroll_even_after_recreation() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val app = (rule.activity.application as ReplandApplication).appContainer
        val id = "qa-scroll-${UUID.randomUUID()}"
        val day = LocalDate.now().plusDays(2)
        val segment = runBlocking {
            app.taskRepository.save(TaskDraft(id = id, displayName = "QA 日程返回位置", totalDurationMinutes = 30))
            val plan = app.planRepository.observeCurrentPlan().first()
            val weekly = app.timeRepository.observeWeeklyBlocks().first()
            val overrides = app.timeRepository.observeDateOverrides().first()
            val semester = app.timeRepository.observeTimeConstraintSettings().first().semesterFirstWeekMonday
            val start = (0..1380 step 30).first { start -> runCatching {
                PlacementValidator.requireValid(PlannedSegment(taskId = id, date = day, startMinute = start, endMinute = start + 30),
                    plan?.segments.orEmpty(), weekly, overrides, semester)
            }.isSuccess }
            app.planRepository.placeTask(id, day, start, start + 30, "focus")
            app.planRepository.observeCurrentPlan().first()!!.segments.single { it.taskId == id }
        }
        try {
            selectDate(day)
            rule.openDetailedSchedule()
            val entryId = "segment:${segment.id}"
            rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("timeline-$entryId"))
            rule.waitForIdle()
            val before = rule.onNodeWithTag("today-page-scroll").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue("The fixture must actually scroll before opening detail", before > 0f)
            rule.onNodeWithTag("timeline-$entryId").performClick()
            rule.onNodeWithTag("open-task-$entryId").performScrollTo().performClick()
            waitTag("task-detail-scroll")
            rule.activityRule.scenario.recreate()
            waitTag("task-detail-back")
            rule.onNodeWithTag("task-detail-back").performClick()
            waitTag("today-page-scroll")
            rule.waitForIdle()
            rule.onNodeWithTag("schedule-selected-date").assertTextEquals(
                day.format(DateTimeFormatter.ofPattern("yyyy/MM/dd E", Locale.CHINA)))
            rule.onNodeWithTag("schedule-detail-mode").assertIsSelected()
            val after = rule.onNodeWithTag("today-page-scroll").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue("Scroll changed from $before to $after", kotlin.math.abs(before - after) <= 1f)
            rule.onNodeWithTag("timeline-$entryId").assertIsDisplayed()
        } finally {
            runBlocking {
                app.planRepository.observeCurrentPlan().first()?.segments?.firstOrNull { it.taskId == id }?.let {
                    app.planRepository.removePlacement(it.id)
                }
            }
            SQLiteDatabase.openDatabase(rule.activity.getDatabasePath("repland.db").absolutePath, null,
                SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(id)) }
        }
    }
}
