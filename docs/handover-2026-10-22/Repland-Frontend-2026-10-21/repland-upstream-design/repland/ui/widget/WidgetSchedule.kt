package com.swan1127.repland.ui.widget

import androidx.annotation.StringRes
import com.swan1127.repland.R
import com.swan1127.repland.domain.model.ScheduleTimeline
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.domain.model.TimelinePhase
import java.time.LocalDateTime

/**
 * Which block a today-surface names right now, and what it calls it — decided here and nowhere
 * else.
 *
 * Two surfaces consume this decision: the home-screen widget, which draws two rows of it, and the
 * Today tab's focal card, which reads row 1 through `ui/schedule/TimelineFocus.kt` and adds a count
 * of parallel blocks. They share it on purpose. The plan document already treats "两个界面描述同一
 * 时刻却不一致" as a defect class of its own — a user who sees two answers concludes one of them is
 * broken — and the card's own private copy of this rule is exactly how it came to print
 * 「今天还没有具体安排」 at 23:00 on a day that had eight blocks.
 *
 * A widget is the one surface in this app that nobody can scroll, tap into, or wait on: it is a
 * 2×2-inch rectangle of text that has to be *right* the instant the user glances at the home
 * screen. That makes it a strictly worse place to discover a wrong answer than any screen inside
 * the app — and a strictly better place to put the answer, because it needs no launch.
 *
 * **Why this is a separate pure file.** The obvious implementation reads the database, formats
 * strings and pushes `RemoteViews` in one `AppWidgetProvider` method. That code cannot be tested
 * at all: `AppWidgetManager` and `RemoteViews` are Android framework classes and this project has
 * no Robolectric, so "which block does the widget name at 10:05" would be answerable only by
 * installing the APK and looking. Splitting the *decision* out means the decision is a plain
 * function of `(entries, now)` — and the plumbing that remains is a straight mapping with no
 * branches left to get wrong.
 *
 * **Why the clock is a parameter.** Same reason as [com.swan1127.repland.ui.tools.recommendTools]:
 * "the block that is running now" is only a meaningful phrase once "now" has a value. Passing it
 * in lets the tests stand at 09:00, 10:59 and 23:00 without a fake clock, and it guarantees the
 * widget and the day timeline cannot disagree, because both ask [ScheduleTimeline.clock] the same
 * question with the same answer.
 *
 * **What this file deliberately does not do.** It does not format anything. Minutes stay integers
 * and the caller turns them into text, because the project formats clock times with a fixed
 * `Locale.ROOT` pattern (see [com.swan1127.repland.domain.model.TimeBlockValidator.formatTime])
 * and a widget that used the device locale would show a different time from the app next to it.
 */

/** The widget shows two rows: the thing you are in, and the thing after it. */
const val WIDGET_LINE_LIMIT: Int = 2

/**
 * The label a row carries, and the string that renders it.
 *
 * The resource id lives on the enum rather than in a `when` at the call site so the mapping is
 * exhaustive by construction: adding a case here is a compile error until it has a label, and
 * `WidgetScheduleResourceTest` walks [entries] to prove each label is declared in **both**
 * resource files. That check exists because a missing translation does not crash — Android falls
 * back to `values/`, so the failure mode is an English user reading Chinese in the middle of an
 * otherwise English home screen, which no build and no screenshot on a Chinese device reveals.
 */
enum class WidgetEmphasis(@param:StringRes val labelRes: Int) {
    /** A block whose window contains now. */
    NOW(R.string.widget_label_now),

    /** A block whose window has passed and whose work is still open. */
    LATE(R.string.widget_label_late),

    /** A block that has not started yet. */
    NEXT(R.string.widget_label_next),
}

/** One row of the widget. Minutes, not text: see the note above. */
data class WidgetLine(
    val entryId: String,
    val emphasis: WidgetEmphasis,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
)

/**
 * The three things the widget can be.
 *
 * Modelled as a sealed type rather than "a list that is sometimes empty" so that the two empty
 * cases cannot be confused with each other or with a populated one. They need different words:
 * "今天还没有具体安排" is false at 23:00 on a day that was full, and "今天的安排已结束" is false
 * at 08:00 on a day that is still empty. A single "no lines" branch would have to pick one and be
 * wrong the rest of the time.
 */
sealed interface WidgetState {

    /**
     * There is something to show.
     *
     * The invariants are asserted here rather than left to the caller: a `Schedule` with no rows
     * is exactly the `Empty` case, and a `Schedule` with three rows would draw past the bottom of
     * the widget. `TimelineEntry` already uses the same technique for its minute range.
     */
    data class Schedule(val lines: List<WidgetLine>, val remainingCount: Int) : WidgetState {
        init {
            require(lines.isNotEmpty()) { "a Schedule with no lines is Empty" }
            require(lines.size <= WIDGET_LINE_LIMIT) {
                "the widget draws at most $WIDGET_LINE_LIMIT lines, got ${lines.size}"
            }
            require(remainingCount >= 0) { "remainingCount cannot be negative" }
        }
    }

    /** Nothing is scheduled for today at all. */
    data object Empty : WidgetState

    /** Today had a schedule and it is behind us — every block is over, or every task is closed. */
    data object Done : WidgetState
}

/** Matches the order `ScheduleTimeline.entries` sorts by, so both agree on "the first" block. */
private val TIMELINE_ORDER: Comparator<TimelineEntry> =
    compareBy(TimelineEntry::startMinute, TimelineEntry::endMinute, TimelineEntry::id)

/**
 * Which block the widget names, and what it calls it.
 *
 * The order of the three candidate lists *is* the specification, so it is stated once here rather
 * than left to the reading order of the code:
 *
 * | row | source | label |
 * |---|---|---|
 * | 1 | the block containing now | 正在进行 |
 * | 1 | else the block that starts soonest | 下一项 |
 * | 1 | else the open work block that has been missed longest | 已超时 |
 * | 2 | the next block after row 1 | 下一项 |
 *
 * Two decisions in that table are worth defending:
 *
 * **A missed block is the last resort, never a nag.** `已超时` only appears when nothing is
 * running and nothing is left to start, i.e. once the day is effectively over. Showing it while
 * the user still has a block ahead would turn the widget into a permanent accusation on the one
 * screen they cannot dismiss — and the missed block is not lost, it is still on the day timeline
 * where it can be moved.
 *
 * **Only work blocks can be late.** [ScheduleTimeline.entries] leaves `taskClosed` false for every
 * course, rest and commitment block, because nobody "confirms" a lecture — so at 23:00 every
 * lecture block of the day reads as [TimelinePhase.OVERRUN]. Treating those as missed would make
 * the widget announce that the user is late for a lecture that finished nine hours ago. Late is
 * therefore restricted to [TimelineKind.TASK], where "over" really does mean "you did not do it".
 *
 * Row 2 is always the *next* block, never a second missed one: a row is an instruction about what
 * to do, and something that has already passed cannot be next.
 *
 * **Two rows is a shape, not a limit.** The list is built from one primary pick and one secondary
 * pick, so there is no `take` to write and nothing to truncate — [WIDGET_LINE_LIMIT] exists for
 * the `require` in [WidgetState.Schedule] and for the layout, which has exactly that many rows.
 */
fun widgetState(entries: List<TimelineEntry>, now: LocalDateTime): WidgetState {
    // One clock reading per entry, taken with the same `userFinished` the day timeline passes, so
    // a task the user marked done reads as ENDED here exactly as it does there.
    val phases = entries.map { it to ScheduleTimeline.clock(it, now, userFinished = it.taskClosed) }

    // The same comparator ScheduleTimeline.entries sorts by, so "the first active block" here is
    // "the first active card" there. A widget that picked a different one of two parallel blocks
    // than the screen next to it would look like a bug in the app rather than in the widget.
    val active = phases
        .filter { (_, clock) -> clock.phase == TimelinePhase.ACTIVE }
        .map { it.first }
        .sortedWith(TIMELINE_ORDER)
    val upcoming = phases
        .filter { (_, clock) -> clock.phase == TimelinePhase.UPCOMING }
        .map { it.first }
        .sortedWith(TIMELINE_ORDER)
    val missed = phases
        .filter { (entry, clock) -> clock.phase == TimelinePhase.OVERRUN && entry.kind == TimelineKind.TASK }
        .map { it.first }
        .sortedWith(TIMELINE_ORDER)

    val primary = active.firstOrNull()?.toLine(WidgetEmphasis.NOW)
        ?: upcoming.firstOrNull()?.toLine(WidgetEmphasis.NEXT)
        ?: missed.firstOrNull()?.toLine(WidgetEmphasis.LATE)
        ?: return if (entries.isEmpty()) WidgetState.Empty else WidgetState.Done

    val secondary = upcoming.firstOrNull { it.id != primary.entryId }?.toLine(WidgetEmphasis.NEXT)
    val lines = listOfNotNull(primary, secondary)
    val shown = lines.mapTo(mutableSetOf(), WidgetLine::entryId)

    // Counts only what is not already on screen, so the footer and the rows never say the same
    // thing twice — "下一项 英语阅读 · 今天还有 1 项" is not a lie, but it reads like one.
    return WidgetState.Schedule(lines, upcoming.count { it.id !in shown })
}

/**
 * When the widget's content next changes on its own, so the caller can wake up for it.
 *
 * `AppWidgetProviderInfo.updatePeriodMillis` cannot go below 30 minutes, which is long enough for
 * a "what am I doing now" widget to be confidently wrong. The state only ever changes when the
 * clock crosses a block boundary — a start turns a block from upcoming into current, an end turns
 * it from current into over — so the next boundary is exactly when a redraw is worth its cost.
 *
 * Midnight is the floor. After the last block of the day there is no boundary left, but the date
 * still changes, and a widget that spent the night describing yesterday would be worse than one
 * that is merely empty.
 *
 * Always returns a time strictly after [now]: the caller arms an alarm with it, and an alarm in
 * the past fires immediately, which would turn this into a busy loop.
 */
fun nextRefreshAt(entries: List<TimelineEntry>, now: LocalDateTime): LocalDateTime {
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
    val boundary = entries.asSequence()
        .flatMap { sequenceOf(it.start, it.end) }
        .filter { it.isAfter(now) }
        .minOrNull()
    return if (boundary == null || boundary.isAfter(midnight)) midnight else boundary
}

private fun TimelineEntry.toLine(emphasis: WidgetEmphasis): WidgetLine = WidgetLine(
    entryId = id,
    emphasis = emphasis,
    title = title,
    startMinute = startMinute,
    endMinute = endMinute,
)
