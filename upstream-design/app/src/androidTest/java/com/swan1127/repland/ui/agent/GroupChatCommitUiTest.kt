package com.swan1127.repland.ui.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.data.GroupChatHistoryStore
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.theme.ReplandTheme
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GroupChatCommitUiTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.now()
    private val store get() = GroupChatHistoryStore(InstrumentationRegistry.getInstrumentation().targetContext)
    private val chat = object : AgentChatModel {
        override suspend fun say(systemPrompt: String, userPrompt: String) = AgentChatResult.Said("无需发起网络请求")
    }

    @Before fun seed() {
        clearGroupChatSession()
        store.clear()
        store.save(StoredGroupChatSession(date, emptyList(), listOf(ArrangementCandidate(
            title = "复习数学", category = TaskCategory.COURSE, durationMinutes = 30,
            timeHint = ArrangementTimeHint(explicitStartMinute = 600), needsClarification = emptySet(),
        ))))
    }
    @After fun clean() { clearGroupChatSession(); store.clear() }

    @Test fun failed_commit_retains_editable_draft() {
        var dismissed = false
        compose.setContent { ReplandTheme {
            GroupChatSheet(date, emptyList(), false, chat,
                onRefineWithAi = { _, _, _ -> ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.DISABLED) },
                onCommitArrangement = { _, _ -> false }, onDismiss = { dismissed = true })
        } }
        compose.onNodeWithTag("group-confirm").performClick()
        compose.onNodeWithText("保存失败，草案已保留，请重试。").assertIsDisplayed()
        compose.onNodeWithTag("group-confirm").assertIsDisplayed()
        assertFalse(dismissed)
        assertEquals(1, store.load(date)?.pendingCandidates?.size)
    }

    @Test fun successful_commit_clears_pending_draft_before_closing() {
        var dismissed = false
        var counts: Pair<Int, Int>? = null
        compose.setContent { ReplandTheme {
            GroupChatSheet(date, emptyList(), false, chat,
                onRefineWithAi = { _, _, _ -> ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.DISABLED) },
                onCommitArrangement = { tasks, placements -> counts = tasks.size to placements.size; true },
                onDismiss = { dismissed = true })
        } }
        compose.onNodeWithTag("group-confirm").performClick()
        compose.waitUntil { dismissed }
        assertEquals(1 to 1, counts)
        assertTrue(store.load(date)?.pendingCandidates.orEmpty().isEmpty())
    }

    @Test fun conflict_reason_keeps_the_draft_available_for_correction() {
        val reason = "安排与已有课程、休息或锁定时段冲突，请调整草案后重试。"
        compose.setContent { ReplandTheme {
            GroupChatSheet(date, emptyList(), false, chat,
                onRefineWithAi = { _, _, _ -> ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.DISABLED) },
                onCommitArrangement = { _, _ -> false }, onDismiss = {}, commitFailureMessage = reason)
        } }
        compose.onNodeWithTag("group-confirm").performClick()
        compose.onNodeWithText(reason).assertIsDisplayed()
        assertEquals(1, store.load(date)?.pendingCandidates?.size)
    }
}
