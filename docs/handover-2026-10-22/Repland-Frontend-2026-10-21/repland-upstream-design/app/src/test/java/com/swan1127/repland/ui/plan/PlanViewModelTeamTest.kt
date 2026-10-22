package com.swan1127.repland.ui.plan

import com.swan1127.repland.domain.model.MemoryReference
import com.swan1127.repland.domain.model.MemorySignal
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlanGenerationInput
import com.swan1127.repland.domain.model.TEST_CLOCK
import com.swan1127.repland.domain.model.TEST_MONDAY
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.confirmedPlan
import com.swan1127.repland.domain.model.executionLog
import com.swan1127.repland.domain.model.feedback
import com.swan1127.repland.domain.model.planDraft
import com.swan1127.repland.domain.model.planInput
import com.swan1127.repland.domain.model.segment
import com.swan1127.repland.domain.model.task
import com.swan1127.repland.domain.model.weeklyBlock
import com.swan1127.repland.domain.planteam.PlanAgentId
import com.swan1127.repland.domain.planteam.PlanningTeamCoordinator
import com.swan1127.repland.domain.planteam.runtime.AcceptanceFinished
import com.swan1127.repland.domain.planteam.runtime.DraftReady
import com.swan1127.repland.domain.planteam.runtime.TeamStarted
import com.swan1127.repland.domain.ports.PlanRepository
import com.swan1127.repland.testkit.FakePlanDraftGenerator
import com.swan1127.repland.testkit.FakePlanRepository
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.withCollected
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTeamTest : ViewModelTestBase() {
    private val repository = FakePlanRepository()
    private val generator = FakePlanDraftGenerator()
    private val coordinator = PlanningTeamCoordinator()

    private fun model(plans: PlanRepository = repository) = PlanViewModel(plans, generator, coordinator, dispatcher, TEST_CLOCK)

    private fun input() = planInput(
        tasks = listOf(task("t1")),
        weeklyBlocks = listOf(weeklyBlock(startMinute = 480, endMinute = 1080)),
    )

    private fun generate(
        model: PlanViewModel,
        input: PlanGenerationInput,
        memory: MemoryReference? = null,
        logs: List<TaskExecutionLog>? = null,
    ) = model.generateDraft(
        input.tasks, input.weeklyBlocks, input.dateOverrides, input.semesterFirstWeekMonday,
        input.lockedSegments, input.categoryPreferences, input.manualTaskOrder,
        memoryReference = memory, executionLogs = logs, focusMinutes = 30,
    )

    @Test
    fun `production team produces real events and an unconfirmed draft without legacy generator writes`() = runTest(dispatcher) {
        val model = model()
        withCollected(model.uiState) {
            generate(model, input())
            advanceUntilIdle()

            val state = model.uiState.value
            assertNotNull(state.draft)
            assertEquals(state.draft, state.teamResult?.draft)
            assertTrue(state.teamEvents.first() is TeamStarted)
            assertTrue(state.teamEvents.last() is DraftReady)
            assertTrue(state.teamEvents.filterIsInstance<AcceptanceFinished>().single().passed)
            assertTrue(generator.inputs.isEmpty())
            assertTrue(repository.acceptedDrafts.isEmpty())
            assertFalse(state.isGenerating)
            assertNull(state.actionError)
        }
    }

    @Test
    fun `team only receives execution feedback from the injected business day`() = runTest(dispatcher) {
        val model = model()
        val noon = TEST_MONDAY.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val logs = listOf(
            executionLog("today", "t1", feedback = feedback(completedContent = "今日反馈"), createdAtEpochMillis = noon),
            executionLog("yesterday", "t1", feedback = feedback(completedContent = "旧反馈"), createdAtEpochMillis = noon - 86_400_000),
        )
        withCollected(model.uiState) {
            generate(model, input(), logs = logs)
            advanceUntilIdle()

            assertEquals(1, model.uiState.value.teamResult?.calibration?.feedbackCount)
            assertEquals(true, model.uiState.value.teamResult?.calibration?.connected)
            assertTrue(model.uiState.value.teamResult?.hints?.perCategory.orEmpty().values.all { it.preferredWindow == null })
        }
    }

    @Test
    fun `missing execution connection is not reported as a quiet day`() = runTest(dispatcher) {
        val model = model()
        withCollected(model.uiState) {
            generate(model, input())
            advanceUntilIdle()

            assertEquals(false, model.uiState.value.teamResult?.calibration?.connected)
        }
    }

    @Test
    fun `orphan execution feedback is excluded even when it occurred today`() = runTest(dispatcher) {
        val model = model()
        val noon = TEST_MONDAY.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val orphan = executionLog(
            "orphan", "deleted-task", feedback = feedback(completedContent = "已删除事项的反馈"), createdAtEpochMillis = noon,
        )
        withCollected(model.uiState) {
            generate(model, input(), logs = listOf(orphan))
            advanceUntilIdle()

            val result = requireNotNull(model.uiState.value.teamResult)
            assertEquals(true, result.calibration?.connected)
            assertEquals(0, result.calibration?.feedbackCount)
            assertTrue(result.calibration?.signals.orEmpty().none { it.taskId == "deleted-task" })
            assertNotNull(model.uiState.value.draft)
            assertNull(model.uiState.value.actionError)
        }
    }

    @Test
    fun `memory influences hints but does not alter committed duration or priority`() = runTest(dispatcher) {
        val model = model()
        val source = input()
        val memory = MemoryReference(TEST_MONDAY, listOf(MemorySignal("t1", "余项", 1.0, 0.4, TEST_MONDAY, 0.6)))
        withCollected(model.uiState) {
            generate(model, source, memory)
            advanceUntilIdle()

            val result = requireNotNull(model.uiState.value.teamResult)
            assertNotNull(result.hints?.perTask?.get("t1")?.preferredWindow)
            assertEquals(60, result.draft.segments.filter { it.taskId == "t1" }.sumOf { it.endMinute - it.startMinute })
            assertEquals(input().tasks, source.tasks)
            assertTrue(repository.acceptedDrafts.isEmpty())
        }
    }

    @Test
    fun `cancelled generation cannot overwrite an external draft`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var subscriptions = 0
        repository.currentPlanFlow = flow {
            if (subscriptions++ > 0) gate.await()
            emit(null)
        }
        val model = model()
        val external = planDraft(orderedTaskIds = listOf("external"))
        withCollected(model.uiState) {
            generate(model, input())
            advanceUntilIdle()
            assertTrue(model.uiState.value.isGenerating)

            model.showAgentDraft(external)
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(external, model.uiState.value.draft)
            assertFalse(model.uiState.value.isGenerating)
            assertTrue(model.uiState.value.teamEvents.isEmpty())
            assertNull(model.uiState.value.actionError)
        }
    }

    @Test
    fun `editing an existing draft invalidates an in flight replacement`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.currentPlanFlow = flow { gate.await(); emit(null) }
        val model = model()
        val original = planDraft(orderedTaskIds = listOf("original"))
        val edited = original.copy(orderedTaskIds = listOf("edited"))
        withCollected(model.uiState) {
            model.showAgentDraft(original)
            generate(model, input())
            advanceUntilIdle()
            model.updateDraft(edited)
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(edited, model.uiState.value.draft)
            assertNull(model.uiState.value.teamResult)
            assertFalse(model.uiState.value.isGenerating)
        }
    }

    @Test
    fun `discard cancels generation and does not resurrect its draft`() = runTest(dispatcher) {
        val model = model()
        withCollected(model.uiState) {
            generate(model, input())
            model.discardDraft()
            advanceUntilIdle()

            assertNull(model.uiState.value.draft)
            assertTrue(model.uiState.value.teamEvents.isEmpty())
            assertFalse(model.uiState.value.isGenerating)
        }
    }

    @Test
    fun `changed task facts reject confirmation while preserving the draft`() = runTest(dispatcher) {
        val model = model()
        val original = input()
        withCollected(model.uiState) {
            generate(model, original)
            advanceUntilIdle()
            val draft = model.uiState.value.draft

            model.acceptDraft(original.copy(tasks = listOf(task("t1", durationMinutes = 120))))
            advanceUntilIdle()

            assertEquals(draft, model.uiState.value.draft)
            assertNotNull(model.uiState.value.actionError)
            assertTrue(repository.acceptedDrafts.isEmpty())
        }
    }

    @Test
    fun `changed repository plan rejects confirmation even before UI catches up`() = runTest(dispatcher) {
        val model = model()
        val source = input()
        withCollected(model.uiState) {
            generate(model, source)
            advanceUntilIdle()
            val draft = model.uiState.value.draft
            repository.currentPlan.value = confirmedPlan("another-plan")

            model.acceptDraft(source)
            advanceUntilIdle()

            assertEquals(draft, model.uiState.value.draft)
            assertNotNull(model.uiState.value.actionError)
            assertTrue(repository.acceptedDrafts.isEmpty())
        }
    }

    @Test
    fun `changed memory consent rejects a draft generated with that reference`() = runTest(dispatcher) {
        val model = model()
        val source = input()
        withCollected(model.uiState) {
            generate(model, source, MemoryReference.empty(TEST_MONDAY))
            advanceUntilIdle()

            model.acceptDraft(source, memoryReference = null)
            advanceUntilIdle()

            assertNotNull(model.uiState.value.draft)
            assertNotNull(model.uiState.value.actionError)
            assertTrue(repository.acceptedDrafts.isEmpty())
        }
    }

    @Test
    fun `editing a draft cannot remove a current locked placement`() = runTest(dispatcher) {
        val locked = segment("locked", startMinute = 540, endMinute = 600, isLocked = true)
        repository.currentPlan.value = confirmedPlan(segments = listOf(locked))
        val model = model()
        val source = input()
        withCollected(model.uiState) {
            generate(model, source)
            advanceUntilIdle()
            val original = requireNotNull(model.uiState.value.draft)
            model.updateDraft(original.copy(segments = emptyList()))
            advanceUntilIdle()

            assertTrue(model.uiState.value.teamEvents.isEmpty())
            assertNull(model.uiState.value.teamResult)

            model.acceptDraft(source)
            advanceUntilIdle()

            assertNotNull(model.uiState.value.actionError)
            assertNotNull(model.uiState.value.draft)
            assertTrue(repository.acceptedDrafts.isEmpty())
        }
    }

    @Test
    fun `a newly locked flag cannot bypass availability validation`() = runTest(dispatcher) {
        val model = model()
        val source = input()
        withCollected(model.uiState) {
            generate(model, source)
            advanceUntilIdle()
            val original = requireNotNull(model.uiState.value.draft)
            model.updateDraft(original.copy(segments = listOf(segment("fake-lock", startMinute = 1200, endMinute = 1260, isLocked = true))))

            model.acceptDraft(source)
            advanceUntilIdle()

            assertNotNull(model.uiState.value.actionError)
            assertTrue(repository.acceptedDrafts.isEmpty())
        }
    }

    @Test
    fun `edited overlapping new segments are rejected before any write`() = runTest(dispatcher) {
        val model = model()
        val source = input().copy(tasks = listOf(task("t1"), task("t2")))
        withCollected(model.uiState) {
            generate(model, source)
            advanceUntilIdle()
            val original = requireNotNull(model.uiState.value.draft)
            model.updateDraft(original.copy(segments = listOf(
                segment("one", "t1", startMinute = 480, endMinute = 540),
                segment("two", "t2", startMinute = 510, endMinute = 570),
            )))

            model.acceptDraft(source)
            advanceUntilIdle()

            assertNotNull(model.uiState.value.actionError)
            assertTrue(repository.acceptedDrafts.isEmpty())
        }
    }

    @Test
    fun `successful confirmation is single flight and invokes its callback once`() = runTest(dispatcher) {
        val gated = GatedPlanRepository(repository)
        val model = model(gated)
        val source = input()
        var callbacks = 0
        withCollected(model.uiState) {
            generate(model, source)
            advanceUntilIdle()

            model.acceptDraft(source) { callbacks++ }
            model.acceptDraft(source) { callbacks++ }
            advanceUntilIdle()
            assertTrue(model.uiState.value.isAccepting)
            assertEquals(1, gated.attempts)
            assertTrue(repository.acceptedDrafts.isEmpty())

            gated.gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, repository.acceptedDrafts.size)
            assertEquals(1, callbacks)
            assertNull(model.uiState.value.draft)
            assertFalse(model.uiState.value.isAccepting)
        }
    }

    @Test
    fun `failed confirmation preserves input and draft with a visible error`() = runTest(dispatcher) {
        val model = model()
        val source = input()
        withCollected(model.uiState) {
            generate(model, source)
            advanceUntilIdle()
            val draft = model.uiState.value.draft
            repository.failWith = IllegalStateException("write failed")

            model.acceptDraft(source)
            advanceUntilIdle()

            assertEquals(draft, model.uiState.value.draft)
            assertEquals("write failed", model.uiState.value.actionError)
            assertFalse(model.uiState.value.isAccepting)
        }
    }

    @Test
    fun `cancelled confirmation is not converted into a write failure`() = runTest(dispatcher) {
        val model = model()
        val source = input()
        withCollected(model.uiState) {
            generate(model, source)
            advanceUntilIdle()
            repository.failWith = CancellationException("cancelled")

            model.acceptDraft(source)
            advanceUntilIdle()

            assertNotNull(model.uiState.value.draft)
            assertNull(model.uiState.value.actionError)
            assertFalse(model.uiState.value.isAccepting)
            assertTrue(repository.acceptedDrafts.isEmpty())
        }
    }

    @Test
    fun `clearing local team memory removes run history and all unconfirmed UI state`() = runTest(dispatcher) {
        val model = model()
        withCollected(model.uiState) {
            generate(model, input())
            advanceUntilIdle()
            assertTrue(coordinator.memoryBank().episodesOf(PlanAgentId.REQUIREMENTS).isNotEmpty())

            model.clearTeamMemory()
            advanceUntilIdle()

            assertTrue(PlanAgentId.entries.all { coordinator.memoryBank().episodesOf(it).isEmpty() })
            assertNull(model.uiState.value.draft)
            assertNull(model.uiState.value.teamResult)
            assertTrue(model.uiState.value.teamEvents.isEmpty())
            assertNull(model.uiState.value.actionError)
        }
    }

    private class GatedPlanRepository(private val delegate: FakePlanRepository) : PlanRepository by delegate {
        val gate = CompletableDeferred<Unit>()
        var attempts = 0

        override suspend fun accept(draft: PlanDraft) {
            attempts++
            gate.await()
            delegate.accept(draft)
        }
    }
}
