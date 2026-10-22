package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Exercise the real transaction and durable draft encoding, rather than a fake CAS. */
class PlanningTeamStorageIntegrationTest {
    private fun verify(block: suspend (ReplandDatabase, RoomTaskRepository, RoomPlanRepository, PlanningOperationService) -> Unit) = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val tasks = RoomTaskRepository(database)
            val plans = RoomPlanRepository(database)
            val time = RoomTimeRepository(database.timeDao())
            tasks.save(TaskDraft(id = "learning", displayName = "复习测试", category = TaskCategory.COURSE, totalDurationMinutes = 30))
            time.saveDateOverride(DateOverrideDraft(title = "测试可用时间", type = DateOverrideType.AVAILABLE,
                date = LocalDate.now().plusDays(1), startMinute = 540, endMinute = 720))
            val reads = PlanningReadService(tasks, time, plans, RoomCategoryPreferenceRepository(database.categoryPreferenceDao()))
            block(database, tasks, plans, PlanningOperationService(reads, plans, PlanGenerator))
        } finally { database.close() }
    }

    @Test fun generated_draft_cannot_replace_a_concurrent_edit_or_a_new_input() = verify { _, tasks, plans, service ->
        val generated = service.preview(PlanningPreviewKind.FORMULATE)
        val edited = generated.copy(numericAdvice = listOf("用户自己的预览备注"))
        plans.replaceDraftIfCurrent(generated, edited)
        assertTrue(runCatching { plans.saveGeneratedDraftIfCurrent(generated, generated) }.isFailure)
        assertEquals(edited, plans.observeDraft().first())
        tasks.save(TaskDraft(id = "later", displayName = "生成后新增", category = TaskCategory.COURSE, totalDurationMinutes = 30))
        assertTrue(runCatching { plans.saveGeneratedDraftIfCurrent(edited, generated) }.isFailure)
        assertEquals(edited, plans.observeDraft().first())
        assertNull(plans.observeCurrentPlan().first())
    }

    @Test fun confirmation_requires_the_current_durable_preview_and_is_idempotent() = verify { _, _, plans, service ->
        val original = service.preview(PlanningPreviewKind.FORMULATE)
        val edited = original.copy(numericAdvice = listOf("修改后预览"))
        plans.replaceDraftIfCurrent(original, edited)
        assertTrue(runCatching { service.confirm(original) }.isFailure)
        assertNull(plans.observeCurrentPlan().first())
        service.confirm(edited)
        val accepted = plans.observeCurrentPlan().first()
        assertNotNull(accepted)
        assertTrue(runCatching { service.confirm(edited) }.isFailure)
        assertEquals(accepted, plans.observeCurrentPlan().first())
        assertEquals(1, plans.observePlanHistory().first().size)
    }

    @Test fun memory_reference_guard_survives_encoding_and_cannot_be_removed_by_editing() = verify { db, tasks, plans, _ ->
        val time = RoomTimeRepository(db.timeDao())
        val reads = PlanningReadService(tasks, time, plans, RoomCategoryPreferenceRepository(db.categoryPreferenceDao()))
        var reference: MemoryReference? = null
        fun service() = PlanningOperationService(reads, plans, PlanGenerator, memoryReference = { reference })
        val draft = service().preview(PlanningPreviewKind.FORMULATE)
        val reloaded = plans.observeDraft().first()!!
        assertNotNull(reloaded.memoryReferenceRevision)
        assertEquals(draft, PlanDraftCodec.decode(PlanDraftCodec.encode(draft)))
        assertTrue(runCatching { plans.replaceDraftIfCurrent(reloaded, reloaded.copy(memoryReferenceRevision = null)) }.isFailure)
        reference = MemoryReference.empty(LocalDate.now())
        assertTrue(runCatching { service().confirm(reloaded) }.isFailure)
        assertNull(plans.observeCurrentPlan().first())
        assertEquals(reloaded, plans.observeDraft().first())
        reference = null
        service().confirm(reloaded)
        assertNotNull(plans.observeCurrentPlan().first())
    }

    @Test fun legacy_draft_codec_keeps_backward_compatibility() = verify { _, _, _, service ->
        val draft = service.preview(PlanningPreviewKind.FORMULATE)
        val legacy = org.json.JSONObject(PlanDraftCodec.encode(draft)).apply { remove("memoryReferenceRevision") }.toString()
        assertEquals(draft.copy(memoryReferenceRevision = null), PlanDraftCodec.decode(legacy))
    }
}
