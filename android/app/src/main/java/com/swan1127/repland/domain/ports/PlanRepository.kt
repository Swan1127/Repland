package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.PlanDraft
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface PlanRepository {
    fun observeTracks(): Flow<List<com.swan1127.repland.domain.model.RhythmTrack>>
    suspend fun saveTracks(tracks: List<com.swan1127.repland.domain.model.RhythmTrack>)
    fun observeAssistantWorkspace(): Flow<com.swan1127.repland.domain.model.AssistantWorkspace?>
    suspend fun saveAssistantWorkspace(value: com.swan1127.repland.domain.model.AssistantWorkspace?)
    fun observeDraft(): Flow<PlanDraft?>
    suspend fun saveDraft(draft: PlanDraft?)
    fun observeTaskOrder(): Flow<List<String>>
    suspend fun saveTaskOrder(ids: List<String>)
    fun observeCanUndoTaskOrder(): Flow<Boolean>
    suspend fun undoTaskOrder()
    suspend fun saveTasksAndPlace(tasks: List<com.swan1127.repland.domain.model.TaskDraft>, segments: List<com.swan1127.repland.domain.model.PlannedSegment>)
    fun observeCurrentPlan(): Flow<ConfirmedPlan?>

    fun observePlanHistory(): Flow<List<ConfirmedPlan>>

    suspend fun accept(draft: PlanDraft)

    suspend fun restore(planId: String)

    suspend fun clearCurrentPlan()

    suspend fun setSegmentLocked(segmentId: String, isLocked: Boolean)

    suspend fun placeTask(taskId: String, date: LocalDate, startMinute: Int, endMinute: Int, trackId: String)

    /** Moves one schedule instance while leaving the reusable task and its records untouched. */
    suspend fun movePlacement(segmentId: String, startMinute: Int, endMinute: Int, trackId: String)

    /** Removes only one schedule instance; the reusable task and its history remain intact. */
    suspend fun removePlacement(segmentId: String)
}
