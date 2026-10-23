package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskPlacement

/** Saves an entire reviewed arrangement atomically; failure leaves tasks and plan unchanged. */
fun interface ArrangementCommitRepository {
    /** Placements must reference the explicit ids of drafts in this same batch. */
    suspend fun commit(drafts: List<TaskDraft>, placements: List<TaskPlacement>)
}
