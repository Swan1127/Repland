package com.swan1127.repland.domain.planteam.runtime

import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.planteam.AvailabilityMap
import com.swan1127.repland.domain.planteam.CalibrationReport
import com.swan1127.repland.domain.planteam.ConflictReport
import com.swan1127.repland.domain.planteam.PersonalizationHints
import com.swan1127.repland.domain.planteam.PlanAgentId
import com.swan1127.repland.domain.planteam.PlanTeamContext
import com.swan1127.repland.domain.planteam.PlanningBrief
import com.swan1127.repland.domain.planteam.SimulatedLanding

enum class WorkspaceSlot(val label: String) {
    BRIEF("需求简报"),
    AVAILABILITY("时间地图"),
    HINTS("习惯偏好"),
    CALIBRATION("执行校准"),
    DRAFT("方案草案"),
    CONFLICT_REPORT("冲突报告"),
    LANDING("落地预览"),
}

data class WorkspaceEntry(
    val agent: PlanAgentId,
    val slot: WorkspaceSlot,
    val summary: String,
    val revision: Int,
)

class AgentWorkspace(
    val runId: String,
    val ctx: PlanTeamContext,
) {
    private val ledger = mutableListOf<WorkspaceEntry>()
    private var revision = 0

    var brief: PlanningBrief? = null
        private set
    var availability: AvailabilityMap? = null
        private set
    var hints: PersonalizationHints? = null
        private set
    var calibration: CalibrationReport? = null
        private set
    var draft: PlanDraft? = null
        private set
    var report: ConflictReport? = null
        private set
    var landing: SimulatedLanding? = null
        private set

    fun putBrief(agent: PlanAgentId, value: PlanningBrief, summary: String) =
        put(agent, WorkspaceSlot.BRIEF, summary) { brief = value }

    fun putAvailability(agent: PlanAgentId, value: AvailabilityMap, summary: String) =
        put(agent, WorkspaceSlot.AVAILABILITY, summary) { availability = value }

    fun putHints(agent: PlanAgentId, value: PersonalizationHints, summary: String) =
        put(agent, WorkspaceSlot.HINTS, summary) { hints = value }

    fun putCalibration(agent: PlanAgentId, value: CalibrationReport, summary: String) =
        put(agent, WorkspaceSlot.CALIBRATION, summary) { calibration = value }

    fun putDraft(agent: PlanAgentId, value: PlanDraft, summary: String) =
        put(agent, WorkspaceSlot.DRAFT, summary) { draft = value }

    fun putReport(agent: PlanAgentId, value: ConflictReport, summary: String) =
        put(agent, WorkspaceSlot.CONFLICT_REPORT, summary) { report = value }

    fun putLanding(agent: PlanAgentId, value: SimulatedLanding, summary: String) =
        put(agent, WorkspaceSlot.LANDING, summary) { landing = value }

    fun requireBrief(reader: PlanAgentId): PlanningBrief =
        brief ?: missing(reader, WorkspaceSlot.BRIEF, PlanAgentId.REQUIREMENTS)

    fun requireAvailability(reader: PlanAgentId): AvailabilityMap =
        availability ?: missing(reader, WorkspaceSlot.AVAILABILITY, PlanAgentId.AVAILABILITY)

    fun requireHints(reader: PlanAgentId): PersonalizationHints =
        hints ?: missing(reader, WorkspaceSlot.HINTS, PlanAgentId.HABIT)

    fun requireCalibration(reader: PlanAgentId): CalibrationReport =
        calibration ?: missing(reader, WorkspaceSlot.CALIBRATION, PlanAgentId.CALIBRATOR)

    fun requireDraft(reader: PlanAgentId): PlanDraft =
        draft ?: missing(reader, WorkspaceSlot.DRAFT, PlanAgentId.COMPOSER)

    fun entries(): List<WorkspaceEntry> = ledger.toList()

    fun filledSlots(): Set<WorkspaceSlot> = ledger.map { it.slot }.toSet()


    private val ran = LinkedHashSet<PlanAgentId>()

    fun markRan(agent: PlanAgentId) {
        ran += agent
    }

    fun agentsRan(): Set<PlanAgentId> = ran.toSet()


    private val traceLines = mutableListOf<String>()

    fun appendTrace(line: String) {
        traceLines += line
    }

    fun trace(): List<String> = traceLines.toList()


    fun ownerOf(slot: WorkspaceSlot): PlanAgentId = OWNER.getValue(slot)

    private inline fun put(agent: PlanAgentId, slot: WorkspaceSlot, summary: String, write: () -> Unit) {
        val owner = OWNER.getValue(slot)
        require(owner == agent) {
            "工作区格子「${slot.label}」属于 ${owner.displayName()}，${agent.displayName()} 不能写入。"
        }
        write()
        revision += 1
        ledger += WorkspaceEntry(agent, slot, summary, revision)
    }

    private fun <T> missing(reader: PlanAgentId, slot: WorkspaceSlot, producer: PlanAgentId): T =
        error("${reader.displayName()} 想读「${slot.label}」，但 ${producer.displayName()} 还没有把它放进工作区。")

    companion object {
        val OWNER: Map<WorkspaceSlot, PlanAgentId> = mapOf(
            WorkspaceSlot.BRIEF to PlanAgentId.REQUIREMENTS,
            WorkspaceSlot.AVAILABILITY to PlanAgentId.AVAILABILITY,
            WorkspaceSlot.HINTS to PlanAgentId.HABIT,
            WorkspaceSlot.CALIBRATION to PlanAgentId.CALIBRATOR,
            WorkspaceSlot.DRAFT to PlanAgentId.COMPOSER,
            WorkspaceSlot.CONFLICT_REPORT to PlanAgentId.CONFLICT,
            WorkspaceSlot.LANDING to PlanAgentId.COMPOSER,
        )
    }
}
