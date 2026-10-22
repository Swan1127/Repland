package com.swan1127.repland.domain.planteam.runtime

enum class CriterionLevel { BLOCKER, REQUIRED, ADVISORY }

enum class CriterionStatus { PASS, FAIL, SKIPPED }

data class CriterionResult(
    val criterionId: String,
    val statement: String,
    val level: CriterionLevel,
    val status: CriterionStatus,
    val detail: String = "",
) {

    val blocking: Boolean get() = status == CriterionStatus.FAIL && level != CriterionLevel.ADVISORY
}

sealed interface Verdict {
    data object Pass : Verdict
    data class Fail(val detail: String) : Verdict
    data class Skip(val detail: String) : Verdict

    companion object {
        fun fail(detail: String): Verdict = Fail(detail)
        fun skip(detail: String): Verdict = Skip(detail)
    }
}

class AcceptanceContext(
    val workspace: AgentWorkspace,
    val memory: AgentMemory,
    val advisorAvailable: Boolean,
) {
    val taskIds: Set<String> get() = workspace.ctx.input.tasks.map { it.id }.toSet()
    val taskCount: Int get() = workspace.ctx.input.tasks.size
}

class AcceptanceCriterion(
    val id: String,
    val statement: String,
    val level: CriterionLevel,
    val check: (AcceptanceContext) -> Verdict,
) {
    fun evaluate(ctx: AcceptanceContext): CriterionResult {
        val verdict = try {
            check(ctx)
        } catch (error: Exception) {
            Verdict.Fail("检查本身出错：${error.message ?: error::class.simpleName}")
        }
        return when (verdict) {
            is Verdict.Pass -> CriterionResult(id, statement, level, CriterionStatus.PASS)
            is Verdict.Fail -> CriterionResult(id, statement, level, CriterionStatus.FAIL, verdict.detail)
            is Verdict.Skip -> CriterionResult(id, statement, level, CriterionStatus.SKIPPED, verdict.detail)
        }
    }
}
