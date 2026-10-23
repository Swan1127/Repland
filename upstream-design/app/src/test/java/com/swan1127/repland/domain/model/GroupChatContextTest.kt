package com.swan1127.repland.domain.model

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GroupChatContextTest {
    @Test fun `follow up reaches both specialists and structured planner with previous draft`() = runBlocking {
        val prompts = mutableListOf<String>()
        var refinement = ""
        val draft = ArrangementCandidate("英语", TaskCategory.COURSE, 30, ArrangementTimeHint(600), emptySet())
        val request = GroupChatRequest(
            utterance = "第二项改到18点，第一项保留", date = LocalDate.of(2026, 10, 22),
            occupiedEntries = emptyList(), agents = DefaultAgentTeam.agents, cloudEnabled = true,
            chat = object : AgentChatModel {
                override suspend fun say(systemPrompt: String, userPrompt: String): AgentChatResult {
                    prompts += userPrompt
                    return AgentChatResult.Said("草案仍需确认。")
                }
            },
            refine = { text, _, _ ->
                refinement = text
                ArrangementAssistantAdviceResult.Advice(ArrangementAssistantAdvice(listOf(draft), "待确认"))
            },
            history = listOf(GroupChatMessage(GroupChatMessageRole.USER, "英语半小时，再跑步40分钟")),
            previousCandidates = listOf(draft, draft.copy(title = "跑步", category = TaskCategory.LEISURE, durationMinutes = 40)),
            categoryPreferences = mapOf(TaskCategory.COURSE to 35),
            profileEvidence = listOf(ProfileEvidence("e", ProfileEvidenceScope.GENERAL, "过去有确认反馈", emptyList(), 0, 0, false)),
        )
        GroupChatEngine(typingPauseMillis = 0).run(request) {}
        assertTrue(refinement.contains("2. 跑步"))
        assertTrue(refinement.contains("第二项改到18点"))
        assertTrue(refinement.contains("英语半小时"))
        assertTrue(prompts.all { it.contains("COURSE=35") && it.contains("过去有确认反馈") })
    }

    @Test fun `habit trace never claims preferences were read when no data was provided`() = runBlocking {
        val events = mutableListOf<GroupChatEvent>()
        val request = GroupChatRequest("@小习 安排英语", LocalDate.of(2026, 10, 22), emptyList(), DefaultAgentTeam.agents, true,
            object : AgentChatModel {
                override suspend fun say(systemPrompt: String, userPrompt: String) = AgentChatResult.Said("还没有个人偏好依据。")
            }, { _, _, _ -> ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.INVALID_RESPONSE) })
        GroupChatEngine(typingPauseMillis = 0).run(request) { events += it }
        val habit = events.filterIsInstance<GroupChatEvent.AgentSaid>().first { it.agentId == "habit" }
        assertTrue(habit.tools.any { it.label == "偏好资料不足" && it.outcome == ToolOutcome.SKIPPED })
        assertFalse(habit.tools.any { it.label == "读取偏好" && it.outcome == ToolOutcome.DONE })
    }
}
