package com.swan1127.repland.ui.schedule

import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.ui.widget.WidgetEmphasis
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what the Today tab's focal card says at a given moment.
 *
 * **Why this file had to exist.** The card is the largest text in the app and the only thing on the
 * Today tab that speaks in a full sentence. Before this rule was extracted it decided for itself,
 * from a list the call site had already filtered — and the filter removed exactly the blocks the
 * clock needs in order to report `OVERRUN`. Two of its four branches were therefore unreachable,
 * and the visible result was not a missing label but a **false one**: at 23:00 on a day with eight
 * blocks behind it, the card printed 「今天还没有具体安排」. Nothing in the suite could see that,
 * because nothing in the suite could stand at 23:00.
 *
 * The three defects worth hunting here, none of which a screenshot of a normal morning shows:
 *
 *   1. **A state that can never be reached.** `已超时` is the third priority, behind a running block
 *      and one that is still ahead, so it is easy to write it in a way no input reaches — which is
 *      exactly what happened. `Done` is the same trap one level up: it is only distinguishable from
 *      `Empty` by *also* having entries, so collapsing the two costs nothing visible until the one
 *      hour of the day when they differ.
 *   2. **A count that includes the wrong blocks.** `parallelCount` must exclude the block it is
 *      counting around, and must not count a block in the same slot whose own window is already
 *      behind the clock. Both directions are silent: an off-by-one in a count reads as prose.
 *   3. **A boundary off by one minute.** `start <= now < end` — a block that starts exactly now is
 *      running, one that ends exactly now is not.
 *
 * **What this file does not assert.** That the card and the widget agree on which block they name.
 * They cannot disagree: [focalCard] reads row 1 of [com.swan1127.repland.ui.widget.widgetState]
 * rather than re-deriving it, so an equality assertion between the two would be a tautology — it
 * would pass for any input, including the ones that used to be wrong, and a test that cannot fail
 * is worse than no test. What is asserted instead is the behaviour that agreement is supposed to
 * produce: which block, under which label, at which moments.
 *
 * **What this does not prove:** that the card is rendered — there is no Compose test in this
 * project, so the mapping from [FocalCard] to pixels is unverified and is listed as an open gap.
 * That the caller passes the unfiltered day is checked in `FocalCardResourceTest`, by reading the
 * call out of the source, because the call site itself cannot be executed here.
 */
class TimelineFocusTest {

    private val date: LocalDate = LocalDate.of(2026, 10, 9)

    private fun at(hour: Int, minute: Int = 0): LocalDateTime = date.atTime(hour, minute)

    /** Minutes since midnight, spelled as a clock time. `10 * 30` is 05:00, which is the point. */
    private fun h(hour: Int, minute: Int = 0): Int = hour * 60 + minute

    private fun entry(
        id: String,
        startMinute: Int,
        endMinute: Int,
        kind: TimelineKind = TimelineKind.TASK,
        title: String = id,
        taskClosed: Boolean = false,
    ): TimelineEntry = TimelineEntry(
        id = id,
        title = title,
        kind = kind,
        date = date,
        startMinute = startMinute,
        endMinute = endMinute,
        taskClosed = taskClosed,
    )

    private fun course(id: String, startMinute: Int, endMinute: Int, title: String = id) =
        entry(id, startMinute, endMinute, kind = TimelineKind.COURSE, title = title)

    private fun named(card: FocalCard): FocalCard.Named = card as FocalCard.Named

    // ------------------------------------------------------------------ empty versus done

    @Test
    fun `an empty day shows the empty card`() {
        assertEquals(FocalCard.Empty, focalCard(emptyList(), at(9)))
    }

    @Test
    fun `a full day that is over reads as done rather than as an empty day`() {
        // The defect this file exists for. Every one of these blocks ends before 23:00, so the old
        // call-site filter left an empty list and the card printed 「今天还没有具体安排」 about a day
        // that had six blocks in it.
        //
        // Lectures rather than tasks, deliberately: a day that ends with unfinished *work* is a
        // different state, and the right answer there is to name the missed block — that is what
        // the 已超时 tests below are for. This one is about the day where there is genuinely nothing
        // left to do, which the card had no words for.
        val lectureDay = listOf(
            course("c1", h(8), h(10)),
            course("c2", h(10), h(12)),
            course("c3", h(14), h(16)),
            course("c4", h(16), h(18)),
            course("c5", h(19), h(20)),
            course("c6", h(20), h(22)),
        )

        assertEquals(FocalCard.Done, focalCard(lectureDay, at(23)))

        // Neither result is a constant, which is the anti-vacuity half of the assertion above: the
        // same list before the day starts names a block, and a genuinely empty day at the same hour
        // is Empty. Without this, a rule that always returned Done would pass the line above.
        assertTrue(focalCard(lectureDay, at(7)) is FocalCard.Named)
        assertEquals(FocalCard.Empty, focalCard(emptyList(), at(23)))
    }

    @Test
    fun `a day of finished lectures is over, not missed`() {
        // Lectures are never "confirmed", so their entries are never closed and their window
        // passing leaves them OVERRUN forever. Naming one would accuse the user of missing a class
        // they attended — the widget's rule already restricts 已超时 to work blocks, and this pins
        // that the card inherits the restriction rather than re-deciding it.
        assertEquals(FocalCard.Done, focalCard(listOf(course("c1", h(8), h(9))), at(9, 30)))
    }

    @Test
    fun `a day whose only task is finished is over, not an empty day`() {
        // The second half of the old defect. A completed task is still on the timeline, so the list
        // is non-empty, but the clock reports ENDED for it and it must not be named — announcing a
        // done task as 正在进行 is the same class of lie as calling a finished day empty.
        val done = entry("t1", h(10), h(11), taskClosed = true)

        assertEquals(FocalCard.Done, focalCard(listOf(done), at(10, 30)))
    }

    // ------------------------------------------------------------------ the three labels

    @Test
    fun `a block in progress is named as now`() {
        val card = focalCard(listOf(course("c1", h(9), h(11), title = "数据结构")), at(10))

        assertEquals("c1", named(card).entryId)
        assertEquals(WidgetEmphasis.NOW, named(card).emphasis)
        assertEquals("数据结构", named(card).title)
        assertEquals(h(9), named(card).startMinute)
        assertEquals(h(11), named(card).endMinute)
    }

    @Test
    fun `a block still ahead is named as next`() {
        val card = focalCard(listOf(course("c1", h(10), h(11))), at(9))

        assertEquals("c1", named(card).entryId)
        assertEquals(WidgetEmphasis.NEXT, named(card).emphasis)
    }

    @Test
    fun `the card names the widget's first row, not its second`() {
        // The rule underneath decides two rows; the card draws one. Without this fixture a rule that
        // read the *second* row would still be caught — but only by accident, through
        // `a block in a different slot is not counted`, which happens to pair an active block with
        // an upcoming one. That accident is not a reason to leave it to chance: a rule that named
        // the block after the one it is about would put the card one step ahead of the timeline
        // beneath it, and this is the fixture that says so on purpose.
        val card = focalCard(
            listOf(course("c1", h(10), h(11)), course("c2", h(12), h(13))),
            at(9),
        )

        assertEquals("c1", named(card).entryId)
        assertEquals(WidgetEmphasis.NEXT, named(card).emphasis)
    }

    @Test
    fun `an unfinished work block left behind is named as late`() {
        // The branch that used to be dead. It is only reachable when nothing is running and nothing
        // is ahead, which is why it needs a fixture where the day is otherwise over — and why the
        // old filter, which deleted this block before the clock ever saw it, made it unreachable.
        val card = focalCard(listOf(entry("t1", h(8), h(9), title = "写实验报告")), at(9, 30))

        assertEquals("t1", named(card).entryId)
        assertEquals(WidgetEmphasis.LATE, named(card).emphasis)
        assertEquals("写实验报告", named(card).title)
    }

    @Test
    fun `a missed block is not named while another block is running`() {
        // A missed block is the last resort, never a nag: while there is something to do, that is
        // what the largest text on the screen is for.
        val card = focalCard(
            listOf(entry("missed", h(8), h(9)), entry("running", h(10), h(11))),
            at(10, 30),
        )

        assertEquals("running", named(card).entryId)
        assertEquals(WidgetEmphasis.NOW, named(card).emphasis)
    }

    @Test
    fun `a missed block is not named while another block is still ahead`() {
        val card = focalCard(
            listOf(entry("missed", h(8), h(9)), entry("ahead", h(10), h(11))),
            at(9, 30),
        )

        assertEquals("ahead", named(card).entryId)
        assertEquals(WidgetEmphasis.NEXT, named(card).emphasis)
    }

    @Test
    fun `the longest-missed work block is the one named`() {
        val card = focalCard(
            listOf(entry("later", h(8), h(9)), entry("earlier", h(7), h(8))),
            at(9, 30),
        )

        assertEquals("earlier", named(card).entryId)
        assertEquals(WidgetEmphasis.LATE, named(card).emphasis)
    }

    @Test
    fun `a finished task is not named even while its window is open`() {
        // `ScheduleTimeline.entries` keeps a closed task on the timeline with `taskClosed = true`.
        // The card used to call the clock without that argument, so a done task read as ACTIVE and
        // the app announced 正在进行 about work the user had already finished.
        val card = focalCard(
            listOf(entry("done", h(10), h(11), taskClosed = true), course("c2", h(12), h(13))),
            at(10, 30),
        )

        assertEquals("c2", named(card).entryId)
        assertEquals(WidgetEmphasis.NEXT, named(card).emphasis)
    }

    // ------------------------------------------------------------------ the boundaries

    @Test
    fun `a block starting exactly now is running`() {
        assertEquals(WidgetEmphasis.NOW, named(focalCard(listOf(course("c1", h(9), h(10))), at(9))).emphasis)
    }

    @Test
    fun `a block ending exactly now is no longer the one named`() {
        // 09:00 ends the first block and starts the second, so one assertion walks the boundary from
        // both sides: the first is over, the second is running.
        val card = focalCard(
            listOf(course("c1", h(8), h(9)), course("c2", h(9), h(10))),
            at(9),
        )

        assertEquals("c2", named(card).entryId)
        assertEquals(WidgetEmphasis.NOW, named(card).emphasis)
    }

    // ------------------------------------------------------------------ the parallel count

    @Test
    fun `the named block does not count itself as parallel`() {
        val card = focalCard(listOf(course("c1", h(10), h(11))), at(10, 30))

        assertEquals(0, named(card).parallelCount)
    }

    @Test
    fun `a block sharing the slot is counted`() {
        val card = focalCard(
            listOf(course("c1", h(10), h(11)), entry("t1", h(10), h(11))),
            at(10, 30),
        )

        assertEquals(1, named(card).parallelCount)
    }

    @Test
    fun `a block in the same slot that has already ended is not counted`() {
        // "同一时段还有 N 件并行事项" is about the slot the user is in *now*. A 09:00–10:00 block
        // that finished an hour ago shares the start minute of the 09:00–12:00 block being named,
        // and counting it would tell the user they are doing two things at once.
        val card = focalCard(
            listOf(course("long", h(9), h(12)), entry("short", h(9), h(10))),
            at(10, 30),
        )

        assertEquals("long", named(card).entryId)
        assertEquals(0, named(card).parallelCount)
    }

    @Test
    fun `a late block never carries a parallel count`() {
        // Both blocks are behind the clock, so neither is "in the same slot as what you are doing".
        // Written as an assertion rather than left implicit because the arithmetic version of this
        // count — `matching - 1` — goes to -1 here and trips the state's own invariant instead of
        // producing a wrong number, which would look like a crash rather than a counting bug.
        val card = focalCard(
            listOf(entry("a", h(8), h(9)), entry("b", h(8), h(9))),
            at(9, 30),
        )

        assertEquals(WidgetEmphasis.LATE, named(card).emphasis)
        assertEquals(0, named(card).parallelCount)
    }

    @Test
    fun `a block in a different slot is not counted`() {
        // Note the shape, because it is not obvious from the name: the two blocks are adjacent, so
        // the decision underneath has *two* rows (c1 running, c2 next) and c2's start minute equals
        // c1's end. That makes this fixture the second killer of two mutants that were written to be
        // killed elsewhere — reading the wrong row, and keying the count on the end minute instead
        // of the start. Both were found by mutation testing, not by reading.
        val card = focalCard(
            listOf(course("c1", h(10), h(11)), course("c2", h(11), h(12))),
            at(10, 30),
        )

        assertEquals("c1", named(card).entryId)
        assertEquals(0, named(card).parallelCount)
    }

    // ------------------------------------------------------------------ determinism

    @Test
    fun `the card does not depend on the order the entries arrive in`() {
        val blocks = listOf(
            course("c1", h(8), h(9)),
            entry("t1", h(10), h(11)),
            entry("t2", h(10), h(11)),
        )

        assertEquals(focalCard(blocks, at(10, 30)), focalCard(blocks.reversed(), at(10, 30)))
    }

    @Test
    fun `two blocks in progress pick the one the timeline would sort first`() {
        // Parallel tracks are normal, and the day timeline sorts by (start, end, id). The card
        // names the same card the user is looking at because both go through the same comparator.
        val card = focalCard(
            listOf(entry("long", h(10), h(11)), entry("short", h(10), h(10, 30))),
            at(10, 10),
        )

        assertEquals("short", named(card).entryId)
    }

    // ------------------------------------------------------------------ the state's invariant

    @Test
    fun `a named card refuses a negative parallel count`() {
        assertThrows(IllegalArgumentException::class.java) {
            FocalCard.Named(
                entryId = "x",
                emphasis = WidgetEmphasis.NOW,
                title = "x",
                startMinute = 0,
                endMinute = 60,
                parallelCount = -1,
            )
        }
    }
}
