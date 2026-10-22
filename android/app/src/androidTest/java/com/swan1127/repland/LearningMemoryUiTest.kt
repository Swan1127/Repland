package com.swan1127.repland

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.ReviewPoint
import com.swan1127.repland.domain.model.MemoryModel
import com.swan1127.repland.domain.model.RecallGrade
import com.swan1127.repland.ui.components.LearningMemorySettings
import com.swan1127.repland.ui.theme.ReplandTheme
import com.swan1127.repland.ui.tools.ReviewPointsTool
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic component tests; no user's SharedPreferences or app database is opened. */
@RunWith(AndroidJUnit4::class)
class LearningMemoryUiTest {
    @get:Rule val rule = createComposeRule()
    private val day = LocalDate.of(2026, 10, 22)
    private val point = ReviewPoint(
        "point", "task", "英语复习", "remember 与 recall 的区别", day.minusDays(2), 0,
        MemoryModel.seed(3, day.minusDays(2)),
    )

    private fun show(onGrade: suspend (String, RecallGrade) -> Boolean) {
        rule.setContent {
            ReplandTheme {
                ReviewPointsTool(listOf(point), 0, onGrade, onClose = {}, today = day)
            }
        }
    }

    private fun assertGrade(tag: String, expected: RecallGrade) {
        val submitted = mutableListOf<Pair<String, RecallGrade>>()
        show { id, grade -> submitted += id to grade; true }
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        rule.onNodeWithText("已记录本次回忆反馈").assertExists()
        assertEquals(listOf("point" to expected), submitted)
    }

    @Test fun rememberedFeedbackUsesTheRecalledGrade() = assertGrade("review-remembered", RecallGrade.RECALLED)
    @Test fun fuzzyFeedbackUsesItsOwnGrade() = assertGrade("review-fuzzy", RecallGrade.FUZZY)
    @Test fun forgottenFeedbackUsesTheForgotGrade() = assertGrade("review-forgotten", RecallGrade.FORGOT)

    @Test fun pendingFeedbackCannotBeSubmittedTwiceOrReportedAsSaved() {
        val finish = CompletableDeferred<Boolean>()
        var calls = 0
        show { _, _ -> calls++; finish.await() }
        rule.onNodeWithTag("review-fuzzy").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { click -> click(); click() }
        rule.onNodeWithTag("review-fuzzy").assertIsNotEnabled()
        rule.onNodeWithTag("review-remembered").assertIsNotEnabled()
        rule.onNodeWithTag("review-forgotten").assertIsNotEnabled()
        rule.onNodeWithTag("review-grade-saving").assertExists()
        rule.onNodeWithText("已记录本次回忆反馈").assertDoesNotExist()
        assertEquals(1, calls)
        rule.runOnIdle { finish.complete(true) }
        rule.onNodeWithText("已记录本次回忆反馈").assertExists()
    }

    @Test fun failedFeedbackKeepsThePointAndAllowsRetry() {
        var shouldSave = false
        var calls = 0
        show { _, _ -> calls++; shouldSave }
        rule.onNodeWithTag("review-fuzzy").performScrollTo().performClick()
        rule.onNodeWithTag("review-grade-error").assertExists()
        rule.onNodeWithText("已记录本次回忆反馈").assertDoesNotExist()
        rule.onNodeWithText(point.content).assertExists()
        rule.runOnIdle { shouldSave = true }
        rule.onNodeWithTag("review-fuzzy").performScrollTo().performClick()
        rule.onNodeWithText("已记录本次回忆反馈").assertExists()
        rule.onNodeWithTag("review-grade-error").assertDoesNotExist()
        assertEquals(2, calls)
    }

    @Test fun aNewDayAllowsADuePointToBeReviewedAgain() {
        val today = mutableStateOf(day)
        var calls = 0
        rule.setContent {
            ReplandTheme {
                ReviewPointsTool(listOf(point), 0, { _, _ -> calls++; true }, onClose = {}, today = today.value)
            }
        }
        rule.onNodeWithTag("review-remembered").performScrollTo().performClick()
        rule.onNodeWithText("已记录本次回忆反馈").assertExists()
        rule.runOnIdle { today.value = day.plusDays(1) }
        rule.onNodeWithTag("review-fuzzy").performScrollTo().performClick()
        assertEquals(2, calls)
    }

    @Test fun memoryReferenceIsAnExplicitAccessibleSwitchAtLargeFont() {
        val enabled = mutableStateOf(false)
        var changes = 0
        rule.setContent {
            ReplandTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    LearningMemorySettings(enabled.value, { enabled.value = it; changes++ }, pointCount = 0)
                }
            }
        }
        rule.onNodeWithTag("learning-memory-reference-switch").assertIsOff().performClick()
        rule.onNodeWithTag("learning-memory-reference-switch").assertIsOn()
        assertEquals(1, changes)
    }

    @Test fun dataResetCanDisableReferenceChanges() {
        var changes = 0
        rule.setContent {
            ReplandTheme {
                LearningMemorySettings(false, { changes++ }, pointCount = 2, interactive = false)
            }
        }
        rule.onNodeWithTag("learning-memory-reference-switch").assertIsOff().assertIsNotEnabled()
        assertEquals(0, changes)
    }
}
