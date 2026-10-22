package com.swan1127.repland.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A weekly availability template or recurring period that blocks automatic scheduling. */
data class WeeklyTimeBlock(
    val id: String,
    val title: String,
    val kind: TimeBlockKind,
    val dayOfWeek: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
    val weekPattern: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    /** A stable visual lane. Parallel courses may share a time only when their lanes differ. */
    val trackId: String = "course",
    /** A lightweight, user-owned note shown from the day timeline. */
    val note: String? = null,
)

data class WeeklyTimeBlockDraft(
    val id: String? = null,
    val title: String,
    val kind: TimeBlockKind,
    val dayOfWeek: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
    val weekPattern: String? = null,
    val trackId: String = "course",
    val note: String? = null,
    /**
     * Carried only so a restore can put a deleted row back with the moment it was first created.
     *
     * A normal editor never sets this — the row it is editing still exists, so the writer reads the
     * original timestamp off it. The undo snapshot is the one caller that replays a row whose
     * record is already gone, and without this it would come back stamped "now", quietly rewriting
     * a field that the JSON export publishes.
     */
    val createdAtEpochMillis: Long? = null,
)

/** A one-day exception that either blocks time or temporarily makes a period available. */
data class DateOverride(
    val id: String,
    val title: String,
    val type: DateOverrideType,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    /** A lightweight, user-owned note shown from the day timeline. */
    val note: String? = null,
)

data class DateOverrideDraft(
    val id: String? = null,
    val title: String,
    val type: DateOverrideType,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val note: String? = null,
    /** See [WeeklyTimeBlockDraft.createdAtEpochMillis] — same reason, same one caller. */
    val createdAtEpochMillis: Long? = null,
)

/** Atomically observed inputs for deciding whether a confirmed plan is now stale. */
data class TimeConstraintSettings(
    val semesterFirstWeekMonday: LocalDate?,
    val updatedAtEpochMillis: Long,
)

enum class TimeBlockKind {
    AVAILABLE,
    COURSE,
    REST,
    OTHER,
}

enum class DateOverrideType {
    BLOCKED,
    AVAILABLE,
}

/**
 * What makes two weekly rows "the same block".
 *
 * One function, used by the timetable import, the calendar import, and each path's "already in the
 * timetable" check. These were once two hand-written copies of the same list and they disagreed:
 * one trimmed the title and folded a blank week pattern to null, the other compared the raw values.
 * A parser that padded a title therefore produced a row the first check considered a repeat and the
 * second considered new, so it was inserted a second time — and the writer's own `trim()` made the
 * two rows identical only *after* the fact, which is why the duplicate was invisible.
 *
 * The normalisation has to match what the writer stores, or "already there" is decided against a
 * value the database never holds. `RoomTimeRepository` trims the title and folds a blank week
 * pattern to null; this mirrors it deliberately.
 */
fun weeklyBlockIdentity(
    title: String,
    dayOfWeek: DayOfWeek,
    startMinute: Int,
    endMinute: Int,
    weekPattern: String?,
): List<Any?> = listOf(
    title.trim(),
    dayOfWeek,
    startMinute,
    endMinute,
    weekPattern?.trim()?.takeIf(String::isNotBlank),
)

/**
 * The same idea for a one-day override.
 *
 * [type] is part of the identity: an AVAILABLE override over the same minutes says the opposite of
 * a BLOCKED one, so folding them together would make a calendar import silently decline to open up
 * time the user had marked busy.
 */
fun dateOverrideIdentity(
    title: String,
    type: DateOverrideType,
    date: LocalDate,
    startMinute: Int,
    endMinute: Int,
): List<Any?> = listOf(title.trim(), type, date, startMinute, endMinute)

/**
 * The lane a weekly row is drawn in — and the lane [WeeklyBlockConflicts] compares.
 *
 * One function with two callers, on purpose. [ScheduleTimeline] decides a row's lane from its kind
 * as well as from `trackId`: a REST row is drawn in the rest lane and every other non-course row
 * in the fixed lane, whatever `trackId` the row happens to carry. A conflict rule that compared the
 * raw `trackId` would therefore report a REST row as colliding with a course that merely shares the
 * stored default — a pair the timeline draws in two different lanes, i.e. deliberately in parallel.
 * Keeping the mapping in one place is what stops the warning from disagreeing with the picture.
 */
fun WeeklyTimeBlock.laneKey(): String = when (kind) {
    TimeBlockKind.COURSE -> trackId
    TimeBlockKind.REST -> "rest"
    else -> "fixed"
}

object TimeBlockValidator {
    fun isValid(draft: WeeklyTimeBlockDraft): Boolean =
        draft.title.isNotBlank() && isValidRange(draft.startMinute, draft.endMinute)

    fun isValid(draft: DateOverrideDraft): Boolean =
        draft.title.isNotBlank() && isValidRange(draft.startMinute, draft.endMinute)

    fun parseTime(text: String): Int? {
        val match = TIME_PATTERN.matchEntire(text.trim()) ?: return null
        return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    }

    fun formatTime(minutes: Int): String = String.format(
        Locale.ROOT,
        "%02d:%02d",
        minutes / 60,
        minutes % 60,
    )

    private fun isValidRange(startMinute: Int, endMinute: Int): Boolean =
        startMinute in 0 until MINUTES_PER_DAY &&
            endMinute in 1..MINUTES_PER_DAY &&
            startMinute < endMinute

    private const val MINUTES_PER_DAY = 24 * 60
    private val TIME_PATTERN = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")
}

/**
 * Interprets the week notation that Chinese university timetables commonly use.
 *
 * An unfamiliar notation stays active so a real class cannot silently disappear from the
 * planner. A semester start date is required before a pattern can limit a weekly block.
 */
object CourseWeekPattern {
    fun appliesOn(
        pattern: String?,
        date: LocalDate,
        firstWeekMonday: LocalDate?,
    ): Boolean {
        if (pattern.isNullOrBlank() || firstWeekMonday == null) return true

        val daysFromFirstWeek = ChronoUnit.DAYS.between(firstWeekMonday, date)
        if (daysFromFirstWeek < 0) return false
        val weekNumber = (daysFromFirstWeek / 7).toInt() + 1
        val ranges = parse(pattern) ?: return true
        return ranges.any { range -> range.contains(weekNumber) }
    }

    /**
     * Whether two week notations can both be active in the same week.
     *
     * A different question from [appliesOn], and deliberately not built on top of it: that one needs
     * a semester start to turn a *date* into a week number, while this one compares two notations
     * that are already written in week numbers. Two patterns can therefore be told apart before the
     * semester date is known — which matters, because that date is optional and a conflict rule that
     * went quiet without it would say nothing on a fresh install, exactly when a fresh timetable has
     * just been imported and is most likely to be wrong.
     *
     * Fail-safe in the same direction as [appliesOn]: a blank or unrecognised notation counts as
     * "always active", so it overlaps everything. A mis-parse must never be able to hide a clash.
     */
    fun weeksMayOverlap(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return true
        val firstRanges = parse(first) ?: return true
        val secondRanges = parse(second) ?: return true
        return firstRanges.any { a -> secondRanges.any { b -> a.intersects(b) } }
    }

    private fun parse(pattern: String): List<WeekRange>? {
        val normalized = pattern
            .replace("第", "")
            .replace("周", "")
            .replace('，', ',')
            .replace('、', ',')
            .replace('；', ',')
            .replace('至', '-')
            .replace('－', '-')
            .replace('—', '-')
            .replace('–', '-')
            .replace('～', '-')
            .replace('~', '-')
            .replace('（', ' ')
            .replace('）', ' ')
            .replace('(', ' ')
            .replace(')', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return null

        val allWeeks = normalized.contains("单双")
        val matches = WEEK_RANGE.findAll(normalized).mapNotNull { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val end = match.groupValues[2].toIntOrNull() ?: start
            if (start < 1 || end < start) return@mapNotNull null
            val parity = if (allWeeks) {
                WeekParity.ALL
            } else {
                when (match.groupValues[3]) {
                    "单" -> WeekParity.ODD
                    "双" -> WeekParity.EVEN
                    else -> WeekParity.ALL
                }
            }
            WeekRange(start, end, parity)
        }.toList()
        return matches.takeIf { it.isNotEmpty() }
    }

    private data class WeekRange(
        val start: Int,
        val end: Int,
        val parity: WeekParity,
    ) {
        fun contains(week: Int): Boolean = week in start..end && when (parity) {
            WeekParity.ALL -> true
            WeekParity.ODD -> week % 2 == 1
            WeekParity.EVEN -> week % 2 == 0
        }

        /**
         * Whether some single week satisfies both ranges.
         *
         * The parity cases are the whole point, and the tempting mistake is to reason about
         * "adjacent weeks". A week is odd *or* even and never both, so an odd-week range and an
         * even-week range can never be on together — not even where the two ranges overlap, and not
         * even when the overlap is wide enough to contain one of each. "1-16 单" and "1-16 双" are
         * the same hours on alternating weeks, which is a normal timetable, not a clash.
         *
         * With the parity question settled, two same-parity ranges need only agree on *one* week:
         * take the first week of the overlap this range accepts and check it is still inside the
         * overlap. Two ALL ranges always do.
         */
        fun intersects(other: WeekRange): Boolean {
            val low = maxOf(start, other.start)
            val high = minOf(end, other.end)
            if (low > high) return false
            if (parity == WeekParity.ALL || other.parity == WeekParity.ALL) return true
            if (parity != other.parity) return false
            val wanted = if (parity == WeekParity.ODD) 1 else 0
            val firstAccepted = if (low % 2 == wanted) low else low + 1
            return firstAccepted <= high
        }
    }

    private enum class WeekParity { ALL, ODD, EVEN }

    private val WEEK_RANGE = Regex("(\\d+)\\s*(?:-\\s*(\\d+))?\\s*(单|双)?")
}
