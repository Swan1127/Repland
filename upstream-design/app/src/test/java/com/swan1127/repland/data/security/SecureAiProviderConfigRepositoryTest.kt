package com.swan1127.repland.data.security

import com.swan1127.repland.domain.model.AiProviderConfig
import com.swan1127.repland.testkit.InMemoryPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SecureAiProviderConfigRepositoryTest {
    @Test
    fun `complete reset removes provider metadata credential bytes and key alias and refreshes observers`() = runTest {
        val prefs = InMemoryPreferences()
        prefs.edit().putString("base_url", "https://example.invalid/v1").putString("model", "test-model")
            .putString("encrypted_api_key", "encrypted-test-bytes").putString("api_key_iv", "test-iv")
            .putLong("updated_at", 10).commit()
        var keyDeleted = false
        val repository = SecureAiProviderConfigRepository(prefs) { keyDeleted = true }
        assertTrue(repository.observe().first().hasApiKey)

        repository.clearAllLocalData()

        assertTrue(keyDeleted)
        assertTrue(prefs.getAll().isEmpty())
        assertEquals(AiProviderConfig(), repository.observe().first())
        assertNull(repository.readSecret())
    }

    @Test
    fun `keystore deletion failure still invalidates cached provider configuration`() = runTest {
        val prefs = InMemoryPreferences()
        prefs.edit().putString("encrypted_api_key", "encrypted-test-bytes").putString("api_key_iv", "test-iv").commit()
        val repository = SecureAiProviderConfigRepository(prefs) { error("keystore unavailable") }
        try {
            repository.clearAllLocalData()
            fail("Reset must not report success")
        } catch (_: IllegalStateException) {
            assertFalse(repository.observe().first().hasApiKey)
            assertNull(repository.readSecret())
        }
    }
}
