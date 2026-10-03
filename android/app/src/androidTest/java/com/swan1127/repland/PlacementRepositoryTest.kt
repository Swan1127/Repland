package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlacementRepositoryTest {
    @Test fun rejected_course_rest_commitment_and_date_conflicts_preserve_current_plan() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val tasks = RoomTaskRepository(database)
            tasks.save(TaskDraft(displayName = "测试任务", description = "", category = TaskCategory.COURSE,
                userPriority = TaskPriority.HIGH, estimatedDays = 1, totalDurationMinutes = 30, dueDate = null))
            val id = tasks.observeTasks().first().single().id
            val plans = RoomPlanRepository(database)
            val day = LocalDate.of(2026, 10, 3)
            plans.placeTask(id, day, 600, 630, "focus")
            val original = plans.observeCurrentPlan().first()
            for (kind in listOf(TimeBlockKind.COURSE, TimeBlockKind.REST, TimeBlockKind.OTHER)) {
                database.timeDao().insertWeeklyBlock(WeeklyTimeBlockEntity("block", "约束", kind.name,
                    day.dayOfWeek.value, 480, 540, null, 1, 1))
                val result = runCatching { plans.placeTask(id, day, 480, 510, "another-track") }
                assertTrue(result.exceptionOrNull() is IllegalArgumentException)
                assertEquals(original, plans.observeCurrentPlan().first())
            }
            database.timeDao().deleteWeeklyBlock("block")
            database.timeDao().insertDateOverride(DateOverrideEntity("exception", "单日阻挡",
                DateOverrideType.BLOCKED.name, day.toEpochDay(), 480, 540, 1, 1))
            assertTrue(runCatching { plans.placeTask(id, day, 480, 510, "focus") }.isFailure)
            assertEquals(original, plans.observeCurrentPlan().first())
        } finally { database.close() }
    }
}
