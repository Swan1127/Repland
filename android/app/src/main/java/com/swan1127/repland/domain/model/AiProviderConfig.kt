package com.swan1127.repland.domain.model

/**
 * Non-secret provider metadata that can be shown in Settings. The API key is
 * intentionally excluded so Compose state, Room exports and screenshots never
 * carry it.
 */
data class AiProviderConfig(
    val baseUrl: String = DEFAULT_AGNES_BASE_URL,
    val model: String = DEFAULT_AGNES_MODEL,
    val hasApiKey: Boolean = false,
    val updatedAtEpochMillis: Long? = null,
)

/** Read only by a debug transport at request time; never place this in UI state. */
data class AiProviderSecret(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)

const val DEFAULT_AGNES_BASE_URL = "https://apihub.agnes-ai.com/v1"
const val DEFAULT_AGNES_MODEL = "agnes-2.5-flash"
