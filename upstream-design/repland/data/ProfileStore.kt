package com.swan1127.repland.data

import android.content.Context
import android.content.SharedPreferences
import com.swan1127.repland.domain.model.AvatarSaveResult
import com.swan1127.repland.domain.model.ProfileIdentity
import com.swan1127.repland.domain.model.UserIdentity
import java.io.File
import java.io.InputStream

/**
 * Where the cosmetic identity actually sits.
 *
 * The nickname is a short string in `SharedPreferences`; the avatar is a file in app-private
 * storage — **never a `content://` URI**. A persisted URI is the tempting shortcut, but the grant
 * behind it is revocable and, on a local-first app that prides itself on keeping data on the
 * device, shipping the bytes into `filesDir` is the honest move: the avatar survives a reboot and
 * never points at another app's content.
 *
 * The class is constructor-injected with the directory and the preferences so the rules in
 * `ProfileIdentity` — and the file write itself — can be exercised from a JVM test against a temp
 * directory. Only the `Context` secondary constructor touches Android.
 */
class ProfileStore(private val dir: File, private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, "profile"),
        context.applicationContext.getSharedPreferences("replanandroid-profile", Context.MODE_PRIVATE),
    )

    private val avatarDir = dir

    /** The stored nickname, already sanitised; "" means not set. */
    var nickname: String
        get() = ProfileIdentity.sanitizeNickname(prefs.getString(KEY_NICKNAME, "") ?: "")
        set(value) {
            prefs.edit().putString(KEY_NICKNAME, ProfileIdentity.sanitizeNickname(value)).apply()
        }

    /**
     * Path of the stored avatar, or null when none is set.
     *
     * Self-healing: if the recorded path points at a file that is no longer there, the stale
     * pointer is dropped rather than handed back to the UI, which would otherwise try to decode a
     * path that does not exist.
     */
    fun avatarPath(): String? {
        val stored = prefs.getString(KEY_AVATAR_PATH, null) ?: return null
        return if (File(stored).exists()) stored else {
            prefs.edit().remove(KEY_AVATAR_PATH).apply()
            null
        }
    }

    /** Writes [source] as the avatar and records its path, replacing any previous avatar. */
    fun saveAvatar(source: InputStream): AvatarSaveResult {
        val result = ProfileIdentity.saveAvatar(avatarDir, source)
        return when (result) {
            is AvatarSaveResult.Saved -> {
                clearAvatarFile(except = result.path)
                prefs.edit().putString(KEY_AVATAR_PATH, result.path).apply()
                result
            }
            else -> result
        }
    }

    /** Removes the avatar: both the file and the recorded path. */
    fun clearAvatar() {
        clearAvatarFile(except = null)
        prefs.edit().remove(KEY_AVATAR_PATH).apply()
    }

    /** nickname + current avatar path as one value. */
    fun identity(): UserIdentity = UserIdentity(nickname = nickname, avatarPath = avatarPath())

    /** Deletes the private avatar files as well as all nickname/avatar preferences. */
    fun clearAllLocalData() {
        var filesCleared = true
        if (avatarDir.exists()) {
            val files = avatarDir.listFiles { file -> file.name.startsWith("avatar.") }
            if (files == null) filesCleared = false
            files?.forEach { file -> if (file.exists() && !file.delete()) filesCleared = false }
        }
        val preferencesCleared = prefs.edit().clear().commit()
        check(filesCleared && preferencesCleared) { "个人资料未能全部清除。" }
    }

    /** Deletes any avatar file except [keep], so a new save does not leave a stale extension behind. */
    private fun clearAvatarFile(except: String?) {
        avatarDir.listFiles { file -> file.name.startsWith("avatar.") && file.absolutePath != except }
            ?.forEach { it.delete() }
    }

    private companion object {
        const val KEY_NICKNAME = "nickname"
        const val KEY_AVATAR_PATH = "avatar-path"
    }
}
