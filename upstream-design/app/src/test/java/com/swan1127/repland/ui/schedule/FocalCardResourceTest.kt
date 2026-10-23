package com.swan1127.repland.ui.schedule

import com.swan1127.repland.R
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.ui.widget.WidgetEmphasis
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.IllegalFormatConversionException
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element

/**
 * Ties the focal card's words to the resource table, and its one call site to the day's full list.
 *
 * **Two guards, because the card has two halves and only one of them is executable.** The decision
 * is a pure function and `TimelineFocusTest` runs it. The rendering is Compose, and this project has
 * no Robolectric and no Compose test, so "which key does the card read" and "what does it hand the
 * rule" are answerable only from the source. Both are read out of `ReplandApp.kt` rather than
 * listed by hand, for the same reason the widget's guard parses its provider: a hard-coded list goes
 * stale silently, which is the failure this file exists to prevent.
 *
 * **Why the resource half matters more here than on a normal screen.** This card is the largest text
 * in the app and the first thing an English-locale user reads on the Today tab. Android *falls back*
 * to `values/` for a missing translation without a warning, so the failure mode is one line of
 * Chinese inside an otherwise English screen — invisible to every developer running the default
 * locale, and invisible to a screenshot taken on a Chinese device.
 *
 * **The three defect classes it hunts:**
 *
 *   1. **A word with two keys.** The card deliberately reuses the widget's three phase words. The
 *      way that goes wrong is someone "fixing" the naming by adding `today_label_now` with the same
 *      text — the resource table's own comment forbids it, and `the card reuses the phase words…`
 *      catches it by comparing rendered *text*, not key names, so it also catches the paste-the-
 *      Chinese-into-a-new-key version.
 *   2. **A label no situation produces.** `已超时` was unreachable for the whole life of the card.
 *      `every label the card can print is reachable through the rule` runs one situation per
 *      emphasis through [focalCard] and fails if the rule stops producing one — the same anti-
 *      vacuity shape as the widget's guard, applied to the surface that actually had the bug.
 *   3. **A call site that filters the day again.** The original defect was one `filter` at the call
 *      site, and no unit test can see a call site. `the call site passes the whole day, unfiltered`
 *      reads the call out of the source and refuses a `.filter` or a `.sortedBy` on it.
 *
 * **What it does not prove:** that the card is ever rendered (no Compose test — the mapping from
 * [FocalCard] to pixels is an open gap); that the English reads well; that the reused keys are the
 * right words for a card as opposed to a widget row, which is a judgement, not an assertion. And it
 * deliberately does not assert that the `when` blocks over [FocalCard] are exhaustive — Kotlin
 * makes that a compile error, so a test for it could only ever pass.
 */
class FocalCardResourceTest {

    private val resRoot = File("src/main/res")
    private val defaultStrings: Map<String, String> by lazy { parseStrings(resRoot.resolve("values/strings.xml")) }
    private val englishStrings: Map<String, String> by lazy { parseStrings(resRoot.resolve("values-en/strings.xml")) }
    private val appSource: File = File("src/main/java/com/swan1127/repland/ui/ReplandApp.kt")
    private val appText: String by lazy { appSource.readText() }

    /**
     * `R.string.<name>` values are unique per type, so the reverse lookup is a plain map.
     *
     * Reflection is the only way from an id back to its name in a JVM test: `R` is a class of ints
     * and the names exist only in the XML. The positive control below keeps it honest — a silently
     * empty map would make every assertion in this file pass on nothing.
     */
    private val namesById: Map<Int, String> by lazy {
        R.string::class.java.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { field -> field.getInt(null) to field.name }
    }

    /** The keys the card itself reads, parsed out of the composable rather than listed by hand. */
    private val renderedKeys: Set<String> by lazy {
        STRING_REFERENCE.findAll(cardSource()).map { match -> match.groupValues[1] }.toSet()
    }

    /**
     * Every key whose text the card can put on screen.
     *
     * The three phase words are missing from the parse on purpose: the card reads them through
     * `state.emphasis.labelRes`, so the id is a variable at the call site and only the enum knows
     * which key it is. This is the same join the widget's guard performs, for the same reason.
     */
    private val allRenderedKeys: Set<String> by lazy {
        renderedKeys + WidgetEmphasis.entries.map { namesById.getValue(it.labelRes) }
    }

    @Before
    fun requireInputs() {
        // Without this, a wrong working directory shows up as a dozen unrelated empty-set
        // assertions passing. Gradle runs unit tests from the app module directory.
        assertTrue("missing values/strings.xml", defaultStrings.isNotEmpty())
        assertTrue("missing values-en/strings.xml", englishStrings.isNotEmpty())
        assertTrue(
            "missing ${appSource.absolutePath} — both the key list and the call-site check are read " +
                "from it, so an unreadable source would make this file pass on nothing",
            appSource.isFile,
        )
        assertTrue(
            "the TodayFocalOverview body was not found in ${appSource.path} — every key below is " +
                "parsed out of it, so a renamed or reordered composable would empty the list",
            cardSource().contains("FocalCard"),
        )
    }

    // ------------------------------------------------------------------ anti-vacuity

    @Test
    fun `the id lookup resolves the whole string table`() {
        assertTrue("resolved only ${namesById.size} R.string ids", namesById.size >= 400)
        assertEquals("today_title", namesById.getValue(R.string.today_title))
        assertEquals("today_parallel_count", namesById.getValue(R.string.today_parallel_count))
    }

    @Test
    fun `the composable source is actually being parsed`() {
        // If the slice stopped finding the function — a rename, a second `@Composable` inserted
        // before the body — the derived key list would empty out and the checks below would pass on
        // nothing. The expected set is named outright so that a genuine change has to be
        // acknowledged here rather than absorbed silently.
        //
        // The three `widget_label_*` entries arrive through `WidgetEmphasis` rather than through the
        // parse: the card reads its label as `state.emphasis.labelRes`, so no `R.string.` literal
        // for them appears in the composable. Asserting the joined set here is what makes that join
        // load-bearing — without this line, deleting it would change no result anywhere in the suite
        // and the card's whole vocabulary would stop being pinned by anything.
        assertEquals(
            setOf(
                "today_parallel_count",
                "today_title",
                "today_unplaced_count",
                "widget_done_title",
                "widget_empty_hint",
                "widget_empty_title",
                "widget_label_late",
                "widget_label_next",
                "widget_label_now",
                "widget_time_range",
            ),
            allRenderedKeys,
        )
    }

    // ------------------------------------------------------------------ the words

    @Test
    fun `every key the card can render is declared in both resource files`() {
        val missing = allRenderedKeys
            .filter { !defaultStrings.containsKey(it) || !englishStrings.containsKey(it) }
            .sorted()
        assertEquals("focal-card keys missing from a resource file", emptyList<String>(), missing)
    }

    @Test
    fun `the card reuses the phase words instead of declaring its own`() {
        // The resource table says one word must not have two keys. Comparing rendered *text* rather
        // than key names is what makes this catch the two ways it goes wrong: a `today_label_now`
        // pointing at the widget's id, and a `today_label_now` holding a freshly typed 正在进行.
        val cardTexts = renderedKeys.map { defaultStrings.getValue(it) }.toSet()
        val phaseWords = WidgetEmphasis.entries
            .map { defaultStrings.getValue(namesById.getValue(it.labelRes)) }
            .toSet()

        assertEquals(
            "the card declared a second key for a word the widget already has",
            emptyList<String>(),
            cardTexts.intersect(phaseWords).sorted(),
        )
        // Anti-vacuity: an empty `phaseWords` would satisfy the assertion above for free. That the
        // three labels are declared, distinct and reachable is the widget guard's job and is
        // asserted there; this test only refuses a second copy of them.
        assertEquals("the phase vocabulary should be three words", 3, phaseWords.size)
        assertTrue("the card's own texts should not be empty", cardTexts.isNotEmpty())
    }

    @Test
    fun `every label the card can print is reachable through the rule`() {
        // The defect, as an assertion. `已超时` was unreachable for the card's whole life because the
        // call site filtered the block away before the clock ever saw it; a regression that
        // reintroduced a filter inside the rule would drop LATE from this set.
        val day = LocalDate.of(2026, 10, 9)
        fun block(id: String, start: Int, end: Int) = TimelineEntry(
            id = id,
            title = id,
            kind = TimelineKind.TASK,
            date = day,
            startMinute = start,
            endMinute = end,
        )
        fun label(entry: TimelineEntry, at: LocalDateTime) =
            (focalCard(listOf(entry), at) as? FocalCard.Named)?.emphasis

        val produced = setOf(
            label(block("now", 10 * 60, 11 * 60), day.atTime(10, 30)),
            label(block("next", 10 * 60, 11 * 60), day.atTime(9, 0)),
            label(block("late", 8 * 60, 9 * 60), day.atTime(9, 30)),
        )

        assertEquals(
            "an emphasis the rule can never produce carries a label nobody will ever read",
            WidgetEmphasis.entries.toSet(),
            produced,
        )
        val undeclared = produced.filterNotNull()
            .map { namesById.getValue(it.labelRes) }
            .filter { !defaultStrings.containsKey(it) || !englishStrings.containsKey(it) }
        assertEquals("phase labels missing from a resource file", emptyList<String>(), undeclared)
    }

    @Test
    fun `the two states with no block both have their own words`() {
        // Empty and Done are different sentences about different situations, and the card could not
        // say the second one until this fix. Asserting the pair here — rather than only that each
        // key exists — is what makes a "simplification" that points Done at the empty title fail.
        val empty = defaultStrings.getValue("widget_empty_title")
        val done = defaultStrings.getValue("widget_done_title")

        assertTrue("the card renders both empty-state titles", allRenderedKeys.contains("widget_empty_title"))
        assertTrue("the card renders the done title", allRenderedKeys.contains("widget_done_title"))
        assertTrue("the two states must not share one sentence", empty != done)
    }

    // ------------------------------------------------------------------ the call site

    @Test
    fun `the call site passes the whole day, unfiltered`() {
        // No unit test can see a call site, and the original defect was exactly one `filter` here:
        // `timelineEntries.filter { it.endMinute > nowMinute }` removed every block the clock needs
        // in order to say 已超时. The statement is taken by balancing parentheses rather than by
        // taking the line, and compared with all whitespace removed, so wrapping the call across
        // several lines does not quietly disable the check — a guard that switches itself off when
        // the code is reformatted still reports green, which is worse than having none. The count
        // assertion means a second focal card added elsewhere has to come past this test too.
        val calls = Regex("""\bfocalCard\(""").findAll(appText).toList()
        assertEquals("the focal card should be built in exactly one place", 1, calls.size)
        val call = callStatement(calls[0].range.first)
        val squashed = call.replace(Regex("""\s+"""), "")

        assertTrue(
            "a filter at the call site is the defect this rule was extracted to remove: $call",
            !squashed.contains(".filter"),
        )
        assertTrue("the day must not be pre-sorted for the rule either: $call", !squashed.contains(".sortedBy"))
        assertTrue(
            "the first argument must be the day's entries, unfiltered: $call",
            squashed.startsWith("focalCard(timelineEntries,"),
        )
    }

    // ------------------------------------------------------------------ format arguments

    @Test
    fun `the two argument-carrying keys declare exactly one count each`() {
        // `%1$d` and not `%1$s`: a numeric placeholder given a string throws, while a string
        // placeholder given a count prints it silently. The dangerous direction is the silent one,
        // so the kind is named rather than merely compared.
        val expected = listOf("1d")
        assertEquals(expected, specifiersOf(defaultStrings.getValue("today_parallel_count")))
        assertEquals(expected, specifiersOf(defaultStrings.getValue("today_unplaced_count")))
        assertEquals(expected, specifiersOf(englishStrings.getValue("today_parallel_count")))
        assertEquals(expected, specifiersOf(englishStrings.getValue("today_unplaced_count")))
    }

    @Test
    fun `a numeric placeholder rejects text`() {
        val numeric = defaultStrings.getValue("today_parallel_count")
        val threw = runCatching { String.format(numeric, "不是数字") }.exceptionOrNull()
        assertTrue(
            "a %1\$d placeholder was expected to reject text, got $threw",
            threw is IllegalFormatConversionException,
        )
    }

    @Test
    fun `every argument-carrying key formats with nothing left over`() {
        val parallel = String.format(defaultStrings.getValue("today_parallel_count"), 2)
        assertTrue("leftover placeholder in $parallel", !parallel.contains('%'))
        assertTrue("the count went missing from $parallel", parallel.contains("2"))

        val unplaced = String.format(englishStrings.getValue("today_unplaced_count"), 3)
        assertTrue("leftover placeholder in $unplaced", !unplaced.contains('%'))
        assertTrue("the count went missing from $unplaced", unplaced.contains("3"))
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The whole `focalCard(...)` statement, taken by balancing parentheses from its opening one.
     *
     * Deliberately not `lines().single { "focalCard(" in it }`: that would silently stop matching
     * the moment the call is wrapped across two lines, and a guard that turns itself off when the
     * code is reformatted is worse than no guard, because it still reports green.
     */
    private fun callStatement(start: Int): String {
        var depth = 0
        for (index in start until appText.length) {
            when (appText[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return appText.substring(start, index + 1)
                }
            }
        }
        return ""
    }

    /**
     * The body of `TodayFocalOverview`, sliced out of the 5,000-line app file.
     *
     * From the function declaration to the next `@Composable`, which is the next declaration in the
     * file. A heuristic rather than a parse — and it fails loudly rather than quietly: the slice is
     * asserted to contain `FocalCard`, so a rename or a reordering reddens `requireInputs` instead
     * of emptying the derived key list.
     */
    private fun cardSource(): String {
        val start = appText.indexOf("private fun TodayFocalOverview(")
        if (start < 0) return ""
        val end = appText.indexOf("@Composable", start)
        return if (end < 0) appText.substring(start) else appText.substring(start, end)
    }

    private fun parseStrings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                put(element.getAttribute("name"), element.textContent.orEmpty())
            }
        }
    }

    /**
     * The positional conversions a value refers to, normalised to `index + conversion`.
     *
     * Only the `%n$x` form counts: these strings are rendered through `stringResource(id, args...)`,
     * which cannot place an argument any other way.
     */
    private fun specifiersOf(value: String): List<String> =
        POSITIONAL_SPECIFIER.findAll(value).map { match -> match.groupValues[1] + match.groupValues[2] }.toList()

    private companion object {
        val STRING_REFERENCE = Regex("""R\.string\.([A-Za-z0-9_]+)""")

        // `\${'$'}` and not `${'$'}`: a bare `$` in a regex is the end-of-input anchor, which would
        // make this pattern match nothing and the whole guard vacuously green.
        val POSITIONAL_SPECIFIER = Regex("""%([0-9]+)\${'$'}([a-zA-Z])""")
    }
}
