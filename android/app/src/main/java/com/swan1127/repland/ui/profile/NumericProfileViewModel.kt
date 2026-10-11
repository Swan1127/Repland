package com.swan1127.repland.ui.profile

import androidx.lifecycle.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.*
import com.swan1127.repland.data.room.RoomNumericProfileRepository
import com.swan1127.repland.data.room.numericHash
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class NumericTaskTarget(val task: Task, val revision: String)
data class NumericProfileUiState(val snapshot: NumericProfileSnapshot?=null, val tasks: List<Task> = emptyList(),
    val description: NumericDescription?=null, val error: String?=null, val readError: String?=null,
    val busy: Boolean=false, val generating: Boolean=false, val target: NumericTaskTarget?=null,
    val advice: NumericDurationAdvice?=null, val receipt: String?=null, val trusted: Boolean=false, val descriptionExpired: Boolean=false)

class NumericProfileViewModel(private val repository: NumericProfileRepository, private val tasks: TaskRepository,
    private val aiSettings: AiSettingsRepository, private val config: AiProviderConfigRepository,
    private val advisor: NumericProfileAdvisor, val remoteSupported: Boolean) : ViewModel() {
    private val state=MutableStateFlow(NumericProfileUiState())
    val uiState=state.asStateFlow()
    private var readJob: Job?=null; private var requestJob: Job?=null; private var generation=0L
    init { retry() }
    fun retry() {
        readJob?.cancel()
        state.update { it.copy(trusted=false,readError=null) }
        readJob=viewModelScope.launch {
            try {
                combine(repository.observe(),tasks.observeTasks(),repository.observeDescription(),aiSettings.observe(),config.observe()) { snapshot, tasks, description, access, provider ->
                    NumericProfileUiState(snapshot=snapshot,tasks=tasks,description=description,descriptionExpired=description!=null &&
                        (description.profileVersion!=snapshot.version || (description.origin!="LOCAL" &&
                            (!access.isEnabled || !access.hasExplicitConsent || description.accessRevision!=numericHash(access.toString()) || description.configurationRevision!=numericHash(provider.toString())))))
                }.collect { read -> state.update { it.copy(snapshot=read.snapshot,tasks=read.tasks,description=read.description,descriptionExpired=read.descriptionExpired,readError=null,trusted=true) } }
            } catch(c: CancellationException) { throw c }
            catch(_: Exception) { state.update { it.copy(trusted=false,readError="画像读取失败；最后成功的内容保留，暂不能确认。请重试。") } }
        }
    }
    private fun action(saved: Boolean=true, block: suspend () -> Unit) {
        if(state.value.busy || !state.value.trusted) return
        state.update { it.copy(busy=true,error=null,receipt=null) }
        viewModelScope.launch { try { block(); if(saved) state.update { it.copy(receipt="已保存；当前计划保持不变。") } }
            catch(c: CancellationException) { throw c }
            catch(e: Exception) { state.update { it.copy(error=if(e is IllegalArgumentException) e.message else "本次未保存，请重试；原记录保留。") } }
            finally { state.update { it.copy(busy=false) } } }
    }
    fun setEnabled(enabled: Boolean) { cancelDescription(); action { repository.setEnabled(enabled) } }
    fun setAiConsent(consent: Boolean) { cancelDescription(); action { repository.setAiConsent(consent) } }
    fun parameter(id: String, enabled: Boolean)=action { repository.setParameterEnabled(id,enabled) }
    fun exclude(id: String, excluded: Boolean)=action { repository.setExcluded(id,excluded) }
    fun openTask(id: String)=action(saved=false) {
        val task=tasks.observeTasks().first().single { it.id==id }
        val logs=tasks.observeExecutionLogs(id).first()
        state.update { it.copy(target=NumericTaskTarget(task,RoomNumericProfileRepository.taskRevision(task,logs))) }
    }
    fun closeTask() { if(!state.value.busy) state.update { it.copy(target=null,error=null) } }
    fun confirmInput() { val target=state.value.target ?: return; action { repository.confirmCurrentInput(target.task.id,target.revision); state.update { it.copy(target=null) } } }
    fun confirmActual(text: String) { val target=state.value.target ?: return
        action { val minutes=text.toIntOrNull(); require(minutes!=null && minutes>0) { "请输入完整的正整数分钟；本次未保存。" }
            repository.confirmActualTotal(target.task.id,minutes,target.revision); state.update { it.copy(target=null) } } }
    fun clearActual(id: String)=action { repository.clearActualTotal(id) }
    fun inspectAdvice(id: String)=action(saved=false) { val advice=repository.durationAdvice(id)
        requireNotNull(advice) { "该类别样本不足、输入来源未知或参数已停用，暂不提供估时建议。" }
        state.update { it.copy(advice=advice) } }
    fun cancelAdvice() { if(!state.value.busy) state.update { it.copy(advice=null,error=null) } }
    fun acceptAdvice() { val advice=state.value.advice ?: return
        action { repository.acceptDurationAdvice(advice); state.update { it.copy(advice=null) } } }
    fun cancelDescription() { generation++; requestJob?.cancel(); requestJob=null; state.update { it.copy(generating=false) } }
    fun generateDescription() {
        if(state.value.generating || !state.value.trusted) return
        val epoch=++generation
        state.update { it.copy(generating=true,error=null,receipt=null) }
        requestJob=viewModelScope.launch {
            try {
                val snapshot=repository.refresh()
                require(snapshot.enabled && snapshot.aiConsent) { "请先启用画像建议并确认个性化数据发送授权。" }
                val access=aiSettings.observe().first(); val provider=config.observe().first()
                require(access.isEnabled && access.hasExplicitConsent) { "AI 总开关或数据发送授权未开启；本地数值仍可查看。" }
                val parameters=snapshot.parameters.filter { it.value!=null && it.availability!=NumericAvailability.DISABLED }.map { it.copy(sources=emptyList()) }
                require(parameters.isNotEmpty()) { "尚无有效数值，保持未知，不请求 AI。" }
                val request=NumericNarrationRequest(UUID.randomUUID().toString(),snapshot.version,snapshot.rule,snapshot.windowStart,snapshot.windowEnd,parameters)
                val response=if(remoteSupported && provider.hasApiKey) advisor.describe(request) else null
                ensureActive()
                require(epoch==generation && aiSettings.observe().first()==access && config.observe().first()==provider && repository.refresh().version==snapshot.version) {
                    "画像、AI 开关或配置已变化，旧回复已丢弃，请重新生成。" }
                val valid=response?.takeIf { NumericNarrationValidator.valid(request,it) }
                repository.saveDescription(NumericDescription(snapshot.version,System.currentTimeMillis(),
                    if(valid!=null) "AI · ${provider.model}" else "LOCAL",
                    valid?.claims ?: parameters.map { NumericClaim(it.id,it.value!!) },valid?.nextStep ?: NumericNextStep.NONE,
                    numericHash(provider.toString()),numericHash(access.toString())))
                state.update { it.copy(receipt=if(valid!=null) "已保存此快照的 AI 现状描述。" else "AI 暂不可用，已保存明确标注的本地事实摘要。") }
            } catch(c: CancellationException) { throw c }
            catch(e: Exception) { state.update { it.copy(error=if(e is IllegalArgumentException) e.message else "描述生成失败；本地数值与任务保持不变，请重试。") } }
            finally { if(epoch==generation) state.update { it.copy(generating=false) } }
        }
    }
    class Factory(private val repository: NumericProfileRepository,private val tasks: TaskRepository,private val ai: AiSettingsRepository,
        private val config: AiProviderConfigRepository,private val advisor: NumericProfileAdvisor,private val remoteSupported: Boolean) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass: Class<T>): T=NumericProfileViewModel(repository,tasks,ai,config,advisor,remoteSupported) as T
    }
}
