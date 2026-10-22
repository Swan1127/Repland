package com.swan1127.repland.testkit

import com.swan1127.repland.data.room.AiSettingsDao
import com.swan1127.repland.data.room.AiSettingsEntity
import com.swan1127.repland.data.room.CategoryPreferenceDao
import com.swan1127.repland.data.room.CategoryPreferenceEntity
import com.swan1127.repland.data.room.PlanDao
import com.swan1127.repland.data.room.PlanEntity
import com.swan1127.repland.data.room.PlanSegmentEntity
import com.swan1127.repland.data.room.PlanTaskOrderEntity
import com.swan1127.repland.data.room.PlanWithSegments
import com.swan1127.repland.data.room.ReminderSettingsDao
import com.swan1127.repland.data.room.ReminderSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written stand-ins for the Room DAOs.
 *
 * Four repositories take a **DAO interface** rather than the `ReplandDatabase`, which is what
 * makes them reachable from a plain JVM test: `RoomPlanRepository`, `RoomAiSettingsRepository`,
 * `RoomCategoryPreferenceRepository` and `RoomReminderSettingsRepository`. The other four
 * (`RoomTaskRepository`, `RoomTimeRepository`, `RoomEngagementRepository`,
 * `RoomProfileEvidenceRepository`) take the concrete database and still need Robolectric.
 *
 * **What these fakes do and do not stand in for.** A fake DAO is a *model of the SQL*, written by
 * hand from the annotations. It therefore covers the repository's own logic — the mapping, the
 * guards, the ordering, the defaults — but it cannot cover:
 *
 * - the `@Query` text itself (a wrong `WHERE` clause is invisible here),
 * - `@Relation` loading semantics (the fake composes `PlanWithSegments` by filtering on `planId`,
 *   which is what the annotation *means*, not what Room *does*),
 * - `OnConflictStrategy.ABORT` (mirrored below by an explicit `require`, so a duplicate key fails
 *   loudly — but the real enforcement is SQLite's).
 *
 * Every `observe`-style method is backed by a real `MutableStateFlow`, so a test can assert on the
 * repository's `Flow` mapping without needing a collector to drive it.
 */

/**
 * The largest fake here, and the reason this batch is worth writing: `PlanDao.replaceCurrentPlan`
 * has a **default body** in the interface, and this fake deliberately does not override it. The
 * inherited body runs for real — `archiveCurrentPlan()`, then `insertPlan`, then `insertSegments`,
 * then `insertTaskOrder` — so the DAO's own composition order is exercised by every plan test.
 */
class FakePlanDao : PlanDao {
    val plans = mutableListOf<PlanEntity>()
    val segments = mutableListOf<PlanSegmentEntity>()
    val taskOrder = mutableListOf<PlanTaskOrderEntity>()

    /** Thrown from `insertPlan`, i.e. after the archive step has already happened. */
    var insertPlanFailure: Throwable? = null

    var archiveCount = 0
        private set

    private val currentPlanFlow = MutableStateFlow<PlanWithSegments?>(null)
    private val historyFlow = MutableStateFlow<List<PlanWithSegments>>(emptyList())

    /**
     * Pre-loads rows and refreshes the observe flows, as if those rows had been written by an
     * earlier session. Without the refresh the `observe` flows would still report the empty
     * initial value, which is a trap this fake must not have.
     */
    fun seed(
        plans: List<PlanEntity> = emptyList(),
        segments: List<PlanSegmentEntity> = emptyList(),
        taskOrder: List<PlanTaskOrderEntity> = emptyList(),
    ) {
        this.plans += plans
        this.segments += segments
        this.taskOrder += taskOrder
        refresh()
    }

    // ---------------------------------------------------------------- reads

    override fun observeCurrentPlan(): Flow<PlanWithSegments?> = currentPlanFlow

    override fun observePlanHistory(): Flow<List<PlanWithSegments>> = historyFlow

    override suspend fun getCurrentPlanWithSegments(): PlanWithSegments? =
        plans.firstOrNull(PlanEntity::isCurrent)?.let { snapshot(it.id) }

    override suspend fun getAllPlansWithSegments(): List<PlanWithSegments> = historyOrder()

    override suspend fun getPlanWithSegments(id: String): PlanWithSegments? = snapshot(id)

    /** Mirrors `SELECT MAX(createdAtEpochMillis) FROM plans` — over every plan, archived included. */
    override suspend fun latestCreatedAt(): Long? = plans.maxOfOrNull(PlanEntity::createdAtEpochMillis)

    // ---------------------------------------------------------------- writes

    override suspend fun archiveCurrentPlan() {
        archiveCount += 1
        for (index in plans.indices) {
            if (plans[index].isCurrent) plans[index] = plans[index].copy(isCurrent = false)
        }
        refresh()
    }

    /**
     * Mirrors the DAO's `WHERE id = :segmentId AND planId IN (SELECT id FROM plans WHERE isCurrent = 1)`.
     * The current-plan restriction is part of the SQL, not of the repository, so a test that relies
     * on it is testing this fake rather than the production code — see the class comment.
     */
    override suspend fun setSegmentLocked(segmentId: String, isLocked: Boolean) {
        val currentPlanIds = plans.filter(PlanEntity::isCurrent).map(PlanEntity::id).toSet()
        for (index in segments.indices) {
            val segment = segments[index]
            if (segment.id == segmentId && segment.planId in currentPlanIds) {
                segments[index] = segment.copy(isLocked = isLocked)
            }
        }
        refresh()
    }

    override suspend fun insertPlan(plan: PlanEntity) {
        insertPlanFailure?.let { throw it }
        require(plans.none { it.id == plan.id }) { "ABORT: duplicate plan id ${plan.id}" }
        plans += plan
        refresh()
    }

    override suspend fun insertSegments(segments: List<PlanSegmentEntity>) {
        require(segments.none { incoming -> this.segments.any { it.id == incoming.id } }) {
            "ABORT: duplicate plan_segments id"
        }
        this.segments += segments
        refresh()
    }

    override suspend fun insertTaskOrder(taskOrder: List<PlanTaskOrderEntity>) {
        require(
            taskOrder.none { incoming ->
                this.taskOrder.any { it.planId == incoming.planId && it.taskId == incoming.taskId }
            },
        ) { "ABORT: duplicate plan_task_order key" }
        this.taskOrder += taskOrder
        refresh()
    }

    // ---------------------------------------------------------------- internals

    private fun snapshot(id: String): PlanWithSegments? {
        val plan = plans.firstOrNull { it.id == id } ?: return null
        return PlanWithSegments(
            plan = plan,
            segments = segments.filter { it.planId == id },
            taskOrder = taskOrder.filter { it.planId == id },
        )
    }

    /** Mirrors `ORDER BY createdAtEpochMillis DESC, id DESC`. */
    private fun historyOrder(): List<PlanWithSegments> =
        plans.sortedWith(compareByDescending<PlanEntity> { it.createdAtEpochMillis }.thenByDescending { it.id })
            .mapNotNull { snapshot(it.id) }

    private fun refresh() {
        currentPlanFlow.value = plans.firstOrNull(PlanEntity::isCurrent)?.let { snapshot(it.id) }
        historyFlow.value = historyOrder()
    }
}

/** `ai_settings` holds a single row keyed by [AiSettingsEntity.id]'s default. */
class FakeAiSettingsDao : AiSettingsDao {
    var stored: AiSettingsEntity? = null
    var saveCount = 0
        private set
    var failWith: Throwable? = null

    private val flow = MutableStateFlow<AiSettingsEntity?>(null)

    /** Pre-loads the row and refreshes the observe flow. See [FakePlanDao.seed]. */
    fun seed(entity: AiSettingsEntity?) {
        stored = entity
        flow.value = entity
    }

    override fun observe(id: String): Flow<AiSettingsEntity?> = flow

    override suspend fun get(id: String): AiSettingsEntity? = stored

    override suspend fun save(settings: AiSettingsEntity) {
        failWith?.let { throw it }
        saveCount += 1
        stored = settings
        flow.value = settings
    }
}

/** Mirrors `ORDER BY category ASC` on both reads, which is *not* the enum's declaration order. */
class FakeCategoryPreferenceDao : CategoryPreferenceDao {
    var stored = mutableListOf<CategoryPreferenceEntity>()
    var upsertCount = 0
        private set
    var failWith: Throwable? = null

    private val flow = MutableStateFlow<List<CategoryPreferenceEntity>>(emptyList())

    /** Pre-loads the rows and refreshes the observe flow. See [FakePlanDao.seed]. */
    fun seed(entities: List<CategoryPreferenceEntity>) {
        stored = entities.toMutableList()
        flow.value = alphabetical()
    }

    override fun observeAll(): Flow<List<CategoryPreferenceEntity>> = flow

    override suspend fun getAll(): List<CategoryPreferenceEntity> = alphabetical()

    override suspend fun upsertAll(preferences: List<CategoryPreferenceEntity>) {
        failWith?.let { throw it }
        upsertCount += 1
        // REPLACE semantics for a table keyed by `category`.
        stored = preferences.toMutableList()
        flow.value = alphabetical()
    }

    private fun alphabetical(): List<CategoryPreferenceEntity> = stored.sortedBy { it.category }
}

class FakeReminderSettingsDao : ReminderSettingsDao {
    var stored: ReminderSettingsEntity? = null
    var saveCount = 0
        private set
    var failWith: Throwable? = null

    private val flow = MutableStateFlow<ReminderSettingsEntity?>(null)

    /** Pre-loads the row and refreshes the observe flow. See [FakePlanDao.seed]. */
    fun seed(entity: ReminderSettingsEntity?) {
        stored = entity
        flow.value = entity
    }

    override fun observe(id: String): Flow<ReminderSettingsEntity?> = flow

    override suspend fun get(id: String): ReminderSettingsEntity? = stored

    override suspend fun save(settings: ReminderSettingsEntity) {
        failWith?.let { throw it }
        saveCount += 1
        stored = settings
        flow.value = settings
    }
}
