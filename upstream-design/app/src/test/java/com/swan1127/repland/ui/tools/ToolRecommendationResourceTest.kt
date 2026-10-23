package com.swan1127.repland.ui.tools

import com.swan1127.repland.R
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import java.io.File
import java.time.LocalDate
import java.util.IllegalFormatConversionException
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element

/**
 * Ties the recommendation rule to the words it renders.
 *
 * **Why this needs its own file.** [ToolSuggestion] carries a `@StringRes` id and a
 * [SuggestionReason], and the strip formats one with the other. Nothing in the build compares them:
 * `stringResource(id, arg)` boxes every argument into `Any`, so passing a task name to a `%1$d`
 * placeholder is not a compile error — it throws `IllegalFormatConversionException` at the moment
 * the row is drawn, on the one screen the user is looking at, and only when that particular rule
 * fires. The reverse mistake is worse: `%1$s` accepts an `Int` and prints it, so a count-based
 * reason wired to a name placeholder would render a plausible-looking number in the wrong place.
 * Neither survives a screenshot review, because both need the right situation to occur.
 *
 * **What this proves:** every rule can fire (so no assertion below is checking an empty list);
 * every rule points at the resource key it is supposed to; that key is declared in both resource
 * files; and the placeholder's conversion matches the argument's kind in both files.
 *
 * **What it does not prove:** that the English reads well, and that the strip is visible on a
 * device — this is a JVM test with no Context and no layout.
 *
 * The resource files are read from disk rather than through `Resources`, for the same reason
 * [com.swan1127.repland.i18n.I18nResourceTest] does it: there is no Android runtime here, and a
 * test that needs one would not run in this suite at all.
 */
class ToolRecommendationResourceTest {

    private val day = LocalDate.of(2026, 10, 6)

    private val defaultStrings: Map<String, String> by lazy { parseStrings(File("src/main/res/values/strings.xml")) }
    private val englishStrings: Map<String, String> by lazy { parseStrings(File("src/main/res/values-en/strings.xml")) }

    /**
     * `R.string.<name>` values are unique per type, so the reverse lookup is a plain map.
     *
     * Reflection is the only way to get from an id back to its name in a unit test — `R` is a class
     * of ints and the names only exist in the XML. The positive control below is what keeps this
     * honest: if the reflection silently returned nothing, every assertion in this file would pass
     * on an empty map.
     */
    private val namesById: Map<Int, String> by lazy {
        R.string::class.java.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { field -> field.getInt(null) to field.name }
    }

    @Before
    fun requireResourceFiles() {
        assertTrue("missing values/strings.xml (tests run from the app module directory)", defaultStrings.isNotEmpty())
        assertTrue("missing values-en/strings.xml (tests run from the app module directory)", englishStrings.isNotEmpty())
    }

    // ------------------------------------------------------------------ anti-vacuity

    @Test
    fun `the id lookup resolves the whole string table`() {
        // Guards every assertion below: an empty or truncated map would make them all pass.
        assertTrue("resolved only ${namesById.size} R.string ids", namesById.size >= 400)
        assertEquals("tool_suggest_review_points", namesById.getValue(R.string.tool_suggest_review_points))
        assertEquals("tool_suggest_title", namesById.getValue(R.string.tool_suggest_title))
    }

    @Test
    fun `every recommendation rule can fire`() {
        // One situation per rule, collected into the set of reason keys the rule actually produces.
        // This is the assertion that would catch a rule being deleted or accidentally gated off:
        // the set shrinks and the comparison names the missing tool.
        val produced = producedSuggestions().map { namesById.getValue(it.reasonRes) }.toSet()
        assertEquals(
            setOf(
                "tool_suggest_review_points",
                "tool_suggest_sq3r",
                "tool_suggest_kwl",
                "tool_suggest_reflection",
                "tool_suggest_self_explanation",
            ),
            produced,
        )
    }

    @Test
    fun `every tool the strip can render has a declared title`() {
        val undeclared = ToolId.entries
            .filter { namesById[it.titleRes] == null || !defaultStrings.containsKey(namesById.getValue(it.titleRes)) }
            .map(ToolId::name)
        assertEquals("tools whose title resource is not declared", emptyList<String>(), undeclared)
    }

    // ------------------------------------------------------------------ the guard

    @Test
    fun `each reason is declared in both resource files`() {
        val missing = producedSuggestions()
            .map { namesById.getValue(it.reasonRes) }
            .filter { !defaultStrings.containsKey(it) || !englishStrings.containsKey(it) }
            .distinct()
        assertEquals("reason keys missing from a resource file", emptyList<String>(), missing)
    }

    @Test
    fun `each reason takes exactly one argument`() {
        // The rule never passes two, and a reason that declared two would silently print its second
        // placeholder literally — `stringResource(id, arg)` has nothing to fill it with.
        val offenders = producedSuggestions()
            .map { namesById.getValue(it.reasonRes) }
            .distinct()
            .mapNotNull { key ->
                val count = specifiersOf(defaultStrings.getValue(key)).size
                if (count == 1) null else "$key declares $count arguments"
            }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `the placeholder kind matches the argument kind in the default file`() {
        assertEquals(emptyList<String>(), kindMismatches(defaultStrings))
    }

    @Test
    fun `the placeholder kind matches the argument kind in the english file`() {
        // Checked separately rather than through I18nResourceTest's specifier comparison: that test
        // proves the two files agree with each other, which is satisfied by both being wrong.
        assertEquals(emptyList<String>(), kindMismatches(englishStrings))
    }

    @Test
    fun `a name reason uses a string placeholder and a count reason uses a numeric one`() {
        // The positive control for the two assertions above. It names the expected conversion
        // outright, so the guard cannot pass by comparing a value to itself.
        assertEquals(listOf("1s"), specifiersOf(defaultStrings.getValue("tool_suggest_sq3r")))
        assertEquals(listOf("1s"), specifiersOf(englishStrings.getValue("tool_suggest_sq3r")))
        assertEquals(listOf("1d"), specifiersOf(defaultStrings.getValue("tool_suggest_reflection")))
        assertEquals(listOf("1d"), specifiersOf(englishStrings.getValue("tool_suggest_reflection")))
    }

    @Test
    fun `a numeric placeholder rejects text while a string placeholder accepts a number`() {
        // Why the kind check has to exist, demonstrated rather than asserted in prose. A `%1$d`
        // given a task name throws; a `%1$s` given a count does not, and prints it. So the
        // dangerous direction is silent, and only an explicit kind comparison catches it.
        val numeric = defaultStrings.getValue("tool_suggest_reflection")
        val threw = runCatching { String.format(numeric, "不是数字") }.exceptionOrNull()
        assertTrue("a %1\$d placeholder was expected to reject text, got $threw", threw is IllegalFormatConversionException)
        assertEquals("线代第三章", String.format(defaultStrings.getValue("tool_suggest_sq3r"), "线代第三章").let { it.substringAfter("「").substringBefore("」") })
    }

    @Test
    fun `every reason actually formats with the argument the rule passes`() {
        // End to end: take the real suggestion, resolve its real string, and format it with a
        // sample of the same kind. Nothing may be left over as a literal `%`.
        val leftovers = producedSuggestions().mapNotNull { suggestion ->
            val key = namesById.getValue(suggestion.reasonRes)
            val formatted = when (val reason = suggestion.reason) {
                is SuggestionReason.TaskName -> String.format(defaultStrings.getValue(key), reason.value)
                is SuggestionReason.Count -> String.format(defaultStrings.getValue(key), reason.value)
                null -> defaultStrings.getValue(key)
            }
            if (formatted.contains('%')) "$key -> $formatted" else null
        }
        assertEquals("reasons with an unfilled placeholder", emptyList<String>(), leftovers)
    }

    // ------------------------------------------------------------------ helpers

    /**
     * One situation per rule, all at once. Rule order means some of these would be trimmed off the
     * strip by [MAX_SUGGESTIONS], which is why each rule gets its own situation rather than one
     * crowded day — a trimmed suggestion would never reach the assertions above.
     */
    private fun producedSuggestions(): List<ToolSuggestion> {
        val reading = task("reading", name = "线代第三章", category = TaskCategory.COURSE, status = TaskStatus.IN_PROGRESS)
        val finished = task("finished", name = "高数第四章", category = TaskCategory.COURSE, status = TaskStatus.COMPLETED)
        val office = task("office", name = "报销", category = TaskCategory.OFFICE, status = TaskStatus.COMPLETED)
        val readingBlock = block("reading", 9 * 60, 10 * 60)
        val finishedBlock = block("finished", 9 * 60, 10 * 60)
        val officeBlock = block("office", 9 * 60, 10 * 60)
        return listOf(
            ToolSituation(nowMinute = 9 * 60, tasks = emptyList(), todaySegments = emptyList(), dueReviewPointCount = 2, hasReflection = false, kwlLearnedTaskIds = emptySet(), explainedTaskIds = emptySet()),
            ToolSituation(nowMinute = 11 * 60, tasks = listOf(reading), todaySegments = listOf(readingBlock), dueReviewPointCount = 0, hasReflection = false, kwlLearnedTaskIds = emptySet(), explainedTaskIds = emptySet()),
            ToolSituation(nowMinute = 11 * 60, tasks = listOf(finished), todaySegments = listOf(finishedBlock), dueReviewPointCount = 0, hasReflection = false, kwlLearnedTaskIds = emptySet(), explainedTaskIds = setOf("finished")),
            ToolSituation(nowMinute = 22 * 60, tasks = listOf(office), todaySegments = listOf(officeBlock), dueReviewPointCount = 0, hasReflection = false, kwlLearnedTaskIds = emptySet(), explainedTaskIds = setOf("office")),
            ToolSituation(nowMinute = 11 * 60, tasks = listOf(office), todaySegments = listOf(officeBlock), dueReviewPointCount = 0, hasReflection = false, kwlLearnedTaskIds = emptySet(), explainedTaskIds = emptySet()),
        ).flatMap(::recommendTools)
    }

    private fun kindMismatches(strings: Map<String, String>): List<String> =
        producedSuggestions().distinctBy(ToolSuggestion::reasonRes).mapNotNull { suggestion ->
            val key = namesById.getValue(suggestion.reasonRes)
            val specifiers = specifiersOf(strings.getValue(key))
            val expected = when (suggestion.reason) {
                is SuggestionReason.TaskName -> "1s"
                is SuggestionReason.Count -> "1d"
                null -> return@mapNotNull if (specifiers.isEmpty()) null else "$key declares $specifiers but the rule passes nothing"
            }
            if (specifiers == listOf(expected)) null else "$key declares $specifiers but the rule passes $expected"
        }

    private fun task(id: String, name: String, category: TaskCategory, status: TaskStatus): Task = Task(
        id = id,
        description = name,
        displayName = name,
        category = category,
        userPriority = TaskPriority.MEDIUM,
        estimatedDays = 1,
        totalDurationMinutes = 60,
        dueDate = null,
        status = status,
        completionSummary = null,
        actualDurationMinutes = null,
        progressPercent = null,
        postponeCount = 0,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
        completionResult = null,
        scheduledForDate = null,
    )

    private fun block(taskId: String, start: Int, end: Int): PlannedSegment =
        PlannedSegment(id = "segment-$taskId", taskId = taskId, date = day, startMinute = start, endMinute = end)

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
     * Only positional forms count, and any other `%` is reported as an anomaly by the caller: these
     * strings are formatted through `stringResource(id, arg)`, which requires the `%1$` form to
     * place the argument at all.
     */
    private fun specifiersOf(value: String): List<String> =
        POSITIONAL_SPECIFIER.findAll(value).map { match -> match.groupValues[1] + match.groupValues[2] }.toList()

    private companion object {
        // `\${'$'}` and not `${'$'}`: a bare `$` in a regex is the end-of-input anchor, which would
        // make this pattern match nothing and the whole guard vacuously green.
        val POSITIONAL_SPECIFIER = Regex("""%([0-9]+)\${'$'}([a-zA-Z])""")
    }
}
