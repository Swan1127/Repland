package com.swan1127.repland.ui.theme

import com.swan1127.repland.domain.model.ThemeAccent
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element

/**
 * The two things a new accent needs *besides* its colours.
 *
 * A palette is not self-contained: it also needs a label, in both languages, and it is measured
 * against the shared page colours in `Color.kt`. Both of those live outside `AccentPalette.kt`, so
 * neither would be caught by [AccentPaletteTest]. Both failures are silent at runtime:
 *
 * - A missing label renders as the **empty string** in the picker. `R` is generated, so a missing
 *   *key* is a compile error — but `theme_accent_plum` in `values/` and not in `values-en/` is not,
 *   because Android falls back to `values/` and shows Chinese to an English user.
 * - A retuned `Palette.Page` would leave [AccentPaletteTest] measuring contrast against a page
 *   colour the app no longer uses, so its thresholds would be checked against a fiction.
 *
 * Both are read from source rather than from `R`, for the same reason `I18nResourceTest` is: the
 * defect is a *file* defect, and `R` cannot express "this key is missing from one of the two files".
 */
class ThemeAccentResourceTest {

    private val resRoot = File("src/main/res")
    private val defaultStrings = resRoot.resolve("values/strings.xml")
    private val englishStrings = resRoot.resolve("values-en/strings.xml")

    /**
     * Reached through the directory junction described in `app/build.gradle.kts`, so this is the
     * same file the app compiles rather than a copy.
     */
    private val colorSource = File("src/main/java/com/swan1127/repland/ui/theme/Color.kt")

    /** Also reached through the junction; holds the accent-to-label mapping. */
    private val toolsSource = File("src/main/java/com/swan1127/repland/ui/tools/Tools.kt")

    @Before
    fun requireSources() {
        assertTrue("missing ${defaultStrings.absolutePath} (tests run from the app module directory)", defaultStrings.isFile)
        assertTrue("missing ${englishStrings.absolutePath} (tests run from the app module directory)", englishStrings.isFile)
        assertTrue("missing ${colorSource.absolutePath} (tests run from the app module directory)", colorSource.isFile)
        assertTrue("missing ${toolsSource.absolutePath} (tests run from the app module directory)", toolsSource.isFile)
    }

    // ------------------------------------------------------------------ labels

    @Test
    fun `every accent has a label in both languages`() {
        val zh = stringKeys(defaultStrings)
        val en = stringKeys(englishStrings)

        // Anti-vacuity: a parse that silently returned nothing would make every assertion below pass.
        assertTrue("no <string> entries parsed from ${defaultStrings.path}", zh.size > 100)
        assertTrue("no <string> entries parsed from ${englishStrings.path}", en.size > 100)

        val expected = ThemeAccent.entries.map { labelKeyFor(it) }.toSet()
        assertEquals("accent labels missing from values/", emptySet<String>(), expected - zh)
        assertEquals("accent labels missing from values-en/", emptySet<String>(), expected - en)
    }

    @Test
    fun `no accent label is left behind by a removed accent`() {
        // The other direction: an accent deleted from the enum but not from the resources leaves a
        // key nothing can reach. Comparing the *count* is what catches it, because key-set
        // membership alone cannot tell "this accent exists" from "a stale key happens to match".
        val declared = stringKeys(defaultStrings).filter { it.startsWith(ACCENT_KEY_PREFIX) }
        assertEquals(
            "one '${ACCENT_KEY_PREFIX}*' key per accent, no more and no fewer",
            ThemeAccent.entries.size,
            declared.size,
        )
    }

    // ------------------------------------------------------------------ the mapping

    @Test
    fun `each accent is mapped to its own label resource`() {
        // The two resource files can be complete while the *mapping* is wrong: returning
        // `theme_accent_plum` for BAMBOO would label every row with the wrong name, and nothing else
        // notices. Resource ids are plain ints, so no assertion on the strings can see which id a
        // branch returns; the mapping has to be read out of the source, which is also how the widget
        // resource guard works.
        val source = toolsSource.readText()
        val body = source
            .substringAfter("fun accentLabelRes(accent: ThemeAccent): Int = when (accent) {")
            .substringBefore("\n}")

        // Anti-vacuity: a renamed function would make `substringAfter` return the whole file, or an
        // empty string — and every assertion below would then pass on nothing.
        assertTrue(
            "the accentLabelRes body was not located — this guard is scanning the wrong text",
            body.length < source.length && body.contains("ThemeAccent."),
        )

        val wrong = ThemeAccent.entries.mapNotNull { accent ->
            val expected = "ThemeAccent.${accent.name} -> R.string.${labelKeyFor(accent)}"
            if (body.contains(expected)) null else "missing: $expected"
        }
        assertEquals("accent label mapping", emptyList<String>(), wrong)
    }

    // ------------------------------------------------------------------ the shared page colours

    @Test
    fun `the page colours the contrast invariants assume are still the app's`() {
        // `AccentPaletteTest` holds these two values as literals so its arithmetic can take `Long`s
        // without pulling Compose's `Color` into a unit test. That duplication is deliberate and
        // this is what stops it drifting: if `Palette.Page` is retuned, the contrast thresholds are
        // being measured against a colour the app no longer paints, and this fails first.
        val source = colorSource.readText()
        assertTrue(
            "Palette.Page is no longer 0xFFF7F8FC — AccentPaletteTest's PAGE literal is now wrong",
            Regex("""val\s+Page\s*=\s*WarmPaper""").containsMatchIn(source) &&
                Regex("""val\s+WarmPaper\s*=\s*Color\(0xFFF7F8FC\)""").containsMatchIn(source),
        )
        assertTrue(
            "Palette.NightPage is no longer 0xFF171921 — AccentPaletteTest's NIGHT_PAGE literal is now wrong",
            Regex("""val\s+NightPage\s*=\s*Color\(0xFF171921\)""").containsMatchIn(source),
        )
    }

    // ------------------------------------------------------------------ parsing

    private fun labelKeyFor(accent: ThemeAccent): String =
        ACCENT_KEY_PREFIX + accent.name.lowercase()

    private fun stringKeys(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildSet {
            for (index in 0 until nodes.length) {
                add((nodes.item(index) as Element).getAttribute("name"))
            }
        }
    }

    private companion object {
        const val ACCENT_KEY_PREFIX = "theme_accent_"
    }
}
