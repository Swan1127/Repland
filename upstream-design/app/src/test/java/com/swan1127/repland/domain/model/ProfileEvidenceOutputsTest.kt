package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProfileEvidenceOutputs] decides which of the user's tool writing appears next to a profile
 * evidence row.
 *
 * The rule is deliberately narrow, so almost every test here is about what must *not* be shown:
 * writing for a task the row does not cite, a note the user never actually wrote (blank), the same
 * sentence saved twice, and — the load-bearing one — anything at all being counted. The rule never
 * touches a conclusion or a source, and `attach` is asserted to leave every other field alone,
 * because the whole point of keeping tool outputs out of [ProfileEvidenceSource] is that they are
 * not part of what the conclusion counts.
 */
class ProfileEvidenceOutputsTest {

    // ------------------------------------------------------------------ matching

    @Test
    fun `writing about a task the row cites is shown under that task's name`() {
        val related = ProfileEvidenceOutputs.related(
            sources = listOf(source("t1", name = "泰勒展开")),
            records = listOf(record(ToolOutputKind.KWL, "t1", "余项为什么收敛")),
        )

        val output = related.shown.single()
        assertEquals(ToolOutputKind.KWL, output.kind)
        assertEquals("t1", output.taskId)
        assertEquals("泰勒展开", output.taskDisplayName)
        assertEquals("余项为什么收敛", output.excerpt)
        assertEquals(1, related.total)
        assertEquals(0, related.hiddenCount)
    }

    @Test
    fun `writing about a task the row does not cite is left out`() {
        val related = ProfileEvidenceOutputs.related(
            sources = listOf(source("t1")),
            records = listOf(
                record(ToolOutputKind.KWL, "t1", "属于这一行"),
                record(ToolOutputKind.KWL, "t2", "属于别的任务"),
            ),
        )

        assertEquals(listOf("属于这一行"), related.shown.map { it.excerpt })
        assertEquals(1, related.total)
    }

    @Test
    fun `a row with no sources has no related writing even when writing exists`() {
        // The aggregate row can end up with an empty source list (every log it cited was deleted),
        // and then there is no task to match on. Showing all of the user's writing would be worse
        // than showing none: it would attach to a row that no longer stands for anything.
        val related = ProfileEvidenceOutputs.related(
            sources = emptyList(),
            records = listOf(record(ToolOutputKind.KWL, "t1", "随便写的")),
        )

        assertEquals(RelatedToolOutputs.EMPTY, related)
        assertTrue(related.isEmpty)
    }

    @Test
    fun `every tool kind can be attached`() {
        // Anti-vacuity: a rule that silently dropped one kind would still pass every single-kind
        // test written for the other two, so all of them are swept here.
        ToolOutputKind.entries.forEach { kind ->
            val related = ProfileEvidenceOutputs.related(
                sources = listOf(source("t1")),
                records = listOf(record(kind, "t1", "一句话")),
            )
            assertEquals("kind $kind was not matched", 1, related.total)
        }
    }

    // ------------------------------------------------------------- what is not writing

    @Test
    fun `a blank excerpt is not writing`() {
        // The KWL tool saves a row as soon as "学到了" is edited, so a blank one is the normal
        // state of a half-filled tool, not an edge case. Listing it would pad the count with
        // nothing and make the section look like it had content it does not have.
        val related = ProfileEvidenceOutputs.related(
            sources = listOf(source("t1")),
            records = listOf(
                record(ToolOutputKind.KWL, "t1", ""),
                record(ToolOutputKind.KWL, "t1", "   "),
                record(ToolOutputKind.KWL, "t1", "真的有内容"),
            ),
        )

        assertEquals(listOf("真的有内容"), related.shown.map { it.excerpt })
        assertEquals(1, related.total)
    }

    @Test
    fun `the same sentence saved twice with different spacing is listed once`() {
        // A sentence re-saved from a restored backup can carry surrounding whitespace. The
        // comparison is on trimmed text so the user does not see their own sentence twice.
        val related = ProfileEvidenceOutputs.related(
            sources = listOf(source("t1")),
            records = listOf(
                record(ToolOutputKind.SELF_EXPLANATION, "t1", "  同一个句子  "),
                record(ToolOutputKind.SELF_EXPLANATION, "t1", "同一个句子"),
            ),
        )

        assertEquals(listOf("同一个句子"), related.shown.map { it.excerpt })
        assertEquals(1, related.total)
    }

    @Test
    fun `the same sentence from two different tools is two entries`() {
        // Deduplication is on (kind, task, text), not on text alone: the same words written in KWL
        // and in the self-explanation box are two different things the user did.
        val related = ProfileEvidenceOutputs.related(
            sources = listOf(source("t1")),
            records = listOf(
                record(ToolOutputKind.KWL, "t1", "同一个句子"),
                record(ToolOutputKind.SELF_EXPLANATION, "t1", "同一个句子"),
            ),
        )

        assertEquals(2, related.total)
    }

    // -------------------------------------------------------------------- ordering

    @Test
    fun `entries are ordered by kind, then task, then excerpt`() {
        val related = ProfileEvidenceOutputs.related(
            sources = listOf(source("t1", "甲"), source("t2", "乙")),
            records = listOf(
                record(ToolOutputKind.SELF_EXPLANATION, "t2", "zzz"),
                record(ToolOutputKind.KWL, "t1", "bbb"),
                record(ToolOutputKind.KWL, "t1", "aaa"),
            ),
        )

        assertEquals(
            listOf(
                Triple(ToolOutputKind.KWL, "t1", "aaa"),
                Triple(ToolOutputKind.KWL, "t1", "bbb"),
                Triple(ToolOutputKind.SELF_EXPLANATION, "t2", "zzz"),
            ),
            related.shown.map { Triple(it.kind, it.taskId, it.excerpt) },
        )
    }

    @Test
    fun `the order does not depend on the order the records arrive in`() {
        val forwards = listOf(
            record(ToolOutputKind.KWL, "t1", "aaa"),
            record(ToolOutputKind.REVIEW_POINT, "t1", "bbb"),
        )
        val backwards = forwards.reversed()

        assertEquals(
            ProfileEvidenceOutputs.related(listOf(source("t1")), forwards).shown,
            ProfileEvidenceOutputs.related(listOf(source("t1")), backwards).shown,
        )
    }

    // ------------------------------------------------------------------- the cap

    @Test
    fun `the list is capped while the total still counts everything`() {
        val records = (1..7).map { record(ToolOutputKind.KWL, "t1", "第 $it 条") }

        val related = ProfileEvidenceOutputs.related(sources = listOf(source("t1")), records = records)

        assertEquals(ProfileEvidenceOutputs.MAX_SHOWN, related.shown.size)
        assertEquals(7, related.total)
        assertEquals(7 - ProfileEvidenceOutputs.MAX_SHOWN, related.hiddenCount)
    }

    @Test
    fun `an explicit limit is honoured and the total ignores it`() {
        val records = (1..4).map { record(ToolOutputKind.KWL, "t1", "第 $it 条") }

        val related = ProfileEvidenceOutputs.related(
            sources = listOf(source("t1")),
            records = records,
            limit = 2,
        )

        assertEquals(2, related.shown.size)
        assertEquals(4, related.total)
        assertEquals(2, related.hiddenCount)
    }

    @Test
    fun `a limit below one is refused rather than silently showing nothing`() {
        // `take(0)` would return an empty list while `total` stayed positive — a card that says
        // "还有 3 条" above nothing. The refusal makes that state unrepresentable.
        assertThrows(IllegalArgumentException::class.java) {
            ProfileEvidenceOutputs.related(
                sources = listOf(source("t1")),
                records = listOf(record(ToolOutputKind.KWL, "t1", "一句话")),
                limit = 0,
            )
        }
    }

    @Test
    fun `the hidden count never goes negative`() {
        val shown = ProfileEvidenceToolOutput(ToolOutputKind.KWL, "t1", "任务", "一句话")

        assertEquals(0, RelatedToolOutputs(shown = listOf(shown), total = 0).hiddenCount)
        assertEquals(0, RelatedToolOutputs.EMPTY.hiddenCount)
        assertEquals(0, RelatedToolOutputs.EMPTY.total)
        assertTrue(RelatedToolOutputs.EMPTY.isEmpty)
    }

    // --------------------------------------------------------------------- attach

    @Test
    fun `attach decorates only the derived field`() {
        val original = evidence(
            id = "e1",
            scope = ProfileEvidenceScope.LEARNING,
            conclusion = "已确认 2 条课程学习内容或结果反馈",
            sources = listOf(source("t1", "甲")),
            isUserEdited = true,
        )

        val attached = ProfileEvidenceOutputs.attach(
            evidence = listOf(original),
            records = listOf(record(ToolOutputKind.REVIEW_POINT, "t1", "复习这句话")),
        ).single()

        assertEquals(original.id, attached.id)
        assertEquals(original.scope, attached.scope)
        assertEquals(original.conclusion, attached.conclusion)
        assertEquals(original.sources, attached.sources)
        assertEquals(original.createdAtEpochMillis, attached.createdAtEpochMillis)
        assertEquals(original.updatedAtEpochMillis, attached.updatedAtEpochMillis)
        assertEquals(original.isUserEdited, attached.isUserEdited)
        assertEquals(listOf("复习这句话"), attached.relatedToolOutputs.shown.map { it.excerpt })
    }

    @Test
    fun `attach gives each row only its own tasks' writing`() {
        val attached = ProfileEvidenceOutputs.attach(
            evidence = listOf(
                evidence(id = "e1", sources = listOf(source("t1", "甲"))),
                evidence(id = "e2", sources = listOf(source("t2", "乙"))),
            ),
            records = listOf(
                record(ToolOutputKind.KWL, "t1", "甲的话"),
                record(ToolOutputKind.KWL, "t2", "乙的话"),
            ),
        )

        assertEquals(listOf("甲的话"), attached.first { it.id == "e1" }.relatedToolOutputs.shown.map { it.excerpt })
        assertEquals(listOf("乙的话"), attached.first { it.id == "e2" }.relatedToolOutputs.shown.map { it.excerpt })
    }

    @Test
    fun `attach on no evidence is no evidence`() {
        assertTrue(
            ProfileEvidenceOutputs.attach(
                evidence = emptyList(),
                records = listOf(record(ToolOutputKind.KWL, "t1", "一句话")),
            ).isEmpty(),
        )
    }

    @Test
    fun `evidence that has never been attached carries an empty set, not null`() {
        // The field is derived and defaulted, so a row straight out of the database is valid and
        // renders as "no related writing" rather than crashing the card.
        assertEquals(RelatedToolOutputs.EMPTY, evidence(id = "e1", sources = listOf(source("t1"))).relatedToolOutputs)
    }

    // -------------------------------------------------------------------- fixtures

    private fun source(taskId: String, name: String = "任务 $taskId") = ProfileEvidenceSource(
        executionLogId = "log-$taskId",
        taskId = taskId,
        taskDisplayName = name,
        eventType = ExecutionLogEventType.FEEDBACK,
        createdAtEpochMillis = 0L,
    )

    private fun record(kind: ToolOutputKind, taskId: String, excerpt: String) =
        ToolOutputRecord(kind = kind, taskId = taskId, excerpt = excerpt)

    private fun evidence(
        id: String,
        scope: ProfileEvidenceScope = ProfileEvidenceScope.GENERAL,
        conclusion: String = "已确认记录 1 条执行反馈",
        sources: List<ProfileEvidenceSource> = emptyList(),
        isUserEdited: Boolean = false,
    ) = ProfileEvidence(
        id = id,
        scope = scope,
        conclusion = conclusion,
        sources = sources,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 2L,
        isUserEdited = isUserEdited,
    )
}
