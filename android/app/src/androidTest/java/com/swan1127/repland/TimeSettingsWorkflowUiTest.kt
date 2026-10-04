package com.swan1127.repland

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.domain.model.*
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimeSettingsWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val repo get() = (rule.activity.application as ReplandApplication).appContainer.timeRepository
    private fun waitTag(tag: String) {
        try { rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: Exception) { screenshot("qa-time20-timeout-$tag.png"); throw failure }
    }
    private fun gone(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    private fun open() {
        waitTag("navigation-mine"); rule.onNodeWithTag("navigation-mine").performClick()
        waitTag("mine-time-settings"); rule.onNodeWithTag("mine-time-settings").performScrollTo().performClick()
        waitTag("time-add-weekly")
    }
    private fun fill(tag: String, value: String) { rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value) }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { File(rule.activity.getExternalFilesDir(null), name).outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }

    @Test fun mine_entry_new_and_existing_weekly_edit_keep_identity_fields_and_day_end_after_recreation() {
        val name = "QA-time20-${UUID.randomUUID()}"; var savedId: String? = null
        val original = runBlocking { repo.observeWeeklyBlocks().first() }
        try {
            open(); screenshot("qa-timesettings20.png")
            rule.onNodeWithTag("time-add-weekly").performScrollTo().performClick(); waitTag("weekly-block-title")
            fill("weekly-block-title", name); fill("weekly-block-start", "23:00"); fill("weekly-block-end", "24:00")
            fill("weekly-block-note", "保留草稿备注")
            rule.activityRule.scenario.recreate(); waitTag("weekly-block-title")
            rule.onNodeWithTag("weekly-block-title").assertTextContains(name)
            rule.onNodeWithTag("weekly-block-end").performScrollTo().assertTextContains("24:00")
            rule.onNodeWithTag("weekly-block-save").performClick(); gone("weekly-block-title")
            val saved = runBlocking { repo.observeWeeklyBlocks().first().single { it.title == name } }; savedId = saved.id
            assertEquals(1440, saved.endMinute); assertEquals(original.size + 1, runBlocking { repo.observeWeeklyBlocks().first().size })
            rule.onNodeWithText("每周时间已保存；现有任务计划未自动重新安排").assertIsDisplayed()
            rule.onNodeWithTag("weekly-edit-${saved.id}").performScrollTo()
            screenshot("qa-time20-before-weekly-edit.png")
            rule.onNodeWithTag("weekly-edit-${saved.id}").performClick(); waitTag("weekly-block-title")
            fill("weekly-block-title", "$name-edited")
            rule.activityRule.scenario.recreate(); waitTag("weekly-block-title")
            rule.onNodeWithTag("weekly-block-title").assertTextContains("$name-edited")
            rule.onNodeWithTag("weekly-block-save").performClick(); gone("weekly-block-title")
            val edited = runBlocking { repo.observeWeeklyBlocks().first().single { it.id == saved.id } }
            assertEquals(saved.id, edited.id); assertEquals(saved.createdAtEpochMillis, edited.createdAtEpochMillis)
            assertEquals(saved.note, edited.note); assertEquals(1440, edited.endMinute)
            rule.onNodeWithTag("weekly-delete-${saved.id}").performScrollTo().performClick(); waitTag("time-delete-confirm")
            rule.activityRule.scenario.recreate(); waitTag("time-delete-confirm")
            rule.onNodeWithTag("time-delete-confirm").performClick(); gone("time-delete-confirm")
            assertEquals(original, runBlocking { repo.observeWeeklyBlocks().first() }); savedId = null
            rule.onNodeWithTag("timetable-back").performClick(); waitTag("mine-time-settings")
        } finally { savedId?.let { runBlocking { repo.deleteWeeklyBlock(it) } } }
    }

    @Test fun single_day_and_semester_forms_restore_input_validate_and_commit_only_after_save() {
        val name = "QA-override20-${UUID.randomUUID()}"; var savedId: String? = null
        val originalSemester = runBlocking { repo.observeTimeConstraintSettings().first().semesterFirstWeekMonday }
        try {
            open(); rule.onNodeWithTag("time-add-override").performScrollTo().performClick(); waitTag("date-override-title")
            fill("date-override-title", name); fill("date-override-date", "2026-10-05")
            fill("date-override-start", "23:00"); fill("date-override-end", "24:00")
            rule.activityRule.scenario.recreate(); waitTag("date-override-title")
            rule.onNodeWithTag("date-override-title").assertTextContains(name)
            rule.onNodeWithTag("date-override-save").performClick(); gone("date-override-title")
            val saved = runBlocking { repo.observeDateOverrides().first().single { it.title == name } }; savedId = saved.id
            assertEquals(1440, saved.endMinute); assertEquals(LocalDate.of(2026, 10, 5), saved.date)
            rule.onNodeWithTag("override-edit-${saved.id}").performScrollTo()
            screenshot("qa-time20-before-override-edit.png")
            rule.onNodeWithTag("override-edit-${saved.id}").performClick(); waitTag("date-override-title")
            fill("date-override-note", "旋转保留")
            rule.activityRule.scenario.recreate(); waitTag("date-override-title")
            rule.onNodeWithTag("date-override-note").performScrollTo().assertTextContains("旋转保留")
            rule.onNodeWithTag("date-override-save").performClick(); gone("date-override-title")
            assertEquals(saved.id, runBlocking { repo.observeDateOverrides().first().single { it.title == name }.id })
            rule.onNodeWithTag("time-edit-semester").performScrollTo().performClick(); waitTag("semester-start-date")
            fill("semester-start-date", "2026-10-06"); rule.onNodeWithTag("semester-start-save").performClick()
            rule.onNodeWithTag("semester-start-date").assertExists()
            assertEquals(originalSemester, runBlocking { repo.observeTimeConstraintSettings().first().semesterFirstWeekMonday })
            fill("semester-start-date", "2026-10-05")
            rule.activityRule.scenario.recreate(); waitTag("semester-start-date")
            rule.onNodeWithTag("semester-start-date").assertTextContains("2026-10-05")
            rule.onNodeWithTag("semester-start-save").performClick(); gone("semester-start-date")
            assertEquals(LocalDate.of(2026, 10, 5), runBlocking { repo.observeTimeConstraintSettings().first().semesterFirstWeekMonday })
            rule.onNodeWithTag("override-delete-${saved.id}").performScrollTo().performClick(); waitTag("time-delete-confirm")
            rule.onNodeWithTag("time-delete-confirm").performClick(); gone("time-delete-confirm"); savedId = null
        } finally {
            savedId?.let { runBlocking { repo.deleteDateOverride(it) } }
            runBlocking { repo.saveSemesterFirstWeekMonday(originalSemester) }
        }
    }

    @Test fun database_failure_keeps_weekly_fields_and_retry_shows_a_real_success_receipt() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val name = "QA-failure20-${UUID.randomUUID()}"; var savedId: String? = null
        val original = runBlocking { repo.observeWeeklyBlocks().first() }
        val originalSettings = runBlocking { repo.observeTimeConstraintSettings().first() }
        val sql = SQLiteDatabase.openDatabase(rule.activity.getDatabasePath("repland.db").absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            sql.execSQL("CREATE TRIGGER qa_ui_time_revision_failure BEFORE INSERT ON semester_settings BEGIN SELECT RAISE(ABORT, 'qa ui failure'); END")
            open(); rule.onNodeWithTag("time-add-weekly").performScrollTo().performClick(); waitTag("weekly-block-title")
            fill("weekly-block-title", name); fill("weekly-block-start", "23:00"); fill("weekly-block-end", "24:00")
            rule.onNodeWithTag("weekly-block-save").performClick(); waitTag("time-mutation-error")
            rule.onNodeWithTag("weekly-block-title").assertTextContains(name)
            assertEquals(original, runBlocking { repo.observeWeeklyBlocks().first() })
            assertEquals(originalSettings, runBlocking { repo.observeTimeConstraintSettings().first() })
            screenshot("qa-timesettings20-failure.png")
            rule.activityRule.scenario.recreate(); waitTag("weekly-block-title")
            rule.onNodeWithTag("weekly-block-title").assertTextContains(name); rule.onNodeWithTag("time-mutation-error").assertExists()
            sql.execSQL("DROP TRIGGER qa_ui_time_revision_failure")
            rule.onNodeWithTag("weekly-block-save").performClick(); gone("weekly-block-title")
            rule.onNodeWithText("每周时间已保存；现有任务计划未自动重新安排").assertIsDisplayed()
            val saved = runBlocking { repo.observeWeeklyBlocks().first().single { it.title == name } }; savedId = saved.id
            assertEquals(1440, saved.endMinute); assertEquals(original.size + 1, runBlocking { repo.observeWeeklyBlocks().first().size })
        } finally {
            sql.execSQL("DROP TRIGGER IF EXISTS qa_ui_time_revision_failure"); sql.close()
            savedId?.let { runBlocking { repo.deleteWeeklyBlock(it) } }
        }
    }
}
