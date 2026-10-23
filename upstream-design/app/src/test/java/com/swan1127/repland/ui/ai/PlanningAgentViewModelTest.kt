package com.swan1127.repland.ui.ai

import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiAdvisorResult
import com.swan1127.repland.domain.model.AiDailySummaryRequest
import com.swan1127.repland.domain.model.AiPreferences
import com.swan1127.repland.domain.model.AiReplanRequest
import com.swan1127.repland.domain.model.AiSortingExplanationRequest
import com.swan1127.repland.domain.model.AiTaskUnderstandingRequest
import com.swan1127.repland.domain.model.PlanningAgent
import com.swan1127.repland.domain.model.PlanningAgentRequestType
import com.swan1127.repland.domain.model.PlanningAgentState
import com.swan1127.repland.domain.model.PlanningAgentWork
import com.swan1127.repland.domain.model.PlanningAgentWorkflow
import com.swan1127.repland.domain.model.TEST_MONDAY
import com.swan1127.repland.domain.model.planInput
import com.swan1127.repland.domain.model.task
import com.swan1127.repland.testkit.FakeAiAdvisor
import com.swan1127.repland.testkit.FakeAiSettingsRepository
import com.swan1127.repland.testkit.FakePlanDraftGenerator
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.collectInBackground
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives a real [PlanningAgentWorkflow] over a real [PlanningAgent].
 *
 * Both are concrete classes, but their constructors take interfaces (`AiAdvisor`,
 * `PlanDraftGenerator`), so the whole orchestration runs in a plain JVM test. Nothing here
 * reaches the network: the fake advisor answers with a failure result, which is exactly the
 * path a provider-less install takes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanningAgentViewModelTest : ViewModelTestBase() {

    private val settings = FakeAiSettingsRepository()
    private val aiAdvisor = FakeAiAdvisor()
    private val workflow = PlanningAgentWorkflow(
        PlanningAgent(aiAdvisor, FakePlanDraftGenerator()),
    )

    private fun viewModel() = PlanningAgentViewModel(settings, workflow)

    private val consentedAndEnabled =
        AiPreferences(isEnabled = true, consentedAtEpochMillis = 1_700_000_000_000L)

    // --- observation ----------------------------------------------------------------------------

    @Test
    fun `before collection the agent is idle and loading`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled

        val vm = viewModel()

        assertEquals(AiPreferences(), vm.uiState.value.preferences)
        assertEquals(PlanningAgentState.Idle, vm.uiState.value.workflow)
        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun `collecting reports the stored preferences and stops loading`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled
        val vm = viewModel()

        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        assertEquals(consentedAndEnabled, vm.uiState.value.preferences)
        assertEquals(false, vm.uiState.value.isLoading)
        job.cancel()
    }

    // --- the consent gate on begin --------------------------------------------------------------

    @Test
    fun `begin waits for consent when the stored preference has none`() = runTest(dispatcher) {
        settings.state.value = AiPreferences(isEnabled = true, consentedAtEpochMillis = null)
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
        advanceUntilIdle()

        assertEquals(
            PlanningAgentState.AwaitingConsent(PlanningAgentRequestType.TASK_UNDERSTANDING),
            vm.uiState.value.workflow,
        )
        // Nothing was asked of the provider, and no request was even built.
        assertEquals(emptyList<Any>(), aiAdvisor.requests)
        job.cancel()
    }

    @Test
    fun `begin refuses before the preferences were ever collected`() = runTest(dispatcher) {
        // FINDING, pinned. `begin` reads `uiState.value.preferences`, which is a WhileSubscribed
        // stateIn. With nobody collecting it is the declared initial value — no consent — so an
        // enabled, consented install still stops at AwaitingConsent. Asserted on the workflow's
        // own state, which is readable without subscribing.
        settings.state.value = consentedAndEnabled
        val vm = viewModel()

        vm.beginTaskUnderstanding(task("t1"), emptyList(), null)

        assertEquals(
            PlanningAgentState.AwaitingConsent(PlanningAgentRequestType.TASK_UNDERSTANDING),
            workflow.state.value,
        )
    }

    @Test
    fun `begin previews a request once consent and the switch are both on`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
        advanceUntilIdle()

        val state = vm.uiState.value.workflow
        assertTrue(state is PlanningAgentState.PreviewingRequest)
        state as PlanningAgentState.PreviewingRequest
        assertEquals(PlanningAgentRequestType.TASK_UNDERSTANDING, state.requestType)
        assertTrue(state.request is AiTaskUnderstandingRequest)
        // A preview is not a request: the provider has not been contacted yet.
        assertEquals(emptyList<Any>(), aiAdvisor.requests)
        job.cancel()
    }

    @Test
    fun `begin falls back locally when consent exists but the switch is off`() = runTest(dispatcher) {
        settings.state.value =
            AiPreferences(isEnabled = false, consentedAtEpochMillis = 1_700_000_000_000L)
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
        advanceUntilIdle()

        val state = vm.uiState.value.workflow
        assertTrue(state is PlanningAgentState.FailedFallback)
        assertEquals(
            AiAdvisorFailureReason.DISABLED,
            (state as PlanningAgentState.FailedFallback).reason,
        )
        assertEquals(emptyList<Any>(), aiAdvisor.requests)
        job.cancel()
    }

    // --- each begin builds its own request type -------------------------------------------------

    @Test
    fun `beginDifficultyAndDuration prepares a difficulty request`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.beginDifficultyAndDuration(task("t1"), emptyList(), null)
        advanceUntilIdle()

        val state = vm.uiState.value.workflow as PlanningAgentState.PreviewingRequest
        assertEquals(PlanningAgentRequestType.DIFFICULTY_AND_DURATION, state.requestType)
        job.cancel()
    }

    @Test
    fun `beginTaskBreakdown prepares a breakdown request`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.beginTaskBreakdown(task("t1"), emptyList(), null)
        advanceUntilIdle()

        val state = vm.uiState.value.workflow as PlanningAgentState.PreviewingRequest
        assertEquals(PlanningAgentRequestType.TASK_BREAKDOWN, state.requestType)
        job.cancel()
    }

    @Test
    fun `beginSortingExplanation carries the local ranking reasons into the request`() =
        runTest(dispatcher) {
            settings.state.value = consentedAndEnabled
            val vm = viewModel()
            val job = collectInBackground(vm.uiState)
            advanceUntilIdle()

            vm.beginSortingExplanation(task("t1"), emptyList(), null, listOf("截止日期更近", "优先级更高"))
            advanceUntilIdle()

            val state = vm.uiState.value.workflow as PlanningAgentState.PreviewingRequest
            assertEquals(PlanningAgentRequestType.SORTING_EXPLANATION, state.requestType)
            val request = state.request as AiSortingExplanationRequest
            assertEquals(listOf("截止日期更近", "优先级更高"), request.localRankingReasons)
            job.cancel()
        }

    @Test
    fun `beginDailySummary carries the day into the request`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.beginDailySummary(TEST_MONDAY, emptyList())
        advanceUntilIdle()

        val state = vm.uiState.value.workflow as PlanningAgentState.PreviewingRequest
        assertEquals(PlanningAgentRequestType.DAILY_SUMMARY, state.requestType)
        assertEquals(TEST_MONDAY, (state.request as AiDailySummaryRequest).day)
        job.cancel()
    }

    @Test
    fun `beginReplan prepares a replan request`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.beginReplan(
            affectedTasks = listOf(task("t1")),
            executionLogs = emptyList(),
            currentPlan = null,
            localPlanInput = planInput(tasks = listOf(task("t1"))),
            constraintSummary = listOf("每天最多 4 小时"),
        )
        advanceUntilIdle()

        val state = vm.uiState.value.workflow as PlanningAgentState.PreviewingRequest
        assertEquals(PlanningAgentRequestType.REPLAN, state.requestType)
        assertEquals(listOf("每天最多 4 小时"), (state.request as AiReplanRequest).constraintSummary)
        job.cancel()
    }

    // --- consent and the switch -----------------------------------------------------------------

    @Test
    fun `grantConsentAndEnable records consent and moves a prepared request to preview`() =
        runTest(dispatcher) {
            settings.state.value = AiPreferences(isEnabled = false, consentedAtEpochMillis = null)
            val vm = viewModel()
            val job = collectInBackground(vm.uiState)
            advanceUntilIdle()
            vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.workflow is PlanningAgentState.AwaitingConsent)

            vm.grantConsentAndEnable()
            advanceUntilIdle()

            assertEquals(1, settings.grantConsentCount)
            assertTrue(vm.uiState.value.workflow is PlanningAgentState.PreviewingRequest)
            job.cancel()
        }

    @Test
    fun `a failed grantConsentAndEnable changes nothing and does not crash`() = runTest(dispatcher) {
        settings.state.value = AiPreferences(isEnabled = false, consentedAtEpochMillis = null)
        settings.failWith = IllegalStateException("write failed")
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
        advanceUntilIdle()

        vm.grantConsentAndEnable()
        advanceUntilIdle()

        // runCatching with only an onSuccess branch: the failure is swallowed and the state keeps
        // waiting for consent. No message is shown to the user.
        assertTrue(vm.uiState.value.workflow is PlanningAgentState.AwaitingConsent)
        job.cancel()
    }

    @Test
    fun `switching off records the change and ends a prepared request locally`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.workflow is PlanningAgentState.PreviewingRequest)

        vm.setEnabled(false)
        advanceUntilIdle()

        assertEquals(listOf(false), settings.enabledCalls)
        val state = vm.uiState.value.workflow
        assertTrue(state is PlanningAgentState.FailedFallback)
        assertEquals(
            AiAdvisorFailureReason.DISABLED,
            (state as PlanningAgentState.FailedFallback).reason,
        )
        job.cancel()
    }

    @Test
    fun `switching on with consent already given returns a prepared request to preview`() =
        runTest(dispatcher) {
            settings.state.value =
                AiPreferences(isEnabled = false, consentedAtEpochMillis = 1_700_000_000_000L)
            val vm = viewModel()
            val job = collectInBackground(vm.uiState)
            advanceUntilIdle()
            vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.workflow is PlanningAgentState.FailedFallback)

            vm.setEnabled(true)
            advanceUntilIdle()

            assertEquals(listOf(true), settings.enabledCalls)
            assertTrue(vm.uiState.value.workflow is PlanningAgentState.PreviewingRequest)
            job.cancel()
        }

    @Test
    fun `switching off with nothing prepared still records the change`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setEnabled(false)
        advanceUntilIdle()

        assertEquals(listOf(false), settings.enabledCalls)
        assertEquals(PlanningAgentState.Idle, workflow.state.value)
    }

    @Test
    fun `switching off ends a prepared request even when recording the change fails`() =
        runTest(dispatcher) {
            settings.state.value = consentedAndEnabled
            val vm = viewModel()
            val job = collectInBackground(vm.uiState)
            advanceUntilIdle()
            vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.workflow is PlanningAgentState.PreviewingRequest)

            settings.failWith = IllegalStateException("write failed")
            vm.setEnabled(false)
            advanceUntilIdle()

            // The local ending must not depend on the write landing. If it did, a failed write
            // would leave the agent sitting on a request the user has just switched off — and the
            // onSuccess branch that also ends it would never run.
            val state = vm.uiState.value.workflow
            assertTrue(state is PlanningAgentState.FailedFallback)
            assertEquals(
                AiAdvisorFailureReason.DISABLED,
                (state as PlanningAgentState.FailedFallback).reason,
            )
            job.cancel()
        }

    @Test
    fun `switching on without consent leaves the request waiting for consent`() = runTest(dispatcher) {
        settings.state.value = AiPreferences(isEnabled = false, consentedAtEpochMillis = null)
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.workflow is PlanningAgentState.AwaitingConsent)

        vm.setEnabled(true)
        advanceUntilIdle()

        // Enabling is not consent. The resume has to carry the *stored* consent flag, not assume
        // it, or flipping the switch would push a request out without the user ever agreeing.
        assertTrue(vm.uiState.value.workflow is PlanningAgentState.AwaitingConsent)
        job.cancel()
    }

    // --- dismiss and accept ---------------------------------------------------------------------

    @Test
    fun `dismiss moves a prepared request to the dismissed state`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
        advanceUntilIdle()

        vm.dismiss()
        advanceUntilIdle()

        assertEquals(
            PlanningAgentState.UserDismissed(PlanningAgentRequestType.TASK_UNDERSTANDING),
            vm.uiState.value.workflow,
        )
        // Dismissing never invokes the provider.
        assertEquals(emptyList<Any>(), aiAdvisor.requests)
        job.cancel()
    }

    @Test
    fun `dismiss with nothing prepared leaves the agent idle`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.dismiss()
        advanceUntilIdle()

        assertEquals(PlanningAgentState.Idle, workflow.state.value)
    }

    @Test
    fun `markAccepted only acknowledges, it is not a write`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
        advanceUntilIdle()

        vm.markAccepted()
        advanceUntilIdle()

        assertEquals(
            PlanningAgentState.UserAccepted(PlanningAgentRequestType.TASK_UNDERSTANDING),
            vm.uiState.value.workflow,
        )
        job.cancel()
    }

    @Test
    fun `markAccepted with nothing prepared leaves the agent idle`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.markAccepted()
        advanceUntilIdle()

        assertEquals(PlanningAgentState.Idle, workflow.state.value)
    }

    // --- confirm --------------------------------------------------------------------------------

    @Test
    fun `confirmPreview with nothing prepared never contacts the provider`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.confirmPreview()
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), aiAdvisor.requests)
        assertEquals(PlanningAgentState.Idle, workflow.state.value)
    }

    @Test
    fun `confirmPreview refuses when consent has been withdrawn since the preview`() =
        runTest(dispatcher) {
            settings.state.value = consentedAndEnabled
            val vm = viewModel()
            val job = collectInBackground(vm.uiState)
            advanceUntilIdle()
            vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.workflow is PlanningAgentState.PreviewingRequest)

            // Consent is withdrawn between preview and confirm.
            settings.state.value = AiPreferences(isEnabled = true, consentedAtEpochMillis = null)
            advanceUntilIdle()

            vm.confirmPreview()
            advanceUntilIdle()

            // Re-checking the access at confirm time is what stops a request going out after
            // consent is gone; trusting the earlier preview would send it anyway.
            assertEquals(emptyList<Any>(), aiAdvisor.requests)
            assertTrue(vm.uiState.value.workflow is PlanningAgentState.AwaitingConsent)
            job.cancel()
        }

    @Test
    fun `confirmPreview shows RequestingAdvice while the provider is in flight`() =
        runTest(dispatcher) {
            settings.state.value = consentedAndEnabled
            val vm = viewModel()
            val job = collectInBackground(vm.uiState)
            advanceUntilIdle()
            vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
            advanceUntilIdle()
            val gate = CompletableDeferred<Unit>()
            aiAdvisor.gate = gate

            vm.confirmPreview()
            advanceUntilIdle()

            assertEquals(
                PlanningAgentState.RequestingAdvice(PlanningAgentRequestType.TASK_UNDERSTANDING),
                vm.uiState.value.workflow,
            )

            gate.complete(Unit)
            advanceUntilIdle()
            job.cancel()
        }

    @Test
    fun `a provider that is not configured falls back locally with its reason`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled
        aiAdvisor.result =
            AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
        advanceUntilIdle()

        vm.confirmPreview()
        advanceUntilIdle()

        val state = vm.uiState.value.workflow
        assertTrue(state is PlanningAgentState.FailedFallback)
        assertEquals(
            AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED,
            (state as PlanningAgentState.FailedFallback).reason,
        )
        assertEquals(1, aiAdvisor.requests.size)
        job.cancel()
    }

    @Test
    fun `a provider failure falls back locally with its reason`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled
        aiAdvisor.result = AiAdvisorResult.Failed(AiAdvisorFailureReason.TIMEOUT)
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
        advanceUntilIdle()

        vm.confirmPreview()
        advanceUntilIdle()

        val state = vm.uiState.value.workflow as PlanningAgentState.FailedFallback
        assertEquals(AiAdvisorFailureReason.TIMEOUT, state.reason)
        job.cancel()
    }

    @Test
    fun `a transport exception from the provider becomes a local fallback, not a crash`() =
        runTest(dispatcher) {
            settings.state.value = consentedAndEnabled
            aiAdvisor.failWith = IllegalStateException("socket closed")
            val vm = viewModel()
            val job = collectInBackground(vm.uiState)
            advanceUntilIdle()
            vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
            advanceUntilIdle()

            vm.confirmPreview()
            advanceUntilIdle()

            // PlanningAgent.request catches Throwable and converts it to TRANSPORT_FAILURE, so the
            // screen still gets a usable local fallback.
            val state = vm.uiState.value.workflow as PlanningAgentState.FailedFallback
            assertEquals(AiAdvisorFailureReason.TRANSPORT_FAILURE, state.reason)
            job.cancel()
        }

    @Test
    fun `the fallback state never claims the provider answered`() = runTest(dispatcher) {
        settings.state.value = consentedAndEnabled
        aiAdvisor.result = AiAdvisorResult.Failed(AiAdvisorFailureReason.TIMEOUT)
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.beginTaskUnderstanding(task("t1"), emptyList(), null)
        advanceUntilIdle()

        vm.confirmPreview()
        advanceUntilIdle()

        val state = vm.uiState.value.workflow
        assertFalse(state is PlanningAgentState.ShowingAdvice)
        assertFalse(state is PlanningAgentState.ShowingDraft)
        job.cancel()
    }
}
