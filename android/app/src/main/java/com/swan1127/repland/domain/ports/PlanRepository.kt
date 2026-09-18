package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.PlanDraft
import kotlinx.coroutines.flow.Flow

interface PlanRepository {
    fun observeCurrentPlan(): Flow<ConfirmedPlan?>

    fun observePlanHistory(): Flow<List<ConfirmedPlan>>

    suspend fun accept(draft: PlanDraft)

    suspend fun restore(planId: String)

    suspend fun clearCurrentPlan()

    suspend fun setSegmentLocked(segmentId: String, isLocked: Boolean)
}
