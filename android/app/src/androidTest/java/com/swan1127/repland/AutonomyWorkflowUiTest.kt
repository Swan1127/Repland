package com.swan1127.repland

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.AutonomyRepository
import com.swan1127.repland.ui.agent.*
import com.swan1127.repland.ui.theme.ReplandTheme
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Exercises real UI -> language routing -> transactional storage -> planner -> undo. */
@RunWith(AndroidJUnit4::class)
class AutonomyWorkflowUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var db: ReplandDatabase
    private lateinit var vm: AutonomyViewModel
    private val store = ViewModelStore()
    private val reconciliations = AtomicInteger()

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java)
            .allowMainThreadQueries().build()
        val tasks = RoomTaskRepository(db)
        val plans = RoomPlanRepository(db)
        val time = RoomTimeRepository(db.timeDao())
        val reads = PlanningReadService(tasks, time, plans, RoomCategoryPreferenceRepository(db.categoryPreferenceDao()))
        runBlocking {
            time.saveDateOverride(DateOverrideDraft(title = "明天可用", date = LocalDate.now().plusDays(1),
                type = DateOverrideType.AVAILABLE, startMinute = 14 * 60, endMinute = 18 * 60))
        }
        val storedAutonomy = RoomAutonomyRepository(db)
        val autonomy = object : AutonomyRepository by storedAutonomy {
            override suspend fun reconcile(): String? {
                val result = storedAutonomy.reconcile()
                reconciliations.incrementAndGet()
                return result
            }
        }
        rule.runOnIdle {
            vm = ViewModelProvider(store, AutonomyViewModel.Factory(autonomy, reads,
                PlanningOperationService(reads, plans, PlanGenerator)))[AutonomyViewModel::class.java]
        }
        rule.setContent {
            val state by vm.state.collectAsState()
            val items by tasks.observeTasks().collectAsState(emptyList())
            ReplandTheme {
                AutonomousAssistantScreen(state, items,
                    onSubmit = { vm.submit(it, false) { ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED) } },
                    onUndo = vm::undo, onOpenTask = {})
            }
        }
        rule.waitUntil(10_000) { vm.state.value.ready }
    }

    @After fun close() {
        rule.runOnIdle { store.clear() }
        db.close()
    }

    private fun send(text: String) {
        rule.onNodeWithTag("autonomous-input").performScrollTo().performTextClearance()
        rule.onNodeWithTag("autonomous-input").performTextInput(text)
        rule.onNodeWithTag("autonomous-submit").performScrollTo().performClick()
    }

    @Test fun capture_progress_and_undo_preserve_real_evidence_and_time_window() {
        send("明天下午复习物理45分钟")
        rule.waitUntil(10_000) { vm.state.value.submission == 1L }
        val task = runBlocking { db.taskDao().getAll().single().toDomain() }
        assertEquals("复习物理", task.displayName)
        assertEquals(45, task.totalDurationMinutes)
        assertEquals(TaskInputSource.USER_INPUT, task.inputSources.duration)
        assertNull(task.actualDurationMinutes)
        val before = runBlocking { db.planDao().getCurrentPlanWithSegments()!!.toDomain() }
        assertTrue(before.segments.isNotEmpty())
        assertTrue(before.segments.all { it.date == LocalDate.now().plusDays(1) && it.startMinute >= 14 * 60 && it.endMinute <= 18 * 60 })
        send("复习物理完成了一半")
        rule.waitUntil(10_000) { vm.state.value.submission == 2L }
        assertEquals(50, runBlocking { db.taskDao().getById(task.id)!!.progressPercent })
        assertNull(runBlocking { db.taskDao().getById(task.id)!!.actualDurationMinutes })
        rule.onNodeWithTag("autonomy-undo").performScrollTo().performClick()
        rule.waitUntil(10_000) { !vm.state.value.busy && !vm.state.value.delegation.canUndo }
        val restored = runBlocking { db.taskDao().getById(task.id)!!.toDomain() }
        assertNull(restored.progressPercent)
        assertEquals(TaskStatus.NOT_STARTED, restored.status)
        val logs = runBlocking { db.executionLogDao().getForTask(task.id) }
        assertEquals(2, logs.size)
        assertEquals(ExecutionLogEventType.PARTIAL_COMPLETION.name, logs.first().eventType)
        assertEquals(ExecutionLogEventType.CORRECTION.name, logs.last().eventType)
        assertEquals(logs.first().id, logs.last().correctedLogId)
        assertTrue(logs.all { it.actualDurationMinutes == null })
    }

    @Test fun ambiguity_keeps_input_and_does_not_write_a_completion() {
        runBlocking {
            RoomTaskRepository(db).save(TaskDraft(id = "first", displayName = "报告", totalDurationMinutes = 30))
            RoomTaskRepository(db).save(TaskDraft(id = "second", displayName = "报告", totalDurationMinutes = 30))
        }
        send("报告写完了")
        rule.waitUntil(10_000) { vm.state.value.completedAttempts == 1L }
        assertEquals(0L, vm.state.value.submission)
        assertNotNull(vm.state.value.error)
        rule.onNodeWithTag("autonomous-input").performScrollTo().assertTextContains("报告写完了")
        assertTrue(runBlocking { db.taskDao().getAll().all { it.status == TaskStatus.NOT_STARTED.name } })
        assertTrue(runBlocking { db.executionLogDao().getAll().isEmpty() })
        send("查看待办")
        rule.waitUntil(10_000) { vm.state.value.submission == 1L }
        assertEquals(2, vm.state.value.queryResults!!.size)
        assertEquals(2, runBlocking { db.taskDao().getAll().size })
    }

    @Test fun automatic_replan_replaces_old_query_with_its_current_undo_receipt() {
        send("明天下午复习物理45分钟")
        rule.waitUntil(10_000) { vm.state.value.submission == 1L }
        val before = runBlocking { db.planDao().getCurrentPlanWithSegments()!!.toDomain() }
        val occupied = before.segments.first()
        send("查看待办")
        rule.waitUntil(10_000) { vm.state.value.submission == 2L }
        assertEquals(1, vm.state.value.queryResults!!.size)
        val attempts = vm.state.value.completedAttempts

        runBlocking {
            RoomTimeRepository(db.timeDao()).saveDateOverride(DateOverrideDraft(
                title = "临时会议", date = occupied.date, type = DateOverrideType.BLOCKED,
                startMinute = occupied.startMinute, endMinute = occupied.endMinute))
        }
        rule.waitUntil(10_000) {
            val current = vm.state.value
            current.response?.startsWith("已根据任务和时间变化调整后续安排。") == true &&
                current.delegation.summary == current.response && current.delegation.canUndo
        }
        val after = runBlocking { db.planDao().getCurrentPlanWithSegments()!!.toDomain() }
        assertTrue(after.segments.isNotEmpty())
        assertTrue(after.segments.none { it.date == occupied.date &&
            it.startMinute < occupied.endMinute && it.endMinute > occupied.startMinute })
        assertNull(vm.state.value.queryResults)
        assertEquals(2L, vm.state.value.submission)
        assertEquals(attempts, vm.state.value.completedAttempts)
        rule.onNodeWithTag("autonomous-response").performScrollTo()
            .assertTextEquals(vm.state.value.delegation.summary!!)
        rule.onNodeWithTag("autonomy-undo").assertExists()
    }

    @Test fun late_repository_state_and_noop_reconcile_preserve_the_latest_explicit_reply() {
        send("明天下午复习物理45分钟")
        rule.waitUntil(10_000) { vm.state.value.submission == 1L && vm.state.value.delegation.canUndo }
        val captureResponse = vm.state.value.response
        assertTrue(captureResponse!!.startsWith("已用本地规则处理。"))
        // This emits a new repository state with the plain stored receipt. The richer
        // reply produced by the completed command must retain its local/AI prefix.
        runBlocking { RoomAutonomyRepository(db).setEnabled(false) }
        rule.waitUntil(10_000) { !vm.state.value.delegation.enabled }
        assertEquals(captureResponse, vm.state.value.response)

        send("查看待办")
        rule.waitUntil(10_000) { vm.state.value.submission == 2L }
        val queryResponse = vm.state.value.response
        val results = vm.state.value.queryResults
        assertEquals(1, results!!.size)
        runBlocking { RoomAutonomyRepository(db).setEnabled(true) }
        rule.waitUntil(10_000) { vm.state.value.delegation.enabled }
        val calls = reconciliations.get()
        rule.runOnIdle { vm.onForeground() }
        rule.waitUntil(10_000) { reconciliations.get() > calls }
        assertEquals(queryResponse, vm.state.value.response)
        assertEquals(results, vm.state.value.queryResults)
        rule.onNodeWithTag("autonomous-response").performScrollTo().assertTextEquals(queryResponse!!)
    }
}
