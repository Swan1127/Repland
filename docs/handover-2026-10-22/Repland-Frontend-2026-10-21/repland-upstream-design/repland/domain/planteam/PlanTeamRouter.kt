package com.swan1127.repland.domain.planteam

object PlanTeamRouter {
    fun select(kind: PlanRequestKind, ctx: PlanTeamContext): LinkedHashSet<PlanAgentId> {
        val set = LinkedHashSet<PlanAgentId>()
        when (kind) {
            PlanRequestKind.ADD ->
                set += listOf(
                    PlanAgentId.REQUIREMENTS, PlanAgentId.CALIBRATOR, PlanAgentId.AVAILABILITY,
                    PlanAgentId.CONFLICT, PlanAgentId.COMPOSER,
                )

            PlanRequestKind.REMOVE ->
                set += listOf(
                    PlanAgentId.CALIBRATOR, PlanAgentId.AVAILABILITY,
                    PlanAgentId.CONFLICT, PlanAgentId.COMPOSER,
                )

            PlanRequestKind.FULL_REPLAN, PlanRequestKind.REPAIR ->
                set += listOf(
                    PlanAgentId.REQUIREMENTS, PlanAgentId.CALIBRATOR, PlanAgentId.AVAILABILITY,
                    PlanAgentId.HABIT, PlanAgentId.CONFLICT, PlanAgentId.COMPOSER,
                )

            PlanRequestKind.ADJUST ->
                set += listOf(PlanAgentId.CALIBRATOR, PlanAgentId.CONFLICT, PlanAgentId.COMPOSER)

            PlanRequestKind.RECOVER ->
                set += listOf(
                    PlanAgentId.CALIBRATOR, PlanAgentId.AVAILABILITY,
                    PlanAgentId.CONFLICT, PlanAgentId.COMPOSER,
                )

            PlanRequestKind.PREVIEW_HABIT ->
                set += listOf(PlanAgentId.HABIT, PlanAgentId.AVAILABILITY, PlanAgentId.COMPOSER)
        }
        check(PlanAgentId.COMPOSER in set) { "任何请求最终都要走合成 agent" }
        return set
    }
}
