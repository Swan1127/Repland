package com.swan1127.repland.data

import android.content.Context
import com.swan1127.repland.domain.model.AgentProfile
import com.swan1127.repland.domain.model.AgentRole
import com.swan1127.repland.domain.model.DefaultAgentTeam
import com.swan1127.repland.domain.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the editable side of the group chat: each agent's name, avatar, self-intro,
 * role prompt and catchphrase, plus the user's own avatar.
 *
 * Kept out of Room on purpose, for the same reason as [AuxiliaryStore]: none of this is a
 * scheduler input, so it needs no migration and cannot corrupt a plan. A missing or
 * malformed record falls back to [DefaultAgentTeam], so a bad write never leaves the team
 * with a blank name.
 */
class AgentProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("agent_team", Context.MODE_PRIVATE)

    fun loadAgents(): List<AgentProfile> {
        val raw = prefs.getString(KEY_AGENTS, null) ?: return DefaultAgentTeam.agents
        val parsed = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val role = AgentRole.entries.firstOrNull { it.name == obj.optString("role") }
                    ?: return@mapNotNull null
                AgentProfile(
                    id = obj.optString("id"),
                    role = role,
                    name = obj.optString("name").ifBlank { role.name },
                    avatar = obj.optString("avatar").ifBlank { "🙂" },
                    selfIntro = obj.optString("selfIntro"),
                    introPrompt = obj.optString("introPrompt"),
                    catchphrase = obj.optString("catchphrase"),
                    isPlanner = obj.optBoolean("isPlanner", role == AgentRole.PLANNER),
                )
            }
        }.getOrDefault(emptyList())
        if (parsed.isEmpty()) return DefaultAgentTeam.agents
        // Fill any missing role from the defaults so the roster can never shrink by accident.
        return DefaultAgentTeam.agents.map { default ->
            parsed.firstOrNull { it.id == default.id || it.role == default.role } ?: default
        }
    }

    fun saveAgents(agents: List<AgentProfile>) {
        val array = JSONArray()
        agents.forEach { agent ->
            array.put(JSONObject().apply {
                put("id", agent.id)
                put("role", agent.role.name)
                put("name", agent.name)
                put("avatar", agent.avatar)
                put("selfIntro", agent.selfIntro)
                put("introPrompt", agent.introPrompt)
                put("catchphrase", agent.catchphrase)
                put("isPlanner", agent.isPlanner)
            })
        }
        prefs.edit().putString(KEY_AGENTS, array.toString()).apply()
    }

    fun loadUser(): UserProfile {
        val raw = prefs.getString(KEY_USER, null) ?: return DefaultAgentTeam.user
        return runCatching {
            val obj = JSONObject(raw)
            UserProfile(
                name = obj.optString("name").ifBlank { DefaultAgentTeam.user.name },
                avatar = obj.optString("avatar").ifBlank { DefaultAgentTeam.user.avatar },
            )
        }.getOrDefault(DefaultAgentTeam.user)
    }

    fun saveUser(user: UserProfile) {
        prefs.edit().putString(
            KEY_USER,
            JSONObject().apply {
                put("name", user.name)
                put("avatar", user.avatar)
            }.toString(),
        ).apply()
    }

    private companion object {
        const val KEY_AGENTS = "agents"
        const val KEY_USER = "user"
    }
}
