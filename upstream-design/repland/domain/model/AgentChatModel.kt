package com.swan1127.repland.domain.model

/**
 * One turn of one group-chat member.
 *
 * This is a **prose** port, not a function-calling one: the transport returns a single
 * complete JSON object with no tool protocol, so the model is asked for a sentence a human
 * would have said. Every fact it is allowed to cite — today's occupancy, the free windows,
 * what the earlier members already concluded — is placed in the user prompt by
 * [GroupChatEngine], which means a role cannot invent an availability figure: if a number
 * is not in the prompt, the only way it can appear in the answer is by hallucinating, and
 * the system prompt forbids exactly that.
 *
 * There is deliberately no local implementation. A failed call must surface as
 * [AgentChatResult.Failed] rather than as a canned sentence, because a canned sentence is
 * indistinguishable from a real answer on screen — which is the failure mode this whole
 * rewrite exists to remove.
 */
interface AgentChatModel {
    suspend fun say(systemPrompt: String, userPrompt: String): AgentChatResult
}

sealed interface AgentChatResult {
    /** The member's line, already stripped of fences and leading pleasantries. */
    data class Said(val text: String) : AgentChatResult

    data class Failed(val reason: AiAdvisorFailureReason) : AgentChatResult
}

/**
 * Explicit unavailable implementation for previews and callers without an AI provider.
 */
object NoOpAgentChatModel : AgentChatModel {
    override suspend fun say(systemPrompt: String, userPrompt: String): AgentChatResult =
        AgentChatResult.Failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
}
