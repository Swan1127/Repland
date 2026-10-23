package com.swan1127.repland.domain.model

import com.swan1127.repland.data.ai.OpenAiCompatibleCodec
import org.junit.Assert.*
import org.junit.Test

class AssistantUserMessageTest {
    private val words = "我只完成一半，因为实验设备坏了；请保留周五截止日期。"

    @Test fun `all detail assistant operations retain the user's words in provider payload`() {
        val source = task("t1")
        val requests = listOf(
            AiRequestFactory.taskUnderstanding(source, emptyList(), null, words),
            AiRequestFactory.difficultyAndDuration(source, emptyList(), null, words),
            AiRequestFactory.taskBreakdown(source, emptyList(), null, words),
            AiRequestFactory.replan(listOf(source), emptyList(), null, emptyList(), emptyList(), emptyList(), words),
        )
        requests.forEach { request ->
            assertEquals(words, request.userMessage)
            assertTrue(OpenAiCompatibleCodec.buildChatBody("test-model", request).contains(words))
        }
    }

    @Test fun `planning preparation preserves task scoped context before the consent preview`() {
        val source = task("t1")
        val agent = PlanningAgent(NoOpAiAdvisor, PlanGenerator)
        val work = PlanningAgentWork.TaskUnderstanding(source, emptyList(), null, words)
        assertEquals(words, agent.prepare(work).request.userMessage)
    }
}
