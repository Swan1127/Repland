package com.swan1127.repland.ui.engagement

import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.testkit.FakeEngagementRepository
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.collectInBackground
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngagementViewModelTest : ViewModelTestBase() {

    private val repository = FakeEngagementRepository()

    @Test
    fun `the exposed mode is the declared initial value, not the stored one, until it is collected`() =
        runTest(dispatcher) {
            repository.state.value = EngagementMode.EXECUTOR

            val viewModel = EngagementViewModel(repository)

            // WhileSubscribed(5_000) means the repository is not read until somebody subscribes.
            // If this ever starts returning EXECUTOR, the sharing strategy changed.
            assertEquals(EngagementMode.GUIDED, viewModel.mode.value)
        }

    @Test
    fun `collecting the mode picks up the stored value`() = runTest(dispatcher) {
        repository.state.value = EngagementMode.EXECUTOR
        val viewModel = EngagementViewModel(repository)

        val job = collectInBackground(viewModel.mode)
        advanceUntilIdle()

        assertEquals(EngagementMode.EXECUTOR, viewModel.mode.value)
        job.cancel()
    }

    @Test
    fun `setMode is fire and forget, so the write has not happened until the scope runs`() =
        runTest(dispatcher) {
            val viewModel = EngagementViewModel(repository)
            val job = collectInBackground(viewModel.mode)
            advanceUntilIdle()

            viewModel.setMode(EngagementMode.CO_PLANNER)
            assertEquals(emptyList<EngagementMode>(), repository.modes)

            advanceUntilIdle()
            assertEquals(listOf(EngagementMode.CO_PLANNER), repository.modes)
            job.cancel()
        }

    @Test
    fun `a stored mode change reaches the view model`() = runTest(dispatcher) {
        val viewModel = EngagementViewModel(repository)
        val job = collectInBackground(viewModel.mode)
        advanceUntilIdle()

        viewModel.setMode(EngagementMode.EXECUTOR)
        advanceUntilIdle()

        assertEquals(EngagementMode.EXECUTOR, viewModel.mode.value)
        job.cancel()
    }

    @Test
    fun `recordTimelineOpened forwards the entry id verbatim`() = runTest(dispatcher) {
        val viewModel = EngagementViewModel(repository)

        viewModel.recordTimelineOpened("entry-42")
        advanceUntilIdle()

        assertEquals(listOf("entry-42"), repository.openedEntries)
    }

    @Test
    fun `two mode changes are forwarded in call order`() = runTest(dispatcher) {
        val viewModel = EngagementViewModel(repository)

        viewModel.setMode(EngagementMode.CO_PLANNER)
        viewModel.setMode(EngagementMode.GUIDED)
        advanceUntilIdle()

        assertEquals(
            listOf(EngagementMode.CO_PLANNER, EngagementMode.GUIDED),
            repository.modes,
        )
    }
}
