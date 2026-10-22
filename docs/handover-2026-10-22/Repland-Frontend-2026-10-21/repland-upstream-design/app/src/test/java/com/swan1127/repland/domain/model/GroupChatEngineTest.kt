package com.swan1127.repland.domain.model

import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatEngineTest {

    private val agents = DefaultAgentTeam.agents

    /** Records how many times the provider was asked, so "no local fallback" is testable. */
    private class FakeChat(
        private val answer: (String) -> AgentChatResult = { AgentChatResult.Said("我这边看过了。") },
    ) : AgentChatModel {
        var calls = 0
            private set

        override suspend fun say(systemPrompt: String, userPrompt: String): AgentChatResult {
            calls++
            return answer(systemPrompt)
        }
    }

    private fun advice() = ArrangementAssistantAdviceResult.Advice(
        ArrangementAssistantAdvice(
            candidates = listOf(
                ArrangementCandidate(
                    title = "复习数据结构",
                    category = TaskCategory.COURSE,
                    durationMinutes = 90,
                    timeHint = ArrangementTimeHint(explicitStartMinute = 15 * 60),
                    needsClarification = emptySet(),
                    placementSource = ArrangementPlacementSource.USER_EXPLICIT,
                ),
            ),
            confidenceLabel = "较有把握",
        ),
    )

    private fun request(
        text: String,
        chat: AgentChatModel = FakeChat(),
        cloudEnabled: Boolean = true,
        refine: suspend (String, LocalDate, List<TimelineEntry>) -> ArrangementAssistantAdviceResult = { _, _, _ -> advice() },
    ) = GroupChatRequest(
        utterance = text,
        date = LocalDate.of(2026, 10, 21),
        occupiedEntries = emptyList(),
        agents = agents,
        cloudEnabled = cloudEnabled,
        chat = chat,
        refine = refine,
    )

    @Test
    fun `every role is spoken by the provider then a plan is produced`() = runBlocking {
        val chat = FakeChat()
        val events = mutableListOf<GroupChatEvent>()
        GroupChatEngine().run(request("15:00 复习数据结构 90 分钟", chat)) { events += it }

        assertTrue(events.first() is GroupChatEvent.UserSaid)
        val said = events.filterIsInstance<GroupChatEvent.AgentSaid>()
        assertEquals("every role speaks once", agents.size, said.size)
        assertEquals("the planner closes the conversation", "planner", said.last().agentId)
        assertTrue("a plan is produced", events.any { it is GroupChatEvent.PlanReady })
        assertTrue("nothing failed", events.none { it is GroupChatEvent.Failed })

        // Four specialists plus the planner's own line: one provider call per message.
        assertEquals("every message came from the provider", agents.size, chat.calls)
    }

    @Test
    fun `typing precedes every agent message`() = runBlocking {
        val events = mutableListOf<GroupChatEvent>()
        GroupChatEngine().run(request("15:00 复习数据结构 90 分钟")) { events += it }

        events.filterIsInstance<GroupChatEvent.AgentTyping>().forEach { typing ->
            val index = events.indexOf(typing)
            val saidAfter = events.drop(index + 1).filterIsInstance<GroupChatEvent.AgentSaid>().firstOrNull()
            assertEquals("the next agent message belongs to the typing agent", typing.agentId, saidAfter?.agentId)
        }
    }

    @Test
    fun `at mention limits the speakers`() = runBlocking {
        val chat = FakeChat()
        val events = mutableListOf<GroupChatEvent>()
        GroupChatEngine().run(request("@小盾 检查一下", chat)) { events += it }

        val said = events.filterIsInstance<GroupChatEvent.AgentSaid>()
        // The mentioned agent plus the planner, who always closes the conversation.
        assertEquals(2, said.size)
        assertTrue(said.any { it.agentId == "conflict" })
        assertTrue(said.any { it.agentId == "planner" })
        assertEquals("only the speakers were asked", 2, chat.calls)
    }

    @Test
    fun `every agent message carries the speaker catchphrase`() = runBlocking {
        val events = mutableListOf<GroupChatEvent>()
        GroupChatEngine().run(request("15:00 复习数据结构 90 分钟")) { events += it }

        val byId = agents.associateBy { it.id }
        events.filterIsInstance<GroupChatEvent.AgentSaid>().forEach { said ->
            val agent = byId.getValue(said.agentId)
            assertTrue("${agent.name} leads with its catchphrase", said.text.startsWith(agent.catchphrase))
        }
    }

    @Test
    fun `a failing provider ends the turn instead of inventing text`() = runBlocking {
        val chat = FakeChat { AgentChatResult.Failed(AiAdvisorFailureReason.TRANSPORT_FAILURE) }
        val events = mutableListOf<GroupChatEvent>()
        GroupChatEngine().run(request("15:00 复习数据结构 90 分钟", chat)) { events += it }

        val failure = events.filterIsInstance<GroupChatEvent.Failed>().single()
        assertEquals(AiAdvisorFailureReason.TRANSPORT_FAILURE, failure.reason)
        assertTrue("no agent line is fabricated", events.none { it is GroupChatEvent.AgentSaid })
        assertTrue("no plan is fabricated", events.none { it is GroupChatEvent.PlanReady })
    }

    /** Answers after a delay, so "the provider is slow" can be simulated exactly. */
    private class SlowChat(
        private val delayMillis: Long,
        private val answer: (String) -> AgentChatResult = { AgentChatResult.Said("我这边看过了。") },
    ) : AgentChatModel {
        var calls = 0
            private set

        override suspend fun say(systemPrompt: String, userPrompt: String): AgentChatResult {
            calls++
            delay(delayMillis)
            return answer(systemPrompt)
        }
    }

    @Test
    fun `disabled cloud fails before any provider call`() = runBlocking {
        val chat = FakeChat()
        var refineCalls = 0
        val events = mutableListOf<GroupChatEvent>()
        GroupChatEngine().run(
            request("15:00 复习数据结构 90 分钟", chat, cloudEnabled = false) { _, _, _ ->
                refineCalls++
                advice()
            },
        ) { events += it }

        val failure = events.filterIsInstance<GroupChatEvent.Failed>().single()
        assertEquals(AiAdvisorFailureReason.DISABLED, failure.reason)
        assertEquals("the provider is never contacted", 0, chat.calls)
        assertEquals("the planner is never asked", 0, refineCalls)
        assertTrue("no plan is fabricated", events.none { it is GroupChatEvent.PlanReady })
    }

    @Test
    fun `a stalled provider produces a timeout notice rather than an endless typing bubble`() =
        runBlocking {
            val chat = SlowChat(delayMillis = 10_000)
            val events = mutableListOf<GroupChatEvent>()
            // One twentieth of the delay: the budget expires long before any answer arrives.
            GroupChatEngine(turnTimeoutMillis = 500).run(request("15:00 复习数据结构 90 分钟", chat)) {
                events += it
            }

            val failure = events.filterIsInstance<GroupChatEvent.Failed>().single()
            assertEquals(AiAdvisorFailureReason.TIMEOUT, failure.reason)
            assertTrue("no agent line is fabricated", events.none { it is GroupChatEvent.AgentSaid })
            assertTrue("no plan is fabricated", events.none { it is GroupChatEvent.PlanReady })
        }
}
