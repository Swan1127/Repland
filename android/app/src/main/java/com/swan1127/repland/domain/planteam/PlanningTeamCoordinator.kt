package com.swan1127.repland.domain.planteam

import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.NoOpAiAdvisor
import com.swan1127.repland.domain.planteam.runtime.AgentMemoryBank
import com.swan1127.repland.domain.planteam.runtime.PlanningTeamRuntime
import com.swan1127.repland.domain.planteam.runtime.TeamEvent

class PlanningTeamCoordinator(
    private val runtime: PlanningTeamRuntime = PlanningTeamRuntime(),
) {

    suspend fun run(
        ctx: PlanTeamContext,
        advisor: AiAdvisor = NoOpAiAdvisor,
        onEvent: (TeamEvent) -> Unit = {},
    ): PlanTeamResult = runtime.execute(ctx, advisor, onEvent)


    fun memoryBank(): AgentMemoryBank = runtime.memoryBank()
}
