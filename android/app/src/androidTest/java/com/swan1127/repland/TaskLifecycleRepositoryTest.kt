package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.ReplandDatabase
import com.swan1127.repland.data.room.RoomPlanRepository
import com.swan1127.repland.data.room.RoomTaskRepository
import com.swan1127.repland.data.room.toDomain
import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskLifecycleRepositoryTest {
    private lateinit var database: ReplandDatabase
    private lateinit var repository: RoomTaskRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReplandDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomTaskRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun confirmed_statuses_and_partial_completion_append_ordered_logs() = runBlocking {
        val taskId = createTask()

        repository.confirmStatus(taskId, TaskStatus.IN_PROGRESS)
        repository.recordPartialCompletion(
            taskId,
            TaskFeedback(
                completedContent = "完成第一章",
                completionResult = "理解主要概念",
                actualDurationMinutes = 35,
                progressPercent = 40,
            ),
        )
        repository.confirmStatus(
            taskId,
            TaskStatus.POSTPONED,
            TaskFeedback(postponeReason = "临时课程冲突"),
        )
        repository.confirmStatus(taskId, TaskStatus.IN_PROGRESS)
        repository.confirmStatus(
            taskId,
            TaskStatus.COMPLETED,
            TaskFeedback(completedContent = "完成全部章节", completionResult = "已提交", progressPercent = 100),
        )

        val logs = repository.observeExecutionLogs(taskId).first()
        assertEquals(
            listOf(
                ExecutionLogEventType.STATUS_CHANGE,
                ExecutionLogEventType.PARTIAL_COMPLETION,
                ExecutionLogEventType.STATUS_CHANGE,
                ExecutionLogEventType.STATUS_CHANGE,
                ExecutionLogEventType.STATUS_CHANGE,
            ),
            logs.map { it.eventType },
        )
        assertEquals(TaskStatus.POSTPONED, logs[2].confirmedStatus)
        assertEquals("临时课程冲突", logs[2].feedback.postponeReason)
        assertEquals(40, logs[1].feedback.progressPercent)
        assertTrue(logs.zipWithNext().all { (first, second) ->
            first.createdAtEpochMillis < second.createdAtEpochMillis
        })
        assertEquals(TaskStatus.COMPLETED, database.taskDao().getById(taskId)?.toDomain()?.status)
        assertEquals(1, database.taskDao().getById(taskId)?.postponeCount)
    }

    @Test
    fun historical_feedback_correction_is_appended_without_overwriting_source() = runBlocking {
        val taskId = createTask()
        repository.recordFeedback(
            taskId,
            TaskFeedback(actualDurationMinutes = 20, progressPercent = 10, completedContent = "读了两页"),
        )
        val original = repository.observeExecutionLogs(taskId).first().single()

        repository.correctExecutionLog(
            taskId = taskId,
            correctedLogId = original.id,
            feedback = TaskFeedback(actualDurationMinutes = 30, progressPercent = 15, completedContent = "读了三页"),
        )

        val logs = repository.observeExecutionLogs(taskId).first()
        assertEquals(2, logs.size)
        assertEquals(ExecutionLogEventType.FEEDBACK, logs[0].eventType)
        assertEquals(20, logs[0].feedback.actualDurationMinutes)
        assertEquals(ExecutionLogEventType.CORRECTION, logs[1].eventType)
        assertEquals(original.id, logs[1].correctedLogId)
        assertEquals(30, logs[1].feedback.actualDurationMinutes)
        // A correction to past evidence is not a hidden current-task update.
        assertEquals(20, database.taskDao().getById(taskId)?.actualDurationMinutes)
    }

    @Test
    fun replacement_creates_new_task_and_preserves_original_plan_and_log_history() = runBlocking {
        val originalId = createTask()
        repository.recordFeedback(originalId, TaskFeedback(progressPercent = 10))
        val plans = RoomPlanRepository(database.planDao())
        plans.accept(
            PlanDraft(
                generatedAt = LocalDateTime.of(2026, 9, 17, 9, 0),
                segments = listOf(
                    PlannedSegment(
                        taskId = originalId,
                        date = LocalDate.of(2026, 9, 18),
                        startMinute = 9 * 60,
                        endMinute = 10 * 60,
                    ),
                ),
                pendingTaskIds = emptyList(),
                unscheduledTasks = emptyList(),
            ),
        )

        val replacementId = repository.replaceTask(
            originalId,
            taskDraft(displayName = "新的复习方案"),
        )

        val original = database.taskDao().getById(originalId)!!
        val replacement = database.taskDao().getById(replacementId)!!
        val originalLogs = repository.observeExecutionLogs(originalId).first()
        val planHistory = plans.observePlanHistory().first()

        assertEquals(TaskStatus.REPLACED.name, original.status)
        assertEquals(TaskStatus.NOT_STARTED.name, replacement.status)
        assertNotEquals(original.id, replacement.id)
        assertEquals(
            listOf(ExecutionLogEventType.FEEDBACK, ExecutionLogEventType.REPLACEMENT),
            originalLogs.map { it.eventType },
        )
        assertEquals(replacementId, originalLogs.last().replacementTaskId)
        assertTrue(originalLogs.zipWithNext().all { (first, second) ->
            first.createdAtEpochMillis < second.createdAtEpochMillis
        })
        assertTrue(planHistory.single().segments.any { it.taskId == originalId })
        assertNull(database.executionLogDao().getById(originalLogs.last().id)?.correctedLogId)
    }

    @Test
    fun editing_an_existing_task_preserves_its_initial_priority() = runBlocking {
        val taskId = createTask()

        repository.save(
            taskDraft(displayName = "调整后的标题").copy(
                id = taskId,
                userPriority = TaskPriority.LOW,
            ),
        )

        assertEquals(TaskPriority.HIGH.name, database.taskDao().getById(taskId)?.userPriority)
    }

    @Test
    fun user_lock_on_current_confirmed_segment_is_persisted() = runBlocking {
        val taskId = createTask()
        val plans = RoomPlanRepository(database.planDao())
        plans.accept(
            PlanDraft(
                generatedAt = LocalDateTime.of(2026, 9, 17, 9, 0),
                segments = listOf(
                    PlannedSegment(
                        taskId = taskId,
                        date = LocalDate.of(2026, 9, 18),
                        startMinute = 9 * 60,
                        endMinute = 10 * 60,
                    ),
                ),
                pendingTaskIds = emptyList(),
                unscheduledTasks = emptyList(),
            ),
        )
        val segmentId = plans.observeCurrentPlan().first()!!.segments.single().id

        plans.setSegmentLocked(segmentId, true)

        assertTrue(plans.observeCurrentPlan().first()!!.segments.single().isLocked)
    }

    @Test
    fun restoring_history_creates_a_new_current_version_without_rewriting_the_source() = runBlocking {
        val taskId = createTask()
        val plans = RoomPlanRepository(database.planDao())
        plans.accept(
            PlanDraft(
                generatedAt = LocalDateTime.of(2026, 9, 17, 9, 0),
                segments = listOf(
                    PlannedSegment(taskId = taskId, date = LocalDate.of(2026, 9, 18), startMinute = 540, endMinute = 600),
                ),
                pendingTaskIds = emptyList(),
                unscheduledTasks = emptyList(),
                orderedTaskIds = listOf(taskId),
            ),
        )
        val historicalSource = plans.observeCurrentPlan().first()!!
        plans.accept(
            PlanDraft(
                generatedAt = LocalDateTime.of(2026, 9, 17, 10, 0),
                segments = listOf(
                    PlannedSegment(taskId = taskId, date = LocalDate.of(2026, 9, 19), startMinute = 600, endMinute = 660),
                ),
                pendingTaskIds = emptyList(),
                unscheduledTasks = emptyList(),
            ),
        )

        plans.restore(historicalSource.id)

        val history = plans.observePlanHistory().first()
        val restored = history.single { it.isCurrent }
        val original = history.single { it.id == historicalSource.id }
        assertEquals(3, history.size)
        assertNotEquals(historicalSource.id, restored.id)
        assertFalse(original.isCurrent)
        assertEquals(historicalSource.segments.map { it.copy(id = "") }, restored.segments.map { it.copy(id = "") })
        assertEquals(listOf(taskId), restored.orderedTaskIds)
    }

    private suspend fun createTask(): String {
        repository.save(taskDraft())
        return database.taskDao().observeAll().first().single().id
    }

    private fun taskDraft(displayName: String = "复习数据结构") = TaskDraft(
        displayName = displayName,
        description = "完成本周复习",
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.HIGH,
        estimatedDays = 2,
        totalDurationMinutes = 90,
        dueDate = LocalDate.now().plusDays(2),
    )
}
