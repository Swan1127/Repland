package com.swan1127.repland

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.planteam.PlanRequestKind
import com.swan1127.repland.domain.planteam.runtime.AcceptanceFinished
import com.swan1127.repland.domain.planteam.runtime.TeamEvent
import com.swan1127.repland.domain.planteam.runtime.TeamStarted
import com.swan1127.repland.ui.plan.PlanningTeamLogPanel
import com.swan1127.repland.ui.plan.PlanningTeamStatusDialog
import com.swan1127.repland.ui.theme.ReplandTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanningTeamLogUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun realEventsCanBeExpandedAndStaleLogsDisappearWhenCleared() {
        val started = TeamStarted(1, 1L, "synthetic-run", PlanRequestKind.REPAIR,
            LocalDate.of(2026, 10, 22), emptyList(), 0, null)
        val events = mutableStateOf<List<TeamEvent>>(listOf(started, AcceptanceFinished(2, 2L, emptyList(), false)))
        rule.setContent { ReplandTheme { PlanningTeamLogPanel(events.value) } }
        rule.onNodeWithText("存在未通过的验收，请查看记录").assertExists()
        rule.onNodeWithText("验收通过，仍需你确认应用").assertDoesNotExist()
        rule.onNodeWithText(started.summary).assertDoesNotExist()
        rule.onNodeWithTag("team-log-expand").performClick()
        rule.onNodeWithText(started.summary).assertExists()
        rule.runOnIdle { events.value = emptyList() }
        rule.onNodeWithTag("team-log").assertDoesNotExist()
    }

    @Test fun cancellingTheTeamDialogRequestsCancellationExactlyOnce() {
        var cancels = 0
        var retries = 0
        rule.setContent {
            ReplandTheme {
                PlanningTeamStatusDialog(true, emptyList(), null, { cancels++ }, { retries++ })
            }
        }
        rule.onNodeWithText("取消").performClick()
        assertEquals(1, cancels)
        assertEquals(0, retries)
        rule.onNodeWithText("重新生成").assertDoesNotExist()
    }
}
