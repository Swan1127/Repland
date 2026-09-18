package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.ReplandDatabase
import com.swan1127.repland.data.room.RoomCategoryPreferenceRepository
import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.TaskCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryPreferenceRepositoryTest {
    private lateinit var database: ReplandDatabase
    private lateinit var repository: RoomCategoryPreferenceRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReplandDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomCategoryPreferenceRepository(database.categoryPreferenceDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun category_preferences_default_then_persist_user_adjustment() = runBlocking {
        assertEquals(CategoryPreferences.defaults, repository.observe().first())

        val adjusted = CategoryPreferences.defaults +
            (TaskCategory.COURSE to 10) + (TaskCategory.LEISURE to 90)
        repository.save(adjusted)

        assertEquals(adjusted, repository.observe().first())
    }
}
