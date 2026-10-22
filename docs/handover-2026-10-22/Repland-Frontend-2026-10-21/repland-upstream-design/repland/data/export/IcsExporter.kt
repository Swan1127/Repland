package com.swan1127.repland.data.export

import com.swan1127.repland.data.importer.IcsImporter
import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Minimal RFC 5545 writer for confirmed plan segments.
 *
 * Two deliberate choices:
 *
 * 1. **Wall time, no trailing `Z`.** Every time in the app is a local wall-clock time. Writing
 *    `...Z` would declare it UTC and every imported event would shift by the UTC offset —
 *    a classic off-by-one-hour bug that looks like a calendar problem but is a format problem.
 *    Floating local time (`DTSTART:20260919T082000`) is exactly the right encoding here.
 * 2. **No dependency.** The format is line-based text, so it is assembled with a StringBuilder
 *    rather than a library. Escaping rules are applied per spec.
 */
object IcsExporter {

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** RFC 5545 §3.3.11: backslash, semicolon, comma and newlines must be escaped. */
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")

    private fun LocalDateTime.stamp(): String = format(STAMP)

    private fun LocalDate.atMinute(minute: Int): LocalDateTime =
        atStartOfDay().plusMinutes(minute.toLong())

    /**
     * @param date when non-null, only that day's segments are exported ("export today").
     *             When null the whole plan is exported ("export everything").
     */
    fun build(
        plan: ConfirmedPlan?,
        tasks: List<Task>,
        date: LocalDate? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        val titleById = tasks.associate { it.id to it.displayName }
        val segments = plan?.segments.orEmpty()
            .filter { date == null || it.date == date }
            .sortedWith(compareBy({ it.date }, { it.startMinute }))

        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//Repland//CN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")

        segments.forEach { segment ->
            val title = titleById[segment.taskId] ?: "未命名任务"
            sb.append("BEGIN:VEVENT\r\n")
            // Stable per-segment UID so re-importing updates rather than duplicates. The prefix and
            // suffix are read from the reader rather than written out again here: the reader uses
            // exactly this pair to recognise its own output, so two copies of the literal could
            // drift and the round trip would then re-import the user's own plan as busy time.
            sb.append("UID:")
                .append(IcsImporter.OWN_UID_PREFIX)
                .append(uidFor(segment))
                .append(IcsImporter.OWN_UID_SUFFIX)
                .append("\r\n")
            sb.append("DTSTAMP:").append(now.stamp()).append("\r\n")
            sb.append("DTSTART:").append(segment.date.atMinute(segment.startMinute).stamp()).append("\r\n")
            sb.append("DTEND:").append(segment.date.atMinute(segment.endMinute).stamp()).append("\r\n")
            sb.append("SUMMARY:").append(escape(title)).append("\r\n")
            sb.append("DESCRIPTION:")
                .append(escape("Repland 计划段落 · ${segment.endMinute - segment.startMinute} 分钟"))
                .append("\r\n")
            if (segment.isLocked) {
                sb.append("CATEGORIES:已锁定\r\n")
            }
            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun uidFor(segment: PlannedSegment): String =
        if (segment.id.isNotBlank()) segment.id else "${segment.taskId}-${segment.date}-${segment.startMinute}"

    /** Default filename, e.g. `repland-2026-09-28.ics` or `repland-plan.ics`. */
    fun fileName(date: LocalDate?): String =
        if (date == null) "repland-plan.ics" else "repland-$date.ics"
}
