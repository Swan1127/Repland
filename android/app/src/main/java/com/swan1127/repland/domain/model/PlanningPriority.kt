package com.swan1127.repland.domain.model

import java.time.LocalDate
import kotlin.math.roundToInt

/** User-owned relative weights for the four task categories. */
object CategoryPreferences {
    val defaults: Map<TaskCategory, Int> = TaskCategory.entries.associateWith(TaskCategory::defaultWeight)

    fun normalized(weights: Map<TaskCategory, Int>): Map<TaskCategory, Int> =
        TaskCategory.entries.associateWith { category -> weights[category] ?: defaults.getValue(category) }

    fun isValid(weights: Map<TaskCategory, Int>): Boolean {
        val normalized = normalized(weights)
        return normalized.values.all { it in 0..100 } && normalized.values.sum() > 0
    }
}

enum class PriorityReasonKind {
    INITIAL_PRIORITY,
    CATEGORY_PREFERENCE,
    OVERDUE,
    DEADLINE,
    POSTPONEMENTS,
    LOCAL_AI_NEUTRAL,
}

data class PriorityReason(
    val kind: PriorityReasonKind,
    val value: Int? = null,
)

/** Explainable local-only result. It never changes Task.userPriority. */
data class LocalPriorityAssessment(
    val taskId: String,
    val score: Int,
    val reasons: List<PriorityReason>,
)

/**
 * Deterministic local fallback for dynamic priority.
 *
 * The AI-fit portion (45%) is deliberately fixed at neutral 50. The context portion
 * (15%) is instead calculated only from observable deadline and postponement evidence.
 */
object LocalPriorityRanker {
    private const val AI_NEUTRAL_SCORE = 50.0
    private const val AI_FIT_WEIGHT = 0.45
    private const val INITIAL_PRIORITY_WEIGHT = 0.25
    private const val CATEGORY_WEIGHT = 0.15
    private const val CONTEXT_WEIGHT = 0.15

    fun rank(
        tasks: List<Task>,
        categoryPreferences: Map<TaskCategory, Int> = CategoryPreferences.defaults,
        today: LocalDate,
        manualTaskOrder: List<String> = emptyList(),
    ): List<LocalPriorityAssessment> {
        val normalizedPreferences = CategoryPreferences.normalized(categoryPreferences)
        require(CategoryPreferences.isValid(normalizedPreferences)) { "Category preferences are invalid." }
        val taskById = tasks.associateBy(Task::id)
        val assessments = tasks.associate { task ->
            task.id to assess(task, normalizedPreferences, today)
        }
        val manualPosition = manualTaskOrder
            .distinct()
            .filter(taskById::containsKey)
            .mapIndexed { index, id -> id to index }
            .toMap()
        return tasks.sortedWith(
            compareBy<Task> { task -> manualPosition[task.id] ?: Int.MAX_VALUE }
                .thenByDescending { task -> assessments.getValue(task.id).score }
                .thenByDescending { task -> task.userPriority.score }
                .thenBy { task -> task.dueDate ?: LocalDate.MAX }
                .thenByDescending(Task::postponeCount)
                .thenBy(Task::createdAtEpochMillis)
                .thenBy(Task::id),
        ).map { task -> assessments.getValue(task.id) }
    }

    private fun assess(
        task: Task,
        categoryPreferences: Map<TaskCategory, Int>,
        today: LocalDate,
    ): LocalPriorityAssessment {
        val categoryScore = categoryScore(task.category, categoryPreferences)
        val deadlineScore = deadlineScore(task.dueDate, today)
        val postponementScore = (50 + task.postponeCount * 15).coerceAtMost(100)
        val contextScore = maxOf(deadlineScore, postponementScore)
        val score = (
            AI_NEUTRAL_SCORE * AI_FIT_WEIGHT +
                task.userPriority.score * INITIAL_PRIORITY_WEIGHT +
                categoryScore * CATEGORY_WEIGHT +
                contextScore * CONTEXT_WEIGHT
            ).roundToInt().coerceIn(0, 100)

        return LocalPriorityAssessment(
            taskId = task.id,
            score = score,
            reasons = buildList {
                add(PriorityReason(PriorityReasonKind.INITIAL_PRIORITY, task.userPriority.score))
                add(PriorityReason(PriorityReasonKind.CATEGORY_PREFERENCE, categoryPreferences.getValue(task.category)))
                if (task.dueDate != null) {
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, task.dueDate).toInt()
                    add(
                        PriorityReason(
                            if (days < 0) PriorityReasonKind.OVERDUE else PriorityReasonKind.DEADLINE,
                            kotlin.math.abs(days),
                        ),
                    )
                }
                if (task.postponeCount > 0) add(PriorityReason(PriorityReasonKind.POSTPONEMENTS, task.postponeCount))
                add(PriorityReason(PriorityReasonKind.LOCAL_AI_NEUTRAL))
            },
        )
    }

    private fun categoryScore(
        category: TaskCategory,
        preferences: Map<TaskCategory, Int>,
    ): Double {
        val maximum = preferences.values.maxOrNull()?.takeIf { it > 0 } ?: return 0.0
        return preferences.getValue(category).toDouble() / maximum * 100.0
    }

    private fun deadlineScore(dueDate: LocalDate?, today: LocalDate): Int {
        if (dueDate == null) return 50
        return when (val days = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate).toInt()) {
            in Int.MIN_VALUE..-1 -> 100
            0 -> 98
            1 -> 92
            2 -> 84
            in 3..5 -> 74
            in 6..10 -> 64
            else -> 50
        }
    }
}

enum class UnscheduledReason {
    TOTAL_CAPACITY_IN_ROLLING_WINDOW,
    CAPACITY_BEFORE_DUE_DATE,
    CAPACITY_IN_ROLLING_WINDOW,
}
