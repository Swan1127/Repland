package com.swan1127.repland.data.importer

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reader's own traps, one test per mistake it exists to prevent.
 *
 * The timezone is pinned to `Asia/Shanghai` (+08:00, no daylight saving) so every expected value in
 * this file is a fact rather than a property of the machine the suite happens to run on — which is
 * the whole point, because the bug class here is "the same file reads differently in another zone".
 */
class IcsImporterTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    // ------------------------------------------------------------------ fixtures

    /** A document with CRLF line endings, which is what the spec requires and what most tools write. */
    private fun calendar(vararg body: String): String =
        (listOf("BEGIN:VCALENDAR", "VERSION:2.0") + body + "END:VCALENDAR")
            .joinToString("\r\n") + "\r\n"

    private fun event(vararg lines: String): String =
        (listOf("BEGIN:VEVENT") + lines + "END:VEVENT").joinToString("\r\n")

    /**
     * [extra] is inserted **inside** the event, before `END:VEVENT`.
     *
     * Appending to the returned string instead would put a line such as `RRULE:` after `END:VEVENT`,
     * where the reader correctly ignores it — a fixture that makes every recurrence test pass by
     * never being read at all.
     */
    private fun timed(
        start: String = "20260919T080000",
        end: String = "20260919T090000",
        summary: String = "写实验报告",
        uid: String = "evt-1",
        extra: List<String> = emptyList(),
    ): String = event(
        *(
            listOf("UID:$uid", "DTSTART:$start", "DTEND:$end", "SUMMARY:$summary") + extra
            ).toTypedArray(),
    )

    private fun parse(text: String): List<IcsEvent> = IcsImporter.parse(text, zone)

    private fun only(text: String): IcsEvent = parse(text).single()

    private fun timingOf(text: String): IcsTiming = only(text).timing

    private fun recurrenceOf(vararg extra: String): IcsRecurrence =
        only(calendar(timed(extra = extra.toList()))).recurrence

    private fun at(hour: Int, minute: Int = 0): LocalDateTime =
        LocalDate.of(2026, 9, 19).atStartOfDay().plusHours(hour.toLong()).plusMinutes(minute.toLong())

    // ------------------------------------------------------------------ line layer

    @Test
    fun `a folded summary is joined back into one line`() {
        // A 75-octet line is folded by inserting CRLF plus one space. Not unfolding truncates every
        // SUMMARY long enough to be folded, which is most real ones.
        val folded = event(
            "UID:evt-1",
            "DTSTART:20260919T080000",
            "DTEND:20260919T090000",
            "SUMMARY:一章很长的标题被折成了两行",
            " 第二行接着写",
        )

        assertEquals("一章很长的标题被折成了两行第二行接着写", only(calendar(folded)).summary)
    }

    @Test
    fun `unfolding removes exactly one space, not all leading whitespace`() {
        val folded = event(
            "UID:evt-1",
            "DTSTART:20260919T080000",
            "DTEND:20260919T090000",
            "SUMMARY:前面",
            "  后面还有两个空格",
        )

        // The fold marker is one space; the second is part of the value.
        assertEquals("前面 后面还有两个空格", only(calendar(folded)).summary)
    }

    @Test
    fun `a continuation line with nothing to continue is dropped rather than merged`() {
        val text = " leading junk with no previous line\r\n" + calendar(timed())

        assertEquals(1, parse(text).size)
    }

    @Test
    fun `lf only line endings are accepted`() {
        val text = calendar(timed()).replace("\r\n", "\n")

        assertEquals("写实验报告", only(text).summary)
    }

    @Test
    fun `a property outside any event is ignored`() {
        val text = calendar("SUMMARY:orphan", timed())

        assertEquals(1, parse(text).size)
        assertEquals("写实验报告", only(text).summary)
    }

    @Test
    fun `two events in one file are both read`() {
        val text = calendar(timed(uid = "a"), timed(uid = "b"))

        assertEquals(listOf("a", "b"), parse(text).map(IcsEvent::uid))
    }

    // ------------------------------------------------------------------ escapes

    @Test
    fun `escaped comma semicolon and backslash are decoded`() {
        val text = calendar(timed(summary = """a\, b\; c\\ d"""))

        assertEquals("""a, b; c\ d""", only(text).summary)
    }

    @Test
    fun `an escaped backslash followed by n is not a newline`() {
        // `\\n` is an escaped backslash and then a letter n. A two-step unescape — replacing \n
        // first, or replacing \\ first — turns it into a real line break, so a title that contains
        // a backslash comes back split in two.
        val text = calendar(timed(summary = """C:\\new"""))

        assertEquals("""C:\new""", only(text).summary)
    }

    @Test
    fun `an escaped n is a newline`() {
        val text = calendar(timed(summary = """第一行\n第二行"""))

        assertEquals("第一行\n第二行", only(text).summary)
    }

    @Test
    fun `an unknown escape keeps the character and drops the backslash`() {
        val text = calendar(timed(summary = """100\%"""))

        assertEquals("100%", only(text).summary)
    }

    // ------------------------------------------------------------------ property splitting

    @Test
    fun `a colon inside a value does not truncate it`() {
        val text = calendar(timed(summary = "Meeting at 10:30"))

        assertEquals("Meeting at 10:30", only(text).summary)
    }

    @Test
    fun `a colon inside a quoted parameter does not end the name`() {
        // A naive "split on the first colon" reads the value as `b";TZID=Asia/Shanghai:2026...`,
        // which is unparseable — so this asserts the timing, not just that nothing crashed.
        val text = calendar(
            event(
                "UID:evt-1",
                "DTSTART;X-NOTE=\"a:b\";TZID=Asia/Shanghai:20260919T080000",
                "DTEND:20260919T090000",
                "SUMMARY:ok",
            ),
        )

        assertEquals(IcsTiming.Timed(at(8), at(9)), timingOf(text))
    }

    @Test
    fun `parameters are read case insensitively and without their quotes`() {
        val text = calendar(
            event(
                "UID:evt-1",
                "DTSTART;tzid=\"Asia/Shanghai\";value=date-time:20260919T080000",
                "DTEND:20260919T090000",
                "SUMMARY:ok",
            ),
        )

        assertEquals(IcsTiming.Timed(at(8), at(9)), timingOf(text))
    }

    @Test
    fun `a repeated property keeps the first value`() {
        val text = calendar(
            event(
                "UID:evt-1",
                "DTSTART:20260919T080000",
                "DTEND:20260919T090000",
                "SUMMARY:第一个",
                "SUMMARY:第二个",
            ),
        )

        assertEquals("第一个", only(text).summary)
    }

    @Test
    fun `an alarm's summary does not overwrite the event's own`() {
        // VALARM has its own SUMMARY and DESCRIPTION and is read *after* the event's, so
        // last-write-wins would title every meeting after its reminder.
        val text = calendar(
            event(
                "UID:evt-1",
                "DTSTART:20260919T080000",
                "DTEND:20260919T090000",
                "SUMMARY:真正的标题",
                "BEGIN:VALARM",
                "ACTION:DISPLAY",
                "TRIGGER:-PT15M",
                "SUMMARY:闹钟文字",
                "DESCRIPTION:闹钟说明",
                "END:VALARM",
            ),
        )

        assertEquals("真正的标题", only(text).summary)
    }

    @Test
    fun `a nested component's summary is ignored even when the event has none`() {
        // The test above is satisfied by *two* independent guards — the nesting depth and
        // first-value-wins — so changing either one alone leaves it green, which makes it a weaker
        // witness than it reads. This fixture takes the second guard out of the picture: the event
        // has no SUMMARY of its own, so only the depth check can keep the alarm's text out.
        val text = calendar(
            event(
                "UID:evt-1",
                "DTSTART:20260919T080000",
                "DTEND:20260919T090000",
                "BEGIN:VALARM",
                "ACTION:DISPLAY",
                "SUMMARY:闹钟文字",
                "END:VALARM",
            ),
        )

        assertEquals("", only(text).summary)
    }

    // ------------------------------------------------------------------ time

    @Test
    fun `a floating time is read as wall time, unchanged`() {
        assertEquals(IcsTiming.Timed(at(8), at(9)), timingOf(calendar(timed())))
    }

    @Test
    fun `a utc time is converted to the given zone`() {
        val text = calendar(timed(start = "20260919T000000Z", end = "20260919T010000Z"))

        assertEquals(IcsTiming.Timed(at(8), at(9)), timingOf(text))
    }

    @Test
    fun `a utc instant late in the day lands on the next local date`() {
        // 23:30Z on the 18th is 07:30 on the 19th in +08:00. Reading the date before converting
        // puts the event on the wrong day — the classic off-by-one-day calendar bug.
        val text = calendar(timed(start = "20260918T233000Z", end = "20260919T003000Z"))

        val timing = timingOf(text) as IcsTiming.Timed
        assertEquals(LocalDate.of(2026, 9, 19), timing.start.toLocalDate())
        assertEquals(at(7, 30), timing.start)
    }

    @Test
    fun `a tzid time is converted from that zone`() {
        // 08:00 in New York on 19 September 2026 is EDT (UTC-4), so 20:00 in +08:00.
        val text = calendar(
            event(
                "UID:evt-1",
                "DTSTART;TZID=America/New_York:20260919T080000",
                "DTEND;TZID=America/New_York:20260919T090000",
                "SUMMARY:ok",
            ),
        )

        assertEquals(IcsTiming.Timed(at(20), at(21)), timingOf(text))
    }

    @Test
    fun `a tzid with a leading slash is resolved`() {
        // Outlook writes `/freeassociation.sourceforge.net/Europe/Berlin` style ids.
        val text = calendar(
            event(
                "UID:evt-1",
                "DTSTART;TZID=/Asia/Shanghai:20260919T080000",
                "DTEND;TZID=/Asia/Shanghai:20260919T090000",
                "SUMMARY:ok",
            ),
        )

        assertEquals(IcsTiming.Timed(at(8), at(9)), timingOf(text))
    }

    @Test
    fun `an unknown tzid falls back to the device zone instead of dropping the event`() {
        val text = calendar(
            event(
                "UID:evt-1",
                "DTSTART;TZID=Mars/Olympus:20260919T080000",
                "DTEND;TZID=Mars/Olympus:20260919T090000",
                "SUMMARY:ok",
            ),
        )

        // Kept as wall time: the clock may be off by an unknown offset, but a visible event that
        // might be an hour out beats an invisible one.
        assertEquals(IcsTiming.Timed(at(8), at(9)), timingOf(text))
    }

    @Test
    fun `an eight digit start is an all day event even without the value parameter`() {
        val text = calendar(
            event("UID:evt-1", "DTSTART:20260919", "DTEND:20260920", "SUMMARY:假期"),
        )

        assertEquals(
            IcsTiming.AllDay(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 20)),
            timingOf(text),
        )
    }

    @Test
    fun `an all day end is exclusive so a one day event covers one day`() {
        val text = calendar(
            event(
                "UID:evt-1",
                "DTSTART;VALUE=DATE:20260919",
                "DTEND;VALUE=DATE:20260920",
                "SUMMARY:假期",
            ),
        )

        val timing = timingOf(text) as IcsTiming.AllDay
        assertEquals(LocalDate.of(2026, 9, 19), timing.start)
        // Treating the end as inclusive would make this event cover the 20th as well.
        assertEquals(LocalDate.of(2026, 9, 20), timing.endExclusive)
    }

    @Test
    fun `an all day event with no end covers exactly one day`() {
        val text = calendar(event("UID:evt-1", "DTSTART;VALUE=DATE:20260919", "SUMMARY:假期"))

        assertEquals(
            IcsTiming.AllDay(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 20)),
            timingOf(text),
        )
    }

    @Test
    fun `an all day end equal to the start still covers one day`() {
        val text = calendar(
            event(
                "UID:evt-1",
                "DTSTART;VALUE=DATE:20260919",
                "DTEND;VALUE=DATE:20260919",
                "SUMMARY:假期",
            ),
        )

        assertEquals(
            IcsTiming.AllDay(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 20)),
            timingOf(text),
        )
    }

    @Test
    fun `a timed event with no end at all is unreadable rather than zero length`() {
        val text = calendar(event("UID:evt-1", "DTSTART:20260919T080000", "SUMMARY:ok"))

        val timing = timingOf(text)
        assertTrue(timing is IcsTiming.Unreadable)
        assertEquals("20260919T080000", (timing as IcsTiming.Unreadable).raw)
    }

    @Test
    fun `a missing end falls back to duration`() {
        val text = calendar(
            event("UID:evt-1", "DTSTART:20260919T080000", "DURATION:PT1H30M", "SUMMARY:ok"),
        )

        assertEquals(IcsTiming.Timed(at(8), at(9, 30)), timingOf(text))
    }

    @Test
    fun `an unparseable start is unreadable and keeps the raw value`() {
        val text = calendar(event("UID:evt-1", "DTSTART:next tuesday", "SUMMARY:ok"))

        assertEquals(IcsTiming.Unreadable("next tuesday"), timingOf(text))
    }

    @Test
    fun `a missing start is unreadable`() {
        val text = calendar(event("UID:evt-1", "SUMMARY:ok"))

        assertTrue(timingOf(text) is IcsTiming.Unreadable)
    }

    @Test
    fun `duration parses days hours minutes and weeks`() {
        assertEquals(Duration.ofMinutes(90), IcsImporter.parseDuration("PT1H30M"))
        assertEquals(Duration.ofDays(2), IcsImporter.parseDuration("P2D"))
        assertEquals(Duration.ofDays(7), IcsImporter.parseDuration("P1W"))
        assertEquals(Duration.ofSeconds(45), IcsImporter.parseDuration("PT45S"))
        assertEquals(
            Duration.ofDays(1).plusHours(2).plusMinutes(3),
            IcsImporter.parseDuration("P1DT2H3M"),
        )
    }

    @Test
    fun `a negative or malformed duration is rejected`() {
        assertNull(IcsImporter.parseDuration("-PT1H"))
        assertNull(IcsImporter.parseDuration("P"))
        assertNull(IcsImporter.parseDuration("1H"))
        assertNull(IcsImporter.parseDuration(""))
    }

    // ------------------------------------------------------------------ recurrence

    @Test
    fun `a weekly rule with byday yields those weekdays`() {
        assertEquals(
            IcsRecurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), null),
            recurrenceOf("RRULE:FREQ=WEEKLY;BYDAY=MO,WE"),
        )
    }

    @Test
    fun `a weekly rule with no byday uses the start date's weekday`() {
        // 2026-09-19 is a Saturday.
        assertEquals(
            IcsRecurrence.Weekly(setOf(DayOfWeek.SATURDAY), null),
            recurrenceOf("RRULE:FREQ=WEEKLY"),
        )
    }

    @Test
    fun `a positional byday such as 2MO is read as monday`() {
        assertEquals(
            IcsRecurrence.Weekly(setOf(DayOfWeek.MONDAY), null),
            recurrenceOf("RRULE:FREQ=WEEKLY;BYDAY=2MO"),
        )
    }

    @Test
    fun `until becomes the series end date in the given zone`() {
        // 23:59:59Z on 20 December is 07:59:59 on the 21st in +08:00, so the last local date the
        // series covers is the 21st, not the 20th.
        assertEquals(
            IcsRecurrence.Weekly(setOf(DayOfWeek.SATURDAY), LocalDate.of(2026, 12, 21)),
            recurrenceOf("RRULE:FREQ=WEEKLY;UNTIL=20261220T235959Z"),
        )
    }

    @Test
    fun `a date valued until is read as that date`() {
        assertEquals(
            IcsRecurrence.Weekly(setOf(DayOfWeek.SATURDAY), LocalDate.of(2026, 12, 20)),
            recurrenceOf("RRULE:FREQ=WEEKLY;UNTIL=20261220"),
        )
    }

    @Test
    fun `a two week interval is unsupported rather than guessed at`() {
        // The app can express 单/双 parity, but only against a semester start this reader does not
        // have; guessing the parity would block time on the wrong weeks.
        assertEquals(
            IcsRecurrence.Unsupported("FREQ=WEEKLY;INTERVAL=2"),
            recurrenceOf("RRULE:FREQ=WEEKLY;INTERVAL=2"),
        )
    }

    @Test
    fun `a count limited series is unsupported`() {
        assertTrue(recurrenceOf("RRULE:FREQ=WEEKLY;COUNT=16") is IcsRecurrence.Unsupported)
    }

    @Test
    fun `a daily rule is unsupported`() {
        assertTrue(recurrenceOf("RRULE:FREQ=DAILY") is IcsRecurrence.Unsupported)
    }

    @Test
    fun `an unparseable until is unsupported rather than treated as endless`() {
        // Treating it as endless would block time for months after the series really stopped.
        assertTrue(recurrenceOf("RRULE:FREQ=WEEKLY;UNTIL=whenever") is IcsRecurrence.Unsupported)
    }

    @Test
    fun `an event with no rule has no recurrence`() {
        assertEquals(IcsRecurrence.None, only(calendar(timed())).recurrence)
    }

    // ------------------------------------------------------------------ status and provenance

    @Test
    fun `a cancelled event is reported as cancelled`() {
        assertEquals(IcsStatus.CANCELLED, only(calendar(timed(extra = listOf("STATUS:CANCELLED")))).status)
    }

    @Test
    fun `a lowercase status is still read`() {
        assertEquals(IcsStatus.CANCELLED, only(calendar(timed(extra = listOf("status:cancelled")))).status)
    }

    @Test
    fun `a transparent event is not busy`() {
        assertFalse(only(calendar(timed(extra = listOf("TRANSP:TRANSPARENT")))).busy)
    }

    @Test
    fun `an ordinary event is busy and confirmed`() {
        val event = only(calendar(timed()))

        assertTrue(event.busy)
        assertEquals(IcsStatus.CONFIRMED, event.status)
    }

    @Test
    fun `an event written by this app's exporter is recognised`() {
        assertTrue(only(calendar(timed(uid = "repland-seg-1@local"))).fromRepland)
    }

    @Test
    fun `a foreign uid that merely starts with the prefix is not recognised`() {
        // The prefix alone is not enough: requiring the suffix too is what keeps a real event from
        // an outside calendar whose id happens to begin the same way from being skipped.
        assertFalse(only(calendar(timed(uid = "repland-meeting-2026"))).fromRepland)
    }

    @Test
    fun `an event with no uid is still read`() {
        val text = calendar(
            event("DTSTART:20260919T080000", "DTEND:20260919T090000", "SUMMARY:ok"),
        )

        assertEquals("", only(text).uid)
    }

    // ------------------------------------------------------------------ shape checks

    @Test
    fun `a summary is trimmed`() {
        assertEquals("写实验报告", only(calendar(timed(summary = "  写实验报告  "))).summary)
    }

    @Test
    fun `a calendar with no events yields nothing`() {
        assertEquals(emptyList<IcsEvent>(), parse(calendar()))
    }

    @Test
    fun `empty text yields nothing`() {
        assertEquals(emptyList<IcsEvent>(), parse(""))
    }

    @Test
    fun `a document that is not a calendar is rejected by the header check`() {
        assertFalse(IcsImporter.looksLikeCalendar("{\"json\":true}"))
        assertTrue(IcsImporter.looksLikeCalendar(calendar(timed())))
    }

    @Test
    fun `parsing is deterministic across two runs`() {
        val text = calendar(timed(uid = "a"), timed(uid = "b"))

        assertEquals(parse(text), parse(text))
    }
}
