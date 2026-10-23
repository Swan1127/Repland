package com.swan1127.repland.domain.model

import java.time.LocalDate

enum class GroupChatMessageRole { USER, ASSISTANT }

data class GroupChatMessage(
    val role: GroupChatMessageRole,
    val text: String,
    val speakerId: String? = null,
)

/** Working conversation and its uncommitted draft; never stores credentials. */
data class StoredGroupChatSession(
    val date: LocalDate,
    val messages: List<GroupChatMessage>,
    val pendingCandidates: List<ArrangementCandidate> = emptyList(),
)
