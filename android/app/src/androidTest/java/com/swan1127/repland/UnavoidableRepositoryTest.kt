package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class UnavoidableRepositoryTest {
    @Test fun generated_draft_cannot_move_or_drop_confirmed_unavoidable_work() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val tasks = RoomTaskRepository(db); val plans = RoomPlanRepository(db)
            tasks.save(TaskDraft(id = "required", displayName = "不可避免", userPriority = TaskPriority.REQUIRED, totalDurationMinutes = 30))
            val day = LocalDate.now().plusDays(1)
            plans.placeTask("required", day, 600, 630, "focus")
            val original = plans.observeCurrentPlan().first()!!
            val segment = original.segments.single()
            for (segments in listOf(emptyList(), listOf(segment.copy(startMinute = 660, endMinute = 690)))) {
                val draft = PlanDraft(LocalDateTime.now(), segments, emptyList(), emptyList(), listOf("required"))
                plans.saveDraft(draft)
                assertTrue("Automatic confirmation must reject movement or deletion", runCatching { plans.accept(draft) }.isFailure)
                assertEquals(original, plans.observeCurrentPlan().first())
                assertEquals(draft, plans.observeDraft().first())
                assertEquals(1, plans.observePlanHistory().first().size)
            }
        } finally { db.close() }
    }

    @Test fun another_track_cannot_cover_a_confirmed_unavoidable_interval() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val tasks = RoomTaskRepository(db); val plans = RoomPlanRepository(db)
            tasks.save(TaskDraft(id = "required", displayName = "不可避免", userPriority = TaskPriority.REQUIRED))
            tasks.save(TaskDraft(id = "ordinary", displayName = "普通"))
            val day = LocalDate.now().plusDays(1)
            plans.placeTask("required", day, 600, 630, "focus")
            val original = plans.observeCurrentPlan().first()
            assertTrue(runCatching { plans.placeTask("ordinary", day, 600, 630, "custom") }.isFailure)
            assertEquals(original, plans.observeCurrentPlan().first())
        } finally { db.close() }
    }
}
