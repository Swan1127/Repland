package com.swan1127.repland

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.*
import com.swan1127.repland.ui.profile.NumericProfileViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class NumericProfileAiWorkflowTest {
    private class Config: AiProviderConfigRepository {
        val state=MutableStateFlow(AiProviderConfig(model="fixture",hasApiKey=true)); var secretReads=0
        override fun observe()=state
        override suspend fun save(baseUrl: String,model: String,apiKey: String) { state.value=state.value.copy(model=model,updatedAtEpochMillis=System.currentTimeMillis()) }
        override suspend fun clearApiKey() { state.value=state.value.copy(hasApiKey=false) }
        override suspend fun readSecret(): AiProviderSecret? { secretReads++; error("No secrets in fixture") }
    }
    private fun main(action: () -> Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun until(condition: () -> Boolean) {
        val end=System.currentTimeMillis()+15000
        while(!condition() && System.currentTimeMillis()<end) Thread.sleep(20)
        assertTrue("Timed out awaiting profile state",condition())
    }
    @Test fun explicit_generation_minimal_payload_cache_fallback_cancel_disable_configuration_and_changed_evidence()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),ReplandDatabase::class.java).build()
        val store=ViewModelStore()
        try {
            val tasks=RoomTaskRepository(db); val config=Config(); val profile=RoomNumericProfileRepository(db,config); val ai=RoomAiSettingsRepository(db.aiSettingsDao())
            tasks.save(TaskDraft(id="private-task",displayName="private prose",category=TaskCategory.COURSE,totalDurationMinutes=20))
            tasks.confirmStatus("private-task",TaskStatus.IN_PROGRESS); tasks.confirmStatus("private-task",TaskStatus.COMPLETED)
            profile.confirmActualTotal("private-task",30,RoomNumericProfileRepository.taskRevision(tasks.observeTasks().first().single(),tasks.observeExecutionLogs("private-task").first()))
            var calls=0; var gate: CompletableDeferred<Unit>?=null; var malformed=false
            val advisor=NumericProfileAdvisor { request ->
                calls++; assertTrue(request.parameters.all { it.sources.isEmpty() })
                gate?.await()
                NumericNarration(request.requestId,request.profileVersion,request.parameters.map { NumericClaim(it.id,if(malformed) 999.0 else it.value!!) },NumericNextStep.NONE)
            }
            lateinit var vm: NumericProfileViewModel
            main { vm=NumericProfileViewModel(profile,tasks,ai,config,advisor,true); store.put("numeric",vm) }
            until { vm.uiState.value.trusted }
            assertEquals(0,calls)
            main { vm.generateDescription() }; until { !vm.uiState.value.generating }
            assertEquals(0,calls); assertNull(profile.observeDescription().first())
            profile.setAiConsent(true); ai.grantConsentAndEnable()
            main { vm.generateDescription() }; until { !vm.uiState.value.generating }
            val good=profile.observeDescription().first()!!; assertTrue(good.origin.startsWith("AI")); assertEquals(1,calls)
            main { vm.retry() }; until { vm.uiState.value.trusted }; assertEquals(1,calls)
            malformed=true; main { vm.generateDescription() }; until { !vm.uiState.value.generating }
            val fallback=profile.observeDescription().first()!!; assertEquals("LOCAL",fallback.origin); malformed=false
            suspend fun pending(change: suspend () -> Unit, cancel: Boolean=false) {
                gate=CompletableDeferred(); val count=calls
                main { vm.generateDescription() }; until { calls>count }
                change(); if(cancel) main { vm.cancelDescription() }
                gate!!.complete(Unit); until { !vm.uiState.value.generating }
                assertEquals(fallback,profile.observeDescription().first()); gate=null
            }
            pending({},cancel=true)
            pending({ ai.setEnabled(false) }); ai.setEnabled(true)
            pending({ config.save("https://example.invalid","changed","") })
            pending({ profile.setParameterEnabled("estimate:COURSE",false) })
            assertEquals(0,config.secretReads)
            assertEquals(TaskStatus.COMPLETED,tasks.observeTasks().first().single().status)
        } finally { main { store.clear() }; db.close() }
    }
}
