package com.swan1127.repland.data.room

import androidx.room.withTransaction
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.TimeArrangementSnapshot
import com.swan1127.repland.domain.model.TimeConstraintSettings
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import com.swan1127.repland.domain.ports.TimeRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTimeRepository(private val database: ReplandDatabase) : TimeRepository {
    private val timeDao = database.timeDao()
    override fun observeWeeklyBlocks(): Flow<List<WeeklyTimeBlock>> =
        timeDao.observeWeeklyBlocks().map { blocks -> blocks.map(WeeklyTimeBlockEntity::toDomain) }

    override fun observeDateOverrides(): Flow<List<DateOverride>> =
        timeDao.observeDateOverrides().map { overrides -> overrides.map(DateOverrideEntity::toDomain) }

    override suspend fun currentWeeklyBlocks(): List<WeeklyTimeBlock> =
        timeDao.getAllWeeklyBlocks().map(WeeklyTimeBlockEntity::toDomain)

    override suspend fun currentDateOverrides(): List<DateOverride> =
        timeDao.getAllDateOverrides().map(DateOverrideEntity::toDomain)

    /**
     * Replaces the whole arrangement with [snapshot], as **one transaction**.
     *
     * Two reasons it cannot be a delete pass followed by an insert pass:
     *
     * - **No intermediate state is published.** Room only invalidates the observed queries when the
     *   transaction commits, so the day timeline never sees the arrangement emptied and never swaps
     *   its grid for the empty-state card half-way through — which is what was throwing the
     *   timeline back to the current hour on every undo.
     * - **A crash cannot lose the timetable.** Between the two passes of a split write there is a
     *   window in which every block has been deleted and none has been written back. A process
     *   death in that window leaves the user with no timetable at all, and no way to get it back.
     *
     * Reconciled by id rather than wiped and refilled, so rows that are already correct are updated
     * in place instead of being deleted and re-inserted under the same id.
     */
    override suspend fun restoreArrangement(snapshot: TimeArrangementSnapshot) = database.withTransaction {
        val wantedBlockIds = snapshot.blocks.mapNotNull { it.id }.toSet()
        timeDao.getAllWeeklyBlocks()
            .filter { it.id !in wantedBlockIds }
            .forEach { timeDao.deleteWeeklyBlock(it.id) }
        val wantedOverrideIds = snapshot.overrides.mapNotNull { it.id }.toSet()
        timeDao.getAllDateOverrides()
            .filter { it.id !in wantedOverrideIds }
            .forEach { timeDao.deleteDateOverride(it.id) }
        // Upserts: a row already present is updated in place, a deleted row comes back under its
        // original id, and nothing is duplicated.
        snapshot.blocks.forEach { saveWeeklyBlock(it) }
        snapshot.overrides.forEach { saveDateOverride(it) }
    }

    override fun observeTimeConstraintSettings(): Flow<TimeConstraintSettings> =
        timeDao.observeSemesterSettings().map { settings ->
            TimeConstraintSettings(
                semesterFirstWeekMonday = settings?.firstWeekMondayEpochDay?.let(LocalDate::ofEpochDay),
                updatedAtEpochMillis = settings?.updatedAtEpochMillis ?: 0L,
            )
        }

    override suspend fun saveWeeklyBlock(draft: WeeklyTimeBlockDraft) {
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { id -> timeDao.getWeeklyBlockById(id) }
        timeDao.insertWeeklyBlock(
            WeeklyTimeBlockEntity(
                // The caller's id wins when it has one. Minting a fresh id whenever the row is not
                // already present looks harmless, but it makes a restore of a *deleted* row come
                // back under a new identity — so the row that returns is not the row that left, and
                // a redo can no longer address it.
                id = draft.id ?: UUID.randomUUID().toString(),
                title = draft.title.trim(),
                kind = draft.kind.name,
                dayOfWeek = draft.dayOfWeek.value,
                startMinute = draft.startMinute,
                endMinute = draft.endMinute,
                weekPattern = draft.weekPattern?.trim()?.takeIf(String::isNotBlank),
                trackId = draft.trackId.ifBlank { "course" },
                note = draft.note?.trim()?.takeIf(String::isNotBlank),
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: draft.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            ),
        )
        touchTimeConstraints(now)
    }

    override suspend fun deleteWeeklyBlock(id: String) {
        timeDao.deleteWeeklyBlock(id)
        touchTimeConstraints(System.currentTimeMillis())
    }

    override suspend fun saveWeeklyBlocks(drafts: List<WeeklyTimeBlockDraft>) {
        val validDrafts = drafts.filter(com.swan1127.repland.domain.model.TimeBlockValidator::isValid)
            .distinctBy { draft ->
                listOf(
                    draft.title.trim(),
                    draft.kind,
                    draft.dayOfWeek,
                    draft.startMinute,
                    draft.endMinute,
                    draft.weekPattern?.trim()?.takeIf(String::isNotBlank),
                    draft.trackId.ifBlank { "course" },
                )
            }
        if (validDrafts.isEmpty()) return
        val now = System.currentTimeMillis()
        timeDao.insertWeeklyBlocks(
            validDrafts.map { draft ->
                WeeklyTimeBlockEntity(
                    id = UUID.randomUUID().toString(),
                    title = draft.title.trim(),
                    kind = draft.kind.name,
                    dayOfWeek = draft.dayOfWeek.value,
                    startMinute = draft.startMinute,
                    endMinute = draft.endMinute,
                    weekPattern = draft.weekPattern?.trim()?.takeIf(String::isNotBlank),
                    trackId = draft.trackId.ifBlank { "course" },
                    note = draft.note?.trim()?.takeIf(String::isNotBlank),
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
            },
        )
        touchTimeConstraints(now)
    }

    override suspend fun saveDateOverride(draft: DateOverrideDraft) {
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { id -> timeDao.getDateOverrideById(id) }
        timeDao.insertDateOverride(
            DateOverrideEntity(
                // See saveWeeklyBlock: the caller's id must survive, or a restored row is a new row.
                id = draft.id ?: UUID.randomUUID().toString(),
                title = draft.title.trim(),
                type = draft.type.name,
                dateEpochDay = draft.date.toEpochDay(),
                startMinute = draft.startMinute,
                endMinute = draft.endMinute,
                note = draft.note?.trim()?.takeIf(String::isNotBlank),
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: draft.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            ),
        )
        touchTimeConstraints(now)
    }

    override suspend fun deleteDateOverride(id: String) {
        timeDao.deleteDateOverride(id)
        touchTimeConstraints(System.currentTimeMillis())
    }

    override suspend fun saveSemesterFirstWeekMonday(date: LocalDate?) {
        val now = System.currentTimeMillis()
        timeDao.insertSemesterSettings(
            SemesterSettingsEntity(
                firstWeekMondayEpochDay = date?.toEpochDay(),
                updatedAtEpochMillis = nextConstraintRevision(now),
            ),
        )
    }

    private suspend fun touchTimeConstraints(now: Long) {
        val current = timeDao.getSemesterSettings()
        timeDao.insertSemesterSettings(
            SemesterSettingsEntity(
                firstWeekMondayEpochDay = current?.firstWeekMondayEpochDay,
                updatedAtEpochMillis = nextConstraintRevision(now),
            ),
        )
    }

    private suspend fun nextConstraintRevision(now: Long): Long =
        maxOf(now, (timeDao.getSemesterSettings()?.updatedAtEpochMillis ?: Long.MIN_VALUE) + 1)
}
