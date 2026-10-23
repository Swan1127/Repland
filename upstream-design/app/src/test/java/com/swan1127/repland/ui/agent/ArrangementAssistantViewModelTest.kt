package com.swan1127.repland.ui.agent

import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiPreferences
import com.swan1127.repland.domain.model.ArrangementAssistantAdvice
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceResult
import com.swan1127.repland.domain.model.TEST_MONDAY
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.testkit.FakeAiSettingsRepository
import com.swan1127.repland.testkit.FakeArrangementAssistantAdvisor
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.collectInBackground
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArrangementAssistantViewModelTest : ViewModelTestBase() {

    private val repository = FakeAiSettingsRepository()
    private val advisor = FakeArrangementAssistantAdvisor()

    private fun viewModel() = ArrangementAssistantViewModel(repository, advisor)

    private fun entry(id: String, kind: TimelineKind, start: Int, end: Int) = TimelineEntry(
        id = id,
        title = "entry $id",
        kind = kind,
        date = TEST_MONDAY,
        startMinute = start,
        endMinute = end,
        trackId = "course",
    )

    private fun enabledAndConsented() =
        AiPreferences(isEnabled = true, consentedAtEpochMillis = 1_700_000_000_000L)

    // --- access state ---------------------------------------------------------------------------

    @Test
    fun `access starts disabled and without consent before collection`() = runTest(dispatcher) {
        repository.state.value = enabledAndConsented()

        val vm = viewModel()

        assertFalse(vm.access.value.isEnabled)
        assertFalse(vm.access.value.hasExplicitConsent)
    }

    @Test
    fun `collecting reports the stored access`() = runTest(dispatcher) {
        repository.state.value = enabledAndConsented()
        val vm = viewModel()

        val job = collectInBackground(vm.access)
        advanceUntilIdle()

        assertEquals(true, vm.access.value.isEnabled)
        assertEquals(true, vm.access.value.hasExplicitConsent)
        job.cancel()
    }

    // --- the access guard -----------------------------------------------------------------------

    @Test
    fun `refine refuses when the access state was never collected`() = runTest(dispatcher) {
        // The guard reads `access.value`, and that is a WhileSubscribed stateIn. With nobody
        // collecting, `.value` is the declared initial value — disabled — so the advisor is never
        // consulted even though the repository holds an enabled, consented preference.
        repository.state.value = enabledAndConsented()
        val vm = viewModel()

        val result = vm.refine("今天下午安排一下复习", TEST_MONDAY, emptyList())

        assertEquals(
            ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.DISABLED),
            result,
        )
        assertEquals(emptyList<Any>(), advisor.requests)
    }

    @Test
    fun `refine refuses when consent is missing even though it is enabled`() = runTest(dispatcher) {
        repository.state.value = AiPreferences(isEnabled = true, consentedAtEpochMillis = null)
        val vm = viewModel()
        val job = collectInBackground(vm.access)
        advanceUntilIdle()
        assertEquals(true, vm.access.value.isEnabled)
        assertFalse(vm.access.value.hasExplicitConsent)

        val result = vm.refine("安排一下", TEST_MONDAY, emptyList())

        assertEquals(
            ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.DISABLED),
            result,
        )
        assertEquals(emptyList<Any>(), advisor.requests)
        job.cancel()
    }

    @Test
    fun `refine refuses when consent exists but the feature is switched off`() = runTest(dispatcher) {
        repository.state.value = AiPreferences(
            isEnabled = false,
            consentedAtEpochMillis = 1_700_000_000_000L,
        )
        val vm = viewModel()
        val job = collectInBackground(vm.access)
        advanceUntilIdle()
        assertFalse(vm.access.value.isEnabled)
        assertEquals(true, vm.access.value.hasExplicitConsent)

        val result = vm.refine("安排一下", TEST_MONDAY, emptyList())

        assertEquals(
            ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.DISABLED),
            result,
        )
        job.cancel()
    }

    // --- the request the advisor receives -------------------------------------------------------

    @Test
    fun `refine forwards the utterance and the date`() = runTest(dispatcher) {
        repository.state.value = enabledAndConsented()
        val vm = viewModel()
        val job = collectInBackground(vm.access)
        advanceUntilIdle()

        vm.refine("把这周的复习放到晚上", TEST_MONDAY, emptyList())

        val request = advisor.requests.single()
        assertEquals("把这周的复习放到晚上", request.utterance)
        assertEquals(TEST_MONDAY, request.date)
        job.cancel()
    }

    @Test
    fun `courses rest and commitments are marked hard busy while a task is not`() =
        runTest(dispatcher) {
            repository.state.value = enabledAndConsented()
            val vm = viewModel()
            val job = collectInBackground(vm.access)
            advanceUntilIdle()
            val entries = listOf(
                entry("course", TimelineKind.COURSE, 480, 540),
                entry("rest", TimelineKind.REST, 600, 660),
                entry("commitment", TimelineKind.COMMITMENT, 700, 720),
                entry("task", TimelineKind.TASK, 800, 900),
            )

            vm.refine("安排一下", TEST_MONDAY, entries)

            val intervals = advisor.requests.single().occupiedIntervals
            assertEquals(
                listOf(true, true, true, false),
                intervals.map { it.isHardBusy },
            )
            job.cancel()
        }

    @Test
    fun `each occupied interval keeps its title, minutes and track`() = runTest(dispatcher) {
        repository.state.value = enabledAndConsented()
        val vm = viewModel()
        val job = collectInBackground(vm.access)
        advanceUntilIdle()

        vm.refine("安排一下", TEST_MONDAY, listOf(entry("course", TimelineKind.COURSE, 480, 540)))

        val interval = advisor.requests.single().occupiedIntervals.single()
        assertEquals("entry course", interval.title)
        assertEquals(480, interval.startMinute)
        assertEquals(540, interval.endMinute)
        assertEquals("course", interval.trackId)
        job.cancel()
    }

    @Test
    fun `an empty entry list produces no occupied intervals`() = runTest(dispatcher) {
        repository.state.value = enabledAndConsented()
        val vm = viewModel()
        val job = collectInBackground(vm.access)
        advanceUntilIdle()

        vm.refine("安排一下", TEST_MONDAY, emptyList())

        assertEquals(emptyList<Any>(), advisor.requests.single().occupiedIntervals)
        job.cancel()
    }

    // --- the advisor's answer is passed straight back -------------------------------------------

    @Test
    fun `an advice result is returned verbatim`() = runTest(dispatcher) {
        repository.state.value = enabledAndConsented()
        val advice = ArrangementAssistantAdvice(candidates = emptyList(), confidenceLabel = "中等")
        advisor.result = ArrangementAssistantAdviceResult.Advice(advice)
        val vm = viewModel()
        val job = collectInBackground(vm.access)
        advanceUntilIdle()

        val result = vm.refine("安排一下", TEST_MONDAY, emptyList())

        assertEquals(ArrangementAssistantAdviceResult.Advice(advice), result)
        job.cancel()
    }

    @Test
    fun `a failure result is returned verbatim`() = runTest(dispatcher) {
        repository.state.value = enabledAndConsented()
        advisor.result = ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.TIMEOUT)
        val vm = viewModel()
        val job = collectInBackground(vm.access)
        advanceUntilIdle()

        val result = vm.refine("安排一下", TEST_MONDAY, emptyList())

        assertEquals(
            ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.TIMEOUT),
            result,
        )
        job.cancel()
    }

    @Test
    fun `the advisor is consulted exactly once per refine`() = runTest(dispatcher) {
        repository.state.value = enabledAndConsented()
        val vm = viewModel()
        val job = collectInBackground(vm.access)
        advanceUntilIdle()

        vm.refine("第一次", TEST_MONDAY, emptyList())
        vm.refine("第二次", TEST_MONDAY, emptyList())

        assertEquals(listOf("第一次", "第二次"), advisor.requests.map { it.utterance })
        job.cancel()
    }
}
