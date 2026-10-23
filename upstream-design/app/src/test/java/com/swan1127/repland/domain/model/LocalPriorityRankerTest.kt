package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [LocalPriorityRanker] is the explainable, deterministic fallback for dynamic priority.
 * It must never mutate [Task.userPriority] and must always be reproducible.
 */
class LocalPriorityRankerTest {

    private val today = LocalDate.of(2026, 3, 2)

    @Test
    fun `an overdue task outranks a task due far in the future`() {
        val overdue = task("overdue", dueDate = today.minusDays(6))
        val far = task("far", dueDate = today.plusDays(60))

        val ranked = LocalPriorityRanker.rank(listOf(far, overdue), today = today)

        assertEquals(listOf("overdue", "far"), ranked.map { it.taskId })
        assertTrue(ranked[0].score > ranked[1].score)
    }

    @Test
    fun `a manual order overrides the computed score`() {
        val overdue = task("overdue", dueDate = today.minusDays(6))
        val far = task("far", dueDate = today.plusDays(60))

        val ranked = LocalPriorityRanker.rank(
            tasks = listOf(overdue, far),
            today = today,
            manualTaskOrder = listOf("far"),
        )

        assertEquals(listOf("far", "overdue"), ranked.map { it.taskId })
    }

    @Test
    fun `repeated postponement raises the context score`() {
        val never = task("never", dueDate = null, postponeCount = 0)
        val often = task("often", dueDate = null, postponeCount = 4)

        val ranked = LocalPriorityRanker.rank(listOf(never, often), today = today)

        assertEquals("often", ranked.first().taskId)
        assertTrue(ranked.first().score > ranked.last().score)
    }

    @Test
    fun `identical tasks fall back to a stable id order`() {
        val ranked = LocalPriorityRanker.rank(
            tasks = listOf(
                task("b", dueDate = today.plusDays(2), priority = TaskPriority.HIGH),
                task("a", dueDate = today.plusDays(2), priority = TaskPriority.HIGH),
            ),
            today = today,
        )

        assertEquals(listOf("a", "b"), ranked.map { it.taskId })
    }

    @Test
    fun `ranking is reproducible for the same input`() {
        val tasks = listOf(
            task("a", dueDate = today.plusDays(2), priority = TaskPriority.HIGH),
            task("b", dueDate = today.plusDays(9), priority = TaskPriority.LOW),
            task("c", dueDate = null, postponeCount = 3),
        )

        val first = LocalPriorityRanker.rank(tasks, today = today).map { it.taskId to it.score }
        val second = LocalPriorityRanker.rank(tasks, today = today).map { it.taskId to it.score }

        assertEquals(first, second)
    }

    @Test
    fun `every score stays inside the 0 to 100 range`() {
        val tasks = listOf(
            task("a", priority = TaskPriority.REQUIRED, dueDate = today.minusDays(30)),
            task("b", priority = TaskPriority.LOW, dueDate = null, postponeCount = 99),
            task("c", priority = TaskPriority.MEDIUM, category = TaskCategory.LEISURE),
        )

        LocalPriorityRanker.rank(tasks, today = today).forEach { assessment ->
            assertTrue("score ${assessment.score} is out of range", assessment.score in 0..100)
        }
    }

    @Test
    fun `the neutral AI-fit marker is always attached`() {
        val ranked = LocalPriorityRanker.rank(listOf(task("a")), today = today)

        assertTrue(ranked.single().reasons.any { it.kind == PriorityReasonKind.LOCAL_AI_NEUTRAL })
    }

    @Test
    fun `an overdue reason carries the number of days past the deadline`() {
        val ranked = LocalPriorityRanker.rank(
            tasks = listOf(task("a", dueDate = today.minusDays(6))),
            today = today,
        )

        val overdue = ranked.single().reasons.single { it.kind == PriorityReasonKind.OVERDUE }
        assertEquals(6, overdue.value)
    }

    @Test
    fun `all-zero category preferences are rejected`() {
        val zeroed = TaskCategory.entries.associateWith { 0 }

        assertThrows(IllegalArgumentException::class.java) {
            LocalPriorityRanker.rank(
                tasks = listOf(task("a")),
                categoryPreferences = zeroed,
                today = today,
            )
        }
    }

    @Test
    fun `ranking never rewrites the user-owned priority`() {
        val original = task("a", priority = TaskPriority.LOW, dueDate = today.minusDays(10))

        LocalPriorityRanker.rank(listOf(original), today = today)

        assertEquals(TaskPriority.LOW, original.userPriority)
    }
}
