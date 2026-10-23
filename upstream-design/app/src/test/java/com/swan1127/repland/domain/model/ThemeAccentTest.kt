package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The storage contract of the accent setting.
 *
 * Every assertion here is about one claim: **no stored value can stop the app from painting
 * itself.** A theme is cosmetic, so the only acceptable failure modes for a bad or absent value
 * are "fall back to the default" and "fall back to the default" — never a crash and never a
 * half-applied scheme.
 */
class ThemeAccentTest {

    @Test
    fun `an absent setting resolves to the default accent`() {
        assertEquals(ThemeAccent.BAMBOO, ThemeAccent.fromStorage(null))
    }

    @Test
    fun `a blank setting resolves to the default accent`() {
        assertEquals(ThemeAccent.BAMBOO, ThemeAccent.fromStorage(""))
        assertEquals(ThemeAccent.BAMBOO, ThemeAccent.fromStorage("   "))
    }

    @Test
    fun `an unknown name resolves to the default accent rather than failing`() {
        assertEquals(ThemeAccent.BAMBOO, ThemeAccent.fromStorage("MAGENTA"))
        assertEquals(ThemeAccent.BAMBOO, ThemeAccent.fromStorage("{}"))
    }

    @Test
    fun `matching is exact, not case-insensitive and not trimmed`() {
        // The name is written by `enumValue.name`, so anything else is by definition a value from
        // another build or a hand-edited preference. Accepting near-misses would mean two spellings
        // of the same accent exist in the wild, and the picker would then show neither as selected.
        //
        // The lower-cased *non-default* names below are the load-bearing cases. Asserting only
        // `fromStorage("bamboo") == BAMBOO` would prove nothing, because BAMBOO is also what the
        // fallback returns — an implementation that matched case-insensitively would satisfy it.
        // "indigo" and "plum" can only come back as the default if the comparison really is exact.
        assertEquals(ThemeAccent.BAMBOO, ThemeAccent.fromStorage("indigo"))
        assertEquals(ThemeAccent.BAMBOO, ThemeAccent.fromStorage("plum"))
        assertEquals(ThemeAccent.BAMBOO, ThemeAccent.fromStorage("Bamboo"))
        assertEquals(ThemeAccent.BAMBOO, ThemeAccent.fromStorage("bamboo"))
        assertEquals(ThemeAccent.BAMBOO, ThemeAccent.fromStorage(" BAMBOO"))
        assertEquals(ThemeAccent.BAMBOO, ThemeAccent.fromStorage("INDIGO "))
    }

    @Test
    fun `every accent round-trips through storage`() {
        ThemeAccent.entries.forEach { accent ->
            assertEquals(accent, ThemeAccent.fromStorage(accent.name))
        }
    }

    @Test
    fun `the default is one of the entries`() {
        assertTrue(ThemeAccent.DEFAULT in ThemeAccent.entries)
    }

    @Test
    fun `accent names are unique and non-blank`() {
        val names = ThemeAccent.entries.map { it.name }
        assertEquals("two accents share a name, so one could never be stored", names.size, names.toSet().size)
        assertTrue("a blank name cannot round-trip through storage", names.none { it.isBlank() })
    }

    @Test
    fun `there is more than one accent to choose from`() {
        // The feature is literally "multiple themes"; a one-entry picker would be a control that
        // cannot change anything, which is the failure this whole file exists to prevent.
        assertTrue("a picker with one option is not a picker", ThemeAccent.entries.size >= 2)
    }
}
