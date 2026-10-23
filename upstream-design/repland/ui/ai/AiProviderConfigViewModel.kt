package com.swan1127.repland.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.AiProviderConfig
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceRequest
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceResult
import com.swan1127.repland.domain.model.ArrangementAssistantAdvisor
import com.swan1127.repland.domain.ports.AiModelCatalog
import com.swan1127.repland.domain.ports.AiModelCatalogResult
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

data class AiProviderConfigUiState(
    val config: AiProviderConfig = AiProviderConfig(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val connectionTest: AiProviderConnectionTest = AiProviderConnectionTest.Idle,
    val modelList: AiModelListState = AiModelListState.Idle,
)

sealed interface AiProviderConnectionTest {
    data object Idle : AiProviderConnectionTest
    data object Testing : AiProviderConnectionTest
    data class Connected(val model: String) : AiProviderConnectionTest
    data class Failed(val reason: AiAdvisorFailureReason) : AiProviderConnectionTest
}

/** The provider's own model list. Idle means "not asked", which keeps the field free-form. */
sealed interface AiModelListState {
    data object Idle : AiModelListState
    data object Loading : AiModelListState
    data class Loaded(val ids: List<String>) : AiModelListState
    data class Failed(val reason: AiAdvisorFailureReason) : AiModelListState
}

class AiProviderConfigViewModel(
    private val repository: AiProviderConfigRepository,
    private val arrangementAdvisor: ArrangementAssistantAdvisor,
    private val modelCatalog: AiModelCatalog,
) : ViewModel() {
    private val errorMessage = MutableStateFlow<String?>(null)
    private val connectionTest = MutableStateFlow<AiProviderConnectionTest>(AiProviderConnectionTest.Idle)
    private val modelList = MutableStateFlow<AiModelListState>(AiModelListState.Idle)
    private var resetGeneration = 0L
    private var saveJob: Job? = null
    private var connectionJob: Job? = null
    private var modelsJob: Job? = null
    val uiState: StateFlow<AiProviderConfigUiState> = combine(
        repository.observe(),
        errorMessage,
        connectionTest,
        modelList,
    ) { config, error, test, models ->
        AiProviderConfigUiState(
            config = config,
            isLoading = false,
            errorMessage = error,
            connectionTest = test,
            modelList = models,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiProviderConfigUiState(),
    )

    fun save(baseUrl: String, model: String, apiKey: String) {
        resetTransientState()
        val generation = resetGeneration
        saveJob = viewModelScope.launch {
            runCatching { repository.save(baseUrl, model, apiKey) }
                .onSuccess {
                    if (generation != resetGeneration) return@onSuccess
                    errorMessage.value = null
                    // The list belongs to the address that was saved; a stale list from the
                    // previous provider would offer models this one does not serve.
                    modelList.value = AiModelListState.Idle
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (generation == resetGeneration) errorMessage.value = it.message ?: "配置无法保存，请检查服务地址和模型。"
                }
        }
    }

    fun clearApiKey() {
        resetTransientState()
        val generation = resetGeneration
        saveJob = viewModelScope.launch {
            runCatching { repository.clearApiKey() }
                .onSuccess {
                    if (generation != resetGeneration) return@onSuccess
                    errorMessage.value = null
                    modelList.value = AiModelListState.Idle
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (generation == resetGeneration) errorMessage.value = "密钥无法清除，请稍后重试。"
                }
        }
    }

    /** Cancels work and drops metadata that belonged to the account before a complete reset. */
    fun resetTransientState() {
        resetGeneration += 1
        saveJob?.cancel()
        connectionJob?.cancel()
        modelsJob?.cancel()
        saveJob = null
        connectionJob = null
        modelsJob = null
        errorMessage.value = null
        connectionTest.value = AiProviderConnectionTest.Idle
        modelList.value = AiModelListState.Idle
    }

    /**
     * Asks the saved provider which models it serves. Needs a stored key for the same reason
     * the connection test does: the request is authenticated, and the UI never holds the
     * decrypted value.
     */
    fun refreshModels() {
        if (!uiState.value.config.hasApiKey) {
            modelList.value = AiModelListState.Failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
            return
        }
        modelsJob?.cancel()
        val generation = resetGeneration
        val config = uiState.value.config
        modelsJob = viewModelScope.launch {
            modelList.value = AiModelListState.Loading
            val models = when (val result = modelCatalog.listModels()) {
                is AiModelCatalogResult.Models -> AiModelListState.Loaded(result.ids)
                is AiModelCatalogResult.Failed -> AiModelListState.Failed(result.reason)
            }
            if (generation == resetGeneration && config == uiState.value.config) modelList.value = models
        }
    }

    /** Sends a fixed, non-personal request through the configured debug adapter. */
    fun testConnection() {
        if (!uiState.value.config.hasApiKey) {
            connectionTest.value = AiProviderConnectionTest.Failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
            return
        }
        connectionJob?.cancel()
        val generation = resetGeneration
        val config = uiState.value.config
        connectionJob = viewModelScope.launch {
            connectionTest.value = AiProviderConnectionTest.Testing
            val test = when (
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
            }
            if (generation == resetGeneration && config == uiState.value.config) connectionTest.value = test
        }
    }

    class Factory(
        private val repository: AiProviderConfigRepository,
        private val arrangementAdvisor: ArrangementAssistantAdvisor,
        private val modelCatalog: AiModelCatalog,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(AiProviderConfigViewModel::class.java))
            return AiProviderConfigViewModel(repository, arrangementAdvisor, modelCatalog) as T
        }
    }
}
