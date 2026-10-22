package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.ImportedCourse
import java.io.InputStream

/**
 * A timetable file the user picked, reduced to the one thing reading it actually needs: a way
 * to open its bytes.
 *
 * The ViewModel deliberately never sees `android.net.Uri`. Storage Access Framework hands back
 * opaque `content://` ids that only a `Context` can open, so a Uri-shaped parameter drags
 * Android into the class whose entire job is deciding what to *do* with the rows — and `Uri`
 * has no usable constructor off-device, which is what made every method taking one unreachable
 * from a plain JVM test. Resolving the Uri is the caller's business; reading is the importer's.
 *
 * [open] returns null when the source cannot be read, mirroring
 * `ContentResolver.openInputStream`, so the "could not read the file" path stays one branch
 * instead of two.
 */
fun interface TimetableSource {
    fun open(): InputStream?
}

/** Reads the weekday-by-period PDF timetable layout into import candidates. */
interface TimetableImporter {
    suspend fun parse(source: TimetableSource): List<ImportedCourse>
}

/**
 * Reads the raw `星期 × 节次` grid out of a spreadsheet.
 *
 * Returns the grid rather than courses on purpose: the recognition rules are shared with the
 * clipboard path, so whoever asks for a grid runs [com.swan1127.repland.data.importer.TimetableGridParser]
 * over either source and gets the same answer.
 */
interface SpreadsheetGridImporter {
    suspend fun parse(source: TimetableSource): List<List<String>>
}
