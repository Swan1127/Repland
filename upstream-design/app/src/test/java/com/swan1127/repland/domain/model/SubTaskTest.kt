package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure, JVM-only coverage of the subtask rules in [SubTasks] and [SubTaskValidator].
 *
 * These are the same rules the mutation harness perturbs, so the two together decide whether the
 * behaviour a regression would break is actually described. No Android runtime, no clock, no file
 * system: a deterministic id provider stands in for UUID so round-trip assertions are stable.
 */
class SubTaskTest {

    private fun taskWith(vararg subs: SubTask): Task = Task(
        id = "t1",
        description = "d",
        displayName = "Task",
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.MEDIUM,
        estimatedDays = 1,
        totalDurationMinutes = null,
        dueDate = null,
        status = TaskStatus.NOT_STARTED,
        completionSummary = null,
        actualDurationMinutes = null,
        progressPercent = null,
        postponeCount = 0,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
        subtasks = subs.toList(),
    )

    private fun sub(id: String, title: String = "step $id", isDone: Boolean = false): SubTask =
        SubTask(id = id, title = title, isDone = isDone, createdAtEpochMillis = id.hashCode().toLong())

    // ---------------------------------------------------------- SubTaskValidator.cleanTitle

    @Test
    fun `cleanTitle collapses internal whitespace and trims`() {
        assertEquals("a b c", SubTaskValidator.cleanTitle("  a   b\n\tc  "))
    }

    @Test
    fun `cleanTitle joins physical lines with a single space`() {
        assertEquals("line one line two", SubTaskValidator.cleanTitle("line one\nline two"))
    }

    @Test
    fun `cleanTitle caps at the maximum length`() {
        val long = "x".repeat(SubTaskValidator.MAX_TITLE_LENGTH + 30)
        val cleaned = SubTaskValidator.cleanTitle(long)
        assertEquals(SubTaskValidator.MAX_TITLE_LENGTH, cleaned.length)
    }

    @Test
    fun `cleanTitle returns empty when only whitespace remains`() {
        assertEquals("", SubTaskValidator.cleanTitle("   \n\t  "))
    }

    @Test
    fun `isValidTitle is false for blank and true for real text`() {
        assertFalse(SubTaskValidator.isValidTitle("   "))
        assertTrue(SubTaskValidator.isValidTitle("read chapter 3"))
    }

    // ---------------------------------------------------------- SubTasks.add

    @Test
    fun `add appends a cleaned, not-done subtask`() {
        val task = taskWith()
        val updated = SubTasks.add(task, "  Read\n the book  ", nowMillis = 100L) { "s1" }
        assertEquals(1, updated.subtasks.size)
        assertEquals("s1", updated.subtasks[0].id)
        assertEquals("Read the book", updated.subtasks[0].title)
        assertFalse(updated.subtasks[0].isDone)
        assertEquals(100L, updated.subtasks[0].createdAtEpochMillis)
    }

    @Test
    fun `add cleans a too-long title down to the cap`() {
        val task = taskWith()
        val updated = SubTasks.add(task, "x".repeat(200), nowMillis = 1L) { "s1" }
        assertEquals(SubTaskValidator.MAX_TITLE_LENGTH, updated.subtasks[0].title.length)
    }

    @Test
    fun `add is rejected for a blank title`() {
        val task = taskWith()
        try {
            SubTasks.add(task, "   ", nowMillis = 1L) { "s1" }
            throw AssertionError("expected a require() failure for a blank title")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `add preserves existing subtasks`() {
        val task = taskWith(sub("a"), sub("b"))
        val updated = SubTasks.add(task, "c", nowMillis = 1L) { "c" }
        assertEquals(listOf("a", "b", "c"), updated.subtasks.map { it.id })
    }

    // ---------------------------------------------------------- SubTasks.remove

    @Test
    fun `remove drops only the matching subtask`() {
        val task = taskWith(sub("a"), sub("b"), sub("c"))
        val updated = SubTasks.remove(task, "b")
        assertEquals(listOf("a", "c"), updated.subtasks.map { it.id })
    }

    @Test
    fun `remove is a no-op for an unknown id`() {
        val task = taskWith(sub("a"))
        val updated = SubTasks.remove(task, "zzz")
        assertEquals(listOf("a"), updated.subtasks.map { it.id })
    }

    // ---------------------------------------------------------- SubTasks.toggle

    @Test
    fun `toggle flips only the matching subtask`() {
        val task = taskWith(sub("a", isDone = false), sub("b", isDone = false))
        val updated = SubTasks.toggle(task, "a", isDone = true)
        assertTrue(updated.subtasks.first { it.id == "a" }.isDone)
        assertFalse(updated.subtasks.first { it.id == "b" }.isDone)
    }

    @Test
    fun `toggle leaves other fields of the subtask untouched`() {
        val task = taskWith(sub("a", title = "keep me", isDone = false))
        val updated = SubTasks.toggle(task, "a", isDone = true)
        val s = updated.subtasks.first { it.id == "a" }
        assertEquals("keep me", s.title)
        assertEquals("a".hashCode().toLong(), s.createdAtEpochMillis)
    }

    // ---------------------------------------------------------- SubTasks.progress

    @Test
    fun `progress of an empty list has no subtasks and zero percent`() {
        val p = SubTasks.progress(taskWith())
        assertEquals(0, p.total)
        assertEquals(0, p.done)
        assertEquals(0, p.percent)
        assertFalse(p.hasSubtasks)
        assertFalse(p.allDone)
    }

    @Test
    fun `progress counts done and derives percent`() {
        val task = taskWith(sub("a", isDone = true), sub("b", isDone = false), sub("c", isDone = true))
        val p = SubTasks.progress(task)
        assertEquals(3, p.total)
        assertEquals(2, p.done)
        assertEquals(66, p.percent) // 2/3 -> 66
        assertTrue(p.hasSubtasks)
        assertFalse(p.allDone)
    }

    @Test
    fun `progress reports allDone only when every subtask is done`() {
        val task = taskWith(sub("a", isDone = true), sub("b", isDone = true))
        assertTrue(SubTasks.progress(task).allDone)
    }

    @Test
    fun `progress overload matches the task overload`() {
        val task = taskWith(sub("a", isDone = true), sub("b", isDone = false))
        assertEquals(SubTasks.progress(task), SubTasks.progress(task.subtasks))
    }
}
