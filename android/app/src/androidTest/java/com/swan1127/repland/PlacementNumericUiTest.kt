package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.agent.AgentCenterScreen
import com.swan1127.repland.ui.schedule.TimelineDashboard
import com.swan1127.repland.ui.theme.ReplandTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PlacementNumericUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun event_minutes_are_individually_validated_without_carrying_or_rewriting() {
        var writes = 0
        var minute = -1
        rule.setContent { ReplandTheme { TimelineDashboard(
            entries = emptyList(), mode = EngagementMode.GUIDED, onOpenEntry = {}, onOpenTask = {},
            eventObjects = listOf(com.swan1127.repland.ui.schedule.TimelineEventObject("raw29", "原始时刻", 30)),
            onPlaceEvent = { _, _, start -> writes++; minute = start },
        ) } }
        rule.onNodeWithTag("empty-event-library-trigger").performClick()
        rule.onNodeWithTag("event-object-raw29").performClick()
        listOf("99", "-30", "+30", "3.5", "999999999999", "60").forEach { raw ->
            rule.onNodeWithTag("event-minute-input").performTextReplacement(raw)
            rule.onNodeWithTag("event-minute-input").assertTextContains(raw)
            rule.onNodeWithTag("event-place-confirm").assertIsNotEnabled().performClick()
            assertEquals(0, writes)
        }
        rule.onNodeWithTag("event-minute-input").performTextReplacement("00")
        rule.onNodeWithTag("event-hour-input").performTextReplacement("-8")
        rule.onNodeWithTag("event-hour-input").assertTextContains("-8")
        rule.onNodeWithTag("event-place-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("event-hour-input").performTextReplacement("08")
        rule.onNodeWithTag("event-place-confirm").assertIsEnabled().performClick()
        assertEquals(1, writes); assertEquals(480, minute)
    }

    @Test fun course_duration_is_not_clamped_into_validity_and_clock_parts_do_not_carry() {
        var writes = 0
        var duration = -1
        rule.setContent { ReplandTheme { TimelineDashboard(
            entries = listOf(TimelineEntry("course", "原课程", TimelineKind.COURSE, LocalDate.now(), 540, 600)),
            mode = EngagementMode.GUIDED, onOpenEntry = {}, onOpenTask = {},
            onCreateCourse = { writes++; duration = it.durationMinutes },
        ) } }
        rule.onNodeWithTag("add-course-trigger").performClick()
        rule.onNodeWithTag("course-title-input").performTextReplacement("数字边界")
        rule.onNodeWithText("手动设时间").performClick()
        listOf("0", "14", "361", "-30", "+30", "3.5", "999999999999").forEach { raw ->
            rule.onNodeWithTag("course-duration-input").performTextReplacement(raw)
            rule.onNodeWithTag("course-duration-input").assertTextContains(raw)
            rule.onNodeWithText("保存课程").assertIsNotEnabled().performClick()
            assertEquals(0, writes)
        }
        rule.onNodeWithTag("course-duration-input").performTextReplacement("30")
        rule.onNodeWithText("分").performTextReplacement("99")
        rule.onNodeWithText("保存课程").assertIsNotEnabled()
        rule.onNodeWithText("分").performTextReplacement("00")
        rule.onNodeWithText("时").performTextReplacement("23")
        rule.onNodeWithText("分").performTextReplacement("45")
        rule.onNodeWithText("保存课程").assertIsNotEnabled()
        rule.onNodeWithText("时").performTextReplacement("08")
        rule.onNodeWithText("分").performTextReplacement("00")
        rule.onNodeWithText("保存课程").assertIsEnabled().performClick()
        assertEquals(1, writes); assertEquals(30, duration)
    }

    @Test fun assistant_clock_raw_input_survives_saved_state_restore_without_changing_proposal() {
        val original = AssistantTaskProposal("raw29", "助手原始数字", TaskCategory.COURSE, 30,
            ArrangementTimeHint(600), emptySet(), ArrangementPlacementSource.USER_EXPLICIT)
        var workspace: AssistantWorkspace? = null
        var saves = 0
        val restoration = StateRestorationTester(rule)
        restoration.setContent { ReplandTheme { AgentCenterScreen(
            initialWorkspace = AssistantWorkspace(LocalDate.now(), "助手原始数字", listOf(original)),
            onWorkspaceChanged = { workspace = it }, onSaveTasks = { saves++ },
            onPlaceTask = { _, _, _, _ -> }, onOpenTimeStudio = {},
        ) } }
        rule.onNodeWithTag("agent-confirm-tasks").performScrollTo()
        // The timeline tag includes positioning padding. Tap the rendered title,
        // not the center of that larger semantics rectangle.
        rule.onNode(hasText("助手原始数字") and hasAnyAncestor(hasTestTag("agent-proposal-raw29")), useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("时").performTextReplacement("-9")
        rule.onNodeWithText("时").assertTextContains("-9")
        rule.onNodeWithText("更新草案").assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("时").assertTextContains("-9")
        rule.onNodeWithText("更新草案").assertIsNotEnabled()
        assertEquals(listOf(original), workspace!!.proposals); assertEquals(0, saves)
        rule.onNodeWithText("时").performTextReplacement("09")
        rule.onNodeWithText("分").performTextReplacement("99")
        rule.onNodeWithText("更新草案").assertIsNotEnabled()
        rule.onNodeWithText("分").performTextReplacement("00")
        rule.onNodeWithText("分钟").performTextReplacement("3.5")
        rule.onNodeWithText("分钟").assertTextContains("3.5")
        rule.onNodeWithText("更新草案").assertIsNotEnabled()
        rule.onNodeWithText("分钟").performTextReplacement("45")
        rule.onNodeWithText("更新草案").assertIsEnabled().performClick()
        assertEquals(540, workspace!!.proposals.single().timeHint.explicitStartMinute)
        assertEquals(45, workspace!!.proposals.single().durationMinutes)
        assertEquals(original.id, workspace!!.proposals.single().id); assertEquals(0, saves)
    }
}
