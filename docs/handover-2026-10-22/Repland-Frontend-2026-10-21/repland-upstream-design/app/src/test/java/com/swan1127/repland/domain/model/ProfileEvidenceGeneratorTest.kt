package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProfileEvidenceGenerator] writes the "已确认记录…" conclusions the profile screen shows.
 * The two rules that matter are both about *what must not be counted*: a log the user has
 * since corrected, and a replacement row. Counting either one inflates a conclusion the
 * user is told is traceable back to their own confirmed logs.
 */
class ProfileEvidenceGeneratorTest {

    // ---------------------------------------------------------------- correction semantics

    @Test
    fun `a corrected log is not counted and its correction is`() {
        val original = executionLog("l1", "t1", feedback = feedback(actualDurationMinutes = 30))
        val correction = executionLog(
            "l2", "t1",
            eventType = ExecutionLogEventType.CORRECTION,
            feedback = feedback(actualDurationMinutes = 45),
            correctedLogId = "l1",
        )
        val evidence = ProfileEvidenceGenerator.generate(listOf(task("t1")), listOf(original, correction))
        val general = evidence.single { it.scope == ProfileEvidenceScope.GENERAL }
        assertEquals(listOf("l2"), general.sourceLogIds)
        assertTrue(general.conclusion.startsWith("已确认记录 1 条执行反馈"))
    }

    @Test
    fun `a replacement event is never counted`() {
        val replacement = executionLog(
            "l1", "t1",
            eventType = ExecutionLogEventType.REPLACEMENT,
            feedback = feedback(actualDurationMinutes = 30, progressPercent = 50),
        )
        assertTrue(ProfileEvidenceGenerator.generate(listOf(task("t1")), listOf(replacement)).isEmpty())
    }

    @Test
    fun `a log with no confirmed feedback produces no evidence`() {
        val empty = executionLog("l1", "t1", eventType = ExecutionLogEventType.STATUS_CHANGE)
        assertTrue(ProfileEvidenceGenerator.generate(listOf(task("t1")), listOf(empty)).isEmpty())
    }

    @Test
    fun `a blank feedback string is not treated as confirmed feedback`() {
        val blank = executionLog("l1", "t1", feedback = feedback(completedContent = "   "))
        assertTrue(ProfileEvidenceGenerator.generate(listOf(task("t1")), listOf(blank)).isEmpty())
    }

    // ---------------------------------------------------------------- general evidence

    @Test
    fun `general evidence reports the confirmed count and the summed duration`() {
        val first = executionLog("l1", "t1", feedback = feedback(actualDurationMinutes = 30, progressPercent = 50))
        val second = executionLog("l2", "t2", feedback = feedback(actualDurationMinutes = 45, progressPercent = 20))
        val general = ProfileEvidenceGenerator.generate(listOf(task("t1"), task("t2")), listOf(first, second))
            .single { it.scope == ProfileEvidenceScope.GENERAL }
        assertEquals(
            "已确认记录 2 条执行反馈；其中 2 次实际投入共 75 分钟；覆盖 2 个任务的进度；不据此推断效率或能力。",
            general.conclusion,
        )
        assertEquals(listOf("l1", "l2"), general.sourceLogIds)
    }

    @Test
    fun `general evidence omits the duration fact when no duration was recorded`() {
        val progressOnly = executionLog("l1", "t1", feedback = feedback(progressPercent = 40))
        val general = ProfileEvidenceGenerator.generate(listOf(task("t1")), listOf(progressOnly))
            .single { it.scope == ProfileEvidenceScope.GENERAL }
        assertEquals(
            "已确认记录 1 条执行反馈；覆盖 1 个任务的进度；不据此推断效率或能力。",
            general.conclusion,
        )
    }

    @Test
    fun `the progress fact counts distinct tasks not logs`() {
        // Two logs on the same task are one task of progress.
        val first = executionLog("l1", "t1", feedback = feedback(progressPercent = 20))
        val second = executionLog("l2", "t1", feedback = feedback(progressPercent = 60))
        val general = ProfileEvidenceGenerator.generate(listOf(task("t1")), listOf(first, second))
            .single { it.scope == ProfileEvidenceScope.GENERAL }
        assertTrue(general.conclusion.contains("覆盖 1 个任务的进度"))
    }

    @Test
    fun `every source log id in the general conclusion is an effective log`() {
        val corrected = executionLog("l1", "t1", feedback = feedback(progressPercent = 10))
        val correction = executionLog(
            "l2", "t1",
            eventType = ExecutionLogEventType.CORRECTION,
            feedback = feedback(progressPercent = 80),
            correctedLogId = "l1",
        )
        val general = ProfileEvidenceGenerator.generate(listOf(task("t1")), listOf(corrected, correction))
            .single { it.scope == ProfileEvidenceScope.GENERAL }
        assertFalse(general.sourceLogIds.contains("l1"))
    }

    // ---------------------------------------------------------------- learning evidence

    @Test
    fun `learning evidence is produced for a course task with confirmed content`() {
        val log = executionLog("l1", "t1", feedback = feedback(completedContent = "读完了第一章"))
        val evidence = ProfileEvidenceGenerator.generate(listOf(task("t1")), listOf(log))
        assertEquals(
            listOf(ProfileEvidenceScope.GENERAL, ProfileEvidenceScope.LEARNING),
            evidence.map(ProfileEvidenceDraft::scope),
        )
        val learning = evidence.single { it.scope == ProfileEvidenceScope.LEARNING }
        assertEquals(
            "已确认 1 条课程学习内容或结果反馈；学习结论可逐条回溯到来源执行日志。",
            learning.conclusion,
        )
        assertEquals(listOf("l1"), learning.sourceLogIds)
    }

    @Test
    fun `learning evidence is produced from a completion result too`() {
        val log = executionLog("l1", "t1", feedback = feedback(completionResult = "测验 82 分"))
        val evidence = ProfileEvidenceGenerator.generate(listOf(task("t1")), listOf(log))
        assertTrue(evidence.any { it.scope == ProfileEvidenceScope.LEARNING })
    }

    @Test
    fun `a non-course task never produces learning evidence`() {
        val log = executionLog("l1", "t1", feedback = feedback(completedContent = "整理报销"))
        val evidence = ProfileEvidenceGenerator.generate(
            listOf(task("t1", category = TaskCategory.OFFICE)),
            listOf(log),
        )
        assertEquals(listOf(ProfileEvidenceScope.GENERAL), evidence.map(ProfileEvidenceDraft::scope))
    }

    @Test
    fun `a course log with no content produces no learning evidence`() {
        val log = executionLog("l1", "t1", feedback = feedback(actualDurationMinutes = 30))
        val evidence = ProfileEvidenceGenerator.generate(listOf(task("t1")), listOf(log))
        assertEquals(listOf(ProfileEvidenceScope.GENERAL), evidence.map(ProfileEvidenceDraft::scope))
    }

    @Test
    fun `a course log whose task was deleted produces no learning evidence`() {
        val log = executionLog("l1", "gone", feedback = feedback(completedContent = "读完了第一章"))
        val evidence = ProfileEvidenceGenerator.generate(emptyList(), listOf(log))
        assertEquals(listOf(ProfileEvidenceScope.GENERAL), evidence.map(ProfileEvidenceDraft::scope))
    }

    // ---------------------------------------------------------------- conclusion validator

    @Test
    fun `a conclusion must be non-blank and within the length limit`() {
        assertFalse(ProfileEvidenceValidator.isValidConclusion(""))
        assertFalse(ProfileEvidenceValidator.isValidConclusion("   "))
        assertTrue(ProfileEvidenceValidator.isValidConclusion("x"))
        assertTrue(ProfileEvidenceValidator.isValidConclusion("x".repeat(ProfileEvidenceValidator.MAX_CONCLUSION_LENGTH)))
        assertFalse(ProfileEvidenceValidator.isValidConclusion("x".repeat(ProfileEvidenceValidator.MAX_CONCLUSION_LENGTH + 1)))
    }

    @Test
    fun `the length limit measures the trimmed text`() {
        val padded = " " + "x".repeat(ProfileEvidenceValidator.MAX_CONCLUSION_LENGTH) + " "
        assertTrue(ProfileEvidenceValidator.isValidConclusion(padded))
    }
}
