package com.swan1127.repland.debug

import com.swan1127.repland.data.ai.AiHttpOutcome
import com.swan1127.repland.data.ai.AiHttpTransport
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiAdvisorResult
import com.swan1127.repland.domain.model.AiProviderConfig
import com.swan1127.repland.domain.model.AiProviderSecret
import com.swan1127.repland.domain.model.AiTaskContext
import com.swan1127.repland.domain.model.AiTaskUnderstandingAdvice
import com.swan1127.repland.domain.model.AiTaskUnderstandingRequest
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceRequest
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceResult
import com.swan1127.repland.domain.model.ArrangementPlacementSource
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.ports.AiModelCatalogResult
import com.swan1127.repland.domain.ports.AiProviderConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The debug adapters are the only code that turns a provider's answer into app state, and
 * two of their failure modes are silent:
 *
 * 1. **The reflective constructor.** `ReplandApplication` builds these by
 *    `Class.forName(...).getDeclaredConstructor(AiProviderConfigRepository::class.java)`, and
 *    if that exact signature ever stops existing the app quietly falls back to the no-op
 *    advisor — the UI would just say "not configured" forever. [reflectiveConstructor] pins it.
 * 2. **An HTTP status nobody translated.** Every branch below is a distinct thing to tell the
 *    user, and a missing one collapses into a generic failure.
 */

private class FakeTransport(private var outcome: AiHttpOutcome) : AiHttpTransport {
    /** POSTs, as (url, apiKey, body). */
    val calls = mutableListOf<Triple<String, String, String>>()

    /** GETs, as (url, apiKey). Kept apart from [calls] so a test can assert "no request at
     *  all" without having to care which verb was used. */
    val gets = mutableListOf<Pair<String, String>>()

    fun respondWith(next: AiHttpOutcome) {
        outcome = next
    }

    override suspend fun post(url: String, apiKey: String, jsonBody: String): AiHttpOutcome {
        calls += Triple(url, apiKey, jsonBody)
        return outcome
    }

    override suspend fun get(url: String, apiKey: String): AiHttpOutcome {
        gets += url to apiKey
        return outcome
    }
}

private class FakeConfigRepository(private val secret: AiProviderSecret?) : AiProviderConfigRepository {
    override fun observe(): Flow<AiProviderConfig> = flowOf(AiProviderConfig())

    override suspend fun save(baseUrl: String, model: String, apiKey: String) = Unit

    override suspend fun clearApiKey() = Unit

    override suspend fun readSecret(): AiProviderSecret? = secret
}

class DebugOpenAiCompatibleAdvisorTest {

    private val secret = AiProviderSecret(
        baseUrl = "https://api.deepseek.com",
        model = "deepseek-flash",
        apiKey = "sk-test",
    )

    private fun taskContext() = AiTaskContext(
        reference = "t1",
        title = "复习英语",
        description = "",
        category = TaskCategory.COURSE,
        initialPriority = TaskPriority.MEDIUM,
        currentStatus = TaskStatus.NOT_STARTED,
        estimatedDays = 1,
        expectedDurationMinutes = 60,
        dueDate = null,
        recentFeedback = emptyList(),
        confirmedSegments = emptyList(),
    )

    private fun request() = AiTaskUnderstandingRequest(task = taskContext())

    private fun adviceBody(content: String) =
        """{"choices":[{"message":{"role":"assistant","content":${com.swan1127.repland.data.ai.OpenAiCompatibleCodec.quote(content)}}}]}"""

    // ------------------------------------------------------------------ reflective contract

    @Test
    fun `the one-argument constructor the application reflects on exists`() {
        // @JvmOverloads is what makes this real; a plain defaulted parameter would compile to
        // a synthetic (repository, mask, marker) constructor and this lookup would throw.
        val advisor = reflectiveConstructor("com.swan1127.repland.debug.DebugOpenAiCompatibleAdvisor")
        val arrangement = reflectiveConstructor("com.swan1127.repland.debug.DebugOpenAiCompatibleArrangementAdvisor")
        assertNotNull(advisor)
        assertNotNull(arrangement)
        assertEquals(1, advisor.parameterCount)
        assertEquals(1, arrangement.parameterCount)
    }

    private fun reflectiveConstructor(className: String): java.lang.reflect.Constructor<*> =
        Class.forName(className).getDeclaredConstructor(AiProviderConfigRepository::class.java)

    // ------------------------------------------------------------------ the advisor

    @Test
    fun `no stored secret is unavailable rather than a failure`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Unreachable)
        val advisor = DebugOpenAiCompatibleAdvisor(FakeConfigRepository(null), transport)
        assertEquals(
            AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED),
            advisor.request(request()),
        )
        assertTrue("a call must not be attempted without a key", transport.calls.isEmpty())
    }

    @Test
    fun `a blank key is treated as unconfigured`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Unreachable)
        val advisor = DebugOpenAiCompatibleAdvisor(
            FakeConfigRepository(secret.copy(apiKey = "   ")),
            transport,
        )
        assertEquals(
            AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED),
            advisor.request(request()),
        )
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `a successful answer becomes advice`() = runTest {
        val transport = FakeTransport(
            AiHttpOutcome.Responded(200, adviceBody("""{"summary":"复习三单元"}""")),
        )
        val advisor = DebugOpenAiCompatibleAdvisor(FakeConfigRepository(secret), transport)
        val result = advisor.request(request())
        val advice = (result as AiAdvisorResult.Advice).response as AiTaskUnderstandingAdvice
        assertEquals("复习三单元", advice.summary)
    }

    @Test
    fun `the call goes to chat completions under the configured base url`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Responded(200, adviceBody("""{"summary":"x"}""")))
        DebugOpenAiCompatibleAdvisor(FakeConfigRepository(secret), transport).request(request())
        val (url, apiKey, body) = transport.calls.single()
        assertEquals("https://api.deepseek.com/chat/completions", url)
        assertEquals("sk-test", apiKey)
        assertTrue(body.contains("deepseek-flash"))
    }

    @Test
    fun `a trailing slash on the base url does not double up`() = runTest {
        // The repository trims one slash on save, but a value written by an older build (or a
        // hand-edited preference) must not produce `...//chat/completions`.
        val transport = FakeTransport(AiHttpOutcome.Responded(200, adviceBody("""{"summary":"x"}""")))
        val advisor = DebugOpenAiCompatibleAdvisor(
            FakeConfigRepository(secret.copy(baseUrl = "https://api.deepseek.com/")),
            transport,
        )
        advisor.request(request())
        assertEquals("https://api.deepseek.com/chat/completions", transport.calls.single().first)
    }

    @Test
    fun `http statuses become the reasons the user needs`() = runTest {
        val cases = mapOf(
            401 to AiAdvisorFailureReason.AUTHENTICATION_FAILURE,
            403 to AiAdvisorFailureReason.AUTHENTICATION_FAILURE,
            429 to AiAdvisorFailureReason.RATE_LIMITED,
            400 to AiAdvisorFailureReason.REMOTE_FAILURE,
            500 to AiAdvisorFailureReason.REMOTE_FAILURE,
            503 to AiAdvisorFailureReason.REMOTE_FAILURE,
        )
        cases.forEach { (status, reason) ->
            val transport = FakeTransport(AiHttpOutcome.Responded(status, """{"error":"nope"}"""))
            val advisor = DebugOpenAiCompatibleAdvisor(FakeConfigRepository(secret), transport)
            assertEquals(
                "status $status",
                AiAdvisorResult.Failed(reason),
                advisor.request(request()),
            )
        }
    }

    @Test
    fun `an ok status with unusable content is an invalid response`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Responded(200, """{"choices":[]}"""))
        val advisor = DebugOpenAiCompatibleAdvisor(FakeConfigRepository(secret), transport)
        assertEquals(
            AiAdvisorResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE),
            advisor.request(request()),
        )
    }

    @Test
    fun `a timeout is reported as a timeout, not as unreachable`() = runTest {
        // The two lead to different user advice: retry later vs check the network.
        val transport = FakeTransport(AiHttpOutcome.TimedOut)
        val advisor = DebugOpenAiCompatibleAdvisor(FakeConfigRepository(secret), transport)
        assertEquals(
            AiAdvisorResult.Failed(AiAdvisorFailureReason.TIMEOUT),
            advisor.request(request()),
        )
    }

    @Test
    fun `a connection failure is a transport failure`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Unreachable)
        val advisor = DebugOpenAiCompatibleAdvisor(FakeConfigRepository(secret), transport)
        assertEquals(
            AiAdvisorResult.Failed(AiAdvisorFailureReason.TRANSPORT_FAILURE),
            advisor.request(request()),
        )
    }

    // ------------------------------------------------------------------ the arrangement advisor

    private fun arrangementRequest() = ArrangementAssistantAdviceRequest(
        utterance = "下午两点开会",
        date = java.time.LocalDate.of(2026, 3, 2),
        occupiedIntervals = emptyList(),
    )

    private fun arrangementBody(candidatesJson: String, confidence: String = "还行") =
        """{"choices":[{"message":{"content":${com.swan1127.repland.data.ai.OpenAiCompatibleCodec.quote(
            """{"candidates":[$candidatesJson],"confidenceLabel":"$confidence"}""",
        )}}}]}"""

    @Test
    fun `arrangement candidates are parsed`() = runTest {
        val transport = FakeTransport(
            AiHttpOutcome.Responded(
                200,
                arrangementBody("""{"title":"开会","category":"OFFICE","durationMinutes":60,"explicitStartMinute":840}"""),
            ),
        )
        val advisor = DebugOpenAiCompatibleArrangementAdvisor(FakeConfigRepository(secret), transport)
        val result = advisor.refine(arrangementRequest())
        val advice = (result as ArrangementAssistantAdviceResult.Advice).advice
        assertEquals("开会", advice.candidates.single().title)
        assertEquals(TaskCategory.OFFICE, advice.candidates.single().category)
        assertEquals(840, advice.candidates.single().timeHint.explicitStartMinute)
    }

    @Test
    fun `a stated time is marked as the user's own, not the advisor's`() = runTest {
        val transport = FakeTransport(
            AiHttpOutcome.Responded(200, arrangementBody("""{"title":"开会","explicitStartMinute":840}""")),
        )
        val advisor = DebugOpenAiCompatibleArrangementAdvisor(FakeConfigRepository(secret), transport)
        val advice = (advisor.refine(arrangementRequest()) as ArrangementAssistantAdviceResult.Advice).advice
        assertEquals(ArrangementPlacementSource.USER_EXPLICIT, advice.candidates.single().placementSource)
    }

    @Test
    fun `an item with no stated time is left unscheduled rather than given a slot`() = runTest {
        // The core constraint of this feature: the advisor must not turn "写作业" into a
        // confident-looking 09:00 block. An unscheduled candidate is what makes the preview
        // ask the user instead of guessing.
        val transport = FakeTransport(
            AiHttpOutcome.Responded(200, arrangementBody("""{"title":"写作业","needsClarification":["TIME"]}""")),
        )
        val advisor = DebugOpenAiCompatibleArrangementAdvisor(FakeConfigRepository(secret), transport)
        val candidate = (advisor.refine(arrangementRequest()) as ArrangementAssistantAdviceResult.Advice)
            .advice.candidates.single()
        assertEquals(ArrangementPlacementSource.UNSCHEDULED, candidate.placementSource)
        assertEquals(null, candidate.timeHint.explicitStartMinute)
        assertEquals(1, candidate.needsClarification.size)
    }

    @Test
    fun `an out-of-range start minute is dropped instead of trusted`() = runTest {
        val transport = FakeTransport(
            AiHttpOutcome.Responded(200, arrangementBody("""{"title":"开会","explicitStartMinute":5000}""")),
        )
        val advisor = DebugOpenAiCompatibleArrangementAdvisor(FakeConfigRepository(secret), transport)
        val candidate = (advisor.refine(arrangementRequest()) as ArrangementAssistantAdviceResult.Advice)
            .advice.candidates.single()
        assertEquals(null, candidate.timeHint.explicitStartMinute)
        assertEquals(ArrangementPlacementSource.UNSCHEDULED, candidate.placementSource)
    }

    @Test
    fun `a duration outside the accepted range becomes unknown instead of losing the item`() = runTest {
        // The validator drops out-of-range candidates outright, so an item the user did
        // mention would vanish from the preview. Keeping it with an unknown length is the
        // recoverable failure: the preview asks, the user answers.
        val transport = FakeTransport(
            AiHttpOutcome.Responded(200, arrangementBody("""{"title":"写论文","durationMinutes":99999}""")),
        )
        val advisor = DebugOpenAiCompatibleArrangementAdvisor(FakeConfigRepository(secret), transport)
        val candidate = (advisor.refine(arrangementRequest()) as ArrangementAssistantAdviceResult.Advice)
            .advice.candidates.single()
        assertEquals("写论文", candidate.title)
        assertEquals(null, candidate.durationMinutes)
    }

    @Test
    fun `a block that would run past midnight keeps the item but not the start`() = runTest {
        // 23:30 plus two hours does not fit in the day. Clamping the length would invent a
        // duration, so the start goes instead and the item becomes unscheduled.
        val transport = FakeTransport(
            AiHttpOutcome.Responded(
                200,
                arrangementBody("""{"title":"复习","explicitStartMinute":1410,"durationMinutes":120}"""),
            ),
        )
        val advisor = DebugOpenAiCompatibleArrangementAdvisor(FakeConfigRepository(secret), transport)
        val candidate = (advisor.refine(arrangementRequest()) as ArrangementAssistantAdviceResult.Advice)
            .advice.candidates.single()
        assertEquals(null, candidate.timeHint.explicitStartMinute)
        assertEquals(120, candidate.durationMinutes)
        assertEquals(ArrangementPlacementSource.UNSCHEDULED, candidate.placementSource)
    }

    @Test
    fun `a title-less candidate is dropped`() = runTest {
        val transport = FakeTransport(
            AiHttpOutcome.Responded(200, arrangementBody("""{"title":"  "},{"title":"开会"}""")),
        )
        val advisor = DebugOpenAiCompatibleArrangementAdvisor(FakeConfigRepository(secret), transport)
        val advice = (advisor.refine(arrangementRequest()) as ArrangementAssistantAdviceResult.Advice).advice
        assertEquals(1, advice.candidates.size)
        assertEquals("开会", advice.candidates.single().title)
    }

    @Test
    fun `an unknown category falls back to the app's most common one`() = runTest {
        val transport = FakeTransport(
            AiHttpOutcome.Responded(200, arrangementBody("""{"title":"开会","category":"NOT_A_CATEGORY"}""")),
        )
        val advisor = DebugOpenAiCompatibleArrangementAdvisor(FakeConfigRepository(secret), transport)
        val advice = (advisor.refine(arrangementRequest()) as ArrangementAssistantAdviceResult.Advice).advice
        assertEquals(TaskCategory.COURSE, advice.candidates.single().category)
    }

    @Test
    fun `no candidates at all is an invalid response`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Responded(200, arrangementBody("")))
        val advisor = DebugOpenAiCompatibleArrangementAdvisor(FakeConfigRepository(secret), transport)
        assertEquals(
            ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE),
            advisor.refine(arrangementRequest()),
        )
    }

    @Test
    fun `the arrangement advisor reports unconfigured the same way`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Unreachable)
        val advisor = DebugOpenAiCompatibleArrangementAdvisor(FakeConfigRepository(null), transport)
        assertEquals(
            ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED),
            advisor.refine(arrangementRequest()),
        )
        assertTrue(transport.calls.isEmpty())
    }
}

/**
 * The model catalog is what keeps a provider's model names out of the app. Its failure modes
 * mirror the advisor's, with one addition worth pinning: an *empty* list must not be reported
 * as a successful empty list, because the screen would then say the provider serves nothing.
 */
class DebugOpenAiCompatibleModelCatalogTest {

    private val secret = AiProviderSecret(
        baseUrl = "https://api.deepseek.com",
        model = "deepseek-flash",
        apiKey = "sk-test",
    )

    private fun body(vararg ids: String) =
        """{"data":[${ids.joinToString(",") { """{"id":"$it"}""" }}]}"""

    @Test
    fun `the one-argument constructor the application reflects on exists`() {
        val ctor = Class.forName("com.swan1127.repland.debug.DebugOpenAiCompatibleModelCatalog")
            .getDeclaredConstructor(AiProviderConfigRepository::class.java)
        assertEquals(1, ctor.parameterCount)
    }

    @Test
    fun `no stored secret is unconfigured and no request is made`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Unreachable)
        val catalog = DebugOpenAiCompatibleModelCatalog(FakeConfigRepository(null), transport)
        assertEquals(
            AiModelCatalogResult.Failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED),
            catalog.listModels(),
        )
        assertTrue(transport.gets.isEmpty())
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `a blank key is treated as unconfigured`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Unreachable)
        val catalog = DebugOpenAiCompatibleModelCatalog(
            FakeConfigRepository(secret.copy(apiKey = "   ")),
            transport,
        )
        assertEquals(
            AiModelCatalogResult.Failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED),
            catalog.listModels(),
        )
        assertTrue(transport.gets.isEmpty())
    }

    @Test
    fun `the list is read from models under the configured base url`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Responded(200, body("deepseek-flash", "deepseek-v4-pro")))
        val catalog = DebugOpenAiCompatibleModelCatalog(FakeConfigRepository(secret), transport)

        assertEquals(
            AiModelCatalogResult.Models(listOf("deepseek-flash", "deepseek-v4-pro")),
            catalog.listModels(),
        )
        assertEquals("https://api.deepseek.com/models" to "sk-test", transport.gets.single())
        assertTrue("a GET must not be sent as a POST", transport.calls.isEmpty())
    }

    @Test
    fun `a trailing slash on the base url does not double up`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Responded(200, body("m")))
        val catalog = DebugOpenAiCompatibleModelCatalog(
            FakeConfigRepository(secret.copy(baseUrl = "https://api.deepseek.com/")),
            transport,
        )
        catalog.listModels()
        assertEquals("https://api.deepseek.com/models", transport.gets.single().first)
    }

    @Test
    fun `http statuses become the reasons the user needs`() = runTest {
        val cases = mapOf(
            401 to AiAdvisorFailureReason.AUTHENTICATION_FAILURE,
            403 to AiAdvisorFailureReason.AUTHENTICATION_FAILURE,
            429 to AiAdvisorFailureReason.RATE_LIMITED,
            404 to AiAdvisorFailureReason.REMOTE_FAILURE,
            500 to AiAdvisorFailureReason.REMOTE_FAILURE,
        )
        cases.forEach { (status, reason) ->
            val transport = FakeTransport(AiHttpOutcome.Responded(status, """{"error":"nope"}"""))
            val catalog = DebugOpenAiCompatibleModelCatalog(FakeConfigRepository(secret), transport)
            assertEquals(
                "status $status",
                AiModelCatalogResult.Failed(reason),
                catalog.listModels(),
            )
        }
    }

    @Test
    fun `a timeout is reported as a timeout, not as unreachable`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.TimedOut)
        val catalog = DebugOpenAiCompatibleModelCatalog(FakeConfigRepository(secret), transport)
        assertEquals(AiModelCatalogResult.Failed(AiAdvisorFailureReason.TIMEOUT), catalog.listModels())
    }

    @Test
    fun `a connection failure is a transport failure`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Unreachable)
        val catalog = DebugOpenAiCompatibleModelCatalog(FakeConfigRepository(secret), transport)
        assertEquals(
            AiModelCatalogResult.Failed(AiAdvisorFailureReason.TRANSPORT_FAILURE),
            catalog.listModels(),
        )
    }

    @Test
    fun `an empty list is a failure, not a provider with no models`() = runTest {
        // `Models(emptyList())` would render as "服务商提供 0 个模型", which reads as a broken
        // provider. The shape was merely unexpected, and saying so is more useful.
        val transport = FakeTransport(AiHttpOutcome.Responded(200, """{"data":[]}"""))
        val catalog = DebugOpenAiCompatibleModelCatalog(FakeConfigRepository(secret), transport)
        assertEquals(
            AiModelCatalogResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE),
            catalog.listModels(),
        )
    }

    @Test
    fun `a non-json body is an invalid response rather than a crash`() = runTest {
        val transport = FakeTransport(AiHttpOutcome.Responded(200, "<html>gateway</html>"))
        val catalog = DebugOpenAiCompatibleModelCatalog(FakeConfigRepository(secret), transport)
        assertEquals(
            AiModelCatalogResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE),
            catalog.listModels(),
        )
    }
}
