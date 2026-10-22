package com.swan1127.repland.testkit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/**
 * Drives a `androidx.lifecycle.ViewModel` in a plain JVM test.
 *
 * No Robolectric is involved. Three facts were established by experiment before this base class
 * was written (a throwaway probe, since deleted):
 *
 * 1. `ViewModel`'s constructor touches no Android API, so a subclass can simply be constructed.
 * 2. `viewModelScope` resolves `Dispatchers.Main.immediate`; installing a test dispatcher as Main
 *    is therefore what makes `launch` deterministic instead of racing on a background thread.
 * 3. A `stateIn(viewModelScope, WhileSubscribed(...), initial)` produces its **initial** value
 *    without anyone collecting, and only runs its upstream once it has a subscriber.
 *
 * Point 3 is the one that bites: reading `vm.uiState.value` without collecting first returns the
 * initial value forever, which would make a test assert the wrong thing and still pass. Use
 * [collectInBackground] before expecting anything the repository supplied.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ViewModelTestBase {
    protected val dispatcher = StandardTestDispatcher()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }
}

/**
 * Subscribes to [state] so that a `SharingStarted.WhileSubscribed` upstream actually runs.
 *
 * Call this **before** advancing, and before asserting anything other than the initial value.
 *
 * Prefer [withCollected]: a collector never completes on its own, so whoever calls this also has
 * to cancel it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.collectInBackground(state: StateFlow<*>): Job = launch { state.collect { } }

/**
 * Runs [body] with every flow in [states] being collected, then always cancels the collectors.
 *
 * The cancel is not tidiness. A collector launched here is a **child of the test coroutine** and
 * never completes, so `runTest` waits for it until its own timeout — which means one forgotten
 * `cancel()` turns a passing test into a silent sixty-second hang rather than a failure, and a
 * class of thirty tests into half an hour. Doing it in a `finally` makes that impossible to
 * forget, and an exception in [body] still disposes the collectors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun TestScope.withCollected(vararg states: StateFlow<*>, body: suspend TestScope.() -> Unit) {
    val jobs = states.map { collectInBackground(it) }
    advanceUntilIdle()
    try {
        body()
    } finally {
        jobs.forEach { it.cancel() }
    }
}
