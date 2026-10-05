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

class PlanningReadFailureTest {
    @Test fun task_read_failure_preserves_confirmed_plan_draft_order_and_tasks() = verify("tasks")
    @Test fun weekly_read_failure_preserves_confirmed_plan_draft_order_and_tasks() = verify("weekly")
    @Test fun date_read_failure_preserves_confirmed_plan_draft_order_and_tasks() = verify("dates")
    @Test fun settings_read_failure_preserves_confirmed_plan_draft_order_and_tasks() = verify("settings")

    private fun verify(part: String) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        val tasks = RoomTaskRepository(db); val time = RoomTimeRepository(db.timeDao()); val plans = RoomPlanRepository(db)
        val faultTasks = ReadFaultTasks(tasks); val faultTime = ReadFaultTime(time)
        val reads = PlanningReadService(faultTasks, faultTime, plans, RoomCategoryPreferenceRepository(db.categoryPreferenceDao()))
        val operations = PlanningOperationService(reads, plans, PlanGenerator, RoomExecutionSessionRepository(db))
        val date = LocalDate.now().plusDays(1)
        try {
            tasks.save(TaskDraft("original", "原任务", "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 30, null))
            plans.accept(PlanDraft(LocalDateTime.now(), listOf(PlannedSegment(taskId = "original", date = date,
                startMinute = 600, endMinute = 630)), emptyList(), emptyList(), listOf("original")))
            val savedDraft = operations.preview(PlanningPreviewKind.SORT_ONLY)
            val originalTasks = tasks.observeTasks().first(); val originalPlan = plans.observeCurrentPlan().first()
            val originalOrder = plans.observeTaskOrder().first()
            if (part == "tasks") faultTasks.failed.value = true else faultTime.failedPart.value = part
            val newTask = TaskDraft("new", "不可写入", "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 30, null)
            val actions = mutableListOf<suspend () -> Unit>()
            PlanningPreviewKind.entries.forEach { kind -> actions.add { operations.preview(kind); Unit } }
            actions += listOf<suspend () -> Unit>(
                { operations.confirm(savedDraft) },
                { operations.confirmChanges(listOf(newTask), emptyList(), emptySet(), date); Unit },
                { operations.query(TaskQueryScope.ALL_ACTIVE); Unit },
                { operations.explainOrder("original"); Unit },
            )
            actions.forEach { action ->
                assertTrue("$part must prevent the operation", runCatching { action() }.isFailure)
                assertEquals(originalTasks, tasks.observeTasks().first())
                assertEquals(originalPlan, plans.observeCurrentPlan().first())
                assertEquals(savedDraft, plans.observeDraft().first())
                assertEquals(originalOrder, plans.observeTaskOrder().first())
            }
            faultTasks.failed.value = false; faultTime.failedPart.value = null
            operations.confirm(savedDraft)
            assertNull(plans.observeDraft().first()); assertEquals(originalPlan, plans.observeCurrentPlan().first())
            val result = operations.confirmChanges(listOf(newTask), emptyList(), emptySet(), date)
            assertEquals(1, result.createdTasks); assertEquals(2, tasks.observeTasks().first().size)
        } finally { db.close() }
    }
}
