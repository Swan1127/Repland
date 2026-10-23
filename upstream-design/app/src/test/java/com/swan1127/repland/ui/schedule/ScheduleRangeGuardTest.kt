package com.swan1127.repland.ui.schedule

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Today tab's range selector, and the screen that used to hide behind its last branch.
 *
 * **The defect this file exists to prevent.** `ScheduleRange` declared four members, but only three
 * of them were reachable: `OVERVIEW` was never assigned by any chip. The `when` that read the range
 * ended in `else { DailyDesk(...) }`, so the dead member fell into a whole screen — `DailyDesk.kt`,
 * 155 lines, written in the initial commit and rendering nowhere for the entire life of the app.
 * The `if (scheduleRange != ScheduleRange.OVERVIEW)` guard just below it was true for every value
 * the app could hold, which is the same defect wearing a second coat.
 *
 * **Why this is a source guard and not a unit test.** The range is Compose state inside a
 * composable; this project has no Robolectric and no Compose test, so "which values can the chips
 * set" and "which values does the branch table handle" are answerable only from the text. Both are
 * read out of `ReplandApp.kt` rather than listed by hand, for the same reason [FocalCardResourceTest]
 * parses its composable: a hand-written list goes stale silently, which is the failure this file
 * exists to catch.
 *
 * **The three sets it compares.** `ScheduleRange`'s declared members, the values the `FilterChip`s
 * can set, and the values the `when` has a branch for. The invariant is that all three are equal.
 * A member with no chip is the original defect; a chip with no branch is a compile error today, and
 * this is what says so out loud rather than leaving it to a compiler message nobody reads.
 *
 * **What it does not prove.** That the chips render or respond to a tap (no Compose test); that the
 * three ranges are the right three, which is a product judgement; and that no *live* code calls
 * `DailyDesk`. That last one needs no assertion — the symbol is gone, so any surviving call site or
 * import is a compile error, and the compiler is a better guard than a regex. What the compiler
 * cannot see is a re-added file that nothing calls yet, which is exactly what
 * `the superseded Today screen is gone` checks.
 */
class ScheduleRangeGuardTest {

    private val appSource = File("src/main/java/com/swan1127/repland/ui/ReplandApp.kt")
    private val appText: String by lazy { appSource.readText() }

    /**
     * Where the superseded screen lived. Reached through the directory junction described in
     * `app/build.gradle.kts`, so this is the same tree the app compiles.
     */
    private val dailyDeskSource = File("src/main/java/com/swan1127/repland/ui/schedule/DailyDesk.kt")

    /** The members `private enum class ScheduleRange { … }` declares. */
    private val declaredRanges: Set<String> by lazy {
        val body = ENUM_DECLARATION.find(appText)?.groupValues?.get(1) ?: return@lazy emptySet()
        body.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /**
     * The values a `FilterChip` can set.
     *
     * Read off the chip's `selected = scheduleRange == ScheduleRange.X` rather than its `onClick`,
     * because a chip that offers a value also selects on it — and the `onClick` form appears
     * elsewhere too (`onOpenSchedule`, `onSelectDate`), so matching it would fold in values that no
     * chip offers.
     */
    private val chipRanges: Set<String> by lazy {
        CHIP_SELECTED.findAll(appText).map { it.groupValues[1] }.toSet()
    }

    /** The values the `when (scheduleRange)` has an explicit branch for. */
    private val branchRanges: Set<String> by lazy {
        BRANCH.findAll(appText).map { it.groupValues[1] }.toSet()
    }

    @Before
    fun requireSources() {
        // Without this, a wrong working directory shows up as a handful of empty-set assertions
        // passing. Gradle runs unit tests from the app module directory.
        assertTrue(
            "missing ${appSource.absolutePath} — the enum, the chips and the branch table are all " +
                "read from it, so an unreadable source would make this file pass on nothing",
            appSource.isFile,
        )
    }

    // ------------------------------------------------------------------ anti-vacuity

    @Test
    fun `the three sets are actually being parsed out of the source`() {
        // Every assertion below compares these three sets, so a regex that stopped matching would
        // make two of them empty and the third… also empty, and `emptySet == emptySet` is green.
        // Naming the sizes is what turns "the parse broke" from a silent pass into a failure.
        assertTrue("no `ScheduleRange` members parsed from the enum declaration", declaredRanges.isNotEmpty())
        assertTrue("no chip values parsed from the range selector", chipRanges.isNotEmpty())
        assertTrue("no branches parsed from the `when (scheduleRange)`", branchRanges.isNotEmpty())
        assertEquals(
            "the chip selector and the branch table should both be found in the same file as the enum",
            declaredRanges.size,
            chipRanges.size,
        )
    }

    // ------------------------------------------------------------------ the defect

    @Test
    fun `every range the enum declares can be reached by a chip`() {
        // The original defect, as an assertion: `OVERVIEW` existed in the enum and no chip could
        // produce it, so its branch was reachable only from a value nothing wrote.
        assertEquals(
            "a range no chip can set is a branch nothing can enter",
            emptySet<String>(),
            declaredRanges - chipRanges,
        )
    }

    @Test
    fun `every range a chip can set has a branch`() {
        // The other direction. Today a missing branch is a Kotlin compile error, but this test is
        // what makes the requirement explicit in the suite rather than implicit in a compiler
        // message, and it is what would catch the branch table being replaced by an `else` again.
        assertEquals(
            "a range with no branch would fall through to whatever an `else` held",
            emptySet<String>(),
            chipRanges - branchRanges,
        )
    }

    @Test
    fun `the range list has not grown without a decision here`() {
        // The sets above only have to agree *with each other*; three members could agree as easily
        // as four. Pinning the list outright means a fourth range cannot be added — not even
        // consistently, with its own chip and its own branch — without someone editing this line.
        // That is the point: the fourth member is what went wrong, not the inconsistency.
        assertEquals(
            "the Today tab's range selector changed shape — was that deliberate?",
            setOf("DAY", "WEEK", "MONTH"),
            declaredRanges,
        )
    }

    @Test
    fun `the branch table has no else to fall through to`() {
        // An `else` is how the dead member was absorbed: with a catch-all, adding a member without a
        // chip is silent, and the `when` keeps compiling. Scoped to the block by balancing braces,
        // because this 5,000-line file has other `when`s that use `else` legitimately.
        val block = bracedBlockAfter("when (scheduleRange)")
        assertTrue(
            "the `when (scheduleRange)` block was not located — this guard is scanning the wrong text",
            block.isNotEmpty() && block.contains("ScheduleRange."),
        )
        assertTrue(
            "an `else` here lets a new range fall through silently: $block",
            !block.contains("else ->"),
        )
    }

    @Test
    fun `the always-true range guard is gone`() {
        // `if (scheduleRange != ScheduleRange.OVERVIEW)` was true for every value the app could
        // hold, so the daily-review button below it was unconditional in everything but name. The
        // pattern is refused outright: a range comparison may only ever be an equality against a
        // value some chip can set, never an inequality used as a filter.
        assertTrue(
            "a range inequality is a guard that cannot fail: " +
                appText.lines().filter { "scheduleRange != " in it }.joinToString(" | "),
            !appText.contains("scheduleRange != "),
        )
    }

    @Test
    fun `the daily review is offered under every range`() {
        // The button the always-true guard wrapped. It has to appear inside `TodayScreen` outside
        // the branch table — the previous defect was that its condition *looked* like it varied
        // while never varying. Asserting it is present in the screen and that no range inequality
        // survives (above) is the strongest statement this project can make without a Compose test.
        val screen = todayScreenSource()
        assertTrue(
            "the `TodayScreen` body was not located — this guard is scanning the wrong text",
            screen.isNotEmpty() && screen.contains("when (scheduleRange)"),
        )
        assertTrue(
            "the daily-review entry point should be reachable from the Today tab",
            screen.contains("open_daily_review"),
        )
    }

    @Test
    fun `the superseded Today screen is gone`() {
        // `DailyDesk` was the pre-IA-refactor Today screen: a second, weaker copy of what
        // `TimelineDashboard` and the focal card now cover. Its file compiling while nothing calls
        // it is invisible to the compiler, which is the one hole this test fills. A re-added call
        // site would not need checking — with the symbol deleted it would not compile.
        assertTrue(
            "${dailyDeskSource.absolutePath} is back — it was superseded by TimelineDashboard and " +
                "the focal card, and re-adding it puts a duplicate of both at the top of Today",
            !dailyDeskSource.isFile,
        )
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The `{ … }` block that follows the given anchor, taken by balancing braces.
     *
     * Deliberately not `substringAfter(anchor).substringBefore("\n        }")`: that stops at the
     * first `}` at one guessed indentation, which would silently return a *prefix* of the block if
     * the body were reindented — and a truncated block still passes a `!contains("else ->")` check.
     * A guard that shrinks its own scope when the code is reformatted reports green while seeing
     * less, which is worse than having none.
     */
    private fun bracedBlockAfter(anchor: String): String {
        val anchorAt = appText.indexOf(anchor)
        if (anchorAt < 0) return ""
        var depth = 0
        var opened = -1
        for (index in anchorAt until appText.length) {
            when (appText[index]) {
                '{' -> {
                    if (depth == 0) opened = index
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0) return appText.substring(opened, index + 1)
                }
            }
        }
        return ""
    }

    /**
     * The body of `TodayScreen`, sliced out of the 5,000-line app file.
     *
     * From the function declaration to the next `@Composable`, which is the next declaration in the
     * file. A heuristic rather than a parse — and it fails loudly rather than quietly: the slice is
     * asserted to contain the branch table, so a rename reddens the test instead of emptying it.
     */
    private fun todayScreenSource(): String {
        val start = appText.indexOf("private fun TodayScreen(")
        if (start < 0) return ""
        val end = appText.indexOf("@Composable", start)
        return if (end < 0) appText.substring(start) else appText.substring(start, end)
    }

    private companion object {
        val ENUM_DECLARATION = Regex("""private enum class ScheduleRange\s*\{([^}]*)\}""")

        // `selected = scheduleRange == ScheduleRange.X`, the chip's own offer. The `onClick` form is
        // not matched on purpose: it is also used by `onOpenSchedule` and `onSelectDate`, which set
        // a value that no chip in the row offers.
        val CHIP_SELECTED = Regex("""FilterChip\(selected = scheduleRange == ScheduleRange\.([A-Za-z0-9_]+)""")

        // `ScheduleRange.X ->`, which only the branch table writes.
        val BRANCH = Regex("""ScheduleRange\.([A-Za-z0-9_]+)\s*->""")
    }
}
