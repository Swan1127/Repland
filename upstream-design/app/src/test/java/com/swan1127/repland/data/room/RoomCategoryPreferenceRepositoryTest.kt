package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.testkit.FakeCategoryPreferenceDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [RoomCategoryPreferenceRepository] is a thin pass-through with two pieces of real logic: it
 * normalises on both sides, and it refuses an unusable set of weights on the way in.
 *
 * Reachable from a plain JVM test because the constructor takes the `CategoryPreferenceDao`
 * interface.
 */
class RoomCategoryPreferenceRepositoryTest {

    // ------------------------------------------------------------------ observe

    @Test
    fun `observe falls back to the defaults for an empty table`() = runTest {
        val weights = RoomCategoryPreferenceRepository(FakeCategoryPreferenceDao()).observe().first()

        assertEquals(CategoryPreferences.defaults, weights)
    }

    @Test
    fun `observe completes a partial table with the defaults`() = runTest {
        // A table holding only the rows the user touched must still produce a weight for every
        // category, because the planner indexes by category without a null check.
        val dao = FakeCategoryPreferenceDao().apply {
            seed(listOf(CategoryPreferenceEntity(category = "COURSE", weight = 5)))
        }

        val weights = RoomCategoryPreferenceRepository(dao).observe().first()

        assertEquals(5, weights[TaskCategory.COURSE])
        assertEquals(CategoryPreferences.defaults[TaskCategory.LEISURE], weights[TaskCategory.LEISURE])
        assertEquals(CategoryPreferences.defaults.keys, weights.keys)
    }

    // ------------------------------------------------------------------ save

    @Test
    fun `save writes one row per category, keyed by the enum name`() = runTest {
        val dao = FakeCategoryPreferenceDao()

        RoomCategoryPreferenceRepository(dao).save(mapOf(TaskCategory.COURSE to 40, TaskCategory.OFFICE to 10))

        assertEquals(
            setOf("COURSE", "EXTRACURRICULAR", "OFFICE", "LEISURE"),
            dao.stored.map(CategoryPreferenceEntity::category).toSet(),
        )
        assertEquals(40, dao.stored.single { it.category == "COURSE" }.weight)
        assertEquals(10, dao.stored.single { it.category == "OFFICE" }.weight)
    }

    @Test
    fun `save fills a category the caller left out with its default`() = runTest {
        val dao = FakeCategoryPreferenceDao()

        RoomCategoryPreferenceRepository(dao).save(mapOf(TaskCategory.COURSE to 40))

        assertEquals(
            CategoryPreferences.defaults[TaskCategory.EXTRACURRICULAR],
            dao.stored.single { it.category == "EXTRACURRICULAR" }.weight,
        )
    }

    @Test
    fun `save of an empty map stores the defaults`() = runTest {
        val dao = FakeCategoryPreferenceDao()

        RoomCategoryPreferenceRepository(dao).save(emptyMap())

        assertEquals(
            CategoryPreferences.defaults,
            dao.stored.associate { TaskCategory.valueOf(it.category) to it.weight },
        )
    }

    @Test
    fun `save refuses a set that sums to zero`() {
        // Every category pinned to zero leaves the ranker with nothing to weigh, so the whole
        // write is refused rather than stored and re-derived later.
        val dao = FakeCategoryPreferenceDao()
        val repository = RoomCategoryPreferenceRepository(dao)

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.save(TaskCategory.entries.associateWith { 0 }) }
        }

        assertEquals("Category preferences are invalid.", thrown.message)
        assertEquals(0, dao.upsertCount)
        assertEquals(emptyList<CategoryPreferenceEntity>(), dao.stored)
    }

    @Test
    fun `save refuses an out-of-range weight`() {
        val dao = FakeCategoryPreferenceDao()
        val repository = RoomCategoryPreferenceRepository(dao)

        listOf(101, -1).forEach { weight ->
            assertThrows(IllegalArgumentException::class.java) {
                runTest { repository.save(mapOf(TaskCategory.COURSE to weight)) }
            }
        }

        assertEquals(0, dao.upsertCount)
    }

    @Test
    fun `save accepts the range boundaries`() = runTest {
        val dao = FakeCategoryPreferenceDao()

        RoomCategoryPreferenceRepository(dao).save(
            mapOf(
                TaskCategory.COURSE to 0,
                TaskCategory.EXTRACURRICULAR to 100,
                TaskCategory.OFFICE to 0,
                TaskCategory.LEISURE to 0,
            ),
        )

        assertEquals(100, dao.stored.single { it.category == "EXTRACURRICULAR" }.weight)
        assertEquals(0, dao.stored.single { it.category == "COURSE" }.weight)
    }

    @Test
    fun `the observed map is in enum order, so the DAO's ORDER BY is inert on this path`() = runTest {
        // FINDING, pinned rather than papered over. Both DAO queries say `ORDER BY category ASC`,
        // which is COURSE, EXTRACURRICULAR, LEISURE, OFFICE. That order never reaches a caller:
        // `observe` runs the rows through `CategoryPreferences.normalized`, which rebuilds the map
        // from `TaskCategory.entries` — COURSE, EXTRACURRICULAR, OFFICE, LEISURE. So the map comes
        // out enum-ordered however the rows were stored or returned, and the ORDER BY is only
        // observable through a raw `getAll()`.
        //
        // Nothing breaks. A Map compares equal regardless of iteration order, and the one consumer
        // that needs a *byte-stable* order does not lean on the query: LocalDataJsonExporter sorts
        // the map itself with `.toSortedMap(compareBy { it.name })`, and that alphabetical result
        // is what LocalDataJsonExporterTest pins.
        val dao = FakeCategoryPreferenceDao()
        val repository = RoomCategoryPreferenceRepository(dao)

        repository.save(mapOf(TaskCategory.COURSE to 40))

        // The write carries the map in enum order...
        assertEquals(
            listOf("COURSE", "EXTRACURRICULAR", "OFFICE", "LEISURE"),
            dao.stored.map(CategoryPreferenceEntity::category),
        )
        // ...the DAO hands it back in the query's alphabetical order (the fake mirrors the ORDER BY
        // so that this contrast is actually visible)...
        assertEquals(
            listOf("COURSE", "EXTRACURRICULAR", "LEISURE", "OFFICE"),
            dao.getAll().map(CategoryPreferenceEntity::category),
        )
        // ...and the repository discards that order and returns enum order again.
        assertEquals(
            listOf("COURSE", "EXTRACURRICULAR", "OFFICE", "LEISURE"),
            repository.observe().first().keys.map { it.name },
        )
    }
}
