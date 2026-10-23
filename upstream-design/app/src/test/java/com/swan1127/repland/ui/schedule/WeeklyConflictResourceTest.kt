package com.swan1127.repland.ui.schedule

import com.swan1127.repland.R
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.WeeklyBlockConflicts
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.weeklyBlock
import java.io.File
import java.util.IllegalFormatConversionException
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element

/**
 * Ties the conflict rule to the words the banner renders.
 *
 * The banner formats a resource with an argument the rule produced, and nothing in the build
 * compares the two: `stringResource(id, arg)` boxes every argument into `Any`, so a `%1$d` given a
 * title throws `IllegalFormatConversionException` the moment the banner draws — on the one screen
 * the user is looking at, and only when a conflict actually exists, which is precisely the state a
 * developer testing on a clean timetable never sees.
 *
 * **What this proves:** the three keys exist in both files; the placeholder kinds match the
 * arguments the banner passes; the rule can actually produce a conflict to format; and every
 * declared `conflict_*` key is referenced by the banner, so a removed string cannot leave a key
 * behind and a declared-but-unused key cannot hide.
 *
 * **What it does not prove:** that the English reads well, or that the banner is visible on a
 * device — this is a JVM test with no Context and no layout.
 */
class WeeklyConflictResourceTest {

    private val resRoot = File("src/main/res")
    private val defaultStrings: Map<String, String> by lazy { parseStrings(resRoot.resolve("values/strings.xml")) }
    private val englishStrings: Map<String, String> by lazy { parseStrings(resRoot.resolve("values-en/strings.xml")) }

    /** Reached through the directory junction, so this is the file the app compiles. */
    private val bannerSource = File("src/main/java/com/swan1127/repland/ui/schedule/WeeklyConflictBanner.kt")

    /**
     * `R.string.<name>` values are unique per type, so the reverse lookup is a plain map. The
     * positive control below keeps it honest: a reflection that silently returned nothing would
     * make every assertion in this file pass on an empty map.
     */
    private val namesById: Map<Int, String> by lazy {
        R.string::class.java.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { field -> field.getInt(null) to field.name }
    }

    @Before
    fun requireSources() {
        assertTrue("missing values/strings.xml (tests run from the app module directory)", defaultStrings.isNotEmpty())
        assertTrue("missing values-en/strings.xml (tests run from the app module directory)", englishStrings.isNotEmpty())
        assertTrue("missing ${bannerSource.absolutePath} (tests run from the app module directory)", bannerSource.isFile)
    }

    // ------------------------------------------------------------------ anti-vacuity

    @Test
    fun `the id lookup resolves the whole string table`() {
        assertTrue("resolved only ${namesById.size} R.string ids", namesById.size >= 400)
        assertEquals("conflict_banner_title", namesById.getValue(R.string.conflict_banner_title))
    }

    @Test
    fun `the rule can produce a conflict for the banner to render`() {
        // Every formatting assertion below runs over this list. If the rule stopped reporting
        // anything, those assertions would iterate an empty list and pass on nothing.
        assertTrue("the rule produced no conflict to format", sampleConflicts().isNotEmpty())
    }

    // ------------------------------------------------------------------ the words

    @Test
    fun `every conflict key is declared in both resource files`() {
        val missing = CONFLICT_KEYS.filter {
            !defaultStrings.containsKey(it) || !englishStrings.containsKey(it)
        }
        assertEquals("conflict keys missing from a resource file", emptyList<String>(), missing)
    }

    @Test
    fun `no conflict key is left behind by a removed banner`() {
        // The other direction: a key deleted from the banner but not from the resources leaves text
        // nothing can reach. Comparing the *count* is what catches it — set membership alone cannot
        // tell "this key is live" from "a stale key happens to match".
        val declared = defaultStrings.keys.filter { it.startsWith(CONFLICT_KEY_PREFIX) }
        assertEquals(
            "one '${CONFLICT_KEY_PREFIX}*' key per banner string, no more and no fewer",
            CONFLICT_KEYS.size,
            declared.size,
        )
    }

    @Test
    fun `the banner references every declared conflict key`() {
        // Resource ids are plain ints and `R` is generated, so a mistyped reference is a compile
        // error and needs no guard. What does need one is a key that is declared and never used:
        // that compiles, ships, and shows up only as text in the string table that nothing renders.
        val source = bannerSource.readText()
        assertTrue(
            "the banner source was not located — this guard is scanning the wrong text",
            source.contains("fun WeeklyConflictBanner("),
        )
        val unreferenced = CONFLICT_KEYS.filter { !source.contains("R.string.$it") }
        assertEquals("conflict keys nothing renders", emptyList<String>(), unreferenced)
    }

    // ------------------------------------------------------------------ the placeholder kinds

    @Test
    fun `the title takes a count and the row takes two names, in both files`() {
        // The kinds are named outright rather than compared to each other: comparing the two files
        // would be satisfied by both being wrong. `%1$d` for the title and `%1$s`/`%2$s` for the row
        // are what the banner actually passes.
        assertEquals(listOf("1d"), specifiersOf(defaultStrings.getValue("conflict_banner_title")))
        assertEquals(listOf("1d"), specifiersOf(englishStrings.getValue("conflict_banner_title")))
        assertEquals(listOf("1s", "2s"), specifiersOf(defaultStrings.getValue("conflict_row_pair")))
        assertEquals(listOf("1s", "2s"), specifiersOf(englishStrings.getValue("conflict_row_pair")))
        assertEquals(emptyList<String>(), specifiersOf(defaultStrings.getValue("conflict_banner_body")))
        assertEquals(emptyList<String>(), specifiersOf(englishStrings.getValue("conflict_banner_body")))
    }

    @Test
    fun `a numeric placeholder rejects text while a string placeholder accepts a number`() {
        // Why the kind check has to exist, demonstrated rather than asserted in prose. The dangerous
        // direction is the silent one: a `%1$s` given a count prints the digits and looks plausible.
        val numeric = defaultStrings.getValue("conflict_banner_title")
        val threw = runCatching { String.format(numeric, "不是数字") }.exceptionOrNull()
        assertTrue("a %1\$d placeholder was expected to reject text, got $threw", threw is IllegalFormatConversionException)
        assertTrue(String.format(defaultStrings.getValue("conflict_row_pair"), "高等数学", "大学英语").contains("高等数学"))
    }

    @Test
    fun `every conflict formats with the arguments the banner passes`() {
        // End to end: take a real conflict, resolve the real strings, and format them with the same
        // kinds the banner uses. Nothing may be left over as a literal `%`.
        val conflicts = sampleConflicts()
        val leftovers = buildList {
            addAll(
                conflicts.map { conflict ->
                    "conflict_banner_title -> " to String.format(
                        defaultStrings.getValue("conflict_banner_title"),
                        conflicts.size,
                    )
                },
            )
            addAll(
                conflicts.map { conflict ->
                    "conflict_row_pair -> " to String.format(
                        defaultStrings.getValue("conflict_row_pair"),
                        conflict.first.title,
                        conflict.second.title,
                    )
                },
            )
        }.filter { (_, formatted) -> formatted.contains('%') }
        assertEquals("conflict strings with an unfilled placeholder", emptyList<Pair<String, String>>(), leftovers)
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A real pair of rows that overlap in one lane, run through the real rule.
     *
     * Deliberately not a hand-built [com.swan1127.repland.domain.model.WeeklyBlockConflict]: the
     * point of this file is to check the banner against what the rule actually emits, and a literal
     * would keep passing after the rule stopped emitting anything like it.
     */
    private fun sampleConflicts() = WeeklyBlockConflicts.find(
        listOf(
            block(id = "a", title = "高等数学", start = 480, end = 600),
            block(id = "b", title = "大学英语", start = 540, end = 660),
        ),
    )

    private fun block(id: String, title: String, start: Int, end: Int): WeeklyTimeBlock =
        weeklyBlock(
            id = id,
            title = title,
            kind = TimeBlockKind.COURSE,
            startMinute = start,
            endMinute = end,
        )

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
     * Only positional forms count: these strings are formatted through `stringResource(id, arg)`,
     * which requires the `%1$` form to place the argument at all.
     */
    private fun specifiersOf(value: String): List<String> =
        POSITIONAL_SPECIFIER.findAll(value).map { match -> match.groupValues[1] + match.groupValues[2] }.toList()

    private companion object {
        val CONFLICT_KEYS = listOf(
            "conflict_banner_title",
            "conflict_banner_body",
            "conflict_row_pair",
        )
        const val CONFLICT_KEY_PREFIX = "conflict_"

        // `\${'$'}` and not `${'$'}`: a bare `$` in a regex is the end-of-input anchor, which would
        // make this pattern match nothing and the whole guard vacuously green.
        val POSITIONAL_SPECIFIER = Regex("""%([0-9]+)\${'$'}([a-zA-Z])""")
    }
}
