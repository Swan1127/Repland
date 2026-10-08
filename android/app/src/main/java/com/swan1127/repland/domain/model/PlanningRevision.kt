package com.swan1127.repland.domain.model

/** Detects edits, deletion, preference changes and changes to the confirmed plan. */
object PlanningRevision {
    fun of(input: PlanGenerationInput, current: ConfirmedPlan?, persistedOrder: List<String> = emptyList()): String {
        val source = listOf(
            input.tasks.sortedBy { it.id }, input.weeklyBlocks.sortedBy { it.id },
            input.dateOverrides.sortedBy { it.id }, input.semesterFirstWeekMonday,
            TaskCategory.knownEntries.map { CategoryPreferences.normalized(input.categoryPreferences).getValue(it) },
            current, persistedOrder,
        ).joinToString("\n")
        return java.security.MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
