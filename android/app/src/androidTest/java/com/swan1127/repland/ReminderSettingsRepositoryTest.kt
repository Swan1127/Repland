package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.ReplandDatabase
import com.swan1127.repland.data.room.RoomReminderSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderSettingsRepositoryTest {
    private lateinit var database: ReplandDatabase
    private lateinit var repository: RoomReminderSettingsRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReplandDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomReminderSettingsRepository(database.reminderSettingsDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun local_reminders_are_opt_in_and_the_user_can_turn_them_back_off() = runBlocking {
        assertFalse(repository.observe().first().isEnabled)

        repository.setEnabled(true)
        assertTrue(repository.observe().first().isEnabled)

        repository.setEnabled(false)
        assertFalse(repository.observe().first().isEnabled)
    }
}
