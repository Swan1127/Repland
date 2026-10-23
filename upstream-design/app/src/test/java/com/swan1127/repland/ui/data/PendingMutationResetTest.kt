package com.swan1127.repland.ui.data

import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.ProfileEvidence
import com.swan1127.repland.domain.model.ProfileEvidenceScope
import com.swan1127.repland.domain.model.ReminderPreferences
import com.swan1127.repland.testkit.FakeCategoryPreferenceRepository
import com.swan1127.repland.testkit.FakeEngagementRepository
import com.swan1127.repland.testkit.FakeProfileEvidenceRepository
import com.swan1127.repland.testkit.FakeReminderSettingsRepository
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.collectInBackground
import com.swan1127.repland.ui.engagement.EngagementViewModel
import com.swan1127.repland.ui.preferences.CategoryPreferenceViewModel
import com.swan1127.repland.ui.profile.ProfileEvidenceViewModel
import com.swan1127.repland.ui.reminders.ReminderSettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingMutationResetTest : ViewModelTestBase() {
    @Test
    fun `reset cancels all blocked profile writes and keeps observation and later actions alive`() = runTest(dispatcher) {
        val repository = FakeProfileEvidenceRepository()
        val vm = ProfileEvidenceViewModel(repository)
        val collector = collectInBackground(vm.uiState)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        vm.generate()
        vm.updateConclusion("old", "confirmed writing")
        vm.delete("old")
        advanceUntilIdle()

        vm.cancelPendingMutations()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, repository.generateCount)
        assertTrue(repository.updatedConclusions.isEmpty())
        assertTrue(repository.deletedIds.isEmpty())
        assertFalse(vm.uiState.value.hasActionError)

        val evidence = ProfileEvidence("new", ProfileEvidenceScope.GENERAL, "new evidence", emptyList(), 1L, 1L, false)
        repository.state.value = listOf(evidence)
        advanceUntilIdle()
        assertEquals(listOf(evidence), vm.uiState.value.evidence)
        repository.gate = null
        vm.generate()
        advanceUntilIdle()
        assertEquals(1, repository.generateCount)
        collector.cancel()
    }

    @Test
    fun `reset cancels blocked preference save without cancelling observed defaults or later saves`() = runTest(dispatcher) {
        val repository = FakeCategoryPreferenceRepository()
        val vm = CategoryPreferenceViewModel(repository)
        val collector = collectInBackground(vm.uiState)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        vm.save(CategoryPreferences.defaults)
        advanceUntilIdle()
        vm.cancelPendingMutations()
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(repository.savedWeights.isEmpty())

        val updated = CategoryPreferences.defaults.mapValues { 4 }
        repository.state.value = updated
        advanceUntilIdle()
        assertEquals(updated, vm.uiState.value.weights)
        repository.gate = null
        vm.save(CategoryPreferences.defaults)
        advanceUntilIdle()
        assertEquals(listOf(CategoryPreferences.defaults), repository.savedWeights)
        collector.cancel()
    }

    @Test
    fun `reset cancels blocked reminder change while keeping observation and future settings alive`() = runTest(dispatcher) {
        val repository = FakeReminderSettingsRepository()
        val vm = ReminderSettingsViewModel(repository)
        val collector = collectInBackground(vm.uiState)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        vm.setEnabled(true)
        advanceUntilIdle()
        vm.cancelPendingMutations()
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(repository.enabledCalls.isEmpty())

        repository.state.value = ReminderPreferences(true)
        advanceUntilIdle()
        assertEquals(ReminderPreferences(true), vm.uiState.value.preferences)
        repository.gate = null
        vm.setEnabled(false)
        advanceUntilIdle()
        assertEquals(listOf(false), repository.enabledCalls)
        collector.cancel()
    }

    @Test
    fun `reset cancels blocked usage records and mode changes but not live observation`() = runTest(dispatcher) {
        val repository = FakeEngagementRepository()
        val vm = EngagementViewModel(repository)
        val collector = collectInBackground(vm.mode)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        vm.setMode(EngagementMode.CO_PLANNER)
        vm.recordTimelineOpened("old-entry")
        advanceUntilIdle()
        vm.cancelPendingMutations()
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(repository.modes.isEmpty())
        assertTrue(repository.openedEntries.isEmpty())

        repository.state.value = EngagementMode.EXECUTOR
        advanceUntilIdle()
        assertEquals(EngagementMode.EXECUTOR, vm.mode.value)
        repository.gate = null
        vm.recordTimelineOpened("new-entry")
        advanceUntilIdle()
        assertEquals(listOf("new-entry"), repository.openedEntries)
        collector.cancel()
    }
}
