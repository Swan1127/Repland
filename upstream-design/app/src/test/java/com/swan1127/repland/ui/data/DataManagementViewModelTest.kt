package com.swan1127.repland.ui.data

import com.swan1127.repland.data.export.LocalDataJsonExporter
import com.swan1127.repland.domain.ports.DataExportFormat
import com.swan1127.repland.domain.ports.ExportSink
import com.swan1127.repland.testkit.FakeDataManagementRepository
import com.swan1127.repland.testkit.ViewModelTestBase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataManagementViewModelTest : ViewModelTestBase() {

    private val repository = FakeDataManagementRepository()
    private val written = mutableListOf<ByteArray>()
    private val sink = ExportSink { written += it }

    private fun viewModel() = DataManagementViewModel(repository)

    @Test
    fun `reset invalidates old ui state and export preserves that reset version`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.clearAllLocalData()
        advanceUntilIdle()
        assertEquals(1L, vm.uiState.value.localResetVersion)
        vm.exportTo(DataExportFormat.JSON, sink)
        advanceUntilIdle()
        assertEquals(1L, vm.uiState.value.localResetVersion)
        vm.clearAllLocalData()
        advanceUntilIdle()
        assertEquals(2L, vm.uiState.value.localResetVersion)
    }

    @Test
    fun `a partial reset failure also discards old ui caches`() = runTest(dispatcher) {
        repository.failWith = IllegalStateException("one store failed")
        val vm = viewModel()
        vm.clearAllLocalData()
        advanceUntilIdle()
        assertEquals(DataManagementResult.CLEAR_FAILED, vm.uiState.value.result)
        assertEquals(1L, vm.uiState.value.localResetVersion)
    }

    @Test
    fun `duplicate clear taps and export cannot race an in flight reset`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val vm = viewModel()
        vm.clearAllLocalData()
        vm.clearAllLocalData()
        vm.exportTo(DataExportFormat.JSON, sink)
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repository.clearCount)
        assertTrue(written.isEmpty())
        assertEquals(1L, vm.uiState.value.localResetVersion)
    }

    @Test
    fun `before any work the state is idle`() = runTest(dispatcher) {
        val vm = viewModel()

        assertEquals(false, vm.uiState.value.isWorking)
        assertNull(vm.uiState.value.result)
    }

    @Test
    fun `a successful clear reports success and stops working`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.clearAllLocalData()
        advanceUntilIdle()

        assertEquals(1, repository.clearCount)
        assertEquals(false, vm.uiState.value.isWorking)
        assertEquals(DataManagementResult.CLEAR_SUCCEEDED, vm.uiState.value.result)
    }

    @Test
    fun `a failed clear reports failure and stops working`() = runTest(dispatcher) {
        repository.failWith = IllegalStateException("database locked")
        val vm = viewModel()

        vm.clearAllLocalData()
        advanceUntilIdle()

        // The failure is caught, so the screen gets a result instead of a crash — and the wipe
        // must not be reported as having happened.
        assertEquals(0, repository.clearCount)
        assertEquals(false, vm.uiState.value.isWorking)
        assertEquals(DataManagementResult.CLEAR_FAILED, vm.uiState.value.result)
    }

    @Test
    fun `the working flag is raised while the clear is still in flight`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val vm = viewModel()

        vm.clearAllLocalData()
        advanceUntilIdle()

        // Blocked inside the repository call: the UI has to be able to show progress, so this
        // intermediate state is part of the contract, not an implementation detail.
        assertEquals(true, vm.uiState.value.isWorking)
        assertNull(vm.uiState.value.result)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isWorking)
        assertEquals(DataManagementResult.CLEAR_SUCCEEDED, vm.uiState.value.result)
    }

    @Test
    fun `starting a new clear drops the previous result instead of showing it as fresh`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.clearAllLocalData()
            advanceUntilIdle()
            assertEquals(DataManagementResult.CLEAR_SUCCEEDED, vm.uiState.value.result)

            val gate = CompletableDeferred<Unit>()
            repository.gate = gate
            vm.clearAllLocalData()
            advanceUntilIdle()

            // A stale CLEAR_SUCCEEDED left on screen during the second run would read as if the
            // second wipe had already finished.
            assertNull(vm.uiState.value.result)
            assertEquals(true, vm.uiState.value.isWorking)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(DataManagementResult.CLEAR_SUCCEEDED, vm.uiState.value.result)
        }

    @Test
    fun `two clears both reach the repository`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.clearAllLocalData()
        advanceUntilIdle()
        vm.clearAllLocalData()
        advanceUntilIdle()

        assertEquals(2, repository.clearCount)
    }

    // ---------------------------------------------------------------- export

    @Test
    fun `an export hands the chosen format's own bytes to the sink`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.exportTo(DataExportFormat.JSON, sink)
        advanceUntilIdle()

        assertEquals(1, written.size)
        // Self-identifying, so this fails if the JSON branch is ever wired to another writer.
        assertTrue(written.single().toString(Charsets.UTF_8).contains(LocalDataJsonExporter.FORMAT))
        assertEquals(false, vm.uiState.value.isWorking)
        assertEquals(DataManagementResult.EXPORT_SUCCEEDED, vm.uiState.value.result)
    }

    @Test
    fun `the spreadsheet export writes a zip archive rather than the JSON text`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.exportTo(DataExportFormat.XLSX, sink)
        advanceUntilIdle()

        assertEquals(DataManagementResult.EXPORT_SUCCEEDED, vm.uiState.value.result)
        val bytes = written.single()
        // "PK" — an .xlsx is a ZIP. Checking the container also catches the case where both
        // formats end up routed to the same writer, which a byte-count assertion would not.
        assertEquals("PK", bytes.toString(Charsets.ISO_8859_1).take(2))
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(LocalDataJsonExporter.FORMAT))
    }

    @Test
    fun `a destination that cannot be written is reported as a failed export`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.exportTo(DataExportFormat.JSON, ExportSink { error("no output stream") })
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isWorking)
        assertEquals(DataManagementResult.EXPORT_FAILED, vm.uiState.value.result)
    }

    @Test
    fun `a snapshot that cannot be read is reported as a failed export`() = runTest(dispatcher) {
        repository.failWith = IllegalStateException("database locked")
        val vm = viewModel()
        val received = mutableListOf<ByteArray>()

        vm.exportTo(DataExportFormat.XLSX, ExportSink { received += it })
        advanceUntilIdle()

        // Nothing may reach the destination: a truncated file that looks saved is worse than an
        // error message, because the user may then rely on it and wipe the device copy.
        assertEquals(0, received.size)
        assertEquals(DataManagementResult.EXPORT_FAILED, vm.uiState.value.result)
    }

    @Test
    fun `the working flag is raised while the export is still in flight`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val vm = viewModel()

        vm.exportTo(DataExportFormat.XLSX, sink)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.isWorking)
        assertNull(vm.uiState.value.result)
        assertEquals(0, written.size)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isWorking)
        assertEquals(DataManagementResult.EXPORT_SUCCEEDED, vm.uiState.value.result)
        assertEquals(1, written.size)
    }

    @Test
    fun `starting a new export drops the previous result instead of showing it as fresh`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.exportTo(DataExportFormat.JSON, sink)
            advanceUntilIdle()
            assertEquals(DataManagementResult.EXPORT_SUCCEEDED, vm.uiState.value.result)

            val gate = CompletableDeferred<Unit>()
            repository.gate = gate
            vm.exportTo(DataExportFormat.XLSX, sink)
            advanceUntilIdle()

            // A stale EXPORT_SUCCEEDED left on screen during the second run would read as if the
            // second file had already been written.
            assertNull(vm.uiState.value.result)
            assertEquals(true, vm.uiState.value.isWorking)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(DataManagementResult.EXPORT_SUCCEEDED, vm.uiState.value.result)
            assertEquals(2, written.size)
        }
}
