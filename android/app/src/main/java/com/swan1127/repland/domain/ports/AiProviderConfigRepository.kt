package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.AiProviderConfig
import com.swan1127.repland.domain.model.AiProviderSecret
import kotlinx.coroutines.flow.Flow

/**
 * Keeps provider metadata separate from the opt-in switch. Saving a key never
 * sends it anywhere; a network request still needs an explicit assistant action.
 */
interface AiProviderConfigRepository {
    fun observe(): Flow<AiProviderConfig>

    /** A blank [apiKey] retains an existing key, which lets Settings stay masked. */
    suspend fun save(baseUrl: String, model: String, apiKey: String)

    suspend fun clearApiKey()

    /** Only a debug-only network adapter may ask for the decrypted value. */
    suspend fun readSecret(): AiProviderSecret?
}
