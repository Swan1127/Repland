package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InteractionWorkspaceRepositoryTest {
    @Test fun undo_restores_only_order_without_moving_the_plan() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db)
            repo.saveTaskOrder(listOf("a", "b"))
            repo.saveTaskOrder(listOf("b", "a"))
            assertTrue(repo.observeCanUndoTaskOrder().first())
            repo.undoTaskOrder()
            assertEquals(listOf("a", "b"), repo.observeTaskOrder().first())
            assertFalse(repo.observeCanUndoTaskOrder().first())
            assertNull(repo.observeCurrentPlan().first())
        } finally { db.close() }
    }
    private fun proposal() = AssistantTaskProposal("draft-id", "学英语", TaskCategory.COURSE, 30,
        ArrangementTimeHint(600), emptySet(), ArrangementPlacementSource.USER_EXPLICIT, "track-language")

    @Test fun tracks_and_editable_assistant_workspace_survive_database_reopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "interaction-workspace-test-${java.util.UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, ReplandDatabase::class.java, name).build()
        try {
            val tracks = RhythmTracks.defaults + RhythmTrack("track-language", "语言学习")
            val workspace = AssistantWorkspace(LocalDate.now(), "10:00 学英语 30 分钟", listOf(proposal()), ArrangementIntent.ARRANGE_TODAY,
                ArrangementIntent.ARRANGE_TODAY, "source-context")
            RoomPlanRepository(db).saveTracks(tracks)
            RoomPlanRepository(db).saveAssistantWorkspace(workspace)
            db.close()
            db = Room.databaseBuilder(context, ReplandDatabase::class.java, name).build()
            assertEquals(tracks, RoomPlanRepository(db).observeTracks().first())
            assertEquals(workspace, RoomPlanRepository(db).observeAssistantWorkspace().first())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun conflicting_batch_preserves_assistant_workspace_but_success_clears_it_atomically() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db)
            val workspace = AssistantWorkspace(LocalDate.now(), "学英语", listOf(proposal()))
            repo.saveAssistantWorkspace(workspace)
            val tasks = listOf("a", "b").map { TaskDraft(it, it, "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 30, null) }
            val segments = tasks.map { PlannedSegment("placement-${it.id}", it.id!!, LocalDate.now(), 600, 630) }
            assertTrue(runCatching { repo.saveTasksAndPlace(tasks, segments) }.isFailure)
            assertEquals(workspace, repo.observeAssistantWorkspace().first())
            assertTrue(db.taskDao().getAll().isEmpty())
            repo.saveTasksAndPlace(tasks.take(1), segments.take(1))
            assertNull(repo.observeAssistantWorkspace().first())
            assertEquals(1, db.taskDao().getAll().size)
        } finally { db.close() }
    }

    @Test fun changed_context_blocks_assistant_batch_without_clearing_input() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db)
            val workspace = AssistantWorkspace(LocalDate.now(), "学英语", listOf(proposal()), sourceRevision = "stale")
            repo.saveAssistantWorkspace(workspace)
            val task = TaskDraft("new", "学英语", "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 30, null)
            assertTrue(runCatching { repo.saveTasksAndPlace(listOf(task), emptyList()) }.isFailure)
            assertTrue(db.taskDao().getAll().isEmpty())
            assertEquals(workspace, repo.observeAssistantWorkspace().first())
        } finally { db.close() }
    }

    @Test fun duplicate_track_name_is_rejected_without_replacing_definitions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db)
            assertTrue(runCatching { repo.saveTracks(RhythmTracks.defaults + RhythmTrack("duplicate", "专注")) }.isFailure)
            assertEquals(RhythmTracks.defaults, repo.observeTracks().first())
        } finally { db.close() }
    }
}
