package com.swan1127.repland.data.importer

import com.swan1127.repland.domain.model.ImportedCourse
import com.swan1127.repland.domain.model.WeekPatternFormat
import java.time.DayOfWeek

/**
 * Turns a spreadsheet-shaped grid of strings into courses.
 *
 * Both the clipboard path and the .xlsx path funnel through here, so the recognition rules
 * exist exactly once. The input is a plain `List<List<String>>` because that is the shape
 * both sources naturally produce — tab-separated text pasted out of Excel, and cell text
 * read out of the sheet XML.
 *
 * The expected layout is the one Chinese university timetables actually use: a header row
 * naming the weekdays, a period column on its left, and the course name where the two
 * intersect. If no such header exists the grid is rejected rather than guessed at — a
 * wrong timetable silently corrupts every later plan, which is far worse than an error.
 *
 * Weekdays are recognised in Chinese and in English, so a timetable pasted from an
 * English-language spreadsheet imports too. Only the weekday vocabulary is bilingual: the period
 * column is found by position, not by its header text, so the rest of the header row can say
 * anything.
 */
object TimetableGridParser {

    /**
     * Chinese spellings, matched as substrings.
     *
     * A substring match is correct here and only here: Chinese is written without spaces, so a
     * header cell reading `星期一(第1-16周)` has to be recognised by the part that names the day.
     */
    private val chineseDayTokens: List<Pair<DayOfWeek, List<String>>> = listOf(
        DayOfWeek.MONDAY to listOf("星期一", "周一", "礼拜一"),
        DayOfWeek.TUESDAY to listOf("星期二", "周二", "礼拜二"),
        DayOfWeek.WEDNESDAY to listOf("星期三", "周三", "礼拜三"),
        DayOfWeek.THURSDAY to listOf("星期四", "周四", "礼拜四"),
        DayOfWeek.FRIDAY to listOf("星期五", "周五", "礼拜五"),
        DayOfWeek.SATURDAY to listOf("星期六", "周六", "礼拜六"),
        DayOfWeek.SUNDAY to listOf("星期日", "星期天", "周日", "周天", "礼拜日"),
    )

    /**
     * English spellings, matched on word boundaries.
     *
     * The asymmetry with [chineseDayTokens] is deliberate. This runs over every cell of every row
     * while hunting for the header row, so a substring match on `sat` would also fire inside
     * "satisfy" and `mon` inside "common" — three such cells in one row and an ordinary course
     * list is promoted to a header, which is exactly the "reject rather than guess" rule inverted.
     * `\b` costs nothing and removes the whole class.
     *
     * Full names and the common three-letter abbreviations are both accepted, because English
     * timetables use both. Note that an English user's grid can only be read back if the exporter
     * emits a spelling that appears here; see [com.swan1127.repland.data.export.XlsxExporter].
     */
    private val englishDayTokens: List<Pair<DayOfWeek, Regex>> = listOf(
        DayOfWeek.MONDAY to Regex("""\bmon(day)?\b"""),
        DayOfWeek.TUESDAY to Regex("""\btue(s|sday)?\b"""),
        DayOfWeek.WEDNESDAY to Regex("""\bwed(nesday)?\b"""),
        DayOfWeek.THURSDAY to Regex("""\bthu(r|rs|rsday)?\b"""),
        DayOfWeek.FRIDAY to Regex("""\bfri(day)?\b"""),
        DayOfWeek.SATURDAY to Regex("""\bsat(urday)?\b"""),
        DayOfWeek.SUNDAY to Regex("""\bsun(day)?\b"""),
    )

    private val periodRange = Regex("""(\d{1,2})\s*[-~、,，]\s*(\d{1,2})""")
    private val periodSingle = Regex("""(\d{1,2})""")

    /** Splits text pasted from a spreadsheet into rows of cells. */
    fun parseTabSeparated(text: String): List<List<String>> =
        text.lines()
            .filter { it.isNotBlank() }
            .map { line -> line.split('\t').map(String::trim) }

    fun parse(rows: List<List<String>>): List<ImportedCourse> {
        val normalized = rows.map { row -> row.map(String::trim) }
        val headerIndex = normalized.indexOfFirst { row -> row.count { dayOf(it) != null } >= 3 }
        if (headerIndex < 0) return emptyList()

        val dayColumns = normalized[headerIndex]
            .mapIndexedNotNull { index, cell -> dayOf(cell)?.let { index to it } }
            .toMap()
        if (dayColumns.size < 3) return emptyList()

        val firstDayColumn = dayColumns.keys.min()
        val periodColumn = (firstDayColumn - 1).takeIf { it >= 0 }

        val courses = mutableListOf<ImportedCourse>()
        var counter = 0
        rowsLoop@ for (row in normalized.drop(headerIndex + 1)) {
            val periodCell = periodColumn?.let { row.getOrNull(it) }.orEmpty()
            val span = parsePeriod(periodCell) ?: continue@rowsLoop
            for ((column, day) in dayColumns) {
                val raw = row.getOrNull(column).orEmpty()
                if (raw.isEmpty()) continue
                val (title, pattern) = WeekPatternFormat.split(raw)
                if (title.isEmpty()) continue
                counter += 1
                courses += ImportedCourse(
                    id = "grid-$counter",
                    title = title,
                    dayOfWeek = day,
                    startPeriod = span.first,
                    endPeriod = span.second,
                    weekPattern = pattern,
                )
            }
        }
        return courses
    }

    private fun dayOf(cell: String): DayOfWeek? {
        if (cell.isEmpty()) return null
        val lower = cell.lowercase()
        chineseDayTokens.firstOrNull { (_, tokens) -> tokens.any(lower::contains) }
            ?.let { return it.first }
        return englishDayTokens.firstOrNull { (_, pattern) -> pattern.containsMatchIn(lower) }?.first
    }

    /**
     * Reads "3", "第3节" or "3-4" into an inclusive period span.
     *
     * Anything outside 1..12 is rejected: a timetable with a 30th period is a mis-read cell,
     * not a real class, and admitting it would push the block past midnight.
     */
    private fun parsePeriod(cell: String): Pair<Int, Int>? {
        if (cell.isBlank()) return null
        periodRange.find(cell)?.let { match ->
            val start = match.groupValues[1].toIntOrNull()
            val end = match.groupValues[2].toIntOrNull()
            if (start != null && end != null && start in 1..12 && end in start..12) {
                return start to end
            }
        }
        val single = periodSingle.find(cell)?.groupValues?.get(1)?.toIntOrNull()
        return single?.takeIf { it in 1..12 }?.let { it to it }
    }
}
