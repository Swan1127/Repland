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
)

data class WeeklyTimeBlockDraft(
    val id: String? = null,
    val title: String,
    val kind: TimeBlockKind,
    val dayOfWeek: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
    val weekPattern: String? = null,
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
)

data class DateOverrideDraft(
    val id: String? = null,
    val title: String,
    val type: DateOverrideType,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
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
    }

    private enum class WeekParity { ALL, ODD, EVEN }

    private val WEEK_RANGE = Regex("(\\d+)\\s*(?:-\\s*(\\d+))?\\s*(单|双)?")
}
