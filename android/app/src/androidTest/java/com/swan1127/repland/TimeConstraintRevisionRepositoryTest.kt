package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.ReplandDatabase
import com.swan1127.repland.data.room.RoomTimeRepository
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeConstraintRevisionRepositoryTest {
    private lateinit var database: ReplandDatabase
    private lateinit var repository: RoomTimeRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReplandDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomTimeRepository(database.timeDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun every_time_constraint_change_including_delete_advances_the_draft_revision() = runBlocking {
        assertTrue(repository.observeTimeConstraintSettings().first().updatedAtEpochMillis == 0L)

        repository.saveWeeklyBlock(
            WeeklyTimeBlockDraft(
                title = "可用时间",
                kind = TimeBlockKind.AVAILABLE,
                dayOfWeek = DayOfWeek.MONDAY,
                startMinute = 8 * 60,
                endMinute = 10 * 60,
                weekPattern = null,
            ),
        )
        val afterSave = repository.observeTimeConstraintSettings().first().updatedAtEpochMillis
        val blockId = repository.observeWeeklyBlocks().first().single().id

        repository.deleteWeeklyBlock(blockId)
        val afterDelete = repository.observeTimeConstraintSettings().first().updatedAtEpochMillis

        repository.saveSemesterFirstWeekMonday(LocalDate.of(2026, 9, 14))
        val afterSemesterChange = repository.observeTimeConstraintSettings().first().updatedAtEpochMillis

        assertTrue(afterSave > 0L)
        assertTrue(afterDelete > afterSave)
        assertTrue(afterSemesterChange > afterDelete)
    }
}
