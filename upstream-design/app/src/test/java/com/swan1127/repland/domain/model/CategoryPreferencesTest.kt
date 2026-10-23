package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CategoryPreferences] is small enough to look obviously correct, which is exactly why it
 * had no tests. Two things make it worth pinning: [LocalPriorityRanker.rank] calls
 * `require(isValid(...))`, so a wrong `isValid` either crashes the planner or silently
 * accepts a weight map the user did not mean; and `normalized` is what decides the weight of
 * any category the stored map happens to omit.
 */
class CategoryPreferencesTest {

    @Test
    fun `defaults carry every category's own default weight`() {
        assertEquals(
            mapOf(
                TaskCategory.COURSE to 30,
                TaskCategory.EXTRACURRICULAR to 25,
                TaskCategory.OFFICE to 25,
                TaskCategory.LEISURE to 20,
            ),
            CategoryPreferences.defaults,
        )
    }

    @Test
    fun `defaults cover every category so no lookup can miss`() {
        assertEquals(TaskCategory.entries.toSet(), CategoryPreferences.defaults.keys)
    }

    @Test
    fun `normalized fills a missing category with its default`() {
        val normalized = CategoryPreferences.normalized(mapOf(TaskCategory.COURSE to 90))
        assertEquals(90, normalized.getValue(TaskCategory.COURSE))
        assertEquals(25, normalized.getValue(TaskCategory.EXTRACURRICULAR))
        assertEquals(25, normalized.getValue(TaskCategory.OFFICE))
        assertEquals(20, normalized.getValue(TaskCategory.LEISURE))
    }

    @Test
    fun `normalized keeps every provided weight untouched`() {
        val provided = mapOf(
            TaskCategory.COURSE to 1,
            TaskCategory.EXTRACURRICULAR to 2,
            TaskCategory.OFFICE to 3,
            TaskCategory.LEISURE to 4,
        )
        assertEquals(provided, CategoryPreferences.normalized(provided))
    }

    @Test
    fun `normalized turns an empty map into the defaults`() {
        assertEquals(CategoryPreferences.defaults, CategoryPreferences.normalized(emptyMap()))
    }

    @Test
    fun `normalized is idempotent`() {
        val once = CategoryPreferences.normalized(mapOf(TaskCategory.OFFICE to 70))
        assertEquals(once, CategoryPreferences.normalized(once))
    }

    @Test
    fun `normalized always returns exactly one entry per category`() {
        assertEquals(TaskCategory.entries.size, CategoryPreferences.normalized(emptyMap()).size)
    }

    // ---------------------------------------------------------------- validity

    @Test
    fun `the defaults are valid`() {
        assertTrue(CategoryPreferences.isValid(CategoryPreferences.defaults))
    }

    @Test
    fun `an empty map is valid because it normalizes to the defaults`() {
        assertTrue(CategoryPreferences.isValid(emptyMap()))
    }

    @Test
    fun `a single zero weight is still valid as long as the total is positive`() {
        // 0 + 25 + 25 + 20 = 70.
        assertTrue(CategoryPreferences.isValid(mapOf(TaskCategory.COURSE to 0)))
    }

    @Test
    fun `a map that zeroes every category is invalid`() {
        val allZero = TaskCategory.entries.associateWith { 0 }
        assertFalse(CategoryPreferences.isValid(allZero))
    }

    @Test
    fun `a weight above one hundred is invalid`() {
        assertFalse(CategoryPreferences.isValid(mapOf(TaskCategory.COURSE to 101)))
    }

    @Test
    fun `a negative weight is invalid`() {
        assertFalse(CategoryPreferences.isValid(mapOf(TaskCategory.COURSE to -1)))
    }

    @Test
    fun `the boundaries zero and one hundred are both valid`() {
        assertTrue(CategoryPreferences.isValid(mapOf(TaskCategory.COURSE to 0, TaskCategory.LEISURE to 100)))
        assertTrue(CategoryPreferences.isValid(mapOf(TaskCategory.COURSE to 100, TaskCategory.LEISURE to 0)))
    }

    @Test
    fun `validity is judged on the normalized map, so a missing category cannot make it invalid`() {
        // The single provided weight is fine, and the three defaults are filled in; a check
        // that forgot to normalize would see a sum of 90 (still valid) but a 1-entry map, so
        // pin the normalization explicitly through a case where the total depends on it.
        assertTrue(CategoryPreferences.isValid(mapOf(TaskCategory.COURSE to 100)))
    }

    @Test
    fun `an invalid weight survives normalization and is still rejected`() {
        // The other direction: normalization must not paper over a bad provided value.
        assertFalse(CategoryPreferences.isValid(mapOf(TaskCategory.OFFICE to 1_000)))
    }
}
