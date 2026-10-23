package com.swan1127.repland.ui.schedule

import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.ui.widget.WidgetEmphasis
import com.swan1127.repland.ui.widget.WidgetState
import com.swan1127.repland.ui.widget.widgetState
import java.time.LocalDateTime

/**
 * What the Today tab's focal card says, decided here and nowhere else.
 *
 * **Why this file exists.** The card used to decide for itself. It took the day's blocks, dropped
 * every one that had already ended — `filter { it.endMinute > nowMinute }` at the call site — and
 * then asked [com.swan1127.repland.domain.model.ScheduleTimeline.clock] which phase the first
 * survivor was in. `clock` returns [com.swan1127.repland.domain.model.TimelinePhase.OVERRUN] only
 * when `now >= entry.end`, which is exactly the set the filter had just removed. So of the four
 * branches in the card's `when (phase)`, **two were dead**: `已超时` could never be printed, and
 * `ENDED` was unreachable as well because the call left `userFinished` at its default.
 *
 * The visible consequence was worse than a dead branch. At 23:00 on a day that had eight blocks,
 * every one of them ended, the filtered list was empty, and the card printed
 * **「今天还没有具体安排」** — the app's single largest piece of text, telling the user their day was
 * empty when it was over. The home-screen widget has a word for that state
 * (`widget_done_title` / 「今天的安排已结束」) and a test asserting it is reachable; the card sitting
 * inside the app could not say it.
 *
 * **Why the fix is delegation rather than a second rule.** Once the call-site filter is gone, the
 * card's question — "which block should this surface name right now, and what is it called?" — is
 * the same question [widgetState] already answers. Two facts make that exact rather than
 * approximate:
 *
 *  * `ACTIVE` means `start <= now < end` and `UPCOMING` means `now < start`, so *every* `UPCOMING`
 *    block starts after *every* `ACTIVE` one. Concatenating "the running block, else the one
 *    starting soonest" and sorting the whole not-yet-ended set by `(start, end, id)` therefore
 *    produce the same first element — the old card's rule and the widget's row 1 agree on every
 *    day that still has something ahead.
 *  * The two surfaces are both describing the same moment to the same user. The plan document
 *    already treats "两个界面描述同一时刻却不一致" as a defect class in its own right, because a
 *    user who sees two answers concludes one of them is broken. Delegating makes disagreement
 *    *unrepresentable* instead of merely tested.
 *
 * So this file adds a shape, not a rule. It reads row 1 of the widget's decision and re-labels it
 * for a card that has one line instead of two. The three label words come from [WidgetEmphasis]
 * rather than from a second enum, because one word must not have two keys — the resource table
 * says so itself, above the widget's strings, and a duplicate vocabulary is how 「正在进行」 ends
 * up worded two ways.
 *
 * **What changed as a side effect, and why each is wanted.** Delegating inherits the widget's
 * `userFinished = it.taskClosed`. A block whose task is already completed now reads as `ENDED`
 * there and is skipped here, so a finished task stops being announced as 正在进行 — the old card
 * called `clock` without that argument and would have labelled a done task as in progress. And a
 * day whose only block is a completed task now reads as [FocalCard.Done] rather than as an empty
 * day. Both are the same defect the rest of this file is about: the card asserting something
 * untrue about the moment.
 *
 * **Why the clock is a parameter.** Same reason as [widgetState]: "the block the screen is about"
 * is only a meaningful phrase once "now" has a value. Passing it in lets the tests stand at 09:00,
 * 10:30 and 23:00 without a fake clock, and it is what makes the 23:00 case — the one that was
 * broken — a JVM test instead of a screenshot taken at the right hour.
 *
 * **The dependency direction is deliberate and worth naming.** A screen file importing from
 * `ui/widget` reads backwards. The alternative was to keep a second copy of the selection rule here
 * and pin the two together with an equality test, which is precisely the arrangement that produced
 * the defect: a copy that looked right, was wrong at one boundary, and no screenshot revealed it.
 * One rule with an odd home beats two rules that must be kept in step by hand.
 *
 * **Known gap, stated rather than hidden.** The `widget_` prefix on the shared keys and on
 * [WidgetEmphasis] is now a misnomer: the words belong to the app's phase vocabulary and the widget
 * is only one of two consumers. Renaming them is a mechanical change with its own verification cost
 * (the widget's mutation harness anchors on `WidgetEmphasis.NOW` and on `widget_empty_hint`) and no
 * user-visible benefit, so it is deferred rather than smuggled into this fix.
 */
sealed interface FocalCard {

    /**
     * There is a block to name.
     *
     * `emphasis` carries the label's resource id rather than the words, so the mapping from state
     * to word stays exhaustive by construction: [WidgetEmphasis] cannot gain a case without gaining
     * a label, and the resource guard walks it to prove each label is declared in both files.
     */
    data class Named(
        val entryId: String,
        val emphasis: WidgetEmphasis,
        val title: String,
        val startMinute: Int,
        val endMinute: Int,
        /** How many *other* blocks share this one's slot and have not already ended. */
        val parallelCount: Int,
    ) : FocalCard {
        init {
            require(parallelCount >= 0) { "parallelCount cannot be negative, got $parallelCount" }
        }
    }

    /** Nothing is scheduled for today at all. */
    data object Empty : FocalCard

    /** Today had a schedule and it is behind us — every block is over, or every task is closed. */
    data object Done : FocalCard
}

/**
 * The card's content for a given day and moment.
 *
 * `entries` is the **whole** day, unfiltered. Handing this function a pre-filtered list is the
 * original defect, so the caller is a straight line — `focalCard(timelineEntries, now)` — and
 * `FocalCardResourceTest` reads that call out of `ReplandApp.kt` to keep it one.
 */
fun focalCard(entries: List<TimelineEntry>, now: LocalDateTime): FocalCard {
    return when (val state = widgetState(entries, now)) {
        is WidgetState.Schedule -> {
            // Row 1 only. Row 2 is a widget-layout concern (the widget draws exactly two rows);
            // the card draws one block plus a count, so it stops here.
            val line = state.lines.first()
            FocalCard.Named(
                entryId = line.entryId,
                emphasis = line.emphasis,
                title = line.title,
                startMinute = line.startMinute,
                endMinute = line.endMinute,
                // The lead is excluded by id rather than by arithmetic: `- 1` would go negative for
                // a 已超时 lead, whose own window is behind the clock and so is not in the counted
                // set at all. Counting only blocks that have not ended keeps this identical to what
                // the card counted before, which is what makes the fix additive rather than a
                // redefinition.
                parallelCount = entries.count { entry ->
                    entry.id != line.entryId &&
                        entry.startMinute == line.startMinute &&
                        entry.end.isAfter(now)
                },
            )
        }

        WidgetState.Empty -> FocalCard.Empty
        WidgetState.Done -> FocalCard.Done
    }
}
