package com.swan1127.repland.domain.model

import com.swan1127.repland.data.importer.IcsEvent
import com.swan1127.repland.data.importer.IcsImporter
import com.swan1127.repland.data.importer.IcsRecurrence
import com.swan1127.repland.data.importer.IcsStatus
import com.swan1127.repland.data.importer.IcsTiming
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapping from calendar events onto the app's model of occupied time.
 *
 * Two tests in here are not about any single case and would catch a whole class of mistakes:
 * `every produced row passes the app's own validator` (the mapper can never emit a row the writer
 * will reject) and the counter tests, which exist because a breakdown that does not add up is worse
 * than no breakdown — the user reads it as fact.
 */
class CalendarImportTest {

    private val fallbackTitle = "（无标题安排）"

    private fun date(day: Int): LocalDate = LocalDate.of(2026, 9, day)

    /** Minutes on 2026-09-19. Written as a helper so `10 * 60 + 30` cannot be mis-typed as `10 * 30`. */
    private fun at(hour: Int, minute: Int = 0): LocalDateTime =
        date(19).atStartOfDay().plusHours(hour.toLong()).plusMinutes(minute.toLong())

    private fun timedEvent(
        summary: String = "例会",
        start: LocalDateTime = at(10),
        end: LocalDateTime = at(11),
        uid: String = "e1",
        recurrence: IcsRecurrence = IcsRecurrence.None,
        status: IcsStatus = IcsStatus.CONFIRMED,
        busy: Boolean = true,
        fromRepland: Boolean = false,
    ): IcsEvent = IcsEvent(
        uid = uid,
        summary = summary,
        timing = IcsTiming.Timed(start, end),
        recurrence = recurrence,
        status = status,
        busy = busy,
        fromRepland = fromRepland,
    )

    private fun allDayEvent(
        summary: String = "假期",
        start: LocalDate = date(19),
        endExclusive: LocalDate = date(20),
        uid: String = "e1",
        recurrence: IcsRecurrence = IcsRecurrence.None,
        fromRepland: Boolean = false,
    ): IcsEvent = IcsEvent(
        uid = uid,
        summary = summary,
        timing = IcsTiming.AllDay(start, endExclusive),
        recurrence = recurrence,
        fromRepland = fromRepland,
    )

    private fun plan(
        events: List<IcsEvent>,
        existingBlocks: List<WeeklyTimeBlock> = emptyList(),
        existingOverrides: List<DateOverride> = emptyList(),
        semesterFirstWeekMonday: LocalDate? = null,
    ): CalendarImportOutcome = CalendarImport.plan(
        events = events,
        existingWeeklyBlocks = existingBlocks,
        existingDateOverrides = existingOverrides,
        fallbackTitle = fallbackTitle,
        semesterFirstWeekMonday = semesterFirstWeekMonday,
    )

    private fun imported(outcome: CalendarImportOutcome): CalendarImportOutcome.Imported {
        assertTrue("expected an Imported outcome but was $outcome", outcome is CalendarImportOutcome.Imported)
        return outcome as CalendarImportOutcome.Imported
    }

    private fun weeklyBlock(
        title: String = "高数",
        dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        startMinute: Int = 8 * 60,
        endMinute: Int = 9 * 60,
        weekPattern: String? = null,
    ): WeeklyTimeBlock = WeeklyTimeBlock(
        id = "existing-1",
        title = title,
        kind = TimeBlockKind.COURSE,
        dayOfWeek = dayOfWeek,
        startMinute = startMinute,
        endMinute = endMinute,
        weekPattern = weekPattern,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )

    private fun override(
        title: String = "例会",
        type: DateOverrideType = DateOverrideType.BLOCKED,
        date: LocalDate = date(19),
        startMinute: Int = 10 * 60,
        endMinute: Int = 11 * 60,
    ): DateOverride = DateOverride(
        id = "existing-1",
        title = title,
        type = type,
        date = date,
        startMinute = startMinute,
        endMinute = endMinute,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )

    // ------------------------------------------------------------------ one-off events

    @Test
    fun `a one off timed event becomes a blocked one day override`() {
        val outcome = imported(plan(listOf(timedEvent(summary = "看牙医"))))

        assertEquals(
            listOf(
                DateOverrideDraft(
                    title = "看牙医",
                    type = DateOverrideType.BLOCKED,
                    date = date(19),
                    startMinute = 10 * 60,
                    endMinute = 11 * 60,
                ),
            ),
            outcome.dateOverrides,
        )
        assertTrue(outcome.weeklyBlocks.isEmpty())
    }

    @Test
    fun `an all day event becomes a whole day override`() {
        val outcome = imported(plan(listOf(allDayEvent())))

        assertEquals(
            listOf(
                DateOverrideDraft(
                    title = "假期",
                    type = DateOverrideType.BLOCKED,
                    date = date(19),
                    startMinute = 0,
                    endMinute = 24 * 60,
                ),
            ),
            outcome.dateOverrides,
        )
    }

    @Test
    fun `a multi day all day event becomes one override per day`() {
        val outcome = imported(
            plan(listOf(allDayEvent(start = date(19), endExclusive = date(22)))),
        )

        // The end is exclusive, so the 19th, 20th and 21st — three days, not four.
        assertEquals(listOf(date(19), date(20), date(21)), outcome.dateOverrides.map(DateOverrideDraft::date))
    }

    @Test
    fun `an all day event longer than the cap is skipped as out of range`() {
        val outcome = imported(
            plan(listOf(allDayEvent(start = date(1), endExclusive = date(1).plusDays(400)))),
        )

        assertTrue(outcome.dateOverrides.isEmpty())
        assertEquals(listOf(CalendarSkipReason.OUT_OF_RANGE), outcome.skipped.map(CalendarSkip::reason))
    }

    @Test
    fun `an event that crosses midnight becomes two overrides`() {
        // A night shift is a real commitment; dropping it or clamping it to 24:00 would both be
        // false, so it is split at the day boundary.
        val outcome = imported(
            plan(listOf(timedEvent(summary = "夜班", start = at(23), end = date(20).atStartOfDay().plusHours(1)))),
        )

        assertEquals(
            listOf(
                DateOverrideDraft(
                    title = "夜班",
                    type = DateOverrideType.BLOCKED,
                    date = date(19),
                    startMinute = 23 * 60,
                    endMinute = 24 * 60,
                ),
                DateOverrideDraft(
                    title = "夜班",
                    type = DateOverrideType.BLOCKED,
                    date = date(20),
                    startMinute = 0,
                    endMinute = 60,
                ),
            ),
            outcome.dateOverrides,
        )
    }

    @Test
    fun `an event that ends before it starts produces nothing and is skipped`() {
        val outcome = imported(
            plan(listOf(timedEvent(start = at(11), end = at(10)))),
        )

        assertTrue(outcome.dateOverrides.isEmpty())
        assertEquals(listOf(CalendarSkipReason.OUT_OF_RANGE), outcome.skipped.map(CalendarSkip::reason))
    }

    // ------------------------------------------------------------------ recurring events

    @Test
    fun `a weekly event becomes a weekly block on its weekday`() {
        val outcome = imported(
            plan(
                listOf(
                    timedEvent(
                        summary = "组会",
                        recurrence = IcsRecurrence.Weekly(setOf(DayOfWeek.MONDAY), null),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                WeeklyTimeBlockDraft(
                    title = "组会",
                    kind = TimeBlockKind.OTHER,
                    dayOfWeek = DayOfWeek.MONDAY,
                    startMinute = 10 * 60,
                    endMinute = 11 * 60,
                    weekPattern = null,
                ),
            ),
            outcome.weeklyBlocks,
        )
        assertTrue(outcome.dateOverrides.isEmpty())
    }

    @Test
    fun `a weekly event on several weekdays becomes one block per day`() {
        val outcome = imported(
            plan(
                listOf(
                    timedEvent(recurrence = IcsRecurrence.Weekly(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY), null)),
                ),
            ),
        )

        // Sorted, so the output does not depend on the set's iteration order.
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            outcome.weeklyBlocks.map(WeeklyTimeBlockDraft::dayOfWeek),
        )
    }

    @Test
    fun `a weekly all day event becomes a whole day block per weekday`() {
        val outcome = imported(
            plan(
                listOf(
                    allDayEvent(
                        summary = "休息日",
                        recurrence = IcsRecurrence.Weekly(setOf(DayOfWeek.SUNDAY), null),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                WeeklyTimeBlockDraft(
                    title = "休息日",
                    kind = TimeBlockKind.OTHER,
                    dayOfWeek = DayOfWeek.SUNDAY,
                    startMinute = 0,
                    endMinute = 24 * 60,
                    weekPattern = null,
                ),
            ),
            outcome.weeklyBlocks,
        )
    }

    @Test
    fun `a weekly event keeps its clock time and takes its day from the rule`() {
        // The start date is a Saturday, but the rule says Monday: the weekday must come from the
        // rule, not from the day the series happens to begin.
        val outcome = imported(
            plan(
                listOf(
                    timedEvent(
                        start = at(10),
                        end = at(11),
                        recurrence = IcsRecurrence.Weekly(setOf(DayOfWeek.MONDAY), null),
                    ),
                ),
            ),
        )

        assertEquals(DayOfWeek.MONDAY, outcome.weeklyBlocks.single().dayOfWeek)
        assertEquals(10 * 60, outcome.weeklyBlocks.single().startMinute)
    }

    @Test
    fun `an until limited series becomes a week pattern from the semester start`() {
        // 2026-09-21 is a Monday, and the series ends on 2026-12-21, which is 14 weeks later.
        val outcome = imported(
            plan(
                events = listOf(
                    timedEvent(recurrence = IcsRecurrence.Weekly(setOf(DayOfWeek.MONDAY), date(21).plusMonths(3))),
                ),
                semesterFirstWeekMonday = date(21),
            ),
        )

        assertEquals("1-14周", outcome.weeklyBlocks.single().weekPattern)
        assertEquals(0, outcome.approximatedSeries)
    }

    @Test
    fun `an until limited series with no semester start is imported always on and counted`() {
        val outcome = imported(
            plan(
                listOf(
                    timedEvent(recurrence = IcsRecurrence.Weekly(setOf(DayOfWeek.MONDAY), date(21).plusMonths(3))),
                ),
            ),
        )

        assertEquals(1, outcome.weeklyBlocks.size)
        assertEquals(null, outcome.weeklyBlocks.single().weekPattern)
        // Counted, not skipped: the block *was* imported, but it will outlive the series.
        assertEquals(1, outcome.approximatedSeries)
        assertTrue(outcome.skipped.isEmpty())
    }

    @Test
    fun `an open ended series is not counted as approximated`() {
        val outcome = imported(
            plan(listOf(timedEvent(recurrence = IcsRecurrence.Weekly(setOf(DayOfWeek.MONDAY), null)))),
        )

        assertEquals(1, outcome.weeklyBlocks.size)
        assertEquals(0, outcome.approximatedSeries)
    }

    @Test
    fun `a series ending before the semester start is counted as approximated`() {
        // The week number would be zero or negative, which the app's notation cannot express, so the
        // block is imported always-on rather than being dropped.
        val outcome = imported(
            plan(
                events = listOf(
                    timedEvent(recurrence = IcsRecurrence.Weekly(setOf(DayOfWeek.MONDAY), date(1))),
                ),
                semesterFirstWeekMonday = date(21),
            ),
        )

        assertEquals(1, outcome.weeklyBlocks.size)
        assertEquals(null, outcome.weeklyBlocks.single().weekPattern)
        assertEquals(1, outcome.approximatedSeries)
    }

    @Test
    fun `an imported weekly block is kind other rather than course`() {
        // A calendar does not say an entry is a lecture, and claiming it is one would put a label on
        // the timeline the user never chose.
        val outcome = imported(
            plan(listOf(timedEvent(recurrence = IcsRecurrence.Weekly(setOf(DayOfWeek.MONDAY), null)))),
        )

        assertEquals(TimeBlockKind.OTHER, outcome.weeklyBlocks.single().kind)
    }

    // ------------------------------------------------------------------ rejections

    @Test
    fun `an event from this app's own export is skipped`() {
        // Export then import must not turn the user's own plan into outside commitments.
        val outcome = imported(plan(listOf(timedEvent(fromRepland = true))))

        assertTrue(outcome.weeklyBlocks.isEmpty())
        assertTrue(outcome.dateOverrides.isEmpty())
        assertEquals(listOf(CalendarSkipReason.OWN_EXPORT), outcome.skipped.map(CalendarSkip::reason))
    }

    @Test
    fun `a cancelled event is skipped`() {
        val outcome = imported(plan(listOf(timedEvent(status = IcsStatus.CANCELLED))))

        assertEquals(listOf(CalendarSkipReason.CANCELLED), outcome.skipped.map(CalendarSkip::reason))
    }

    @Test
    fun `a transparent event is skipped`() {
        val outcome = imported(plan(listOf(timedEvent(busy = false))))

        assertEquals(listOf(CalendarSkipReason.FREE_TIME), outcome.skipped.map(CalendarSkip::reason))
    }

    @Test
    fun `an unreadable time is skipped`() {
        val event = IcsEvent("e1", "坏数据", IcsTiming.Unreadable("next tuesday"))
        val outcome = imported(plan(listOf(event)))

        assertEquals(listOf(CalendarSkipReason.UNREADABLE_TIME), outcome.skipped.map(CalendarSkip::reason))
    }

    @Test
    fun `an unsupported recurrence is skipped rather than imported as a one off`() {
        // Importing a biweekly seminar as a single occurrence would lose every other week.
        val outcome = imported(
            plan(listOf(timedEvent(recurrence = IcsRecurrence.Unsupported("FREQ=WEEKLY;INTERVAL=2")))),
        )

        assertTrue(outcome.weeklyBlocks.isEmpty())
        assertTrue(outcome.dateOverrides.isEmpty())
        assertEquals(
            listOf(CalendarSkipReason.UNSUPPORTED_RECURRENCE),
            outcome.skipped.map(CalendarSkip::reason),
        )
    }

    @Test
    fun `a cancelled event is not reported as an own export even when both apply`() {
        // The reasons are checked in a fixed order, so one event produces exactly one reason.
        val outcome = imported(
            plan(listOf(timedEvent(fromRepland = true, status = IcsStatus.CANCELLED))),
        )

        assertEquals(1, outcome.skipped.size)
        assertEquals(CalendarSkipReason.OWN_EXPORT, outcome.skipped.single().reason)
    }

    // ------------------------------------------------------------------ titles and duplicates

    @Test
    fun `an event with no summary gets the fallback title`() {
        val outcome = imported(plan(listOf(timedEvent(summary = ""))))

        assertEquals(fallbackTitle, outcome.dateOverrides.single().title)
    }

    @Test
    fun `a skip carries the title so the user can be told which entry`() {
        val outcome = imported(plan(listOf(timedEvent(summary = "组会", status = IcsStatus.CANCELLED))))

        assertEquals("组会", outcome.skipped.single().summary)
    }

    @Test
    fun `a skip with no summary carries the fallback title`() {
        val outcome = imported(plan(listOf(timedEvent(summary = "", status = IcsStatus.CANCELLED))))

        assertEquals(fallbackTitle, outcome.skipped.single().summary)
    }

    @Test
    fun `an event already in the timetable is a duplicate not a new row`() {
        val outcome = imported(
            plan(
                events = listOf(timedEvent(summary = "例会")),
                existingOverrides = listOf(override(title = "例会")),
            ),
        )

        assertTrue(outcome.dateOverrides.isEmpty())
        assertEquals(1, outcome.duplicateCount)
        assertEquals(0, outcome.ignoredCount)
    }

    @Test
    fun `an existing weekly block with a padded title still counts as the same row`() {
        // The identity trims, mirroring what the writer stores; comparing raw values would insert a
        // second row that only looks identical after the database trims it.
        val outcome = imported(
            plan(
                events = listOf(
                    timedEvent(
                        summary = " 组会 ",
                        recurrence = IcsRecurrence.Weekly(setOf(DayOfWeek.MONDAY), null),
                    ),
                ),
                existingBlocks = listOf(
                    weeklyBlock(title = "组会", dayOfWeek = DayOfWeek.MONDAY, startMinute = 10 * 60, endMinute = 11 * 60),
                ),
            ),
        )

        assertTrue(outcome.weeklyBlocks.isEmpty())
        assertEquals(1, outcome.duplicateCount)
    }

    @Test
    fun `an available override at the same minutes is not the same row as a blocked one`() {
        val outcome = imported(
            plan(
                events = listOf(timedEvent(summary = "例会")),
                existingOverrides = listOf(override(title = "例会", type = DateOverrideType.AVAILABLE)),
            ),
        )

        assertEquals(1, outcome.dateOverrides.size)
        assertEquals(0, outcome.duplicateCount)
    }

    @Test
    fun `the same event twice in one file is added once and reported once`() {
        val outcome = imported(
            plan(listOf(timedEvent(uid = "a"), timedEvent(uid = "b"))),
        )

        assertEquals(1, outcome.dateOverrides.size)
        assertEquals(1, outcome.duplicateCount)
        assertEquals(1, outcome.addedCount)
    }

    @Test
    fun `two different events with the same title at different times are both added`() {
        val outcome = imported(
            plan(
                listOf(
                    timedEvent(start = at(10), end = at(11)),
                    timedEvent(start = at(14), end = at(15)),
                ),
            ),
        )

        assertEquals(2, outcome.dateOverrides.size)
    }

    // ------------------------------------------------------------------ counters and shape

    @Test
    fun `no events at all yields NoEvents`() {
        assertEquals(CalendarImportOutcome.NoEvents, plan(emptyList()))
    }

    @Test
    fun `a file whose every event is skipped still reports the reasons`() {
        val outcome = imported(plan(listOf(timedEvent(status = IcsStatus.CANCELLED))))

        assertEquals(0, outcome.addedCount)
        assertEquals(0, outcome.duplicateCount)
        assertEquals(1, outcome.ignoredCount)
    }

    @Test
    fun `the counters add up to the number of events`() {
        val events = listOf(
            timedEvent(uid = "ok", start = at(10), end = at(11)),
            timedEvent(uid = "dup", start = at(10), end = at(11)),
            timedEvent(uid = "cancelled", status = IcsStatus.CANCELLED),
            timedEvent(uid = "free", busy = false),
        )

        val outcome = imported(plan(events))

        // Added + duplicates + ignored must account for every event, or the screen's breakdown is
        // reporting numbers the user cannot reconcile.
        assertEquals(events.size, outcome.addedCount + outcome.duplicateCount + outcome.ignoredCount)
    }

    @Test
    fun `every produced row passes the app's own validator`() {
        // Anti-vacuity in the strongest available form: whatever this mapper emits, the writers and
        // the planner will accept. A whole-day block of 0..1440 is the case that would otherwise
        // only fail at write time.
        val events = listOf(
            timedEvent(uid = "timed"),
            allDayEvent(uid = "allday"),
            allDayEvent(uid = "weekend", recurrence = IcsRecurrence.Weekly(setOf(DayOfWeek.SUNDAY), null)),
            timedEvent(uid = "weekly", recurrence = IcsRecurrence.Weekly(setOf(DayOfWeek.MONDAY), null)),
            timedEvent(uid = "night", start = at(23), end = date(20).atStartOfDay().plusHours(1)),
        )

        val outcome = imported(plan(events))

        assertTrue("expected rows to check", outcome.addedCount >= 5)
        outcome.weeklyBlocks.forEach { draft ->
            assertTrue("invalid weekly block: $draft", TimeBlockValidator.isValid(draft))
        }
        outcome.dateOverrides.forEach { draft ->
            assertTrue("invalid override: $draft", TimeBlockValidator.isValid(draft))
        }
    }

    // ------------------------------------------------------------------ the two halves together

    @Test
    fun `a real calendar file parsed and mapped produces the rows the timetable expects`() {
        // The reader and the mapper are tested separately, and each agrees with the interface the
        // other assumes — which is exactly the arrangement in which a disagreement about that
        // interface stays invisible. This is the only test that runs a file through both, so it is
        // where an exclusive-versus-inclusive all-day end shows up as the wrong number of rows.
        val text = (
            listOf(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "BEGIN:VEVENT",
                "UID:allday",
                "DTSTART;VALUE=DATE:20260919",
                "DTEND;VALUE=DATE:20260920",
                "SUMMARY:校庆",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "UID:timed",
                "DTSTART:20260919T100000",
                "DTEND:20260919T110000",
                "SUMMARY:看牙医",
                "END:VEVENT",
                "END:VCALENDAR",
            ).joinToString("\r\n") + "\r\n"
            )

        val outcome = imported(plan(IcsImporter.parse(text, ZoneId.of("Asia/Shanghai"))))

        assertEquals(
            listOf(
                DateOverrideDraft(
                    title = "校庆",
                    type = DateOverrideType.BLOCKED,
                    date = date(19),
                    startMinute = 0,
                    endMinute = 24 * 60,
                ),
                DateOverrideDraft(
                    title = "看牙医",
                    type = DateOverrideType.BLOCKED,
                    date = date(19),
                    startMinute = 10 * 60,
                    endMinute = 11 * 60,
                ),
            ),
            outcome.dateOverrides,
        )
    }

    // ------------------------------------------------------------------ the shared identity

    @Test
    fun `weekly identity trims the title and folds a blank pattern to null`() {
        assertEquals(
            weeklyBlockIdentity("高数", DayOfWeek.MONDAY, 480, 540, null),
            weeklyBlockIdentity("  高数  ", DayOfWeek.MONDAY, 480, 540, "   "),
        )
    }

    @Test
    fun `weekly identity separates rows that differ in any one field`() {
        val base = weeklyBlockIdentity("高数", DayOfWeek.MONDAY, 480, 540, null)

        assertTrue(base != weeklyBlockIdentity("高数（二）", DayOfWeek.MONDAY, 480, 540, null))
        assertTrue(base != weeklyBlockIdentity("高数", DayOfWeek.TUESDAY, 480, 540, null))
        assertTrue(base != weeklyBlockIdentity("高数", DayOfWeek.MONDAY, 490, 540, null))
        assertTrue(base != weeklyBlockIdentity("高数", DayOfWeek.MONDAY, 480, 550, null))
        assertTrue(base != weeklyBlockIdentity("高数", DayOfWeek.MONDAY, 480, 540, "1-16周"))
    }

    @Test
    fun `override identity keeps the two types apart`() {
        assertTrue(
            dateOverrideIdentity("例会", DateOverrideType.BLOCKED, date(19), 600, 660) !=
                dateOverrideIdentity("例会", DateOverrideType.AVAILABLE, date(19), 600, 660),
        )
    }
}
