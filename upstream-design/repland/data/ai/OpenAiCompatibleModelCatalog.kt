package com.swan1127.repland.data.ai

import com.swan1127.repland.data.ai.AiHttpOutcome
import com.swan1127.repland.data.ai.AiHttpTransport
import com.swan1127.repland.data.ai.OpenAiCompatibleCodec
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.ports.AiModelCatalog
import com.swan1127.repland.domain.ports.AiModelCatalogResult
import com.swan1127.repland.domain.ports.AiProviderConfigRepository

/**
 * `GET {baseUrl}/models`, shared by both build types.
 *
 * The endpoint is the OpenAI-compatible convention, so it works for every provider whose
 * base URL already ends in the version segment (OpenAI, Kimi, GLM, DashScope, Groq,
 * OpenRouter). A provider that keeps the version segment off its base URL may not serve
 * `/models`; that is reported as a failure rather than worked around with a guessed second
 * URL, because the text field remains editable and a wrong guess would be worse than a
 * message.
 *
 * A missing key is reported without attempting a network request.
 */
class OpenAiCompatibleModelCatalog @JvmOverloads constructor(
    private val configRepository: AiProviderConfigRepository,
    private val transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
) : AiModelCatalog {

    override suspend fun listModels(): AiModelCatalogResult {
        val secret = configRepository.readSecret()
            ?: return failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
        if (secret.apiKey.isBlank()) return failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)

        val url = "${secret.baseUrl.trimEnd('/')}/models"
        return when (val outcome = transport.get(url, secret.apiKey)) {
            is AiHttpOutcome.Responded -> {
                if (outcome.statusCode !in 200..299) {
                    failed(OpenAiCompatibleCodec.mapHttpStatus(outcome.statusCode))
                } else {
                    val ids = OpenAiCompatibleCodec.parseModelIds(outcome.body)
                    // An empty list is not a useful answer: showing "no models" would look
                    // like the provider is broken when the shape was merely unexpected.
                    if (ids.isEmpty()) failed(AiAdvisorFailureReason.INVALID_RESPONSE)
                    else AiModelCatalogResult.Models(ids)
                }
            }

            AiHttpOutcome.TimedOut -> failed(AiAdvisorFailureReason.TIMEOUT)
            AiHttpOutcome.Unreachable -> failed(AiAdvisorFailureReason.TRANSPORT_FAILURE)
        }
    }

    private fun failed(reason: AiAdvisorFailureReason): AiModelCatalogResult =
        AiModelCatalogResult.Failed(reason)
}
