package com.swan1127.repland.ui.reminders

import com.swan1127.repland.domain.model.ReminderPreferences
import com.swan1127.repland.testkit.FakeReminderSettingsRepository
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.collectInBackground
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSettingsViewModelTest : ViewModelTestBase() {

    private val repository = FakeReminderSettingsRepository()

    @Test
    fun `before collection the state is loading with reminders off`() = runTest(dispatcher) {
        repository.state.value = ReminderPreferences(isEnabled = true)

        val viewModel = ReminderSettingsViewModel(repository)

        assertEquals(ReminderPreferences(isEnabled = false), viewModel.uiState.value.preferences)
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `collecting reports the stored preference and stops loading`() = runTest(dispatcher) {
        repository.state.value = ReminderPreferences(isEnabled = true)
        val viewModel = ReminderSettingsViewModel(repository)

        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        assertEquals(ReminderPreferences(isEnabled = true), viewModel.uiState.value.preferences)
        assertEquals(false, viewModel.uiState.value.isLoading)
        job.cancel()
    }

    @Test
    fun `setEnabled forwards both true and false in order`() = runTest(dispatcher) {
        val viewModel = ReminderSettingsViewModel(repository)

        viewModel.setEnabled(true)
        viewModel.setEnabled(false)
        advanceUntilIdle()

        assertEquals(listOf(true, false), repository.enabledCalls)
    }

    @Test
    fun `setEnabled is not applied until the scope runs`() = runTest(dispatcher) {
        val viewModel = ReminderSettingsViewModel(repository)

        viewModel.setEnabled(true)
        assertEquals(emptyList<Boolean>(), repository.enabledCalls)

        advanceUntilIdle()
        assertEquals(listOf(true), repository.enabledCalls)
    }

    @Test
    fun `a stored change is reflected while collecting`() = runTest(dispatcher) {
        val viewModel = ReminderSettingsViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.setEnabled(true)
        advanceUntilIdle()

        assertEquals(ReminderPreferences(isEnabled = true), viewModel.uiState.value.preferences)
        job.cancel()
    }

    @Test
    fun `a throwing observation stream escapes the scope instead of clearing loading`() {
        // FINDING, pinned rather than papered over. Unlike ProfileEvidenceViewModel and
        // TaskViewModel, this view model has no `catch` on its observed flow, so a read failure is
        // not turned into a state: the sharing coroutine fails and the exception leaves
        // viewModelScope as an uncaught exception, while `isLoading` stays at its declared `true`
        // forever. The screen would spin and the process would take a main-thread exception.
        val thrown = assertThrows(IllegalStateException::class.java) {
            runTest(dispatcher) {
                repository.observeFlow = flow { throw IllegalStateException("read failed") }
                val viewModel = ReminderSettingsViewModel(repository)
                val job = collectInBackground(viewModel.uiState)
                advanceUntilIdle()
                job.cancel()
            }
        }

        assertEquals("read failed", thrown.message)
    }
}
