package com.swan1127.repland.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TaskRecurrence] decides when a repeating task comes back. It is a pure function of
 * `(task, today)` precisely so that every case below can be written as a date instead of waited for.
 *
 * The tests are grouped by the decision each one pins: [TaskRecurrence.advance]'s single-addition
 * rule, [TaskRecurrence.anchorFor]'s precedence, [TaskRecurrence.stepsToFuture]'s catch-up, and
 * [TaskRecurrence.onCompletion]'s answer as a whole.
 *
 * Three of them are load-bearing in a way the others are not, and each says so in its own comment:
 *
 *  - `a multi period advance applies one addition rather than repeating a single one` separates a
 *    single addition from an iterated one. Both are plausible code; only one keeps a task on the 31st.
 *  - `a month end anchor finished on the last day of a short month is not left on today` is the case
 *    where the period count and the date arithmetic disagree. It fails against the obvious
 *    implementation, `ChronoUnit.MONTHS.between(...) + 1`.
 *  - the two property sweeps replace the case lists for the *strictly-future* guarantee, because the
 *    failure above was found at a boundary nobody would have thought to write down.
 */
class TaskRecurrenceTest {

    private val jan31 = LocalDate.of(2026, 1, 31)
    private val feb28 = LocalDate.of(2026, 2, 28)

    /** 60 days either side of [TEST_MONDAY]; covers month ends and a leap-year February. */
    private val nearbyDates: List<LocalDate> = (-60..60).map { TEST_MONDAY.plusDays(it.toLong()) }

    // ------------------------------------------------------------ advance

    @Test
    fun `each rule moves by its own period`() {
        assertEquals(TEST_MONDAY.plusDays(1), TaskRecurrence.advance(TEST_MONDAY, RepeatRule.DAILY, 1))
        assertEquals(TEST_MONDAY.plusWeeks(1), TaskRecurrence.advance(TEST_MONDAY, RepeatRule.WEEKLY, 1))
        assertEquals(LocalDate.of(2026, 4, 2), TaskRecurrence.advance(TEST_MONDAY, RepeatRule.MONTHLY, 1))
    }

    @Test
    fun `a multi period advance applies one addition rather than repeating a single one`() {
        // The load-bearing test for `advance`. January 31st plus twelve months is January 31st a
        // year later; twelve successive one-month additions give January 28th, because February
        // clamps to the 28th and the clamped day then sticks for every month after it.
        //
        // A one-month case cannot tell the two implementations apart — they agree there — which is
        // exactly why this test uses twelve.
        assertEquals(LocalDate.of(2027, 1, 31), TaskRecurrence.advance(jan31, RepeatRule.MONTHLY, 12))

        var iterated = jan31
        repeat(12) { iterated = iterated.plusMonths(1) }
        assertEquals(LocalDate.of(2027, 1, 28), iterated)
    }

    @Test
    fun `a single month from a month end clamps to the end of the shorter month`() {
        // Pinned so the test above is not vacuous: clamping is real, and it is the reason the
        // single-addition rule has to be stated rather than assumed.
        assertEquals(feb28, TaskRecurrence.advance(jan31, RepeatRule.MONTHLY, 1))
    }

    @Test
    fun `a day step is exact even across a month boundary`() {
        assertEquals(LocalDate.of(2026, 3, 1), TaskRecurrence.advance(feb28, RepeatRule.DAILY, 1))
        // 2024 is a leap year, so this is the 29th and not a crash.
        assertEquals(
            LocalDate.of(2024, 2, 29),
            TaskRecurrence.advance(LocalDate.of(2024, 2, 28), RepeatRule.DAILY, 1),
        )
    }

    @Test
    fun `advancing by zero or a negative number of periods is refused`() {
        // A zero step would hand back the same date, which would leave a finished task scheduled on
        // the day it was finished. Refused rather than silently coerced to one: a caller asking for
        // it has a bug the coercion would hide.
        assertThrows(IllegalArgumentException::class.java) {
            TaskRecurrence.advance(TEST_MONDAY, RepeatRule.DAILY, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskRecurrence.advance(TEST_MONDAY, RepeatRule.WEEKLY, -1)
        }
    }

    @Test
    fun `a recurrence advance cannot describe fewer than one period`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceAdvance(steps = 0, scheduledForDate = TEST_MONDAY, dueDate = null)
        }
        // One period is the smallest legal advance, and the constructor must not reject it.
        assertEquals(1, RecurrenceAdvance(steps = 1, scheduledForDate = TEST_MONDAY, dueDate = null).steps)
    }

    // ------------------------------------------------------------ anchorFor

    @Test
    fun `the planned day anchors a task that has both dates`() {
        val dated = task("t1", dueDate = TEST_MONDAY.plusDays(6), scheduledForDate = TEST_MONDAY)

        // The planned day wins: it means "the day I intend to work on it", which is what a repeat
        // is about. The deadline is only a fallback.
        assertEquals(TEST_MONDAY, TaskRecurrence.anchorFor(dated, TEST_MONDAY.plusDays(30)))
    }

    @Test
    fun `the deadline anchors a task with no planned day`() {
        val deadlineOnly = task("t1", dueDate = TEST_MONDAY)

        assertEquals(TEST_MONDAY, TaskRecurrence.anchorFor(deadlineOnly, TEST_MONDAY.plusDays(30)))
    }

    @Test
    fun `an undated task anchors on today`() {
        // Not a guess about when the user wants it — only a starting point, and `onCompletion`
        // makes that explicit by not running the catch-up for an undated task at all.
        assertEquals(TEST_MONDAY, TaskRecurrence.anchorFor(task("t1"), TEST_MONDAY))
    }

    // ------------------------------------------------------------ stepsToFuture

    @Test
    fun `completing on the anchor day still moves a whole period`() {
        // The smallest advance is one period, never zero.
        assertEquals(1, TaskRecurrence.stepsToFuture(RepeatRule.DAILY, TEST_MONDAY, TEST_MONDAY))
        assertEquals(1, TaskRecurrence.stepsToFuture(RepeatRule.WEEKLY, TEST_MONDAY, TEST_MONDAY))
        assertEquals(1, TaskRecurrence.stepsToFuture(RepeatRule.MONTHLY, TEST_MONDAY, TEST_MONDAY))
    }

    @Test
    fun `completing early still moves exactly one period`() {
        val anchor = TEST_MONDAY.plusDays(5)

        // The day count is negative here, so the result is floored at one rather than becoming a
        // jump to "one period after today". The cadence is anchored on the planned day, not the clock.
        assertEquals(1, TaskRecurrence.stepsToFuture(RepeatRule.DAILY, anchor, TEST_MONDAY))
        assertEquals(1, TaskRecurrence.stepsToFuture(RepeatRule.WEEKLY, anchor, TEST_MONDAY))
        assertEquals(1, TaskRecurrence.stepsToFuture(RepeatRule.MONTHLY, anchor, TEST_MONDAY))
    }

    @Test
    fun `a late completion counts the whole periods it is behind`() {
        assertEquals(2, TaskRecurrence.stepsToFuture(RepeatRule.WEEKLY, TEST_MONDAY, TEST_MONDAY.plusDays(7)))
        assertEquals(3, TaskRecurrence.stepsToFuture(RepeatRule.WEEKLY, TEST_MONDAY, TEST_MONDAY.plusDays(14)))
        assertEquals(4, TaskRecurrence.stepsToFuture(RepeatRule.WEEKLY, TEST_MONDAY, TEST_MONDAY.plusDays(21)))
    }

    @Test
    fun `a month end anchor finished on the last day of a short month is not left on today`() {
        // The case where `ChronoUnit.MONTHS.between` and `plusMonths` disagree, and the reason this
        // function is not a one-liner.
        //
        // January 31st to February 28th counts as *zero* complete months — the day-of-month goes
        // backwards, so the month is not counted — while `31 January + 1 month` *is* 28 February.
        // So the obvious `between + 1` gives one step, which lands on February 28th: today. The
        // task the user just finished would stay in today's list, and be overdue again tomorrow.
        //
        // Pinned on four independent boundaries, including a leap year, because a fix that
        // special-cased "February" would pass the first and fail the rest.
        val boundaries = listOf(
            LocalDate.of(2026, 1, 31) to LocalDate.of(2026, 2, 28),
            LocalDate.of(2024, 1, 31) to LocalDate.of(2024, 2, 29),
            LocalDate.of(2026, 3, 31) to LocalDate.of(2026, 4, 30),
            LocalDate.of(2026, 5, 31) to LocalDate.of(2026, 6, 30),
        )

        boundaries.forEach { (anchor, today) ->
            // Asserting the premise, not just the outcome: if java.time ever started counting this
            // as one month, the correction below would become unnecessary and this test should say
            // so rather than quietly keep passing.
            assertEquals("between() should count no whole month from $anchor", 0L, ChronoUnit.MONTHS.between(anchor, today))

            val steps = TaskRecurrence.stepsToFuture(RepeatRule.MONTHLY, anchor, today)
            val landed = TaskRecurrence.advance(anchor, RepeatRule.MONTHLY, steps)

            assertEquals("steps for $anchor", 2, steps)
            assertTrue("$anchor advanced by $steps months should pass $today, landed on $landed", landed.isAfter(today))
            // And the day of month survives the catch-up rather than being dragged to the 28th.
            assertEquals("day of month for $anchor", 31, landed.dayOfMonth)
        }
    }

    @Test
    fun `an absurd span is refused rather than silently overflowing`() {
        // `(periods + 1).toInt()` on a span this wide wraps into a *past* date, which is a silent
        // wrong answer. 1900 to 5000 is 1,132,252 days, past the guard's 1,000,000.
        assertEquals(1_132_252L, ChronoUnit.DAYS.between(LocalDate.of(1900, 1, 1), LocalDate.of(5000, 1, 1)))

        assertThrows(IllegalArgumentException::class.java) {
            TaskRecurrence.stepsToFuture(RepeatRule.DAILY, LocalDate.of(1900, 1, 1), LocalDate.of(5000, 1, 1))
        }
    }

    @Test
    fun `the computed step always lands strictly after the clock`() {
        // A sweep rather than a case list. The boundary failure above lives at
        // (anchor = 31 January, today = 28 February), which no hand-written case list would have
        // included; the cross product of two ranges contains it by construction.
        RepeatRule.entries.forEach { rule ->
            nearbyDates.forEach { today ->
                nearbyDates.forEach { anchor ->
                    val steps = TaskRecurrence.stepsToFuture(rule, anchor, today)
                    val landed = TaskRecurrence.advance(anchor, rule, steps)

                    assertTrue(
                        "$rule anchor=$anchor today=$today steps=$steps landed on $landed",
                        landed.isAfter(today),
                    )
                }
            }
        }
    }

    @Test
    fun `a later clock never produces a smaller step count`() {
        // The catch-up has to be monotone: if today moves later, the task cannot come back sooner.
        // A correction that subtracted a period somewhere would show up here as a dip.
        val anchors = listOf(jan31, feb28, TEST_MONDAY)

        RepeatRule.entries.forEach { rule ->
            anchors.forEach { anchor ->
                val steps = nearbyDates.map { TaskRecurrence.stepsToFuture(rule, anchor, it) }

                assertEquals("$rule from $anchor is not monotone", steps.sorted(), steps)
            }
        }
    }

    // ------------------------------------------------------------ onCompletion

    @Test
    fun `a task that does not repeat has nothing to advance`() {
        assertNull(TaskRecurrence.onCompletion(task("t1", scheduledForDate = TEST_MONDAY), TEST_MONDAY))
        // A deadline alone is not a cadence: without a rule it is just a deadline.
        assertNull(TaskRecurrence.onCompletion(task("t1", dueDate = TEST_MONDAY), TEST_MONDAY))
        assertNull(TaskRecurrence.onCompletion(task("t1"), TEST_MONDAY))
    }

    @Test
    fun `a daily task moves to the next day`() {
        val advance = TaskRecurrence.onCompletion(
            task("t1", scheduledForDate = TEST_MONDAY, repeatRule = RepeatRule.DAILY),
            TEST_MONDAY,
        )!!

        assertEquals(1, advance.steps)
        assertEquals(TEST_MONDAY.plusDays(1), advance.scheduledForDate)
        assertNull(advance.dueDate)
    }

    @Test
    fun `a weekly task keeps its weekday even when it is caught up from weeks behind`() {
        // The phase-lock claim: three missed weeks move a Monday task to a Monday, not to a
        // Wednesday. Counting periods rather than adding days is what buys this.
        val advance = TaskRecurrence.onCompletion(
            task("t1", scheduledForDate = TEST_MONDAY, repeatRule = RepeatRule.WEEKLY),
            TEST_MONDAY.plusDays(21),
        )!!

        assertEquals(4, advance.steps)
        assertEquals(LocalDate.of(2026, 3, 30), advance.scheduledForDate)
        assertEquals(DayOfWeek.MONDAY, advance.scheduledForDate!!.dayOfWeek)
    }

    @Test
    fun `a monthly task keeps its day of month`() {
        val advance = TaskRecurrence.onCompletion(
            task("t1", scheduledForDate = jan31, repeatRule = RepeatRule.MONTHLY),
            LocalDate.of(2026, 3, 15),
        )!!

        assertEquals(2, advance.steps)
        assertEquals(LocalDate.of(2026, 3, 31), advance.scheduledForDate)
    }

    @Test
    fun `a monthly task finished on the last day of a short month lands in the following month`() {
        // The same boundary as `stepsToFuture`'s test, seen from the caller's side: the row must
        // not come back dated today.
        val advance = TaskRecurrence.onCompletion(
            task("t1", scheduledForDate = jan31, repeatRule = RepeatRule.MONTHLY),
            feb28,
        )!!

        assertEquals(LocalDate.of(2026, 3, 31), advance.scheduledForDate)
        assertTrue(advance.scheduledForDate!!.isAfter(feb28))
    }

    @Test
    fun `both dates move by the same number of periods so their offset survives`() {
        // Plan Saturday, hand in Monday. Shifting only one of them would leave the other pointing
        // at a day that has already happened.
        val planned = LocalDate.of(2026, 3, 7)
        val deadline = LocalDate.of(2026, 3, 9)
        assertEquals(DayOfWeek.SATURDAY, planned.dayOfWeek)

        val advance = TaskRecurrence.onCompletion(
            task("t1", scheduledForDate = planned, dueDate = deadline, repeatRule = RepeatRule.WEEKLY),
            planned,
        )!!

        assertEquals(1, advance.steps)
        assertEquals(planned.plusWeeks(1), advance.scheduledForDate)
        assertEquals(deadline.plusWeeks(1), advance.dueDate)
        // The two-day gap is preserved exactly, which is the whole point of moving both.
        assertEquals(2L, ChronoUnit.DAYS.between(advance.scheduledForDate, advance.dueDate))
    }

    @Test
    fun `a task with only a deadline moves the deadline and leaves the planned day empty`() {
        val advance = TaskRecurrence.onCompletion(
            task("t1", dueDate = TEST_MONDAY, repeatRule = RepeatRule.WEEKLY),
            TEST_MONDAY,
        )!!

        // It does not invent a planned day: the task never had one.
        assertNull(advance.scheduledForDate)
        assertEquals(TEST_MONDAY.plusWeeks(1), advance.dueDate)
    }

    @Test
    fun `an undated task gains its first planned day rather than being quietly ignored`() {
        // A rule with no date anywhere would be *inert*: the user picks 每天, completes the task,
        // and nothing happens, with no error anywhere. So the first occurrence is materialised one
        // period out. A decision, not an accident.
        //
        // The expectations are literals rather than `advance(today, rule, 1)`: an expectation
        // computed by the code under test cannot fail, so writing it that way would have made this
        // case assert nothing about `advance` at all.
        val expected = mapOf(
            RepeatRule.DAILY to TEST_MONDAY.plusDays(1),
            RepeatRule.WEEKLY to TEST_MONDAY.plusWeeks(1),
            RepeatRule.MONTHLY to LocalDate.of(2026, 4, 2),
        )

        RepeatRule.entries.forEach { rule ->
            val advance = TaskRecurrence.onCompletion(task("t1", repeatRule = rule), TEST_MONDAY)!!

            assertEquals("steps for $rule", 1, advance.steps)
            assertEquals("first occurrence for $rule", expected.getValue(rule), advance.scheduledForDate)
            assertNull("an undated task has no deadline to move", advance.dueDate)
        }
    }

    @Test
    fun `every returned date is strictly in the future`() {
        // The property the whole file exists for, swept over the boundaries rather than the round
        // dates. Clearing the finished occurrence's outcome fields is the repository's business;
        // this function's business is that the row comes back ahead of the clock.
        RepeatRule.entries.forEach { rule ->
            nearbyDates.forEach { today ->
                nearbyDates.forEach { anchor ->
                    val advance = TaskRecurrence.onCompletion(
                        task("t1", scheduledForDate = anchor, dueDate = anchor.plusDays(2), repeatRule = rule),
                        today,
                    )!!

                    assertTrue(
                        "$rule anchor=$anchor today=$today gave ${advance.scheduledForDate}",
                        advance.scheduledForDate!!.isAfter(today),
                    )
                    assertTrue(
                        "$rule anchor=$anchor today=$today gave deadline ${advance.dueDate}",
                        advance.dueDate!!.isAfter(today),
                    )
                }
            }
        }
    }

    @Test
    fun `every advance reports at least one period`() {
        // `RecurrenceAdvance` requires it, so a zero would throw rather than be asserted here —
        // this sweeps enough anchors to have entered every branch that could produce one.
        RepeatRule.entries.forEach { rule ->
            nearbyDates.forEach { anchor ->
                val advance = TaskRecurrence.onCompletion(
                    task("t1", scheduledForDate = anchor, repeatRule = rule),
                    TEST_MONDAY,
                )!!

                assertTrue("$rule from $anchor reported ${advance.steps}", advance.steps >= 1)
            }
        }
    }
}
