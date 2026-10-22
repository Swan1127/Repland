package com.swan1127.repland.ui.tasks

import com.swan1127.repland.R
import java.io.File
import java.util.IllegalFormatConversionException
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element

/**
 * Ties the batch actions to the words the list renders.
 *
 * Two things here cannot be checked by the compiler. The first is the enum-to-key mapping: the
 * action bar resolves its button text through `stringResource(action.labelRes)`, so the enum entry
 * and the resource name are joined only by an `Int`, and a copy-paste that pointed two entries at
 * one key — or at a key that does not exist — would compile and ship. The second is the reverse:
 * a declared `batch_*` key that nothing renders, which is exactly how a key survives a rename.
 *
 * The placeholder kinds are the third: `stringResource(id, arg)` boxes every argument into `Any`,
 * so a `%1$d` handed a status label throws `IllegalFormatConversionException` at draw time, on the
 * confirm dialog, only for the users who reach it.
 *
 * **What this does not prove:** that the wording reads well, or that the bar is visible — this is a
 * JVM test with no Context and no layout.
 */
class TaskBatchResourceTest {

    private val resRoot = File("src/main/res")
    private val defaultStrings: Map<String, String> by lazy { parseStrings(resRoot.resolve("values/strings.xml")) }
    private val englishStrings: Map<String, String> by lazy { parseStrings(resRoot.resolve("values-en/strings.xml")) }

    /** Reached through the directory junction, so this is the file the app compiles. */
    private val screenSource = File("src/main/java/com/swan1127/repland/ui/ReplandApp.kt")

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
        assertTrue("missing ${screenSource.absolutePath} (tests run from the app module directory)", screenSource.isFile)
    }

    // ------------------------------------------------------------------ anti-vacuity

    @Test
    fun `the id lookup resolves the whole string table`() {
        assertTrue("resolved only ${namesById.size} R.string ids", namesById.size >= 400)
        assertEquals("batch_select_mode", namesById.getValue(R.string.batch_select_mode))
    }

    @Test
    fun `the screen source is the one being scanned`() {
        // Without this, a moved or renamed file makes the reference guard below scan nothing and go
        // green — a guard that cannot fail is worse than none, because it is trusted.
        val source = screenSource.readText()
        assertTrue("private fun TasksScreen( was not found", source.contains("private fun TasksScreen("))
        assertTrue("the batch entry tag was not found", source.contains("\"batch-enter\""))
    }

    // ------------------------------------------------------------------ the words exist

    @Test
    fun `every batch key is declared in both resource files`() {
        val missing = BATCH_KEYS.filter {
            !defaultStrings.containsKey(it) || !englishStrings.containsKey(it)
        }
        assertEquals("batch keys missing from a resource file", emptyList<String>(), missing)
    }

    @Test
    fun `no batch key is left behind by removed UI`() {
        // The other direction, and the one set membership cannot answer: a key deleted from the
        // screen but not from the resources leaves text nothing can reach. Comparing the *count* is
        // what tells "this key is live" from "a stale key happens to match".
        val declared = defaultStrings.keys.filter { it.startsWith(BATCH_KEY_PREFIX) }
        assertEquals(
            "one '${BATCH_KEY_PREFIX}*' key per batch string, no more and no fewer",
            BATCH_KEYS.size,
            declared.size,
        )
    }

    @Test
    fun `the screen references every batch key it is expected to render`() {
        // The three `batch_action_*` keys are resolved through the enum and cannot appear here; the
        // enum guard below covers them. These seven are resolved by name and so must be present.
        val source = screenSource.readText()
        val unreferenced = LITERAL_KEYS.filter { !source.contains("R.string.$it") }
        assertEquals("batch keys nothing renders", emptyList<String>(), unreferenced)
    }

    @Test
    fun `the two key lists are disjoint and cover every declared batch key`() {
        // Two guards on the guard's own bookkeeping, both able to fail.
        //
        // The disjointness is a constraint on two hand-written lists: putting an enum-driven key in
        // the literal list would make the reference guard demand `R.string.batch_action_*` in the
        // screen source, where it can never appear, and the failure would look like a real defect.
        assertEquals(
            "a key appears in both the literal and the enum-driven list",
            emptyList<String>(),
            LITERAL_KEYS.intersect(ENUM_KEYS.toSet()).toList(),
        )
        // And the coverage direction, which is what stops a *new* resource key from slipping past
        // every guard: `no batch key is left behind by removed UI` catches it by count, this one
        // names the key that did it. Neither is a tautology — `declared` is read from the file.
        val declared = defaultStrings.keys.filter { it.startsWith(BATCH_KEY_PREFIX) }
        assertEquals(
            "declared batch keys that no guard list covers",
            emptyList<String>(),
            (declared - BATCH_KEYS.toSet()).sorted(),
        )
    }

    // ------------------------------------------------------------------ the enum points at its own key

    @Test
    fun `every action resolves to a key named after it`() {
        // The mapping the compiler cannot see. `COMPLETE` must resolve to `batch_action_complete`
        // and not, say, to `batch_action_cancel` — both are valid ids, so only the name ties them.
        val wrong = BatchTaskAction.entries.mapNotNull { action ->
            val expected = "batch_action_" + action.name.lowercase()
            val actual = namesById[action.labelRes]
            if (actual == expected) null else "$action -> $actual (expected $expected)"
        }
        assertEquals("actions whose label key is not named after them", emptyList<String>(), wrong)
    }

    @Test
    fun `the action labels are three different keys`() {
        val ids = BatchTaskAction.entries.map { it.labelRes }
        assertEquals("two actions share a label key", ids.size, ids.toSet().size)
    }

    @Test
    fun `the enum-driven keys are exactly the batch_action keys declared`() {
        val declared = defaultStrings.keys.filter { it.startsWith("${BATCH_KEY_PREFIX}action_") }
        assertEquals(
            "one batch_action_* key per enum entry",
            BatchTaskAction.entries.size,
            declared.size,
        )
    }

    @Test
    fun `the enum does not borrow the generic cancel key`() {
        // Why `batch_action_cancel` exists as its own key rather than reusing `cancel`. The dialog's
        // dismiss button does use `cancel`; if the enum did too, the test above could not tell a
        // renamed key from a missing one, and the count would be satisfied by a shared id.
        val generic = R.string.cancel
        val borrowers = BatchTaskAction.entries.filter { it.labelRes == generic }
        assertEquals("actions reusing the generic cancel string", emptyList<BatchTaskAction>(), borrowers)
    }

    // ------------------------------------------------------------------ the placeholder kinds

    @Test
    fun `the placeholders are the kinds the screen passes`() {
        // Named outright rather than compared between the two files: comparing them would be
        // satisfied by both being wrong. The screen passes a count to the selection bar, a count and
        // an action label to the dialog body, and a count to the skip note.
        //
        // Compared **sorted**, because with positional specifiers the appearance order decides
        // nothing at `String.format` — what decides whether a call throws is which conversions exist
        // and how many. (The cross-language order is checked separately, and more strictly, by
        // `I18nResourceTest`, which requires the two files to name their arguments in the same
        // sequence; both files do, which is why the English body leads with the count.)
        listOf(defaultStrings, englishStrings).forEach { strings ->
            assertEquals(listOf("1d"), specifiersOf(strings.getValue("batch_selected_count")).sorted())
            assertEquals(listOf("1d", "2s"), specifiersOf(strings.getValue("batch_confirm_body")).sorted())
            assertEquals(listOf("1d"), specifiersOf(strings.getValue("batch_confirm_skipped")).sorted())
            assertEquals(emptyList<String>(), specifiersOf(strings.getValue("batch_confirm_title")))
        }
    }

    @Test
    fun `a numeric placeholder rejects text while a string placeholder accepts a number`() {
        // Why the kind check has to exist, demonstrated rather than asserted in prose. The dangerous
        // direction is the silent one: a `%1$s` given a count prints the digits and looks plausible.
        val numeric = defaultStrings.getValue("batch_selected_count")
        val threw = runCatching { String.format(numeric, "三") }.exceptionOrNull()
        assertTrue("a %1\$d placeholder was expected to reject text, got $threw", threw is IllegalFormatConversionException)
        assertTrue(
            String.format(defaultStrings.getValue("batch_confirm_body"), 3, "完成").contains("完成"),
        )
    }

    @Test
    fun `every batch string formats with the arguments the screen passes`() {
        // End to end: resolve the real strings and format them the way the screen does, with the
        // worst-case counts (zero skipped, many skipped). Nothing may be left over as a literal `%`.
        val formatted = listOf(
            "batch_selected_count" to String.format(defaultStrings.getValue("batch_selected_count"), 3),
            "batch_confirm_body" to String.format(defaultStrings.getValue("batch_confirm_body"), 3, "完成"),
            "batch_confirm_skipped" to String.format(defaultStrings.getValue("batch_confirm_skipped"), 0),
            "batch_confirm_title" to String.format(defaultStrings.getValue("batch_confirm_title")),
            "batch_exit" to String.format(defaultStrings.getValue("batch_exit")),
            "batch_select_all" to String.format(defaultStrings.getValue("batch_select_all")),
            "batch_select_mode" to String.format(defaultStrings.getValue("batch_select_mode")),
        )
        val leftovers = formatted.filter { (_, text) -> text.contains('%') }
        assertEquals("batch strings with an unfilled placeholder", emptyList<Pair<String, String>>(), leftovers)
    }

    // ------------------------------------------------------------------ helpers

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
        /** Resolved by name in `ReplandApp.kt`, so a literal `R.string.<key>` must exist there. */
        val LITERAL_KEYS = listOf(
            "batch_select_mode",
            "batch_exit",
            "batch_selected_count",
            "batch_select_all",
            "batch_confirm_title",
            "batch_confirm_body",
            "batch_confirm_skipped",
        )

        /** Resolved through `BatchTaskAction.labelRes`, so the name is tied to the enum instead. */
        val ENUM_KEYS = BatchTaskAction.entries.map { "batch_action_" + it.name.lowercase() }

        val BATCH_KEYS = LITERAL_KEYS + ENUM_KEYS

        const val BATCH_KEY_PREFIX = "batch_"

        // `\${'$'}` and not `${'$'}`: a bare `$` in a regex is the end-of-input anchor, which would
        // make this pattern match nothing and the whole guard vacuously green.
        val POSITIONAL_SPECIFIER = Regex("""%([0-9]+)\${'$'}([a-zA-Z])""")
    }
}
