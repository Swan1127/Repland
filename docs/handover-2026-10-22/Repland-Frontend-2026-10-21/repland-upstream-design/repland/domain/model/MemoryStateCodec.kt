package com.swan1127.repland.domain.model

import java.time.LocalDate

object MemoryStateCodec {
    const val Version = 1

    fun encode(state: MemoryState): Map<String, String> = mapOf(
        "version" to Version.toString(),
        "capturedOn" to state.capturedOn.toString(),
        "lastEventOn" to state.lastEventOn?.toString().orEmpty(),
        "stabilityDays" to state.stabilityDays.toString(),
        "difficulty" to state.difficulty.toString(),
        "difficultySeed" to state.difficultySeed.toString(),
        "importance" to state.importance.toString(),
        "reviews" to state.reviews.toString(),
        "lapses" to state.lapses.toString(),
        "exposures" to state.exposures.toString(),
    )

    fun decode(fields: Map<String, String?>): MemoryState? {
        if (fields["version"]?.toIntOrNull() != Version) return null
        val capturedOn = parseDate(fields["capturedOn"]) ?: return null
        val lastEvent = fields["lastEventOn"]?.takeIf(String::isNotBlank)
        val lastEventOn = if (lastEvent == null) null else parseDate(lastEvent) ?: return null
        val stability = finite(fields["stabilityDays"]) ?: return null
        val difficulty = finite(fields["difficulty"]) ?: return null
        val difficultySeed = finite(fields["difficultySeed"]) ?: return null
        val importance = fields["importance"]?.toIntOrNull() ?: return null
        val reviews = fields["reviews"]?.toIntOrNull() ?: return null
        val lapses = fields["lapses"]?.toIntOrNull() ?: return null
        val exposures = fields["exposures"]?.toIntOrNull() ?: return null
        return MemoryState(
            capturedOn = capturedOn,
            lastEventOn = lastEventOn,
            stabilityDays = stability.coerceIn(MemoryModel.MinStability, MemoryModel.MaxStability),
            difficulty = difficulty.coerceIn(MemoryModel.MinDifficulty, MemoryModel.MaxDifficulty),
            difficultySeed = difficultySeed.coerceIn(MemoryModel.MinDifficulty, MemoryModel.MaxDifficulty),
            importance = importance.coerceIn(MemoryModel.ImportanceMin, MemoryModel.ImportanceMax),
            reviews = reviews.coerceAtLeast(0),
            lapses = lapses.coerceAtLeast(0).coerceAtMost(reviews.coerceAtLeast(0)),
            exposures = exposures.coerceAtLeast(0),
        )
    }

    fun gain(value: Double): Double =
        if (value.isFinite()) value.coerceIn(MemoryModel.MinGain, MemoryModel.MaxGain) else 1.0

    private fun parseDate(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun finite(value: String?): Double? = value?.toDoubleOrNull()?.takeIf(Double::isFinite)
}
