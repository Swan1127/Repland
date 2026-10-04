package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.ReplandDatabase
import com.swan1127.repland.data.room.RoomEngagementRepository
import com.swan1127.repland.domain.model.EngagementMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngagementRepositoryTest {
    @Test fun modeAndMinimalUsageEventsPersistTogether() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, ReplandDatabase::class.java).build()
        try {
            val repository = RoomEngagementRepository(database)
            assertEquals(EngagementMode.GUIDED, repository.observeMode().first())
            repository.setMode(EngagementMode.EXECUTOR)
            repository.recordTimelineOpened("segment:one")
            assertEquals(EngagementMode.EXECUTOR, repository.observeMode().first())
            val events = database.engagementDao().getEvents()
            assertEquals(2, events.size)
            // Millisecond ties are sorted by UUID, not by call order.
            val changed = events.single { it.type == "MODE_CHANGED" }
            val opened = events.single { it.type == "TIMELINE_OPENED" }
            assertEquals("EXECUTOR", changed.mode)
            assertEquals(null, changed.subjectId)
            assertEquals("EXECUTOR", opened.mode)
            assertEquals("segment:one", opened.subjectId)
        } finally {
            database.close()
        }
    }
}
