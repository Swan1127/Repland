package com.swan1127.repland.domain.model

import com.swan1127.repland.data.importer.IcsEvent
import com.swan1127.repland.data.importer.IcsRecurrence
import com.swan1127.repland.data.importer.IcsStatus
import com.swan1127.repland.data.importer.IcsTiming
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Why an event in the file did not become a block.
 *
 * A reason per event, not a count, because the four cases need four different sentences on screen
 * and one of them — [ALREADY_PRESENT] — is not a problem at all. Folding them into one "skipped"
 * number is what makes a re-import of an unchanged file look like a failure, and makes a file full
 * of cancelled meetings look like it was read correctly.
 */
enum class CalendarSkipReason {

    /** Written by this app's own exporter: it describes the user's plan, not an outside commitment. */
    OWN_EXPORT,

    /** `STATUS:CANCELLED`. */
    CANCELLED,

    /** `TRANSP:TRANSPARENT` — the organiser marked the time as free, so it must not block planning. */
    FREE_TIME,

    /** `DTSTART` missing, or an end that could not be read or was not after the start. */
    UNREADABLE_TIME,

    /** A recurrence rule this importer deliberately does not expand (a non-weekly frequency, an interval, or `COUNT`). */
    UNSUPPORTED_RECURRENCE,

    /** A single all-day event spanning more days than any real commitment, which is a malformed file rather than a holiday. */
    OUT_OF_RANGE,

    /** Already in the timetable, by the same identity the timetable import uses. */
    ALREADY_PRESENT,
}

data class CalendarSkip(val summary: String, val reason: CalendarSkipReason)

sealed interface CalendarImportOutcome {

    /**
     * @param approximatedSeries how many weekly series were imported as always-on although the file
     *        says they end. Non-zero is not an error, but it *is* something the user should be told:
     *        those blocks will keep occupying the week after the series is over, and only a semester
     *        start date can fix it. Kept as its own number so it is never reported as a skip.
     */
    data class Imported(
        val weeklyBlocks: List<WeeklyTimeBlockDraft>,
        val dateOverrides: List<DateOverrideDraft>,
        val skipped: List<CalendarSkip>,
        val approximatedSeries: Int,
    ) : CalendarImportOutcome {

        val addedCount: Int get() = weeklyBlocks.size + dateOverrides.size

        val duplicateCount: Int get() = skipped.count { it.reason == CalendarSkipReason.ALREADY_PRESENT }

        /** Everything that was dropped for a reason the user may care about. */
        val ignoredCount: Int get() = skipped.size - duplicateCount
    }

    /** The file parsed, and held no event this importer could use. */
    data object NoEvents : CalendarImportOutcome
}

/**
 * Turns calendar events into the app's own model of occupied time.
 *
 * The decision this encodes: **an imported event is busy time, not a task.** A lecture, a dentist
 * appointment and a shift at work are things the user must attend, not things they complete, so
 * they become [WeeklyTimeBlock]s and [DateOverride]s — the two structures
 * [PlanningConstraintValidator] already refuses to schedule work on top of. Importing them as tasks
 * would put them in the task list waiting to be ticked off, which is the opposite of what a
 * calendar means.
 *
 * A weekly-recurring event becomes a weekly block; a one-off becomes a one-day override. A
 * timed event that crosses midnight becomes **two** overrides rather than being dropped or
 * clamped, because a night shift is a real commitment and neither of the cheap answers is true.
 */
object CalendarImport {

    /**
     * The longest all-day span that is read as a real commitment.
     *
     * A file can declare an all-day event ending in 2099 (which is how some tools encode "ongoing"),
     * and expanding that would write tens of thousands of rows. Beyond this the event is reported
     * as [CalendarSkipReason.OUT_OF_RANGE] instead.
     */
    const val MAX_ALL_DAY_SPAN_DAYS: Long = 60

    fun plan(
        events: List<IcsEvent>,
        existingWeeklyBlocks: List<WeeklyTimeBlock>,
        existingDateOverrides: List<DateOverride>,
        /**
         * Used when an event has no `SUMMARY`.
         *
         * Passed in rather than hard-coded because the model requires a non-blank title and this
         * object must not depend on resources — a placeholder is a piece of language, and a
         * language belongs in the resource files where a translator can reach it.
         */
        fallbackTitle: String,
        /** Only used to express a series' end as week numbers; without it an ending series is approximated. */
        semesterFirstWeekMonday: LocalDate? = null,
    ): CalendarImportOutcome {
        if (events.isEmpty()) return CalendarImportOutcome.NoEvents

        val existingBlockKeys = existingWeeklyBlocks.mapTo(mutableSetOf()) { block ->
            weeklyBlockIdentity(block.title, block.dayOfWeek, block.startMinute, block.endMinute, block.weekPattern)
        }
        val existingOverrideKeys = existingDateOverrides.mapTo(mutableSetOf()) { override ->
            dateOverrideIdentity(override.title, override.type, override.date, override.startMinute, override.endMinute)
        }

        val blocks = mutableListOf<WeeklyTimeBlockDraft>()
        val overrides = mutableListOf<DateOverrideDraft>()
        val skipped = mutableListOf<CalendarSkip>()
        val seenBlockKeys = mutableSetOf<List<Any?>>()
        val seenOverrideKeys = mutableSetOf<List<Any?>>()
        var approximated = 0

        events.forEach { event ->
            val title = event.summary.ifBlank { fallbackTitle }
            val rejection = reject(event)
            if (rejection != null) {
                skipped += CalendarSkip(title, rejection)
                return@forEach
            }

            val timing = event.timing
            val weekly = event.recurrence as? IcsRecurrence.Weekly
            val pattern = weekly?.let { seriesPattern(it.lastDate, semesterFirstWeekMonday) }
            if (weekly != null && weekly.lastDate != null && pattern == null) approximated++

            val produced = when (timing) {
                is IcsTiming.AllDay -> produceAllDay(event, timing, title, weekly, pattern)
                is IcsTiming.Timed -> produceTimed(timing, title, weekly, pattern)
                is IcsTiming.Unreadable -> Produced()
            }
            if (produced.blocks.isEmpty() && produced.overrides.isEmpty()) {
                // The reader accepted the timing but it produced nothing usable (a span past the
                // cap, or a range with no positive length).
                skipped += CalendarSkip(title, CalendarSkipReason.OUT_OF_RANGE)
                return@forEach
            }

            produced.blocks.forEach { draft ->
                val key = weeklyBlockIdentity(draft.title, draft.dayOfWeek, draft.startMinute, draft.endMinute, draft.weekPattern)
                // Checked against the file's own earlier rows as well as the timetable's, so a file
                // that lists the same meeting twice reports one skip rather than writing two rows.
                if (key in existingBlockKeys || !seenBlockKeys.add(key)) {
                    skipped += CalendarSkip(title, CalendarSkipReason.ALREADY_PRESENT)
                } else {
                    blocks += draft
                }
            }
            produced.overrides.forEach { draft ->
                val key = dateOverrideIdentity(draft.title, draft.type, draft.date, draft.startMinute, draft.endMinute)
                if (key in existingOverrideKeys || !seenOverrideKeys.add(key)) {
                    skipped += CalendarSkip(title, CalendarSkipReason.ALREADY_PRESENT)
                } else {
                    overrides += draft
                }
            }
        }

        if (blocks.isEmpty() && overrides.isEmpty() && skipped.isEmpty()) {
            return CalendarImportOutcome.NoEvents
        }
        return CalendarImportOutcome.Imported(blocks, overrides, skipped, approximated)
    }

    /** The reason an event cannot become busy time, or null when it can. */
    private fun reject(event: IcsEvent): CalendarSkipReason? = when {
        // Checked before anything else: the user's own plan is not an outside commitment, and this
        // is what keeps export → import from filling the week with the blocks the plan already has.
        event.fromRepland -> CalendarSkipReason.OWN_EXPORT
        event.status == IcsStatus.CANCELLED -> CalendarSkipReason.CANCELLED
        !event.busy -> CalendarSkipReason.FREE_TIME
        event.timing is IcsTiming.Unreadable -> CalendarSkipReason.UNREADABLE_TIME
        event.recurrence is IcsRecurrence.Unsupported -> CalendarSkipReason.UNSUPPORTED_RECURRENCE
        else -> null
    }

    private class Produced(
        val blocks: List<WeeklyTimeBlockDraft> = emptyList(),
        val overrides: List<DateOverrideDraft> = emptyList(),
    )

    private fun produceAllDay(
        event: IcsEvent,
        timing: IcsTiming.AllDay,
        title: String,
        weekly: IcsRecurrence.Weekly?,
        pattern: String?,
    ): Produced {
        val days = ChronoUnit.DAYS.between(timing.start, timing.endExclusive)
        if (days < 1 || days > MAX_ALL_DAY_SPAN_DAYS) return Produced()
        if (weekly != null) {
            // A whole-day weekly event (a standing day off) is one block per weekday it lands on.
            return Produced(
                blocks = weekly.days.sorted().map { day ->
                    WeeklyTimeBlockDraft(
                        title = title,
                        kind = TimeBlockKind.OTHER,
                        dayOfWeek = day,
                        startMinute = 0,
                        endMinute = MINUTES_PER_DAY,
                        weekPattern = pattern,
                    )
                },
            )
        }
        return Produced(
            overrides = (0 until days).map { offset ->
                val date = timing.start.plusDays(offset)
                DateOverrideDraft(
                    title = title,
                    type = DateOverrideType.BLOCKED,
                    date = date,
                    startMinute = 0,
                    endMinute = MINUTES_PER_DAY,
                )
            },
        )
    }

    private fun produceTimed(
        timing: IcsTiming.Timed,
        title: String,
        weekly: IcsRecurrence.Weekly?,
        pattern: String?,
    ): Produced {
        val ranges = splitAcrossMidnight(timing.start, timing.end)
        if (ranges.isEmpty()) return Produced()
        if (weekly != null) {
            // A repeating timed event keeps its clock time; its day comes from the rule, not from
            // the start date, so the weekday set is what decides where it lands.
            val clockRanges = ranges.filter { it.dayOffset == 0 }
            return Produced(
                blocks = weekly.days.sorted().flatMap { day ->
                    clockRanges.map { range ->
                        WeeklyTimeBlockDraft(
                            title = title,
                            kind = TimeBlockKind.OTHER,
                            dayOfWeek = day,
                            startMinute = range.startMinute,
                            endMinute = range.endMinute,
                            weekPattern = pattern,
                        )
                    }
                },
            )
        }
        return Produced(
            overrides = ranges.map { range ->
                DateOverrideDraft(
                    title = title,
                    type = DateOverrideType.BLOCKED,
                    date = timing.start.toLocalDate().plusDays(range.dayOffset.toLong()),
                    startMinute = range.startMinute,
                    endMinute = range.endMinute,
                )
            },
        )
    }

    /** One blocked range per calendar day, so an event ending after midnight stays exact. */
    private data class DayRange(val dayOffset: Int, val startMinute: Int, val endMinute: Int)

    private fun splitAcrossMidnight(start: LocalDateTime, end: LocalDateTime): List<DayRange> {
        if (!end.isAfter(start)) return emptyList()
        val startDate = start.toLocalDate()
        val result = mutableListOf<DayRange>()
        var cursor = start
        // Bounded by construction: each step moves to the next midnight, so a 3-day event is three
        // iterations and a malformed 100-year one is refused by the day count below.
        while (cursor.isBefore(end)) {
            val dayOffset = ChronoUnit.DAYS.between(startDate, cursor.toLocalDate()).toInt()
            if (dayOffset >= MAX_ALL_DAY_SPAN_DAYS) return emptyList()
            val nextMidnight = cursor.toLocalDate().plusDays(1).atStartOfDay()
            val segmentEnd = if (end.isBefore(nextMidnight)) end else nextMidnight
            result += DayRange(
                dayOffset = dayOffset,
                startMinute = cursor.hour * 60 + cursor.minute,
                endMinute = if (segmentEnd == nextMidnight) {
                    MINUTES_PER_DAY
                } else {
                    segmentEnd.hour * 60 + segmentEnd.minute
                },
            )
            cursor = segmentEnd
        }
        return result
    }

    /**
     * The series' end as the app's own week notation, or null when it cannot be expressed.
     *
     * Null is returned in two different situations and the caller counts only the second: a series
     * with no end at all (correctly always-on) and a series that ends but whose week numbers are
     * unknown because no semester start is set (always-on as an approximation, and the caller says so).
     */
    private fun seriesPattern(lastDate: LocalDate?, semesterFirstWeekMonday: LocalDate?): String? {
        if (lastDate == null || semesterFirstWeekMonday == null) return null
        val weeks = ChronoUnit.DAYS.between(semesterFirstWeekMonday, lastDate) / 7 + 1
        if (weeks < 1) return null
        return "1-${weeks}周"
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
