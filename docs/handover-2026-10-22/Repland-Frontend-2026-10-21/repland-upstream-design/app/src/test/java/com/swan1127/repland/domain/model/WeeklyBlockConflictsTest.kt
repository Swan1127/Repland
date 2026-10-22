package com.swan1127.repland.domain.model

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WeeklyBlockConflicts] reports the rows the timeline draws as if they were deliberate.
 *
 * The whole value of this rule is that it stays quiet when it should: the week view resolves an
 * overlap by giving each row its own lane, so a false report is not a cosmetic bug — it is the
 * banner telling the user to fix something that is already correct, which is how a warning gets
 * trained out of a user. Most of the cases below are therefore "no conflict", and each one names
 * the specific reason it is not a clash.
 */
class WeeklyBlockConflictsTest {

    private fun find(blocks: List<WeeklyTimeBlock>) = WeeklyBlockConflicts.find(blocks)

    @Test
    fun `no blocks means no conflicts`() {
        assertTrue(find(emptyList()).isEmpty())
    }

    @Test
    fun `a lone block never conflicts with itself`() {
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600),
        )
        assertTrue(find(blocks).isEmpty())
    }

    @Test
    fun `two rows overlapping in one lane on one day conflict`() {
        val blocks = listOf(
            weeklyBlock(id = "a", title = "高等数学", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600),
            weeklyBlock(id = "b", title = "大学英语", kind = TimeBlockKind.COURSE, startMinute = 540, endMinute = 660),
        )
        val conflicts = find(blocks)
        assertEquals(1, conflicts.size)
        val conflict = conflicts.single()
        assertEquals(listOf("a", "b"), listOf(conflict.first.id, conflict.second.id))
        assertEquals(DayOfWeek.MONDAY, conflict.dayOfWeek)
    }

    @Test
    fun `the reported range is the overlap, not either row's own span`() {
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600),
            weeklyBlock(id = "b", kind = TimeBlockKind.COURSE, startMinute = 540, endMinute = 660),
        )
        val conflict = find(blocks).single()
        assertEquals(540, conflict.startMinute)
        assertEquals(600, conflict.endMinute)
    }

    @Test
    fun `a row inside another reports the inner span`() {
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 660),
            weeklyBlock(id = "b", kind = TimeBlockKind.COURSE, startMinute = 540, endMinute = 600),
        )
        val conflict = find(blocks).single()
        assertEquals(540, conflict.startMinute)
        assertEquals(600, conflict.endMinute)
    }

    @Test
    fun `two rows on different lanes are parallel, not in conflict`() {
        // The lane is the app's own way of saying "these really do run at the same time".
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600, trackId = "math"),
            weeklyBlock(id = "b", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600, trackId = "english"),
        )
        assertTrue(find(blocks).isEmpty())
    }

    @Test
    fun `rows that merely touch do not conflict`() {
        // Back-to-back classes are the normal shape of a day, not a mistake.
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 540),
            weeklyBlock(id = "b", kind = TimeBlockKind.COURSE, startMinute = 540, endMinute = 600),
        )
        assertTrue(find(blocks).isEmpty())
    }

    @Test
    fun `rows on different days never conflict`() {
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, dayOfWeek = DayOfWeek.MONDAY, startMinute = 480, endMinute = 600),
            weeklyBlock(id = "b", kind = TimeBlockKind.COURSE, dayOfWeek = DayOfWeek.TUESDAY, startMinute = 480, endMinute = 600),
        )
        assertTrue(find(blocks).isEmpty())
    }

    @Test
    fun `an odd-week class and an even-week class at the same hour do not conflict`() {
        // The common Chinese timetable shape: same hour, alternating weeks.
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600, weekPattern = "1-16周(单)"),
            weeklyBlock(id = "b", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600, weekPattern = "1-16周(双)"),
        )
        assertTrue(find(blocks).isEmpty())
    }

    @Test
    fun `classes in disjoint week ranges do not conflict`() {
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600, weekPattern = "1-8周"),
            weeklyBlock(id = "b", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600, weekPattern = "9-16周"),
        )
        assertTrue(find(blocks).isEmpty())
    }

    @Test
    fun `an unrecognised week pattern stays active and still conflicts`() {
        // Fail-safe: a mis-parse must not be able to hide a real clash.
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600, weekPattern = "待定"),
            weeklyBlock(id = "b", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600, weekPattern = "1-16周"),
        )
        assertEquals(1, find(blocks).size)
    }

    @Test
    fun `a rest row and a course row are not in conflict even when the stored lane matches`() {
        // Both rows carry the stored default trackId "course", but the timeline draws a rest row in
        // the rest lane. Comparing the raw trackId would report a pair the user sees side by side.
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.REST, startMinute = 480, endMinute = 600),
            weeklyBlock(id = "b", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600),
        )
        assertTrue(find(blocks).isEmpty())
    }

    @Test
    fun `two rest rows at the same hour do conflict`() {
        // The positive control for the case above: rest rows are not exempt, they merely live in
        // their own lane.
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.REST, startMinute = 480, endMinute = 600),
            weeklyBlock(id = "b", kind = TimeBlockKind.REST, startMinute = 540, endMinute = 660),
        )
        assertEquals(1, find(blocks).size)
    }

    @Test
    fun `an available template still counts as a claim on the week`() {
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.AVAILABLE, startMinute = 480, endMinute = 600),
            weeklyBlock(id = "b", kind = TimeBlockKind.OTHER, startMinute = 540, endMinute = 660),
        )
        assertEquals(1, find(blocks).size)
    }

    @Test
    fun `three rows in one lane report every overlapping pair`() {
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600),
            weeklyBlock(id = "b", kind = TimeBlockKind.COURSE, startMinute = 540, endMinute = 660),
            weeklyBlock(id = "c", kind = TimeBlockKind.COURSE, startMinute = 570, endMinute = 690),
        )
        val pairs = find(blocks).map { listOf(it.first.id, it.second.id) }
        assertEquals(listOf(listOf("a", "b"), listOf("a", "c"), listOf("b", "c")), pairs)
    }

    @Test
    fun `the pair order does not depend on the input order`() {
        val forwards = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600),
            weeklyBlock(id = "b", kind = TimeBlockKind.COURSE, startMinute = 540, endMinute = 660),
        )
        val backwards = forwards.reversed()
        assertEquals(
            find(forwards).map { listOf(it.first.id, it.second.id) },
            find(backwards).map { listOf(it.first.id, it.second.id) },
        )
    }

    @Test
    fun `a tie on the contested start is broken by the contested end`() {
        // Three rows all in one lane, all overlapping. The pairs (a,c) and (b,c) share a contested
        // start of 520, so only the contested *end* separates them — and the order the pairs happen
        // to fall out of the search is the opposite one. A banner that reshuffled here would look
        // like it was reacting to something the user did.
        val blocks = listOf(
            weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 1000),
            weeklyBlock(id = "b", kind = TimeBlockKind.COURSE, startMinute = 500, endMinute = 600),
            weeklyBlock(id = "c", kind = TimeBlockKind.COURSE, startMinute = 520, endMinute = 700),
        )
        assertEquals(
            listOf(listOf("a", "b"), listOf("b", "c"), listOf("a", "c")),
            find(blocks).map { listOf(it.first.id, it.second.id) },
        )
    }

    @Test
    fun `the result is ordered by day, then by the contested start`() {
        val blocks = listOf(
            weeklyBlock(id = "wed-late", kind = TimeBlockKind.COURSE, dayOfWeek = DayOfWeek.WEDNESDAY, startMinute = 900, endMinute = 1000),
            weeklyBlock(id = "wed-early-b", kind = TimeBlockKind.COURSE, dayOfWeek = DayOfWeek.WEDNESDAY, startMinute = 480, endMinute = 600),
            weeklyBlock(id = "mon", kind = TimeBlockKind.COURSE, dayOfWeek = DayOfWeek.MONDAY, startMinute = 600, endMinute = 700),
            weeklyBlock(id = "wed-late-2", kind = TimeBlockKind.COURSE, dayOfWeek = DayOfWeek.WEDNESDAY, startMinute = 960, endMinute = 1020),
            weeklyBlock(id = "wed-early-a", kind = TimeBlockKind.COURSE, dayOfWeek = DayOfWeek.WEDNESDAY, startMinute = 480, endMinute = 600),
            weeklyBlock(id = "mon-2", kind = TimeBlockKind.COURSE, dayOfWeek = DayOfWeek.MONDAY, startMinute = 660, endMinute = 720),
        )
        val conflicts = find(blocks)
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.WEDNESDAY),
            conflicts.map(WeeklyBlockConflict::dayOfWeek),
        )
        assertEquals(listOf(660, 480, 960), conflicts.map(WeeklyBlockConflict::startMinute))
    }

    // ---------------------------------------------------------------- the shared lane mapping
    @Test
    fun `a course row keeps its own track as its lane`() {
        val block = weeklyBlock(id = "a", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600, trackId = "math")
        assertEquals("math", block.laneKey())
    }

    @Test
    fun `a rest row is drawn in the rest lane whatever track it stores`() {
        val block = weeklyBlock(id = "a", kind = TimeBlockKind.REST, startMinute = 480, endMinute = 600, trackId = "math")
        assertEquals("rest", block.laneKey())
    }

    @Test
    fun `every other kind shares the fixed lane`() {
        assertEquals("fixed", weeklyBlock(id = "a", kind = TimeBlockKind.AVAILABLE, startMinute = 480, endMinute = 600).laneKey())
        assertEquals("fixed", weeklyBlock(id = "a", kind = TimeBlockKind.OTHER, startMinute = 480, endMinute = 600).laneKey())
    }

    /**
     * The two channels that must agree, checked against each other rather than against a literal.
     *
     * The rule compares `laneKey()`; the timeline publishes the lane it draws into
     * `TimelineEntry.trackId`. A hand-written expectation on either side would still pass if the
     * two drifted apart — and drift means the banner reports a clash for a pair the user is looking
     * at side by side. Only a comparison between the two catches that.
     */
    @Test
    fun `the rule's lane is the lane the timeline actually draws`() {
        val kinds = TimeBlockKind.entries
        val blocks = kinds.mapIndexed { index, kind ->
            weeklyBlock(
                id = "b$index",
                kind = kind,
                dayOfWeek = DayOfWeek.MONDAY,
                startMinute = 480 + index * 10,
                endMinute = 600 + index * 10,
                trackId = "math",
            )
        }
        val drawn = ScheduleTimeline.entries(
            date = TEST_MONDAY,
            weeklyBlocks = blocks,
            dateOverrides = emptyList(),
            segments = emptyList(),
            tasks = emptyList(),
            semesterFirstWeekMonday = null,
        ).associateBy { it.id.removePrefix("weekly:") }

        kinds.forEachIndexed { index, kind ->
            val id = "b$index"
            if (kind == TimeBlockKind.AVAILABLE) {
                // The timeline never draws an availability template, so there is no lane to agree
                // with. Its lane is still defined, because two templates can overlap each other.
                assertNull(drawn[id])
            } else {
                assertEquals(blocks[index].laneKey(), drawn.getValue(id).trackId)
            }
        }
    }
}
