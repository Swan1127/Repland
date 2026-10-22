package com.swan1127.repland.data.importer

import com.swan1127.repland.domain.ports.TimetableSource
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [XlsxTimetableImporter] reads the two parts of an `.xlsx` that matter — the shared string table
 * and the first worksheet — by hand, so the ways it can go wrong are specific: a cell type it does
 * not resolve, a row it drops, or a malformed file it accepts instead of refusing.
 *
 * The fixtures here are **hand-built archives**, not files produced by
 * [com.swan1127.repland.data.export.XlsxExporter]. That is deliberate: using the app's own writer
 * would mean the reader and the writer could share a misunderstanding and still agree. The three
 * cell encodings below (shared string, literal value, inline string) are the ones real producers
 * emit, and the round trip through the exporter is asserted separately in `XlsxExporterTest`.
 */
class XlsxTimetableImporterTest {

    private val importer = XlsxTimetableImporter()

    private fun sourceOf(bytes: ByteArray) = TimetableSource { bytes.inputStream() }

    private fun xlsx(vararg parts: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun worksheet(body: String) = "xl/worksheets/sheet1.xml" to
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet><sheetData>$body</sheetData></worksheet>"

    private fun strings(vararg values: String) = "xl/sharedStrings.xml" to
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><sst>" +
        values.joinToString("") { "<si><t>$it</t></si>" } +
        "</sst>"

    // ------------------------------------------------------------------ reading cells

    @Test
    fun `a shared string cell is resolved through the string table`() = runTest {
        val grid = importer.parse(
            sourceOf(
                xlsx(
                    strings("节次", "星期一", "数学"),
                    worksheet(
                        "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>" +
                            "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>2</v></c></row>",
                    ),
                ),
            ),
        )

        // The short row is padded back out to the widest column, so a caller can index it.
        assertEquals(listOf(listOf("节次", "星期一"), listOf("数学", "")), grid)
    }

    @Test
    fun `a literal value cell is read without the string table`() = runTest {
        val grid = importer.parse(
            sourceOf(xlsx(worksheet("<row r=\"1\"><c r=\"A1\"><v>42</v></c></row>"))),
        )

        assertEquals(listOf(listOf("42")), grid)
    }

    @Test
    fun `an inline string cell is read from its t element`() = runTest {
        val grid = importer.parse(
            sourceOf(
                xlsx(worksheet("<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><t>数学</t></c></row>")),
            ),
        )

        assertEquals(listOf(listOf("数学")), grid)
    }

    @Test
    fun `a rich text cell has its runs joined back together`() = runTest {
        val grid = importer.parse(
            sourceOf(
                xlsx(
                    "xl/sharedStrings.xml" to
                        "<sst><si><t>高等</t><t>数学</t></si></sst>",
                    worksheet("<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row>"),
                ),
            ),
        )

        // Excel splits styled text into several runs; the logical string is their concatenation.
        assertEquals(listOf(listOf("高等数学")), grid)
    }

    @Test
    fun `a cell whose string index is out of range is dropped rather than crashing`() = runTest {
        val grid = importer.parse(
            sourceOf(
                xlsx(
                    strings("节次"),
                    worksheet("<row r=\"1\"><c r=\"A1\" t=\"s\"><v>7</v></c></row>"),
                ),
            ),
        )

        assertTrue(grid.isEmpty())
    }

    @Test
    fun `an empty cell is skipped and does not widen the grid`() = runTest {
        val grid = importer.parse(
            sourceOf(
                xlsx(
                    strings("数学"),
                    worksheet("<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"C1\" t=\"s\"><v/></c></row>"),
                ),
            ),
        )

        assertEquals(listOf(listOf("数学")), grid)
    }

    @Test
    fun `rows are returned in order even when one of them is empty`() = runTest {
        val grid = importer.parse(
            sourceOf(
                xlsx(
                    strings("上", "下"),
                    worksheet(
                        "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row>" +
                            "<row r=\"3\"><c r=\"A3\" t=\"s\"><v>1</v></c></row>",
                    ),
                ),
            ),
        )

        // Row 2 held nothing, but dropping it would shift every row below it — which for a
        // timetable means the periods no longer line up with the courses.
        assertEquals(listOf(listOf("上"), listOf(""), listOf("下")), grid)
    }

    @Test
    fun `only the first worksheet is read`() = runTest {
        val grid = importer.parse(
            sourceOf(
                xlsx(
                    strings("第一张", "第二张"),
                    "xl/worksheets/sheet1.xml" to
                        "<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row></sheetData></worksheet>",
                    "xl/worksheets/sheet2.xml" to
                        "<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>1</v></c></row></sheetData></worksheet>",
                ),
            ),
        )

        // This is the contract the exporter depends on: it writes 周课表 as sheet 1, because a
        // timetable export with five sheets is only re-importable if the timetable is the first.
        assertEquals(listOf(listOf("第一张")), grid)
    }

    // ------------------------------------------------------------------ refusing bad input

    @Test
    fun `an archive with no worksheet is rejected`() = runTest {
        val failure = runCatching {
            importer.parse(sourceOf(xlsx("docProps/app.xml" to "<Properties/>")))
        }.exceptionOrNull()

        assertTrue("expected a TimetableImportException, was $failure", failure is TimetableImportException)
    }

    @Test
    fun `an archive with no readable part at all is rejected`() = runTest {
        val failure = runCatching {
            importer.parse(sourceOf(xlsx("docProps/app.xml" to "<Properties/>")))
        }.exceptionOrNull()

        assertTrue("expected a TimetableImportException, was $failure", failure is TimetableImportException)
    }

    @Test
    fun `a source that cannot be opened is reported rather than crashing`() = runTest {
        val failure = runCatching {
            importer.parse(TimetableSource { null })
        }.exceptionOrNull()

        assertTrue("expected a TimetableImportException, was $failure", failure is TimetableImportException)
    }

    @Test
    fun `a worksheet with no cells at all yields an empty grid`() = runTest {
        val grid = importer.parse(sourceOf(xlsx(worksheet(""))))

        assertTrue(grid.isEmpty())
    }
}
