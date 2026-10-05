package com.swan1127.repland.ui.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

data class ReadSnapshot<T>(
    val value: T,
    val isLoading: Boolean = true,
    val hasLoaded: Boolean = false,
    val error: String? = null,
) {
    val isTrusted: Boolean get() = hasLoaded && !isLoading && error == null
}

/** Retain the last complete value, never turn a failed observation into empty facts.
 * A retry or a new subscription creates the source again; UI state changes do not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> recoverableRead(
    initial: T,
    retries: Flow<Int>,
    errorMessage: String,
    source: () -> Flow<T>,
): Flow<ReadSnapshot<T>> {
    var lastGood = ReadSnapshot(initial)
    return retries.flatMapLatest {
        flow {
            emit(lastGood.copy(isLoading = true))
            try {
                source().collect { value ->
                    lastGood = ReadSnapshot(value, isLoading = false, hasLoaded = true)
                    emit(lastGood)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emit(lastGood.copy(isLoading = false, error = errorMessage))
            }
        }
    }
}
