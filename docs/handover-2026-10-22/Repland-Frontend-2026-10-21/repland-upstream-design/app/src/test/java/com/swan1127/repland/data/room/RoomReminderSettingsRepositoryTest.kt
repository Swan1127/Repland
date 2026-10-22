package com.swan1127.repland.data.room

import com.swan1127.repland.testkit.FakeReminderSettingsDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RoomReminderSettingsRepository] is the smallest repository in the project: one boolean behind
 * one row. It is pinned anyway because the read and the write must agree on *which* row they mean —
 * a repository that read by one key and wrote under another would look correct and persist nothing.
 *
 * Reachable from a plain JVM test because the constructor takes the `ReminderSettingsDao` interface.
 */
class RoomReminderSettingsRepositoryTest {

    @Test
    fun `observe falls back to disabled when no row exists`() = runTest {
        assertFalse(RoomReminderSettingsRepository(FakeReminderSettingsDao()).observe().first().isEnabled)
    }

    @Test
    fun `observe maps a stored row`() = runTest {
        val dao = FakeReminderSettingsDao().apply { seed(ReminderSettingsEntity(isEnabled = true)) }

        assertTrue(RoomReminderSettingsRepository(dao).observe().first().isEnabled)
    }

    @Test
    fun `setEnabled writes the flag in both directions`() = runTest {
        val dao = FakeReminderSettingsDao()
        val repository = RoomReminderSettingsRepository(dao)

        repository.setEnabled(true)
        assertTrue(dao.stored!!.isEnabled)

        repository.setEnabled(false)
        assertFalse(dao.stored!!.isEnabled)
        assertEquals(2, dao.saveCount)
    }

    @Test
    fun `every read and write uses the single settings row`() = runTest {
        val dao = FakeReminderSettingsDao()

        RoomReminderSettingsRepository(dao).setEnabled(true)

        assertEquals(REMINDER_SETTINGS_ID, dao.stored!!.id)
    }
}
