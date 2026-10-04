package com.swan1127.repland

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.time.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimetableConfirmationUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun waitTag(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private val repo get() = (rule.activity.application as ReplandApplication).appContainer.timeRepository
    private fun vm(): TimeViewModel = ViewModelProvider(rule.activity)[TimeViewModel::class.java]
    private fun openImportPage() {
        waitTag("navigation-agent"); rule.onNodeWithTag("navigation-agent").performClick()
        waitTag("agent-import-timetable"); rule.onNodeWithTag("agent-import-timetable").performClick()
        waitTag("import-timetable-pdf")
    }
    private fun fixture(name: String): File {
        PDFBoxResourceLoader.init(rule.activity)
        val file = File(rule.activity.cacheDir, "qa-timetable-${UUID.randomUUID()}.pdf")
        PDDocument().use { document ->
            val page = PDPage(); document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                fun text(value: String, x: Float, y: Float, size: Float) {
                    content.beginText(); content.setFont(PDType1Font.HELVETICA, size)
                    content.newLineAtOffset(x, y); content.showText(value); content.endText()
                }
                text("1", 80f, 700f, 12f); text(name, 120f, 680f, 10f)
            }
            document.save(file)
        }
        return file
    }
    private fun read(file: File): TimetableImportState.Review {
        rule.runOnIdle { vm().readTimetable(Uri.fromFile(file)) }
        waitTag("timetable-import-review")
        return vm().timetableImportState.value as TimetableImportState.Review
    }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { File(rule.activity.getExternalFilesDir(null), name).outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }

    @Test fun preview_and_course_edit_survive_recreation_then_cancel_without_writes() {
        openImportPage()
        val before = runBlocking { repo.observeWeeklyBlocks().first() }
        val settings = runBlocking { repo.observeTimeConstraintSettings().first() }
        val file = fixture("CancelCourse")
        try {
            val id = read(file).courses.single().id
            rule.onNodeWithTag("timetable-import-confirm").assertIsNotEnabled()
            rule.onNodeWithTag("import-course-select-$id").performClick()
            rule.activityRule.scenario.recreate(); waitTag("timetable-import-review")
            rule.onNodeWithTag("import-course-select-$id").assertIsOff()
            rule.onNodeWithTag("import-course-edit-$id").performScrollTo().performClick()
            waitTag("import-course-name")
            rule.onNodeWithTag("import-course-name").performTextReplacement("保留识别编辑")
            rule.onNodeWithTag("import-course-start-period").performScrollTo().performTextReplacement("3")
            rule.onNodeWithTag("import-course-end-period").performScrollTo().performTextReplacement("4")
            rule.activityRule.scenario.recreate(); waitTag("import-course-name")
            rule.onNodeWithTag("import-course-name").assertTextContains("保留识别编辑")
            rule.onNodeWithTag("import-course-start-period").performScrollTo().assertTextContains("3")
            rule.onNodeWithTag("import-course-save").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("import-course-name").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithText("保留识别编辑").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("10:00–11:35").assertExists()
            rule.onNodeWithTag("timetable-import-cancel").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("timetable-import-review").fetchSemanticsNodes().isEmpty() }
            assertEquals(before, runBlocking { repo.observeWeeklyBlocks().first() })
            assertEquals(settings, runBlocking { repo.observeTimeConstraintSettings().first() })
        } finally { file.delete() }
    }

    @Test fun actual_generated_pdf_requires_clock_acknowledgement_and_confirm_to_save_with_receipt() {
        openImportPage()
        val before = runBlocking { repo.observeWeeklyBlocks().first() }
        val file = fixture("Confirm${System.currentTimeMillis()}")
        try {
            val course = read(file).courses.single()
            assertEquals(before, runBlocking { repo.observeWeeklyBlocks().first() })
            rule.onNodeWithTag("timetable-import-confirm").assertIsNotEnabled()
            rule.onNodeWithTag("import-clock-acknowledge").performScrollTo().performClick()
            screenshot("qa-timetable19-preview.png")
            rule.onNodeWithTag("timetable-import-confirm").assertIsEnabled().performClick()
            waitTag("timetable-import-receipt")
            rule.onNodeWithText("已新增 1 门课程；跳过 0 项重复课程。").assertIsDisplayed()
            screenshot("qa-timetable19-receipt.png")
            val after = runBlocking { repo.observeWeeklyBlocks().first() }
            assertEquals(before.size + 1, after.size)
            val saved = after.single { it.title == course.title }
            assertEquals(8 * 60, saved.startMinute); assertEquals(8 * 60 + 45, saved.endMinute)
            assertEquals(TimeBlockKind.COURSE, saved.kind)
            rule.runOnIdle { vm().confirmTimetableImport() }
            assertEquals(after, runBlocking { repo.observeWeeklyBlocks().first() })
            runBlocking { repo.deleteWeeklyBlock(saved.id) }
        } finally { file.delete() }
    }
}
