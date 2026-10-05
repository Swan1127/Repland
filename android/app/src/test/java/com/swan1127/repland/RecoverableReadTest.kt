package com.swan1127.repland

import com.swan1127.repland.ui.state.recoverableRead
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class RecoverableReadTest {
    @Test fun initial_failure_is_not_a_successful_empty_collection() = runBlocking {
        val state = recoverableRead(emptyList<String>(), flowOf(0), "read failed") {
            flow<List<String>> { error("injected") }
        }.first { !it.isLoading }
        assertFalse(state.hasLoaded); assertFalse(state.isTrusted)
        assertEquals("read failed", state.error)
    }

    @Test fun later_failure_retains_last_complete_value() = runBlocking {
        val values = recoverableRead(emptyList<String>(), flowOf(0), "read failed") {
            flow { emit(listOf("original")); error("injected") }
        }.toList()
        assertEquals(3, values.size)
        assertTrue(values[1].isTrusted)
        assertEquals(listOf("original"), values.last().value)
        assertTrue(values.last().hasLoaded); assertFalse(values.last().isTrusted)
    }

    @Test fun explicit_retry_recreates_source_and_preserves_value_while_loading() = runBlocking {
        val retry = MutableStateFlow(0)
        var sources = 0
        val values = withTimeout(5_000) {
            recoverableRead(emptyList<String>(), retry, "read failed") {
                val attempt = ++sources
                flow { emit(listOf(if (attempt == 1) "original" else "recovered"))
                    if (attempt == 1) error("injected") else awaitCancellation() }
            }.onEach { if (it.error != null) retry.value++ }.take(5).toList()
        }
        assertEquals(2, sources)
        assertEquals(listOf("original"), values[3].value)
        assertTrue(values[3].isLoading); assertFalse(values[3].isTrusted)
        assertEquals(listOf("recovered"), values.last().value)
        assertTrue(values.last().isTrusted)
    }

    @Test fun synchronous_source_factory_failure_is_recoverable() = runBlocking {
        val retry = MutableStateFlow(0)
        var sources = 0
        val values = withTimeout(5_000) {
            recoverableRead(0, retry, "read failed") {
                if (++sources == 1) error("factory failed") else flowOf(7)
            }.onEach { if (it.error != null) retry.value++ }.take(4).toList()
        }
        assertFalse(values[1].hasLoaded)
        assertEquals(7, values.last().value); assertTrue(values.last().isTrusted)
    }

    @Test fun unrelated_ui_changes_cannot_clear_read_failure_or_restart_source() = runBlocking {
        var sources = 0
        val action = MutableStateFlow(0)
        val values = withTimeout(5_000) {
            recoverableRead("original", flowOf(0), "read failed") {
                sources++; flow<String> { error("injected") }
            }.combine(action) { read, counter -> read to counter }
                .onEach { if (it.first.error != null && it.second == 0) action.value = 1 }
                .first { it.second == 1 }
        }
        assertEquals(1, sources); assertEquals("read failed", values.first.error)
        assertFalse(values.first.isTrusted)
    }

    @Test fun cancellation_closes_source_without_reporting_business_failure() = runBlocking {
        val states = Channel<com.swan1127.repland.ui.state.ReadSnapshot<Int>>(Channel.UNLIMITED)
        var closed = false
        val job = launch {
            recoverableRead(0, MutableStateFlow(0), "read failed") {
                flow { try { emit(1); awaitCancellation() } finally { closed = true } }
            }.collect { states.send(it) }
        }
        withTimeout(5_000) { while (!states.receive().isTrusted) Unit }
        job.cancelAndJoin()
        assertTrue(closed)
        while (true) { val state = states.tryReceive().getOrNull() ?: break; assertNull(state.error) }
    }
}
