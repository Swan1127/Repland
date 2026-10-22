package com.swan1127.repland.domain.planteam.runtime

import com.swan1127.repland.domain.planteam.PlanAgentId

data class MemoryEpisode(
    val runId: String,
    val requestKind: String,
    val summary: String,
    val atMillis: Long,
)

data class MemoryWrite(val key: String, val value: String)

class AgentMemory(
    val agentId: PlanAgentId,
    val runId: String,
    seed: Map<String, String> = emptyMap(),
) {
    private val facts = LinkedHashMap<String, String>(seed)
    private val writes = mutableListOf<MemoryWrite>()


    fun recall(key: String): String? = facts[key]

    fun recallInt(key: String): Int? = facts[key]?.toIntOrNull()


    fun note(key: String, value: String) {
        if (facts[key] == value) return
        facts[key] = value
        writes += MemoryWrite(key, value)
    }

    fun note(key: String, value: Int) = note(key, value.toString())

    fun forget(key: String) {
        if (facts.remove(key) != null) writes += MemoryWrite(key, "")
    }


    fun written(): List<MemoryWrite> = writes.toList()


    fun snapshot(): Map<String, String> = LinkedHashMap(facts)
}

class AgentMemoryBank(
    private val maxEpisodesPerAgent: Int = 6,
    private val maxFactsPerAgent: Int = 24,
) {
    private val facts = HashMap<PlanAgentId, MutableMap<String, String>>()
    private val episodes = HashMap<PlanAgentId, MutableList<MemoryEpisode>>()


    @Synchronized
    fun seed(agentId: PlanAgentId, runId: String): AgentMemory =
        AgentMemory(agentId, runId, facts[agentId]?.toMap().orEmpty())

    @Synchronized
    fun episodesOf(agentId: PlanAgentId): List<MemoryEpisode> = episodes[agentId].orEmpty().toList()


    @Synchronized
    fun commit(memory: AgentMemory, requestKind: String, summary: String, atMillis: Long) {
        val store = facts.getOrPut(memory.agentId) { LinkedHashMap() }
        store.putAll(memory.snapshot())
        while (store.size > maxFactsPerAgent) {
            val oldest = store.keys.firstOrNull() ?: break
            store.remove(oldest)
        }
        val list = episodes.getOrPut(memory.agentId) { mutableListOf() }
        list += MemoryEpisode(memory.runId, requestKind, summary, atMillis)
        while (list.size > maxEpisodesPerAgent) list.removeAt(0)
    }

    @Synchronized
    fun clear() {
        facts.clear()
        episodes.clear()
    }
}
