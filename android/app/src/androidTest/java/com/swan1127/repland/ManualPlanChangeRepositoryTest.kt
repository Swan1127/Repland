package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ManualPlanChangeRepositoryTest {
    private val day = LocalDate.now().plusDays(1)
    private fun verify(block: suspend (ReplandDatabase, RoomPlanRepository, RoomTaskRepository) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val plans = RoomPlanRepository(db); val tasks = RoomTaskRepository(db)
            tasks.save(TaskDraft(id = "a", displayName = "A", totalDurationMinutes = 30))
            tasks.save(TaskDraft(id = "b", displayName = "B", totalDurationMinutes = 30))
            plans.placeTask("a", day, 600, 630, "focus")
            block(db, plans, tasks)
        } finally { db.close() }
    }
    private suspend fun confirmation(plans: RoomPlanRepository, change: ManualPlanChange): PlanChangeConfirmationRequired {
        val error = runCatching { plans.applyManualChange(change) }.exceptionOrNull()
        assertTrue("Expected scoped confirmation, got $error", error is PlanChangeConfirmationRequired)
        return error as PlanChangeConfirmationRequired
    }

    @Test fun ordinary_overlap_requires_confirmation_on_same_and_other_tracks_and_does_not_record_execution() = verify { _, plans, tasks ->
        for (track in listOf("focus", "custom")) {
            val change = ManualPlanChange.Place("b", day, 615, 645, track)
            val before = plans.observeCurrentPlan().first()!!
            val consent = confirmation(plans, change)
            assertEquals(before, plans.observeCurrentPlan().first()) // Cancelling is zero-write.
            plans.applyManualChange(change, consent.revision)
            val after = plans.observeCurrentPlan().first()!!
            assertNotEquals(before.id, after.id)
            assertEquals(2, after.segments.size)
            assertTrue(tasks.observeExecutionLogs("b").first().isEmpty())
            assertEquals(TaskStatus.NOT_STARTED, tasks.observeTasks().first().single { it.id == "b" }.status)
            plans.removePlacement(after.segments.single { it.taskId == "b" }.id)
        }
    }

    @Test fun changing_constraints_invalidates_pending_overlap_without_losing_plan_or_draft() = verify { db, plans, _ ->
        val change = ManualPlanChange.Place("b", day, 615, 645, "custom")
        val consent = confirmation(plans, change)
        val original = plans.observeCurrentPlan().first()
        val draft = PlanDraft(LocalDateTime.now(), emptyList(), listOf("b"), emptyList())
        plans.saveDraft(draft)
        RoomTimeRepository(db.timeDao()).saveDateOverride(DateOverrideDraft(title = "新约束", type = DateOverrideType.BLOCKED,
            date = day, startMinute = 600, endMinute = 660))
        assertTrue(runCatching { plans.applyManualChange(change, consent.revision) }.isFailure)
        assertEquals(original, plans.observeCurrentPlan().first())
        assertEquals(draft, plans.observeDraft().first())
    }

    @Test fun confirmed_unavoidable_move_and_removal_require_separate_fresh_consent() = verify { db, plans, _ ->
        val entity = db.taskDao().getById("a")!!
        db.taskDao().update(entity.copy(userPriority = TaskPriority.REQUIRED.name))
        val before = plans.observeCurrentPlan().first()!!
        val move = ManualPlanChange.Move(before.segments.single().id, 660, 690, "custom")
        val consent = confirmation(plans, move)
        assertEquals(before, plans.observeCurrentPlan().first())
        plans.applyManualChange(move, consent.revision)
        val moved = plans.observeCurrentPlan().first()!!
        assertFalse(moved.segments.single().isLocked)
        assertEquals(660, moved.segments.single().startMinute)
        assertTrue(runCatching { plans.applyManualChange(move, consent.revision) }.isFailure)
        val remove = ManualPlanChange.Remove(moved.segments.single().id)
        val removal = confirmation(plans, remove)
        plans.applyManualChange(remove, removal.revision)
        assertTrue(plans.observeCurrentPlan().first()!!.segments.isEmpty())
        assertEquals(3, plans.observePlanHistory().first().size)
    }

    @Test fun consent_never_bypasses_locked_or_unavoidable_work_or_hard_time_constraints() = verify { db, plans, _ ->
        val slot = plans.observeCurrentPlan().first()!!.segments.single()
        plans.setSegmentLocked(slot.id, true)
        val locked = plans.observeCurrentPlan().first()!!
        val attempts = listOf(ManualPlanChange.Place("b", day, 600, 630, "custom"),
            ManualPlanChange.Move(locked.segments.single().id, 660, 690, "focus"),
            ManualPlanChange.Remove(locked.segments.single().id), ManualPlanChange.Clear)
        attempts.forEach {
            val failure = runCatching { plans.applyManualChange(it) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException && failure !is PlanChangeConfirmationRequired)
        }
        assertEquals(locked, plans.observeCurrentPlan().first())
        plans.setSegmentLocked(locked.segments.single().id, false)
        val task = db.taskDao().getById("a")!!
        db.taskDao().update(task.copy(userPriority = TaskPriority.REQUIRED.name))
        assertTrue(runCatching { plans.placeTask("b", day, 600, 630, "custom") }.exceptionOrNull() is IllegalArgumentException)
        for (kind in listOf(TimeBlockKind.COURSE, TimeBlockKind.REST, TimeBlockKind.OTHER)) {
            RoomTimeRepository(db.timeDao()).saveWeeklyBlock(WeeklyTimeBlockDraft(title = "保护", kind = kind,
                dayOfWeek = day.dayOfWeek, startMinute = 720, endMinute = 780))
            val failure = runCatching { plans.placeTask("b", day, 720, 750, "custom") }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException && failure !is PlanChangeConfirmationRequired)
        }
    }

    @Test fun transaction_failure_preserves_history_and_allows_retry_of_same_consent() = verify { db, plans, _ ->
        val change = ManualPlanChange.Place("b", day, 615, 645, "custom")
        val consent = confirmation(plans, change)
        val before = plans.observeCurrentPlan().first()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_manual BEFORE INSERT ON plan_segments BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        assertTrue(runCatching { plans.applyManualChange(change, consent.revision) }.isFailure)
        assertEquals(before, plans.observeCurrentPlan().first())
        assertEquals(1, plans.observePlanHistory().first().size)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_manual")
        plans.applyManualChange(change, consent.revision)
        assertEquals(2, plans.observeCurrentPlan().first()!!.segments.size)
    }
}
