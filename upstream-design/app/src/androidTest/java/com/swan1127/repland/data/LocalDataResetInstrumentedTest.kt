package com.swan1127.repland.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.data.room.ReplandDatabase
import com.swan1127.repland.data.room.RoomDataManagementRepository
import com.swan1127.repland.data.security.SecureAiProviderConfigRepository
import com.swan1127.repland.domain.model.AiProviderConfig
import com.swan1127.repland.domain.model.DefaultAgentTeam
import com.swan1127.repland.domain.model.UserIdentity
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** All rows, preferences and files belong only to this test; the installed user's data is untouched. */
@RunWith(AndroidJUnit4::class)
class LocalDataResetInstrumentedTest {
    @Test
    fun completeResetClearsEveryRoomTableAndIndependentStore() = runBlocking {
        val context = ResetTestContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val database = Room.inMemoryDatabaseBuilder(context, ReplandDatabase::class.java).build()
        try {
            val sql = database.openHelper.writableDatabase
            val tables = mutableListOf<String>()
            sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')").use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            assertTrue("The test must exercise real application tables", tables.isNotEmpty())

            // Synthetic rows in every generated Room table catch omissions when a new entity is added.
            for (table in tables) {
                require(table.matches(Regex("[A-Za-z0-9_]+")))
                val names = mutableListOf<String>()
                val values = mutableListOf<Any?>()
                sql.query("PRAGMA table_info(`$table`)").use { columns ->
                    while (columns.moveToNext()) {
                        val name = columns.getString(columns.getColumnIndexOrThrow("name"))
                        val type = columns.getString(columns.getColumnIndexOrThrow("type"))
                        require(name.matches(Regex("[A-Za-z0-9_]+")))
                        names += "`$name`"
                        values += when {
                            type.contains("INT", true) -> 1L
                            type.contains("REAL", true) -> 1.0
                            type.contains("BLOB", true) -> byteArrayOf(1)
                            else -> "instrumented-reset-test"
                        }
                    }
                }
                sql.execSQL("INSERT INTO `$table` (${names.joinToString()}) VALUES (${names.joinToString { "?" }})", values.toTypedArray())
                sql.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1L, cursor.getLong(0))
                }
            }

            val auxiliary = AuxiliaryStore(context)
            val agents = AgentProfileStore(context)
            val profile = ProfileStore(context)
            val chat = GroupChatHistoryStore(context)
            val focus = FocusSessionStore(context)
            val providerPreferences = context.getSharedPreferences("secure-test-provider", Context.MODE_PRIVATE)
            var encryptionKeyRemoved = false

            listOf("repland-aux", "agent_team", GroupChatHistoryStore.PREFERENCES, "repland_focus_session").forEach { name ->
                assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("session", "synthetic test data").commit())
            }
            profile.nickname = "instrumented reset user"
            profile.saveAvatar(ByteArrayInputStream(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)))
            val avatar = File(requireNotNull(profile.avatarPath()))
            assertTrue(avatar.exists())
            assertTrue(providerPreferences.edit().putString("base_url", "https://example.invalid/v1")
                .putString("model", "test-model").putString("encrypted_api_key", "synthetic-encrypted-bytes")
                .putString("api_key_iv", "synthetic-iv").commit())
            // Inject a test-only Keystore action: never address the real app's key alias.
            val provider = SecureAiProviderConfigRepository(providerPreferences) { encryptionKeyRemoved = true }
            assertTrue(provider.observe().first().hasApiKey)

            val reset = LocalDataResetCoordinator(
                auxiliary::clearAllLocalData, agents::clearAllLocalData, profile::clearAllLocalData,
                provider::clearAllLocalData, chat::clear, focus::clear,
            )
            RoomDataManagementRepository(database, reset::clearAll).clearAllLocalData()

            for (table in tables) {
                sql.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Old rows remain in $table", 0L, cursor.getLong(0))
                }
            }
            assertTrue(context.testPreferences().all { it.all.isEmpty() })
            assertEquals(UserIdentity.EMPTY, profile.identity())
            assertFalse(avatar.exists())
            assertEquals(DefaultAgentTeam.agents, agents.loadAgents())
            assertEquals(DefaultAgentTeam.user, agents.loadUser())
            assertNull(focus.load())
            assertNull(chat.load(java.time.LocalDate.now()))
            assertTrue(encryptionKeyRemoved)
            assertEquals(AiProviderConfig(), provider.observe().first())
            assertNull(provider.readSecret())
        } finally {
            database.close()
            context.dispose()
        }
    }

    private class ResetTestContext(base: Context) : ContextWrapper(base) {
        private val prefix = "instrumented-reset-${UUID.randomUUID()}"
        private val root = File(base.cacheDir, prefix).apply { check(mkdirs()) }
        private val preferenceNames = linkedSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = "$prefix-$name"
            preferenceNames += isolated
            return baseContext.getSharedPreferences(isolated, mode)
        }
        fun testPreferences(): List<SharedPreferences> = preferenceNames.map { baseContext.getSharedPreferences(it, MODE_PRIVATE) }
        fun dispose() {
            preferenceNames.forEach { baseContext.deleteSharedPreferences(it) }
            check(root.name == prefix && root.parentFile == baseContext.cacheDir)
            root.deleteRecursively()
        }
    }
}
