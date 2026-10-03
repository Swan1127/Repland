package com.swan1127.repland.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.AiProviderConfig
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceRequest
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceResult
import com.swan1127.repland.domain.model.ArrangementAssistantAdvisor
import com.swan1127.repland.domain.model.NoOpArrangementAssistantAdvisor
import com.swan1127.repland.domain.ports.AiProviderConfigRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.drop

data class AiProviderConfigUiState(
    val config: AiProviderConfig = AiProviderConfig(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val connectionTest: AiProviderConnectionTest = AiProviderConnectionTest.Idle,
    val supportsRemote: Boolean = false,
)

sealed interface AiProviderConnectionTest {
    data object Idle : AiProviderConnectionTest
    data object Testing : AiProviderConnectionTest
    data class Connected(val model: String) : AiProviderConnectionTest
    data class Failed(val reason: AiAdvisorFailureReason) : AiProviderConnectionTest
}

class AiProviderConfigViewModel(
    private val repository: AiProviderConfigRepository,
    private val arrangementAdvisor: ArrangementAssistantAdvisor,
) : ViewModel() {
    private val errorMessage = MutableStateFlow<String?>(null)
    private val connectionTest = MutableStateFlow<AiProviderConnectionTest>(AiProviderConnectionTest.Idle)
    private var testJob: Job? = null
    private fun cancelTest() { testJob?.cancel(); testJob = null; connectionTest.value = AiProviderConnectionTest.Idle }
    init { viewModelScope.launch { repository.observe().drop(1).collect { cancelTest() } } }
    val uiState: StateFlow<AiProviderConfigUiState> = combine(repository.observe(), errorMessage, connectionTest) { config, error, test ->
        AiProviderConfigUiState(config = config, isLoading = false, errorMessage = error, connectionTest = test,
            supportsRemote = arrangementAdvisor !== NoOpArrangementAssistantAdvisor)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiProviderConfigUiState(),
    )

    fun save(baseUrl: String, model: String, apiKey: String) {
        cancelTest()
        viewModelScope.launch {
            runCatching { repository.save(baseUrl, model, apiKey) }
                .onSuccess { errorMessage.value = null }
                .onFailure { errorMessage.value = it.message ?: "配置无法保存，请检查服务地址和模型。" }
        }
    }

    fun clearApiKey() {
        cancelTest()
        viewModelScope.launch {
            runCatching { repository.clearApiKey() }
                .onSuccess { errorMessage.value = null }
                .onFailure { errorMessage.value = "密钥无法清除，请稍后重试。" }
        }
    }

    /** Sends a fixed, non-personal request through the configured debug adapter. */
    fun testConnection() {
        if (!uiState.value.config.hasApiKey) {
            connectionTest.value = AiProviderConnectionTest.Failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
            return
        }
        if (testJob?.isActive == true) return
        val config = uiState.value.config
        testJob = viewModelScope.launch {
            connectionTest.value = AiProviderConnectionTest.Testing
            val next = try { when (
                val result = arrangementAdvisor.refine(
                    ArrangementAssistantAdviceRequest(
                        utterance = "连接测试：请生成一项 10 分钟的连接测试事项。",
                        date = LocalDate.now(),
                        occupiedIntervals = emptyList(),
                    ),
                )
            ) {
                is ArrangementAssistantAdviceResult.Advice -> AiProviderConnectionTest.Connected(config.model)
                is ArrangementAssistantAdviceResult.Unavailable -> AiProviderConnectionTest.Failed(result.reason)
                is ArrangementAssistantAdviceResult.Failed -> AiProviderConnectionTest.Failed(result.reason)
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { AiProviderConnectionTest.Failed(AiAdvisorFailureReason.TRANSPORT_FAILURE) }
            if (uiState.value.config == config) connectionTest.value = next
        }
    }

    class Factory(
        private val repository: AiProviderConfigRepository,
        private val arrangementAdvisor: ArrangementAssistantAdvisor,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(AiProviderConfigViewModel::class.java))
            return AiProviderConfigViewModel(repository, arrangementAdvisor) as T
        }
    }
}
