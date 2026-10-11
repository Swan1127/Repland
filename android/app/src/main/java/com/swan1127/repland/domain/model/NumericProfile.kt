package com.swan1127.repland.domain.model

import kotlin.math.ceil

enum class NumericAvailability { UNKNOWN, REFERENCE, USABLE, DISABLED }
data class NumericParameter(val id: String, val name: String, val unit: String, val value: Double?,
    val low: Double? = null, val high: Double? = null, val count: Int = 0,
    val sources: List<String> = emptyList(), val availability: NumericAvailability = NumericAvailability.UNKNOWN)
data class NumericEstimateSample(val taskId: String, val category: TaskCategory, val originalMinutes: Int?,
    val actualTotalMinutes: Int?, val eligible: Boolean)
data class NumericSource(val id: String, val taskId: String, val title: String, val detail: String,
    val included: Boolean, val excluded: Boolean = false)
data class NumericProfileSnapshot(val version: String, val windowStart: String, val windowEnd: String,
    val updatedAt: Long, val parameters: List<NumericParameter>, val sources: List<NumericSource>,
    val categoryWeights: Map<TaskCategory, Int>, val explicitCategories: Set<TaskCategory>,
    val enabled: Boolean = true, val aiConsent: Boolean = false,
    val rule: String = NumericProfileCalculator.RULE)
data class NumericDurationAdvice(val taskId: String, val taskRevision: String, val profileVersion: String,
    val parameterId: String, val originalMinutes: Int, val suggestedMinutes: Int, val factor: Double,
    val sources: List<String>)

/** Pure, deterministic engineering statistics. No task, plan or user score mutations. */
object NumericProfileCalculator {
    const val RULE = "numeric-v1"
    const val WINDOW_DAYS = 90L
    const val MIN_SAMPLES = 3
    private fun availability(n: Int, disabled: Boolean) = when {
        disabled -> NumericAvailability.DISABLED
        n == 0 -> NumericAvailability.UNKNOWN
        n < MIN_SAMPLES -> NumericAvailability.REFERENCE
        else -> NumericAvailability.USABLE
    }
    fun quantile(values: List<Double>, fraction: Double): Double? {
        if (values.isEmpty()) return null
        val sorted=values.sorted(); val index=(sorted.size-1)*fraction
        val lower=index.toInt(); val upper=ceil(index).toInt()
        return sorted[lower]+(sorted[upper]-sorted[lower])*(index-lower)
    }
    fun estimates(samples: List<NumericEstimateSample>, excluded: Set<String>, disabled: Set<String>): List<NumericParameter> =
        (listOf<TaskCategory?>(null)+TaskCategory.knownEntries).map { category ->
            val id="estimate:${category?.name ?: "ALL"}"
            val valid=samples.distinctBy { it.taskId }.filter { it.eligible && it.taskId !in excluded &&
                (category==null || it.category==category) && (it.originalMinutes ?: 0)>0 && (it.actualTotalMinutes ?: 0)>0 }
            NumericParameter(id,if(category==null) "估时修正 · 全局摘要" else "估时修正 · ${category.name}","倍",
                quantile(valid.map { it.actualTotalMinutes!!.toDouble()/it.originalMinutes!! },0.5),count=valid.size,
                sources=valid.map { "task:${it.taskId}" }.sorted(),availability=availability(valid.size,id in disabled))
        }
    fun rounds(sessions: List<ExecutionSession>, excluded: Set<String>, disabled: Boolean): NumericParameter {
        val valid=sessions.distinctBy { it.id }.filter { it.endedAtEpochMillis!=null && it.outcome!=null &&
            it.accumulatedMillis in 1..86_400_000 && it.runningSinceEpochMillis==null && it.id !in excluded }
        val values=valid.map { it.accumulatedMillis/60_000.0 }
        return NumericParameter("rounds","实际执行段","分钟",quantile(values,0.5),quantile(values,0.25),quantile(values,0.75),
            valid.size,valid.map { "session:${it.id}" }.sorted(),availability(valid.size,disabled))
    }
    fun suggestMinutes(original: Int?, parameter: NumericParameter): Int? {
        if(original==null || original<=0 || parameter.availability!=NumericAvailability.USABLE || parameter.value==null) return null
        val result=ceil(original*parameter.value)
        return result.takeIf { it.isFinite() && it in 1.0..1440.0 }?.toInt()
    }
}

data class NumericClaim(val parameterId: String, val value: Double)
enum class NumericNextStep { NONE, REVIEW_ESTIMATE, CONSIDER_SPLITTING }
data class NumericNarration(val requestId: String, val profileVersion: String, val claims: List<NumericClaim>,
    val nextStep: NumericNextStep)
data class NumericNarrationRequest(val requestId: String, val profileVersion: String, val rule: String,
    val windowStart: String, val windowEnd: String, val parameters: List<NumericParameter>)
data class NumericDescription(val profileVersion: String, val generatedAt: Long, val origin: String,
    val claims: List<NumericClaim>, val nextStep: NumericNextStep, val configurationRevision: String? = null, val accessRevision: String? = null)
fun interface NumericProfileAdvisor { suspend fun describe(request: NumericNarrationRequest): NumericNarration? }
object NoOpNumericProfileAdvisor : NumericProfileAdvisor { override suspend fun describe(request: NumericNarrationRequest): NumericNarration? = null }
object NumericNarrationValidator {
    fun valid(request: NumericNarrationRequest, response: NumericNarration): Boolean {
        if(response.requestId!=request.requestId || response.profileVersion!=request.profileVersion ||
            response.claims.isEmpty() || response.claims.size>request.parameters.size ||
            response.claims.distinctBy { it.parameterId }.size!=response.claims.size) return false
        if(!response.claims.all { claim -> request.parameters.any { it.id==claim.parameterId && it.availability!=NumericAvailability.DISABLED && it.value==claim.value && claim.value.isFinite() } }) return false
        return when(response.nextStep) {
            NumericNextStep.NONE -> true
            NumericNextStep.REVIEW_ESTIMATE -> response.claims.any { it.parameterId.startsWith("estimate:") && request.parameters.any { p -> p.id==it.parameterId && p.availability==NumericAvailability.USABLE } }
            NumericNextStep.CONSIDER_SPLITTING -> request.parameters.any { it.id=="rounds" && it.availability==NumericAvailability.USABLE } && response.claims.any { it.parameterId=="rounds" }
        }
    }
}
