package com.swan1127.repland.i18n

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the **code** half of the language problem.
 *
 * [I18nResourceTest] already checks the resource files: that `values/` and `values-en/` agree key
 * for key and that the English file contains no CJK. Neither of those would have caught the defect
 * this class is about, because the defect was not in a resource file — it was a `DateTimeFormatter`
 * pattern with 月/日 written into it and a hard-coded `Locale.CHINA`. A pattern is code, so the only
 * thing that can fail when someone reintroduces one is a test that reads the code.
 *
 * Two rules, both decidable:
 *
 *  1. no source names a Chinese locale (`Locale.CHINA`, `SIMPLIFIED_CHINESE`, …);
 *  2. no source passes a string literal containing CJK to `ofPattern`.
 *
 * **What is deliberately allowed.** `Locale.getDefault()` and `Locale.ROOT` are not forbidden — one
 * is the user's own choice and the other is explicitly language-free. `Locale.US` is not forbidden
 * either: the exported timetable's clock is a pair of ASCII digits and must not be localised. And
 * the rules do not look for CJK in ordinary string literals, because that is the separate,
 * much larger "UI copy is still Chinese" item — `Text("返回")` is not a date bug.
 *
 * **Comments are stripped before scanning.** A KDoc that explains "this used to say
 * `Locale.SIMPLIFIED_CHINESE`" is documentation, not a defect, and a guard that punished writing
 * things down would be deleted within a week. Block comments are removed with their newlines
 * preserved so the reported line numbers still point at the right line.
 */
class HardCodedDateLocaleTest {

    private lateinit var sources: List<File>

    @Test
    fun `the scan reaches the whole main source tree`() {
        // The source root is reached through a directory junction on this machine, and a walk that
        // silently stops at it would turn every assertion below into "nothing matched, all good".
        // This is the assertion that makes the other two mean something.
        collectSources()
        assertTrue(
            "expected the full main source tree, found only ${sources.size} Kotlin files",
            sources.size >= 100,
        )
    }

    @Test
    fun `the scan is looking at code that really formats dates`() {
        collectSources()
        val withPattern = sources.filter { code(it).contains("ofPattern") }
        assertTrue(
            "no main source mentions ofPattern, so rule 2 below proves nothing",
            withPattern.isNotEmpty(),
        )
    }

    // ------------------------------------------------------------ the rules themselves

    @Test
    fun `the rules match the shapes they exist to catch`() {
        // Positive controls. Without these a typo in either regex would silently reduce the two
        // tests below to "assert that an empty list is empty" — the exact failure mode that makes
        // a guard look green while guarding nothing.
        listOf(
            """val f = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)""",
            """day.getDisplayName(TextStyle.FULL, Locale.SIMPLIFIED_CHINESE)""",
            """val isTraditional = locale == Locale.TAIWAN""",
        ).forEach { sample ->
            assertTrue("rule 1 missed: $sample", HARD_CODED_CHINESE_LOCALE.containsMatchIn(sample))
        }
        listOf(
            """date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))""",
            """ofPattern("yyyy年M月d日")""",
            """ofPattern( "M月d日" )""",
        ).forEach { sample ->
            assertTrue("rule 2 missed: $sample", CJK_IN_PATTERN_LITERAL.containsMatchIn(sample))
        }
    }

    @Test
    fun `the rules stay quiet on the shapes that are allowed`() {
        // Negative controls: the patterns the codebase legitimately contains today.
        listOf(
            """ofPattern(pattern, currentLocale())""",
            """ofPattern(texts.datePattern, texts.locale)""",
            """ofPattern("yyyy-MM-dd HH:mm")""",
            """ofPattern("yyyyMMdd'T'HHmmss")""",
            """String.format(Locale.US, "%02d:%02d", a, b)""",
            """val locale = Locale.getDefault()""",
            """val x = Locale.ROOT""",
        ).forEach { sample ->
            assertTrue("rule 1 false positive: $sample", !HARD_CODED_CHINESE_LOCALE.containsMatchIn(sample))
            assertTrue("rule 2 false positive: $sample", !CJK_IN_PATTERN_LITERAL.containsMatchIn(sample))
        }
    }

    @Test
    fun `stripping comments preserves line numbers`() {
        collectSources()
        val file = sources.first { it.name == "DateFormats.kt" }
        val raw = file.readText()
        assertEquals(raw.count { it == '\n' }, code(file).count { it == '\n' })
    }

    @Test
    fun `stripping comments removes prose but keeps code`() {
        collectSources()
        val file = sources.first { it.name == "DateFormats.kt" }
        val stripped = code(file)
        // The KDoc quotes the old hard-coded pattern; it must be gone after stripping…
        assertTrue("KDoc survived stripping", !stripped.contains("SIMPLIFIED_CHINESE"))
        // …while the real call next to it must still be there, or the scan is reading nothing.
        assertTrue("code was stripped too", stripped.contains("ofPattern(pattern, locale)"))
    }

    // ------------------------------------------------------------ the tree

    @Test
    fun `no main source pins a Chinese locale`() {
        collectSources()
        report(HARD_CODED_CHINESE_LOCALE, "a hard-coded Chinese locale")
    }

    @Test
    fun `no main source hard-codes a date pattern containing Chinese`() {
        collectSources()
        report(CJK_IN_PATTERN_LITERAL, "CJK inside an ofPattern literal")
    }

    // ------------------------------------------------------------ helpers

    private fun collectSources() {
        if (::sources.isInitialized) return
        val root = File("src/main/java")
        require(root.isDirectory) { "expected the main source tree at ${root.absolutePath}" }
        sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun report(rule: Regex, what: String) {
        val hits = sources.flatMap { file ->
            code(file).lineSequence().withIndex()
                .filter { (_, line) -> rule.containsMatchIn(line) }
                .map { (index, line) -> "  ${file.path}:${index + 1}  ${line.trim()}" }
                .toList()
        }
        assertTrue(
            "found $what in:\n${hits.joinToString("\n")}",
            hits.isEmpty(),
        )
    }

    /**
     * The file with block comments blanked out — newlines kept, so line numbers still line up.
     *
     * Line comments are deliberately left alone: cutting at the first `//` would also cut at the
     * `//` inside a URL string, and losing real code is worse than reporting a comment.
     */
    private fun code(file: File): String =
        file.readText().replace(BLOCK_COMMENT) { match -> "\n".repeat(match.value.count { it == '\n' }) }

    private companion object {
        val HARD_CODED_CHINESE_LOCALE =
            Regex("""\bLocale\.(CHINA|CHINESE|SIMPLIFIED_CHINESE|TRADITIONAL_CHINESE|PRC|TAIWAN|HONGKONG)\b""")

        val CJK_IN_PATTERN_LITERAL =
            Regex("""ofPattern\(\s*"[^"]*[\u3000-\u303f\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\uff00-\uffef]""")

        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")
    }
}
