package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.TimeArrangementSnapshot
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

    /**
     * The rows as they are *right now*, read straight from the database.
     *
     * Undo cannot use the observed flow for this. That flow lags a write by a database
     * round-trip, so two undos in quick succession would both read the same stale list — and the
     * second one would then fail to delete the rows the first had just re-inserted, leaving
     * duplicates behind. A fresh read makes each step independent of how fast the user taps.
     */
    suspend fun currentWeeklyBlocks(): List<WeeklyTimeBlock>

    suspend fun currentDateOverrides(): List<DateOverride>

    /**
     * Replaces the entire arrangement with [snapshot].
     *
     * Deliberately lives in the repository rather than the view model: the whole point is that the
     * delete pass and the insert pass happen inside one database transaction, and only the
     * repository knows what database it is talking to. A view model version can only make two
     * separate calls, which is exactly the shape that publishes a half-finished state.
     */
    suspend fun restoreArrangement(snapshot: TimeArrangementSnapshot)

    suspend fun saveWeeklyBlock(draft: WeeklyTimeBlockDraft)

    suspend fun saveWeeklyBlocks(drafts: List<WeeklyTimeBlockDraft>)

    suspend fun deleteWeeklyBlock(id: String)

    suspend fun saveDateOverride(draft: DateOverrideDraft)

    suspend fun deleteDateOverride(id: String)

    suspend fun saveSemesterFirstWeekMonday(date: LocalDate?)
}
