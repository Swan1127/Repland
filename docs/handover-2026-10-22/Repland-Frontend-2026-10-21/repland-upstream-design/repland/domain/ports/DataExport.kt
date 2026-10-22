package com.swan1127.repland.domain.ports

/**
 * Where an export goes.
 *
 * The ViewModel produces the bytes and hands them here; turning a picked `Uri` into an open stream
 * is the Android side's job, done once in the UI layer. This is the same seam [TimetableSource]
 * uses on the way in, and it exists for the same reason: a ViewModel whose signature names `Uri`
 * cannot be exercised without an emulator, and the export is exactly the part worth exercising —
 * "did the file contain what the user thinks it contains" is not a question a screenshot answers.
 */
fun interface ExportSink {

    /**
     * Writes [bytes], replacing whatever was at the destination.
     *
     * **Throws when the destination cannot be opened or written.** The caller reports failure by
     * catching, so a sink that swallowed the error would turn a failed export into a success
     * message — the one outcome this must not produce, because the user would then believe their
     * data had been saved and might wipe the device copy.
     */
    fun write(bytes: ByteArray)
}

/**
 * The export formats the data screen offers.
 *
 * Only the MIME type lives here, because that is the one thing the system file picker needs before
 * any writer runs. The suggested file name stays with each exporter, matching [IcsExporter] and
 * `PlanImageExporter` — the class that knows how to write a format is the class that names it.
 */
enum class DataExportFormat(val mimeType: String) {

    /** A complete, re-importable copy of everything the app stores. */
    JSON("application/json"),

    /**
     * A spreadsheet for a person to read: the timetable grid this app can read back, the exact
     * blocks, the task list, and the investment trend.
     */
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}
