package com.swan1127.repland.data

import com.swan1127.repland.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class GroupChatHistoryCodecTest {
    @Test fun `conversation and editable draft survive round trip`() {
        val candidate = ArrangementCandidate(
            title = "复习\"极限\"\n第二章\\习题", category = TaskCategory.COURSE,
            durationMinutes = 45, timeHint = ArrangementTimeHint(600, "上午"),
            needsClarification = emptySet(), placementSource = ArrangementPlacementSource.USER_EXPLICIT,
            preferredTrackId = "focus", priority = TaskPriority.HIGH, durationEstimated = true,
        )
        val session = StoredGroupChatSession(LocalDate.of(2026, 10, 22), listOf(
            GroupChatMessage(GroupChatMessageRole.USER, "把第二项改到下午\n保留第一项"),
            GroupChatMessage(GroupChatMessageRole.ASSISTANT, "已生成草案", "planner"),
        ), listOf(candidate))
        assertEquals(session, GroupChatHistoryCodec.decode(GroupChatHistoryCodec.encode(session)))
    }

    @Test fun `invalid and out of bounds records are ignored rather than partially restored`() {
        assertNull(GroupChatHistoryCodec.decode("broken"))
        assertNull(GroupChatHistoryCodec.decode("{\"date\":\"2026-10-22\",\"messages\":[{\"role\":\"UNKNOWN\"}]}"))
        val session = StoredGroupChatSession(LocalDate.of(2026, 10, 22), emptyList(), listOf(
            ArrangementCandidate("任务", TaskCategory.COURSE, 90, ArrangementTimeHint(1400), emptySet()),
        ))
        assertNull(GroupChatHistoryCodec.decode(GroupChatHistoryCodec.encode(session)))
    }

    @Test fun `recent history is bounded and keeps the latest user message`() {
        val session = StoredGroupChatSession(LocalDate.of(2026, 10, 22), (0..90).map {
            GroupChatMessage(GroupChatMessageRole.USER, "消息$it")
        })
        val decoded = requireNotNull(GroupChatHistoryCodec.decode(GroupChatHistoryCodec.encode(session)))
        assertEquals(60, decoded.messages.size)
        assertEquals("消息90", decoded.messages.last().text)
    }
}
