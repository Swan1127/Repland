package com.swan1127.repland.ui.plan

import com.swan1127.repland.domain.model.TEST_MONDAY
import com.swan1127.repland.domain.model.confirmedPlan
import com.swan1127.repland.domain.model.planDraft
import com.swan1127.repland.domain.model.planInput
import com.swan1127.repland.domain.model.segment
import com.swan1127.repland.domain.model.task
import com.swan1127.repland.testkit.FakePlanDraftGenerator
import com.swan1127.repland.testkit.FakePlanRepository
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.collectInBackground
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest : ViewModelTestBase() {

    private val repository = FakePlanRepository()
    private val generator = FakePlanDraftGenerator()

    private fun viewModel() = PlanViewModel(repository, generator)

    // --- observation ----------------------------------------------------------------------------

    @Test
    fun `before collection nothing is loaded and there is no draft`() = runTest(dispatcher) {
        repository.currentPlan.value = confirmedPlan("plan-9")

        val vm = viewModel()

        assertNull(vm.uiState.value.currentPlan)
        assertEquals(emptyList<Any>(), vm.uiState.value.planHistory)
        assertNull(vm.uiState.value.draft)
    }

    @Test
    fun `collecting reports the current plan and the history`() = runTest(dispatcher) {
        repository.currentPlan.value = confirmedPlan("plan-9")
        repository.history.value = listOf(confirmedPlan("plan-1"), confirmedPlan("plan-2"))
        val vm = viewModel()

        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        assertEquals("plan-9", vm.uiState.value.currentPlan?.id)
        assertEquals(listOf("plan-1", "plan-2"), vm.uiState.value.planHistory.map { it.id })
        job.cancel()
    }

    // --- generating a draft ---------------------------------------------------------------------

    @Test
    fun `generateDraft hands every argument to the generator unchanged`() = runTest(dispatcher) {
        val vm = viewModel()
        val tasks = listOf(task("t1"))
        val blocks = emptyList<com.swan1127.repland.domain.model.WeeklyTimeBlock>()
        val overrides = emptyList<com.swan1127.repland.domain.model.DateOverride>()
        val locked = listOf(segment("s1", startMinute = 480, endMinute = 540, isLocked = true))
        val preferences = mapOf(com.swan1127.repland.domain.model.TaskCategory.COURSE to 55)
        val manualOrder = listOf("t1")

        vm.generateDraft(
            tasks = tasks,
            weeklyBlocks = blocks,
            dateOverrides = overrides,
            semesterFirstWeekMonday = TEST_MONDAY,
            lockedSegments = locked,
            categoryPreferences = preferences,
            manualTaskOrder = manualOrder,
        )

        assertEquals(
            listOf(
                planInput(
                    tasks = tasks,
                    weeklyBlocks = blocks,
                    dateOverrides = overrides,
                    semesterFirstWeekMonday = TEST_MONDAY,
                    lockedSegments = locked,
                    categoryPreferences = preferences,
                    manualTaskOrder = manualOrder,
                ),
            ),
            generator.inputs,
        )
    }

    @Test
    fun `generateDraft stores whatever the generator returned`() = runTest(dispatcher) {
        val produced = planDraft(segments = listOf(segment("s1", startMinute = 480, endMinute = 540)))
        generator.result = produced
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.generateDraft(
            tasks = emptyList(),
            weeklyBlocks = emptyList(),
            dateOverrides = emptyList(),
            semesterFirstWeekMonday = null,
            lockedSegments = emptyList(),
            categoryPreferences = emptyMap(),
            manualTaskOrder = emptyList(),
        )
        advanceUntilIdle()

        assertEquals(produced, vm.uiState.value.draft)
        job.cancel()
    }

    @Test
    fun `discardDraft clears the draft`() = runTest(dispatcher) {
        generator.result = planDraft()
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.generateDraft(
            tasks = emptyList(),
            weeklyBlocks = emptyList(),
            dateOverrides = emptyList(),
            semesterFirstWeekMonday = null,
            lockedSegments = emptyList(),
            categoryPreferences = emptyMap(),
            manualTaskOrder = emptyList(),
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.draft != null)

        vm.discardDraft()
        advanceUntilIdle()

        assertNull(vm.uiState.value.draft)
        job.cancel()
    }

    // --- the draft slot rules -------------------------------------------------------------------

    @Test
    fun `an agent draft fills an empty draft slot`() = runTest(dispatcher) {
        val agentDraft = planDraft(segments = listOf(segment("agent", startMinute = 600, endMinute = 660)))
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.showAgentDraft(agentDraft)
        advanceUntilIdle()

        assertEquals(agentDraft, vm.uiState.value.draft)
        job.cancel()
    }

    @Test
    fun `an agent draft never overwrites a draft the user is already editing`() = runTest(dispatcher) {
        val mine = planDraft(segments = listOf(segment("mine", startMinute = 480, endMinute = 540)))
        val agentDraft = planDraft(segments = listOf(segment("agent", startMinute = 600, endMinute = 660)))
        generator.result = mine
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.generateDraft(
            tasks = emptyList(),
            weeklyBlocks = emptyList(),
            dateOverrides = emptyList(),
            semesterFirstWeekMonday = null,
            lockedSegments = emptyList(),
            categoryPreferences = emptyMap(),
            manualTaskOrder = emptyList(),
        )
        advanceUntilIdle()

        vm.showAgentDraft(agentDraft)
        advanceUntilIdle()

        // An agent result stays a draft and never silently replaces work in progress.
        assertEquals(mine, vm.uiState.value.draft)
        job.cancel()
    }

    @Test
    fun `updateDraft replaces an existing draft`() = runTest(dispatcher) {
        val original = planDraft(segments = listOf(segment("s1", startMinute = 480, endMinute = 540)))
        val edited = planDraft(segments = listOf(segment("s1", startMinute = 600, endMinute = 660)))
        generator.result = original
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.generateDraft(
            tasks = emptyList(),
            weeklyBlocks = emptyList(),
            dateOverrides = emptyList(),
            semesterFirstWeekMonday = null,
            lockedSegments = emptyList(),
            categoryPreferences = emptyMap(),
            manualTaskOrder = emptyList(),
        )
        advanceUntilIdle()

        vm.updateDraft(edited)
        advanceUntilIdle()

        assertEquals(edited, vm.uiState.value.draft)
        job.cancel()
    }

    @Test
    fun `updateDraft cannot create a draft from nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.updateDraft(planDraft(segments = listOf(segment("s1", startMinute = 480, endMinute = 540))))
        advanceUntilIdle()

        // Editing only applies to a draft that already exists; it must not conjure one.
        assertNull(vm.uiState.value.draft)
        job.cancel()
    }

    // --- accepting ------------------------------------------------------------------------------

    @Test
    fun `acceptDraft with no draft never calls the repository`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.acceptDraft()
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), repository.acceptedDrafts)
    }

    @Test
    fun `acceptDraft forwards the draft, clears it and invokes the callback`() = runTest(dispatcher) {
        val produced = planDraft(segments = listOf(segment("s1", startMinute = 480, endMinute = 540)))
        generator.result = produced
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.generateDraft(
            tasks = emptyList(),
            weeklyBlocks = emptyList(),
            dateOverrides = emptyList(),
            semesterFirstWeekMonday = null,
            lockedSegments = emptyList(),
            categoryPreferences = emptyMap(),
            manualTaskOrder = emptyList(),
        )
        advanceUntilIdle()
        var callbackCount = 0

        vm.acceptDraft { callbackCount += 1 }
        advanceUntilIdle()

        assertEquals(listOf(produced), repository.acceptedDrafts)
        assertNull(vm.uiState.value.draft)
        assertEquals(1, callbackCount)
        job.cancel()
    }

    @Test
    fun `a failed accept leaves the draft in place and never invokes the callback`() =
        runTest(dispatcher) {
            val produced = planDraft(segments = listOf(segment("s1", startMinute = 480, endMinute = 540)))
            generator.result = produced
            val vm = viewModel()
            val job = collectInBackground(vm.uiState)
            advanceUntilIdle()
            vm.generateDraft(
                tasks = emptyList(),
                weeklyBlocks = emptyList(),
                dateOverrides = emptyList(),
                semesterFirstWeekMonday = null,
                lockedSegments = emptyList(),
                categoryPreferences = emptyMap(),
                manualTaskOrder = emptyList(),
            )
            advanceUntilIdle()
            repository.failWith = IllegalStateException("write failed")
            var callbackCount = 0

            vm.acceptDraft { callbackCount += 1 }
            advanceUntilIdle()

            // FINDING, pinned. The accept is wrapped in runCatching with only an `onSuccess`
            // branch, so a failure is swallowed completely: no error state, no message, and the
            // caller's callback never runs. The user sees the draft still sitting there with no
            // explanation. Recorded as a fact, not endorsed as a design.
            assertEquals(produced, vm.uiState.value.draft)
            assertEquals(0, callbackCount)
            assertFalse(repository.acceptedDrafts.contains(produced))
            job.cancel()
        }

    // --- pass-through actions -------------------------------------------------------------------

    @Test
    fun `restore forwards the plan id`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.restore("plan-7")
        advanceUntilIdle()

        assertEquals(listOf("plan-7"), repository.restoredPlanIds)
    }

    @Test
    fun `clearCurrentPlan is forwarded once`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.clearCurrentPlan()
        advanceUntilIdle()

        assertEquals(1, repository.clearCurrentPlanCount)
    }

    @Test
    fun `setSegmentLocked forwards both arguments`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setSegmentLocked("seg-3", true)
        vm.setSegmentLocked("seg-4", false)
        advanceUntilIdle()

        assertEquals(listOf("seg-3" to true, "seg-4" to false), repository.segmentLocks)
    }

    @Test
    fun `placeTask forwards all five arguments`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.placeTask("t1", TEST_MONDAY, 540, 600, "focus")
        advanceUntilIdle()

        assertEquals(1, repository.placements.size)
        val placement = repository.placements.single()
        assertEquals("t1", placement.taskId)
        assertEquals(TEST_MONDAY, placement.date)
        assertEquals(540, placement.startMinute)
        assertEquals(600, placement.endMinute)
        assertEquals("focus", placement.trackId)
    }

    @Test
    fun `movePlacement forwards all four arguments`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.movePlacement("seg-3", 660, 720, "focus")
        advanceUntilIdle()

        assertEquals(1, repository.moves.size)
        val move = repository.moves.single()
        assertEquals("seg-3", move.segmentId)
        assertEquals(660, move.startMinute)
        assertEquals(720, move.endMinute)
        assertEquals("focus", move.trackId)
    }

    @Test
    fun `removePlacement forwards the segment id`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.removePlacement("seg-3")
        advanceUntilIdle()

        assertEquals(listOf("seg-3"), repository.removals)
    }

    // --- failure handling -----------------------------------------------------------------------

    @Test
    fun `a throwing observation stream escapes the scope`() {
        // FINDING, pinned. Unlike ProfileEvidenceViewModel and TaskViewModel, this state flow has
        // no `catch`, so a read failure is not turned into state: the sharing coroutine fails and
        // the exception leaves viewModelScope as an uncaught exception.
        val thrown = assertThrows(IllegalStateException::class.java) {
            runTest(dispatcher) {
                repository.currentPlanFlow = flow { throw IllegalStateException("read failed") }
                val vm = viewModel()
                val job = collectInBackground(vm.uiState)
                advanceUntilIdle()
                job.cancel()
            }
        }

        assertEquals("read failed", thrown.message)
    }
}
