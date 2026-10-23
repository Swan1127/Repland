package com.swan1127.repland.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class LocalDataResetCoordinatorTest {
    @Test
    fun `reset visits every store in order`() = runTest {
        val cleared = mutableListOf<String>()
        LocalDataResetCoordinator({ cleared += "notes" }, { cleared += "profile" }, { cleared += "credentials" }).clearAll()
        assertEquals(listOf("notes", "profile", "credentials"), cleared)
    }

    @Test
    fun `one failed store does not preserve later stores or report success`() = runTest {
        val cleared = mutableListOf<String>()
        val failure = IllegalStateException("unwritable preference file")
        try {
            LocalDataResetCoordinator(
                { throw failure },
                { cleared += "credentials" },
                { throw IllegalStateException("unwritable avatar") },
                { cleared += "chat" },
            ).clearAll()
            fail("A partial reset must fail")
        } catch (error: IllegalStateException) {
            assertSame(failure, error)
            assertEquals(1, error.suppressed.size)
        }
        assertEquals(listOf("credentials", "chat"), cleared)
    }

    @Test
    fun `cancellation is not converted into a storage error`() = runTest {
        val cancelled = CancellationException("cancelled")
        try {
            LocalDataResetCoordinator({ throw cancelled }).clearAll()
            fail("Cancellation must propagate")
        } catch (error: CancellationException) {
            assertSame(cancelled, error)
        }
    }
}
