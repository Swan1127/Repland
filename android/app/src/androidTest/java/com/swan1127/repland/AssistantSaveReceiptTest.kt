package com.swan1127.repland

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.plan.PlanViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantSaveReceiptTest {
    @Test fun receipt_requires_commit_and_failed_retry_does_not_report_success() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        val vm = PlanViewModel(RoomPlanRepository(db), object : PlanDraftGenerator { override fun generate(input: PlanGenerationInput): PlanDraft = error("not used") })
        val store = ViewModelStore().apply { put("test", vm) }
        try {
            assertNull(vm.uiState.value.assistantReceipt)
            val draft = TaskDraft("new", "英语", "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 30, null)
            var success = 0
            vm.saveAssistantChanges(listOf(draft), emptyList(), emptySet(), LocalDate.now()) { success++ }
            val state = withTimeout(5_000) { vm.uiState.first { it.assistantReceipt != null && !it.isWorking } }
            assertEquals(AssistantSaveResult(1, 0, 0, LocalDate.now()), state.assistantReceipt!!.result)
            assertEquals(1, success); assertEquals(1, db.taskDao().getAll().size)
            vm.saveAssistantChanges(listOf(draft), emptyList(), emptySet(), LocalDate.now()) { success++ }
            val failed = withTimeout(5_000) { vm.uiState.first { it.errorMessage != null && !it.isWorking } }
            assertNull(failed.assistantReceipt); assertEquals(1, success)
        } finally { store.clear(); db.close() }
    }
}
