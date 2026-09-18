package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.ReplandDatabase
import com.swan1127.repland.data.room.RoomAiSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiSettingsRepositoryTest {
    private lateinit var database: ReplandDatabase
    private lateinit var repository: RoomAiSettingsRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReplandDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomAiSettingsRepository(database.aiSettingsDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun advisor_is_opt_in_requires_consent_and_can_be_disabled() = runBlocking {
        assertFalse(repository.observe().first().isEnabled)
        assertFalse(repository.observe().first().hasExplicitConsent)

        val rejected = runCatching { repository.setEnabled(true) }
        assertTrue(rejected.isFailure)

        repository.grantConsentAndEnable()
        assertTrue(repository.observe().first().isEnabled)
        assertTrue(repository.observe().first().hasExplicitConsent)

        repository.setEnabled(false)
        assertFalse(repository.observe().first().isEnabled)
        assertTrue(repository.observe().first().hasExplicitConsent)
    }
}
