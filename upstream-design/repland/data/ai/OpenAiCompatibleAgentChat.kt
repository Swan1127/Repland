package com.swan1127.repland.data.ai

import com.swan1127.repland.data.ai.AiHttpOutcome
import com.swan1127.repland.data.ai.AiHttpTransport
import com.swan1127.repland.data.ai.OpenAiCompatibleCodec
import com.swan1127.repland.domain.model.AgentChatModel
import com.swan1127.repland.domain.model.AgentChatResult
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.ports.AiProviderConfigRepository

/**
 * The group chat's real network call, shared by Debug and Release.
 *
 * It differs from the structured advisors in exactly one respect: it asks for **prose**, so
 * it does not validate against a contract schema. What it does instead is strip the
 * decoration models habitually add — a Markdown fence, a leading "好的，", a trailing
 * "希望这对你有帮助" — because that text is shown verbatim in a chat bubble.
 *
 * AppContainer supplies the user's provider configuration; tests can inject a transport.
 */
class OpenAiCompatibleAgentChat @JvmOverloads constructor(
    private val configRepository: AiProviderConfigRepository,
    private val transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
) : AgentChatModel {

    override suspend fun say(systemPrompt: String, userPrompt: String): AgentChatResult {
        val secret = configRepository.readSecret()
            ?: return fail(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
        if (secret.apiKey.isBlank()) return fail(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)

        val body = OpenAiCompatibleCodec.buildChatBody(
            model = secret.model,
            system = systemPrompt,
            user = userPrompt,
            temperature = 0.75,
        )
        val url = "${secret.baseUrl.trimEnd('/')}/chat/completions"

        return when (val outcome = transport.post(url, secret.apiKey, body)) {
            is AiHttpOutcome.Responded -> {
                if (outcome.statusCode !in 200..299) {
                    fail(OpenAiCompatibleCodec.mapHttpStatus(outcome.statusCode))
                } else {
                    val content = OpenAiCompatibleCodec.parseChatContent(outcome.body)
                    val cleaned = content?.let(::clean)
                    if (cleaned.isNullOrBlank()) {
                        fail(AiAdvisorFailureReason.INVALID_RESPONSE)
                    } else {
                        AgentChatResult.Said(cleaned)
                    }
                }
            }

            AiHttpOutcome.TimedOut -> fail(AiAdvisorFailureReason.TIMEOUT)
            AiHttpOutcome.Unreachable -> fail(AiAdvisorFailureReason.TRANSPORT_FAILURE)
        }
    }

    /**
     * Removes the wrappers a chat bubble must not show. Deliberately conservative: it does
     * not rewrite wording, only surrounding decoration, so a model that answers in an
     * unexpected style still shows up as itself.
     */
    private fun clean(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            // "```json\nbody\n```" → "body". The default keeps the text intact when the
            // model opened a fence and forgot to close it, which is the common typo.
            val withoutOpen = text.removePrefix("```")
            val body = withoutOpen.substringAfter('\n', withoutOpen)
            text = body.substringBeforeLast("```", body).trim()
        }
        text = PREAMBLE.replace(text, "")
        text = SIGN_OFF.replace(text, "")
        return text.trim().take(MAX_CHARS)
    }

    private fun fail(reason: AiAdvisorFailureReason) = AgentChatResult.Failed(reason)

    private companion object {
        /** 600 characters is already four chat bubbles; longer is a monologue. */
        const val MAX_CHARS = 600

        val PREAMBLE = Regex("^(好的|好|收到|当然|没问题|明白)[，,。.!！:：]?\\s*")
        val SIGN_OFF = Regex("(希望(这|以上)[^\\n]*|还有(什么|其他)[^\\n]*吗[？?]?|如需[^\\n]*)\\s*$")
    }
}
