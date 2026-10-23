package com.swan1127.repland.data

import kotlinx.coroutines.CancellationException

/** Clears every independent store, even when one of them cannot be removed. */
class LocalDataResetCoordinator(
    private vararg val targets: suspend () -> Unit,
) {
    suspend fun clearAll() {
        var failure: Exception? = null
        for (clear in targets) {
            try {
                clear()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}
