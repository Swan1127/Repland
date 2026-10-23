package com.swan1127.repland.ui.undo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UndoStore] is Compose-free in every way that matters for testing: the state capture and
 * the apply function are both handed in, so the stack's semantics can be driven directly.
 *
 * The rules that decide whether a user's mis-tap is recoverable are all here: which end of the
 * stack is dropped on overflow, and the fact that recording a new step discards the redo
 * branch.
 */
class UndoStoreTest {

    @Test
    fun `a fresh store can neither undo nor redo`() {
        val store = UndoStore()
        assertFalse(store.canUndo)
        assertFalse(store.canRedo)
        assertNull(store.undoLabel)
        assertNull(store.redoLabel)
    }

    @Test
    fun `recording a step publishes its label as the next undo`() {
        val store = UndoStore()
        store.record("切换课表集", before = "A", after = "B", apply = {})
        assertEquals("切换课表集", store.undoLabel)
        assertTrue(store.canUndo)
        assertNull(store.redoLabel)
    }

    @Test
    fun `undo applies the before state and returns the label`() = runBlocking {
        val store = UndoStore()
        val applied = mutableListOf<String>()
        store.record("移动", before = "A", after = "B", apply = { applied += it })

        assertEquals(emptyList<String>(), applied)
        assertEquals("移动", store.undo())
        assertEquals(listOf("A"), applied)
    }

    @Test
    fun `redo applies the after state and returns the label`() = runBlocking {
        val store = UndoStore()
        val applied = mutableListOf<String>()
        store.record("移动", before = "A", after = "B", apply = { applied += it })

        store.undo()
        assertEquals("移动", store.redo())
        assertEquals(listOf("A", "B"), applied)
    }

    @Test
    fun `undo on an empty stack returns null and does nothing`() = runBlocking {
        val store = UndoStore()
        assertNull(store.undo())
        assertFalse(store.canUndo)
        assertFalse(store.canRedo)
    }

    @Test
    fun `redo on an empty stack returns null and does nothing`() = runBlocking {
        val store = UndoStore()
        assertNull(store.redo())
        assertFalse(store.canUndo)
        assertFalse(store.canRedo)
    }

    @Test
    fun `undoing the only step clears the undo label and publishes the redo label`() = runBlocking {
        val store = UndoStore()
        store.record("切换课表集", before = "A", after = "B", apply = {})

        store.undo()
        assertNull(store.undoLabel)
        assertEquals("切换课表集", store.redoLabel)
        assertFalse(store.canUndo)
        assertTrue(store.canRedo)
    }

    @Test
    fun `recording a new step clears the redo branch`() = runBlocking {
        // Replaying a previously undone step would apply a state that never followed from the
        // current one, so the branch must go.
        val store = UndoStore()
        store.record("one", before = "A", after = "B", apply = {})
        store.undo()
        assertTrue(store.canRedo)

        store.record("two", before = "B", after = "C", apply = {})
        assertFalse(store.canRedo)
        assertNull(store.redo())
    }

    @Test
    fun `the stack drops the oldest step when it overflows`() = runBlocking {
        val store = UndoStore(maxDepth = 3)
        val applied = mutableListOf<String>()
        (1..4).forEach { step ->
            store.record("step$step", before = "b$step", after = "a$step", apply = { applied += it })
        }

        // step1 is gone; three undos exhaust the stack.
        assertEquals("step4", store.undo())
        assertEquals("step3", store.undo())
        assertEquals("step2", store.undo())
        assertNull(store.undo())
        assertEquals(listOf("b4", "b3", "b2"), applied)
        assertFalse(store.canUndo)
    }

    @Test
    fun `a depth of zero keeps nothing`() = runBlocking {
        val store = UndoStore(maxDepth = 0)
        store.record("one", before = "A", after = "B", apply = {})
        assertFalse(store.canUndo)
        assertNull(store.undo())
    }

    @Test
    fun `undo and redo walk the stack in order`() = runBlocking {
        val store = UndoStore()
        val applied = mutableListOf<String>()
        store.record("one", before = "1b", after = "1a", apply = { applied += it })
        store.record("two", before = "2b", after = "2a", apply = { applied += it })

        assertEquals("two", store.undo())
        assertEquals("one", store.undo())
        assertEquals(listOf("2b", "1b"), applied)

        assertEquals("one", store.redo())
        assertEquals("two", store.redo())
        assertEquals(listOf("2b", "1b", "1a", "2a"), applied)

        // Redo must push back onto the top of the undo stack, not the bottom: the next undo
        // has to reverse the step that was most recently redone.
        assertEquals("two", store.undoLabel)
        assertEquals("two", store.undo())
    }

    // ---------------------------------------------------------------- transaction

    @Test
    fun `transaction captures the state before and after the operation`() = runBlocking {
        val store = UndoStore()
        var state = "A"
        val captures = mutableListOf<String>()
        val restores = mutableListOf<String>()

        store.transaction(
            label = "移动",
            capture = { state.also { captures += it } },
            apply = { state = "B" },
            restore = { value -> restores += value; state = value },
        )

        assertEquals(listOf("A", "B"), captures)
        assertEquals("B", state)
        assertEquals(emptyList<String>(), restores)
        assertEquals("移动", store.undoLabel)

        assertEquals("移动", store.undo())
        assertEquals("A", state)
        assertEquals("移动", store.redo())
        assertEquals("B", state)
        assertEquals(listOf("A", "B"), restores)
    }

    @Test
    fun `transaction records nothing extra when the operation changes nothing`() = runBlocking {
        // The store records what happened, not what was attempted: capture returns the same
        // value on both sides, so undo is a no-op in effect but still a reversible step.
        val store = UndoStore()
        var state = "A"
        store.transaction(
            label = "noop",
            capture = { state },
            apply = { },
            restore = { state = it },
        )
        assertEquals("noop", store.undo())
        assertEquals("A", state)
    }
}
