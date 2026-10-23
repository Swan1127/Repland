package com.swan1127.repland.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The arithmetic behind the insight page.
 *
 * Deliberately pure, Android-free and UI-free: this is the layer that decides what the
 * charts *mean*, so it is the layer that has to be unit-testable. The Compose code only
 * draws what these functions return, and every judgement call — how many samples make a
 * trend, which average to report, when a difference is too small to mention — lives here
 * where a test can hold it still.
 */

/** One task's planning accuracy: what the user predicted, and what it actually took. */
data class CalibrationPoint(
    val taskId: String,
    val title: String,
    val predictedMinutes: Int,
    val actualMinutes: Int,
) {
    /** `actual ÷ predicted`. Above 1 means the task took longer than the user expected. */
    val ratio: Double get() = actualMinutes.toDouble() / predictedMinutes.toDouble()
}

/** Which way the user's estimates lean, once there is enough evidence to say. */
enum class CalibrationDirection {
    /** Tasks consistently take longer than planned. */
    UNDERESTIMATED,

    /** Estimates are close enough to be useful. */
    CALIBRATED,

    /** Tasks consistently take less time than planned. */
    OVERESTIMATED,

    /** Too few completed tasks with both numbers to support any claim. */
    INSUFFICIENT_DATA,
}

data class CalibrationSummary(
    val points: List<CalibrationPoint>,
    val meanRatio: Double?,
    val medianRatio: Double?,
    val direction: CalibrationDirection,
) {
    val count: Int get() = points.size
}

/** Minutes invested on one day. Days with no work are present with a value of zero. */
data class DayMinutes(val date: LocalDate, val minutes: Int)

/** Review points captured on one day. */
data class DayCount(val date: LocalDate, val count: Int)

data class CategoryMinutes(val category: TaskCategory, val minutes: Int)

object LearningInsights {

    /**
     * Below this many samples a "bias" is a coincidence, and reporting one would be the
     * app telling the user a story about themselves that the data does not support.
     */
    const val MIN_CALIBRATION_SAMPLES = 3

    /**
     * How far the median ratio may sit from 1.0 before it is called a bias. Twenty percent
     * is roughly the smallest difference a person can act on when re-planning a task.
     */
    const val CALIBRATED_BAND = 0.2

    /**
     * Compares each finished task's planned length with what it actually took.
     *
     * Only tasks carrying **both** numbers take part: a task with no recorded actual is not
     * evidence of anything, and treating a missing value as zero would read as a wild
     * overestimate.
     *
     * The **median** is what drives [CalibrationSummary.direction], not the mean. One task
     * that overran tenfold — an essay that became a week — moves the mean far enough to
     * mislabel the whole set, while the median stays where most of the work actually is.
     * Both are reported so the difference is visible rather than hidden by the choice.
     */
    fun calibration(tasks: List<Task>): CalibrationSummary {
        val points = tasks.mapNotNull { task ->
            val predicted = task.totalDurationMinutes
            val actual = task.actualDurationMinutes
            if (predicted == null || actual == null || predicted <= 0 || actual <= 0) {
                null
            } else {
                CalibrationPoint(task.id, task.displayName, predicted, actual)
            }
        }
        val ratios = points.map { it.ratio }
        val median = medianOrNull(ratios)
        val direction = when {
            points.size < MIN_CALIBRATION_SAMPLES || median == null -> CalibrationDirection.INSUFFICIENT_DATA
            median > 1 + CALIBRATED_BAND -> CalibrationDirection.UNDERESTIMATED
            median < 1 - CALIBRATED_BAND -> CalibrationDirection.OVERESTIMATED
            else -> CalibrationDirection.CALIBRATED
        }
        return CalibrationSummary(
            points = points,
            meanRatio = ratios.takeIf { it.isNotEmpty() }?.average(),
            medianRatio = median,
            direction = direction,
        )
    }

    /**
     * Sums the minutes recorded in [logs] onto the calendar day each log falls on.
     *
     * [zone] is a parameter rather than `ZoneId.systemDefault()` so the function is
     * deterministic under test; a log written at 23:30 belongs to different days in
     * different zones, and a silently locale-dependent chart is a bug that only shows up
     * for some users.
     *
     * Every day in the window is returned, including empty ones — a trend line with holes
     * in it would be read as missing data rather than as a day off.
     */
    fun minutesByDay(
        logs: List<TaskExecutionLog>,
        zone: ZoneId,
        from: LocalDate,
        days: Int,
    ): List<DayMinutes> {
        require(days > 0) { "days must be positive, was $days" }
        val byDay = logs
            .mapNotNull { log ->
                val minutes = log.feedback.actualDurationMinutes ?: return@mapNotNull null
                dayOf(log.createdAtEpochMillis, zone) to minutes
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, minutes) -> minutes.sum() }
        return window(from, days) { date -> DayMinutes(date, byDay[date] ?: 0) }
    }

    /**
     * Counts review points by the day they were captured.
     *
     * Takes dates rather than [com.swan1127.repland.data.ReviewPoint] so the domain layer
     * does not depend on the storage layer; the caller does the trivial mapping.
     */
    fun reviewPointCounts(dates: List<LocalDate>, from: LocalDate, days: Int): List<DayCount> {
        require(days > 0) { "days must be positive, was $days" }
        val counts = dates.groupingBy { it }.eachCount()
        return window(from, days) { date -> DayCount(date, counts[date] ?: 0) }
    }

    /**
     * Minutes actually invested per category.
     *
     * Categories with nothing invested are omitted: a zero-width donut slice is invisible
     * anyway, and an entry reading "生活 0 分钟" invites the user to wonder what went wrong.
     * The order follows [TaskCategory]'s declaration, so the legend does not reshuffle
     * between renders as counts change.
     */
    fun categoryMinutes(tasks: List<Task>): List<CategoryMinutes> =
        TaskCategory.entries
            .map { category ->
                CategoryMinutes(
                    category = category,
                    minutes = tasks
                        .filter { it.category == category }
                        .sumOf { it.actualDurationMinutes ?: 0 },
                )
            }
            .filter { it.minutes > 0 }

    private fun dayOf(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    private inline fun <T> window(from: LocalDate, days: Int, build: (LocalDate) -> T): List<T> =
        (0 until days).map { build(from.plusDays(it.toLong())) }

    private fun medianOrNull(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }
}
