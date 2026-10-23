package com.swan1127.repland.data.ai

/**
 * One HTTP round trip, normalised so the codec never touches IO.
 *
 * A transport failure and an HTTP error status are kept apart on purpose: "we could not
 * reach the provider" and "the provider answered 429" are different things to tell a user,
 * and collapsing them into one null would lose that.
 */
sealed interface AiHttpOutcome {
    /** The provider answered. Any status code, including 4xx and 5xx, lands here. */
    data class Responded(val statusCode: Int, val body: String) : AiHttpOutcome

    /** No answer within the deadline. */
    data object TimedOut : AiHttpOutcome

    /** DNS, TLS, socket or connection failure — no HTTP exchange completed. */
    data object Unreachable : AiHttpOutcome
}

/**
 * The only IO the AI adapter performs, behind an interface so the decision-making half
 * ([OpenAiCompatibleCodec]) can be exercised on a plain JVM.
 *
 * Implementations must be the sole holders of the decrypted key; it is passed per call
 * rather than stored, so nothing else in the app can read it.
 */
interface AiHttpTransport {
    /** `POST` with a JSON body. [apiKey] becomes `Authorization: Bearer <apiKey>`. */
    suspend fun post(url: String, apiKey: String, jsonBody: String): AiHttpOutcome

    /** `GET`, used to list the models a provider offers. */
    suspend fun get(url: String, apiKey: String): AiHttpOutcome
}
