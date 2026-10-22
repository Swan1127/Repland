package com.swan1127.repland.data.room

import com.swan1127.repland.testkit.FakeAiSettingsDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RoomAiSettingsRepository] holds the one piece of state that is not a preference but a
 * **permission**: whether the user has consented to the bounded AI advisor. The rule is that
 * consent is granted once and then remembered, and that enabling is impossible without it.
 *
 * Reachable from a plain JVM test because the constructor takes the `AiSettingsDao` interface.
 */
class RoomAiSettingsRepositoryTest {

    // ------------------------------------------------------------------ observe

    @Test
    fun `observe falls back to the disabled default when no row exists`() = runTest {
        val preferences = RoomAiSettingsRepository(FakeAiSettingsDao()).observe().first()

        assertFalse(preferences.isEnabled)
        assertNull(preferences.consentedAtEpochMillis)
        assertFalse(preferences.hasExplicitConsent)
    }

    @Test
    fun `observe maps a stored row`() = runTest {
        val dao = FakeAiSettingsDao().apply {
            seed(AiSettingsEntity(isEnabled = true, consentedAtEpochMillis = 500L))
        }

        val preferences = RoomAiSettingsRepository(dao).observe().first()

        assertTrue(preferences.isEnabled)
        assertEquals(500L, preferences.consentedAtEpochMillis)
        assertTrue(preferences.hasExplicitConsent)
    }

    @Test
    fun `observe reports no consent for a row that was never consented`() = runTest {
        val dao = FakeAiSettingsDao().apply {
            seed(AiSettingsEntity(isEnabled = false, consentedAtEpochMillis = null))
        }

        assertFalse(RoomAiSettingsRepository(dao).observe().first().hasExplicitConsent)
    }

    // ------------------------------------------------------------------ grantConsentAndEnable

    @Test
    fun `grantConsentAndEnable enables the advisor and records consent`() = runTest {
        val dao = FakeAiSettingsDao()

        RoomAiSettingsRepository(dao).grantConsentAndEnable()

        val stored = dao.stored!!
        assertTrue(stored.isEnabled)
        assertNotNull(stored.consentedAtEpochMillis)
    }

    @Test
    fun `grantConsentAndEnable keeps the original consent timestamp`() = runTest {
        // Consent is granted once. Refreshing the timestamp would make a user who enabled the
        // advisor months ago look as though they consented today.
        val dao = FakeAiSettingsDao().apply {
            seed(AiSettingsEntity(isEnabled = false, consentedAtEpochMillis = 777L))
        }

        RoomAiSettingsRepository(dao).grantConsentAndEnable()

        assertEquals(777L, dao.stored!!.consentedAtEpochMillis)
    }

    // ------------------------------------------------------------------ setEnabled

    @Test
    fun `setEnabled refuses to enable without any stored row`() {
        val dao = FakeAiSettingsDao()
        val repository = RoomAiSettingsRepository(dao)

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.setEnabled(true) }
        }

        assertEquals("Explicit consent is required before enabling the AI advisor.", thrown.message)
        assertEquals("a refused enable must not write", 0, dao.saveCount)
        assertNull(dao.stored)
    }

    @Test
    fun `setEnabled refuses to enable when the row exists but never consented`() {
        val dao = FakeAiSettingsDao().apply {
            seed(AiSettingsEntity(isEnabled = false, consentedAtEpochMillis = null))
        }
        val repository = RoomAiSettingsRepository(dao)

        assertThrows(IllegalArgumentException::class.java) { runTest { repository.setEnabled(true) } }

        assertEquals(0, dao.saveCount)
    }

    @Test
    fun `setEnabled enables once consent is on record`() = runTest {
        val dao = FakeAiSettingsDao().apply {
            seed(AiSettingsEntity(isEnabled = false, consentedAtEpochMillis = 777L))
        }

        RoomAiSettingsRepository(dao).setEnabled(true)

        assertTrue(dao.stored!!.isEnabled)
        assertEquals(777L, dao.stored!!.consentedAtEpochMillis)
    }

    @Test
    fun `setEnabled can always switch off and keeps consent on record`() = runTest {
        // Switching the advisor off is not a withdrawal of consent, so re-enabling later must not
        // demand a second consent dialog.
        val dao = FakeAiSettingsDao().apply {
            seed(AiSettingsEntity(isEnabled = true, consentedAtEpochMillis = 777L))
        }

        RoomAiSettingsRepository(dao).setEnabled(false)

        val stored = dao.stored!!
        assertFalse(stored.isEnabled)
        assertEquals(777L, stored.consentedAtEpochMillis)
    }

    @Test
    fun `setEnabled off works even when nothing was ever stored`() = runTest {
        val dao = FakeAiSettingsDao()

        RoomAiSettingsRepository(dao).setEnabled(false)

        assertFalse(dao.stored!!.isEnabled)
        assertNull(dao.stored!!.consentedAtEpochMillis)
    }

    @Test
    fun `every write lands on the single settings row`() = runTest {
        // The repository never sets `id`, so both writes rely on the entity's default. Pinned
        // because a read keyed on one id and a write keyed on another would silently stop
        // persisting anything.
        val dao = FakeAiSettingsDao()
        val repository = RoomAiSettingsRepository(dao)

        repository.grantConsentAndEnable()
        assertEquals(AI_SETTINGS_ID, dao.stored!!.id)

        repository.setEnabled(false)
        assertEquals(AI_SETTINGS_ID, dao.stored!!.id)
    }
}
