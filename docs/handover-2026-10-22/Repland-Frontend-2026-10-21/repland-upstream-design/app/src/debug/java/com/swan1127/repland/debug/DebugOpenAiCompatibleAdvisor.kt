package com.swan1127.repland.debug

import com.swan1127.repland.data.ai.AiHttpOutcome
import com.swan1127.repland.data.ai.AiHttpTransport
import com.swan1127.repland.data.ai.OpenAiCompatibleCodec
import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiAdvisorRequest
import com.swan1127.repland.domain.model.AiAdvisorResult
import com.swan1127.repland.domain.ports.AiProviderConfigRepository

/**
 * The OpenAI-compatible AI advisor used by debug builds.
 *
 * `ReplandApplication` constructs this by reflection with exactly one argument — the
 * repository — so the class needs a genuine one-parameter constructor. `@JvmOverloads` is
 * what produces it: a plain defaulted second parameter compiles to a *synthetic*
 * constructor carrying a bit-mask argument that `getDeclaredConstructor(Class)` does not
 * match, and the app would silently fall back to `NoOpAiAdvisor`. A test asserts the
 * one-argument constructor exists, because that failure mode is silent.
 *
 * All decision-making lives in [OpenAiCompatibleCodec]; this class is only the wiring:
 * read the secret, build the body, make one call, translate the outcome.
 */
class DebugOpenAiCompatibleAdvisor @JvmOverloads constructor(
    private val configRepository: AiProviderConfigRepository,
    private val transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
) : AiAdvisor {

    override suspend fun request(request: AiAdvisorRequest): AiAdvisorResult {
        val secret = configRepository.readSecret()
            ?: return unavailable()
        if (secret.apiKey.isBlank()) return unavailable()

        val body = OpenAiCompatibleCodec.buildChatBody(secret.model, request)
        val url = "${secret.baseUrl.trimEnd('/')}/chat/completions"

        return when (val outcome = transport.post(url, secret.apiKey, body)) {
            is AiHttpOutcome.Responded -> {
                if (outcome.statusCode !in 200..299) {
                    AiAdvisorResult.Failed(OpenAiCompatibleCodec.mapHttpStatus(outcome.statusCode))
                } else {
                    val content = OpenAiCompatibleCodec.parseChatContent(outcome.body)
                    if (content == null) {
                        AiAdvisorResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE)
                    } else {
                        OpenAiCompatibleCodec.parseAdvice(request, content)
                    }
                }
            }

            AiHttpOutcome.TimedOut -> AiAdvisorResult.Failed(AiAdvisorFailureReason.TIMEOUT)
            AiHttpOutcome.Unreachable -> AiAdvisorResult.Failed(AiAdvisorFailureReason.TRANSPORT_FAILURE)
        }
    }

    /**
     * `Unavailable` rather than `Failed`: no provider is configured, which is the app's
     * normal state until the user opts in — not an error to report.
     */
    private fun unavailable(): AiAdvisorResult =
        AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
}
