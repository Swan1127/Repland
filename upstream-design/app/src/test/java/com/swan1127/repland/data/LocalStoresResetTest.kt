package com.swan1127.repland.data

import com.swan1127.repland.domain.model.DefaultAgentTeam
import com.swan1127.repland.domain.model.UserIdentity
import com.swan1127.repland.testkit.InMemoryPreferences
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStoresResetTest {
    @Test
    fun `auxiliary reset removes all learning notes and preferences`() {
        val prefs = InMemoryPreferences()
        prefs.edit().putString("kwl", "saved notes").putString("reflections", "reflection")
            .putString("timetable-sets", "saved sets").putInt("focus-minutes", 50).commit()
        AuxiliaryStore(prefs).clearAllLocalData()
        assertTrue(prefs.getAll().isEmpty())
    }

    @Test
    fun `team reset restores the default members and user`() {
        val prefs = InMemoryPreferences()
        prefs.edit().putString("agents", "custom team").putString("user", "custom user").commit()
        val store = AgentProfileStore(prefs)
        store.clearAllLocalData()
        assertTrue(prefs.getAll().isEmpty())
        assertEquals(DefaultAgentTeam.agents, store.loadAgents())
        assertEquals(DefaultAgentTeam.user, store.loadUser())
    }

    @Test
    fun `profile reset removes avatar files and nickname together`() {
        val dir = Files.createTempDirectory("repland-profile-reset").toFile()
        try {
            val avatar = File(dir, "avatar.png").apply { writeBytes(byteArrayOf(1, 2)) }
            val oldAvatar = File(dir, "avatar.gif").apply { writeBytes(byteArrayOf(3)) }
            val prefs = InMemoryPreferences()
            prefs.edit().putString("nickname", "test user").putString("avatar-path", avatar.absolutePath).commit()
            val store = ProfileStore(dir, prefs)
            store.clearAllLocalData()
            assertFalse(avatar.exists())
            assertFalse(oldAvatar.exists())
            assertTrue(prefs.getAll().isEmpty())
            assertEquals(UserIdentity.EMPTY, store.identity())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `failed preference deletion is not reported as a successful reset`() {
        val prefs = InMemoryPreferences().apply { commitsSucceed = false }
        assertThrows(IllegalStateException::class.java) { AuxiliaryStore(prefs).clearAllLocalData() }
        assertThrows(IllegalStateException::class.java) { AgentProfileStore(prefs).clearAllLocalData() }
    }
}
