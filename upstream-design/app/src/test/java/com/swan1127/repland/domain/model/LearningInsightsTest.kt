package com.swan1127.repland.domain.model

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LearningInsights] decides what the insight page tells the user about themselves, which
 * makes it the easiest place in the app to say something confidently wrong. Two rules carry
 * most of the weight here and both are pinned below:
 *
 * 1. **Enough samples, or say nothing.** A "bias" computed from two tasks is a coincidence
 *    dressed as a finding, so [LearningInsights.MIN_CALIBRATION_SAMPLES] exists and the
 *    insufficient case has its own test.
 * 2. **The median, not the mean.** One task that overran tenfold moves the mean far enough
 *    to mislabel a whole set of accurate estimates.
 */
class LearningInsightsTest {

    private val utc = ZoneId.of("UTC")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun done(
        id: String,
        planned: Int?,
        actual: Int?,
        category: TaskCategory = TaskCategory.COURSE,
    ): Task = task(id = id, durationMinutes = planned, category = category)
        .copy(actualDurationMinutes = actual)

    private fun epochOf(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    // ---------------------------------------------------------------- calibration

    @Test
    fun `the ratio is actual over predicted`() {
        val summary = LearningInsights.calibration(listOf(done("a", planned = 60, actual = 90)))
        assertEquals(1.5, summary.points.single().ratio, 1e-9)
    }

    @Test
    fun `a task with no recorded actual is not evidence of anything`() {
        // Treating a missing actual as zero would read as a wild overestimate.
        val summary = LearningInsights.calibration(listOf(done("a", planned = 60, actual = null)))
        assertEquals(emptyList<CalibrationPoint>(), summary.points)
    }

    @Test
    fun `a task with no planned length is not evidence of anything`() {
        val summary = LearningInsights.calibration(listOf(done("a", planned = null, actual = 60)))
        assertEquals(emptyList<CalibrationPoint>(), summary.points)
    }

    @Test
    fun `a zero planned length is dropped rather than dividing by zero`() {
        // Without the guard the ratio would be Infinity and would poison both averages.
        val summary = LearningInsights.calibration(
            listOf(
                done("a", planned = 0, actual = 60),
                done("b", planned = 60, actual = 0),
                done("c", planned = 60, actual = 60),
            ),
        )
        assertEquals(listOf("c"), summary.points.map(CalibrationPoint::taskId))
        assertEquals(1.0, summary.meanRatio!!, 1e-9)
    }

    @Test
    fun `two samples are not enough to call a bias`() {
        // The load-bearing rule. Two tasks that both took twice as long look like a pattern
        // and are not one.
        val summary = LearningInsights.calibration(
            listOf(
                done("a", planned = 60, actual = 120),
                done("b", planned = 60, actual = 120),
            ),
        )
        assertEquals(2, summary.count)
        assertEquals(CalibrationDirection.INSUFFICIENT_DATA, summary.direction)
        assertEquals(2.0, summary.medianRatio!!, 1e-9)
    }

    @Test
    fun `three tasks that run long read as underestimated`() {
        val summary = LearningInsights.calibration(
            listOf(
                done("a", planned = 60, actual = 120),
                done("b", planned = 30, actual = 60),
                done("c", planned = 120, actual = 240),
            ),
        )
        assertEquals(CalibrationDirection.UNDERESTIMATED, summary.direction)
    }

    @Test
    fun `three tasks that finish early read as overestimated`() {
        val summary = LearningInsights.calibration(
            listOf(
                done("a", planned = 60, actual = 30),
                done("b", planned = 60, actual = 30),
                done("c", planned = 60, actual = 30),
            ),
        )
        assertEquals(CalibrationDirection.OVERESTIMATED, summary.direction)
    }

    @Test
    fun `three accurate tasks read as calibrated`() {
        val summary = LearningInsights.calibration(
            listOf(
                done("a", planned = 60, actual = 60),
                done("b", planned = 60, actual = 65),
                done("c", planned = 60, actual = 55),
            ),
        )
        assertEquals(CalibrationDirection.CALIBRATED, summary.direction)
    }

    @Test
    fun `the band is inclusive at its edge`() {
        // 1.2 exactly is still "calibrated"; 1.21 is not. The boundary has to be decided
        // somewhere, and a test is the only place that decision is visible.
        val atEdge = LearningInsights.calibration(
            listOf(
                done("a", planned = 100, actual = 120),
                done("b", planned = 100, actual = 120),
                done("c", planned = 100, actual = 120),
            ),
        )
        assertEquals(1.2, atEdge.medianRatio!!, 1e-9)
        assertEquals(CalibrationDirection.CALIBRATED, atEdge.direction)

        val pastEdge = LearningInsights.calibration(
            listOf(
                done("a", planned = 100, actual = 121),
                done("b", planned = 100, actual = 121),
                done("c", planned = 100, actual = 121),
            ),
        )
        assertEquals(CalibrationDirection.UNDERESTIMATED, pastEdge.direction)
    }

    @Test
    fun `one wild outlier does not move the direction`() {
        // Four accurate estimates and one essay that became a week. The mean says 3.25x and
        // would tell the user they cannot estimate at all; the median says 1.0, which is
        // what actually happened to most of their work.
        val summary = LearningInsights.calibration(
            listOf(
                done("a", planned = 60, actual = 60),
                done("b", planned = 60, actual = 60),
                done("c", planned = 60, actual = 60),
                done("d", planned = 60, actual = 600),
            ),
        )
        assertEquals(1.0, summary.medianRatio!!, 1e-9)
        assertEquals(3.25, summary.meanRatio!!, 1e-9)
        assertEquals(CalibrationDirection.CALIBRATED, summary.direction)
    }

    @Test
    fun `an even number of points averages the two middle ratios`() {
        val summary = LearningInsights.calibration(
            listOf(
                done("a", planned = 60, actual = 60),
                done("b", planned = 60, actual = 60),
                done("c", planned = 60, actual = 120),
                done("d", planned = 60, actual = 120),
            ),
        )
        assertEquals(1.5, summary.medianRatio!!, 1e-9)
    }

    @Test
    fun `an empty task list has no averages and no direction`() {
        val summary = LearningInsights.calibration(emptyList())
        assertEquals(0, summary.count)
        assertNull(summary.meanRatio)
        assertNull(summary.medianRatio)
        assertEquals(CalibrationDirection.INSUFFICIENT_DATA, summary.direction)
    }

    // ---------------------------------------------------------------- minutes by day

    @Test
    fun `minutes land on the day the log was written`() {
        val day = LocalDate.of(2026, 3, 2)
        val logs = listOf(
            executionLog("l1", "t1", feedback = feedback(actualDurationMinutes = 25), createdAtEpochMillis = epochOf(day, 10, 0, utc)),
            executionLog("l2", "t1", feedback = feedback(actualDurationMinutes = 15), createdAtEpochMillis = epochOf(day, 11, 0, utc)),
        )
        val result = LearningInsights.minutesByDay(logs, utc, from = day, days = 1)
        assertEquals(listOf(DayMinutes(day, 40)), result)
    }

    @Test
    fun `days with no work are present with a zero rather than missing`() {
        // A trend line with holes in it reads as missing data instead of as a day off.
        val day = LocalDate.of(2026, 3, 2)
        val result = LearningInsights.minutesByDay(emptyList(), utc, from = day, days = 3)
        assertEquals(
            listOf(DayMinutes(day, 0), DayMinutes(day.plusDays(1), 0), DayMinutes(day.plusDays(2), 0)),
            result,
        )
    }

    @Test
    fun `logs outside the window are ignored`() {
        val day = LocalDate.of(2026, 3, 2)
        val logs = listOf(
            executionLog("old", "t1", feedback = feedback(actualDurationMinutes = 99), createdAtEpochMillis = epochOf(day.minusDays(5), 10, 0, utc)),
            executionLog("new", "t1", feedback = feedback(actualDurationMinutes = 10), createdAtEpochMillis = epochOf(day, 10, 0, utc)),
        )
        val result = LearningInsights.minutesByDay(logs, utc, from = day, days = 2)
        assertEquals(listOf(DayMinutes(day, 10), DayMinutes(day.plusDays(1), 0)), result)
    }

    @Test
    fun `a log with no recorded duration contributes nothing`() {
        val day = LocalDate.of(2026, 3, 2)
        val logs = listOf(
            executionLog("l1", "t1", feedback = feedback(progressPercent = 50), createdAtEpochMillis = epochOf(day, 10, 0, utc)),
        )
        assertEquals(listOf(DayMinutes(day, 0)), LearningInsights.minutesByDay(logs, utc, from = day, days = 1))
    }

    @Test
    fun `the zone decides which day a late log belongs to`() {
        // Same instant, two calendars. This is why the zone is a parameter and not
        // `systemDefault()`: a silently locale-dependent chart is a bug only some users see.
        val day = LocalDate.of(2026, 3, 2)
        val lateNight = epochOf(day, 23, 30, utc)
        val logs = listOf(
            executionLog("l1", "t1", feedback = feedback(actualDurationMinutes = 30), createdAtEpochMillis = lateNight),
        )
        assertEquals(
            listOf(DayMinutes(day, 30), DayMinutes(day.plusDays(1), 0)),
            LearningInsights.minutesByDay(logs, utc, from = day, days = 2),
        )
        assertEquals(
            listOf(DayMinutes(day, 0), DayMinutes(day.plusDays(1), 30)),
            LearningInsights.minutesByDay(logs, tokyo, from = day, days = 2),
        )
    }

    @Test
    fun `a non-positive window is rejected rather than returning nothing`() {
        val day = LocalDate.of(2026, 3, 2)
        assertThrows(IllegalArgumentException::class.java) {
            LearningInsights.minutesByDay(emptyList(), utc, from = day, days = 0)
        }
    }

    // ---------------------------------------------------------------- review points

    @Test
    fun `review points are counted on the day they were captured`() {
        val day = LocalDate.of(2026, 3, 2)
        val result = LearningInsights.reviewPointCounts(
            listOf(day, day, day.plusDays(1)),
            from = day,
            days = 3,
        )
        assertEquals(
            listOf(DayCount(day, 2), DayCount(day.plusDays(1), 1), DayCount(day.plusDays(2), 0)),
            result,
        )
    }

    @Test
    fun `a day with no review points is present with a zero`() {
        val day = LocalDate.of(2026, 3, 2)
        assertEquals(
            listOf(DayCount(day, 0), DayCount(day.plusDays(1), 0)),
            LearningInsights.reviewPointCounts(emptyList(), from = day, days = 2),
        )
    }

    @Test
    fun `capture dates outside the window are ignored`() {
        val day = LocalDate.of(2026, 3, 2)
        val result = LearningInsights.reviewPointCounts(
            listOf(day.minusDays(30), day),
            from = day,
            days = 1,
        )
        assertEquals(listOf(DayCount(day, 1)), result)
    }

    // ---------------------------------------------------------------- category minutes

    @Test
    fun `minutes are summed per category`() {
        val result = LearningInsights.categoryMinutes(
            listOf(
                done("a", planned = 60, actual = 30, category = TaskCategory.COURSE),
                done("b", planned = 60, actual = 45, category = TaskCategory.COURSE),
                done("c", planned = 60, actual = 20, category = TaskCategory.LEISURE),
            ),
        )
        assertEquals(
            listOf(
                CategoryMinutes(TaskCategory.COURSE, 75),
                CategoryMinutes(TaskCategory.LEISURE, 20),
            ),
            result,
        )
    }

    @Test
    fun `a category with nothing invested is omitted`() {
        // A zero-width donut slice is invisible anyway, and "生活 0 分钟" invites the user
        // to wonder what went wrong.
        val result = LearningInsights.categoryMinutes(
            listOf(done("a", planned = 60, actual = 30, category = TaskCategory.COURSE)),
        )
        assertEquals(listOf(TaskCategory.COURSE), result.map(CategoryMinutes::category))
    }

    @Test
    fun `a task with no recorded actual contributes no minutes`() {
        val result = LearningInsights.categoryMinutes(
            listOf(done("a", planned = 60, actual = null, category = TaskCategory.COURSE)),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `the order follows the category declaration, not the totals`() {
        // Otherwise the legend reshuffles between renders as the numbers change.
        //
        // The totals deliberately run *against* the declaration order: leisure is the largest
        // slice and course the smallest, so "declaration order" and "biggest first" cannot
        // produce the same list. An earlier version of this fixture had the two orders agree
        // by accident, which meant it passed without pinning anything.
        val result = LearningInsights.categoryMinutes(
            listOf(
                done("a", planned = 60, actual = 900, category = TaskCategory.LEISURE),
                done("b", planned = 60, actual = 10, category = TaskCategory.COURSE),
                done("c", planned = 60, actual = 500, category = TaskCategory.OFFICE),
            ),
        )
        assertEquals(
            listOf(TaskCategory.COURSE, TaskCategory.OFFICE, TaskCategory.LEISURE),
            result.map(CategoryMinutes::category),
        )
    }
}
