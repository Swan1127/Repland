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
class PlanningOperationServiceTest {
    private lateinit var db: ReplandDatabase
    private lateinit var tasks: RoomTaskRepository
    private lateinit var plans: RoomPlanRepository
    private lateinit var reads: PlanningReadService
    private lateinit var operations: PlanningOperationService
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).allowMainThreadQueries().build()
        tasks = RoomTaskRepository(db); plans = RoomPlanRepository(db)
        reads = PlanningReadService(tasks, RoomTimeRepository(db.timeDao()), plans, RoomCategoryPreferenceRepository(db.categoryPreferenceDao()))
        operations = PlanningOperationService(reads, plans, PlanGenerator, RoomExecutionSessionRepository(db))
    }
    @After fun close() { db.close() }
    private suspend fun seed() {
        tasks.save(TaskDraft("low", "低优先级", "", TaskCategory.COURSE, TaskPriority.LOW, 1, 30, null))
        tasks.save(TaskDraft("high", "高优先级", "", TaskCategory.COURSE, TaskPriority.HIGH, 1, 30, null))
        plans.saveTaskOrder(listOf("low", "high"))
    }
    @Test fun formulate_reranks_while_regular_replan_keeps_manual_order_and_neither_confirms() = runBlocking {
        seed()
        val ordinary = operations.preview(PlanningPreviewKind.REPLAN_REMAINING)
        assertEquals(listOf("low", "high"), ordinary.orderedTaskIds)
        val formulated = operations.preview(PlanningPreviewKind.FORMULATE)
        assertEquals(listOf("high", "low"), formulated.orderedTaskIds)
        assertEquals(listOf("low", "high"), plans.observeTaskOrder().first())
        assertNull(plans.observeCurrentPlan().first())
        assertEquals(reads.snapshot().revision, formulated.sourceRevision)
    }
    @Test fun sort_only_contains_no_placements_and_commits_only_order() = runBlocking {
        seed()
        val sort = operations.preview(PlanningPreviewKind.SORT_ONLY)
        assertTrue(sort.orderOnly); assertTrue(sort.segments.isEmpty()); assertTrue(sort.unscheduledTasks.isEmpty())
        operations.confirm(sort)
        assertEquals(listOf("high", "low"), plans.observeTaskOrder().first())
        assertNull(plans.observeCurrentPlan().first())
    }
    @Test fun query_and_explanation_share_page_membership_without_writing_facts() = runBlocking {
        seed()
        val date = LocalDate.now().plusDays(1)
        plans.accept(PlanDraft(LocalDateTime.now(), listOf(PlannedSegment(taskId = "high", date = date, startMinute = 600, endMinute = 630)), emptyList(), emptyList(), listOf("low", "high")))
        val before = tasks.observeTasks().first()
        val planBefore = plans.observeCurrentPlan().first()
        assertEquals(listOf("high"), operations.query(TaskQueryScope.TODAY, date).map { it.id })
        assertEquals(listOf("low"), operations.query(TaskQueryScope.INBOX).map { it.id })
        assertEquals("high", operations.explainOrder("high").taskId)
        assertEquals(before, tasks.observeTasks().first()); assertEquals(planBefore, plans.observeCurrentPlan().first())
        assertNull(plans.observeDraft().first())
    }
    @Test fun active_round_is_retained_even_when_future_and_not_manually_locked() = runBlocking {
        seed()
        val date = LocalDate.now().plusDays(1)
        plans.accept(PlanDraft(LocalDateTime.now(), listOf(PlannedSegment(taskId = "high", date = date, startMinute = 600, endMinute = 630)), emptyList(), emptyList(), listOf("low", "high")))
        val slot = plans.observeCurrentPlan().first()!!.segments.single()
        RoomExecutionSessionRepository(db).start(slot.id)
        val result = operations.preview(PlanningPreviewKind.FORMULATE)
        assertTrue(result.segments.any { it.taskId == "high" && it.date == date && it.startMinute == 600 && it.endMinute == 630 })
        assertNotNull(RoomExecutionSessionRepository(db).observeActive().first())
    }
    @Test fun stale_preview_is_rejected_by_shared_confirmation_transaction() = runBlocking {
        seed()
        val draft = operations.preview(PlanningPreviewKind.FORMULATE)
        tasks.confirmStatus("high", TaskStatus.COMPLETED)
        try { operations.confirm(draft); fail("Stale draft must fail") } catch (_: IllegalArgumentException) { }
        assertNull(plans.observeCurrentPlan().first())
        assertEquals(listOf("low", "high"), plans.observeTaskOrder().first())
    }
    @Test fun batch_capture_and_arrange_is_atomic_and_duplicate_confirm_is_rejected() = runBlocking {
        val draft = TaskDraft("new", "批量事项", "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 30, null)
        val date = LocalDate.now().plusDays(1)
        val slot = PlannedSegment(taskId = "new", date = date, startMinute = 600, endMinute = 630)
        val receipt = operations.confirmChanges(listOf(draft), listOf(slot), emptySet(), date)
        assertEquals(1, receipt.createdTasks); assertEquals(1, receipt.writtenSegments)
        try { operations.confirmChanges(listOf(draft), listOf(slot), emptySet(), date); fail("Duplicate must fail") } catch (_: IllegalArgumentException) { }
        assertEquals(1, tasks.observeTasks().first().size)
        assertEquals(1, plans.observeCurrentPlan().first()!!.segments.size)
    }
}
