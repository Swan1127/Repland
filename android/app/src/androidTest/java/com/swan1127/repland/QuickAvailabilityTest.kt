package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.plan.*
import com.swan1127.repland.ui.theme.ReplandTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickAvailabilityTest {
    @get:Rule val rule = createComposeRule()
    @Test fun minimal_card_requires_times_and_does_not_select_recurrence_by_default() {
        var saved: QuickAvailability? = null
        rule.setContent { ReplandTheme { QuickAvailabilityDialog(false, null, {}, { saved = it }) } }
        rule.onNodeWithTag("quick-availability-save").assertIsNotEnabled()
        rule.onNodeWithTag("quick-availability-start").performTextInput("18:00")
        rule.onNodeWithTag("quick-availability-end").performTextInput("20:00")
        rule.onNodeWithTag("quick-availability-save").performClick()
        assertEquals(1080, saved!!.startMinute); assertEquals(1200, saved!!.endMinute)
        assertFalse(saved!!.repeatWeekly)
    }
    @Test fun saved_slot_generates_revision_valid_preview_and_retry_does_not_duplicate_slot() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        val plans = RoomPlanRepository(db); val times = RoomTimeRepository(db.timeDao()); val tasks = RoomTaskRepository(db)
        val vm = PlanViewModel(plans, PlanGenerator, times, tasks)
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            tasks.save(TaskDraft("task", "复习", "", TaskCategory.COURSE, TaskPriority.HIGH, 1, 60, null))
            val value = QuickAvailability(LocalDate.now().plusDays(1), 1080, 1200)
            var saves = 0
            vm.saveAvailabilityAndGenerate(value, CategoryPreferences.defaults) { saves++ }
            val state = withTimeout(5_000) { vm.uiState.first { it.draft != null && !it.isWorking } }
            assertNull(state.currentPlan); assertEquals(1, saves)
            assertEquals(60, state.draft!!.segments.sumOf { it.endMinute - it.startMinute })
            plans.accept(state.draft!!)
            assertNotNull(plans.observeCurrentPlan().first())
            vm.saveAvailabilityAndGenerate(value, CategoryPreferences.defaults) { saves++ }
            withTimeout(5_000) { vm.uiState.first { it.draft?.sourceRevision != state.draft!!.sourceRevision && !it.isWorking } }
            assertEquals(2, saves); assertEquals(1, times.observeDateOverrides().first().size)
            assertTrue(times.observeWeeklyBlocks().first().isEmpty())
        } finally { store.clear(); db.close() }
    }
    @Test fun failed_generation_keeps_slot_and_original_plan_without_claiming_success() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        val times = RoomTimeRepository(db.timeDao()); val plans = RoomPlanRepository(db)
        val vm = PlanViewModel(plans, object : PlanDraftGenerator { override fun generate(input: PlanGenerationInput): PlanDraft = error("生成失败，可用时间已保存，请重试") }, times, RoomTaskRepository(db))
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            var saved = false
            vm.saveAvailabilityAndGenerate(QuickAvailability(LocalDate.now().plusDays(1), 1080, 1200, true), CategoryPreferences.defaults) { saved = true }
            withTimeout(5_000) { vm.uiState.first { it.errorMessage != null && !it.isWorking } }
            assertFalse(saved); assertNull(plans.observeCurrentPlan().first()); assertNull(plans.observeDraft().first())
            assertEquals(1, times.observeWeeklyBlocks().first().size)
        } finally { store.clear(); db.close() }
    }
}
