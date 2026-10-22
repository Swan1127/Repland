package com.swan1127.repland.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element

/**
 * Checks `values-en/strings.xml` against `values/strings.xml`.
 *
 * **Why this is a source-level test.** Android resource files are two hand-maintained lists that
 * nothing in the build ever compares. aapt will happily compile a `values-en/` folder that is
 * missing half its keys, and it will not warn: at runtime Android *falls back* to `values/`. So
 * the failure mode is not a crash, it is an English-locale user reading Chinese in the middle of
 * an otherwise English screen — invisible to every developer running the default locale, and
 * invisible to CI, because no test ever looked.
 *
 * The defect classes, in the order they actually happen:
 *
 *   1. **A missing translation.** A string is added to `values/` and never added to `values-en/`.
 *      Because of fallback this is silent. This is the common case, and it is why the central
 *      assertion is *key-set equality* rather than "no key is missing".
 *   2. **A dropped or reindexed format argument.** `%1$s` translated to `%2$s`, or `%1$d`
 *      translated away entirely. Extra arguments are ignored, so a *dropped* specifier renders
 *      wrong text rather than throwing; a *renumbered* one silently prints the wrong value. Both
 *      survive a compile and a screenshot.
 *   3. **An untranslated value.** The English file gets the Chinese text pasted into it, so the
 *      key set is fine and only a human notices.
 *
 * **What this proves:** both files parse to a non-empty key set; they declare exactly the same set
 * of keys; every key carries the same format arguments in both files; the English file contains no
 * CJK character anywhere, comments included; and no English value is blank.
 *
 * **What this does NOT prove:** that the English *reads* well — a fluent-sounding mistranslation
 * passes every assertion here. It does not prove `values-en/` is actually selected at runtime on a
 * device; that needs a real device with the locale switched, which is why it is listed as an open
 * gap rather than assumed. It says nothing about `values-night/` or the colour resources. And the
 * non-English locales beyond `en` do not exist yet, so "the English file is complete" is not the
 * same claim as "the app is localised".
 *
 * **Two deliberate non-assertions**, both verified rather than assumed, and both removed after
 * mutation testing showed they could never fail:
 *
 *   - *Every `R.string.<key>` referenced from Kotlin resolves to a declared string.* aapt generates
 *     `R`, so referencing an undeclared key is a compile error. The same reasoning does not apply to
 *     `getIdentifier(name)` lookups, which aapt cannot check — but the codebase contains none, so
 *     there is nothing to guard.
 *   - *No key is declared twice in one file.* Injecting a duplicate key into `values-en/strings.xml`
 *     does not reach the test at all: aapt2 rejects it first with `duplicate value for resource`.
 *     A test whose defect is caught upstream by the compiler is a test that only ever passes.
 */
class I18nResourceTest {

    private val resRoot = File("src/main/res")
    private val defaultFile = resRoot.resolve("values/strings.xml")
    private val englishFile = resRoot.resolve("values-en/strings.xml")

    private val defaultStrings: Map<String, String> by lazy { parseStrings(defaultFile) }
    private val englishStrings: Map<String, String> by lazy { parseStrings(englishFile) }

    /**
     * Asserts the precondition every other test depends on.
     *
     * Without this, a missing `values-en/strings.xml` shows up as five unrelated
     * `FileNotFoundException`s rather than one sentence saying the file is gone. The working
     * directory is also worth pinning this way: Gradle runs unit tests from the module
     * directory, so these paths are relative to `app/`, and a silent empty result would
     * otherwise make every assertion below pass on nothing.
     */
    @Before
    fun requireResourceFiles() {
        assertTrue("missing ${defaultFile.absolutePath} (tests run from the app module directory)", defaultFile.isFile)
        assertTrue("missing ${englishFile.absolutePath} (tests run from the app module directory)", englishFile.isFile)
    }

    // ------------------------------------------------------------------ the guard

    @Test
    fun `both resource files parse to a non-empty key set`() {
        assertTrue("no <string> entries parsed from ${defaultFile.path}", defaultStrings.isNotEmpty())
        assertTrue("no <string> entries parsed from ${englishFile.path}", englishStrings.isNotEmpty())
    }

    @Test
    fun `the english file declares exactly the keys of the default file`() {
        // Fallback makes a missing key invisible at runtime, so both directions are failures:
        // a missing key shows Chinese to an English user, and an extra key is dead weight that
        // never resolves in any locale.
        assertEquals(
            "keys present in values/ but missing from values-en/",
            emptySet<String>(),
            defaultStrings.keys - englishStrings.keys,
        )
        assertEquals(
            "keys present in values-en/ but not in values/",
            emptySet<String>(),
            englishStrings.keys - defaultStrings.keys,
        )
    }

    @Test
    fun `every translation carries the same format arguments as the default`() {
        val mismatches = defaultStrings.keys.intersect(englishStrings.keys)
            .map { key -> Triple(key, specifiersOf(defaultStrings.getValue(key)), specifiersOf(englishStrings.getValue(key))) }
            .filter { (_, expected, actual) -> expected != actual }
            .map { (key, expected, actual) -> "$key: default=$expected english=$actual" }
            .sorted()

        assertEquals("format arguments differ between the two files", emptyList<String>(), mismatches)
    }

    @Test
    fun `the english file contains no CJK anywhere`() {
        val offending = englishFile.readLines()
            .withIndex()
            .filter { (_, line) -> CJK.containsMatchIn(line) }
            .map { (index, line) -> "${englishFile.path}:${index + 1}: ${line.trim()}" }

        assertEquals(
            "values-en/strings.xml must not contain CJK, comments included — " +
                "a Chinese comment in the English file means someone was editing the wrong file",
            emptyList<String>(),
            offending,
        )
    }

    @Test
    fun `no english translation is blank`() {
        // An empty value in a qualified folder is used as-is rather than falling back, so a blank
        // translation is a silently empty label on screen.
        val blanks = englishStrings.filterValues { it.isBlank() }.keys.sorted()
        assertEquals("blank english translations", emptyList<String>(), blanks)
    }

    // ------------------------------------------------------------------ parsing

    /**
     * Reads the `<string>` entries with the JDK's own XML parser.
     *
     * A regex over the file text would be shorter and wrong: entries carry attributes
     * (`percent_suffix` has `formatted="false"`, which is exactly what a naive
     * `<string name="x">` pattern misses), and entity references have to be decoded to compare
     * the text the user actually sees. This is the same parser [com.swan1127.repland.data.importer.XlsxTimetableImporter]
     * uses, and it is available in a JVM unit test because `javax.xml` comes from the JDK rather
     * than from the Android framework stubs.
     */
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

    // ------------------------------------------------------------------ format arguments

    /**
     * Extracts the format arguments a value refers to, normalised to `index + conversion` —
     * `%1$s` becomes `1s`, `%2$d` becomes `2d`.
     *
     * Width and precision are deliberately dropped. `%1$3d` and `%1$d` pass the same argument;
     * column padding is a presentation choice each locale is allowed to make differently, so
     * treating it as a mismatch would fail correct translations.
     *
     * The one subtlety is prose. `"Save 50% done"` contains a `%` followed by a space and a `d`,
     * which the printf grammar reads as "argument with the space flag, conversion `d`". A match
     * with no argument index *and* a space flag is therefore treated as text rather than a
     * substitution — no real format string uses the space flag without an index, and dropping
     * these is what keeps the check from inventing a mismatch between `"完成度 50%"` and
     * `"50% done"`. Positional arguments, which is what `getString(id, args...)` requires, are
     * unaffected.
     */
    private fun specifiersOf(value: String): List<String> =
        FORMAT_SPECIFIER.findAll(value).mapNotNull { match ->
            val index = match.groupValues[1]
            val flags = match.groupValues[2]
            val conversion = match.groupValues[4]
            if (index.isEmpty() && flags.contains(' ')) return@mapNotNull null
            "${index.ifEmpty { "?" }}$conversion"
        }.toList()

    private companion object {

        /** CJK ideographs, CJK punctuation, kana, and fullwidth forms. */
        val CJK = Regex("[\u3000-\u303f\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\uff00-\uffef]")

        // The `\${'$'}` is load-bearing twice over: the regex needs an *escaped* dollar, because a
        // bare `$` is the end-of-input anchor and would make this pattern match nothing at all —
        // which is exactly the failure this test had until mutation testing caught it.
        val FORMAT_SPECIFIER =
            Regex("""%(?:([0-9]+)\${'$'})?([-+ 0,(#]*)([0-9]+)?(?:\.[0-9]+)?([a-zA-Z])""")
    }
}
