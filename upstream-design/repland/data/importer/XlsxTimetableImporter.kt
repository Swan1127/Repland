package com.swan1127.repland.data.importer

import com.swan1127.repland.domain.ports.SpreadsheetGridImporter
import com.swan1127.repland.domain.ports.TimetableSource
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Reads a .xlsx timetable with no third-party library.
 *
 * An .xlsx is a ZIP of XML parts, and only two of them matter here: `xl/sharedStrings.xml`,
 * the shared string table, and `xl/worksheets/sheet*.xml`, the cells that reference that
 * table by index. Parsing those two by hand keeps the dependency list at zero — and, more to
 * the point, means the user's timetable never has to leave the device to be understood.
 *
 * This class only produces the raw grid; [TimetableGridParser] does the actual recognition,
 * so the rules are shared with the clipboard path.
 */
class XlsxTimetableImporter : SpreadsheetGridImporter {

    override suspend fun parse(source: TimetableSource): List<List<String>> = withContext(Dispatchers.IO) {
        val parts = readParts(source)
        val shared = parts["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheet = parts.entries
            .firstOrNull { (name, _) ->
                name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")
            }
            ?.value
            ?: throw TimetableImportException("这个 .xlsx 里没有工作表")
        parseSheet(sheet, shared)
    }

    /**
     * Pulls the two interesting parts out of the archive.
     *
     * Only those parts are read into memory, so a workbook with a large embedded image does
     * not get decompressed just to look at a timetable.
     */
    private fun readParts(source: TimetableSource): Map<String, String> {
        val parts = mutableMapOf<String, String>()
        val input = source.open()
            ?: throw TimetableImportException("无法打开这个文件")
        input.use { stream ->
            ZipInputStream(stream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    val wanted = name == "xl/sharedStrings.xml" ||
                        (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                    if (wanted) {
                        // ZipInputStream reports end-of-entry as end-of-stream, so readBytes
                        // stops at the boundary rather than running into the next part.
                        parts[name] = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    zip.closeEntry()
                }
            }
        }
        if (parts.isEmpty()) throw TimetableImportException("这个文件不是有效的 .xlsx")
        return parts
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val document = document(xml)
        val items = document.getElementsByTagName("si")
        return (0 until items.length).map { index ->
            val item = items.item(index) as Element
            // Rich text splits one logical string across several <t> runs; join them back.
            val runs = item.getElementsByTagName("t")
            (0 until runs.length).joinToString("") { runs.item(it).textContent.orEmpty() }
        }
    }

    private fun parseSheet(xml: String, shared: List<String>): List<List<String>> {
        val document = document(xml)
        val cells = document.getElementsByTagName("c")
        val grid = mutableMapOf<Int, MutableMap<Int, String>>()
        var lastColumn = 0

        for (index in 0 until cells.length) {
            val cell = cells.item(index) as Element
            val (row, column) = splitReference(cell.getAttribute("r")) ?: continue
            val type = cell.getAttribute("t")
            val value = cell.getElementsByTagName("v").item(0)
            val inline = cell.getElementsByTagName("t").item(0)
            val text = when {
                // t="s" means the value is an index into the shared string table.
                type == "s" && value != null ->
                    shared.getOrNull(value.textContent.trim().toIntOrNull() ?: -1)

                value != null -> value.textContent
                inline != null -> inline.textContent
                else -> null
            }?.trim().orEmpty()
            if (text.isEmpty()) continue
            grid.getOrPut(row) { mutableMapOf() }[column] = text
            if (column > lastColumn) lastColumn = column
        }

        if (grid.isEmpty()) return emptyList()
        val lastRow = grid.keys.max()
        return (1..lastRow).map { row ->
            val columns = grid[row].orEmpty()
            (0..lastColumn).map { column -> columns[column].orEmpty() }
        }
    }

    /** Turns "B12" into row 12, column 1 (zero-based column, one-based row). */
    private fun splitReference(reference: String): Pair<Int, Int>? {
        if (reference.isEmpty()) return null
        var column = 0
        var index = 0
        while (index < reference.length && reference[index].isLetter()) {
            column = column * 26 + (reference[index].uppercaseChar() - 'A' + 1)
            index++
        }
        val row = reference.substring(index).toIntOrNull() ?: return null
        return row to (column - 1)
    }

    /**
     * Parses with DTDs and external entities switched off.
     *
     * The document is user-supplied, so an entity-expansion bomb is a real (if unlikely)
     * input. `setFeature` throws on parsers that do not know the feature, hence runCatching.
     */
    private fun document(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
        }
        return factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
    }
}
