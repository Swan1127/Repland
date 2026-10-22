package com.swan1127.repland.data.export

import com.swan1127.repland.domain.model.ClassPeriodClock
import com.swan1127.repland.domain.model.LearningInsights
import com.swan1127.repland.domain.model.LocalDataSnapshot
import com.swan1127.repland.domain.model.RepeatRule
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.WeekPatternFormat
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes the local snapshot as a real `.xlsx`, with no third-party library.
 *
 * An .xlsx is a ZIP of XML parts, which is exactly how [com.swan1127.repland.data.importer.XlsxTimetableImporter]
 * reads one back — so the reader and this writer share a format understanding instead of a
 * dependency. Apache POI was the obvious alternative and is the wrong one here: it is large, it
 * drags `java.awt` and `javax.xml` assumptions onto Android, and this app's whole position is
 * that the user's data stays on the device and the dependency list stays empty.
 *
 * Two things are deliberate about the output:
 *
 * 1. **The 周课表 sheet is in the format this app imports.** Exported → edited in Excel →
 *    re-imported is a workflow students actually have, so the grid is written with the same
 *    weekday header and period column the parser looks for. A block appears in it only when the
 *    cell is provably reversible — it lands exactly on the default period clock
 *    ([ClassPeriodClock.periodSpanOf] is exact on purpose) *and* [gridCell] confirms the reader
 *    gives its title and week pattern back unchanged. A hand-set time would otherwise be written
 *    as a period span and read back at different minutes, which is the export quietly changing
 *    the data. Blocks the grid cannot hold are not lost — they are in 时间块明细 with their exact
 *    minutes.
 * 2. **Numbers are written as numbers.** Excel can then sum the minutes column instead of treating
 *    it as text, which is the only reason to prefer a spreadsheet over the JSON export.
 */
object XlsxExporter {

    /** Day-of-week header labels, chosen to be the ones the importer recognises. */
    private val WEEKDAY_LABELS = listOf(
        DayOfWeek.MONDAY to "星期一",
        DayOfWeek.TUESDAY to "星期二",
        DayOfWeek.WEDNESDAY to "星期三",
        DayOfWeek.THURSDAY to "星期四",
        DayOfWeek.FRIDAY to "星期五",
        DayOfWeek.SATURDAY to "星期六",
        DayOfWeek.SUNDAY to "星期日",
    )

    private const val PERIOD_HEADER = "节次"

    /** The insight window, matching the one the insight page draws. */
    private const val TREND_DAYS = 30

    fun fileName(today: LocalDate): String = "repland-$today.xlsx"

    fun build(snapshot: LocalDataSnapshot, today: LocalDate, zone: ZoneId): ByteArray {
        val sheets = listOf(
            Sheet("周课表", timetableGrid(snapshot.weeklyTimeBlocks)),
            Sheet("时间块明细", blockDetails(snapshot.weeklyTimeBlocks)),
            Sheet("任务", taskRows(snapshot.tasks)),
            Sheet("每日投入", dailyMinutes(snapshot, today, zone)),
            Sheet("分类投入", categoryMinutes(snapshot.tasks)),
        )
        return writeArchive(sheets)
    }

    // ------------------------------------------------------------------ the sheets

    /**
     * The weekday-by-period grid, grouped by period span rather than by single period.
     *
     * A row is a *span* ("1-2"), because that is what the importer's period cell means: writing
     * "1" for a 08:00–09:35 block would read back as 08:00–08:45. Rows are ordered by start
     * period so the sheet reads top to bottom like a day.
     *
     * Two blocks sharing one span on one day cannot both occupy one cell. They are joined with a
     * newline — readable to a person, and an accepted limit of a grid, which is why 时间块明细
     * exists as the authoritative list.
     *
     * A block is placed here only when [gridCell] can show the cell reads back unchanged. The sheet
     * is a *view*, not the record: anything the grid cannot say exactly is left out of it rather
     * than approximated into it.
     */
    private fun timetableGrid(blocks: List<WeeklyTimeBlock>): List<List<Cell>> {
        val placed = blocks.mapNotNull { block ->
            val span = ClassPeriodClock.periodSpanOf(block.startMinute, block.endMinute) ?: return@mapNotNull null
            val cell = gridCell(block) ?: return@mapNotNull null
            Triple(span, block.dayOfWeek, cell)
        }
        val header = buildList {
            add(Cell.Text(PERIOD_HEADER))
            WEEKDAY_LABELS.forEach { (_, label) -> add(Cell.Text(label)) }
        }
        val bySpan = placed.groupBy { it.first }
            .toSortedMap(compareBy { it.first })
        val rows = bySpan.map { (span, entries) ->
            val byDay = entries.groupBy({ it.second }, { it.third })
            buildList {
                add(Cell.Text(spanLabel(span)))
                WEEKDAY_LABELS.forEach { (day, _) ->
                    val titles = byDay[day].orEmpty()
                    add(if (titles.isEmpty()) Cell.Blank else Cell.Text(titles.joinToString("\n")))
                }
            }
        }
        return listOf(header) + rows
    }

    /**
     * "3" for a single period, "1-2" for a span.
     *
     * Both read back identically — the parser falls back to a single number when there is no
     * separator — but a real timetable writes "3", and this sheet is meant to look like one.
     */
    private fun spanLabel(span: Pair<Int, Int>): String =
        if (span.first == span.second) "${span.first}" else "${span.first}-${span.second}"

    /**
     * The cell text that reads back as [block]'s own title and week pattern, or null when none does.
     *
     * The week pattern is appended in brackets because that is how a real timetable writes it, and
     * it is worth appending at all because re-importing a file this app produced should not lose
     * the week pattern — a course that comes back running all term when it actually ran for eight
     * weeks is a wrong plan, not a cosmetic loss.
     *
     * The verdict is not hand-derived. It runs the reader's own [WeekPatternFormat.split] over the
     * candidate, because that rule has edges worth not re-implementing: it removes the first
     * `N-N周` run from *anywhere* in the string, then trims spaces and brackets and dashes off both
     * ends, and the sheet reader trims the whole cell before any of that. So a title that merely
     * contains "1-16周", a title wrapped in dashes, and a title with padding all come back changed.
     * Those blocks are kept out of the grid and appear only in 时间块明细, where the exact title
     * and the exact pattern are two separate columns.
     */
    private fun gridCell(block: WeeklyTimeBlock): String? {
        val pattern = block.weekPattern?.trim()?.takeIf(String::isNotBlank)
        val candidate = if (pattern == null) block.title else "${block.title}($pattern)"
        // The reader trims every cell, so padding on the title would come back trimmed away.
        if (candidate != candidate.trim()) return null
        val (title, recovered) = WeekPatternFormat.split(candidate)
        return candidate.takeIf { title == block.title && recovered == pattern }
    }

    /** Every block with its exact minutes, so nothing depends on the period grid being exact. */
    private fun blockDetails(blocks: List<WeeklyTimeBlock>): List<List<Cell>> {
        val header = listOf("星期", "开始", "结束", "时长(分钟)", "周次", "标题")
            .map(Cell::Text)
        val rows = blocks
            .sortedWith(compareBy({ it.dayOfWeek.value }, { it.startMinute }, { it.title }))
            .map { block ->
                listOf(
                    Cell.Text(labelFor(block.dayOfWeek)),
                    Cell.Text(clock(block.startMinute)),
                    Cell.Text(clock(block.endMinute)),
                    Cell.Number(block.endMinute - block.startMinute),
                    Cell.Text(block.weekPattern.orEmpty()),
                    Cell.Text(block.title),
                )
            }
        return listOf(header) + rows
    }

    /**
     * Planned against actual, which is the whole point of exporting the task list.
     *
     * The two columns sit next to each other on purpose: the calibration story is visible by
     * reading across a row, before any chart exists.
     */
    private fun taskRows(tasks: List<Task>): List<List<Cell>> {
        val header = listOf("标题", "分类", "优先级", "状态", "预估(分钟)", "实际(分钟)", "截止", "计划日", "重复")
            .map(Cell::Text)
        val rows = tasks
            .sortedWith(compareBy({ it.category.name }, { it.displayName }))
            .map { task ->
                listOf(
                    Cell.Text(task.displayName),
                    Cell.Text(task.category.name),
                    Cell.Text(task.userPriority.name),
                    Cell.Text(task.status.name),
                    numberOrBlank(task.totalDurationMinutes),
                    numberOrBlank(task.actualDurationMinutes),
                    Cell.Text(task.dueDate?.toString().orEmpty()),
                    Cell.Text(task.scheduledForDate?.toString().orEmpty()),
                    // The rule in the user's own words ("每周"), not the enum name: the sheet is read
                    // by a person, and "WEEKLY" is the storage spelling, not the product's.
                    Cell.Text(task.repeatRule?.let { repeatRuleLabel(it) }.orEmpty()),
                )
            }
        return listOf(header) + rows
    }

    private fun dailyMinutes(snapshot: LocalDataSnapshot, today: LocalDate, zone: ZoneId): List<List<Cell>> {
        val from = today.minusDays((TREND_DAYS - 1).toLong())
        val header = listOf("日期", "投入(分钟)").map(Cell::Text)
        val rows = LearningInsights.minutesByDay(snapshot.executionLogs, zone, from, TREND_DAYS)
            .map { day -> listOf(Cell.Text(day.date.toString()), Cell.Number(day.minutes)) }
        return listOf(header) + rows
    }

    private fun categoryMinutes(tasks: List<Task>): List<List<Cell>> {
        val header = listOf("分类", "投入(分钟)").map(Cell::Text)
        val rows = LearningInsights.categoryMinutes(tasks)
            .map { entry -> listOf(Cell.Text(entry.category.name), Cell.Number(entry.minutes)) }
        return listOf(header) + rows
    }

    // ------------------------------------------------------------------ cell helpers

    private fun numberOrBlank(value: Int?): Cell = if (value == null) Cell.Blank else Cell.Number(value)

    /**
     * The rule in the words the editor shows ("每周"), not the storage spelling ("WEEKLY").
     *
     * A literal rather than a resource lookup, for the same reason the sheet headers above are
     * literals: this object is plain JVM code with no `Context`, and the sheet is written in one
     * language throughout. `XlsxExporterTest` pins these against `strings.xml` so the two cannot
     * drift apart silently.
     */
    private fun repeatRuleLabel(rule: RepeatRule): String = when (rule) {
        RepeatRule.DAILY -> "每天"
        RepeatRule.WEEKLY -> "每周"
        RepeatRule.MONTHLY -> "每月"
    }

    private fun labelFor(day: DayOfWeek): String =
        WEEKDAY_LABELS.first { (candidate, _) -> candidate == day }.second

    private fun clock(minute: Int): String =
        String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

    // ------------------------------------------------------------------ the archive

    private sealed interface Cell {
        /** Goes into the shared string table. */
        data class Text(val value: String) : Cell

        /** Written as a number so Excel can sum the column. */
        data class Number(val value: Int) : Cell

        data object Blank : Cell
    }

    private data class Sheet(val name: String, val rows: List<List<Cell>>)

    /**
     * Builds the ZIP.
     *
     * Every entry is stamped with a fixed time so the same snapshot always produces the same
     * bytes. A real clock here would make the output differ on every call for no reason, which
     * makes "did the export change?" unanswerable and the exporter itself untestable.
     */
    private fun writeArchive(sheets: List<Sheet>): ByteArray {
        val shared = sharedStrings(sheets)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(entry("[Content_Types].xml"))
            zip.write(contentTypes(sheets.size).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(entry("_rels/.rels"))
            zip.write(ROOT_RELS.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(entry("xl/workbook.xml"))
            zip.write(workbook(sheets).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(entry("xl/_rels/workbook.xml.rels"))
            zip.write(workbookRels(sheets.size).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(entry("xl/sharedStrings.xml"))
            zip.write(sharedStringsXml(shared).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            sheets.forEachIndexed { index, sheet ->
                zip.putNextEntry(entry("xl/worksheets/sheet${index + 1}.xml"))
                zip.write(worksheet(sheet, shared).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun entry(name: String) = ZipEntry(name).apply { time = FIXED_ENTRY_TIME }

    /** The text of every [Cell.Text], deduplicated, in first-seen order. */
    private fun sharedStrings(sheets: List<Sheet>): Map<String, Int> {
        val index = linkedMapOf<String, Int>()
        sheets.forEach { sheet ->
            sheet.rows.forEach { row ->
                row.forEach { cell ->
                    if (cell is Cell.Text && cell.value !in index) index[cell.value] = index.size
                }
            }
        }
        return index
    }

    private fun worksheet(sheet: Sheet, shared: Map<String, Int>): String = buildString {
        append(XML_DECLARATION)
        append("<worksheet xmlns=\"$SHEET_NS\"><sheetData>")
        sheet.rows.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 1
            append("<row r=\"$rowNumber\">")
            row.forEachIndexed { columnIndex, cell ->
                val reference = "${columnName(columnIndex)}$rowNumber"
                when (cell) {
                    is Cell.Text -> append("<c r=\"$reference\" t=\"s\"><v>${shared.getValue(cell.value)}</v></c>")
                    is Cell.Number -> append("<c r=\"$reference\"><v>${cell.value}</v></c>")
                    Cell.Blank -> Unit // A missing cell is an empty cell; writing one adds noise.
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun sharedStringsXml(shared: Map<String, Int>): String = buildString {
        append(XML_DECLARATION)
        append("<sst xmlns=\"$SHEET_NS\" count=\"${shared.size}\" uniqueCount=\"${shared.size}\">")
        shared.keys.forEach { value ->
            // xml:space="preserve" because a title with a leading or trailing space is still the
            // user's title; without it Excel is free to drop the whitespace it just stored.
            append("<si><t xml:space=\"preserve\">").append(escape(value)).append("</t></si>")
        }
        append("</sst>")
    }

    private fun workbook(sheets: List<Sheet>): String = buildString {
        append(XML_DECLARATION)
        append("<workbook xmlns=\"$SHEET_NS\" xmlns:r=\"$REL_NS\"><sheets>")
        sheets.forEachIndexed { index, sheet ->
            append("<sheet name=\"").append(escape(sheet.name)).append("\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int): String = buildString {
        append(XML_DECLARATION)
        append("<Relationships xmlns=\"$PKG_REL_NS\">")
        repeat(count) { index ->
            append("<Relationship Id=\"rId${index + 1}\" Type=\"$OFFICE_REL_NS/worksheet\" ")
                .append("Target=\"worksheets/sheet${index + 1}.xml\"/>")
        }
        append("<Relationship Id=\"rId${count + 1}\" Type=\"$OFFICE_REL_NS/sharedStrings\" Target=\"sharedStrings.xml\"/>")
        append("</Relationships>")
    }

    private fun contentTypes(sheetCount: Int): String = buildString {
        append(XML_DECLARATION)
        append("<Types xmlns=\"$CONTENT_TYPES_NS\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        repeat(sheetCount) { index ->
            append("<Override PartName=\"/xl/worksheets/sheet${index + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>")
        append("</Types>")
    }

    /** Zero-based column index to spreadsheet letters: 0 → A, 25 → Z, 26 → AA. */
    private fun columnName(index: Int): String {
        var remaining = index
        val name = StringBuilder()
        while (true) {
            name.insert(0, ('A' + remaining % 26))
            remaining = remaining / 26 - 1
            if (remaining < 0) break
        }
        return name.toString()
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                // A literal newline inside a cell is legal in a shared string; keep it.
                '\n' -> append('\n')
                else -> if (character.code < 0x20 && character != '\t') Unit else append(character)
            }
        }
    }

    private const val FIXED_ENTRY_TIME = 315_532_800_000L // 1980-01-01, the DOS epoch

    private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val CONTENT_TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
    private const val OFFICE_REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private const val ROOT_RELS = XML_DECLARATION +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"
}
