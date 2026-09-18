package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.TimeConstraintSettings
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import com.swan1127.repland.domain.ports.TimeRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTimeRepository(
    private val timeDao: TimeDao,
) : TimeRepository {
    override fun observeWeeklyBlocks(): Flow<List<WeeklyTimeBlock>> =
        timeDao.observeWeeklyBlocks().map { blocks -> blocks.map(WeeklyTimeBlockEntity::toDomain) }

    override fun observeDateOverrides(): Flow<List<DateOverride>> =
        timeDao.observeDateOverrides().map { overrides -> overrides.map(DateOverrideEntity::toDomain) }

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
                id = existing?.id ?: UUID.randomUUID().toString(),
                title = draft.title.trim(),
                kind = draft.kind.name,
                dayOfWeek = draft.dayOfWeek.value,
                startMinute = draft.startMinute,
                endMinute = draft.endMinute,
                weekPattern = draft.weekPattern?.trim()?.takeIf(String::isNotBlank),
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
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
                id = existing?.id ?: UUID.randomUUID().toString(),
                title = draft.title.trim(),
                type = draft.type.name,
                dateEpochDay = draft.date.toEpochDay(),
                startMinute = draft.startMinute,
                endMinute = draft.endMinute,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
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
