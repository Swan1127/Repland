package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.LocalDataSnapshot

interface DataManagementRepository {
    suspend fun snapshot(): LocalDataSnapshot

    /** Called only after the user has explicitly confirmed a complete local reset. */
    suspend fun clearAllLocalData()
}
