package com.swan1127.repland.data.export

import com.swan1127.repland.data.importer.TimetableGridParser
import com.swan1127.repland.data.importer.XlsxTimetableImporter
import com.swan1127.repland.domain.model.AiPreferences
import com.swan1127.repland.domain.model.ClassPeriodClock
import com.swan1127.repland.domain.model.LocalDataSnapshot
import com.swan1127.repland.domain.model.ReminderPreferences
import com.swan1127.repland.domain.model.RepeatRule
import com.swan1127.repland.domain.model.TEST_CLOCK
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TimeConstraintSettings
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.executionLog
import com.swan1127.repland.domain.model.feedback
import com.swan1127.repland.domain.model.task
import com.swan1127.repland.domain.model.weeklyBlock
import com.swan1127.repland.domain.ports.TimetableSource
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * [XlsxExporter] writes a real `.xlsx` by hand, which exposes two classes of mistake that a
 * library would have absorbed: the archive can be malformed, and a sheet can say something this
 * app cannot read back.
 *
 * Both are checked, and the two checks deliberately use **different readers**. Structure, cell
 * values and cell *types* are read with [cellsOf], a reader written for this test only — using the
 * app's own [XlsxTimetableImporter] for those would let one shared misunderstanding pass as
 * agreement between writer and reader. The round trip is then checked separately with the real
 * importer, because "can this app read what it wrote" is a property of the pair and has to be
 * asserted as one.
 *
 * The period clock is the fixed one from [ClassPeriodClock]: period 1 is 08:00–08:45, period 2 is
 * 08:50–09:35, period 3 is 10:00–10:45, period 4 is 10:50–11:35.
 */
class XlsxExporterTest {

    private val zone = TEST_CLOCK.zone
    private val today = LocalDate.now(TEST_CLOCK)

    private val periodOne = 8 * 60 to 8 * 60 + 45
    private val periodOneTwo = 8 * 60 to 9 * 60 + 35
    private val periodThree = 10 * 60 to 10 * 60 + 45
    private val periodThreeFour = 10 * 60 to 11 * 60 + 35

    // ------------------------------------------------------------------ fixtures

    private fun snapshot(
        tasks: List<Task> = emptyList(),
        logs: List<TaskExecutionLog> = emptyList(),
        blocks: List<WeeklyTimeBlock> = emptyList(),
    ) = LocalDataSnapshot(
        generatedAtEpochMillis = 1_000L,
        tasks = tasks,
        executionLogs = logs,
        weeklyTimeBlocks = blocks,
        dateOverrides = emptyList(),
        timeConstraintSettings = TimeConstraintSettings(null, 0L),
        planHistory = emptyList(),
        categoryPreferences = emptyMap(),
        reminderPreferences = ReminderPreferences(),
        aiPreferences = AiPreferences(),
        profileEvidence = emptyList(),
    )

    private fun build(snapshot: LocalDataSnapshot) = XlsxExporter.build(snapshot, today, zone)

    /** Every part of the archive, by entry name. */
    private fun archive(bytes: ByteArray): Map<String, String> {
        val parts = linkedMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return parts
    }

    /**
     * Reads one worksheet into rows of text, resolving shared strings.
     *
     * Written for this test rather than reused from the app: see the class comment. Cells that were
     * never written (a blank) simply do not appear, so the rows are padded back out to the widest
     * column seen.
     */
    private fun cellsOf(parts: Map<String, String>, sheet: Int): List<List<String>> {
        val table = document(parts.getValue("xl/sharedStrings.xml")).getElementsByTagName("si")
        val shared = (0 until table.length).map { index ->
            val runs = (table.item(index) as Element).getElementsByTagName("t")
            (0 until runs.length).joinToString("") { runs.item(it).textContent.orEmpty() }
        }
        val cells = document(parts.getValue("xl/worksheets/sheet$sheet.xml")).getElementsByTagName("c")
        val grid = sortedMapOf<Int, MutableMap<Int, String>>()
        var widest = 0
        for (index in 0 until cells.length) {
            val cell = cells.item(index) as Element
            val reference = cell.getAttribute("r")
            val column = reference.takeWhile(Char::isLetter)
                .fold(0) { acc, letter -> acc * 26 + (letter - 'A' + 1) } - 1
            val row = reference.dropWhile(Char::isLetter).toInt()
            val raw = cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
            val text = if (cell.getAttribute("t") == "s") shared[raw.trim().toInt()] else raw
            grid.getOrPut(row) { mutableMapOf() }[column] = text
            if (column > widest) widest = column
        }
        return grid.entries.map { (_, row) -> (0..widest).map { row[it].orEmpty() } }
    }

    private fun document(xml: String) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(xml.byteInputStream(Charsets.UTF_8))

    /** The 周课表 sheet, header row first. */
    private fun grid(bytes: ByteArray) = cellsOf(archive(bytes), 1)

    /** The 时间块明细 sheet, header row first. */
    private fun details(bytes: ByteArray) = cellsOf(archive(bytes), 2)

    private fun sourceOf(bytes: ByteArray) = TimetableSource { bytes.inputStream() }

    // ------------------------------------------------------------------ the archive

    @Test
    fun `the archive carries every part a spreadsheet reader needs`() {
        val parts = archive(build(snapshot()))

        assertEquals(
            listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/sharedStrings.xml",
                "xl/worksheets/sheet1.xml",
                "xl/worksheets/sheet2.xml",
                "xl/worksheets/sheet3.xml",
                "xl/worksheets/sheet4.xml",
                "xl/worksheets/sheet5.xml",
            ),
            parts.keys.toList(),
        )
    }

    @Test
    fun `the workbook declares five sheets and 周课表 is the first`() {
        val workbook = archive(build(snapshot())).getValue("xl/workbook.xml")

        val names = listOf("周课表", "时间块明细", "任务", "每日投入", "分类投入")
        names.forEach { name ->
            assertTrue("workbook should name $name, was: $workbook", workbook.contains("name=\"$name\""))
        }
        // The importer reads the first worksheet it finds, so 周课表 has to be sheet 1 for the
        // round trip to mean anything. The grid's own header is the observable proof of that.
        assertEquals(listOf("节次", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"), grid(build(snapshot()))[0])
    }

    @Test
    fun `the same snapshot always produces the same bytes`() {
        val one = snapshot(tasks = listOf(task(id = "a")), blocks = listOf(weeklyBlock(startMinute = periodOneTwo.first, endMinute = periodOneTwo.second)))

        // A real clock inside the ZIP would make these differ for no reason, which would also make
        // "did the export change?" unanswerable.
        assertArrayEquals(build(one), build(one))
    }

    @Test
    fun `a different snapshot produces different bytes`() {
        val quiet = snapshot(tasks = listOf(task(id = "a")))
        val busy = snapshot(tasks = listOf(task(id = "a"), task(id = "b")))

        assertNotEquals(build(quiet).size, build(busy).size)
    }

    @Test
    fun `an empty snapshot still produces a readable archive`() {
        val bytes = build(snapshot())

        // Header only, and the reader agrees there are no courses rather than failing.
        assertEquals(1, grid(bytes).size)
        assertTrue(TimetableGridParser.parse(grid(bytes)).isEmpty())
    }

    // ------------------------------------------------------------------ the grid

    @Test
    fun `a block on the period clock is written as its period span`() {
        val bytes = build(
            snapshot(blocks = listOf(weeklyBlock(title = "高等数学", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second))),
        )

        assertEquals(listOf("1-2", "高等数学"), grid(bytes)[1].take(2))
    }

    @Test
    fun `a single period block is written as one number rather than a range`() {
        val bytes = build(
            snapshot(blocks = listOf(weeklyBlock(title = "体育", startMinute = periodThree.first, endMinute = periodThree.second))),
        )

        // Both "3" and "3-3" read back as period 3; a real timetable writes the former.
        assertEquals("3", grid(bytes)[1][0])
    }

    @Test
    fun `the weekday column follows the day of the block`() {
        val bytes = build(
            snapshot(
                blocks = listOf(
                    weeklyBlock(title = "物理", dayOfWeek = DayOfWeek.WEDNESDAY, startMinute = periodThreeFour.first, endMinute = periodThreeFour.second),
                ),
            ),
        )

        // 星期一 is column B, so Wednesday is column D.
        assertEquals(listOf("3-4", "", "", "物理"), grid(bytes)[1].take(4))
    }

    @Test
    fun `rows are ordered by period so the sheet reads like a day`() {
        val bytes = build(
            snapshot(
                blocks = listOf(
                    weeklyBlock(title = "下午", startMinute = periodThreeFour.first, endMinute = periodThreeFour.second),
                    weeklyBlock(title = "上午", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second),
                ),
            ),
        )

        assertEquals(listOf("1-2", "3-4"), grid(bytes).drop(1).map { it[0] })
    }

    @Test
    fun `two blocks sharing a span and a day share one cell joined by a newline`() {
        val bytes = build(
            snapshot(
                blocks = listOf(
                    weeklyBlock(id = "a", title = "数学", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second),
                    weeklyBlock(id = "b", title = "英语", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second),
                ),
            ),
        )

        // A grid cell holds one string, so this is a deliberate limit rather than an oversight —
        // which is exactly why 时间块明细 exists and is asserted to hold both rows below.
        assertEquals("数学\n英语", grid(bytes)[1][1])
        assertEquals(listOf("数学", "英语"), details(bytes).drop(1).map { it[5] })
    }

    // ------------------------------------------------------------------ what the grid refuses

    @Test
    fun `a block with hand set times is kept out of the grid but kept in the detail sheet`() {
        val offClock = weeklyBlock(title = "自定义自习", startMinute = 8 * 60, endMinute = 9 * 60)
        val bytes = build(snapshot(blocks = listOf(offClock)))

        assertEquals(1, grid(bytes).size)
        assertEquals("自定义自习", details(bytes)[1][5])
        assertEquals("08:00", details(bytes)[1][1])
        assertEquals("09:00", details(bytes)[1][2])
    }

    @Test
    fun `a title that merely contains a week pattern is kept out of the grid`() {
        // The reader strips the first "N-N周" run from anywhere in the cell, so writing this title
        // would silently rename the course to "第复习" on the way back in.
        val bytes = build(
            snapshot(blocks = listOf(weeklyBlock(title = "第1-16周复习", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second))),
        )

        assertEquals(1, grid(bytes).size)
        assertEquals("第1-16周复习", details(bytes)[1][5])
    }

    @Test
    fun `a padded title is kept out of the grid because the reader trims every cell`() {
        val bytes = build(
            snapshot(blocks = listOf(weeklyBlock(title = "高等数学 ", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second))),
        )

        assertEquals(1, grid(bytes).size)
        assertEquals("高等数学 ", details(bytes)[1][5])
    }

    @Test
    fun `a week pattern the notation cannot express is kept out of the grid`() {
        val bytes = build(
            snapshot(
                blocks = listOf(
                    weeklyBlock(title = "选修", weekPattern = "单周", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second),
                ),
            ),
        )

        // Not mangled into "选修(单周)" as a title with no pattern — left out instead, so the
        // pattern is never silently dropped on a re-import.
        assertEquals(1, grid(bytes).size)
        assertEquals("单周", details(bytes)[1][4])
    }

    @Test
    fun `a week pattern the reader would normalise is kept out of the grid`() {
        val bytes = build(
            snapshot(
                blocks = listOf(
                    weeklyBlock(title = "选修", weekPattern = "1~16周", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second),
                ),
            ),
        )

        // The reader accepts a tilde as the separator and writes the pattern back with a hyphen,
        // so this cell would return "1-16周" where the user typed "1~16周". The title survives that
        // trip, which is exactly why the title alone is not enough to decide placement.
        assertEquals(1, grid(bytes).size)
        assertEquals("1~16周", details(bytes)[1][4])
    }

    @Test
    fun `a title containing a week pattern is kept out of the grid even when the block has one`() {
        val bytes = build(
            snapshot(
                blocks = listOf(
                    weeklyBlock(
                        title = "第1-16周复习",
                        weekPattern = "1-16周",
                        startMinute = periodOneTwo.first,
                        endMinute = periodOneTwo.second,
                    ),
                ),
            ),
        )

        // Appending the pattern would put a second "1-16周" in the cell and the reader takes the
        // first one — the one inside the title — so the title would come back as "第复习(1-16周".
        assertEquals(1, grid(bytes).size)
        assertEquals("第1-16周复习", details(bytes)[1][5])
        assertEquals("1-16周", details(bytes)[1][4])
    }

    // ------------------------------------------------------------------ the round trip

    @Test
    fun `a grid course comes back through the real importer at the same minutes`() = runTest {
        val bytes = build(
            snapshot(
                blocks = listOf(
                    weeklyBlock(title = "高等数学", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second),
                    weeklyBlock(title = "大学物理", dayOfWeek = DayOfWeek.WEDNESDAY, startMinute = periodThreeFour.first, endMinute = periodThreeFour.second),
                ),
            ),
        )

        val courses = TimetableGridParser.parse(XlsxTimetableImporter().parse(sourceOf(bytes)))

        assertEquals(listOf("高等数学", "大学物理"), courses.map { it.title })
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), courses.map { it.dayOfWeek })
        assertEquals(listOf(1 to 2, 3 to 4), courses.map { it.startPeriod to it.endPeriod })

        // The stronger statement: the clock puts the block back on the same minutes it started on.
        val back = courses.map { ClassPeriodClock.toWeeklyTimeBlockDraft(it)!! }
        assertEquals(listOf(periodOneTwo, periodThreeFour), back.map { it.startMinute to it.endMinute })
    }

    @Test
    fun `the week pattern survives the round trip`() = runTest {
        val bytes = build(
            snapshot(
                blocks = listOf(
                    weeklyBlock(title = "高等数学", weekPattern = "1-16周", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second),
                ),
            ),
        )

        val course = TimetableGridParser.parse(XlsxTimetableImporter().parse(sourceOf(bytes))).single()

        assertEquals("高等数学", course.title)
        assertEquals("1-16周", course.weekPattern)
    }

    @Test
    fun `a block the grid refused does not appear in a re-import`() = runTest {
        val bytes = build(
            snapshot(
                blocks = listOf(
                    weeklyBlock(id = "a", title = "高等数学", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second),
                    weeklyBlock(id = "b", title = "自定义自习", startMinute = 8 * 60, endMinute = 9 * 60),
                ),
            ),
        )

        val courses = TimetableGridParser.parse(XlsxTimetableImporter().parse(sourceOf(bytes)))

        // Absent is safe; renamed or re-timed would not be.
        assertEquals(listOf("高等数学"), courses.map { it.title })
    }

    // ------------------------------------------------------------------ the other sheets

    @Test
    fun `minutes are written as numbers so a spreadsheet can sum the column`() {
        val bytes = build(
            snapshot(blocks = listOf(weeklyBlock(title = "高等数学", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second))),
        )

        // 08:00–09:35 is 95 minutes. No t="s" means Excel reads a number, not text.
        val sheet = archive(bytes).getValue("xl/worksheets/sheet2.xml")
        assertTrue("expected a numeric cell, was: $sheet", sheet.contains("<c r=\"D2\"><v>95</v></c>"))
    }

    @Test
    fun `markup in a title is escaped and reads back as the original text`() {
        val bytes = build(
            snapshot(blocks = listOf(weeklyBlock(title = "A & B <C>", startMinute = periodOneTwo.first, endMinute = periodOneTwo.second))),
        )

        val shared = archive(bytes).getValue("xl/sharedStrings.xml")
        assertTrue("expected escaped text, was: $shared", shared.contains("A &amp; B &lt;C&gt;"))
        assertFalse(shared.contains("A & B <C>"))
        assertEquals("A & B <C>", details(bytes)[1][5])
    }

    @Test
    fun `a task with no recorded estimate writes an empty cell rather than a zero`() {
        val bytes = build(snapshot(tasks = listOf(task(id = "a", durationMinutes = null))))

        val row = cellsOf(archive(bytes), 3)[1]

        // A zero would read as "planned to take no time", which is a different claim.
        assertEquals("", row[4])
        assertEquals("", row[5])
    }

    @Test
    fun `the task sheet puts the estimate next to the actual`() {
        val bytes = build(
            snapshot(tasks = listOf(task(id = "a").copy(actualDurationMinutes = 90))),
        )

        val row = cellsOf(archive(bytes), 3)[1]

        // Adjacent so the calibration story is readable across the row before any chart exists.
        assertEquals("Task a", row[0])
        assertEquals("COURSE", row[1])
        assertEquals("60", row[4])
        assertEquals("90", row[5])
    }

    @Test
    fun `tasks are grouped by category so the sheet reads in a stable order`() {
        val bytes = build(
            snapshot(
                tasks = listOf(
                    task(id = "b", category = TaskCategory.OFFICE),
                    task(id = "a", category = TaskCategory.COURSE),
                ),
            ),
        )

        assertEquals(listOf("Task a", "Task b"), cellsOf(archive(bytes), 3).drop(1).map { it[0] })
    }

    @Test
    fun `the daily sheet covers the same thirty days as the insight page`() {
        val loggedToday = executionLog(
            id = "l1",
            taskId = "t1",
            feedback = feedback(actualDurationMinutes = 45),
            createdAtEpochMillis = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
        )
        val bytes = build(snapshot(logs = listOf(loggedToday)))

        val sheet = cellsOf(archive(bytes), 4)

        // Thirty rows plus the header, and empty days are present as zero rather than missing —
        // a trend line with holes reads as missing data instead of as a day off.
        assertEquals(31, sheet.size)
        assertEquals(today.minusDays(29).toString(), sheet[1][0])
        assertEquals("0", sheet[1][1])
        assertEquals(today.toString(), sheet[30][0])
        assertEquals("45", sheet[30][1])
    }

    @Test
    fun `a category with nothing invested is left out of the category sheet`() {
        val bytes = build(
            snapshot(
                tasks = listOf(
                    task(id = "a", category = TaskCategory.COURSE).copy(actualDurationMinutes = 45),
                    task(id = "b", category = TaskCategory.OFFICE),
                ),
            ),
        )

        val sheet = cellsOf(archive(bytes), 5)

        assertEquals(listOf(listOf("分类", "投入(分钟)"), listOf("COURSE", "45")), sheet)
    }

    // ------------------------------------------------------------------ the repeat column

    @Test
    fun `the task sheet carries a repeat column in the user's own words`() {
        val bytes = build(
            snapshot(
                tasks = listOf(
                    task(id = "a", repeatRule = RepeatRule.WEEKLY),
                    task(id = "b"),
                ),
            ),
        )

        val rows = cellsOf(archive(bytes), 3)

        assertEquals("重复", rows[0][8])
        // The words the editor shows, not the storage spelling: the sheet is read by a person, and
        // "WEEKLY" is the enum's name, not the product's.
        assertEquals("每周", rows[1][8])
        // A one-off writes an empty cell rather than the word "NONE" — there is no such rule, and
        // inventing a spelling for it would be the sheet asserting something the model cannot say.
        assertEquals("", rows[2][8])
    }

    @Test
    fun `the repeat labels in the sheet are the ones the editor shows`() {
        // The exporter has no `Context`, so it writes these three labels as literals — the same
        // choice the sheet headers already make. That is fine until `strings.xml` is reworded, at
        // which point the spreadsheet and the editor would quietly disagree.
        //
        // This reads both and compares, so there is no third copy of the words to keep in step.
        // The default locale is the right reference because the sheet is written in Chinese
        // throughout; `values-en` is not what this file emits.
        val strings = File("src/main/res/values/strings.xml").readText()
        val bytes = build(
            snapshot(tasks = RepeatRule.entries.map { rule -> task(id = rule.name, repeatRule = rule) }),
        )

        // Keyed by display name rather than by row order, so the assertion does not depend on how
        // the sheet happens to sort.
        val byDisplayName = cellsOf(archive(bytes), 3).drop(1).associate { it[0] to it[8] }

        RepeatRule.entries.forEach { rule ->
            assertEquals(
                "the sheet must show what the editor shows for $rule",
                stringValue(strings, "repeat_${rule.name.lowercase()}"),
                byDisplayName["Task ${rule.name}"],
            )
        }
    }

    /** The text of a `<string name="key">…</string>` entry, failing loudly when the key is gone. */
    private fun stringValue(xml: String, key: String): String =
        requireNotNull(Regex("""<string name="$key">([^<]*)</string>""").find(xml)) {
            "no <string name=\"$key\"> in strings.xml"
        }.groupValues[1]
}
