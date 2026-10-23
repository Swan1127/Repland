package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.ProfileEvidenceScope
import com.swan1127.repland.domain.model.executionLog
import com.swan1127.repland.domain.model.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `toDomainEvidence` joins stored evidence rows to the execution logs and tasks they cite.
 *
 * The join is where a dangling reference shows up: a log that was deleted, or a task that no
 * longer exists, must not take the whole profile screen down with it. The two rules that
 * matter — drop an unresolvable log, but keep going with a placeholder name for a missing
 * task — are asserted separately because they are deliberately different.
 */
class ProfileEvidenceJoinTest {

    @Test
    fun `each cited log becomes a resolved source`() {
        val evidence = listOf(evidenceEntity(sourceLogIds = "log-1"))
        val logs = listOf(executionLog(id = "log-1", taskId = "task-1", createdAtEpochMillis = 777L))
        val tasks = listOf(task(id = "task-1"))

        val source = evidence.toDomainEvidence(logs, tasks).single().sources.single()

        assertEquals("log-1", source.executionLogId)
        assertEquals("task-1", source.taskId)
        assertEquals("Task task-1", source.taskDisplayName)
        assertEquals(ExecutionLogEventType.FEEDBACK, source.eventType)
        assertEquals(777L, source.createdAtEpochMillis)
    }

    @Test
    fun `the source display name comes from the task`() {
        val source = listOf(evidenceEntity(sourceLogIds = "log-1"))
            .toDomainEvidence(
                logs = listOf(executionLog(id = "log-1", taskId = "task-1")),
                tasks = listOf(task(id = "task-1")),
            )
            .single()
            .sources
            .single()

        assertEquals("Task task-1", source.taskDisplayName)
    }

    @Test
    fun `a source whose task is gone keeps a placeholder name instead of dropping the source`() {
        // The log still exists and still carries evidence, so the source survives; only the
        // display name is unknown. Dropping it would silently shrink the user's evidence.
        val sources = listOf(evidenceEntity(sourceLogIds = "log-1"))
            .toDomainEvidence(
                logs = listOf(executionLog(id = "log-1", taskId = "task-deleted")),
                tasks = emptyList(),
            )
            .single()
            .sources

        assertEquals(1, sources.size)
        assertEquals("未知任务", sources.single().taskDisplayName)
        assertEquals("task-deleted", sources.single().taskId)
    }

    @Test
    fun `a source whose log is gone is dropped`() {
        // Unlike a missing task, a missing log leaves nothing to point at, so the source is
        // removed rather than shown as a broken row.
        val sources = listOf(evidenceEntity(sourceLogIds = "log-missing"))
            .toDomainEvidence(
                logs = listOf(executionLog(id = "log-1", taskId = "task-1")),
                tasks = listOf(task(id = "task-1")),
            )
            .single()
            .sources

        assertEquals(emptyList<Any>(), sources)
    }

    @Test
    fun `a dangling log is dropped while its siblings still resolve`() {
        val sources = listOf(evidenceEntity(sourceLogIds = "log-1|log-gone|log-2"))
            .toDomainEvidence(
                logs = listOf(
                    executionLog(id = "log-1", taskId = "task-1"),
                    executionLog(id = "log-2", taskId = "task-1"),
                ),
                tasks = listOf(task(id = "task-1")),
            )
            .single()
            .sources

        assertEquals(listOf("log-1", "log-2"), sources.map { it.executionLogId })
    }

    @Test
    fun `the sources follow the stored id order not the log order`() {
        val sources = listOf(evidenceEntity(sourceLogIds = "log-3|log-1|log-2"))
            .toDomainEvidence(
                logs = listOf(
                    executionLog(id = "log-1", taskId = "task-1"),
                    executionLog(id = "log-2", taskId = "task-1"),
                    executionLog(id = "log-3", taskId = "task-1"),
                ),
                tasks = listOf(task(id = "task-1")),
            )
            .single()
            .sources

        assertEquals(listOf("log-3", "log-1", "log-2"), sources.map { it.executionLogId })
    }

    @Test
    fun `evidence with no cited logs maps to no sources`() {
        val evidence = listOf(evidenceEntity(sourceLogIds = ""))
            .toDomainEvidence(logs = emptyList(), tasks = emptyList())

        assertEquals(1, evidence.size)
        assertEquals(emptyList<Any>(), evidence.single().sources)
    }

    @Test
    fun `an empty evidence list maps to an empty list`() {
        assertEquals(
            emptyList<Any>(),
            emptyList<ProfileEvidenceEntity>().toDomainEvidence(emptyList(), emptyList()),
        )
    }

    @Test
    fun `each evidence row keeps its own scope conclusion and edit flag`() {
        val rows = listOf(
            evidenceEntity(id = "e1", scope = ProfileEvidenceScope.GENERAL.name, conclusion = "第一条", isUserEdited = false),
            evidenceEntity(id = "e2", scope = ProfileEvidenceScope.LEARNING.name, conclusion = "第二条", isUserEdited = true),
        )
        val evidence = rows.toDomainEvidence(logs = emptyList(), tasks = emptyList())

        assertEquals(listOf("e1", "e2"), evidence.map { it.id })
        assertEquals(listOf(ProfileEvidenceScope.GENERAL, ProfileEvidenceScope.LEARNING), evidence.map { it.scope })
        assertEquals(listOf("第一条", "第二条"), evidence.map { it.conclusion })
        assertEquals(listOf(false, true), evidence.map { it.isUserEdited })
    }

    @Test
    fun `two evidence rows citing the same log each resolve it independently`() {
        val rows = listOf(
            evidenceEntity(id = "e1", sourceLogIds = "log-1"),
            evidenceEntity(id = "e2", sourceLogIds = "log-1"),
        )
        val evidence = rows.toDomainEvidence(
            logs = listOf(executionLog(id = "log-1", taskId = "task-1")),
            tasks = listOf(task(id = "task-1")),
        )

        assertTrue(evidence.all { it.sources.size == 1 })
        assertEquals(listOf("log-1", "log-1"), evidence.map { it.sources.single().executionLogId })
    }

    @Test
    fun `a source resolves the task named by its own log not by another log`() {
        // Two logs, two tasks: the join must key on the log's taskId, not on position.
        val sources = listOf(evidenceEntity(sourceLogIds = "log-a|log-b"))
            .toDomainEvidence(
                logs = listOf(
                    executionLog(id = "log-a", taskId = "task-1"),
                    executionLog(id = "log-b", taskId = "task-2"),
                ),
                tasks = listOf(task(id = "task-1"), task(id = "task-2")),
            )
            .single()
            .sources

        assertEquals(listOf("Task task-1", "Task task-2"), sources.map { it.taskDisplayName })
    }

    // ------------------------------------------------------------ builders

    private fun evidenceEntity(
        id: String = "evidence-1",
        scope: String = ProfileEvidenceScope.LEARNING.name,
        conclusion: String = "结论",
        sourceLogIds: String = "log-1",
        isUserEdited: Boolean = false,
    ): ProfileEvidenceEntity = ProfileEvidenceEntity(
        id = id,
        scope = scope,
        conclusion = conclusion,
        sourceLogIds = sourceLogIds,
        sourceFingerprint = "fingerprint-$id",
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 2_000L,
        isUserEdited = isUserEdited,
    )
}
