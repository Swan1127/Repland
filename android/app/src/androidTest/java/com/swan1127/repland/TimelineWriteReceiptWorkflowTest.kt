package com.swan1127.repland

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Actual Activity -> guarded VM -> Room, isolated QA package, retained data. */
class TimelineWriteReceiptWorkflowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = (rule.activity.application as ReplandApplication).appContainer
    private fun waitTag(tag: String) = rule.waitUntil(15_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun visible(tag: String): SemanticsNodeInteraction {
        waitTag(tag); val node=rule.onNodeWithTag(tag); runCatching { node.performScrollTo() }; return node.assertIsDisplayed()
    }
    private fun open(day: LocalDate) {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        visible("navigation-today").performClick()
        rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-schedule"))
        visible("month-schedule").performClick()
        rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-date-$day"))
        visible("month-date-$day").performClick()
    }
    private fun freeStart(day: LocalDate): Int = runBlocking {
        val entries=ScheduleTimeline.entries(day, app.timeRepository.observeWeeklyBlocks().first(),
            app.timeRepository.observeDateOverrides().first(), app.planRepository.observeCurrentPlan().first()?.segments.orEmpty(),
            app.taskRepository.observeTasks().first(), app.timeRepository.observeTimeConstraintSettings().first().semesterFirstWeekMonday)
        (0..21).map { it*60 }.first { start -> entries.none { start < it.endMinute && start+90 > it.startMinute } }
    }
    private fun db()=SQLiteDatabase.openDatabase(rule.activity.getDatabasePath("repland.db").absolutePath,null,SQLiteDatabase.OPEN_READWRITE)
    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { java.io.File(rule.activity.getExternalFilesDir(null),name).outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) } } finally { bitmap.recycle() }
    }
    @Test fun course_saved_after_activity_recreation_closes_the_restored_form_once() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val day = LocalDate.now().plusDays(3); val start = freeStart(day)
        val title = "QA-course-recreate-${UUID.randomUUID()}"
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repository = app.timeRepository
        val delayed = object : com.swan1127.repland.domain.ports.TimeRepository by repository {
            override suspend fun saveWeeklyBlock(draft: WeeklyTimeBlockDraft) {
                started.complete(Unit); gate.await(); repository.saveWeeklyBlock(draft)
            }
        }
        rule.runOnUiThread {
            val vm = com.swan1127.repland.ui.time.TimeViewModel(delayed,
                com.swan1127.repland.data.importer.PdfTimetableImporter(rule.activity))
            rule.activity.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${com.swan1127.repland.ui.time.TimeViewModel::class.java.canonicalName}", vm)
        }
        rule.activityRule.scenario.recreate()
        try {
            open(day)
            rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("add-course-trigger") or hasTestTag("empty-add-course-trigger"))
            val trigger = if (rule.onAllNodesWithTag("add-course-trigger").fetchSemanticsNodes().isNotEmpty()) "add-course-trigger" else "empty-add-course-trigger"
            visible(trigger).performClick(); visible("course-title-input").performTextReplacement(title)
            if (rule.onAllNodesWithText("手动设时间").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("手动设时间").performClick()
            visible("course-duration-input").performTextReplacement("30")
            rule.onNodeWithText("时").performTextReplacement((start / 60).toString()); rule.onNodeWithText("分").performTextReplacement("00")
            rule.onNodeWithText("保存课程").performClick()
            rule.waitUntil(15_000) { started.isCompleted }
            rule.activityRule.scenario.recreate()
            visible("course-title-input").assertTextContains(title).assertIsNotEnabled()
            rule.runOnIdle { gate.complete(Unit) }
            rule.waitUntil(15_000) { runBlocking { repository.observeWeeklyBlocks().first().count { it.title == title } == 1 } }
            capture("final-course-after-recreation.png")
            rule.waitUntil(15_000) { rule.onAllNodesWithTag("course-title-input").fetchSemanticsNodes().isEmpty() }
            assertEquals(1, runBlocking { repository.observeWeeklyBlocks().first().count { it.title == title } })
        } finally {
            gate.complete(Unit)
            runBlocking { repository.observeWeeklyBlocks().first().filter { it.title == title }.forEach { repository.deleteWeeklyBlock(it.id) } }
        }
    }
    @Test fun failed_course_save_keeps_form_and_retry_commits_once() {
        val day=LocalDate.now().plusDays(3); val start=freeStart(day)
        val title="QA-final-course-${UUID.randomUUID()}"
        val plan=runBlocking { app.planRepository.observeCurrentPlan().first() }
        db().use { it.execSQL("CREATE TRIGGER qa_final_course_write BEFORE INSERT ON weekly_time_blocks WHEN NEW.title = '$title' BEGIN SELECT RAISE(ABORT, 'qa failure'); END") }
        try {
            open(day)
            rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("add-course-trigger") or hasTestTag("empty-add-course-trigger"))
            val trigger=if(rule.onAllNodesWithTag("add-course-trigger").fetchSemanticsNodes().isNotEmpty()) "add-course-trigger" else "empty-add-course-trigger"
            visible(trigger).performClick(); visible("course-title-input").performTextReplacement(title)
            if(rule.onAllNodesWithText("手动设时间").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("手动设时间").performClick()
            visible("course-duration-input").performTextReplacement("30")
            rule.onNodeWithText("时").performTextReplacement((start/60).toString()); rule.onNodeWithText("分").performTextReplacement("00")
            rule.onNodeWithText("保存课程").performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithText("时间设置未保存",substring=true).fetchSemanticsNodes().isNotEmpty() }
            capture("final-course-write-failure.png")
            visible("course-title-input").assertTextContains(title)
            assertFalse(runBlocking { app.timeRepository.observeWeeklyBlocks().first().any { it.title==title } })
            assertEquals(plan,runBlocking { app.planRepository.observeCurrentPlan().first() })
            db().use { it.execSQL("DROP TRIGGER qa_final_course_write") }
            visible("course-duration-input").performTextReplacement("35")
            rule.onNodeWithText("保存课程").performClick()
            rule.waitUntil(15_000) { runBlocking { app.timeRepository.observeWeeklyBlocks().first().count { it.title==title }==1 } }
            rule.waitUntil(15_000) { rule.onAllNodesWithTag("course-title-input").fetchSemanticsNodes().isEmpty() }
            val saved=runBlocking { app.timeRepository.observeWeeklyBlocks().first().single { it.title==title } }
            assertEquals(35,saved.endMinute-saved.startMinute); assertEquals(plan,runBlocking { app.planRepository.observeCurrentPlan().first() })
        } finally {
            db().use { it.execSQL("DROP TRIGGER IF EXISTS qa_final_course_write") }
            runBlocking { app.timeRepository.observeWeeklyBlocks().first().filter { it.title==title }.forEach { app.timeRepository.deleteWeeklyBlock(it.id) } }
        }
    }
    @Test fun failed_exact_move_keeps_raw_times_and_retry_preserves_task_and_logs() {
        val day=LocalDate.now().plusDays(3); val start=freeStart(day); val id="qa-final-move-${UUID.randomUUID()}"
        runBlocking { app.taskRepository.save(TaskDraft(id=id,displayName="最终时间调整",totalDurationMinutes=30)); app.planRepository.placeTask(id,day,start,start+30,"focus") }
        val plan=runBlocking { app.planRepository.observeCurrentPlan().first() }!!
        val segment=plan.segments.single { it.taskId==id }; val entry="segment:${segment.id}"
        val task=runBlocking { app.taskRepository.observeTasks().first().single { it.id==id } }
        val logs=runBlocking { app.taskRepository.observeExecutionLogs(id).first() }
        db().use { it.execSQL("CREATE TRIGGER qa_final_plan_write BEFORE INSERT ON plans BEGIN SELECT RAISE(ABORT, 'qa failure'); END") }
        try {
            open(day); rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("timeline-$entry"))
            visible("timeline-$entry").performTouchInput { click(Offset(center.x,20f)) }
            visible("timeline-time-editor-$entry").performClick()
            val from=TimeBlockValidator.formatTime(start+30); val until=TimeBlockValidator.formatTime(start+60)
            visible("timeline-start-$entry").performTextReplacement(from); visible("timeline-end-$entry").performTextReplacement(until)
            visible("timeline-time-save-$entry").performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithText("操作未保存",substring=true).fetchSemanticsNodes().isNotEmpty() }
            capture("final-time-write-failure.png")
            visible("timeline-start-$entry").assertTextContains(from); visible("timeline-end-$entry").assertTextContains(until)
            assertEquals(plan,runBlocking { app.planRepository.observeCurrentPlan().first() })
            db().use { it.execSQL("DROP TRIGGER qa_final_plan_write") }
            visible("timeline-time-save-$entry").performClick()
            rule.waitUntil(15_000) { runBlocking { app.planRepository.observeCurrentPlan().first()?.id != plan.id } }
            rule.waitUntil(15_000) { rule.onAllNodesWithTag("timeline-start-$entry").fetchSemanticsNodes().isEmpty() }
            assertEquals(start+30,runBlocking { app.planRepository.observeCurrentPlan().first() }!!.segments.single { it.taskId==id }.startMinute)
            assertEquals(task,runBlocking { app.taskRepository.observeTasks().first().single { it.id==id } }); assertEquals(logs,runBlocking { app.taskRepository.observeExecutionLogs(id).first() })
        } finally {
            db().use { it.execSQL("DROP TRIGGER IF EXISTS qa_final_plan_write") }
            runBlocking { app.planRepository.observeCurrentPlan().first()?.segments?.singleOrNull { it.taskId==id }?.let { app.planRepository.removePlacement(it.id) } }
        }
    }
}
