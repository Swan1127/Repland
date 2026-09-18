package com.swan1127.repland.data.importer

import android.content.Context
import android.net.Uri
import com.swan1127.repland.domain.model.ImportedCourse
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.IOException
import java.time.DayOfWeek
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the widely used Chinese university timetable layout: weekday columns and numbered periods.
 * It only creates import candidates; no course is stored until the user confirms the preview.
 */
class PdfTimetableImporter(
    private val context: Context,
) {
    suspend fun parse(uri: Uri): List<ImportedCourse> = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw TimetableImportException("无法读取所选文件")
        input.use { stream ->
            parseStream(stream)
        }
    }

    /**
     * Keeps PDF parsing separate from Android document selection so the same bounded,
     * read-only import path can be verified with a generated document on device.
     */
    internal fun parseStream(input: java.io.InputStream): List<ImportedCourse> =
        PDDocument.load(input).use { document ->
            if (document.numberOfPages > MAX_PAGES) {
                throw TimetableImportException("课表页数过多，请导入不超过 $MAX_PAGES 页的课表")
            }
            GridCourseStripper().extract(document)
        }

    private companion object {
        const val MAX_PAGES = 4
    }
}

class TimetableImportException(message: String) : IOException(message)

private class GridCourseStripper : PDFTextStripper() {
    private val periodMarkers = mutableListOf<PeriodMarker>()
    private val courses = mutableListOf<MutableCourse>()
    private var pageNumber = 0
    private var sequence = 0

    @Throws(IOException::class)
    override fun startPage(page: PDPage) {
        pageNumber += 1
        super.startPage(page)
    }

    @Throws(IOException::class)
    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        val cleaned = text.replace(WHITESPACE, "").trim()
        if (cleaned.isEmpty() || textPositions.isEmpty()) return
        val x = textPositions.map(TextPosition::getXDirAdj).average().toFloat()
        val y = textPositions.map(TextPosition::getYDirAdj).average().toFloat()
        val fontSize = textPositions.map(TextPosition::getFontSizeInPt).average()

        cleaned.toIntOrNull()?.takeIf { it in 1..12 }?.let { period ->
            if (x in PERIOD_COLUMN_MIN..PERIOD_COLUMN_MAX && fontSize >= PERIOD_FONT_SIZE) {
                periodMarkers += PeriodMarker(pageNumber, period, y)
            }
            return
        }

        val dayOfWeek = dayAt(x) ?: return
        val period = periodAt(y) ?: return
        if (fontSize in COURSE_TITLE_MIN_FONT_SIZE..COURSE_TITLE_MAX_FONT_SIZE &&
            isCourseTitle(cleaned)
        ) {
            appendCourseTitle(cleaned, dayOfWeek, period, y)
            return
        }

        if (fontSize in COURSE_DETAIL_MIN_FONT_SIZE..COURSE_DETAIL_MAX_FONT_SIZE) {
            attachPeriodDetails(cleaned, dayOfWeek, period, y)
        }
    }

    fun extract(document: PDDocument): List<ImportedCourse> {
        getText(document)
        return courses
            .mapNotNull(MutableCourse::toImportedCourse)
            .distinctBy { course ->
                listOf(course.title, course.dayOfWeek, course.startPeriod, course.endPeriod, course.weekPattern)
            }
            .sortedWith(compareBy(ImportedCourse::dayOfWeek, ImportedCourse::startPeriod, ImportedCourse::title))
    }

    private fun appendCourseTitle(
        title: String,
        dayOfWeek: DayOfWeek,
        period: Int,
        y: Float,
    ) {
        val previous = courses.lastOrNull()
        if (previous != null &&
            previous.pageNumber == pageNumber &&
            previous.dayOfWeek == dayOfWeek &&
            previous.startPeriod == period &&
            y - previous.y in 0f..TITLE_CONTINUATION_DISTANCE
        ) {
            previous.title += title
            previous.y = y
        } else {
            courses += MutableCourse(
                id = "course-${pageNumber}-${sequence++}",
                title = title,
                pageNumber = pageNumber,
                dayOfWeek = dayOfWeek,
                startPeriod = period,
                endPeriod = period,
                y = y,
            )
        }
    }

    private fun attachPeriodDetails(
        text: String,
        dayOfWeek: DayOfWeek,
        period: Int,
        y: Float,
    ) {
        val match = PERIOD_RANGE.find(text) ?: return
        val course = courses.lastOrNull {
            it.pageNumber == pageNumber &&
                it.dayOfWeek == dayOfWeek &&
                it.startPeriod == period &&
                y >= it.y &&
                y - it.y <= DETAIL_DISTANCE
        } ?: return
        val startPeriod = match.groupValues[1].toIntOrNull() ?: return
        val endPeriod = match.groupValues[2].toIntOrNull() ?: return
        if (startPeriod !in 1..12 || endPeriod !in startPeriod..12) return
        course.startPeriod = startPeriod
        course.endPeriod = endPeriod
        course.weekPattern = WEEK_PATTERN.find(text)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)
    }

    private fun dayAt(x: Float): DayOfWeek? {
        val index = ((x - FIRST_DAY_COLUMN_START) / DAY_COLUMN_WIDTH).toInt()
        return DayOfWeek.entries.getOrNull(index)
    }

    private fun periodAt(y: Float): Int? = periodMarkers
        .asReversed()
        .firstOrNull { marker ->
            marker.pageNumber == pageNumber && marker.y <= y + PERIOD_MARKER_LEEWAY
        }
        ?.period

    private fun isCourseTitle(text: String): Boolean =
        text.length >= 2 &&
            !text.startsWith("(") &&
            !text.startsWith("（") &&
            !text.contains("学时") &&
            !text.contains("教师") &&
            !text.contains("地点")

    private data class PeriodMarker(
        val pageNumber: Int,
        val period: Int,
        val y: Float,
    )

    private data class MutableCourse(
        val id: String,
        var title: String,
        val pageNumber: Int,
        val dayOfWeek: DayOfWeek,
        var startPeriod: Int,
        var endPeriod: Int,
        var y: Float,
        var weekPattern: String? = null,
    ) {
        fun toImportedCourse(): ImportedCourse? =
            title.takeIf(String::isNotBlank)?.let {
                ImportedCourse(
                    id = id,
                    title = it,
                    dayOfWeek = dayOfWeek,
                    startPeriod = startPeriod,
                    endPeriod = endPeriod,
                    weekPattern = weekPattern,
                )
            }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val PERIOD_RANGE = Regex("[（(](\\d{1,2})-(\\d{1,2})[节節]")
        val WEEK_PATTERN = Regex("[）)]([^/]{0,20}[周週][^/]*)")
        const val FIRST_DAY_COLUMN_START = 98f
        const val DAY_COLUMN_WIDTH = 104f
        const val PERIOD_COLUMN_MIN = 60f
        const val PERIOD_COLUMN_MAX = 100f
        const val PERIOD_FONT_SIZE = 10.0
        const val COURSE_TITLE_MIN_FONT_SIZE = 8.5
        const val COURSE_TITLE_MAX_FONT_SIZE = 10.5
        const val COURSE_DETAIL_MIN_FONT_SIZE = 7.0
        const val COURSE_DETAIL_MAX_FONT_SIZE = 8.49
        const val PERIOD_MARKER_LEEWAY = 12f
        const val TITLE_CONTINUATION_DISTANCE = 20f
        const val DETAIL_DISTANCE = 36f
    }
}
