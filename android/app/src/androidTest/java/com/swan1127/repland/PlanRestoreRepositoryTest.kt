package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class PlanRestoreRepositoryTest {
    private lateinit var db: ReplandDatabase
    private lateinit var plans: RoomPlanRepository
    private lateinit var tasks: RoomTaskRepository
    private val day = LocalDate.now().plusDays(1)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        plans = RoomPlanRepository(db); tasks = RoomTaskRepository(db)
    }
    @After fun teardown() = db.close()
    private suspend fun seed(): String {
        for (id in listOf("a", "b")) tasks.save(TaskDraft(id, id, "", TaskCategory.COURSE, TaskPriority.HIGH, 1, 60, null))
        plans.accept(draft(540, listOf("b", "a")))
        val source = plans.observeCurrentPlan().first()!!.id
        plans.accept(draft(660, listOf("a", "b")))
        return source
    }
    private fun draft(start: Int, order: List<String>) = PlanDraft(LocalDateTime.now(),
        listOf(PlannedSegment(taskId = "a", date = day, startMinute = start, endMinute = start + 60)), emptyList(), emptyList(), order)
    private suspend fun expectRejected(source: String) {
        val current = plans.observeCurrentPlan().first()!!
        val history = plans.observePlanHistory().first()
        val order = plans.observeTaskOrder().first()
        try { plans.restore(source); fail("Restoration must be rejected") } catch (_: IllegalArgumentException) { }
        assertEquals(current, plans.observeCurrentPlan().first())
        assertEquals(history, plans.observePlanHistory().first())
        assertEquals(order, plans.observeTaskOrder().first())
    }
    private suspend fun blockedBy(kind: TimeBlockKind) {
        val source = seed()
        RoomTimeRepository(db.timeDao()).saveWeeklyBlock(WeeklyTimeBlockDraft(title = "新约束", kind = kind,
            dayOfWeek = day.dayOfWeek, startMinute = 540, endMinute = 600, trackId = "other-track"))
        expectRejected(source)
    }
    @Test fun new_course_cannot_be_overridden_by_history() = runBlocking { blockedBy(TimeBlockKind.COURSE) }
    @Test fun new_rest_cannot_be_overridden_by_history() = runBlocking { blockedBy(TimeBlockKind.REST) }
    @Test fun new_commitment_cannot_be_overridden_by_history() = runBlocking { blockedBy(TimeBlockKind.OTHER) }
    @Test fun unchanged_future_slot_is_revalidated_against_new_constraints() = runBlocking {
        seed()
        val source = plans.observeCurrentPlan().first()!!.id
        RoomTimeRepository(db.timeDao()).saveDateOverride(DateOverrideDraft(title = "新增固定事项",
            type = DateOverrideType.BLOCKED, date = day, startMinute = 660, endMinute = 720))
        expectRejected(source)
    }
    @Test fun past_history_is_not_retroactively_rejected_by_new_constraints() = runBlocking {
        tasks.save(TaskDraft("a", "a", "", TaskCategory.COURSE, TaskPriority.HIGH, 1, 60, null))
        val past = day.minusDays(2)
        plans.accept(PlanDraft(LocalDateTime.now(), listOf(PlannedSegment(taskId = "a", date = past,
            startMinute = 540, endMinute = 600)), emptyList(), emptyList(), listOf("a")))
        val source = plans.observeCurrentPlan().first()!!.id
        RoomTimeRepository(db.timeDao()).saveDateOverride(DateOverrideDraft(title = "新规则",
            type = DateOverrideType.BLOCKED, date = past, startMinute = 540, endMinute = 600))
        plans.restore(source)
        assertEquals(past, plans.observeCurrentPlan().first()!!.segments.single().date)
    }
    @Test fun current_locked_placement_cannot_be_dropped_by_history() = runBlocking {
        val source = seed(); val slot = plans.observeCurrentPlan().first()!!.segments.single()
        plans.setSegmentLocked(slot.id, true); expectRejected(source)
    }
    @Test fun running_round_cannot_be_moved_by_history() = runBlocking {
        val source = seed(); val slot = plans.observeCurrentPlan().first()!!.segments.single()
        RoomExecutionSessionRepository(db).start(slot.id); expectRejected(source)
    }
    @Test fun completed_future_work_is_not_restored_but_source_and_execution_history_are_retained() = runBlocking {
        val source = seed(); tasks.confirmStatus("a", TaskStatus.COMPLETED)
        val evidence = tasks.observeExecutionLogs("a").first()
        val original = db.planDao().getPlanWithSegments(source)!!.toDomain()
        plans.restore(source)
        assertTrue(plans.observeCurrentPlan().first()!!.segments.isEmpty())
        assertEquals(original.copy(isCurrent = false), db.planDao().getPlanWithSegments(source)!!.toDomain())
        assertEquals(evidence, tasks.observeExecutionLogs("a").first())
        assertEquals(TaskStatus.COMPLETED.name, db.taskDao().getById("a")!!.status)
    }
    @Test fun workspace_failure_rolls_back_plan_version_order_and_draft_clearing() = runBlocking {
        val source = seed(); val before = plans.observeCurrentPlan().first()!!
        val draft = draft(780, listOf("a", "b")); plans.saveDraft(draft)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restored_order BEFORE INSERT ON planning_workspace WHEN NEW.`key` = 'order' BEGIN SELECT RAISE(ABORT, 'test'); END")
        try { plans.restore(source); fail("injected failure") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, plans.observeCurrentPlan().first())
        assertEquals(2, plans.observePlanHistory().first().size)
        assertEquals(listOf("a", "b"), plans.observeTaskOrder().first())
        assertEquals(draft, plans.observeDraft().first())
    }
    @Test fun successful_restore_keeps_original_version_and_publishes_order_and_reminders_from_new_current() = runBlocking {
        val source = seed(); val original = db.planDao().getPlanWithSegments(source)!!.toDomain()
        plans.saveDraft(draft(780, listOf("a", "b")))
        plans.restore(source)
        val restored = plans.observeCurrentPlan().first()!!
        assertNotEquals(source, restored.id)
        assertEquals(original.segments.map { it.copy(id = "") }, restored.segments.map { it.copy(id = "") })
        assertEquals(listOf("b", "a"), plans.observeTaskOrder().first())
        assertNull(plans.observeDraft().first())
        val reminders = LocalReminderPlanner.plan(restored, tasks.observeTasks().first())
        assertTrue(reminders.filter { it.kind == ReminderKind.SEGMENT_START }.all { it.planId == restored.id })
        assertEquals(3, plans.observePlanHistory().first().size)
    }
}
