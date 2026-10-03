package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.agent.ArrangementAssistantViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class PlanningReadServiceTest {
    private lateinit var db: ReplandDatabase
    private lateinit var tasks: RoomTaskRepository
    private lateinit var plans: RoomPlanRepository
    private lateinit var time: RoomTimeRepository
    private lateinit var prefs: RoomCategoryPreferenceRepository
    private lateinit var settings: RoomAiSettingsRepository
    private lateinit var reads: PlanningReadService
    private val today = LocalDate.now()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).allowMainThreadQueries().build()
        tasks = RoomTaskRepository(db); plans = RoomPlanRepository(db); time = RoomTimeRepository(db.timeDao())
        prefs = RoomCategoryPreferenceRepository(db.categoryPreferenceDao()); settings = RoomAiSettingsRepository(db.aiSettingsDao())
        reads = PlanningReadService(tasks, time, plans, prefs) { today.atTime(9, 0) }
    }
    @After fun close() { db.close() }
    private suspend fun seed(id: String = "a", name: String = "英语") {
        tasks.save(TaskDraft(id, name, "不应发送的任务备注", TaskCategory.COURSE, TaskPriority.HIGH, 1, 60, today.plusDays(1)))
    }
    private fun request(words: String = "英语", ids: List<String> = listOf("a")) = ArrangementAssistantAdviceRequest(
        words, today.plusDays(1), emptyList(), ids.map { ArrangementExistingTask(it, "过时标题", TaskCategory.LEISURE, 15) })
    private fun advice() = ArrangementAssistantAdviceResult.Advice(ArrangementAssistantAdvice(
        listOf(ArrangementCandidate("英语", TaskCategory.COURSE, 30, ArrangementTimeHint(), setOf(ArrangementClarification.TIME), existingTaskId = "a")), "测试"))

    @Test fun reads_actual_facts_preferences_and_corrected_feedback_only() = runBlocking {
        seed(); seed("unrelated", "无关任务")
        tasks.recordFeedback("a", TaskFeedback(progressPercent = 20, completedContent = "旧内容"))
        val original = tasks.observeExecutionLogs("a").first().last()
        tasks.correctExecutionLog("a", original.id, TaskFeedback(progressPercent = 40, completedContent = "新内容"))
        tasks.recordFeedback("unrelated", TaskFeedback(completedContent = "无关反馈"))
        prefs.save(mapOf(TaskCategory.COURSE to 70, TaskCategory.OFFICE to 10, TaskCategory.LEISURE to 10, TaskCategory.EXTRACURRICULAR to 10))
        val source = reads.snapshot()
        val prepared = reads.prepare(request(ids = listOf("a", "unrelated")), source)
        val fact = prepared.existingTasks.single { it.id == "a" }
        assertEquals("英语", fact.title)
        assertEquals(TaskPriority.HIGH, fact.priority)
        assertEquals(today.plusDays(1), fact.dueDate)
        assertEquals(70, prepared.categoryPreferences[TaskCategory.COURSE])
        assertEquals(listOf("a"), prepared.taskFeedback.map { it.taskId })
        assertEquals("新内容", prepared.taskFeedback.single().feedback.single().completedContent)
        assertFalse(prepared.toString().contains("旧内容")); assertFalse(prepared.toString().contains("不应发送的任务备注"))
        assertFalse(prepared.toString().contains("无关反馈"))
        assertEquals(source.revision, prepared.sourceRevision)
    }
    @Test fun recomputes_available_and_protected_intervals_without_trusting_ui() = runBlocking {
        seed()
        val date = today.plusDays(1)
        time.saveDateOverride(DateOverrideDraft(title = "空档", type = DateOverrideType.AVAILABLE, date = date, startMinute = 540, endMinute = 720))
        time.saveDateOverride(DateOverrideDraft(title = "固定事项", type = DateOverrideType.BLOCKED, date = date, startMinute = 600, endMinute = 660))
        plans.accept(PlanDraft(LocalDateTime.now(), listOf(PlannedSegment(taskId = "a", date = date, startMinute = 540, endMinute = 570, isLocked = true)), emptyList(), emptyList(), listOf("a")))
        val prepared = reads.prepare(request(), reads.snapshot())
        assertEquals(listOf(ArrangementAvailableInterval(540, 600), ArrangementAvailableInterval(660, 720)), prepared.availableIntervals)
        assertTrue(prepared.occupiedIntervals.first { it.taskId == "a" }.isHardBusy)
        assertTrue(prepared.occupiedIntervals.first { it.title == "固定事项" }.isHardBusy)
    }
    @Test fun stale_source_is_rejected_before_provider_call() = runBlocking {
        seed(); settings.grantConsentAndEnable(); var calls = 0
        val vm = ArrangementAssistantViewModel(settings, object : ArrangementAssistantAdvisor {
            override suspend fun refine(request: ArrangementAssistantAdviceRequest): ArrangementAssistantAdviceResult { calls++; return advice() }
        }, reads)
        val result = vm.refine(request().copy(sourceRevision = "stale"))
        assertEquals(ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.SOURCE_CHANGED), result)
        assertEquals(0, calls)
    }
    @Test fun changing_preference_while_provider_runs_drops_response_without_writes() = runBlocking {
        seed(); settings.grantConsentAndEnable()
        val vm = ArrangementAssistantViewModel(settings, object : ArrangementAssistantAdvisor {
            override suspend fun refine(request: ArrangementAssistantAdviceRequest): ArrangementAssistantAdviceResult {
                prefs.save(mapOf(TaskCategory.COURSE to 70, TaskCategory.OFFICE to 10, TaskCategory.LEISURE to 10, TaskCategory.EXTRACURRICULAR to 10))
                return advice()
            }
        }, reads)
        assertEquals(ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.SOURCE_CHANGED), vm.refine(request()))
        assertNull(plans.observeCurrentPlan().first()); assertNull(plans.observeDraft().first())
        assertEquals(1, tasks.observeTasks().first().size)
    }
    @Test fun feedback_is_bounded_to_relevant_tasks_and_field_lengths() = runBlocking {
        val ids = (1..8).map { "task-$it" }
        ids.forEach { id -> seed(id, id); repeat(4) { tasks.recordFeedback(id, TaskFeedback(completedContent = "字".repeat(500))) } }
        val prepared = reads.prepare(request(ids.joinToString(" "), ids), reads.snapshot())
        assertEquals(5, prepared.taskFeedback.size)
        assertTrue(prepared.taskFeedback.all { it.feedback.size == 3 && it.feedback.all { f -> f.completedContent!!.length == 200 } })
    }
    @Test fun disabled_context_never_reads_or_calls_provider_and_closed_refs_do_not_resurrect() = runBlocking {
        seed(); tasks.confirmStatus("a", TaskStatus.COMPLETED)
        val prepared = reads.prepare(request(), reads.snapshot())
        assertTrue(prepared.existingTasks.isEmpty())
        var calls = 0
        val vm = ArrangementAssistantViewModel(settings, object : ArrangementAssistantAdvisor {
            override suspend fun refine(request: ArrangementAssistantAdviceRequest): ArrangementAssistantAdviceResult { calls++; return advice() }
        }, reads)
        assertEquals(ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.DISABLED), vm.refine(request()))
        settings.grantConsentAndEnable()
        val draft = ArrangementCandidate("英语", TaskCategory.COURSE, 30, ArrangementTimeHint(), emptySet(), existingTaskId = "a", proposalId = "draft")
        assertEquals(ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.SOURCE_CHANGED), vm.refine(request().copy(draftCandidates = listOf(draft))))
        assertEquals(0, calls)
    }
}
