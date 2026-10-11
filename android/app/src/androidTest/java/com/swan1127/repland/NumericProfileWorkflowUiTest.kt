package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.swan1127.repland.data.room.RoomNumericProfileRepository
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class NumericProfileWorkflowUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { java.io.File(rule.activity.getExternalFilesDir(null),name).outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        } } finally { bitmap.recycle() }
    }
    private fun waitTag(tag: String)=rule.waitUntil(20000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun visible(tag: String): SemanticsNodeInteraction {
        waitTag(tag); return rule.onNodeWithTag(tag).also { runCatching { it.performScrollTo() } }.assertIsDisplayed()
    }
    @Test fun actual_source_exclusion_invalid_total_confirmation_advice_cancel_adoption_and_recreation_preserve_plan() {
        check(rule.activity.packageName=="com.swan1127.repland.qa")
        val app=(rule.activity.application as ReplandApplication).appContainer
        // Keep older fixture history without depending on random order in page one.
        val prefix="000-000-numeric-${Long.MAX_VALUE-System.currentTimeMillis()}-${UUID.randomUUID()}"; val ids=(1..3).map { "$prefix-$it" }; val target="$prefix-target"
        runBlocking {
            app.numericProfileRepository.setEnabled(true); app.numericProfileRepository.setParameterEnabled("estimate:LEISURE",true)
            for(id in ids) {
                app.taskRepository.save(TaskDraft(id=id,displayName="画像样本${id.takeLast(1)}",category=TaskCategory.LEISURE,totalDurationMinutes=20))
                app.taskRepository.confirmStatus(id,TaskStatus.IN_PROGRESS); app.taskRepository.confirmStatus(id,TaskStatus.COMPLETED)
                val task=app.taskRepository.observeTasks().first().single { it.id==id }
                app.numericProfileRepository.confirmActualTotal(id,30,RoomNumericProfileRepository.taskRevision(task,app.taskRepository.observeExecutionLogs(id).first()))
            }
            app.taskRepository.save(TaskDraft(id=target,displayName="画像估时目标",category=TaskCategory.LEISURE,totalDurationMinutes=20))
        }
        val before=runBlocking { app.planRepository.observeCurrentPlan().first() }
        try {
            visible("navigation-mine").performClick(); visible("numeric-profile-open").performClick()
            visible("numeric-value-estimate:LEISURE").assertTextContains("1.5",substring=true)
            capture("numeric-profile-values.png")
            visible("numeric-exclude-task:${ids.first()}").performClick()
            rule.waitUntil(20000) { runBlocking { app.numericProfileRepository.refresh().parameters.single { it.id=="estimate:LEISURE" }.count==2 } }
            // Repository commit and the lifecycle-collected Compose snapshot are
            // separate emissions. Assert the actual refreshed page, not its old frame.
            rule.waitUntil(20000) { rule.onAllNodes(hasTestTag("numeric-value-estimate:LEISURE") and
                hasText("2 个有效样本 · 仅供参考",substring=true)).fetchSemanticsNodes().isNotEmpty() }
            visible("numeric-value-estimate:LEISURE").assertTextContains("仅供参考",substring=true)
            visible("numeric-exclude-task:${ids.first()}").performClick()
            rule.waitUntil(20000) { runBlocking { app.numericProfileRepository.refresh().parameters.single { it.id=="estimate:LEISURE" }.count==3 } }
            rule.waitUntil(20000) { rule.onAllNodes(hasTestTag("numeric-value-estimate:LEISURE") and
                hasText("3 个有效样本 · 可用于建议",substring=true)).fetchSemanticsNodes().isNotEmpty() }
            visible("numeric-edit-${ids.first()}").performClick()
            visible("numeric-total-input").performTextReplacement("-1")
            visible("numeric-confirm-total").performClick(); waitTag("numeric-total-error")
            capture("numeric-profile-total-error.png")
            assertEquals(1.5,runBlocking { app.numericProfileRepository.refresh().parameters.single { it.id=="estimate:LEISURE" }.value!! },0.001)
            visible("numeric-total-input").performTextReplacement("30"); visible("numeric-confirm-total").performClick()
            rule.waitUntil(20000) { rule.onAllNodesWithTag("numeric-total-input").fetchSemanticsNodes().isEmpty() }
            visible("numeric-advice-$target").performClick(); capture("numeric-profile-advice.png"); visible("numeric-cancel-advice").performClick()
            assertEquals(20,runBlocking { app.taskRepository.observeTasks().first().single { it.id==target }.totalDurationMinutes })
            visible("numeric-advice-$target").performClick(); visible("numeric-accept-advice").performClick()
            rule.waitUntil(20000) { runBlocking { app.taskRepository.observeTasks().first().single { it.id==target }.totalDurationMinutes==30 } }
            rule.activityRule.scenario.recreate(); waitTag("numeric-close")
            visible("numeric-value-estimate:LEISURE").assertTextContains("1.5",substring=true)
            assertEquals(before,runBlocking { app.planRepository.observeCurrentPlan().first() })
            assertTrue(runBlocking { app.taskRepository.observeExecutionLogs(target).first() }.isEmpty())
            visible("numeric-close").performClick()
        } catch(failure: Throwable) {
            runCatching { capture("numeric-profile-failure.png") }
            throw failure
        } finally { runBlocking { for(id in ids) app.numericProfileRepository.clearActualTotal(id) } }
    }
}
