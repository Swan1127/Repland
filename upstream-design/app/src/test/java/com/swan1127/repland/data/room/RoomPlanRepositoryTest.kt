package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.PlanLockedException
import com.swan1127.repland.domain.model.TEST_MONDAY
import com.swan1127.repland.domain.model.planDraft
import com.swan1127.repland.domain.model.segment
import com.swan1127.repland.testkit.FakePlanDao
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RoomPlanRepository] is the only writer of plan versions. It is reachable from a plain JVM test
 * because it takes the `PlanDao` **interface** rather than the `ReplandDatabase`.
 *
 * Almost every method here does the same thing: mint a **new** plan version, copy the previous
 * content into it under fresh row identities, and archive the old version. That shape is what
 * makes an immutable, restorable plan history possible, and it is also what makes a single
 * missed `copy(planId = ...)` invisible until a user restores a version and gets another plan's
 * segments. These tests pin the shape rather than the individual field values.
 *
 * What the [FakePlanDao] cannot cover is noted on the fake: the `@Query` text, `@Relation`
 * loading, and the real `ABORT` enforcement.
 */
class RoomPlanRepositoryTest {

    // ------------------------------------------------------------------ accept

    @Test
    fun `accept stores the draft as the new current version and archives the previous one`() = runTest {
        val dao = daoWithCurrentPlan(planId = "plan-1", createdAtEpochMillis = 1_000L)
        val repository = RoomPlanRepository(dao)

        repository.accept(planDraft(segments = listOf(segment(id = "draft-seg", startMinute = 480, endMinute = 540))))

        val current = dao.getCurrentPlanWithSegments()!!
        assertTrue(current.plan.isCurrent)
        assertFalse(dao.plans.first { it.id == "plan-1" }.isCurrent)
        assertEquals(1, dao.archiveCount)
    }

    @Test
    fun `accept mints fresh segment identities rather than reusing the draft's`() = runTest {
        // The draft's ids belong to the draft. A stored plan version is immutable, so reusing an
        // id would let a later edit of the draft rewrite an accepted version in place.
        val dao = FakePlanDao()
        val repository = RoomPlanRepository(dao)

        repository.accept(planDraft(segments = listOf(segment(id = "draft-seg", startMinute = 480, endMinute = 540))))

        val stored = dao.getCurrentPlanWithSegments()!!.segments.single()
        assertFalse("the draft's segment id must not be reused", stored.id == "draft-seg")
        assertEquals(dao.getCurrentPlanWithSegments()!!.plan.id, stored.planId)
    }

    @Test
    fun `accept keeps every scheduling field the draft carried`() = runTest {
        val dao = FakePlanDao()
        val repository = RoomPlanRepository(dao)

        repository.accept(
            planDraft(
                segments = listOf(
                    segment(
                        id = "s1",
                        taskId = "task-1",
                        date = TEST_MONDAY.plusDays(2),
                        startMinute = 600,
                        endMinute = 660,
                        isLocked = true,
                        trackId = "review",
                    ),
                ),
            ),
        )

        val stored = dao.getCurrentPlanWithSegments()!!.segments.single()
        assertEquals("task-1", stored.taskId)
        assertEquals(TEST_MONDAY.plusDays(2).toEpochDay(), stored.dateEpochDay)
        assertEquals(600, stored.startMinute)
        assertEquals(660, stored.endMinute)
        assertTrue(stored.isLocked)
        assertEquals("review", stored.trackId)
    }

    @Test
    fun `accept writes one order row per task, in the draft's order`() = runTest {
        val dao = FakePlanDao()
        val repository = RoomPlanRepository(dao)

        repository.accept(planDraft(orderedTaskIds = listOf("c", "a", "b")))

        assertEquals(listOf("c", "a", "b"), dao.currentOrder())
        assertEquals(listOf(0, 1, 2), dao.currentPositions())
    }

    @Test
    fun `accept collapses a repeated task id to its first position`() = runTest {
        // `distinct()` runs before `mapIndexed`, so the position is the index in the *deduplicated*
        // list. Without it the second occurrence would collide on the (planId, taskId) primary key.
        val dao = FakePlanDao()
        val repository = RoomPlanRepository(dao)

        repository.accept(planDraft(orderedTaskIds = listOf("a", "b", "a")))

        assertEquals(listOf("a", "b"), dao.currentOrder())
        assertEquals(listOf(0, 1), dao.currentPositions())
    }

    @Test
    fun `accept stamps the draft's manual flag onto every order row`() = runTest {
        val dao = FakePlanDao()
        val repository = RoomPlanRepository(dao)

        repository.accept(planDraft(orderedTaskIds = listOf("a", "b"), hasManualTaskOrder = true))

        assertEquals(listOf(true, true), dao.currentManualFlags())
    }

    @Test
    fun `accept records a derived order as non-manual`() = runTest {
        val dao = FakePlanDao()
        val repository = RoomPlanRepository(dao)

        repository.accept(planDraft(orderedTaskIds = listOf("a"), hasManualTaskOrder = false))

        assertEquals(listOf(false), dao.currentManualFlags())
    }

    @Test
    fun `accept still creates a version when the draft is empty`() = runTest {
        val dao = FakePlanDao()
        val repository = RoomPlanRepository(dao)

        repository.accept(planDraft())

        val current = dao.getCurrentPlanWithSegments()!!
        assertEquals(emptyList<PlanSegmentEntity>(), current.segments)
        assertEquals(emptyList<PlanTaskOrderEntity>(), current.taskOrder)
        assertTrue(current.plan.isCurrent)
    }

    @Test
    fun `accept does not carry the previous version's segments into the new one`() = runTest {
        // The failure this guards against is the obvious implementation mistake: keeping the old
        // rows and only flipping `isCurrent`. The old plan must survive as a *separate* version.
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedSegments = listOf(segmentEntity(id = "old-seg", taskId = "task-old", startMinute = 480, endMinute = 540)),
            storedOrder = listOf(orderEntity(taskId = "task-old", position = 0)),
        )
        val repository = RoomPlanRepository(dao)

        repository.accept(planDraft(segments = listOf(segment(id = "new", taskId = "task-new", startMinute = 600, endMinute = 660))))

        assertEquals(listOf("task-new"), dao.getCurrentPlanWithSegments()!!.segments.map(PlanSegmentEntity::taskId))
        // ...and the archived version is still intact and still readable.
        val archived = dao.getPlanWithSegments("plan-1")!!
        assertFalse(archived.plan.isCurrent)
        assertEquals(listOf("task-old"), archived.segments.map(PlanSegmentEntity::taskId))
        assertEquals(listOf("task-old"), archived.taskOrder.map(PlanTaskOrderEntity::taskId))
    }

    // ------------------------------------------------------------------ observe

    @Test
    fun `observeCurrentPlan reports no plan when nothing is current`() = runTest {
        val repository = RoomPlanRepository(FakePlanDao())

        assertNull(repository.observeCurrentPlan().first())
    }

    @Test
    fun `observeCurrentPlan maps the stored rows into the domain model`() = runTest {
        val dao = daoWithCurrentPlan(
            planId = "plan-7",
            createdAtEpochMillis = 4_242L,
            storedSegments = listOf(segmentEntity(id = "seg-1", planId = "plan-7", taskId = "task-1", startMinute = 480, endMinute = 540)),
            storedOrder = listOf(orderEntity(planId = "plan-7", taskId = "task-1", position = 0, isManual = true)),
        )

        val plan = RoomPlanRepository(dao).observeCurrentPlan().first()!!

        assertEquals("plan-7", plan.id)
        assertEquals(4_242L, plan.createdAtEpochMillis)
        assertEquals(listOf("seg-1"), plan.segments.map { it.id })
        assertEquals(listOf("task-1"), plan.orderedTaskIds)
        assertTrue(plan.hasManualTaskOrder)
    }

    @Test
    fun `observePlanHistory keeps the DAO's newest-first order`() = runTest {
        val dao = FakePlanDao()
        dao.seed(
            plans = listOf(
                PlanEntity(id = "older", createdAtEpochMillis = 1_000L, isCurrent = false),
                PlanEntity(id = "newer", createdAtEpochMillis = 2_000L, isCurrent = true),
            ),
        )

        val history = RoomPlanRepository(dao).observePlanHistory().first()

        assertEquals(listOf("newer", "older"), history.map { it.id })
    }

    // ------------------------------------------------------------------ restore

    @Test
    fun `restore refuses an unknown version and writes nothing`() {
        val dao = FakePlanDao()
        val repository = RoomPlanRepository(dao)

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.restore("missing-plan") }
        }

        assertEquals("The plan version to restore does not exist.", thrown.message)
        assertEquals(emptyList<PlanEntity>(), dao.plans)
        assertEquals(0, dao.archiveCount)
    }

    @Test
    fun `restore creates a new current version instead of reusing the source identity`() = runTest {
        val dao = FakePlanDao()
        dao.seed(
            plans = listOf(
                PlanEntity(id = "source", createdAtEpochMillis = 1_000L, isCurrent = false),
                PlanEntity(id = "current", createdAtEpochMillis = 2_000L, isCurrent = true),
            ),
            segments = listOf(segmentEntity(id = "src-seg", planId = "source", taskId = "task-1", startMinute = 480, endMinute = 540)),
            taskOrder = listOf(orderEntity(planId = "source", taskId = "task-1", position = 0)),
        )
        val repository = RoomPlanRepository(dao)

        repository.restore("source")

        val current = dao.getCurrentPlanWithSegments()!!
        assertFalse("restoring must mint a version, not revive the source", current.plan.id == "source")
        assertFalse("the version that was current is now archived", dao.plans.first { it.id == "current" }.isCurrent)
        // The source itself is untouched.
        assertFalse(dao.plans.first { it.id == "source" }.isCurrent)
        assertEquals(1, dao.archiveCount)
    }

    @Test
    fun `restore copies the source segments under fresh identities`() = runTest {
        val dao = FakePlanDao()
        dao.seed(
            plans = listOf(PlanEntity(id = "source", createdAtEpochMillis = 1_000L, isCurrent = false)),
            segments = listOf(
                segmentEntity(id = "src-a", planId = "source", taskId = "task-a", startMinute = 480, endMinute = 540, isLocked = true, trackId = "review"),
                segmentEntity(id = "src-b", planId = "source", taskId = "task-b", startMinute = 600, endMinute = 660),
            ),
        )

        RoomPlanRepository(dao).restore("source")

        val restored = dao.getCurrentPlanWithSegments()!!.segments
        assertEquals(listOf("task-a", "task-b"), restored.map(PlanSegmentEntity::taskId))
        assertTrue("a restored segment must not keep the source's row id", restored.none { it.id == "src-a" || it.id == "src-b" })
        assertTrue(restored.all { it.planId == dao.getCurrentPlanWithSegments()!!.plan.id })
        assertTrue(restored.first { it.taskId == "task-a" }.isLocked)
        assertEquals("review", restored.first { it.taskId == "task-a" }.trackId)
    }

    @Test
    fun `restore preserves a stored order including its manual flags and positions`() = runTest {
        val dao = FakePlanDao()
        dao.seed(
            plans = listOf(PlanEntity(id = "source", createdAtEpochMillis = 1_000L, isCurrent = false)),
            segments = listOf(segmentEntity(id = "src-a", planId = "source", taskId = "task-a", startMinute = 480, endMinute = 540)),
            taskOrder = listOf(
                orderEntity(planId = "source", taskId = "task-b", position = 1, isManual = true),
                orderEntity(planId = "source", taskId = "task-a", position = 0, isManual = false),
            ),
        )

        RoomPlanRepository(dao).restore("source")

        assertEquals(listOf("task-a", "task-b"), dao.currentOrder())
        assertEquals(listOf(0, 1), dao.currentPositions())
        assertEquals(listOf(false, true), dao.currentManualFlags())
    }

    @Test
    fun `restore derives a deterministic order when the source has none`() = runTest {
        // FINDING, in contrast to the read-path fallback in PlanWithSegments.toDomain, which is
        // built from the RAW @Relation list and is therefore not reproducible (pinned in
        // PlanWithSegmentsMappingTest). This one sorts first, so a plan restored from a version
        // with no stored order gets the same task order every time.
        val dao = FakePlanDao()
        dao.seed(
            plans = listOf(PlanEntity(id = "source", createdAtEpochMillis = 1_000L, isCurrent = false)),
            // Deliberately not in time order.
            segments = listOf(
                segmentEntity(id = "s-day2", planId = "source", taskId = "task-z", dateEpochDay = day(2), startMinute = 480, endMinute = 540),
                segmentEntity(id = "s-day1-late", planId = "source", taskId = "task-y", dateEpochDay = day(1), startMinute = 600, endMinute = 660),
                segmentEntity(id = "s-day1-early", planId = "source", taskId = "task-x", dateEpochDay = day(1), startMinute = 480, endMinute = 540),
            ),
        )

        RoomPlanRepository(dao).restore("source")

        assertEquals(listOf("task-x", "task-y", "task-z"), dao.currentOrder())
    }

    @Test
    fun `restore's derived order deduplicates a task that spans several segments`() = runTest {
        val dao = FakePlanDao()
        dao.seed(
            plans = listOf(PlanEntity(id = "source", createdAtEpochMillis = 1_000L, isCurrent = false)),
            segments = listOf(
                segmentEntity(id = "s1", planId = "source", taskId = "task-a", dateEpochDay = day(1), startMinute = 480, endMinute = 540),
                segmentEntity(id = "s2", planId = "source", taskId = "task-a", dateEpochDay = day(1), startMinute = 600, endMinute = 660),
                segmentEntity(id = "s3", planId = "source", taskId = "task-b", dateEpochDay = day(1), startMinute = 720, endMinute = 780),
            ),
        )

        RoomPlanRepository(dao).restore("source")

        assertEquals(listOf("task-a", "task-b"), dao.currentOrder())
    }

    @Test
    fun `restore's derived order is not marked manual`() = runTest {
        // Nobody touched this order; it was inferred. Marking it manual would tell the planner the
        // user had taken control, and the planner stops reordering what it believes is hand-set.
        val dao = FakePlanDao()
        dao.seed(
            plans = listOf(PlanEntity(id = "source", createdAtEpochMillis = 1_000L, isCurrent = false)),
            segments = listOf(segmentEntity(id = "s1", planId = "source", taskId = "task-a", startMinute = 480, endMinute = 540)),
        )

        RoomPlanRepository(dao).restore("source")

        assertEquals(listOf(false), dao.currentManualFlags())
    }

    @Test
    fun `restore can re-issue the version that is already current`() = runTest {
        // Pinned as behaviour, not endorsed as intent: restoring the current version is not
        // refused, so it produces a second version with identical content.
        val dao = daoWithCurrentPlan(planId = "current", createdAtEpochMillis = 1_000L)
        val repository = RoomPlanRepository(dao)

        repository.restore("current")

        assertFalse(dao.plans.first { it.id == "current" }.isCurrent)
        assertFalse(dao.getCurrentPlanWithSegments()!!.plan.id == "current")
        assertEquals(2, dao.plans.size)
    }

    // ------------------------------------------------------------------ clearCurrentPlan

    @Test
    fun `clearCurrentPlan archives the version but keeps it in history`() = runTest {
        val dao = daoWithCurrentPlan(planId = "plan-1", createdAtEpochMillis = 1_000L)
        val repository = RoomPlanRepository(dao)

        repository.clearCurrentPlan()

        assertNull(dao.getCurrentPlanWithSegments())
        assertFalse(dao.plans.single { it.id == "plan-1" }.isCurrent)
        assertEquals(1, dao.archiveCount)
    }

    // ------------------------------------------------------------------ setSegmentLocked

    @Test
    fun `setSegmentLocked passes the call straight through to the DAO`() = runTest {
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedSegments = listOf(segmentEntity(id = "seg-1", startMinute = 480, endMinute = 540)),
        )

        RoomPlanRepository(dao).setSegmentLocked("seg-1", true)

        assertTrue(dao.segments.single { it.id == "seg-1" }.isLocked)
        assertEquals(0, dao.archiveCount)
    }

    // ------------------------------------------------------------------ placeTask

    @Test
    fun `placeTask rejects a start minute outside the day`() {
        val dao = FakePlanDao()
        val repository = RoomPlanRepository(dao)

        listOf(-1, 1_440).forEach { start ->
            rejection { repository.placeTask("t1", TEST_MONDAY, start, start + 60, "focus") }
        }

        assertEquals(emptyList<PlanEntity>(), dao.plans)
        assertEquals(0, dao.archiveCount)
    }

    @Test
    fun `the upper half of the start-minute bound is implied by the other two clauses`() {
        // FINDING, pinned rather than papered over. The guard is
        // `startMinute in 0 until 1440 && endMinute in 1..1440 && startMinute < endMinute`, but
        // `startMinute < endMinute <= 1440` already forces `startMinute <= 1439`, so the first
        // clause reduces to `startMinute >= 0`. Widening its upper end is therefore an *equivalent
        // mutation*: no test can distinguish it, and a mutant that does it will survive for a
        // reason that is not a weak test. The lower end is the load-bearing half, and the test
        // above pins it with -1.
        val dao = FakePlanDao()
        val repository = RoomPlanRepository(dao)

        runTest {
            // The largest start the guard accepts...
            repository.placeTask("t1", TEST_MONDAY, 1_439, 1_440, "focus")
            assertEquals(1_439, dao.getCurrentPlanWithSegments()!!.segments.single().startMinute)
        }

        // ...and the smallest one it refuses.
        rejection { repository.placeTask("t2", TEST_MONDAY, 1_440, 1_440, "focus") }
    }

    @Test
    fun `placeTask rejects an end minute outside the day`() {
        val dao = FakePlanDao()
        val repository = RoomPlanRepository(dao)

        rejection { repository.placeTask("t1", TEST_MONDAY, 0, 0, "focus") }
        rejection { repository.placeTask("t1", TEST_MONDAY, 600, 1_441, "focus") }

        assertEquals(emptyList<PlanEntity>(), dao.plans)
    }

    @Test
    fun `placeTask rejects a placement that ends where it starts`() {
        val dao = FakePlanDao()
        val repository = RoomPlanRepository(dao)

        rejection { repository.placeTask("t1", TEST_MONDAY, 600, 600, "focus") }

        assertEquals(emptyList<PlanEntity>(), dao.plans)
    }

    @Test
    fun `placeTask creates a version when there is no current plan`() = runTest {
        val dao = FakePlanDao()

        RoomPlanRepository(dao).placeTask("task-1", TEST_MONDAY, 600, 660, "focus")

        val current = dao.getCurrentPlanWithSegments()!!
        val placed = current.segments.single()
        assertEquals("task-1", placed.taskId)
        assertEquals(600, placed.startMinute)
        assertEquals(660, placed.endMinute)
        assertEquals("focus", placed.trackId)
        assertEquals(listOf("task-1"), dao.currentOrder())
    }

    @Test
    fun `placeTask carries the existing segments forward under fresh identities`() = runTest {
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedSegments = listOf(segmentEntity(id = "old-seg", taskId = "task-old", startMinute = 480, endMinute = 540, isLocked = true, trackId = "review")),
            storedOrder = listOf(orderEntity(taskId = "task-old", position = 0)),
        )

        RoomPlanRepository(dao).placeTask("task-new", TEST_MONDAY, 600, 660, "focus")

        val segments = dao.getCurrentPlanWithSegments()!!.segments
        assertEquals(listOf("task-old", "task-new"), segments.map(PlanSegmentEntity::taskId))
        assertTrue("every row is re-issued", segments.none { it.id == "old-seg" })
        assertTrue(segments.first { it.taskId == "task-old" }.isLocked)
        assertEquals("review", segments.first { it.taskId == "task-old" }.trackId)
    }

    @Test
    fun `placeTask appends the new segment unlocked even when the plan holds locked ones`() = runTest {
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedSegments = listOf(segmentEntity(id = "old-seg", taskId = "task-old", startMinute = 480, endMinute = 540, isLocked = true)),
        )

        RoomPlanRepository(dao).placeTask("task-new", TEST_MONDAY, 600, 660, "focus")

        assertFalse(dao.getCurrentPlanWithSegments()!!.segments.first { it.taskId == "task-new" }.isLocked)
    }

    @Test
    fun `placeTask appends the new task to the order and marks the whole order manual`() = runTest {
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedOrder = listOf(
                orderEntity(taskId = "task-b", position = 1, isManual = false),
                orderEntity(taskId = "task-a", position = 0, isManual = false),
            ),
        )

        RoomPlanRepository(dao).placeTask("task-new", TEST_MONDAY, 600, 660, "focus")

        // The stored positions are read back in order, so the pre-existing order survives.
        assertEquals(listOf("task-a", "task-b", "task-new"), dao.currentOrder())
        assertEquals(listOf(0, 1, 2), dao.currentPositions())
        assertEquals(listOf(true, true, true), dao.currentManualFlags())
    }

    @Test
    fun `placeTask does not duplicate or move a task already in the order`() = runTest {
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedOrder = listOf(
                orderEntity(taskId = "task-a", position = 0),
                orderEntity(taskId = "task-b", position = 1),
            ),
        )

        RoomPlanRepository(dao).placeTask("task-a", TEST_MONDAY, 600, 660, "focus")

        assertEquals(listOf("task-a", "task-b"), dao.currentOrder())
        assertEquals(listOf(0, 1), dao.currentPositions())
    }

    // ------------------------------------------------------------------ movePlacement

    @Test
    fun `movePlacement is a silent no-op when there is no current plan`() = runTest {
        val dao = FakePlanDao()

        RoomPlanRepository(dao).movePlacement("seg-1", 600, 660, "focus")

        assertEquals(emptyList<PlanEntity>(), dao.plans)
        assertEquals(0, dao.archiveCount)
    }

    @Test
    fun `movePlacement is a silent no-op for an unknown segment id`() = runTest {
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedSegments = listOf(segmentEntity(id = "seg-1", startMinute = 480, endMinute = 540)),
        )

        RoomPlanRepository(dao).movePlacement("seg-missing", 600, 660, "focus")

        assertEquals(0, dao.archiveCount)
        assertEquals(1, dao.plans.size)
        assertEquals(480, dao.segments.single { it.id == "seg-1" }.startMinute)
    }

    @Test
    fun `movePlacement moves the segment, re-issues every identity and archives the old version`() = runTest {
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedSegments = listOf(
                segmentEntity(id = "seg-a", taskId = "task-a", startMinute = 480, endMinute = 540),
                segmentEntity(id = "seg-b", taskId = "task-b", startMinute = 600, endMinute = 660),
            ),
        )

        RoomPlanRepository(dao).movePlacement("seg-b", 720, 780, "review")

        val current = dao.getCurrentPlanWithSegments()!!
        val moved = current.segments.first { it.taskId == "task-b" }
        assertEquals(720, moved.startMinute)
        assertEquals(780, moved.endMinute)
        assertEquals("review", moved.trackId)
        assertEquals(TEST_MONDAY.toEpochDay(), moved.dateEpochDay)
        assertTrue(
            "every row in the new version is re-issued",
            current.segments.none { it.id == "seg-a" || it.id == "seg-b" },
        )
        // The superseded rows are not deleted: they still belong to the archived version, which is
        // what makes the old version restorable.
        assertTrue(dao.segments.any { it.id == "seg-a" && it.planId == "plan-1" })
        assertEquals(1, dao.archiveCount)
    }

    @Test
    fun `movePlacement requires explicitly unlocking the segment first`() = runTest {
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedSegments = listOf(segmentEntity(id = "seg-a", taskId = "task-a", startMinute = 480, endMinute = 540, isLocked = true)),
        )

        val repository = RoomPlanRepository(dao)
        val before = dao.getCurrentPlanWithSegments()
        var failure: Exception? = null
        try {
            repository.movePlacement("seg-a", 600, 660, "focus")
        } catch (error: Exception) {
            failure = error
        }
        assertTrue(failure is PlanLockedException)
        assertEquals(before, dao.getCurrentPlanWithSegments())
        assertEquals(1, dao.plans.size)

        repository.setSegmentLocked("seg-a", false)
        repository.movePlacement("seg-a", 600, 660, "focus")
        val moved = dao.getCurrentPlanWithSegments()!!.segments.single { it.taskId == "task-a" }
        assertEquals(600, moved.startMinute)
        assertFalse(moved.isLocked)
    }

    @Test
    fun `movePlacement leaves the task order and the other segments alone`() = runTest {
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedSegments = listOf(
                segmentEntity(id = "seg-a", taskId = "task-a", startMinute = 480, endMinute = 540),
                segmentEntity(id = "seg-b", taskId = "task-b", startMinute = 600, endMinute = 660),
            ),
            storedOrder = listOf(
                orderEntity(taskId = "task-b", position = 0, isManual = true),
                orderEntity(taskId = "task-a", position = 1),
            ),
        )

        RoomPlanRepository(dao).movePlacement("seg-b", 720, 780, "review")

        assertEquals(listOf("task-b", "task-a"), dao.currentOrder())
        assertEquals(listOf(0, 1), dao.currentPositions())
        assertEquals(listOf(true, false), dao.currentManualFlags())
        val untouched = dao.getCurrentPlanWithSegments()!!.segments.single { it.taskId == "task-a" }
        assertEquals(480, untouched.startMinute)
        assertEquals(540, untouched.endMinute)
    }

    @Test
    fun `movePlacement rejects an invalid range without writing`() {
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedSegments = listOf(segmentEntity(id = "seg-a", startMinute = 480, endMinute = 540)),
        )
        val repository = RoomPlanRepository(dao)

        rejection { repository.movePlacement("seg-a", 1_440, 1_500, "focus") }
        rejection { repository.movePlacement("seg-a", 600, 600, "focus") }

        assertEquals(0, dao.archiveCount)
        assertEquals(480, dao.segments.single { it.id == "seg-a" }.startMinute)
    }

    // ------------------------------------------------------------------ removePlacement

    @Test
    fun `removePlacement is a silent no-op when there is no current plan`() = runTest {
        val dao = FakePlanDao()

        RoomPlanRepository(dao).removePlacement("seg-1")

        assertEquals(emptyList<PlanEntity>(), dao.plans)
    }

    @Test
    fun `removePlacement is a silent no-op for an unknown segment id`() = runTest {
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedSegments = listOf(segmentEntity(id = "seg-a", startMinute = 480, endMinute = 540)),
        )

        RoomPlanRepository(dao).removePlacement("seg-missing")

        assertEquals(0, dao.archiveCount)
        assertEquals(1, dao.segments.size)
    }

    @Test
    fun `removePlacement drops the segment and keeps the rest`() = runTest {
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedSegments = listOf(
                segmentEntity(id = "seg-a", taskId = "task-a", startMinute = 480, endMinute = 540),
                segmentEntity(id = "seg-b", taskId = "task-b", startMinute = 600, endMinute = 660),
            ),
        )

        RoomPlanRepository(dao).removePlacement("seg-a")

        assertEquals(listOf("task-b"), dao.getCurrentPlanWithSegments()!!.segments.map(PlanSegmentEntity::taskId))
        assertEquals(1, dao.archiveCount)
    }

    @Test
    fun `removePlacement leaves the task order still naming the removed task`() = runTest {
        // FINDING, pinned rather than papered over. Only the segment row is dropped; the order row
        // survives. So `orderedTaskIds` keeps naming a task that has no placement left, and the
        // restored/exported plan advertises an order containing a task the schedule cannot show.
        // Blast radius, checked: PlanWithSegments.toDomain reads the order verbatim, and
        // LocalDataJsonExporter publishes orderedTaskIds unconditionally.
        val dao = daoWithCurrentPlan(
            planId = "plan-1",
            storedSegments = listOf(
                segmentEntity(id = "seg-a", taskId = "task-a", startMinute = 480, endMinute = 540),
                segmentEntity(id = "seg-b", taskId = "task-b", startMinute = 600, endMinute = 660),
            ),
            storedOrder = listOf(
                orderEntity(taskId = "task-a", position = 0),
                orderEntity(taskId = "task-b", position = 1),
            ),
        )

        RoomPlanRepository(dao).removePlacement("seg-a")

        assertEquals(listOf("task-b"), dao.getCurrentPlanWithSegments()!!.segments.map(PlanSegmentEntity::taskId))
        assertEquals(listOf("task-a", "task-b"), dao.currentOrder())
    }

    // ------------------------------------------------------------------ version clock

    @Test
    fun `a new version is always newer than the newest stored one`() = runTest {
        // `nextPlanCreatedAt` exists so that two confirmations inside one clock millisecond still
        // have a deterministic history order. Seeding a future timestamp makes it observable
        // without depending on the wall clock.
        val future = System.currentTimeMillis() + 1_000_000L
        val dao = daoWithCurrentPlan(planId = "old", createdAtEpochMillis = future)
        val repository = RoomPlanRepository(dao)

        repository.accept(planDraft())
        assertEquals(future + 1, dao.getCurrentPlanWithSegments()!!.plan.createdAtEpochMillis)

        repository.accept(planDraft())
        assertEquals(future + 2, dao.getCurrentPlanWithSegments()!!.plan.createdAtEpochMillis)
    }

    @Test
    fun `the version clock counts archived versions, not only the current one`() = runTest {
        val future = System.currentTimeMillis() + 1_000_000L
        val dao = FakePlanDao()
        dao.seed(plans = listOf(PlanEntity(id = "archived", createdAtEpochMillis = future, isCurrent = false)))

        RoomPlanRepository(dao).accept(planDraft())

        assertEquals(future + 1, dao.getCurrentPlanWithSegments()!!.plan.createdAtEpochMillis)
    }

    // ------------------------------------------------------------------ helpers

    /** Runs a suspending call that is expected to be refused by a `require`. */
    private fun rejection(block: suspend () -> Unit): IllegalArgumentException =
        assertThrows(IllegalArgumentException::class.java) { runTest { block() } }

    private fun day(offset: Long): Long = TEST_MONDAY.plusDays(offset).toEpochDay()

    private fun daoWithCurrentPlan(
        planId: String = "plan-1",
        createdAtEpochMillis: Long = 1_000L,
        storedSegments: List<PlanSegmentEntity> = emptyList(),
        storedOrder: List<PlanTaskOrderEntity> = emptyList(),
    ): FakePlanDao = FakePlanDao().apply {
        seed(
            plans = listOf(PlanEntity(id = planId, createdAtEpochMillis = createdAtEpochMillis, isCurrent = true)),
            segments = storedSegments,
            taskOrder = storedOrder,
        )
    }

    private suspend fun FakePlanDao.currentOrder(): List<String> =
        getCurrentPlanWithSegments()!!.taskOrder.sortedBy(PlanTaskOrderEntity::position)
            .map(PlanTaskOrderEntity::taskId)

    private suspend fun FakePlanDao.currentPositions(): List<Int> =
        getCurrentPlanWithSegments()!!.taskOrder.sortedBy(PlanTaskOrderEntity::position)
            .map(PlanTaskOrderEntity::position)

    private suspend fun FakePlanDao.currentManualFlags(): List<Boolean> =
        getCurrentPlanWithSegments()!!.taskOrder.sortedBy(PlanTaskOrderEntity::position)
            .map(PlanTaskOrderEntity::isManual)

    private fun segmentEntity(
        id: String,
        planId: String = "plan-1",
        taskId: String = "task-1",
        dateEpochDay: Long = TEST_MONDAY.toEpochDay(),
        startMinute: Int,
        endMinute: Int,
        isLocked: Boolean = false,
        trackId: String = "focus",
    ): PlanSegmentEntity = PlanSegmentEntity(
        id = id,
        planId = planId,
        taskId = taskId,
        dateEpochDay = dateEpochDay,
        startMinute = startMinute,
        endMinute = endMinute,
        isLocked = isLocked,
        trackId = trackId,
    )

    private fun orderEntity(
        planId: String = "plan-1",
        taskId: String,
        position: Int,
        isManual: Boolean = false,
    ): PlanTaskOrderEntity = PlanTaskOrderEntity(
        planId = planId,
        taskId = taskId,
        position = position,
        isManual = isManual,
    )
}
