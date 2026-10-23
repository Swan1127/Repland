package com.swan1127.repland.ui.widget

import com.swan1127.repland.R
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
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
 * Ties the widget's text to the resource table it has to come from.
 *
 * **Why the widget needs this more than any screen does.** Every other surface in this app is
 * rendered by Compose, where a missing translation falls back to `values/` and the worst case is
 * one wrong-language line in a screen the user can still read around. A widget is a fixed-size
 * rectangle of text: one line of Chinese in an English-locale widget is not a blemish, it is most
 * of what the user sees. And the widget is the one surface the project cannot test by rendering —
 * there is no Robolectric here and `RemoteViews` needs a real host — so the only thing that can
 * catch a missing key is a source-level check.
 *
 * **What this proves, and why each assertion is derived rather than listed.** The keys the provider
 * renders are *parsed out of the provider source* and the labels are read off [WidgetEmphasis], so
 * neither list can drift from the code it is checking: adding a `getString` to the provider and
 * forgetting `values-en/` fails here without anyone remembering to update this file. A hard-coded
 * list would go stale silently, which is the failure this whole file exists to prevent.
 *
 * It also proves that the two argument-carrying strings declare exactly the arguments the provider
 * fills, and that **no other** string the widget renders declares any. That last direction matters:
 * a message string that grew a `%1$d` the provider never passes would print the literal `%1$d` in
 * the middle of the home screen, and `String.format` is never called on it, so nothing throws.
 *
 * And it proves the labels are not dead: one situation per emphasis is run through the real rule,
 * so a label for a state the rule can no longer produce fails here rather than sitting in the
 * resource table looking translated.
 *
 * **What it does not prove:** that the English reads well; that the widget is ever shown on a real
 * home screen; that `RemoteViews` accepts the layout (that is aapt's job, at build time); or that
 * the launcher renders the widget at the size the metadata asks for. All four are open gaps.
 */
class WidgetScheduleResourceTest {

    private val resRoot = File("src/main/res")
    private val defaultStrings: Map<String, String> by lazy { parseStrings(resRoot.resolve("values/strings.xml")) }
    private val englishStrings: Map<String, String> by lazy { parseStrings(resRoot.resolve("values-en/strings.xml")) }
    private val providerSource: File =
        File("src/main/java/com/swan1127/repland/ui/widget/HomeWidgetProvider.kt")
    private val layout: File = resRoot.resolve("layout/widget_home.xml")

    /**
     * `R.string.<name>` values are unique per type, so the reverse lookup is a plain map.
     *
     * Reflection is the only way from an id back to its name in a JVM test: `R` is a class of ints
     * and the names exist only in the XML. The positive control below is what keeps it honest — a
     * silently empty map would make every assertion in this file pass on nothing.
     */
    private val namesById: Map<Int, String> by lazy {
        R.string::class.java.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { field -> field.getInt(null) to field.name }
    }

    @Before
    fun requireInputs() {
        // Without this, a wrong working directory shows up as a dozen unrelated empty-list
        // assertions passing. Gradle runs unit tests from the app module directory.
        assertTrue("missing values/strings.xml", defaultStrings.isNotEmpty())
        assertTrue("missing values-en/strings.xml", englishStrings.isNotEmpty())
        assertTrue(
            "missing ${providerSource.absolutePath} — the widget's key list is parsed from it, " +
                "so an unreadable source would make every assertion here vacuous",
            providerSource.isFile,
        )
        assertTrue("missing ${layout.path}", layout.isFile)
    }

    // ------------------------------------------------------------------ anti-vacuity

    @Test
    fun `the id lookup resolves the whole string table`() {
        assertTrue("resolved only ${namesById.size} R.string ids", namesById.size >= 400)
        assertEquals("widget_remaining", namesById.getValue(R.string.widget_remaining))
        assertEquals("widget_label_now", namesById.getValue(R.string.widget_label_now))
    }

    @Test
    fun `the provider source is actually being parsed`() {
        // If the regex stopped matching — a rename, a `R.string` written through an alias — the
        // derived key list would empty out and every check below would pass on nothing.
        val keys = keysUsedByProvider()
        assertTrue("parsed no R.string references out of the provider", keys.isNotEmpty())
        assertEquals(
            "the provider no longer references a key this guard checks; update both together",
            setOf(
                "today_title",
                "widget_done_title",
                "widget_empty_hint",
                "widget_empty_title",
                "widget_remaining",
                "widget_time_range",
            ),
            keys,
        )
    }

    // ------------------------------------------------------------------ the labels

    @Test
    fun `every emphasis has its own label declared in both files`() {
        val undeclared = WidgetEmphasis.entries
            .map { it.labelRes }
            .map { namesById.getValue(it) }
            .filter { !defaultStrings.containsKey(it) || !englishStrings.containsKey(it) }
        assertEquals("emphasis labels missing from a resource file", emptyList<String>(), undeclared)
    }

    @Test
    fun `every emphasis can actually appear, and each one has a label`() {
        // The anti-vacuity check for the two tests around it. A label whose emphasis the rule can
        // never produce is dead weight, and `the three labels are three different strings` would
        // still pass with it — so the coverage is asserted, not assumed. All three situations are
        // built from work blocks, which keeps this independent of the rule's task-only gate on
        // 已超时: a course block would produce LATE here for the wrong reason.
        val day = LocalDate.of(2026, 10, 7)
        fun block(id: String, start: Int, end: Int) = TimelineEntry(
            id = id,
            title = id,
            kind = TimelineKind.TASK,
            date = day,
            startMinute = start,
            endMinute = end,
        )
        fun state(entry: TimelineEntry, at: LocalDateTime) = widgetState(listOf(entry), at)

        val produced = listOf(
            state(block("now", 10 * 60, 11 * 60), day.atTime(10, 30)),
            state(block("next", 10 * 60, 11 * 60), day.atTime(9, 0)),
            state(block("late", 8 * 60, 9 * 60), day.atTime(9, 30)),
        ).filterIsInstance<WidgetState.Schedule>()
            .flatMap { it.lines }
            .map { it.emphasis }
            .toSet()

        assertEquals(
            "an emphasis the rule can never produce carries a label nobody will ever read",
            WidgetEmphasis.entries.toSet(),
            produced,
        )
        val undeclared = produced
            .map { namesById.getValue(it.labelRes) }
            .filter { !defaultStrings.containsKey(it) || !englishStrings.containsKey(it) }
        assertEquals("emphasis labels missing from a resource file", emptyList<String>(), undeclared)
    }

    @Test
    fun `the three labels are three different strings`() {
        // A copy-paste that pointed LATE at the NOW key would leave three declared labels that are
        // really two, and the widget would say 正在进行 about a block that is over. Comparing the
        // ids catches the copy-paste; comparing the text catches the same mistake made by pasting
        // the Chinese into a new key.
        val ids = WidgetEmphasis.entries.map { it.labelRes }
        assertEquals("two emphases share one resource id", ids.size, ids.distinct().size)

        val texts = ids.map { namesById.getValue(it) }
        assertEquals("two emphases share one resource key", texts.size, texts.distinct().size)

        val rendered = ids.map { defaultStrings.getValue(namesById.getValue(it)) }
        assertEquals("two emphases render the same words", rendered.size, rendered.distinct().size)
    }

    @Test
    fun `the header reuses the app's own word for today`() {
        // Deliberate reuse rather than a second key: "今天" means the same thing on the Today tab
        // and on the widget over it, and two keys for one word is how a translation ends up
        // disagreeing with itself.
        assertTrue("the widget header should render today_title", keysUsedByProvider().contains("today_title"))
        assertTrue(defaultStrings.containsKey("today_title"))
        assertTrue(englishStrings.containsKey("today_title"))
    }

    // ------------------------------------------------------------------ the guard

    @Test
    fun `every string the widget renders is declared in both resource files`() {
        val keys = keysUsedByProvider() + WidgetEmphasis.entries.map { namesById.getValue(it.labelRes) }
        val missing = keys
            .filter { !defaultStrings.containsKey(it) || !englishStrings.containsKey(it) }
            .sorted()
        assertEquals("widget keys missing from a resource file", emptyList<String>(), missing)
    }

    @Test
    fun `only the two argument-carrying strings declare arguments`() {
        // The keys that legitimately take arguments, and exactly which. Anything else with a
        // placeholder would render it literally: the provider passes no argument to it, so nothing
        // throws and nothing warns — the `%1$d` simply appears on the home screen.
        val expected = mapOf(
            "widget_time_range" to listOf("1s", "2s"),
            "widget_remaining" to listOf("1d"),
        )
        val offenders = keysUsedByProvider().sorted().mapNotNull { key ->
            val specifiers = specifiersOf(defaultStrings.getValue(key))
            if (specifiers == expected[key].orEmpty()) null else "$key declares $specifiers, expected ${expected[key].orEmpty()}"
        }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `the argument-carrying strings agree between the two files`() {
        // Checked here as well as in I18nResourceTest because that test compares the files to each
        // other, which is satisfied by both being wrong in the same way. This one names the
        // expected conversions outright.
        assertEquals(listOf("1s", "2s"), specifiersOf(defaultStrings.getValue("widget_time_range")))
        assertEquals(listOf("1s", "2s"), specifiersOf(englishStrings.getValue("widget_time_range")))
        assertEquals(listOf("1d"), specifiersOf(defaultStrings.getValue("widget_remaining")))
        assertEquals(listOf("1d"), specifiersOf(englishStrings.getValue("widget_remaining")))
    }

    @Test
    fun `a numeric placeholder rejects text while a string placeholder accepts a number`() {
        // Why the kinds have to be named rather than compared. A `%1$d` given a time string throws;
        // a `%1$s` given a count does not, and prints it. The dangerous direction is the silent one.
        val numeric = defaultStrings.getValue("widget_remaining")
        val threw = runCatching { String.format(numeric, "不是数字") }.exceptionOrNull()
        assertTrue("a %1\$d placeholder was expected to reject text, got $threw", threw is IllegalFormatConversionException)

        val tolerant = String.format(defaultStrings.getValue("widget_time_range"), 1, 2)
        assertTrue("a %1\$s placeholder was expected to accept numbers, got $tolerant", tolerant.contains("1"))
    }

    @Test
    fun `every argument-carrying string formats with nothing left over`() {
        // End to end with the same argument kinds the provider passes: two clock strings and one
        // count. Nothing may survive as a literal `%`.
        val range = String.format(defaultStrings.getValue("widget_time_range"), "09:00", "11:00")
        assertEquals("09:00–11:00", range)

        val remaining = String.format(defaultStrings.getValue("widget_remaining"), 3)
        assertTrue("leftover placeholder in $remaining", !remaining.contains('%'))
        assertTrue("the count went missing from $remaining", remaining.contains("3"))
    }

    // ------------------------------------------------------------------ the layout

    @Test
    fun `the widget layout hard-codes no text`() {
        // The layout is inflated by the launcher's process, so its strings cannot go through the
        // provider's `getString`. A literal here is a string that no translation can reach and that
        // no other test looks at, because nothing in this suite renders the layout.
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(layout)
        val offenders = buildList {
            for (name in listOf("TextView", "Button")) {
                val nodes = document.getElementsByTagName(name)
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as Element
                    val text = element.getAttribute("android:text")
                    if (text.isNotEmpty() && !text.startsWith("@string/")) {
                        add("${element.getAttribute("android:id")} -> $text")
                    }
                }
            }
        }
        assertEquals("layout text that is not a string resource", emptyList<String>(), offenders)
    }

    // ------------------------------------------------------------------ helpers

    /** The `R.string` keys the provider actually renders, read out of its source. */
    private fun keysUsedByProvider(): Set<String> =
        PROVIDER_STRING_REFERENCE.findAll(providerSource.readText())
            .map { match -> match.groupValues[1] }
            .toSet()

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
     * Only the `%n$x` form counts: these strings are rendered through `getString(id, args...)`,
     * which cannot place an argument any other way.
     */
    private fun specifiersOf(value: String): List<String> =
        POSITIONAL_SPECIFIER.findAll(value).map { match -> match.groupValues[1] + match.groupValues[2] }.toList()

    private companion object {
        val PROVIDER_STRING_REFERENCE = Regex("""R\.string\.([A-Za-z0-9_]+)""")

        // `\${'$'}` and not `${'$'}`: a bare `$` in a regex is the end-of-input anchor, which would
        // make this pattern match nothing and the whole guard vacuously green.
        val POSITIONAL_SPECIFIER = Regex("""%([0-9]+)\${'$'}([a-zA-Z])""")
    }
}
