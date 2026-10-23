package com.swan1127.repland.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskPlacement
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.DateOverrideType
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real SQLite transactions and Room relations using an isolated in-memory database. */
@RunWith(AndroidJUnit4::class)
class ArrangementPersistenceTest {
    private lateinit var database: ReplandDatabase
    private lateinit var arrangements: RoomArrangementCommitRepository
    private lateinit var plans: RoomPlanRepository
    private val date = LocalDate.of(2026, 10, 22)

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReplandDatabase::class.java,
        ).build()
        arrangements = RoomArrangementCommitRepository(database)
        plans = RoomPlanRepository(database)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun batchKeepsTaskIdentitiesAndCreatesOneCompletePlan() = runBlocking {
        arrangements.commit(
            listOf(draft("first"), draft("second")),
            listOf(placement("first", 540), placement("second", 600)),
        )

        val tasks = database.taskDao().getAll()
        val current = requireNotNull(database.planDao().getCurrentPlanWithSegments())
        assertEquals(setOf("first", "second"), tasks.map(TaskEntity::id).toSet())
        assertEquals(tasks.map(TaskEntity::id).toSet(), current.segments.map(PlanSegmentEntity::taskId).toSet())
        assertEquals(2, current.taskOrder.size)
        assertEquals(1, database.planDao().getAllPlansWithSegments().size)
    }

    @Test
    fun failedSegmentWriteRollsBackEveryTaskAndRestoresPreviousCurrentPlan() = runBlocking {
        arrangements.commit(listOf(draft("original")), listOf(placement("original", 480)))
        val previous = requireNotNull(database.planDao().getCurrentPlanWithSegments())
        // Fails after task writes and plan archival, while the second new segment is being inserted.
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_arrangement BEFORE INSERT ON plan_segments " +
                "WHEN NEW.taskId = 'second' BEGIN SELECT RAISE(ABORT, 'forced segment failure'); END",
        )

        val result = runCatching {
            arrangements.commit(
                listOf(draft("first"), draft("second")),
                listOf(placement("first", 540), placement("second", 600)),
            )
        }

        assertTrue("The injected SQLite failure must reach the caller", result.isFailure)
        assertEquals(listOf("original"), database.taskDao().getAll().map(TaskEntity::id))
        assertEquals(previous, database.planDao().getCurrentPlanWithSegments())
        assertEquals(1, database.planDao().getAllPlansWithSegments().size)
    }

    @Test
    fun concurrentPlacementsAllSurviveInTheCurrentPlan() = runBlocking {
        val ids = (1..8).map { "task-$it" }
        arrangements.commit(ids.map(::draft), emptyList())
        val start = CompletableDeferred<Unit>()
        coroutineScope {
            val writes = ids.mapIndexed { index, id ->
                async(Dispatchers.Default) {
                    start.await()
                    // Separate repository instances must still share SQLite transaction serialization.
                    RoomPlanRepository(database).placeTask(id, date, 480 + index * 30, 510 + index * 30, "focus")
                }
            }
            start.complete(Unit)
            writes.awaitAll()
        }

        val current = requireNotNull(database.planDao().getCurrentPlanWithSegments())
        assertEquals(ids.toSet(), current.segments.map(PlanSegmentEntity::taskId).toSet())
        assertEquals(ids.size, current.segments.size)
        assertEquals(ids.size, database.planDao().getAllPlansWithSegments().size)
    }

    @Test
    fun retryingTheSameBatchDoesNotDuplicatePlacementsOrPlanVersions() = runBlocking {
        val drafts = listOf(draft("first"), draft("second"))
        val placements = listOf(placement("first", 540), placement("second", 600))
        arrangements.commit(drafts, placements)
        val firstPlanId = requireNotNull(database.planDao().getCurrentPlanWithSegments()).plan.id

        arrangements.commit(drafts, placements)

        val current = requireNotNull(database.planDao().getCurrentPlanWithSegments())
        assertEquals(firstPlanId, current.plan.id)
        assertEquals(2, database.taskDao().getAll().size)
        assertEquals(2, current.segments.size)
        assertEquals(1, database.planDao().getAllPlansWithSegments().size)
    }

    @Test
    fun concurrentBatchAndOrdinaryPlacementPreserveBothWrites() = runBlocking {
        arrangements.commit(listOf(draft("existing")), emptyList())
        coroutineScope {
            val first = async(Dispatchers.Default) {
                plans.placeTask("existing", date, 480, 510, "focus")
            }
            val second = async(Dispatchers.Default) {
                arrangements.commit(listOf(draft("new")), listOf(placement("new", 540)))
            }
            awaitAll(first, second)
        }

        val current = requireNotNull(database.planDao().getCurrentPlanWithSegments())
        assertEquals(setOf("existing", "new"), current.segments.map(PlanSegmentEntity::taskId).toSet())
        assertFalse(current.segments.any { database.taskDao().getById(it.taskId) == null })
    }

    @Test
    fun restDateExceptionsAndLockedSegmentsCannotBeBypassedByAChatBatch() = runBlocking {
        arrangements.commit(listOf(draft("locked-task")), listOf(placement("locked-task", 660)))
        val segment = requireNotNull(database.planDao().getCurrentPlanWithSegments()).segments.single()
        plans.setSegmentLocked(segment.id, true)
        val previous = database.planDao().getCurrentPlanWithSegments()
        val time = RoomTimeRepository(database)
        time.saveWeeklyBlock(WeeklyTimeBlockDraft(
            title = "休息", kind = TimeBlockKind.REST, dayOfWeek = date.dayOfWeek, startMinute = 540, endMinute = 570,
        ))
        time.saveDateOverride(DateOverrideDraft(
            title = "固定预约", type = DateOverrideType.BLOCKED, date = date, startMinute = 600, endMinute = 630,
        ))

        listOf(540, 600, 660).forEach { start ->
            val result = runCatching {
                arrangements.commit(
                    listOf(draft("free"), draft("blocked-$start")),
                    listOf(placement("free", 780), placement("blocked-$start", start)),
                )
            }
            assertTrue("Hard constraint at $start must reject the entire batch", result.isFailure)
            assertEquals(listOf("locked-task"), database.taskDao().getAll().map { it.id })
            assertEquals(previous, database.planDao().getCurrentPlanWithSegments())
        }
    }

    @Test
    fun retryingAnIdenticalPlacementRemainsIdempotentAfterTheUserLocksIt() = runBlocking {
        arrangements.commit(listOf(draft("first")), listOf(placement("first", 540)))
        val current = requireNotNull(database.planDao().getCurrentPlanWithSegments())
        plans.setSegmentLocked(current.segments.single().id, true)

        arrangements.commit(listOf(draft("first")), listOf(placement("first", 540)))

        val retried = requireNotNull(database.planDao().getCurrentPlanWithSegments())
        assertEquals(current.plan.id, retried.plan.id)
        assertTrue(retried.segments.single().isLocked)
        assertEquals(1, database.planDao().getAllPlansWithSegments().size)
    }

    private fun draft(id: String) = TaskDraft(
        id = id,
        displayName = id,
        description = "A reviewed arrangement task",
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.MEDIUM,
        estimatedDays = 1,
        totalDurationMinutes = 30,
        dueDate = null,
        scheduledForDate = date,
    )

    private fun placement(id: String, start: Int) = TaskPlacement(id, date, start, start + 30)
}
