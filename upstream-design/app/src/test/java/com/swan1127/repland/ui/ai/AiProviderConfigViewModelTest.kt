package com.swan1127.repland.ui.ai

import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiProviderConfig
import com.swan1127.repland.domain.model.ArrangementAssistantAdvice
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceResult
import com.swan1127.repland.domain.ports.AiModelCatalogResult
import com.swan1127.repland.testkit.FakeAiModelCatalog
import com.swan1127.repland.testkit.FakeAiProviderConfigRepository
import com.swan1127.repland.testkit.FakeArrangementAssistantAdvisor
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.collectInBackground
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiProviderConfigViewModelTest : ViewModelTestBase() {

    private val repository = FakeAiProviderConfigRepository()
    private val advisor = FakeArrangementAssistantAdvisor()
    private val catalog = FakeAiModelCatalog()

    private fun viewModel() = AiProviderConfigViewModel(repository, advisor, catalog)

    private fun configured(hasApiKey: Boolean = true, model: String = "agnes-pro") =
        AiProviderConfig(model = model, hasApiKey = hasApiKey)

    @Test
    fun `reset drops model and connection results and cancels old requests`() = runTest(dispatcher) {
        repository.state.value = configured()
        val vm = viewModel()
        val collector = collectInBackground(vm.uiState)
        advanceUntilIdle()
        val connectionGate = CompletableDeferred<Unit>()
        val modelGate = CompletableDeferred<Unit>()
        advisor.gate = connectionGate
        catalog.gate = modelGate
        advisor.result = ArrangementAssistantAdviceResult.Advice(
            ArrangementAssistantAdvice(candidates = emptyList(), confidenceLabel = "high"),
        )
        catalog.result = AiModelCatalogResult.Models(listOf("old-model"))
        vm.testConnection()
        vm.refreshModels()
        advanceUntilIdle()
        assertEquals(AiProviderConnectionTest.Testing, vm.uiState.value.connectionTest)
        assertEquals(AiModelListState.Loading, vm.uiState.value.modelList)

        vm.resetTransientState()
        repository.clearAllLocalData()
        connectionGate.complete(Unit)
        modelGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(AiProviderConnectionTest.Idle, vm.uiState.value.connectionTest)
        assertEquals(AiModelListState.Idle, vm.uiState.value.modelList)
        assertEquals(AiProviderConfig(), vm.uiState.value.config)
        collector.cancel()
    }

    // --- observation ----------------------------------------------------------------------------

    @Test
    fun `before collection the config is the built-in default and the screen is loading`() =
        runTest(dispatcher) {
            repository.state.value = configured()

            val vm = viewModel()

            assertEquals(AiProviderConfig(), vm.uiState.value.config)
            assertTrue(vm.uiState.value.isLoading)
            assertNull(vm.uiState.value.errorMessage)
            assertEquals(AiProviderConnectionTest.Idle, vm.uiState.value.connectionTest)
        }

    @Test
    fun `collecting reports the stored config and stops loading`() = runTest(dispatcher) {
        repository.state.value = configured(model = "agnes-pro")
        val vm = viewModel()

        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        assertEquals("agnes-pro", vm.uiState.value.config.model)
        assertEquals(false, vm.uiState.value.isLoading)
        job.cancel()
    }

    // --- saving ---------------------------------------------------------------------------------

    @Test
    fun `save forwards all three arguments`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.save("https://api.example.cn/v1", "agnes-pro", "sk-secret")
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("https://api.example.cn/v1", "agnes-pro", "sk-secret")),
            repository.saves,
        )
    }

    @Test
    fun `an empty api key is forwarded as-is, which is how a stored key stays masked`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.save("https://api.example.cn/v1", "agnes-pro", "")
            advanceUntilIdle()

            assertEquals("", repository.saves.single().third)
        }

    @Test
    fun `a successful save clears a previous error message`() = runTest(dispatcher) {
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        repository.failWith = IllegalStateException("boom")
        vm.save("u", "m", "k")
        advanceUntilIdle()
        assertEquals("boom", vm.uiState.value.errorMessage)

        repository.failWith = null
        vm.save("u", "m", "k")
        advanceUntilIdle()

        assertNull(vm.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun `a failed save surfaces the exception message`() = runTest(dispatcher) {
        repository.failWith = IllegalStateException("服务地址无效")
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.save("u", "m", "k")
        advanceUntilIdle()

        assertEquals("服务地址无效", vm.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun `a failed save with no message falls back to its own wording`() = runTest(dispatcher) {
        repository.failWith = IllegalStateException()
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.save("u", "m", "k")
        advanceUntilIdle()

        assertEquals("配置无法保存，请检查服务地址和模型。", vm.uiState.value.errorMessage)
        job.cancel()
    }

    // --- clearing the key -----------------------------------------------------------------------

    @Test
    fun `clearApiKey is forwarded`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.clearApiKey()
        advanceUntilIdle()

        assertEquals(1, repository.clearApiKeyCount)
    }

    @Test
    fun `a successful clearApiKey clears a previous error message`() = runTest(dispatcher) {
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        repository.failWith = IllegalStateException("boom")
        vm.clearApiKey()
        advanceUntilIdle()
        assertEquals("密钥无法清除，请稍后重试。", vm.uiState.value.errorMessage)

        repository.failWith = null
        vm.clearApiKey()
        advanceUntilIdle()

        assertNull(vm.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun `only save surfaces the exception message, clearApiKey always uses its own wording`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val job = collectInBackground(vm.uiState)
            advanceUntilIdle()
            repository.failWith = IllegalStateException("服务地址无效")

            vm.save("u", "m", "k")
            advanceUntilIdle()
            val fromSave = vm.uiState.value.errorMessage

            vm.clearApiKey()
            advanceUntilIdle()
            val fromClear = vm.uiState.value.errorMessage

            // Pinned asymmetry. `save` does `it.message ?: fallback`, so the provider's own
            // wording reaches the screen; `clearApiKey` ignores `it.message` completely and always
            // shows a fixed sentence. Both are deliberate enough to be worth recording, and a
            // future change to either one should have to update this test.
            assertEquals("服务地址无效", fromSave)
            assertEquals("密钥无法清除，请稍后重试。", fromClear)
            job.cancel()
        }

    @Test
    fun `a failed clearApiKey reports its own wording`() = runTest(dispatcher) {
        repository.failWith = IllegalStateException("keystore unavailable")
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.clearApiKey()
        advanceUntilIdle()

        // Deliberately not the save wording: the two failures need different fixes.
        assertEquals("密钥无法清除，请稍后重试。", vm.uiState.value.errorMessage)
        job.cancel()
    }

    // --- the connection test --------------------------------------------------------------------

    @Test
    fun `testConnection refuses with no api key and never consults the advisor`() =
        runTest(dispatcher) {
            repository.state.value = configured(hasApiKey = false)
            val vm = viewModel()
            val job = collectInBackground(vm.uiState)
            advanceUntilIdle()

            vm.testConnection()
            advanceUntilIdle()

            assertEquals(emptyList<Any>(), advisor.requests)
            assertEquals(
                AiProviderConnectionTest.Failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED),
                vm.uiState.value.connectionTest,
            )
            job.cancel()
        }

    @Test
    fun `testConnection refuses before the config has been collected even if a key is stored`() =
        runTest(dispatcher) {
            // The guard reads `uiState.value`, which is a WhileSubscribed stateIn. With nobody
            // collecting it stays at the declared initial value, whose config has no key — so the
            // advisor is never consulted. Observable here through the advisor's call list, which
            // does not depend on the state flow being live.
            repository.state.value = configured(hasApiKey = true)
            val vm = viewModel()

            vm.testConnection()
            advanceUntilIdle()

            assertEquals(emptyList<Any>(), advisor.requests)
        }

    @Test
    fun `testConnection shows Testing while the advisor is in flight`() = runTest(dispatcher) {
        repository.state.value = configured(model = "agnes-pro")
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        advisor.gate = gate
        advisor.result = ArrangementAssistantAdviceResult.Advice(
            ArrangementAssistantAdvice(candidates = emptyList(), confidenceLabel = "高"),
        )

        vm.testConnection()
        advanceUntilIdle()
        assertEquals(AiProviderConnectionTest.Testing, vm.uiState.value.connectionTest)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(
            AiProviderConnectionTest.Connected("agnes-pro"),
            vm.uiState.value.connectionTest,
        )
        job.cancel()
    }

    @Test
    fun `the connection test sends a fixed non-personal request`() = runTest(dispatcher) {
        repository.state.value = configured()
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        advisor.result = ArrangementAssistantAdviceResult.Advice(
            ArrangementAssistantAdvice(candidates = emptyList(), confidenceLabel = "高"),
        )

        vm.testConnection()
        advanceUntilIdle()

        val request = advisor.requests.single()
        assertEquals("连接测试：请生成一项 10 分钟的连接测试事项。", request.utterance)
        assertEquals(emptyList<Any>(), request.occupiedIntervals)
        job.cancel()
    }

    @Test
    fun `an unavailable result is reported with its reason`() = runTest(dispatcher) {
        repository.state.value = configured()
        advisor.result =
            ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.testConnection()
        advanceUntilIdle()

        assertEquals(
            AiProviderConnectionTest.Failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED),
            vm.uiState.value.connectionTest,
        )
        job.cancel()
    }

    @Test
    fun `a failed result is reported with its reason`() = runTest(dispatcher) {
        repository.state.value = configured()
        advisor.result = ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.TIMEOUT)
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.testConnection()
        advanceUntilIdle()

        assertEquals(
            AiProviderConnectionTest.Failed(AiAdvisorFailureReason.TIMEOUT),
            vm.uiState.value.connectionTest,
        )
        job.cancel()
    }

    // --- the model list -------------------------------------------------------------------------

    @Test
    fun `refreshModels refuses with no api key and never asks the provider`() = runTest(dispatcher) {
        repository.state.value = configured(hasApiKey = false)
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.refreshModels()
        advanceUntilIdle()

        assertEquals(0, catalog.callCount)
        assertEquals(
            AiModelListState.Failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED),
            vm.uiState.value.modelList,
        )
        job.cancel()
    }

    @Test
    fun `a loaded model list is reported in the provider's order`() = runTest(dispatcher) {
        repository.state.value = configured()
        catalog.result = AiModelCatalogResult.Models(listOf("deepseek-flash", "deepseek-v4-pro"))
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.refreshModels()
        advanceUntilIdle()

        assertEquals(
            AiModelListState.Loaded(listOf("deepseek-flash", "deepseek-v4-pro")),
            vm.uiState.value.modelList,
        )
        job.cancel()
    }

    @Test
    fun `the model list shows Loading while the request is in flight`() = runTest(dispatcher) {
        repository.state.value = configured()
        catalog.result = AiModelCatalogResult.Models(listOf("m"))
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        catalog.gate = gate

        vm.refreshModels()
        advanceUntilIdle()
        assertEquals(AiModelListState.Loading, vm.uiState.value.modelList)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(AiModelListState.Loaded(listOf("m")), vm.uiState.value.modelList)
        job.cancel()
    }

    @Test
    fun `a failed model list carries the reason rather than an empty list`() = runTest(dispatcher) {
        // An empty `Loaded` would render as "the provider serves nothing", which is a
        // different and much more alarming message than "the request failed".
        repository.state.value = configured()
        catalog.result = AiModelCatalogResult.Failed(AiAdvisorFailureReason.TIMEOUT)
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()

        vm.refreshModels()
        advanceUntilIdle()

        assertEquals(
            AiModelListState.Failed(AiAdvisorFailureReason.TIMEOUT),
            vm.uiState.value.modelList,
        )
        job.cancel()
    }

    @Test
    fun `saving a configuration drops a list that belonged to the previous provider`() =
        runTest(dispatcher) {
            repository.state.value = configured()
            catalog.result = AiModelCatalogResult.Models(listOf("deepseek-flash"))
            val vm = viewModel()
            val job = collectInBackground(vm.uiState)
            advanceUntilIdle()
            vm.refreshModels()
            advanceUntilIdle()
            assertEquals(AiModelListState.Loaded(listOf("deepseek-flash")), vm.uiState.value.modelList)

            vm.save("https://open.bigmodel.cn/api/paas/v4", "glm-5.3", "sk-new")
            advanceUntilIdle()

            assertEquals(AiModelListState.Idle, vm.uiState.value.modelList)
            job.cancel()
        }

    @Test
    fun `clearing the key drops the model list too`() = runTest(dispatcher) {
        repository.state.value = configured()
        catalog.result = AiModelCatalogResult.Models(listOf("m"))
        val vm = viewModel()
        val job = collectInBackground(vm.uiState)
        advanceUntilIdle()
        vm.refreshModels()
        advanceUntilIdle()

        vm.clearApiKey()
        advanceUntilIdle()

        assertEquals(AiModelListState.Idle, vm.uiState.value.modelList)
        job.cancel()
    }

    @Test
    fun `a throwing observation stream escapes the scope`() {
        // FINDING, pinned. This state flow has no `catch`, so a read failure leaves
        // viewModelScope as an uncaught exception rather than becoming an error message.
        val thrown = assertThrows(IllegalStateException::class.java) {
            runTest(dispatcher) {
                repository.observeFlow = flow { throw IllegalStateException("read failed") }
                val vm = viewModel()
                val job = collectInBackground(vm.uiState)
                advanceUntilIdle()
                job.cancel()
            }
        }

        assertEquals("read failed", thrown.message)
    }
}
