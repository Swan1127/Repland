package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger

/** Tests only: real repositories retain transaction semantics, observations can fail. */
internal class ReadFaultTasks(private val base: TaskRepository) : TaskRepository by base {
    val failed = MutableStateFlow(false)
    val sources = AtomicInteger()
    override fun observeTasks(): Flow<List<Task>> {
        sources.incrementAndGet()
        return base.observeTasks().combine(failed) { value, fail -> check(!fail) { "QA read failure" }; value }
    }
}

internal class ReadFaultTime(private val base: TimeRepository) : TimeRepository by base {
    val failedPart = MutableStateFlow<String?>(null)
    val sources = AtomicInteger()
    override fun observeWeeklyBlocks(): Flow<List<WeeklyTimeBlock>> {
        sources.incrementAndGet()
        return guard("weekly", base.observeWeeklyBlocks())
    }
    override fun observeDateOverrides() = guard("dates", base.observeDateOverrides())
    override fun observeTimeConstraintSettings() = guard("settings", base.observeTimeConstraintSettings())
    private fun <T> guard(part: String, source: Flow<T>) = source.combine(failedPart) { value, failed ->
        check(part != failed) { "QA read failure" }; value
    }
}
