package com.swan1127.repland.data

import android.content.SharedPreferences
import com.swan1127.repland.domain.model.ProfileIdentity
import com.swan1127.repland.domain.model.UserIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * The store is the boundary where the pure rules in [ProfileIdentity] meet actual disk + prefs.
 *
 * Three behaviours here are not one-case tests but guardrails:
 *  - `nickname` is sanitised on the way out as well as in, so a corrupted or hand-edited prefs
 *    value can never reach the UI as garbage;
 *  - `avatarPath()` self-heals a stale pointer — a recorded path whose file vanished must not be
 *    handed back, or the UI tries to decode a missing file;
 *  - a new avatar replaces the old one, so a renamed extension never leaves two avatar files.
 */
class ProfileStoreTest {

    /** Minimal in-memory SharedPreferences, just enough for the store's two keys. */
    private class FakePrefs : SharedPreferences {
        private val map = HashMap<String, Any?>()
        private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String, def: String?): String? = map[key] as? String ?: def
        override fun getStringSet(key: String, def: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? MutableSet<String>) ?: def
        override fun getInt(key: String, def: Int): Int = map[key] as? Int ?: def
        override fun getLong(key: String, def: Long): Long = map[key] as? Long ?: def
        override fun getFloat(key: String, def: Float): Float = map[key] as? Float ?: def
        override fun getBoolean(key: String, def: Boolean): Boolean = map[key] as? Boolean ?: def
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                map[key] = value; return this
            }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                map[key] = values; return this
            }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor { map[key] = value; return this }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor { map[key] = value; return this }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor { map[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { map[key] = value; return this }
            override fun remove(key: String): SharedPreferences.Editor { map.remove(key); return this }
            override fun clear(): SharedPreferences.Editor { map.clear(); return this }
            override fun commit(): Boolean { notifyListeners(); return true }
            override fun apply() { notifyListeners() }
            private fun notifyListeners() {
                listeners.forEach { it.onSharedPreferenceChanged(this@FakePrefs, null) }
            }
        }
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
            listeners.add(l)
        }
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
            listeners.remove(l)
        }
    }

    private fun store(): Pair<ProfileStore, File> {
        val dir = createTempDir("profile-store")
        return ProfileStore(dir, FakePrefs()) to dir
    }

    private val png1x1 = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(),
        0x1A.toByte(), 0x0A.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte(),
        0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x08.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1F.toByte(),
        0x15.toByte(), 0xC4.toByte(), 0x89.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x0D.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(), 0x54.toByte(), 0x78.toByte(),
        0x9C.toByte(), 0x63.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x05.toByte(), 0x00.toByte(), 0x01.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x2D.toByte(),
        0xB4.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x49.toByte(),
        0x45.toByte(), 0x4E.toByte(), 0x44.toByte(), 0xAE.toByte(), 0x42.toByte(), 0x60.toByte(),
        0x82.toByte(),
    )

    @Test
    fun `nickname round-trips and is sanitised on read`() {
        val (s) = store()
        s.nickname = "  小   茂  "
        assertEquals("小 茂", s.nickname)
    }

    @Test
    fun `avatarPath is null when nothing stored`() {
        val (s) = store()
        assertNull(s.avatarPath())
    }

    @Test
    fun `avatarPath self-heals a stale pointer`() {
        val (s, _) = store()
        // Save a real avatar, then delete the file out from under the store, then re-read:
        // the recorded path must be dropped, never handed back to be decoded.
        s.saveAvatar(ByteArrayInputStream(png1x1))
        val path = s.avatarPath()!!
        assertTrue(File(path).exists())
        File(path).delete()
        assertNull(s.avatarPath())
    }

    @Test
    fun `saveAvatar writes the file and records the path`() {
        val (s, dir) = store()
        val result = s.saveAvatar(ByteArrayInputStream(png1x1))
        assertTrue(result is com.swan1127.repland.domain.model.AvatarSaveResult.Saved)
        val path = s.avatarPath()
        assertTrue(path != null)
        assertTrue(File(path!!).exists())
        assertTrue(path.startsWith(dir.absolutePath))
    }

    @Test
    fun `clearAvatar removes the file and the recorded path`() {
        val (s) = store()
        s.saveAvatar(ByteArrayInputStream(png1x1))
        val path = s.avatarPath()!!
        assertTrue(File(path).exists())
        s.clearAvatar()
        assertNull(s.avatarPath())
        assertFalse(File(path).exists())
    }

    @Test
    fun `saving a new avatar replaces the old extension file`() {
        val (s, dir) = store()
        s.saveAvatar(ByteArrayInputStream(png1x1))
        // Simulate an older avatar of a different extension left behind, then save again.
        val stale = File(dir, "avatar.gif")
        stale.writeBytes(byteArrayOf(0x47, 0x49, 0x46))
        s.saveAvatar(ByteArrayInputStream(png1x1))
        val avatars = dir.listFiles { f -> f.name.startsWith("avatar.") }?.toList().orEmpty()
        assertEquals(1, avatars.size)
        assertTrue(avatars.first().name.endsWith("avatar.png"))
    }

    @Test
    fun `identity combines nickname and avatar path`() {
        val (s) = store()
        s.nickname = "小茂"
        s.saveAvatar(ByteArrayInputStream(png1x1))
        val id: UserIdentity = s.identity()
        assertEquals("小茂", id.nickname)
        assertTrue(id.avatarPath != null)
    }

    @Test
    fun `identity is empty avatar when none set`() {
        val (s) = store()
        s.nickname = ""
        val id = s.identity()
        assertEquals(UserIdentity.EMPTY, id)
    }
}
