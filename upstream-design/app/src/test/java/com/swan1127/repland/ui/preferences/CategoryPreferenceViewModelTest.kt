package com.swan1127.repland.ui.preferences

import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.testkit.FakeCategoryPreferenceRepository
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
class CategoryPreferenceViewModelTest : ViewModelTestBase() {

    private val repository = FakeCategoryPreferenceRepository()

    private val customWeights = mapOf(
        TaskCategory.COURSE to 40,
        TaskCategory.EXTRACURRICULAR to 30,
        TaskCategory.OFFICE to 20,
        TaskCategory.LEISURE to 10,
    )

    @Test
    fun `before collection the state is loading with the built-in defaults`() = runTest(dispatcher) {
        repository.state.value = customWeights

        val viewModel = CategoryPreferenceViewModel(repository)

        // The initial value is declared by the view model, so it must not leak the stored weights
        // before anyone has subscribed.
        assertEquals(CategoryPreferences.defaults, viewModel.uiState.value.weights)
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `collecting replaces the defaults with the stored weights and clears loading`() =
        runTest(dispatcher) {
            repository.state.value = customWeights
            val viewModel = CategoryPreferenceViewModel(repository)

            val job = collectInBackground(viewModel.uiState)
            advanceUntilIdle()

            assertEquals(customWeights, viewModel.uiState.value.weights)
            assertEquals(false, viewModel.uiState.value.isLoading)
            job.cancel()
        }

    @Test
    fun `saving an all-zero weighting never reaches the repository`() = runTest(dispatcher) {
        val viewModel = CategoryPreferenceViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        // CategoryPreferences.isValid requires the normalised sum to be positive, so an all-zero
        // map is refused. A repository call here would wipe every category weight.
        viewModel.save(TaskCategory.entries.associateWith { 0 })
        advanceUntilIdle()

        assertEquals(emptyList<Map<TaskCategory, Int>>(), repository.savedWeights)
        job.cancel()
    }

    @Test
    fun `saving an out-of-range weighting never reaches the repository`() = runTest(dispatcher) {
        val viewModel = CategoryPreferenceViewModel(repository)

        viewModel.save(customWeights + (TaskCategory.COURSE to 101))
        advanceUntilIdle()

        assertEquals(emptyList<Map<TaskCategory, Int>>(), repository.savedWeights)
    }

    @Test
    fun `a refused save is silent, it does not surface as an error state`() = runTest(dispatcher) {
        val viewModel = CategoryPreferenceViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.save(TaskCategory.entries.associateWith { 0 })
        advanceUntilIdle()

        // There is no error field on this state at all: a refused save leaves the previous
        // weights on screen with no indication that nothing happened.
        assertEquals(CategoryPreferences.defaults, viewModel.uiState.value.weights)
        assertEquals(false, viewModel.uiState.value.isLoading)
        job.cancel()
    }

    @Test
    fun `a valid save is forwarded unchanged`() = runTest(dispatcher) {
        val viewModel = CategoryPreferenceViewModel(repository)

        viewModel.save(customWeights)
        advanceUntilIdle()

        assertEquals(listOf(customWeights), repository.savedWeights)
    }

    @Test
    fun `a failed save escapes the coroutine scope instead of being reported`() {
        repository.failWith = IllegalStateException("disk full")

        // FINDING, pinned rather than papered over. This view model has no `runCatching` around
        // its save and no error field on its state, so a repository failure is not translated into
        // anything the UI can show: it leaves `viewModelScope.launch` as an uncaught exception.
        // In production the scope runs on Dispatchers.Main.immediate, so that is a main-thread
        // uncaught exception, not a silent no-op.
        //
        // The assertion has to wrap the whole `runTest` call: the exception is rethrown when the
        // test builder drains the scheduler, not from `advanceUntilIdle` itself.
        val thrown = assertThrows(IllegalStateException::class.java) {
            runTest(dispatcher) {
                val viewModel = CategoryPreferenceViewModel(repository)
                val job = collectInBackground(viewModel.uiState)
                advanceUntilIdle()

                viewModel.save(customWeights)
                advanceUntilIdle()
                job.cancel()
            }
        }

        assertEquals("disk full", thrown.message)
        assertEquals(emptyList<Map<TaskCategory, Int>>(), repository.savedWeights)
    }

    @Test
    fun `a throwing observation stream falls back to defaults without hanging on loading`() =
        runTest(dispatcher) {
            repository.observeFlow = flow { throw IllegalStateException("read failed") }
            val viewModel = CategoryPreferenceViewModel(repository)

            val job = collectInBackground(viewModel.uiState)
            advanceUntilIdle()

            assertEquals(CategoryPreferences.defaults, viewModel.uiState.value.weights)
            assertEquals(false, viewModel.uiState.value.isLoading)
            job.cancel()
        }

    @Test
    fun `a stored update is reflected while the state is being collected`() = runTest(dispatcher) {
        val viewModel = CategoryPreferenceViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        repository.state.value = customWeights
        advanceUntilIdle()

        assertEquals(customWeights, viewModel.uiState.value.weights)
        job.cancel()
    }
}
