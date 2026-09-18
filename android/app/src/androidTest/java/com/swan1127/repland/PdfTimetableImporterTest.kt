package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.importer.PdfTimetableImporter
import com.swan1127.repland.data.room.ReplandDatabase
import com.swan1127.repland.data.room.RoomTimeRepository
import com.swan1127.repland.domain.model.ClassPeriodClock
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfTimetableImporterTest {
    private lateinit var database: ReplandDatabase
    private lateinit var repository: RoomTimeRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReplandDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomTimeRepository(database.timeDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun parsed_pdf_stays_a_preview_until_the_user_confirms_selected_courses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PDFBoxResourceLoader.init(context)
        val importer = PdfTimetableImporter(context)

        val candidates = importer.parseStream(ByteArrayInputStream(createTimetablePdf()))

        assertEquals(1, candidates.size)
        assertEquals("DatabaseSystems", candidates.single().title)
        assertEquals(DayOfWeek.MONDAY, candidates.single().dayOfWeek)
        assertEquals(1, candidates.single().startPeriod)
        // Parsing is read-only: a preview alone cannot become a fixed constraint.
        assertTrue(repository.observeWeeklyBlocks().first().isEmpty())

        repository.saveWeeklyBlocks(
            candidates.mapNotNull(ClassPeriodClock::toWeeklyTimeBlockDraft),
        )

        val saved = repository.observeWeeklyBlocks().first().single()
        assertEquals("DatabaseSystems", saved.title)
        assertEquals(DayOfWeek.MONDAY, saved.dayOfWeek)
        assertEquals(8 * 60, saved.startMinute)
    }

    private fun createTimetablePdf(): ByteArray = PDDocument().use { document ->
        val page = PDPage()
        document.addPage(page)
        PDPageContentStream(document, page).use { content ->
            writeText(content, "1", x = 80f, y = 700f, size = 12f)
            writeText(content, "DatabaseSystems", x = 120f, y = 680f, size = 10f)
        }
        ByteArrayOutputStream().use { output ->
            document.save(output)
            output.toByteArray()
        }
    }

    private fun writeText(
        content: PDPageContentStream,
        value: String,
        x: Float,
        y: Float,
        size: Float,
    ) {
        content.beginText()
        content.setFont(PDType1Font.HELVETICA, size)
        content.newLineAtOffset(x, y)
        content.showText(value)
        content.endText()
    }
}
