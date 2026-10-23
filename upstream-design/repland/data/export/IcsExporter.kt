package com.swan1127.repland.data.export

import com.swan1127.repland.data.importer.IcsImporter
import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Minimal RFC 5545 writer for confirmed plan segments.
 *
 * Two deliberate choices:
 *
 * 1. **Floating event times; UTC creation stamp.** DTSTART/DTEND preserve local wall time.
 *    DTSTAMP is metadata and must instead describe the actual UTC instant (RFC 5545 §3.8.7.2).
 * 2. **No dependency.** The format is line-based text, so it is assembled with a StringBuilder
 *    rather than a library. Text is escaped, then folded at UTF-8 code-point boundaries so each
 *    physical content line is at most 75 octets, including a continuation space (§3.1).
 */
object IcsExporter {

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT)
    private val UTC_STAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

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
     * @param zoneId interprets [now]'s local wall time when converting DTSTAMP to a UTC instant.
     */
    fun build(
        plan: ConfirmedPlan?,
        tasks: List<Task>,
        date: LocalDate? = null,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
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
            sb.append("DTSTAMP:").append(UTC_STAMP.format(now.atZone(zoneId).toInstant())).append("\r\n")
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
        return foldContentLines(sb.toString())
    }

    /** RFC 5545 §3.1: CRLF + one space continues a logical line; that space consumes one octet. */
    private fun foldContentLines(content: String): String = buildString {
        content.removeSuffix("\r\n").splitToSequence("\r\n").forEach { line ->
            var octets = 0
            var index = 0
            while (index < line.length) {
                val codePoint = line.codePointAt(index)
                val size = when {
                    codePoint <= 0x7f -> 1
                    codePoint <= 0x7ff -> 2
                    codePoint <= 0xffff -> 3
                    else -> 4
                }
                if (octets + size > 75) {
                    append("\r\n ")
                    octets = 1
                }
                appendCodePoint(codePoint)
                octets += size
                index += Character.charCount(codePoint)
            }
            append("\r\n")
        }
    }

    private fun uidFor(segment: PlannedSegment): String =
        if (segment.id.isNotBlank()) segment.id else "${segment.taskId}-${segment.date}-${segment.startMinute}"

    /** Default filename, e.g. `repland-2026-09-28.ics` or `repland-plan.ics`. */
    fun fileName(date: LocalDate?): String =
        if (date == null) "repland-plan.ics" else "repland-$date.ics"
}
