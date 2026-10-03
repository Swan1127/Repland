package com.swan1127.repland

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.AiSettingsRepository
import com.swan1127.repland.ui.agent.ArrangementAssistantViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArrangementAssistantAccessTest {
    private class Settings : AiSettingsRepository {
        val value = MutableStateFlow(AiPreferences())
        override fun observe() = value
        override suspend fun grantConsentAndEnable() { value.value = AiPreferences(true, 1) }
        override suspend fun setEnabled(enabled: Boolean) { value.value = value.value.copy(isEnabled = enabled) }
    }
    private fun request(count: Int = 0) = ArrangementAssistantAdviceRequest("英语", LocalDate.now(), emptyList(), (1..count).map { ArrangementExistingTask("$it", "英语", TaskCategory.COURSE, 30) })
    private val advice = ArrangementAssistantAdviceResult.Advice(ArrangementAssistantAdvice(listOf(ArrangementCandidate("英语", TaskCategory.COURSE, 30, ArrangementTimeHint(600), emptySet())), "test"))
    @Test fun disabled_or_unconsented_requests_never_reach_the_advisor() = runBlocking {
        val settings = Settings(); var calls = 0
        val vm = ArrangementAssistantViewModel(settings, object : ArrangementAssistantAdvisor {
            override suspend fun refine(request: ArrangementAssistantAdviceRequest): ArrangementAssistantAdviceResult { calls++; return advice }
        })
        vm.refine(request()); settings.value.value = AiPreferences(true, null); vm.refine(request())
        assertEquals(0, calls)
    }
    @Test fun oversized_context_never_reaches_the_advisor() = runBlocking {
        val settings = Settings(); settings.grantConsentAndEnable(); var calls = 0
        val vm = ArrangementAssistantViewModel(settings, object : ArrangementAssistantAdvisor {
            override suspend fun refine(request: ArrangementAssistantAdviceRequest): ArrangementAssistantAdviceResult { calls++; return advice }
        })
        assertEquals(ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE), vm.refine(request(51)))
        assertEquals(0, calls)
    }
    @Test fun disabling_during_response_drops_advice() = runBlocking {
        val settings = Settings(); settings.grantConsentAndEnable()
        val vm = ArrangementAssistantViewModel(settings, object : ArrangementAssistantAdvisor {
            override suspend fun refine(request: ArrangementAssistantAdviceRequest): ArrangementAssistantAdviceResult { settings.setEnabled(false); return advice }
        })
        assertEquals(ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.DISABLED), vm.refine(request()))
    }
}
