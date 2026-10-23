package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.AiAdvisorFailureReason

/** What a provider answered when asked which models it offers. */
sealed interface AiModelCatalogResult {
    data class Models(val ids: List<String>) : AiModelCatalogResult

    data class Failed(val reason: AiAdvisorFailureReason) : AiModelCatalogResult
}

/**
 * Lists the model ids the configured provider currently serves.
 *
 * This exists because a baked-in model name goes stale on the provider's schedule, not ours:
 * DeepSeek retired `deepseek-chat` and Alibaba moved its DashScope host, both without the
 * app changing. Reading `GET {baseUrl}/models` turns "which model do I type here" from a
 * guess into a choice, and it degrades cleanly — a provider that does not implement the
 * endpoint leaves the field free-form.
 *
 * Like [AiProviderConfigRepository], only a debug-only adapter is allowed to implement this:
 * the request needs the decrypted key, and release builds stay local-only.
 */
interface AiModelCatalog {
    suspend fun listModels(): AiModelCatalogResult
}

/** The release-build and not-configured behaviour: no list, and the field stays free-form. */
object NoOpAiModelCatalog : AiModelCatalog {
    override suspend fun listModels(): AiModelCatalogResult =
        AiModelCatalogResult.Failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
}
