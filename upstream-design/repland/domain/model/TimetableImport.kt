package com.swan1127.repland.domain.model

import java.time.DayOfWeek

/** A course recognized from a timetable PDF before the user confirms the import. */
data class ImportedCourse(
    val id: String,
    val title: String,
    val dayOfWeek: DayOfWeek,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekPattern: String?,
)

/**
 * The week-pattern notation Chinese university timetables use: "高等数学(1-16周)".
 *
 * It lives in the domain layer rather than inside the spreadsheet reader because the exporter has
 * to agree with the reader *exactly* — it decides whether a cell is safe to write by asking this
 * same function what the reader would make of it. A second copy of the rule would be a copy that
 * can drift, and a drift here shows up as an export that silently renames a course.
 */
object WeekPatternFormat {

    private const val WEEK_SPAN = """(?:第\s*)?\d{1,2}(?:\s*[-~至－—–～]\s*\d{1,2})?\s*[周週]"""
    private const val PARITY = """(?:\s*(?:[（(]\s*(?:单双|单|双)\s*[）)]|单双|单|双))?"""
    private val PATTERN = Regex("""$WEEK_SPAN$PARITY(?:\s*[,，、；;]\s*$WEEK_SPAN$PARITY)*""")

    /**
     * Separates the course name from an entire bounded week notation, including single weeks,
     * disjoint ranges and odd/even suffixes. Taking only the first range would silently turn
     * "1-2周,4-7周,10-18周" into a different timetable when an exported XLSX is imported again.
     *
     * Remove only the matched notation's surrounding parentheses. Trimming every closing bracket
     * off the remaining title would rename a course such as "示例课程（高级）".
     */
    fun split(raw: String): Pair<String, String?> {
        val match = PATTERN.find(raw) ?: return raw to null
        if (!CourseWeekPattern.isRecognized(match.value)) return raw to null
        var start = match.range.first
        var end = match.range.last + 1
        val open = raw.getOrNull(start - 1)
        val close = raw.getOrNull(end)
        if ((open == '(' && close == ')') || (open == '（' && close == '）')) {
            start--
            end++
        }
        val title = raw.removeRange(start, end).trim(' ', '·', '-')
        // Preserve compound and parity notation exactly; retain the existing normalization of
        // whitespace and an ASCII tilde so older clipboard imports keep the same result.
        val pattern = match.value.replace(Regex("\\s+"), "").replace('~', '-')
        return title to pattern
    }
}

/**
 * Default class-period clock used only because common timetable PDFs name periods, not clock time.
 * Imported entries remain editable in the fixed-time screen.
 */
object ClassPeriodClock {
    private val starts = mapOf(
        1 to 8 * 60,
        2 to 8 * 60 + 50,
        3 to 10 * 60,
        4 to 10 * 60 + 50,
        5 to 14 * 60,
        6 to 14 * 60 + 50,
        7 to 16 * 60,
        8 to 16 * 60 + 50,
        9 to 19 * 60,
        10 to 19 * 60 + 50,
        11 to 21 * 60,
        12 to 21 * 60 + 50,
    )

    private val ends = mapOf(
        1 to 8 * 60 + 45,
        2 to 9 * 60 + 35,
        3 to 10 * 60 + 45,
        4 to 11 * 60 + 35,
        5 to 14 * 60 + 45,
        6 to 15 * 60 + 35,
        7 to 16 * 60 + 45,
        8 to 17 * 60 + 35,
        9 to 19 * 60 + 45,
        10 to 20 * 60 + 35,
        11 to 21 * 60 + 45,
        12 to 22 * 60 + 35,
    )

    fun toWeeklyTimeBlockDraft(course: ImportedCourse): WeeklyTimeBlockDraft? {
        val startMinute = starts[course.startPeriod] ?: return null
        val endMinute = ends[course.endPeriod] ?: return null
        if (startMinute >= endMinute) return null
        return WeeklyTimeBlockDraft(
            title = course.title,
            kind = TimeBlockKind.COURSE,
            dayOfWeek = course.dayOfWeek,
            startMinute = startMinute,
            endMinute = endMinute,
            weekPattern = course.weekPattern,
        )
    }

    /**
     * The period span a clock range corresponds to, or null when it is not a period range at all.
     *
     * **Exact, not nearest.** This exists so a timetable can be written back out in the
     * weekday-by-period shape this app reads, but a nearest-match would make the export silently
     * change the data: writing 10:00–11:00 out as "3–4" and reading it back gives 10:00–11:35.
     * A block that is not on the clock is therefore left out of the period grid and appears only
     * in the exact-minutes table, where nothing is approximated.
     *
     * Both bounds must land on a period, which is what makes the round trip exact: `starts` and
     * `ends` each hold twelve distinct values, so the lookup is unambiguous.
     */
    fun periodSpanOf(startMinute: Int, endMinute: Int): Pair<Int, Int>? {
        val start = starts.entries.firstOrNull { it.value == startMinute }?.key ?: return null
        val end = ends.entries.firstOrNull { it.value == endMinute }?.key ?: return null
        if (start > end) return null
        return start to end
    }
}
