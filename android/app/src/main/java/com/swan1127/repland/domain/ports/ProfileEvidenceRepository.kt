package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.ProfileEvidence
import kotlinx.coroutines.flow.Flow

interface ProfileEvidenceRepository {
    fun observe(): Flow<List<ProfileEvidence>>

    /** Adds new evidence for newly available confirmed feedback without overwriting edits. */
    suspend fun generateFromConfirmedFeedback()

    suspend fun updateConclusion(id: String, conclusion: String)

    suspend fun delete(id: String)
}
