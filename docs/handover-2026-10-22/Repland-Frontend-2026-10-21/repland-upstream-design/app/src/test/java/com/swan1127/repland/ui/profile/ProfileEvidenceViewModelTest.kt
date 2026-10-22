package com.swan1127.repland.ui.profile

import com.swan1127.repland.domain.model.ProfileEvidence
import com.swan1127.repland.domain.model.ProfileEvidenceScope
import com.swan1127.repland.domain.model.ProfileEvidenceValidator
import com.swan1127.repland.testkit.FakeProfileEvidenceRepository
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.collectInBackground
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEvidenceViewModelTest : ViewModelTestBase() {

    private val repository = FakeProfileEvidenceRepository()

    private fun evidence(id: String) = ProfileEvidence(
        id = id,
        scope = ProfileEvidenceScope.GENERAL,
        conclusion = "已确认记录 1 条执行反馈",
        sources = emptyList(),
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 1_000L,
        isUserEdited = false,
    )

    @Test
    fun `before collection the state is empty, loading and error free`() = runTest(dispatcher) {
        repository.state.value = listOf(evidence("e1"))

        val viewModel = ProfileEvidenceViewModel(repository)

        assertEquals(emptyList<ProfileEvidence>(), viewModel.uiState.value.evidence)
        assertTrue(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.hasActionError)
    }

    @Test
    fun `collecting reports the stored evidence and stops loading`() = runTest(dispatcher) {
        repository.state.value = listOf(evidence("e1"), evidence("e2"))
        val viewModel = ProfileEvidenceViewModel(repository)

        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        assertEquals(listOf(evidence("e1"), evidence("e2")), viewModel.uiState.value.evidence)
        assertEquals(false, viewModel.uiState.value.isLoading)
        job.cancel()
    }

    @Test
    fun `a blank conclusion is refused and never reaches the repository`() = runTest(dispatcher) {
        val viewModel = ProfileEvidenceViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.updateConclusion("e1", "")
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, String>>(), repository.updatedConclusions)
        assertTrue(viewModel.uiState.value.hasActionError)
        job.cancel()
    }

    @Test
    fun `a whitespace-only conclusion is refused`() = runTest(dispatcher) {
        val viewModel = ProfileEvidenceViewModel(repository)

        viewModel.updateConclusion("e1", "   \n  ")
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, String>>(), repository.updatedConclusions)
    }

    @Test
    fun `a conclusion over the length cap is refused`() = runTest(dispatcher) {
        val viewModel = ProfileEvidenceViewModel(repository)

        viewModel.updateConclusion("e1", "字".repeat(ProfileEvidenceValidator.MAX_CONCLUSION_LENGTH + 1))
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, String>>(), repository.updatedConclusions)
    }

    @Test
    fun `a conclusion exactly at the length cap is accepted`() = runTest(dispatcher) {
        val viewModel = ProfileEvidenceViewModel(repository)
        val atCap = "字".repeat(ProfileEvidenceValidator.MAX_CONCLUSION_LENGTH)

        viewModel.updateConclusion("e1", atCap)
        advanceUntilIdle()

        assertEquals(listOf("e1" to atCap), repository.updatedConclusions)
    }

    @Test
    fun `the refusal is reported through the state, not thrown`() = runTest(dispatcher) {
        val viewModel = ProfileEvidenceViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasActionError)

        viewModel.updateConclusion("e1", "")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasActionError)
        job.cancel()
    }

    @Test
    fun `generate is forwarded once`() = runTest(dispatcher) {
        val viewModel = ProfileEvidenceViewModel(repository)

        viewModel.generate()
        advanceUntilIdle()

        assertEquals(1, repository.generateCount)
    }

    @Test
    fun `delete forwards the id`() = runTest(dispatcher) {
        val viewModel = ProfileEvidenceViewModel(repository)

        viewModel.delete("e9")
        advanceUntilIdle()

        assertEquals(listOf("e9"), repository.deletedIds)
    }

    @Test
    fun `a failed action raises the error flag`() = runTest(dispatcher) {
        repository.failWith = IllegalStateException("network down")
        val viewModel = ProfileEvidenceViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.generate()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasActionError)
        job.cancel()
    }

    @Test
    fun `a later successful action clears the error flag`() = runTest(dispatcher) {
        val viewModel = ProfileEvidenceViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        repository.failWith = IllegalStateException("network down")
        viewModel.generate()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasActionError)

        repository.failWith = null
        viewModel.delete("e1")
        advanceUntilIdle()

        // `action` sets the flag on both branches, so success must clear a previous failure
        // rather than leaving the banner stuck on screen.
        assertFalse(viewModel.uiState.value.hasActionError)
        job.cancel()
    }

    @Test
    fun `a throwing observation stream reports the action error and stops loading`() =
        runTest(dispatcher) {
            repository.observeFlow = flow { throw IllegalStateException("read failed") }
            val viewModel = ProfileEvidenceViewModel(repository)

            val job = collectInBackground(viewModel.uiState)
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.hasActionError)
            job.cancel()
        }
}
