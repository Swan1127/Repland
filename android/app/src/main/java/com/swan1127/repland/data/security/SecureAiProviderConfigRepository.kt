package com.swan1127.repland.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.swan1127.repland.domain.model.AiProviderConfig
import com.swan1127.repland.domain.model.AiProviderSecret
import com.swan1127.repland.domain.model.DEFAULT_AGNES_BASE_URL
import com.swan1127.repland.domain.model.DEFAULT_AGNES_MODEL
import com.swan1127.repland.domain.ports.AiProviderConfigRepository
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A deliberately small Keystore-backed store. The preference file only holds
 * encrypted key bytes and an IV; the plain API key never enters Room, exports,
 * logs, or Compose UI state.
 */
class SecureAiProviderConfigRepository(context: Context) : AiProviderConfigRepository {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(readConfig())

    override fun observe(): Flow<AiProviderConfig> = state.asStateFlow()

    override suspend fun save(baseUrl: String, model: String, apiKey: String) {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val normalizedModel = model.trim().also { require(it.isNotBlank()) { "请选择或填写模型名称。" } }
        val editor = preferences.edit()
            .putString(KEY_BASE_URL, normalizedUrl)
            .putString(KEY_MODEL, normalizedModel)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        if (apiKey.isNotBlank()) {
            val encrypted = encrypt(apiKey.trim())
            editor.putString(KEY_ENCRYPTED_API_KEY, encrypted.cipherText)
            editor.putString(KEY_API_KEY_IV, encrypted.iv)
        }
        editor.apply()
        state.value = readConfig()
    }

    override suspend fun clearApiKey() {
        preferences.edit()
            .remove(KEY_ENCRYPTED_API_KEY)
            .remove(KEY_API_KEY_IV)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
        state.value = readConfig()
    }

    override suspend fun readSecret(): AiProviderSecret? {
        val config = state.value
        val cipherText = preferences.getString(KEY_ENCRYPTED_API_KEY, null) ?: return null
        val iv = preferences.getString(KEY_API_KEY_IV, null) ?: return null
        val apiKey = runCatching { decrypt(cipherText, iv) }.getOrNull()?.takeIf(String::isNotBlank) ?: return null
        return AiProviderSecret(baseUrl = config.baseUrl, model = config.model, apiKey = apiKey)
    }

    private fun readConfig(): AiProviderConfig = AiProviderConfig(
        baseUrl = preferences.getString(KEY_BASE_URL, DEFAULT_AGNES_BASE_URL) ?: DEFAULT_AGNES_BASE_URL,
        model = preferences.getString(KEY_MODEL, DEFAULT_AGNES_MODEL) ?: DEFAULT_AGNES_MODEL,
        hasApiKey = preferences.contains(KEY_ENCRYPTED_API_KEY) && preferences.contains(KEY_API_KEY_IV),
        updatedAtEpochMillis = preferences.getLong(KEY_UPDATED_AT, 0L).takeIf { it > 0L },
    )

    private fun normalizeBaseUrl(raw: String): String {
        val normalized = raw.trim().trimEnd('/')
        val uri = runCatching { URI(normalized) }.getOrNull()
        require(uri?.scheme == "https" && !uri.host.isNullOrBlank()) {
            "服务地址必须是以 https:// 开头的完整地址。"
        }
        return normalized
    }

    private fun encrypt(value: String): EncryptedValue {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return EncryptedValue(
            cipherText = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    private fun decrypt(cipherText: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = javax.crypto.spec.GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private data class EncryptedValue(val cipherText: String, val iv: String)

    private companion object {
        const val PREFERENCES = "secure_ai_provider_config"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_ENCRYPTED_API_KEY = "encrypted_api_key"
        const val KEY_API_KEY_IV = "api_key_iv"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_ALIAS = "repland_ai_provider_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
