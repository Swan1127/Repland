package com.swan1127.repland.ui.agent

import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.confirmedPlan
import com.swan1127.repland.domain.model.task
import com.swan1127.repland.domain.ports.PlanRepository
import com.swan1127.repland.domain.ports.TaskRepository
import com.swan1127.repland.testkit.FakePlanRepository
import com.swan1127.repland.testkit.FakeTaskRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSubmissionCoordinatorTest {
    private val date = LocalDate.of(2026, 10, 21)

    @Test
    fun `success reports persisted tasks and confirmed placements`() = runTest {
        val tasks = PersistedTasks()
        val plans = PersistedPlans()
        val coordinator = AgentSubmissionCoordinator(tasks, plans) { date }

        val result = coordinator.submit(listOf(draft("scheduled"), draft("pending")), listOf(placement("scheduled")))

        assertTrue(result is AgentSubmissionResult.Success)
        val receipt = (result as AgentSubmissionResult.Success).receipt
        assertEquals(2, receipt.createdCount)
        assertEquals(1, receipt.arrangedCount)
        assertEquals(1, receipt.pendingCount)
        assertEquals(date, receipt.date)
        assertEquals(setOf("scheduled", "pending"), receipt.taskIds.toSet())
        assertEquals(listOf(AgentReceiptPlacement("segment-scheduled", "scheduled", "Task scheduled", date, 840, 870, "focus")), receipt.placements)
    }

    @Test
    fun `save failure preserves evidence without claiming success`() = runTest {
        val tasks = PersistedTasks().apply { failingId = "broken" }
        val coordinator = AgentSubmissionCoordinator(tasks, PersistedPlans()) { date }

        val result = coordinator.submit(listOf(draft("broken")), emptyList())

        assertTrue(result is AgentSubmissionResult.Failure)
        assertNull((result as AgentSubmissionResult.Failure).receipt)
        assertTrue(tasks.state.value.isEmpty())
    }

    @Test
    fun `partial save followed by retry creates no duplicate tasks`() = runTest {
        val tasks = PersistedTasks().apply { failingId = "second" }
        val plans = PersistedPlans()
        val coordinator = AgentSubmissionCoordinator(tasks, plans) { date }
        val drafts = listOf(draft("first"), draft("second"))

        val failed = coordinator.submit(drafts, emptyList()) as AgentSubmissionResult.Failure
        assertEquals(listOf("first"), failed.receipt?.taskIds)
        assertFalse(requireNotNull(failed.receipt).isComplete)
        tasks.failingId = null
        val retried = coordinator.submit(drafts, emptyList()) as AgentSubmissionResult.Success

        assertEquals(2, tasks.state.value.size)
        assertEquals(1, retried.receipt.createdCount)
        assertEquals(2, retried.receipt.pendingCount)
    }

    @Test
    fun `placement failure reports actual pending tasks and can retry safely`() = runTest {
        val tasks = PersistedTasks()
        val plans = PersistedPlans().apply { failing = true }
        val coordinator = AgentSubmissionCoordinator(tasks, plans) { date }

        val failed = coordinator.submit(listOf(draft("task")), listOf(placement("task"))) as AgentSubmissionResult.Failure
        assertEquals(1, failed.receipt?.pendingCount)
        assertEquals(0, failed.receipt?.arrangedCount)
        plans.failing = false
        val retried = coordinator.submit(listOf(draft("task")), listOf(placement("task"))) as AgentSubmissionResult.Success

        assertEquals(1, tasks.state.value.size)
        assertEquals(0, retried.receipt.createdCount)
        assertEquals(1, retried.receipt.arrangedCount)
    }

    @Test
    fun `a repository returning without a placement is never success`() = runTest {
        val plans = PersistedPlans().apply { silent = true }
        val coordinator = AgentSubmissionCoordinator(PersistedTasks(), plans) { date }

        val result = coordinator.submit(listOf(draft("task")), listOf(placement("task")))

        assertTrue(result is AgentSubmissionResult.Failure)
        assertEquals(0, (result as AgentSubmissionResult.Failure).receipt?.arrangedCount)
        assertEquals(1, result.receipt?.pendingCount)
    }

    @Test
    fun `a repository returning without a task is never success`() = runTest {
        val tasks = PersistedTasks().apply { silent = true }
        val coordinator = AgentSubmissionCoordinator(tasks, PersistedPlans()) { date }

        val result = coordinator.submit(listOf(draft("task")), emptyList())

        assertTrue(result is AgentSubmissionResult.Failure)
        assertNull((result as AgentSubmissionResult.Failure).receipt)
    }

    @Test
    fun `repeated concurrent confirmation does not append duplicate placements`() = runTest {
        val tasks = PersistedTasks()
        val plans = PersistedPlans()
        val coordinator = AgentSubmissionCoordinator(tasks, plans) { date }
        val first = async { coordinator.submit(listOf(draft("task")), listOf(placement("task"))) }
        val second = async { coordinator.submit(listOf(draft("task")), listOf(placement("task"))) }

        assertTrue(first.await() is AgentSubmissionResult.Success)
        assertTrue(second.await() is AgentSubmissionResult.Success)
        assertEquals(1, tasks.state.value.size)
        assertEquals(1, plans.placementWrites)
    }

    @Test
    fun `cancellation is propagated rather than recorded as a failed save`() = runTest {
        val tasks = PersistedTasks().apply { cancelled = true }
        val coordinator = AgentSubmissionCoordinator(tasks, PersistedPlans()) { date }
        var cancellationObserved = false

        try {
            coordinator.submit(listOf(draft("task")), emptyList())
        } catch (_: CancellationException) {
            cancellationObserved = true
        }

        assertTrue(cancellationObserved)
    }

    @Test
    fun `invalid or unowned placements are rejected before writing`() = runTest {
        val tasks = PersistedTasks()
        val coordinator = AgentSubmissionCoordinator(tasks, PersistedPlans()) { date }

        val result = coordinator.submit(listOf(draft("task")), listOf(placement("other")))

        assertTrue(result is AgentSubmissionResult.Failure)
        assertTrue(tasks.state.value.isEmpty())
    }

    @Test
    fun `blank display name is rejected before persistence verification`() = runTest {
        val tasks = PersistedTasks()
        val coordinator = AgentSubmissionCoordinator(tasks, PersistedPlans()) { date }

        val result = coordinator.submit(listOf(draft("task").copy(displayName = "  ")), emptyList())

        assertTrue(result is AgentSubmissionResult.Failure)
        assertTrue(tasks.state.value.isEmpty())
    }

    @Test
    fun `editing a partially placed proposal moves its existing segment on retry`() = runTest {
        val tasks = PersistedTasks()
        val plans = PersistedPlans().apply { failingTaskId = "second" }
        val coordinator = AgentSubmissionCoordinator(tasks, plans) { date }
        val drafts = listOf(draft("first"), draft("second"))
        val placements = listOf(placement("first"), placement("second").copy(startMinute = 900, endMinute = 930))

        assertTrue(coordinator.submit(drafts, placements) is AgentSubmissionResult.Failure)
        plans.failingTaskId = null
        val revised = placements.map { if (it.taskId == "first") it.copy(startMinute = 960, endMinute = 990) else it }
        val retried = coordinator.submit(drafts, revised)

        assertTrue(retried is AgentSubmissionResult.Success)
        assertEquals(2, plans.segments().size)
        assertEquals(960, plans.segments().single { it.taskId == "first" }.startMinute)
        assertEquals(1, plans.moveWrites)
    }

    @Test
    fun `retry cannot silently move a placement the user has locked`() = runTest {
        val tasks = PersistedTasks()
        val plans = PersistedPlans()
        val coordinator = AgentSubmissionCoordinator(tasks, plans) { date }
        coordinator.submit(listOf(draft("task")), listOf(placement("task")))
        plans.lockPlacements()

        val result = coordinator.submit(listOf(draft("task")), listOf(placement("task").copy(startMinute = 900, endMinute = 930)))

        assertTrue(result is AgentSubmissionResult.Failure)
        assertEquals(840, plans.segments().single().startMinute)
        assertEquals(0, plans.moveWrites)
    }

    @Test
    fun `final result rechecks earlier placements rather than trusting earlier calls`() = runTest {
        val tasks = PersistedTasks()
        val plans = PersistedPlans().apply { replaceEarlierPlacements = true }
        val coordinator = AgentSubmissionCoordinator(tasks, plans) { date }

        val result = coordinator.submit(listOf(draft("first"), draft("second")), listOf(placement("first"), placement("second")))

        assertTrue(result is AgentSubmissionResult.Failure)
        assertEquals(1, (result as AgentSubmissionResult.Failure).receipt?.arrangedCount)
        assertEquals(1, result.receipt?.pendingCount)
    }

    @Test
    fun `historical receipt retains the confirmed times and task title after later edits`() = runTest {
        val tasks = PersistedTasks()
        val plans = PersistedPlans()
        val coordinator = AgentSubmissionCoordinator(tasks, plans) { date }
        val first = coordinator.submit(listOf(draft("task")), listOf(placement("task"))) as AgentSubmissionResult.Success

        val edited = coordinator.submit(
            listOf(draft("task").copy(displayName = "Edited title")),
            listOf(placement("task").copy(startMinute = 900, endMinute = 930)),
        ) as AgentSubmissionResult.Success

        assertEquals(840, first.receipt.placements.single().startMinute)
        assertEquals(870, first.receipt.placements.single().endMinute)
        assertEquals("Task task", first.receipt.placements.single().taskTitle)
        assertEquals(900, edited.receipt.placements.single().startMinute)
        assertEquals("Edited title", edited.receipt.placements.single().taskTitle)
    }

    @Test
    fun `ambiguous existing placement failure retains every actual instance without unrelated tasks`() = runTest {
        val tasks = PersistedTasks()
        val plans = PersistedPlans().apply {
            seedSegments(listOf(
                PlannedSegment("second", "task", date, 960, 990, trackId = "focus"),
                PlannedSegment("first", "task", date, 840, 870, trackId = "focus"),
                PlannedSegment("tomorrow", "task", date.plusDays(1), 600, 630, trackId = "focus"),
                PlannedSegment("unrelated", "other", date, 700, 730, trackId = "focus"),
            ))
        }
        val coordinator = AgentSubmissionCoordinator(tasks, plans) { date }

        val result = coordinator.submit(listOf(draft("task")), listOf(placement("task"))) as AgentSubmissionResult.Failure

        val receipt = requireNotNull(result.receipt)
        assertFalse(receipt.isComplete)
        assertEquals(listOf("first", "second", "tomorrow"), receipt.placements.map { it.segmentId })
        assertEquals(date.plusDays(1), receipt.placements.last().date)
        assertEquals(1, receipt.arrangedCount)
        assertEquals(0, receipt.pendingCount)
        assertEquals(0, plans.placementWrites)
        assertEquals(0, plans.moveWrites)
    }

    @Test
    fun `an arrangement ending at midnight retains 24 hour end notation`() = runTest {
        val coordinator = AgentSubmissionCoordinator(PersistedTasks(), PersistedPlans()) { date }

        val result = coordinator.submit(
            listOf(draft("task")),
            listOf(placement("task").copy(startMinute = 1_410, endMinute = 1_440)),
        ) as AgentSubmissionResult.Success

        val placement = result.receipt.placements.single()
        assertEquals("23:30", receiptTimeLabel(placement.startMinute))
        assertEquals("24:00", receiptTimeLabel(placement.endMinute))
    }

    @Test
    fun `collecting an unscheduled task never fabricates an arrangement snapshot`() = runTest {
        val coordinator = AgentSubmissionCoordinator(PersistedTasks(), PersistedPlans()) { date }

        val result = coordinator.submit(listOf(draft("task")), emptyList()) as AgentSubmissionResult.Success

        assertEquals(0, result.receipt.arrangedCount)
        assertEquals(1, result.receipt.pendingCount)
        assertTrue(result.receipt.placements.isEmpty())
    }

    private fun draft(id: String) = TaskDraft(
        id = id,
        displayName = "Task $id",
        description = "description of $id",
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.MEDIUM,
        estimatedDays = 1,
        totalDurationMinutes = 30,
        dueDate = null,
    )

    private fun placement(taskId: String) = AgentTaskPlacement(taskId, 840, 870, "focus", date)

    private class PersistedTasks(private val fallback: FakeTaskRepository = FakeTaskRepository()) : TaskRepository by fallback {
        val state = MutableStateFlow(emptyList<Task>())
        var failingId: String? = null
        var silent = false
        var cancelled = false

        override fun observeTasks(): Flow<List<Task>> = state

        override suspend fun save(draft: TaskDraft) {
            if (cancelled) throw CancellationException()
            if (draft.id == failingId) error("save rejected")
            if (silent) return
            val saved = task(requireNotNull(draft.id), durationMinutes = draft.totalDurationMinutes, scheduledForDate = draft.scheduledForDate)
                .copy(description = draft.description.trim(), displayName = draft.displayName.trim(), category = draft.category)
            state.value = state.value.filterNot { it.id == saved.id } + saved
        }
    }

    private class PersistedPlans(private val fallback: FakePlanRepository = FakePlanRepository()) : PlanRepository by fallback {
        var failing = false
        var failingTaskId: String? = null
        var silent = false
        var replaceEarlierPlacements = false
        var placementWrites = 0
        var moveWrites = 0

        override suspend fun placeTask(taskId: String, date: LocalDate, startMinute: Int, endMinute: Int, trackId: String) {
            if (failing || taskId == failingTaskId) error("placement rejected")
            if (silent) return
            placementWrites += 1
            val existing = if (replaceEarlierPlacements) emptyList() else segments()
            fallback.currentPlan.value = confirmedPlan(segments = existing + PlannedSegment(id = "segment-$taskId", taskId = taskId, date = date, startMinute = startMinute, endMinute = endMinute, trackId = trackId))
        }

        override suspend fun movePlacement(segmentId: String, startMinute: Int, endMinute: Int, trackId: String) {
            moveWrites += 1
            fallback.currentPlan.value = confirmedPlan(segments = segments().map {
                if (it.id == segmentId) it.copy(startMinute = startMinute, endMinute = endMinute, trackId = trackId) else it
            })
        }

        fun segments(): List<PlannedSegment> = fallback.currentPlan.value?.segments.orEmpty()

        fun seedSegments(segments: List<PlannedSegment>) {
            fallback.currentPlan.value = confirmedPlan(segments = segments)
        }

        fun lockPlacements() {
            fallback.currentPlan.value = confirmedPlan(segments = segments().map { it.copy(isLocked = true) })
        }
    }
}
