package com.swan1127.repland.domain.model

/** Local preference only. Consent is required before the AI switch can be enabled. */
data class AiPreferences(
    val isEnabled: Boolean = false,
    val consentedAtEpochMillis: Long? = null,
) {
    val hasExplicitConsent: Boolean
        get() = consentedAtEpochMillis != null
}
