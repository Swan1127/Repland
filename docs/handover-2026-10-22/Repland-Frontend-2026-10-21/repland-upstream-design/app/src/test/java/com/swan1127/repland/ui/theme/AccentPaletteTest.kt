package com.swan1127.repland.ui.theme

import com.swan1127.repland.domain.model.ThemeAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The accent ramps, checked as *properties* rather than as a copy of the numbers.
 *
 * The distinction matters. Asserting "INDIGO's primary is `0xFF2E4A8C`" would only ever restate the
 * source, and it would fail on every deliberate retune. Asserting "white text on INDIGO's primary
 * is readable" states the thing the colour is *for*, so it survives a retune and fails on a mistake.
 *
 * The invariants split into two families:
 *
 * 1. **The arithmetic** ([relativeLuminance], [contrastRatio]) — proved against the two colours
 *    whose answers are known by definition, black and white, plus mid-grey, which is the one value
 *    that separates a real sRGB linearisation from a naive channel average.
 * 2. **The ramps** — every accent, in both modes, has to clear a contrast bar on every
 *    text-on-fill pair, has to keep the brand luminous in dark mode, and has to stay distinct from
 *    the other accents. The default ramp is additionally pinned, so adding themes cannot restyle
 *    the app for users who never open the setting.
 *
 * **What this does NOT prove:** that any of it looks good. A palette can clear every bar here and
 * still be ugly; that is a judgement no assertion makes. It also says nothing about the *shared*
 * neutrals (`Color.kt`) or the error colours, which no theme may change and which are therefore
 * outside this file's subject.
 */
class AccentPaletteTest {

    // ------------------------------------------------------------------ the arithmetic

    @Test
    fun `black is zero luminance and white is the top of the scale`() {
        assertEquals(0.0, relativeLuminance(0xFF000000), 1e-9)
        assertEquals(1.0, relativeLuminance(0xFFFFFFFF), 1e-9)
    }

    @Test
    fun `luminance linearises sRGB rather than averaging the encoded channels`() {
        // Mid-grey encodes as 0.5 in every channel, so an average of the encoded values would call
        // it half as bright as white. The sRGB transfer function makes it about a fifth. This is
        // the assertion that makes the function worth having: every threshold below would be wrong
        // in the direction that matters most, near black, if the linearisation were dropped.
        val grey = relativeLuminance(0xFF808080)
        assertTrue("mid-grey luminance should be about 0.216, was $grey", grey in 0.20..0.24)
    }

    @Test
    fun `contrast spans the full scale between black and white`() {
        assertEquals(21.0, contrastRatio(0xFF000000, 0xFFFFFFFF), 1e-9)
    }

    @Test
    fun `contrast is symmetric`() {
        val brand = 0xFF3F6094
        val page = 0xFFF8F6F1
        assertEquals(contrastRatio(brand, page), contrastRatio(page, brand), 1e-12)
    }

    @Test
    fun `contrast of a colour with itself is the minimum`() {
        assertEquals(1.0, contrastRatio(0xFF326B75, 0xFF326B75), 1e-12)
    }

    @Test
    fun `alpha does not affect luminance`() {
        // Stated behaviour rather than a desirable one: every token here is opaque, and a
        // translucent token would have to be composited against its surface before it could be
        // measured. Treating alpha as opaque is the quieter of the two possible wrong answers.
        assertEquals(relativeLuminance(0xFF808080), relativeLuminance(0x00808080), 1e-12)
    }

    // ------------------------------------------------------------------ the ramps

    @Test
    fun `the invariants below have something to run over`() {
        // Anti-vacuity. Every other test in this section loops over `modes`; if the list were ever
        // empty or half-built, all of them would pass while proving nothing.
        assertEquals(ThemeAccent.entries.size * 2, modes.size)
        assertTrue("need at least two accents for a picker", modes.size >= 4)
    }

    @Test
    fun `every token is fully opaque`() {
        val translucent = modes.flatMap { mode ->
            mode.ramp.tokens()
                .filterValues { alphaOf(it) != 0xFFL }
                .map { (name, value) -> "$mode $name = ${hex(value)}" }
        }
        assertEquals("translucent tokens", emptyList<String>(), translucent)
    }

    @Test
    fun `text on a fill clears AA`() {
        assertPairClears(4.5) { it.primary to it.onPrimary }
        assertPairClears(4.5) { it.secondary to it.onSecondary }
        assertPairClears(4.5) { it.primaryContainer to it.onPrimaryContainer }
        assertPairClears(4.5) { it.secondaryContainer to it.onSecondaryContainer }
        assertPairClears(4.5) { it.tertiaryContainer to it.onTertiaryContainer }
    }

    @Test
    fun `the second emphasis colour clears AA-large`() {
        // A deliberately lower bar, and not a silently lowered one. `tertiary` is a *signal* —
        // times, counts, streaks — not body copy, and the palette the app shipped with already
        // sits at 3.40:1 on the page and 3.93:1 on a card. Pinning 3.0 means a future theme cannot
        // quietly fall below the level the app already has; raising it is a palette decision.
        assertPairClears(3.0) { it.tertiary to it.onTertiary }
    }

    @Test
    fun `brand text on the page clears AA in every theme`() {
        val failures = modes.mapNotNull { mode ->
            val page = if (mode.dark) NIGHT_PAGE else PAGE
            val ratio = contrastRatio(mode.ramp.primary, page)
            if (ratio >= 4.5) null else "$mode: ${hex(mode.ramp.primary)} on ${hex(page)} = ${"%.2f".format(ratio)}"
        }
        assertEquals("brand colour unreadable on the page", emptyList<String>(), failures)
    }

    @Test
    fun `in light mode the container is the pale member and the brand is the dark one`() {
        val failures = modes
            .filterNot { it.dark }
            .filter { luminanceOf(it.ramp.primaryContainer) <= luminanceOf(it.ramp.primary) }
            .map { it.toString() }
        assertEquals("a light-mode container that is not lighter than its brand", emptyList<String>(), failures)
    }

    @Test
    fun `in dark mode the brand is luminous and the container is the darker member`() {
        // The one non-obvious rule in the whole palette. A dark-mode brand cannot be a *darker*
        // version of the light one: the focal fill and the page would then be two near-blacks that
        // the eye reads as a single flat field, which is precisely the "nothing leads the screen"
        // failure the palette comments were written about.
        val failures = modes.filter { it.dark }.mapNotNull { mode ->
            val brand = luminanceOf(mode.ramp.primary)
            val container = luminanceOf(mode.ramp.primaryContainer)
            when {
                brand < 0.5 -> "$mode: brand luminance ${"%.3f".format(brand)} is not luminous"
                brand <= container ->
                    "$mode: brand ${"%.3f".format(brand)} is not lighter than container ${"%.3f".format(container)}"

                else -> null
            }
        }
        assertEquals("dark-mode focal fill is not luminous", emptyList<String>(), failures)
    }

    @Test
    fun `the accents are actually distinct`() {
        // A picker whose options all resolve to the same colour is a picker that does nothing.
        val brands = ThemeAccent.entries.map { paletteFor(it).light.primary }
        assertEquals("two accents share a brand colour", brands.size, brands.toSet().size)
    }

    @Test
    fun `adding themes did not restyle the default`() {
        // BAMBOO is the calm-teal palette every user sees without opening the accent setting.
        // Pinned so a retune of the ramps cannot silently change the default app.
        val bamboo = paletteFor(ThemeAccent.BAMBOO)
        assertEquals(0xFF326B75L, bamboo.light.primary)
        assertEquals(0xFF80613EL, bamboo.light.tertiary)
        assertEquals(0xFFA8D9DEL, bamboo.dark.primary)
        assertEquals(0xFFDCC0A0L, bamboo.dark.tertiary)
    }

    // ------------------------------------------------------------------ helpers

    private data class Mode(val accent: ThemeAccent, val dark: Boolean, val ramp: ThemeRamp) {
        override fun toString(): String = "${accent.name} ${if (dark) "dark" else "light"}"
    }

    private val modes: List<Mode> = ThemeAccent.entries.flatMap { accent ->
        val palette = paletteFor(accent)
        listOf(Mode(accent, dark = false, ramp = palette.light), Mode(accent, dark = true, ramp = palette.dark))
    }

    private fun assertPairClears(minimum: Double, pair: (ThemeRamp) -> Pair<Long, Long>) {
        val failures = modes.mapNotNull { mode ->
            val (foreground, background) = pair(mode.ramp)
            val ratio = contrastRatio(foreground, background)
            if (ratio >= minimum) {
                null
            } else {
                "$mode: ${hex(foreground)} on ${hex(background)} = ${"%.2f".format(ratio)}"
            }
        }
        assertEquals("pairs below ${minimum}:1", emptyList<String>(), failures)
    }

    private fun ThemeRamp.tokens(): Map<String, Long> = mapOf(
        "primary" to primary,
        "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "inversePrimary" to inversePrimary,
        "secondary" to secondary,
        "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
    )

    private fun luminanceOf(argb: Long): Double = relativeLuminance(argb)

    private fun alphaOf(argb: Long): Long = (argb shr 24) and 0xFFL

    private fun hex(argb: Long): String = "0x" + argb.toString(16).uppercase().padStart(8, '0')

    private companion object {
        /** `Palette.Page`, the light-mode page. Duplicated as a literal because `Palette` is internal. */
        const val PAGE = 0xFFF8F6F1L

        /** `Palette.NightPage`, the dark-mode page. */
        const val NIGHT_PAGE = 0xFF152226L
    }
}
