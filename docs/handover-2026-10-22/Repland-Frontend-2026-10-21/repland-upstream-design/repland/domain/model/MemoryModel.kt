package com.swan1127.repland.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

enum class RecallGrade { FORGOT, FUZZY, RECALLED }

data class MemoryState(
    val capturedOn: LocalDate,
    val lastEventOn: LocalDate?,
    val stabilityDays: Double,
    val difficulty: Double,
    val difficultySeed: Double,
    val importance: Int,
    val reviews: Int,
    val lapses: Int,
    val exposures: Int,
) {
    
    val anchor: LocalDate get() = lastEventOn ?: capturedOn
}

data class MemoryBars(
    val importance: Float,
    val stability: Float,
    val retrievability: Float,
)

object MemoryModel {
    const val Decay: Double = -0.5
    const val Factor: Double = 19.0 / 81.0
    const val TargetRetention: Double = 0.9

    const val MinStability: Double = 1.0
    const val MaxStability: Double = 90.0
    const val InitialStability: Double = 1.0

    const val MinDifficulty: Double = 1.0
    const val MaxDifficulty: Double = 10.0
    const val NeutralDifficulty: Double = 5.5

    const val ImportanceMin: Int = 1
    const val ImportanceMax: Int = 5

    const val MinGain: Double = 0.6
    const val MaxGain: Double = 2.0

    
    private const val GainCoefficient = 4.8
    private const val EdgeWeight = 3.0
    private const val ReferenceStability = 2.0
    private const val DiminishingExponent = 0.25
    private const val MaxGrowthPerReview = 3.0
    private const val FuzzyMultiplier = 0.45
    private const val LapseRatio = 0.4
    private const val LapseCeiling = 3.0
    private const val DifficultyRelief = 0.7
    private const val DifficultyPenalty = 1.6
    private const val DifficultyReversion = 0.25

    private val ladderDays = listOf(1, 2, 4, 7, 15)

    
    fun ladderDays(): List<Int> = ladderDays

    
    fun seed(importance: Int, on: LocalDate, aiDifficulty: Double? = null): MemoryState {
        val difficulty = difficultySeed(aiDifficulty)
        return MemoryState(
            capturedOn = on,
            lastEventOn = null,
            stabilityDays = InitialStability,
            difficulty = difficulty,
            difficultySeed = difficulty,
            importance = importance.coerceIn(ImportanceMin, ImportanceMax),
            reviews = 0,
            lapses = 0,
            exposures = 0,
        )
    }

    
    fun importanceOf(priority: TaskPriority): Int = when (priority) {
        TaskPriority.REQUIRED -> 5
        TaskPriority.HIGH -> 4
        TaskPriority.MEDIUM -> 3
        TaskPriority.LOW -> 1
    }

    
    fun importanceBar(importance: Int): Float =
        (importance.coerceIn(ImportanceMin, ImportanceMax) - ImportanceMin).toFloat() /
            (ImportanceMax - ImportanceMin)

    
    fun difficultySeed(aiDifficulty: Double?): Double = aiDifficulty
        ?.takeIf(Double::isFinite)
        ?.coerceIn(0.0, 1.0)
        ?.let { MinDifficulty + it * (MaxDifficulty - MinDifficulty) }
        ?: NeutralDifficulty

    fun elapsedDays(state: MemoryState, today: LocalDate): Double =
        max(0.0, ChronoUnit.DAYS.between(state.anchor, today).toDouble())

    
    fun retrievability(stabilityDays: Double, elapsedDays: Double): Double {
        if (elapsedDays <= 0.0) return 1.0
        val stability = stabilityDays.coerceIn(MinStability, MaxStability)
        return (1.0 + Factor * elapsedDays / stability).pow(Decay)
    }

    fun retrievabilityAt(state: MemoryState, today: LocalDate): Double =
        retrievability(state.stabilityDays, elapsedDays(state, today))

    
    fun intervalDays(stabilityDays: Double, retention: Double = TargetRetention): Double {
        val retentionRatio = retention.coerceIn(0.05, 1.0)
        val stability = stabilityDays.coerceIn(MinStability, MaxStability)
        return max(1.0, stability / Factor * (retentionRatio.pow(1.0 / Decay) - 1.0))
    }

    fun dueOn(state: MemoryState, today: LocalDate = LocalDate.now()): LocalDate =
        state.anchor.plusDays(
            intervalDays(state.stabilityDays).roundToInt().coerceAtLeast(1).toLong(),
        )

    fun isDue(state: MemoryState, today: LocalDate = LocalDate.now()): Boolean =
        !dueOn(state).isAfter(today)

    
    fun reviewPressure(state: MemoryState, today: LocalDate): Double {
        val weight = importanceBar(state.importance).toDouble()
        return (0.25 + 0.75 * weight) * (1.0 - retrievabilityAt(state, today))
    }

    fun bars(state: MemoryState, today: LocalDate): MemoryBars = MemoryBars(
        importance = importanceBar(state.importance),
        stability = (ln(max(state.stabilityDays, MinStability)) /
            ln(MaxStability / MinStability)).toFloat().coerceIn(0f, 1f),
        retrievability = retrievabilityAt(state, today).toFloat(),
    )

    
    fun grade(
        state: MemoryState,
        grade: RecallGrade,
        today: LocalDate = LocalDate.now(),
        gain: Double = 1.0,
    ): MemoryState {
        val predicted = retrievabilityAt(state, today)
        val stability = when (grade) {
            RecallGrade.FORGOT -> (state.stabilityDays * LapseRatio)
                .coerceIn(MinStability, LapseCeiling)
            else -> (state.stabilityDays * growth(state, predicted, grade, gain))
                .coerceIn(MinStability, MaxStability)
        }
        val shift = when (grade) {
            RecallGrade.FORGOT -> DifficultyPenalty
            RecallGrade.FUZZY -> 0.0
            RecallGrade.RECALLED -> -DifficultyRelief
        }
        return state.copy(
            lastEventOn = today,
            stabilityDays = stability,
            difficulty = (DifficultyReversion * state.difficultySeed +
                (1.0 - DifficultyReversion) * (state.difficulty + shift))
                .coerceIn(MinDifficulty, MaxDifficulty),
            reviews = if (state.reviews == Int.MAX_VALUE) Int.MAX_VALUE else state.reviews + 1,
            lapses = if (grade == RecallGrade.FORGOT && state.lapses < Int.MAX_VALUE) state.lapses + 1 else state.lapses,
        )
    }

    private fun growth(
        state: MemoryState,
        predicted: Double,
        grade: RecallGrade,
        gain: Double,
    ): Double {
        val edge = exp(EdgeWeight * (1.0 - predicted)) - 1.0
        val diminishing = (ReferenceStability / state.stabilityDays).pow(DiminishingExponent)
        val hardness = (MaxDifficulty - state.difficulty) / (MaxDifficulty - MinDifficulty)
        val favour = when (grade) {
            RecallGrade.RECALLED -> 1.0
            RecallGrade.FUZZY -> FuzzyMultiplier
            RecallGrade.FORGOT -> 0.0
        }
        return (1.0 + GainCoefficient * edge * diminishing * hardness * favour *
            MemoryStateCodec.gain(gain)).coerceIn(1.0, 1.0 + MaxGrowthPerReview)
    }

    
    fun touched(state: MemoryState): MemoryState =
        state.copy(exposures = if (state.exposures == Int.MAX_VALUE) Int.MAX_VALUE else state.exposures + 1)

    
    fun observeGain(predictedRetention: Double, grade: RecallGrade, current: Double): Double = when {
        grade == RecallGrade.FORGOT && predictedRetention >= 0.9 -> MemoryStateCodec.gain(current) * 0.85
        grade == RecallGrade.FORGOT && predictedRetention >= 0.7 -> MemoryStateCodec.gain(current) * 0.93
        grade == RecallGrade.RECALLED && predictedRetention <= 0.6 -> MemoryStateCodec.gain(current) * 1.06
        else -> MemoryStateCodec.gain(current)
    }.coerceIn(MinGain, MaxGain)

    
    fun fromLadderStage(capturedOn: LocalDate, stage: Int, importance: Int): MemoryState {
        val index = stage.coerceIn(0, ladderDays.lastIndex)
        return MemoryState(
            capturedOn = capturedOn,
            lastEventOn = capturedOn,
            stabilityDays = ladderDays[index].toDouble(),
            difficulty = NeutralDifficulty,
            difficultySeed = NeutralDifficulty,
            importance = importance.coerceIn(ImportanceMin, ImportanceMax),
            reviews = stage.coerceAtLeast(0),
            lapses = 0,
            exposures = 0,
        )
    }
}
