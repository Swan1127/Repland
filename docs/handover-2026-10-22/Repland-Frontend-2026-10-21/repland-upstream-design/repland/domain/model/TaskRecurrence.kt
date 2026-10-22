package com.swan1127.repland.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How a task comes back after it is finished.
 *
 * Deliberately **not** a `NONE` member: "does not repeat" is expressed as a null
 * [Task.repeatRule], the same way [Task.completionResult] is absent rather than a sentinel. A `NONE`
 * member would have to be handled by every consumer and would make the branch table a three-way
 * question ("no rule / this rule / that rule") where it should be a two-way one.
 *
 * The member set is the three periods a student actually schedules by. A repeat *interval* ("every
 * two weeks") is deliberately absent — it needs a numeric stepper in the editor, and the phase-locked
 * arithmetic in [TaskRecurrence] already generalises to it, so it can be added without redesign.
 */
enum class RepeatRule {
    DAILY,
    WEEKLY,
    MONTHLY,
}

/**
 * Where a repeating task's dates move once one occurrence is finished.
 *
 * @param steps how many whole periods the shift is — always at least one. It is greater than one
 *   only when the completion arrived so late that the very next occurrence would already be behind
 *   the clock; see [TaskRecurrence.onCompletion].
 */
data class RecurrenceAdvance(
    val steps: Int,
    /** The task's new planned day, or null when it has none and gains none. */
    val scheduledForDate: LocalDate?,
    /** The task's new deadline, or null when it has none. */
    val dueDate: LocalDate?,
) {
    init {
        require(steps >= 1) { "a recurrence advance moves at least one period, got $steps" }
    }
}

/**
 * When a repeating task comes back, decided here and nowhere else.
 *
 * **The one decision this file exists to get right: what "done" means for something that repeats.**
 * The row is the *live occurrence*; the completion is *evidence*. Those are different things, so
 * finishing a weekly report does not turn the row into a museum piece — it writes the evidence to the
 * execution log and moves the row on to the next report. See `RoomTaskRepository.confirmStatus`,
 * which inserts the log with `COMPLETED` either way: a repeating task still produces a completion in
 * the log, so profile evidence and the daily review keep working exactly as before. Nothing here can
 * change that, because nothing here writes.
 *
 * **Phase, not arithmetic.** Every date is moved by a *single* addition of `steps` periods —
 * `anchor.plusMonths(steps)` and never `plusMonths(1)` applied `steps` times. That distinction is the
 * whole reason this is a pure function with tests: `LocalDate.plusMonths` clamps (31 January plus one
 * month is 28 February), so iterating it would walk a month-end task *backwards* through the year —
 * 31 Jan → 28 Feb → 28 Mar → 28 Apr — while a single addition of twelve months keeps it on the 31st.
 * A task anchored on the 31st must still be anchored on the 31st a year later.
 *
 * **Late completion catches up instead of re-overdueing.** A weekly task finished three weeks late
 * must not land on a date that is already past, or completing it would immediately make it overdue
 * again and the user could never get out. [onCompletion] therefore shifts far enough that the result
 * is strictly after the clock — and because it does so by *counting periods* rather than by adding
 * days, the phase survives the catch-up: three missed weeks move a Monday task to a Monday. The
 * month-end boundary is the case that actually bites, because there the period count and the date
 * arithmetic disagree; [stepsToFuture] is where that is dealt with.
 *
 * **Both dates move together.** A task may carry a planned day and a deadline at once (plan Saturday,
 * hand in Monday). Shifting only one of them would leave the other pointing at a date that has
 * already happened, which is the "sentence that is false" defect this project keeps finding. Shifting
 * both by the same period count preserves their offset without either one drifting.
 *
 * **An undated task gains a date rather than being quietly ignored.** A task can carry a repeat rule
 * and no date at all. Leaving it undated would make the rule *inert* — the user sets 每天, completes
 * the task, and nothing happens, with no error anywhere. So the first occurrence is materialised one
 * period out. That is a decision, not an accident, and it is pinned by a test.
 *
 * **What this deliberately does not do.** It does not create instances, so there is no per-occurrence
 * history in the task list — the execution log is the history. It does not skip missed occurrences
 * silently *and* it does not enumerate them; a long-late completion advances by `steps` periods in one
 * move, so the user sees one task that is now current, not a backlog of ghosts. It does not touch
 * [Task.postponeCount] (the repository resets it, because postponements belong to the occurrence that
 * ended) and it does not read a clock: `today` is a parameter, which is what makes every case below
 * testable without waiting for a Monday.
 */
object TaskRecurrence {

    /** See [stepsToFuture]. Roughly 2,700 years of daily periods; no stored date comes near it. */
    private const val MAX_PERIODS = 1_000_000

    /**
     * How many times [stepsToFuture] may nudge its estimate. The proof in that function's comment
     * shows one is enough, so this is slack rather than the working bound — the assertion after the
     * loop, not this number, is what guarantees a strictly-future result.
     */
    private const val MAX_CORRECTIONS = 2

    /**
     * [date] moved by [steps] whole periods, keeping its phase.
     *
     * The single-addition rule described in the file comment lives here, and it is the reason this is
     * a separate function rather than `steps` calls to a one-period helper.
     */
    fun advance(date: LocalDate, rule: RepeatRule, steps: Int): LocalDate {
        require(steps >= 1) { "advance moves at least one period, got $steps" }
        return when (rule) {
            RepeatRule.DAILY -> date.plusDays(steps.toLong())
            RepeatRule.WEEKLY -> date.plusWeeks(steps.toLong())
            RepeatRule.MONTHLY -> date.plusMonths(steps.toLong())
        }
    }

    /**
     * The date the recurrence is anchored on: the day the user plans to work, else the deadline, else
     * today.
     *
     * `scheduledForDate` wins because it is the field that means "the day I intend to work on it",
     * which is what a repeat is about; `dueDate` is a deadline and is only a fallback for a task that
     * has no plan date. Today is the last resort so that an undated task's first occurrence has a
     * phase at all — it is not a guess about when the user wants it, only a starting point, and
     * [onCompletion] makes that explicit by ignoring [stepsToFuture] in that case.
     */
    fun anchorFor(task: Task, today: LocalDate): LocalDate =
        task.scheduledForDate ?: task.dueDate ?: today

    /**
     * The smallest number of periods, at least one, that moves [anchor] strictly past [today].
     *
     * Mostly arithmetic, but **not purely**, and the exception is why this is a tested function
     * rather than a one-liner. `ChronoUnit.MONTHS.between` counts *complete* months by
     * day-of-month — it steps back one whenever the day-of-month would go backwards — while
     * `plusMonths` clamps to the end of a short month. The two therefore disagree at the boundary:
     * 31 January to 28 February is **0** complete months (28 < 31, so the month is not counted),
     * and yet `31 January + 1 month` **is** 28 February. So `between + 1` can land on `today`
     * itself, and a monthly task anchored on the 31st, completed on the last day of a short month,
     * would be re-scheduled onto the very day it was just finished.
     *
     * The correction below walks forward to the first period that is strictly past the clock. The
     * bound is not a guess: [advance] is monotone in `steps`, and the count above is already one
     * short of the last period that is *not* past the clock, so a single extra period always clears
     * it — one more than that lands in the month after today's, which is later than every day of
     * it. The trailing `check` exists so that a wrong bound fails loudly instead of silently
     * re-overdueing the task, which is the failure this function is here to prevent.
     */
    fun stepsToFuture(rule: RepeatRule, anchor: LocalDate, today: LocalDate): Int {
        val periods = when (rule) {
            RepeatRule.DAILY -> ChronoUnit.DAYS.between(anchor, today)
            RepeatRule.WEEKLY -> ChronoUnit.WEEKS.between(anchor, today)
            RepeatRule.MONTHLY -> ChronoUnit.MONTHS.between(anchor, today)
        }
        // A span this wide is not a date the app could have stored, and letting it through would
        // overflow the conversion below into a *past* date — a silent wrong answer, which is worse
        // than a loud refusal.
        require(periods in -MAX_PERIODS.toLong()..MAX_PERIODS.toLong()) {
            "a repeat cannot span $periods periods; anchor=$anchor today=$today"
        }
        // `coerceAtLeast(1)` covers the early case: finishing before the anchor must still move the
        // task on by exactly one period, not zero.
        var steps = (periods + 1L).coerceAtLeast(1L).toInt()
        repeat(MAX_CORRECTIONS) {
            if (!advance(anchor, rule, steps).isAfter(today)) steps++
        }
        check(advance(anchor, rule, steps).isAfter(today)) {
            "advancing $anchor by $steps ${rule.name} periods did not pass $today"
        }
        return steps
    }

    /**
     * How a completing task's dates move, or null when it does not repeat.
     *
     * The repository calls this on the completion path and writes the result onto the row instead of
     * the completion, having already written the completion to the execution log.
     */
    fun onCompletion(task: Task, today: LocalDate): RecurrenceAdvance? {
        val rule = task.repeatRule ?: return null
        if (task.scheduledForDate == null && task.dueDate == null) {
            // Nothing to move, so the occurrence is materialised instead of the rule being inert.
            // One period, not `stepsToFuture`: there is no phase to catch up to yet, and anchoring
            // the first occurrence on today's phase is the least surprising reading of "每天".
            return RecurrenceAdvance(
                steps = 1,
                scheduledForDate = advance(today, rule, 1),
                dueDate = null,
            )
        }
        val steps = stepsToFuture(rule, anchorFor(task, today), today)
        return RecurrenceAdvance(
            steps = steps,
            scheduledForDate = task.scheduledForDate?.let { advance(it, rule, steps) },
            dueDate = task.dueDate?.let { advance(it, rule, steps) },
        )
    }
}
