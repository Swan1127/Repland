package com.swan1127.repland.ui.data

import com.swan1127.repland.data.export.LocalDataJsonExporter
import com.swan1127.repland.domain.model.LearningMemoryPointSnapshot
import com.swan1127.repland.domain.model.LearningMemorySnapshot
import com.swan1127.repland.domain.ports.DataExportFormat
import com.swan1127.repland.domain.ports.ExportSink
import com.swan1127.repland.testkit.FakeDataManagementRepository
import com.swan1127.repland.testkit.MiniJson
import com.swan1127.repland.testkit.ViewModelTestBase
import java.time.LocalDate
import java.util.zip.ZipInputStream
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
    fun `UI history clears only after the database reports success`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val model = viewModel()
        var clearCount = 0

        model.clearAllLocalData { clearCount++ }
        advanceUntilIdle()
        assertEquals(0, clearCount)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, clearCount)
        assertEquals(1, repository.clearCount)
        assertEquals(DataManagementResult.CLEAR_SUCCEEDED, model.uiState.value.result)
    }

    @Test
    fun `failed database clear preserves UI drafts and history`() = runTest(dispatcher) {
        repository.failWith = IllegalStateException("database locked")
        val model = viewModel()
        var clearCount = 0

        model.clearAllLocalData { clearCount++ }
        advanceUntilIdle()

        assertEquals(0, clearCount)
        assertEquals(DataManagementResult.CLEAR_FAILED, model.uiState.value.result)
    }

    @Test
    fun `UI history failure cannot be reported as a complete clear`() = runTest(dispatcher) {
        val model = viewModel()

        model.clearAllLocalData { error("history storage unavailable") }
        advanceUntilIdle()

        assertEquals(1, repository.clearCount)
        assertEquals(DataManagementResult.CLEAR_FAILED, model.uiState.value.result)
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
    fun `JSON export receives the actual auxiliary learning snapshot`() = runTest(dispatcher) {
        val model = viewModel()
        val memory = LearningMemorySnapshot(referenceEnabled = false, gain = 0.85, points = emptyList())

        model.exportTo(DataExportFormat.JSON, sink, memorySnapshot = memory)
        advanceUntilIdle()

        val parsed = MiniJson.parseObject(written.single().toString(Charsets.UTF_8))
        val section = parsed["learningMemory"] as Map<*, *>
        assertEquals(false, section["referenceEnabled"])
        assertEquals(0.85, section["gain"])
        assertEquals(emptyList<Any>(), section["points"])
        assertEquals(DataManagementResult.EXPORT_SUCCEEDED, model.uiState.value.result)
    }

    @Test
    fun `invalid memory export does not write a partial file or claim success`() = runTest(dispatcher) {
        val model = viewModel()
        val memory = LearningMemorySnapshot(referenceEnabled = false, gain = Double.NaN, points = emptyList())

        model.exportTo(DataExportFormat.JSON, sink, memorySnapshot = memory)
        advanceUntilIdle()

        assertTrue(written.isEmpty())
        assertFalse(model.uiState.value.isWorking)
        assertEquals(DataManagementResult.EXPORT_FAILED, model.uiState.value.result)
    }

    @Test
    fun `spreadsheet remains a plan export and does not leak learning notes`() = runTest(dispatcher) {
        val model = viewModel()
        val memory = LearningMemorySnapshot(
            referenceEnabled = true,
            gain = 1.0,
            points = listOf(
                LearningMemoryPointSnapshot("point", "task", "private-title-7819", "private-memory-8247", LocalDate.of(2026, 10, 22), 1),
            ),
        )

        model.exportTo(DataExportFormat.XLSX, sink, memorySnapshot = memory)
        advanceUntilIdle()

        val archiveText = ZipInputStream(written.single().inputStream()).use { archive ->
            buildString {
                while (archive.nextEntry != null) append(archive.readBytes().toString(Charsets.UTF_8))
            }
        }
        assertFalse(archiveText.contains("private-title-7819"))
        assertFalse(archiveText.contains("private-memory-8247"))
        assertFalse(archiveText.contains("learningMemory"))
        assertEquals(DataManagementResult.EXPORT_SUCCEEDED, model.uiState.value.result)
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
