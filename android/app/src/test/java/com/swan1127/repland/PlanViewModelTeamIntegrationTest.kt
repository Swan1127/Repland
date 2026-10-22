package com.swan1127.repland

import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlanGenerator
import com.swan1127.repland.domain.model.PlanningOperationService
import com.swan1127.repland.domain.planteam.runtime.AcceptanceFinished
import com.swan1127.repland.domain.planteam.runtime.DraftReady
import com.swan1127.repland.testkit.PlanningOperationFixture
import com.swan1127.repland.ui.plan.PlanViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTeamIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    private val fixture = PlanningOperationFixture()

    @Before fun installMain() { Dispatchers.setMain(dispatcher) }
    @After fun restoreMain() { Dispatchers.resetMain() }

    private fun model(service: PlanningOperationService = fixture.service(planningDispatcher = dispatcher)) =
        PlanViewModel(fixture.plans, PlanGenerator, fixture.time, fixture.taskRepository, service)

    private suspend fun generate(model: PlanViewModel, orderOnly: Boolean = false) {
        val input = fixture.reads.snapshot().input
        model.generateDraft(input.tasks, input.weeklyBlocks, input.dateOverrides, input.semesterFirstWeekMonday,
            input.lockedSegments, input.categoryPreferences, input.manualTaskOrder, orderOnly = orderOnly)
    }

    private suspend fun TestScope.withModel(model: PlanViewModel, action: suspend TestScope.() -> Unit) {
        val ui = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect { } }
        val workspace = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.workspaceUiState.collect { } }
        advanceUntilIdle()
        try { action() } finally { ui.cancel(); workspace.cancel() }
    }

    @Test fun productionPreviewExposesActualEventsWithoutConfirmingThePlan() = runTest(dispatcher) {
        val model = model()
        withModel(model) {
            generate(model)
            advanceUntilIdle()

            val state = model.uiState.value
            assertNotNull(state.draft)
            assertNotNull(state.draft?.sourceRevision)
            assertTrue(state.teamEvents.last() is DraftReady)
            assertTrue(state.teamEvents.filterIsInstance<AcceptanceFinished>().single().passed)
            assertEquals(state.draft, state.teamResult?.draft)
            assertEquals(0, fixture.plans.acceptCalls)
            assertFalse(state.isGenerating)
            assertFalse(state.isWorking)
        }
    }

    @Test fun savingAnEditedPersistedDraftInvalidatesTheOldAcceptanceDisplay() = runTest(dispatcher) {
        val model = model()
        withModel(model) {
            generate(model)
            advanceUntilIdle()
            val original = requireNotNull(model.uiState.value.draft)
            val edited = original.copy(segments = original.segments.map { it.copy(startMinute = it.startMinute + 30, endMinute = it.endMinute + 30) })

            model.saveDraftEdit(original, edited, "edit-1")
            advanceUntilIdle()

            assertEquals(edited, fixture.plans.draft.value)
            assertEquals("edit-1", model.uiState.value.draftEditReceipt)
            assertEquals(original.sourceRevision, edited.sourceRevision)
            assertTrue(model.uiState.value.teamEvents.isEmpty())
            assertNull(model.uiState.value.teamResult)
        }
    }

    @Test fun cancellingGenerationDoesNotLaterCreateAPersistedPreview() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        fixture.beforeLogs = { gate.await() }
        val model = model()
        withModel(model) {
            generate(model)
            advanceUntilIdle()
            assertTrue(model.uiState.value.isGenerating)

            model.discardDraft()
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            assertNull(fixture.plans.draft.value)
            assertNull(model.uiState.value.draft)
            assertFalse(model.uiState.value.isGenerating)
            assertFalse(model.uiState.value.isWorking)
            assertEquals(0, fixture.plans.acceptCalls)
        }
    }

    @Test fun sortOnlyRetainsTheNativeOrderingContractWithoutFakeTeamSteps() = runTest(dispatcher) {
        val model = model()
        withModel(model) {
            generate(model, orderOnly = true)
            advanceUntilIdle()

            val draft = requireNotNull(model.uiState.value.draft)
            assertTrue(draft.orderOnly)
            assertTrue(draft.segments.isEmpty())
            assertTrue(draft.unscheduledTasks.isEmpty())
            assertTrue(model.uiState.value.teamEvents.isEmpty())
            assertNull(model.uiState.value.teamResult)
            assertNotNull(draft.sourceRevision)
        }
    }

    @Test fun failedAtomicConfirmationKeepsTheExactPersistedDraft() = runTest(dispatcher) {
        val model = model()
        withModel(model) {
            generate(model)
            advanceUntilIdle()
            val original = fixture.plans.draft.value
            fixture.plans.acceptFailure = IllegalArgumentException("事务检测到来源已变化")

            model.acceptDraft()
            advanceUntilIdle()

            assertEquals(original, fixture.plans.draft.value)
            assertNotNull(model.uiState.value.errorMessage)
            assertTrue(fixture.plans.acceptedDrafts.isEmpty())
        }
    }

    @Test fun restoredDraftHasNoFabricatedRuntimeOrAcceptanceLog() = runTest(dispatcher) {
        fixture.plans.draft.value = PlanDraft(fixture.clock.instant().atZone(fixture.clock.zone).toLocalDateTime(), emptyList(), emptyList(), emptyList(), sourceRevision = "restored")
        val model = model()
        withModel(model) {
            assertEquals("restored", model.uiState.value.draft?.sourceRevision)
            assertTrue(model.uiState.value.teamEvents.isEmpty())
            assertNull(model.uiState.value.teamResult)
        }
    }
}
