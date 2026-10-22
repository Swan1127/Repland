package com.swan1127.repland.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.swan1127.repland.domain.model.ProfileEvidence
import com.swan1127.repland.domain.model.ProfileEvidenceScope
import com.swan1127.repland.domain.model.ProfileEvidenceSource

@Entity(
    tableName = "profile_evidence",
    indices = [Index(value = ["sourceFingerprint"], unique = true)],
)
data class ProfileEvidenceEntity(
    @PrimaryKey val id: String,
    val scope: String,
    val conclusion: String,
    /** Stable, pipe-delimited UUIDs. UUIDs cannot contain the delimiter. */
    val sourceLogIds: String,
    /** Prevents regenerating identical evidence and thereby protects user edits. */
    val sourceFingerprint: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isUserEdited: Boolean,
)

fun ProfileEvidenceEntity.toDomain(
    sources: List<ProfileEvidenceSource>,
): ProfileEvidence = ProfileEvidence(
    id = id,
    scope = ProfileEvidenceScope.valueOf(scope),
    conclusion = conclusion,
    sources = sources,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    isUserEdited = isUserEdited,
)

fun ProfileEvidenceEntity.sourceLogIdList(): List<String> =
    sourceLogIds.split('|').filter(String::isNotBlank)
