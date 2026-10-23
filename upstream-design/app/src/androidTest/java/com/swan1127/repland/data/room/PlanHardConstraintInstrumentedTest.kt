package com.swan1127.repland.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.ArrangementConflictException
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.DateOverrideType
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlanLockedException
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite guard checks; no installed database, preferences, private PDF, or network are used. */
@RunWith(AndroidJUnit4::class)
class PlanHardConstraintInstrumentedTest {
    private lateinit var database: ReplandDatabase
    private lateinit var plans: RoomPlanRepository
    private lateinit var tasks: RoomTaskRepository
    private lateinit var time: RoomTimeRepository
    private val date = LocalDate.now().plusDays(7)

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java,
        ).build()
        plans = RoomPlanRepository(database)
        tasks = RoomTaskRepository(database)
        time = RoomTimeRepository(database)
        listOf("first", "second", "third").forEach { id ->
            tasks.save(TaskDraft(
                id = id, displayName = id, description = "本地约束验收", category = TaskCategory.COURSE,
                userPriority = TaskPriority.MEDIUM, estimatedDays = 1, totalDurationMinutes = 30, dueDate = null,
            ))
        }
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun staleOrdinaryDraftCannotReplaceThePlanAfterACourseOrBlockedDateWasAdded() = runBlocking {
        plans.accept(draft(segment("first", 480)))
        val oldCoursePreview = draft(segment("second", 540))
        val oldBlockedPreview = draft(segment("second", 600))
        weekly(TimeBlockKind.COURSE, 540, 570)
        dateOverride(DateOverrideType.BLOCKED, 600, 630)

        rejectedWithoutWrites(ArrangementConflictException::class.java) { plans.accept(oldCoursePreview) }
        rejectedWithoutWrites(ArrangementConflictException::class.java) { plans.accept(oldBlockedPreview) }

        val versions = history().size
        plans.accept(draft(segment("second", 630)))
        assertEquals(versions + 1, history().size)
        assertEquals(630, current().segments.single().startMinute)
    }

    @Test
    fun historicalVersionsAreRecheckedAgainstNewCoursesAndBlockedDatesBeforeRestoring() = runBlocking {
        plans.accept(draft(segment("first", 540)))
        val courseHistoryId = current().plan.id
        plans.accept(draft(segment("first", 600)))
        val blockedHistoryId = current().plan.id
        plans.accept(draft(segment("first", 720)))
        val validHistoryId = current().plan.id
        weekly(TimeBlockKind.COURSE, 540, 570)
        dateOverride(DateOverrideType.BLOCKED, 600, 630)

        rejectedWithoutWrites(ArrangementConflictException::class.java) { plans.restore(courseHistoryId) }
        rejectedWithoutWrites(ArrangementConflictException::class.java) { plans.restore(blockedHistoryId) }
        val before = history().size
        plans.restore(validHistoryId)
        assertEquals(before + 1, history().size)
        assertEquals(720, current().segments.single().startMinute)
        assertTrue(history().any { it.plan.id == courseHistoryId })
    }

    @Test
    fun manualPlaceAndMoveCannotCrossCalendarBlocksOrSameLaneWorkAndFailuresCreateNoVersion() = runBlocking {
        plans.accept(draft(segment("first", 480), segment("second", 720)))
        weekly(TimeBlockKind.REST, 540, 570)
        weekly(TimeBlockKind.COURSE, 600, 630)
        dateOverride(DateOverrideType.BLOCKED, 660, 690)
        val moving = current().segments.single { it.taskId == "first" }

        listOf(540, 600, 660).forEach { start ->
            // Calendar protection applies even when the proposed task uses another visual lane.
            rejectedWithoutWrites(ArrangementConflictException::class.java) {
                plans.placeTask("third", date, start, start + 30, "review")
            }
            rejectedWithoutWrites(ArrangementConflictException::class.java) {
                plans.movePlacement(moving.id, start, start + 30, "review")
            }
        }
        rejectedWithoutWrites(ArrangementConflictException::class.java) {
            plans.placeTask("third", date, 720, 750, "focus")
        }
        rejectedWithoutWrites(ArrangementConflictException::class.java) {
            plans.movePlacement(moving.id, 720, 750, "focus")
        }

        plans.movePlacement(moving.id, 720, 750, "review")
        assertEquals(setOf("focus", "review"), current().segments.map { it.trackId }.toSet())
        plans.placeTask("third", date, 720, 750, "parallel")
        val versions = history().size
        plans.placeTask("third", date, 720, 750, "parallel")
        assertEquals("An identical placement remains idempotent", versions, history().size)
        plans.placeTask("third", date, 750, 751, "focus")
        assertTrue(current().segments.any { it.startMinute == 750 && it.endMinute == 751 })
    }

    @Test
    fun movingOneOccurrenceOntoAnIdenticalOccurrenceOfTheSameTaskIsRejected() = runBlocking {
        plans.accept(draft(segment("first", 480), segment("first", 600)))
        val moving = current().segments.single { it.startMinute == 480 }
        rejectedWithoutWrites(ArrangementConflictException::class.java) {
            plans.movePlacement(moving.id, 600, 630, "focus")
        }
    }

    @Test
    fun liveLocksMustRemainUnchangedInNewVersionsAndCanOnlyMoveAfterExplicitUnlocking() = runBlocking {
        plans.accept(draft(segment("first", 480, locked = true)))
        val originalVersionId = current().plan.id
        val locked = current().segments.single()
        rejectedWithoutWrites(PlanLockedException::class.java) { plans.accept(draft()) }
        rejectedWithoutWrites(PlanLockedException::class.java) {
            plans.accept(draft(segment("first", 600, locked = true)))
        }
        rejectedWithoutWrites(PlanLockedException::class.java) {
            plans.movePlacement(locked.id, 600, 630, "focus")
        }
        rejectedWithoutWrites(PlanLockedException::class.java) { plans.removePlacement(locked.id) }

        // New segment identities do not invalidate an otherwise identical, preserved lock.
        plans.accept(draft(segment("first", 480, locked = true), segment("second", 480, track = "review")))
        val preserved = current().segments.single { it.taskId == "first" }
        assertTrue(preserved.isLocked)
        assertFalse(preserved.id == locked.id)
        val second = current().segments.single { it.taskId == "second" }
        plans.setSegmentLocked(second.id, true)
        rejectedWithoutWrites(PlanLockedException::class.java) { plans.restore(originalVersionId) }
        plans.setSegmentLocked(second.id, false)
        plans.setSegmentLocked(preserved.id, false)
        plans.movePlacement(preserved.id, 600, 630, "focus")
        val moved = current().segments.single { it.taskId == "first" }
        assertEquals(600, moved.startMinute)
        assertFalse(moved.isLocked)
    }

    @Test
    fun expiredOrCompletedTaskLocksDoNotPreventReplacingTheCurrentPlan() = runBlocking {
        plans.accept(draft(segment("first", 480, locked = true).copy(date = LocalDate.now().minusDays(1))))
        plans.accept(draft(segment("second", 600)))
        assertEquals("second", current().segments.single().taskId)

        plans.accept(draft(segment("first", 480, locked = true)))
        tasks.confirmStatus("first", TaskStatus.COMPLETED)
        plans.accept(draft())
        assertTrue(current().segments.isEmpty())
    }

    @Test
    fun explicitDateAvailabilityOpensOnlyTheCoveredBusyPortionAndBlockedDatesAlwaysWin() = runBlocking {
        weekly(TimeBlockKind.COURSE, 540, 600)
        dateOverride(DateOverrideType.AVAILABLE, 540, 570)
        // 08:30-09:30 is valid: the only busy portion, 09:00-09:30, was explicitly opened.
        plans.accept(draft(segment("first", 510).copy(endMinute = 570)))
        rejectedWithoutWrites(ArrangementConflictException::class.java) {
            plans.accept(draft(segment("first", 540).copy(endMinute = 600)))
        }
        dateOverride(DateOverrideType.AVAILABLE, 570, 600)
        plans.accept(draft(segment("first", 540).copy(endMinute = 600)))
        dateOverride(DateOverrideType.BLOCKED, 555, 560)
        rejectedWithoutWrites(ArrangementConflictException::class.java) {
            plans.accept(draft(segment("first", 540).copy(endMinute = 600)))
        }
    }

    private suspend fun rejectedWithoutWrites(type: Class<out Exception>, action: suspend () -> Unit) {
        val previous = history()
        val failure = runCatching { action() }.exceptionOrNull()
        assertTrue("Expected ${type.simpleName}, got $failure", type.isInstance(failure))
        assertEquals("A rejected write must not archive, add, or change a plan version", previous, history())
    }

    private suspend fun current() = requireNotNull(database.planDao().getCurrentPlanWithSegments())
    private suspend fun history() = database.planDao().getAllPlansWithSegments()

    private fun segment(taskId: String, start: Int, track: String = "focus", locked: Boolean = false) =
        PlannedSegment("preview-$taskId-$start", taskId, date, start, start + 30, locked, track)

    private fun draft(vararg segments: PlannedSegment) = PlanDraft(
        generatedAt = LocalDateTime.now(), segments = segments.toList(), pendingTaskIds = emptyList(),
        unscheduledTasks = emptyList(), orderedTaskIds = segments.map { it.taskId }.distinct(),
    )

    private suspend fun weekly(kind: TimeBlockKind, start: Int, end: Int) = time.saveWeeklyBlock(
        WeeklyTimeBlockDraft(title = "约束-$start", kind = kind, dayOfWeek = date.dayOfWeek,
            startMinute = start, endMinute = end),
    )

    private suspend fun dateOverride(type: DateOverrideType, start: Int, end: Int) = time.saveDateOverride(
        DateOverrideDraft(title = "日期约束-$start", type = type, date = date, startMinute = start, endMinute = end),
    )
}
