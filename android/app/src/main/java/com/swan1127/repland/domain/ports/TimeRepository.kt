package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.TimeConstraintSettings
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface TimeRepository {
    fun observeWeeklyBlocks(): Flow<List<WeeklyTimeBlock>>

    fun observeDateOverrides(): Flow<List<DateOverride>>

    /** Advances for every user-confirmed change to planning constraints, including deletes. */
    fun observeTimeConstraintSettings(): Flow<TimeConstraintSettings>

    suspend fun saveWeeklyBlock(draft: WeeklyTimeBlockDraft)

    suspend fun saveWeeklyBlocks(drafts: List<WeeklyTimeBlockDraft>)

    suspend fun deleteWeeklyBlock(id: String)

    suspend fun saveDateOverride(draft: DateOverrideDraft)

    suspend fun deleteDateOverride(id: String)

    suspend fun saveSemesterFirstWeekMonday(date: LocalDate?)
}
