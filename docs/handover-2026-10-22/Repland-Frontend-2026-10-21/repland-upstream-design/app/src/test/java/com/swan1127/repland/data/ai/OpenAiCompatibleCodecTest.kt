package com.swan1127.repland.data.ai

import com.swan1127.repland.data.json.StrictJson
import com.swan1127.repland.domain.model.AI_ADVISOR_CONTRACT_VERSION
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiAdvisorResult
import com.swan1127.repland.domain.model.AiDailySummaryAdvice
import com.swan1127.repland.domain.model.AiDailySummaryRequest
import com.swan1127.repland.domain.model.AiDifficultyAndDurationAdvice
import com.swan1127.repland.domain.model.AiDifficultyAndDurationRequest
import com.swan1127.repland.domain.model.AiFeedbackContext
import com.swan1127.repland.domain.model.AiReplanRequest
import com.swan1127.repland.domain.model.AiSortingExplanationAdvice
import com.swan1127.repland.domain.model.AiSortingExplanationRequest
import com.swan1127.repland.domain.model.AiTaskBreakdownAdvice
import com.swan1127.repland.domain.model.AiTaskBreakdownRequest
import com.swan1127.repland.domain.model.AiTaskContext
import com.swan1127.repland.domain.model.AiTaskUnderstandingAdvice
import com.swan1127.repland.domain.model.AiTaskUnderstandingRequest
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OpenAiCompatibleCodec] is the only place in the app that reads text written by a language
 * model, and a model's output is the least controllable input the app ever sees. Every test
 * here is about surviving something a model actually does: wrapping the object in a fence,
 * adding a sentence of commentary, omitting an optional field, or answering a different
 * question than the one asked.
 */
class OpenAiCompatibleCodecTest {

    private fun context(
        reference: String = "t1",
        title: String = "复习英语",
        dueDate: LocalDate? = null,
        feedback: List<AiFeedbackContext> = emptyList(),
    ) = AiTaskContext(
        reference = reference,
        title = title,
        description = "",
        category = TaskCategory.COURSE,
        initialPriority = TaskPriority.MEDIUM,
        currentStatus = TaskStatus.NOT_STARTED,
        estimatedDays = 1,
        expectedDurationMinutes = 60,
        dueDate = dueDate,
        recentFeedback = feedback,
        confirmedSegments = emptyList(),
    )

    private val understanding = AiTaskUnderstandingRequest(task = context())

    // ------------------------------------------------------------------ extractJsonObject

    @Test
    fun `a bare object is returned as-is`() {
        assertEquals("""{"a":1}""", OpenAiCompatibleCodec.extractJsonObject("""{"a":1}"""))
    }

    @Test
    fun `a json fence is unwrapped`() {
        val raw = "```json\n{\"a\":1}\n```"
        assertEquals("""{"a":1}""", OpenAiCompatibleCodec.extractJsonObject(raw))
    }

    @Test
    fun `a fence with no language tag is unwrapped too`() {
        assertEquals("""{"a":1}""", OpenAiCompatibleCodec.extractJsonObject("```\n{\"a\":1}\n```"))
    }

    @Test
    fun `commentary around the object is discarded`() {
        val raw = "好的，这是结果：\n{\"a\":1}\n希望有帮助！"
        assertEquals("""{"a":1}""", OpenAiCompatibleCodec.extractJsonObject(raw))
    }

    @Test
    fun `a fence still wins when the commentary before it contains a brace`() {
        // The only case where unwrapping the fence changes the answer: the depth scan finds
        // the first `{` it meets, so a model that explains itself with a brace — "用 { } 表示
        // 集合" — before the fenced object would otherwise hand back `{ }` as the document.
        // Without this case the fence step is untested: every other fenced input is found by
        // the scanner anyway, so removing the step would look harmless.
        val raw = "说明：用 { } 表示集合。\n```json\n{\"a\":1}\n```"
        assertEquals("""{"a":1}""", OpenAiCompatibleCodec.extractJsonObject(raw))
    }

    @Test
    fun `braces inside a string value do not end the object early`() {
        // The whole point of scanning with a depth counter instead of `lastIndexOf('}')`:
        // a summary that mentions braces must not truncate the parse. `indexOf('{')` would
        // also be wrong here if the prose came first, so both ends matter.
        val raw = """{"summary":"用 { } 表示集合","b":2}"""
        val extracted = OpenAiCompatibleCodec.extractJsonObject(raw)
        assertEquals(raw, extracted)
        assertEquals("用 { } 表示集合", StrictJson.parseObject(extracted!!)["summary"])
    }

    @Test
    fun `an unbalanced brace inside a string does not end the object early`() {
        // The sibling test above uses a *balanced* pair, which the depth counter survives
        // even with the in-string guard removed — so it proves nothing about the guard. A
        // lone brace is the case that actually distinguishes "counts braces" from "counts
        // braces outside strings": without the guard the first one closes the object early
        // and the second one never closes it at all.
        val closesEarly = """{"summary":"结束用 } 表示","b":2}"""
        assertEquals(closesEarly, OpenAiCompatibleCodec.extractJsonObject(closesEarly))

        val neverCloses = """{"summary":"开始用 { 表示","b":2}"""
        assertEquals(neverCloses, OpenAiCompatibleCodec.extractJsonObject(neverCloses))
    }

    @Test
    fun `an escaped quote inside a string does not end the string early`() {
        val raw = """{"summary":"他说 \"{注意}\" 然后继续"}"""
        val extracted = OpenAiCompatibleCodec.extractJsonObject(raw)
        assertEquals("他说 \"{注意}\" 然后继续", StrictJson.parseObject(extracted!!)["summary"])
    }

    @Test
    fun `nested objects are kept whole`() {
        val raw = """{"a":{"b":{"c":1}},"d":2}"""
        assertEquals(raw, OpenAiCompatibleCodec.extractJsonObject(raw))
    }

    @Test
    fun `text with no object at all yields null`() {
        assertNull(OpenAiCompatibleCodec.extractJsonObject("我不知道怎么回答。"))
    }

    @Test
    fun `an unterminated object yields null rather than a partial string`() {
        assertNull(OpenAiCompatibleCodec.extractJsonObject("""{"a":1"""))
    }

    // ------------------------------------------------------------------ quote

    @Test
    fun `quote escapes the characters that would break the document`() {
        assertEquals("\"a\\\"b\"", OpenAiCompatibleCodec.quote("a\"b"))
        assertEquals("\"a\\\\b\"", OpenAiCompatibleCodec.quote("a\\b"))
        assertEquals("\"a\\nb\"", OpenAiCompatibleCodec.quote("a\nb"))
        assertEquals("\"a\\tb\"", OpenAiCompatibleCodec.quote("a\tb"))
    }

    @Test
    fun `quote turns a raw control character into an escape and leaves Chinese alone`() {
        assertEquals("\"\\u0001\"", OpenAiCompatibleCodec.quote("\u0001"))
        assertEquals("\"复习英语\"", OpenAiCompatibleCodec.quote("复习英语"))
    }

    @Test
    fun `quoted output is readable by a strict parser`() {
        val awkward = "他说 \"别慌\"\n然后\r走了\t一步\u0007"
        val parsed = StrictJson.parse(OpenAiCompatibleCodec.quote(awkward))
        assertEquals(awkward, parsed)
    }

    // ------------------------------------------------------------------ buildChatBody

    @Test
    fun `the chat body is a valid document that names the model`() {
        val body = OpenAiCompatibleCodec.buildChatBody("deepseek-flash", understanding)
        val parsed = StrictJson.parseObject(body)
        assertEquals("deepseek-flash", parsed["model"])
        val messages = parsed["messages"] as List<*>
        assertEquals(2, messages.size)
        assertEquals("system", (messages[0] as Map<*, *>)["role"])
        assertEquals("user", (messages[1] as Map<*, *>)["role"])
    }

    @Test
    fun `the system prompt asks for one JSON object and forbids decoration`() {
        // The instruction is load-bearing: it is the only thing keeping the answer parseable,
        // because response_format is deliberately not sent (provider support varies).
        val body = OpenAiCompatibleCodec.buildChatBody("m", understanding)
        val system = ((StrictJson.parseObject(body)["messages"] as List<*>)[0] as Map<*, *>)["content"] as String
        assertTrue(system.contains("只输出一个 JSON 对象"))
        assertTrue(system.contains("Markdown 围栏"))
    }

    @Test
    fun `the user message carries the task's own fields`() {
        val body = OpenAiCompatibleCodec.buildChatBody("m", understanding)
        val user = ((StrictJson.parseObject(body)["messages"] as List<*>)[1] as Map<*, *>)["content"] as String
        assertTrue(user.contains("reference=t1"))
        assertTrue(user.contains("复习英语"))
    }

    @Test
    fun `a replan request tells the model which slots it may not take`() {
        val request = AiReplanRequest(
            affectedTasks = listOf(context()),
            constraintSummary = listOf("每天最多 240 分钟"),
            hardConstraints = listOf(
                com.swan1127.repland.domain.model.AiHardConstraintContext(
                    date = LocalDate.of(2026, 3, 2),
                    startMinute = 540,
                    endMinute = 600,
                ),
            ),
        )
        val user = ((StrictJson.parseObject(OpenAiCompatibleCodec.buildChatBody("m", request))["messages"] as List<*>)[1] as Map<*, *>)["content"] as String
        assertTrue(user.contains("09:00-10:00"))
        assertTrue(user.contains("每天最多 240 分钟"))
    }

    @Test
    fun `a daily summary with no confirmed work says so instead of sending an empty list`() {
        val request = AiDailySummaryRequest(day = LocalDate.of(2026, 3, 2), confirmedFeedback = emptyList())
        val user = ((StrictJson.parseObject(OpenAiCompatibleCodec.buildChatBody("m", request))["messages"] as List<*>)[1] as Map<*, *>)["content"] as String
        assertTrue(user.contains("没有任何已确认的完成记录"))
    }

    // ------------------------------------------------------------------ parseChatContent

    @Test
    fun `the assistant content is read out of a chat response`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"{\"a\":1}"}}]}"""
        assertEquals("""{"a":1}""", OpenAiCompatibleCodec.parseChatContent(body))
    }

    @Test
    fun `an empty content is treated as absent`() {
        assertNull(OpenAiCompatibleCodec.parseChatContent("""{"choices":[{"message":{"content":""}}]}"""))
    }

    @Test
    fun `a response with no choices yields null`() {
        assertNull(OpenAiCompatibleCodec.parseChatContent("""{"choices":[]}"""))
        assertNull(OpenAiCompatibleCodec.parseChatContent("{}"))
    }

    @Test
    fun `a non-json body yields null instead of throwing`() {
        // A proxy or captive portal answering with HTML must not crash the app.
        assertNull(OpenAiCompatibleCodec.parseChatContent("<html>502 Bad Gateway</html>"))
    }

    // ------------------------------------------------------------------ parseModelIds

    @Test
    fun `model ids keep the provider's order`() {
        val body = """{"data":[{"id":"b"},{"id":"a"}]}"""
        assertEquals(listOf("b", "a"), OpenAiCompatibleCodec.parseModelIds(body))
    }

    @Test
    fun `model listing tolerates a missing or odd payload`() {
        assertEquals(emptyList<String>(), OpenAiCompatibleCodec.parseModelIds("{}"))
        assertEquals(emptyList<String>(), OpenAiCompatibleCodec.parseModelIds("not json"))
        assertEquals(listOf("a"), OpenAiCompatibleCodec.parseModelIds("""{"data":[{"id":"a"},{"id":""},{"nope":1}]}"""))
    }

    // ------------------------------------------------------------------ parseAdvice

    @Test
    fun `understanding advice is parsed and stamped with the request identity`() {
        val result = OpenAiCompatibleCodec.parseAdvice(understanding, """{"summary":"复习三单元"}""")
        val advice = (result as AiAdvisorResult.Advice).response as AiTaskUnderstandingAdvice
        assertEquals("复习三单元", advice.summary)
        assertNull(advice.dependencyNote)
        // The model is never asked to echo these; they are stamped locally so the validator's
        // identity check cannot depend on model behaviour.
        assertEquals(AI_ADVISOR_CONTRACT_VERSION, advice.contractVersion)
        assertEquals(understanding.requestId, advice.requestId)
    }

    @Test
    fun `difficulty advice is parsed`() {
        val request = AiDifficultyAndDurationRequest(task = context())
        val result = OpenAiCompatibleCodec.parseAdvice(
            request,
            """{"difficulty":4,"suggestedDurationMinutes":90,"explanation":"需要集中注意力"}""",
        )
        val advice = (result as AiAdvisorResult.Advice).response as AiDifficultyAndDurationAdvice
        assertEquals(4, advice.difficulty)
        assertEquals(90, advice.suggestedDurationMinutes)
    }

    @Test
    fun `breakdown advice is parsed`() {
        val request = AiTaskBreakdownRequest(task = context())
        val result = OpenAiCompatibleCodec.parseAdvice(
            request,
            """{"steps":["通读课文","整理生词"],"explanation":"先输入后整理"}""",
        )
        val advice = (result as AiAdvisorResult.Advice).response as AiTaskBreakdownAdvice
        assertEquals(listOf("通读课文", "整理生词"), advice.steps)
    }

    @Test
    fun `sorting advice is parsed`() {
        val request = AiSortingExplanationRequest(task = context(), localRankingReasons = listOf("今天到期"))
        val result = OpenAiCompatibleCodec.parseAdvice(request, """{"explanation":"因为今天到期"}""")
        val advice = (result as AiAdvisorResult.Advice).response as AiSortingExplanationAdvice
        assertEquals("因为今天到期", advice.explanation)
    }

    @Test
    fun `daily summary advice keeps its optional next step`() {
        val request = AiDailySummaryRequest(day = LocalDate.of(2026, 3, 2), confirmedFeedback = emptyList())
        val result = OpenAiCompatibleCodec.parseAdvice(
            request,
            """{"summary":"完成了一半","suggestedNextStep":"先做数学"}""",
        )
        val advice = (result as AiAdvisorResult.Advice).response as AiDailySummaryAdvice
        assertEquals("完成了一半", advice.summary)
        assertEquals("先做数学", advice.suggestedNextStep)
    }

    @Test
    fun `a fenced answer is accepted`() {
        val raw = "```json\n{\"summary\":\"复习三单元\"}\n```"
        assertTrue(OpenAiCompatibleCodec.parseAdvice(understanding, raw) is AiAdvisorResult.Advice)
    }

    @Test
    fun `an answer with commentary around it is accepted`() {
        val raw = "好的：\n{\"summary\":\"复习三单元\"}\n还需要别的吗？"
        assertTrue(OpenAiCompatibleCodec.parseAdvice(understanding, raw) is AiAdvisorResult.Advice)
    }

    @Test
    fun `prose with no object is an invalid response`() {
        val result = OpenAiCompatibleCodec.parseAdvice(understanding, "我觉得这个任务不难。")
        assertEquals(AiAdvisorResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE), result)
    }

    @Test
    fun `a missing required field is an invalid response`() {
        val result = OpenAiCompatibleCodec.parseAdvice(understanding, """{"note":"忘了写 summary"}""")
        assertEquals(AiAdvisorResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE), result)
    }

    @Test
    fun `a blank required field counts as missing`() {
        val result = OpenAiCompatibleCodec.parseAdvice(understanding, """{"summary":"   "}""")
        assertEquals(AiAdvisorResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE), result)
    }

    @Test
    fun `a value the validator rejects is an invalid response, not advice`() {
        // difficulty is documented as 1..5; the codec parses 9 happily and the validator is
        // what refuses it. This pins that the two are actually wired together.
        val request = AiDifficultyAndDurationRequest(task = context())
        val result = OpenAiCompatibleCodec.parseAdvice(
            request,
            """{"difficulty":9,"explanation":"太离谱"}""",
        )
        assertEquals(AiAdvisorResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE), result)
    }

    @Test
    fun `an answer to a different question is rejected`() {
        // The model replied with a summary when a breakdown was asked for. The codec keys the
        // parse off the request type, so the extra field is simply not read — and the missing
        // `steps` makes it invalid.
        val request = AiTaskBreakdownRequest(task = context())
        val result = OpenAiCompatibleCodec.parseAdvice(request, """{"summary":"复习三单元"}""")
        assertEquals(AiAdvisorResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE), result)
    }

    @Test
    fun `a malformed segment is an invalid response`() {
        val request = AiReplanRequest(affectedTasks = listOf(context()), constraintSummary = emptyList())
        val result = OpenAiCompatibleCodec.parseAdvice(
            request,
            """{"orderedTaskReferences":["t1"],"proposedSegments":[{"taskReference":"t1","date":"不是日期"}],"explanation":"x"}""",
        )
        assertEquals(AiAdvisorResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE), result)
    }

    @Test
    fun `a well-formed replan is accepted`() {
        val request = AiReplanRequest(affectedTasks = listOf(context()), constraintSummary = emptyList())
        val result = OpenAiCompatibleCodec.parseAdvice(
            request,
            """{"orderedTaskReferences":["t1"],"proposedSegments":[{"taskReference":"t1","date":"2026-03-02","startMinute":600,"endMinute":660}],"explanation":"往后挪"}""",
        )
        val advice = (result as AiAdvisorResult.Advice).response
        assertNotNull(advice)
    }

    // ------------------------------------------------------------------ mapHttpStatus

    @Test
    fun `http status maps to the reason the user needs to hear`() {
        assertEquals(
            AiAdvisorFailureReason.AUTHENTICATION_FAILURE,
            OpenAiCompatibleCodec.mapHttpStatus(401),
        )
        assertEquals(
            AiAdvisorFailureReason.AUTHENTICATION_FAILURE,
            OpenAiCompatibleCodec.mapHttpStatus(403),
        )
        assertEquals(AiAdvisorFailureReason.RATE_LIMITED, OpenAiCompatibleCodec.mapHttpStatus(429))
        assertEquals(AiAdvisorFailureReason.REMOTE_FAILURE, OpenAiCompatibleCodec.mapHttpStatus(500))
        assertEquals(AiAdvisorFailureReason.REMOTE_FAILURE, OpenAiCompatibleCodec.mapHttpStatus(503))
        assertEquals(AiAdvisorFailureReason.REMOTE_FAILURE, OpenAiCompatibleCodec.mapHttpStatus(400))
        assertEquals(AiAdvisorFailureReason.REMOTE_FAILURE, OpenAiCompatibleCodec.mapHttpStatus(404))
    }
}
