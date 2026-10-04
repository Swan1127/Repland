package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.TaskCaptureDraft
import com.swan1127.repland.domain.model.TaskDraft
import kotlinx.coroutines.flow.Flow

interface TaskCaptureRepository {
    fun observeDraft(): Flow<TaskCaptureDraft?>
    suspend fun saveDraft(draft: TaskCaptureDraft)
    suspend fun discard(id: String)
    /** Persist the task and consume its draft together; retry must not create another task. */
    suspend fun commit(capture: TaskCaptureDraft, task: TaskDraft)
}
