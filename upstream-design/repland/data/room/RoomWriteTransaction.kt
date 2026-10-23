package com.swan1127.repland.data.room

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Covers the complete read-modify-write operation, including its initial reads. */
internal fun interface RoomWriteTransaction {
    suspend fun run(block: suspend () -> Unit)
}

/** DAO-only repositories used by JVM tests still serialize concurrent mutations. */
internal class SerialRoomWriteTransaction : RoomWriteTransaction {
    private val mutex = Mutex()

    override suspend fun run(block: suspend () -> Unit) = mutex.withLock { block() }
}
