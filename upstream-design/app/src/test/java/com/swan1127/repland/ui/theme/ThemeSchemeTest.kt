package com.swan1127.repland.ui.theme

import androidx.compose.ui.graphics.Color
import com.swan1127.repland.domain.model.ThemeAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The one thing [AccentPaletteTest] cannot see: that `Theme.kt` actually *uses* the ramp it is
 * handed.
 *
 * The two files fail independently. A correct ramp wired to the wrong token — `primary` painted
 * from `tertiary`, or the light neutrals used in dark mode — leaves every contrast invariant intact,
 * because the invariants describe the ramp, not the scheme. The only symptom would be a screen
 * painted in the wrong colour, which no other test and no compiler notices.
 *
 * **What this does NOT prove:** that the scheme *renders*. Nothing here draws anything, and the
 * Compose UI is not exercised. It proves the mapping is wired to the right fields, which is the
 * half that can be stated as a fact.
 */
class ThemeSchemeTest {

    @Test
    fun `the scheme takes its brand from the accent and its neutrals from the shared palette`() {
        val indigo = replandColorScheme(ThemeAccent.INDIGO, darkTheme = false)
        assertEquals(Color(0xFF3F6094), indigo.primary)
        assertEquals(Color(0xFF80613E), indigo.tertiary)
        // The neutrals are the design decision: they must not come from the ramp.
        assertEquals(Palette.Page, indigo.background)
        assertEquals(Palette.Ink, indigo.onBackground)
    }

    @Test
    fun `dark mode flips the neutrals and uses the dark member of the ramp`() {
        val dark = replandColorScheme(ThemeAccent.INDIGO, darkTheme = true)
        assertEquals(Color(0xFFBBD2F5), dark.primary)
        assertEquals(Palette.NightPage, dark.background)
        assertEquals(Palette.NightInk, dark.onBackground)
    }

    @Test
    fun `the two modes of one accent do not share a page colour`() {
        // Catches the swap: handing `darkScheme` the light ramp (or the light neutrals) would
        // otherwise be invisible, since both ramps satisfy the same invariants.
        val light = replandColorScheme(ThemeAccent.PLUM, darkTheme = false)
        val dark = replandColorScheme(ThemeAccent.PLUM, darkTheme = true)
        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.primary, dark.primary)
    }

    @Test
    fun `every accent paints a different brand`() {
        val primaries = ThemeAccent.entries.map { replandColorScheme(it, darkTheme = false).primary }
        assertEquals("two accents resolve to the same scheme", primaries.size, primaries.toSet().size)
    }

    @Test
    fun `the page is identical across accents`() {
        // Stated as an assertion because it is the load-bearing half of the design: the neutrals are
        // shared, so the mirrors outside Compose (`values-night/`, the widget, the window
        // background) stay correct no matter which accent is chosen.
        val pages = ThemeAccent.entries.map { replandColorScheme(it, darkTheme = false).background }
        assertEquals("the neutral page moved with the accent", 1, pages.toSet().size)
    }
}
