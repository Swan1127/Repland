package com.swan1127.repland.domain.model

import java.time.LocalDate

data class MemorySignal(
    val taskId: String,
    val content: String,
    val stabilityDays: Double,
    val retrievability: Double,
    val dueOn: LocalDate,
    val pressure: Double,
) {
    val isAtRisk: Boolean get() = retrievability <= MemoryReference.AtRiskRetention
}

data class MemoryEntry(val taskId: String, val content: String, val state: MemoryState)

data class MemoryReference(
    val asOf: LocalDate,
    val signals: List<MemorySignal>,
) {
    val isEmpty: Boolean get() = signals.isEmpty()

    val dueCount: Int get() = signals.count { !it.dueOn.isAfter(asOf) }

    val atRisk: List<MemorySignal> get() = signals.filter { it.isAtRisk }

    val meanRetrievability: Double?
        get() = signals.takeIf { it.isNotEmpty() }?.let { it.map(MemorySignal::retrievability).average() }

    
    fun forTask(taskId: String): MemorySignal? =
        signals.filter { it.taskId == taskId }.minByOrNull { it.retrievability }

    
    fun logNote(): String {
        if (isEmpty) return "记忆参考：暂无复习点"
        val mean = meanRetrievability?.let { "，平均可提取率 ${(it * 100).toInt()}%" } ?: ""
        return "记忆参考：${signals.size} 条，已到期 $dueCount 条，濒临遗忘 ${atRisk.size} 条$mean"
    }

    companion object {
        
        const val AtRiskRetention: Double = 0.6

        fun of(entries: List<MemoryEntry>, today: LocalDate): MemoryReference = MemoryReference(
            asOf = today,
            signals = entries.map { entry ->
                MemorySignal(
                    taskId = entry.taskId,
                    content = entry.content,
                    stabilityDays = entry.state.stabilityDays,
                    retrievability = MemoryModel.retrievabilityAt(entry.state, today),
                    dueOn = MemoryModel.dueOn(entry.state, today),
                    pressure = MemoryModel.reviewPressure(entry.state, today),
                )
            }.sortedBy { it.retrievability },
        )

        
        fun empty(today: LocalDate): MemoryReference = MemoryReference(today, emptyList())
    }
}
