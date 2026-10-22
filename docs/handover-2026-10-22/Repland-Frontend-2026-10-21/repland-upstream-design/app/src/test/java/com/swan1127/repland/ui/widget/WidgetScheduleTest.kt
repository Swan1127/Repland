package com.swan1127.repland.ui.widget

import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins which block the home-screen widget names, and when it decides to redraw.
 *
 * The widget is the one surface in this app that cannot be walked through: it is a rectangle on the
 * home screen that the user reads in a glance and then either believes or does not. A stale or
 * wrong row there is invisible to every other test in the suite — the screens inside the app all
 * read `ScheduleTimeline` directly and none of them render `RemoteViews` — so this file is the only
 * thing standing between "the rule is right" and "nobody has ever looked".
 *
 * The three defects worth hunting here, none of which a screenshot would show:
 *
 *   1. **A boundary off by one minute.** `start <= now < end` versus `<` or `<=` on either side is
 *      the classic mistake, and it is only visible in the one minute it applies to.
 *   2. **A branch that can never fire.** `已超时` is the third priority, behind a running block and
 *      an upcoming one, so it is easy to write it in a way that no input ever reaches.
 *   3. **A row that duplicates another.** Row 2 must be a *different* block from row 1, which is
 *      only guaranteed by the `id != primary.entryId` filter.
 */
class WidgetScheduleTest {

    private val date: LocalDate = LocalDate.of(2026, 10, 7)

    private fun at(hour: Int, minute: Int = 0): LocalDateTime = date.atTime(hour, minute)

    /**
     * Minutes since midnight, spelled as a clock time.
     *
     * `10 * 30` looks like 10:30 and is 05:00, which is exactly the mistake this helper exists to
     * remove — and it is not a silent one: `TimelineEntry` asserts `startMinute < endMinute`, so a
     * mis-typed literal fails the fixture rather than the assertion.
     */
    private fun h(hour: Int, minute: Int = 0): Int = hour * 60 + minute

    private fun entry(
        id: String,
        startMinute: Int,
        endMinute: Int,
        kind: TimelineKind = TimelineKind.TASK,
        title: String = id,
        taskClosed: Boolean = false,
        on: LocalDate = date,
    ): TimelineEntry = TimelineEntry(
        id = id,
        title = title,
        kind = kind,
        date = on,
        startMinute = startMinute,
        endMinute = endMinute,
        taskClosed = taskClosed,
    )

    private fun course(id: String, startMinute: Int, endMinute: Int, title: String = id) =
        entry(id, startMinute, endMinute, kind = TimelineKind.COURSE, title = title)

    private fun schedule(state: WidgetState): WidgetState.Schedule = state as WidgetState.Schedule

    private fun lines(state: WidgetState): List<WidgetLine> = schedule(state).lines

    // ------------------------------------------------------------------ the three states

    @Test
    fun `an empty day has nothing to show`() {
        assertEquals(WidgetState.Empty, widgetState(emptyList(), at(9)))
    }

    @Test
    fun `a day whose blocks have all passed reads as done`() {
        // Course blocks, deliberately: a lecture that finished is over, not missed. If the widget
        // treated it as missed it would announce 已超时 for a lecture the user attended.
        val state = widgetState(listOf(course("c1", 8 * 60, 9 * 60)), at(23))

        assertEquals(WidgetState.Done, state)
    }

    @Test
    fun `a day whose tasks are all closed reads as done`() {
        val closed = entry("t1", 10 * 60, 11 * 60, taskClosed = true)

        assertEquals(WidgetState.Done, widgetState(listOf(closed), at(10, 30)))
    }

    // ------------------------------------------------------------------ row 1: what now

    @Test
    fun `a block in progress is the first row and reads as now`() {
        val state = widgetState(listOf(course("c1", 9 * 60, 11 * 60, title = "数据结构")), at(10))

        assertEquals(1, lines(state).size)
        assertEquals(WidgetEmphasis.NOW, lines(state)[0].emphasis)
        assertEquals("数据结构", lines(state)[0].title)
        assertEquals(9 * 60, lines(state)[0].startMinute)
        assertEquals(11 * 60, lines(state)[0].endMinute)
    }

    @Test
    fun `a block starting exactly now is the current row`() {
        val state = widgetState(listOf(course("c1", 9 * 60, 10 * 60)), at(9))

        assertEquals(WidgetEmphasis.NOW, lines(state)[0].emphasis)
    }

    @Test
    fun `a block ending exactly now is no longer the current row`() {
        // 09:00 is the end of the first block and the start of the second, so the boundary is
        // walked from both sides in one assertion: the first is over, the second is current.
        val state = widgetState(
            listOf(course("c1", 8 * 60, 9 * 60), course("c2", 9 * 60, 10 * 60)),
            at(9),
        )

        assertEquals(listOf("c2"), lines(state).map(WidgetLine::entryId))
        assertEquals(WidgetEmphasis.NOW, lines(state)[0].emphasis)
    }

    // ------------------------------------------------------------------ row 1: what next

    @Test
    fun `a day whose only block is still ahead shows it as next`() {
        val state = widgetState(listOf(course("c1", 10 * 60, 11 * 60)), at(9))

        assertEquals(WidgetEmphasis.NEXT, lines(state)[0].emphasis)
        assertEquals("c1", lines(state)[0].entryId)
        assertEquals(0, schedule(state).remainingCount)
    }

    @Test
    fun `the earliest of several blocks ahead leads, and the footer counts the rest`() {
        val state = widgetState(
            listOf(course("c3", 14 * 60, 15 * 60), course("c1", 10 * 60, 11 * 60), course("c2", 12 * 60, 13 * 60)),
            at(9),
        )

        assertEquals(listOf("c1", "c2"), lines(state).map(WidgetLine::entryId))
        // c3 is the only one left over: the two rows have already used c1 and c2.
        assertEquals(1, schedule(state).remainingCount)
    }

    @Test
    fun `the block after the current one is the second row`() {
        val state = widgetState(
            listOf(course("c1", 8 * 60, 9 * 60), course("c2", 10 * 60, 11 * 60)),
            at(8, 30),
        )

        assertEquals(listOf("c1", "c2"), lines(state).map(WidgetLine::entryId))
        assertEquals(listOf(WidgetEmphasis.NOW, WidgetEmphasis.NEXT), lines(state).map(WidgetLine::emphasis))
    }

    @Test
    fun `the second row is never the same block as the first`() {
        // The filter is `id != primary.entryId`, and without it a day whose first block is still
        // ahead would draw that block twice — once as 下一项 and once as 下一项 again.
        val state = widgetState(
            listOf(course("c1", 10 * 60, 11 * 60), course("c2", 12 * 60, 13 * 60)),
            at(9),
        )

        assertEquals(2, lines(state).size)
        assertEquals(2, lines(state).map(WidgetLine::entryId).distinct().size)
    }

    @Test
    fun `a closed task is not named even while its window is open`() {
        val state = widgetState(
            listOf(entry("t1", 10 * 60, 11 * 60, taskClosed = true), course("c2", 12 * 60, 13 * 60)),
            at(10, 30),
        )

        assertEquals(listOf("c2"), lines(state).map(WidgetLine::entryId))
    }

    // ------------------------------------------------------------------ row 1: what is late

    @Test
    fun `an open work block whose window has passed is shown as late`() {
        val state = widgetState(listOf(entry("t1", 8 * 60, 9 * 60, title = "写实验报告")), at(9, 30))

        assertEquals(WidgetEmphasis.LATE, lines(state)[0].emphasis)
        assertEquals("写实验报告", lines(state)[0].title)
        assertEquals(0, schedule(state).remainingCount)
    }

    @Test
    fun `a passed course block is never shown as late`() {
        // The whole reason `late` is gated on TimelineKind.TASK. A lecture is not "done", so its
        // entry is never closed, so its window passing makes it OVERRUN forever — and a widget
        // that said 已超时 for every lecture of the day would be telling the user nothing.
        val state = widgetState(listOf(course("c1", 8 * 60, 9 * 60)), at(9, 30))

        assertEquals(WidgetState.Done, state)
    }

    @Test
    fun `a passed work block is not shown as late while a block is still running`() {
        val state = widgetState(
            listOf(entry("missed", 8 * 60, 9 * 60), entry("running", 10 * 60, 11 * 60)),
            at(10, 30),
        )

        assertEquals(listOf("running"), lines(state).map(WidgetLine::entryId))
        assertEquals(WidgetEmphasis.NOW, lines(state)[0].emphasis)
    }

    @Test
    fun `a passed work block is not shown as late while a block is still ahead`() {
        val state = widgetState(
            listOf(entry("missed", 8 * 60, 9 * 60), entry("ahead", 10 * 60, 11 * 60)),
            at(9, 30),
        )

        assertEquals(listOf("ahead"), lines(state).map(WidgetLine::entryId))
        assertEquals(WidgetEmphasis.NEXT, lines(state)[0].emphasis)
    }

    @Test
    fun `the longest-missed work block is the one named`() {
        val state = widgetState(
            listOf(entry("later", 8 * 60, 9 * 60), entry("earlier", 7 * 60, 8 * 60)),
            at(9, 30),
        )

        assertEquals(listOf("earlier"), lines(state).map(WidgetLine::entryId))
    }

    // ------------------------------------------------------------------ the footer

    @Test
    fun `the footer counts only the blocks that are not already on screen`() {
        val state = widgetState(
            listOf(
                course("c1", 8 * 60, 9 * 60),
                entry("t1", 10 * 60, 11 * 60),
                entry("t2", 12 * 60, 13 * 60),
                entry("t3", 14 * 60, 15 * 60),
            ),
            at(8, 30),
        )

        assertEquals(listOf("c1", "t1"), lines(state).map(WidgetLine::entryId))
        assertEquals(2, schedule(state).remainingCount)
    }

    @Test
    fun `a closed task does not count towards the footer`() {
        val state = widgetState(
            listOf(entry("closed", 10 * 60, 11 * 60, taskClosed = true), entry("open", 12 * 60, 13 * 60)),
            at(9),
        )

        assertEquals(listOf("open"), lines(state).map(WidgetLine::entryId))
        assertEquals(0, schedule(state).remainingCount)
    }

    // ------------------------------------------------------------------ determinism

    @Test
    fun `two blocks in progress pick the one the timeline would sort first`() {
        // Parallel tracks are normal. The day timeline sorts by (start, end, id); the widget uses
        // the same comparator, so it names the same card the user is looking at.
        val state = widgetState(
            listOf(entry("long", h(10), h(11)), entry("short", h(10), h(10, 30))),
            at(10, 10),
        )

        assertEquals(listOf("short"), lines(state).map(WidgetLine::entryId))
    }

    @Test
    fun `two identical slots break the tie on the id`() {
        val forward = widgetState(
            listOf(entry("a", 10 * 60, 11 * 60), entry("b", 10 * 60, 11 * 60)),
            at(10, 10),
        )
        val reversed = widgetState(
            listOf(entry("b", 10 * 60, 11 * 60), entry("a", 10 * 60, 11 * 60)),
            at(10, 10),
        )

        assertEquals(listOf("a"), lines(forward).map(WidgetLine::entryId))
        assertEquals(lines(forward), lines(reversed))
    }

    @Test
    fun `the rows do not depend on the order the entries arrive in`() {
        val blocks = listOf(
            course("c1", 8 * 60, 9 * 60),
            entry("t1", 10 * 60, 11 * 60),
            entry("t2", 12 * 60, 13 * 60),
        )

        assertEquals(
            widgetState(blocks, at(8, 30)),
            widgetState(blocks.reversed(), at(8, 30)),
        )
    }

    // ------------------------------------------------------------------ nextRefreshAt

    @Test
    fun `the next refresh is the next block boundary`() {
        val entries = listOf(entry("t1", 10 * 60, 11 * 60))

        assertEquals(at(10), nextRefreshAt(entries, at(9)))
    }

    @Test
    fun `a boundary that has already passed is skipped`() {
        val entries = listOf(entry("t1", 8 * 60, 9 * 60), entry("t2", 10 * 60, 11 * 60))

        assertEquals(at(10), nextRefreshAt(entries, at(9, 30)))
    }

    @Test
    fun `a refresh is never armed in the past`() {
        // The fixture deliberately contains a boundary that has already passed, because a refresh
        // armed in the past fires immediately and the widget would then re-arm itself in a loop.
        val now = at(9, 59)

        val next = nextRefreshAt(listOf(entry("past", h(8), h(9)), entry("t1", h(10), h(11))), now)

        assertTrue("$next must be after $now, or the alarm fires immediately and loops", next.isAfter(now))
    }

    @Test
    fun `a running block's next boundary is its own end`() {
        // The end is the boundary that turns 正在进行 into 已超时. A refresh that only watched starts
        // would leave the widget insisting the user is in a block that finished hours ago.
        assertEquals(at(11), nextRefreshAt(listOf(entry("t1", h(10), h(11))), at(10, 30)))
    }

    @Test
    fun `the refresh lands exactly on the boundary`() {
        // Not a minute after: at exactly 10:00 the block is already current, so the redraw that
        // makes it current has to happen at 10:00.
        assertEquals(at(10), nextRefreshAt(listOf(entry("t1", 10 * 60, 11 * 60)), at(9, 59)))
    }

    @Test
    fun `the next refresh falls back to midnight when the day is over`() {
        assertEquals(
            date.plusDays(1).atStartOfDay(),
            nextRefreshAt(listOf(entry("t1", 8 * 60, 9 * 60)), at(9, 30)),
        )
    }

    @Test
    fun `the next refresh falls back to midnight on an empty day`() {
        assertEquals(date.plusDays(1).atStartOfDay(), nextRefreshAt(emptyList(), at(9)))
    }

    @Test
    fun `midnight wins over a boundary on a later day`() {
        // The caller only ever passes today's entries, but a boundary from another day must not
        // push the redraw past the date change — the widget would spend the night showing
        // yesterday and never correct itself.
        val tomorrow = entry("t1", 8 * 60, 9 * 60, on = date.plusDays(1))

        assertEquals(date.plusDays(1).atStartOfDay(), nextRefreshAt(listOf(tomorrow), at(9)))
    }

    // ------------------------------------------------------------------ the state's own invariants

    @Test
    fun `a schedule state refuses to exist with no rows`() {
        // Empty is a state, not an empty list: the two empty cases need different words.
        assertThrows(IllegalArgumentException::class.java) {
            WidgetState.Schedule(lines = emptyList(), remainingCount = 0)
        }
    }

    @Test
    fun `a schedule state refuses more rows than the widget draws`() {
        val row = WidgetLine("x", WidgetEmphasis.NEXT, "x", 0, 60)

        assertThrows(IllegalArgumentException::class.java) {
            WidgetState.Schedule(lines = List(WIDGET_LINE_LIMIT + 1) { row }, remainingCount = 0)
        }
    }
}
