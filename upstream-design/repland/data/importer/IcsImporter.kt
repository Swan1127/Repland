package com.swan1127.repland.data.importer

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The timing of one calendar event, in the shape the app actually needs.
 *
 * Three cases, not two, because "we read a date but not a time" and "we could not read the time
 * at all" need different answers: the first is an all-day commitment, the second is a defect in the
 * file. Folding them together would either drop a real all-day event or invent a midnight-to-
 * midnight block out of an unparseable line.
 */
sealed interface IcsTiming {

    /** A wall-clock range on one day. */
    data class Timed(val start: LocalDateTime, val end: LocalDateTime) : IcsTiming

    /**
     * A whole-day (or multi-day) marker.
     *
     * [endExclusive] is **exclusive**, which is RFC 5545 §3.6.1's own rule and the single most
     * common way a calendar importer loses a day: an event on the 19th carries `DTEND:20260920`,
     * so a reader that treats the end as inclusive produces a block that covers the 20th too.
     */
    data class AllDay(val start: LocalDate, val endExclusive: LocalDate) : IcsTiming

    /** `DTSTART` was present but could not be read. Kept so the caller can name the value in a report. */
    data class Unreadable(val raw: String) : IcsTiming
}

/**
 * How often an event repeats.
 *
 * [Unsupported] is a case rather than a null because a rule this reader will not expand must be
 * *reported*, not ignored: a biweekly seminar silently dropped from the calendar is exactly the
 * kind of missing commitment the planner would then schedule work on top of.
 */
sealed interface IcsRecurrence {

    data object None : IcsRecurrence

    /**
     * `FREQ=WEEKLY` at a one-week interval.
     *
     * @param days the weekdays it lands on (from `BYDAY`, or the start date's own weekday).
     * @param lastDate the local date of `UNTIL`, or null when the series never ends (`UNTIL` and
     *                 `COUNT` both absent). A `COUNT`-limited series is reported as [Unsupported]
     *                 rather than guessed at, because "N more weeks" has no anchor without a
     *                 semester start and a wrong guess would block time for months.
     */
    data class Weekly(val days: Set<DayOfWeek>, val lastDate: LocalDate?) : IcsRecurrence

    data class Unsupported(val rule: String) : IcsRecurrence
}

enum class IcsStatus { CONFIRMED, CANCELLED }

/** One `VEVENT`, already unfolded, unescaped and decoded. */
data class IcsEvent(
    val uid: String,
    val summary: String,
    val timing: IcsTiming,
    val recurrence: IcsRecurrence = IcsRecurrence.None,
    val status: IcsStatus = IcsStatus.CONFIRMED,
    /** False for `TRANSP:TRANSPARENT` — the organiser marked the time as free. */
    val busy: Boolean = true,
    /**
     * True when this event was written by this app's own exporter.
     *
     * Export → import is a normal thing for a user to try, and without this flag the round trip
     * would take the user's *own plan* and re-import it as outside commitments — every planned
     * block would come back as a busy period, and the planner would then refuse to place the very
     * work those blocks were made for.
     */
    val fromRepland: Boolean = false,
)

/**
 * Minimal RFC 5545 reader for `VEVENT`s.
 *
 * A reader, not a library: the parts of the spec this app meets are line-based text, and a full
 * iCalendar dependency would be larger than the feature. The rules implemented here are the ones
 * that change what the user sees, and each has a test named after the mistake it prevents.
 *
 * **Time is the whole difficulty.** Three encodings appear in real files and they are not
 * interchangeable:
 *
 *  - `20260919T082000` — floating local wall time. This is what
 *    [com.swan1127.repland.data.export.IcsExporter] writes, because every time in the app is a
 *    wall-clock time and declaring it UTC would shift it.
 *  - `20260919T082000Z` — an absolute instant. Converting it to the device zone **can move the
 *    date**, so the date is derived *after* the conversion, never before.
 *  - `DTSTART;TZID=Asia/Shanghai:...` — wall time in a named zone.
 *
 * Anything not understood becomes a typed value ([IcsTiming.Unreadable], [IcsRecurrence.Unsupported])
 * rather than a silently skipped line, so the caller can count it and say so.
 */
object IcsImporter {

    /** The UID prefix and suffix [IcsExporter] writes. Both are required, so a foreign UID that merely starts the same way is not swallowed. */
    const val OWN_UID_PREFIX: String = "repland-"
    const val OWN_UID_SUFFIX: String = "@local"

    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private const val VEVENT = "VEVENT"

    fun parse(text: String): List<IcsEvent> = parse(text, ZoneId.systemDefault())

    /**
     * @param zone the zone used to turn `...Z` instants into wall time, and the fallback for an
     *             unreadable `TZID`. A parameter rather than `ZoneId.systemDefault()` inside so a
     *             test can pin a zone and assert a date that a machine in another zone would read
     *             differently — which is precisely the bug class this reader is about.
     */
    fun parse(text: String, zone: ZoneId): List<IcsEvent> {
        val events = mutableListOf<IcsEvent>()
        var current: MutableMap<String, IcsProperty>? = null
        var nested = 0
        unfold(text).forEach { line ->
            val property = splitProperty(line) ?: return@forEach
            when (property.name) {
                "BEGIN" -> when {
                    property.value.equals(VEVENT, ignoreCase = true) && current == null -> {
                        current = mutableMapOf()
                        nested = 0
                    }
                    // A nested component (`VALARM` is the common one) has its own SUMMARY and
                    // DESCRIPTION. Counting depth is what keeps an alarm's text from overwriting
                    // the event's own — the alarm is read last, so "last write wins" would do it.
                    current != null -> nested++
                }

                "END" -> when {
                    property.value.equals(VEVENT, ignoreCase = true) && current != null && nested == 0 -> {
                        events += current.toEvent(zone)
                        current = null
                    }
                    current != null -> nested = (nested - 1).coerceAtLeast(0)
                }

                else -> if (current != null && nested == 0) {
                    // First occurrence wins: producers repeat SUMMARY or DTSTART across
                    // duplicated blocks, and the first is the one that describes the event.
                    current.putIfAbsent(property.name, property)
                }
            }
        }
        return events
    }

    // ------------------------------------------------------------------ line layer

    /**
     * Joins folded lines back into logical lines.
     *
     * RFC 5545 §3.1 folds a long line by inserting CRLF plus **exactly one** space or tab, so
     * unfolding removes exactly one character. Removing all leading whitespace would eat a
     * meaningful space; not unfolding at all would truncate every SUMMARY long enough to be folded,
     * which is most of them.
     *
     * Both line endings are accepted because LF-only files are common outside conforming producers.
     */
    private fun unfold(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val unfolded = mutableListOf<String>()
        normalized.split('\n').forEach { line ->
            if (line.startsWith(' ') || line.startsWith('\t')) {
                // A continuation with nothing to continue is malformed; dropping it keeps the
                // rest of the file readable rather than merging it into an unrelated line.
                if (unfolded.isNotEmpty()) {
                    unfolded[unfolded.lastIndex] = unfolded.last() + line.substring(1)
                }
            } else {
                unfolded += line
            }
        }
        return unfolded
    }

    private data class IcsProperty(
        val name: String,
        val params: Map<String, String>,
        val value: String,
    )

    /**
     * Splits `NAME;PARAM=VALUE:VALUE` into its three parts.
     *
     * The colon that ends the name is the first colon **outside a quoted parameter value**:
     * `ATTENDEE;CN="a:b":mailto:x` and `ALTREP="cid:part1.0001@example"` both contain a colon that
     * is not a separator. Splitting on the first colon unconditionally truncates the value.
     */
    private fun splitProperty(line: String): IcsProperty? {
        if (line.isBlank()) return null
        val nameEnd = line.indexOfFirst { it == ';' || it == ':' }
        if (nameEnd <= 0) return null
        var quoted = false
        var colon = -1
        for (index in nameEnd until line.length) {
            when (val character = line[index]) {
                '"' -> quoted = !quoted
                ':' -> if (!quoted) {
                    colon = index
                    break
                }
            }
        }
        if (colon < 0) return null
        return IcsProperty(
            name = line.substring(0, nameEnd).uppercase(Locale.ROOT),
            params = parseParameters(line.substring(nameEnd, colon)),
            value = line.substring(colon + 1),
        )
    }

    private fun parseParameters(text: String): Map<String, String> {
        if (text.isEmpty()) return emptyMap()
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        text.forEach { character ->
            when {
                character == '"' -> {
                    quoted = !quoted
                    current.append(character)
                }
                character == ';' && !quoted -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> current.append(character)
            }
        }
        parts += current.toString()
        return parts.mapNotNull { part ->
            val equals = part.indexOf('=')
            if (equals <= 0) return@mapNotNull null
            part.substring(0, equals).trim().uppercase(Locale.ROOT) to
                part.substring(equals + 1).trim().trim('"')
        }.toMap()
    }

    /**
     * RFC 5545 §3.3.11 TEXT escaping, decoded in **one pass**.
     *
     * One pass matters: `\\n` is an escaped backslash followed by a letter `n`, and a two-step
     * implementation (`replace("\\n", "\n")` then `replace("\\\\", "\\")`, or the other order)
     * turns it into a real newline — so a title that legitimately contains a backslash is mangled
     * into two lines.
     */
    internal fun unescape(value: String): String {
        if (!value.contains('\\')) return value
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '\\' && index + 1 < value.length) {
                when (val escaped = value[index + 1]) {
                    'n', 'N' -> out.append('\n')
                    '\\' -> out.append('\\')
                    ';' -> out.append(';')
                    ',' -> out.append(',')
                    // Unknown escapes keep the character itself: dropping the backslash is what
                    // the spec says to do with an unrecognised sequence.
                    else -> out.append(escaped)
                }
                index += 2
            } else {
                out.append(character)
                index++
            }
        }
        return out.toString()
    }

    // ------------------------------------------------------------------ event layer

    private fun Map<String, IcsProperty>.toEvent(zone: ZoneId): IcsEvent {
        val uid = this["UID"]?.value?.trim().orEmpty()
        val startProperty = this["DTSTART"]
        val timing = startProperty?.toTiming(this["DTEND"], this["DURATION"], zone)
            ?: IcsTiming.Unreadable("")
        return IcsEvent(
            uid = uid,
            summary = this["SUMMARY"]?.let { unescape(it.value).trim() }.orEmpty(),
            timing = timing,
            recurrence = this["RRULE"].toRecurrence(timing, zone),
            status = if (this["STATUS"]?.value.equals("CANCELLED", ignoreCase = true)) {
                IcsStatus.CANCELLED
            } else {
                IcsStatus.CONFIRMED
            },
            busy = !this["TRANSP"]?.value.equals("TRANSPARENT", ignoreCase = true),
            fromRepland = uid.startsWith(OWN_UID_PREFIX) && uid.endsWith(OWN_UID_SUFFIX),
        )
    }

    private fun IcsProperty.toTiming(
        endProperty: IcsProperty?,
        durationProperty: IcsProperty?,
        zone: ZoneId,
    ): IcsTiming {
        val raw = value.trim()
        // `VALUE=DATE` is the declared form of an all-day event, but producers omit it while still
        // writing eight digits, so the length is the authority and the parameter is only a hint.
        if (raw.length == 8) {
            val start = runCatching { LocalDate.parse(raw, DATE_ONLY) }.getOrNull()
                ?: return IcsTiming.Unreadable(raw)
            val declared = endProperty?.value?.trim()?.take(8)
            val end = declared?.let { runCatching { LocalDate.parse(it, DATE_ONLY) }.getOrNull() }
            val durationDays = durationProperty?.value?.let(::parseDuration)?.toDays()?.toInt()
            val exclusive = when {
                end != null && end.isAfter(start) -> end
                durationDays != null && durationDays > 0 -> start.plusDays(durationDays.toLong())
                else -> start.plusDays(1)
            }
            return IcsTiming.AllDay(start, exclusive)
        }
        val start = readMoment(raw, params["TZID"], zone) ?: return IcsTiming.Unreadable(raw)
        val end = endProperty?.value?.trim()?.let { readMoment(it, endProperty.params["TZID"], zone) }
        val duration = durationProperty?.value?.let(::parseDuration)
        return when {
            end != null && end.isAfter(start) -> IcsTiming.Timed(start, end)
            duration != null && !duration.isZero && !duration.isNegative -> IcsTiming.Timed(start, start.plus(duration))
            // A missing or non-positive DTEND is not a busy period, so it is reported rather than
            // invented as a zero-length block that no screen would ever show.
            else -> IcsTiming.Unreadable(raw)
        }
    }

    private fun readMoment(raw: String, tzid: String?, fallback: ZoneId): LocalDateTime? {
        val utc = raw.endsWith("Z") || raw.endsWith("z")
        val body = if (utc) raw.dropLast(1) else raw
        val local = runCatching { LocalDateTime.parse(body, DATE_TIME) }.getOrNull() ?: return null
        return when {
            // The conversion is applied before anything reads a date off the value, because an
            // instant late in the UTC day belongs to the *next* local day.
            utc -> local.atZone(ZoneOffset.UTC).withZoneSameInstant(fallback).toLocalDateTime()
            tzid == null -> local
            else -> resolveZone(tzid)?.let { zone ->
                local.atZone(zone).withZoneSameInstant(fallback).toLocalDateTime()
            } ?: local
        }
    }

    /** An unknown `TZID` falls back to the device zone rather than dropping the event; the time may be off, but it stays visible. */
    private fun resolveZone(tzid: String): ZoneId? {
        val candidates = listOf(tzid, tzid.trimStart('/'))
        return candidates.firstNotNullOfOrNull { candidate ->
            runCatching { ZoneId.of(candidate) }.getOrNull()
        }
    }

    private fun IcsProperty?.toRecurrence(timing: IcsTiming, zone: ZoneId): IcsRecurrence {
        if (this == null) return IcsRecurrence.None
        val rule = value.trim()
        val fields = rule.split(';').mapNotNull { part ->
            val equals = part.indexOf('=')
            if (equals <= 0) null else part.substring(0, equals).uppercase(Locale.ROOT) to part.substring(equals + 1)
        }.toMap()
        if (fields["FREQ"] != "WEEKLY") return IcsRecurrence.Unsupported(rule)
        // A multi-week interval can be written as the app's 单/双 parity only when a semester start
        // is known, which this reader does not have; guessing would block time for the wrong weeks.
        if ((fields["INTERVAL"]?.toIntOrNull() ?: 1) != 1) return IcsRecurrence.Unsupported(rule)
        if (fields.containsKey("COUNT")) return IcsRecurrence.Unsupported(rule)
        val days = fields["BYDAY"]
            ?.split(',')
            ?.mapNotNull { it.trim().takeLast(2).toDayOfWeekOrNull() }
            ?.toSet()
            .orEmpty()
        val until = fields["UNTIL"]
        val lastDate = when {
            until == null -> null
            else -> readUntil(until, zone) ?: return IcsRecurrence.Unsupported(rule)
        }
        val startDay = (timing as? IcsTiming.Timed)?.start?.dayOfWeek
            ?: (timing as? IcsTiming.AllDay)?.start?.dayOfWeek
        return IcsRecurrence.Weekly(days.ifEmpty { setOfNotNull(startDay) }, lastDate)
    }

    private fun readUntil(raw: String, zone: ZoneId): LocalDate? {
        val value = raw.trim()
        if (value.length == 8) {
            return runCatching { LocalDate.parse(value, DATE_ONLY) }.getOrNull()
        }
        val moment = readMoment(value, tzid = null, fallback = zone) ?: return null
        return moment.toLocalDate()
    }

    private fun String.toDayOfWeekOrNull(): DayOfWeek? = when (uppercase(Locale.ROOT)) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> null
    }

    /**
     * `DURATION`, in the subset that carries a length: `P[nW][nD][T[nH][nM][nS]]` (RFC 5545 §3.3.6
     * defines the week form, so `P1W` is seven days).
     *
     * A negative duration does not match at all and is rejected rather than approximated.
     */
    internal fun parseDuration(value: String): Duration? {
        val match = DURATION.matchEntire(value.trim().uppercase(Locale.ROOT)) ?: return null
        // Groups are 1=W, 2=D, 3=H, 4=M, 5=S: the literal `T` is non-capturing, so the time
        // fields start at 3 and reading them from 4 would both shift every value and run off the
        // end of the group list.
        val weeks = match.groupValues[1].toLongOrNull()
        val days = match.groupValues[2].toLongOrNull()
        val hours = match.groupValues[3].toLongOrNull()
        val minutes = match.groupValues[4].toLongOrNull()
        val seconds = match.groupValues[5].toLongOrNull()
        if (listOf(weeks, days, hours, minutes, seconds).all { it == null }) return null
        // A week is seven days. Reading the week field as a day count is a silent seven-fold
        // understatement: `P1W` would become a one-day duration and every event using it would
        // shrink without anything reporting a problem.
        return Duration.ofDays((weeks ?: 0) * 7)
            .plusDays(days ?: 0)
            .plusHours(hours ?: 0)
            .plusMinutes(minutes ?: 0)
            .plusSeconds(seconds ?: 0)
    }

    private val DURATION = Regex("^P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")

    /** Also used by [IcsImporter.parse]'s callers to name the format in a failure message. */
    const val CALENDAR_HEADER: String = "BEGIN:VCALENDAR"

    /** True when [text] looks like an iCalendar document at all, so "wrong file" is one check. */
    fun looksLikeCalendar(text: String): Boolean = text.contains(CALENDAR_HEADER, ignoreCase = true)
}
