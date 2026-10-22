package com.swan1127.repland.domain.model

import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The group-chat planning team.
 *
 * Every member's line comes from the provider. The engine's job is to decide *what each
 * member is allowed to know* and in which order they speak — not to write their sentences.
 * That split matters: a role can only cite an occupancy figure because the engine put the
 * figure in its prompt, so a wrong number is traceable to the data rather than to a role
 * improvising. There is no local text generator behind this: a failed call surfaces as
 * [GroupChatEvent.Failed].
 */
enum class AgentRole {
    REQUIREMENTS,
    AVAILABILITY,
    HABIT,
    CONFLICT,
    PLANNER,
}

data class AgentProfile(
    val id: String,
    val role: AgentRole,
    val name: String,
    /** An emoji is enough for an avatar and keeps the app free of image upload plumbing. */
    val avatar: String,
    val selfIntro: String,
    /** The role instruction sent as this member's system prompt. */
    val introPrompt: String,
    /** A short verbal tic the agent leads with, so each role has a recognisable voice. */
    val catchphrase: String,
    val isPlanner: Boolean = false,
)

data class UserProfile(
    val name: String = "我",
    val avatar: String = "🙂",
)

enum class ToolOutcome { DONE, SKIPPED, FAILED }

/** One supporting tool call shown under an agent's message. */
data class ToolTrace(
    val label: String,
    val outcome: ToolOutcome = ToolOutcome.DONE,
)

sealed interface GroupChatEvent {
    data class UserSaid(val text: String) : GroupChatEvent
    data class AgentTyping(val agentId: String) : GroupChatEvent
    data class AgentSaid(
        val agentId: String,
        val text: String,
        val tools: List<ToolTrace> = emptyList(),
    ) : GroupChatEvent
    data class PlanReady(
        val candidates: List<ArrangementCandidate>,
        val confidenceLabel: String,
    ) : GroupChatEvent

    /**
     * A step did not run. This is shown to the user, never swallowed: the previous version
     * of this engine answered from a local interpreter when the provider was unreachable,
     * and the result was indistinguishable on screen from a real answer.
     *
     * It is not always terminal — the planner's prose can fail while the plan it already
     * computed is still valid, in which case this arrives just before [PlanReady].
     */
    data class Failed(
        val reason: AiAdvisorFailureReason,
        val agentName: String? = null,
    ) : GroupChatEvent
}

data class GroupChatRequest(
    val utterance: String,
    val date: LocalDate,
    val occupiedEntries: List<TimelineEntry>,
    val agents: List<AgentProfile>,
    /** False when the user has not enabled the provider or not consented. Fails the turn. */
    val cloudEnabled: Boolean,
    val chat: AgentChatModel,
    val refine: suspend (String, LocalDate, List<TimelineEntry>) -> ArrangementAssistantAdviceResult,
)

object DefaultAgentTeam {
    val agents: List<AgentProfile> = listOf(
        AgentProfile(
            id = "req",
            role = AgentRole.REQUIREMENTS,
            name = "小析",
            avatar = "🧩",
            selfIntro = "我负责把你的一句话拆成清楚的事项。",
            introPrompt = "你是需求分析师。把用户的一句话拆成可执行的事项，指出缺失的时间与时长；不要替用户编造时间。",
            catchphrase = "先把话说清楚。",
        ),
        AgentProfile(
            id = "time",
            role = AgentRole.AVAILABILITY,
            name = "小刻",
            avatar = "⏰",
            selfIntro = "我负责看今天还剩哪些空档。",
            introPrompt = "你是时间管家。读取今天的课程、固定事项与空档，说明哪些时间真正可用；课程与固定事项不可占用。",
            catchphrase = "时间要留余量。",
        ),
        AgentProfile(
            id = "habit",
            role = AgentRole.HABIT,
            name = "小习",
            avatar = "🌱",
            selfIntro = "我负责记住你的习惯和偏好。",
            introPrompt = "你是习惯顾问。根据用户的历史偏好调整安排，但不替用户做决定，也不编造偏好。",
            catchphrase = "顺着你的习惯来。",
        ),
        AgentProfile(
            id = "conflict",
            role = AgentRole.CONFLICT,
            name = "小盾",
            avatar = "🛡️",
            selfIntro = "我负责检查有没有撞车。",
            introPrompt = "你是冲突检查员。检查候选时段是否与课程、固定事项、休息或同轨安排冲突，并指出冲突项。",
            catchphrase = "别撞车。",
        ),
        AgentProfile(
            id = "planner",
            role = AgentRole.PLANNER,
            name = "小规",
            avatar = "🗺️",
            selfIntro = "我负责把大家的意见合成一份可执行的安排。",
            introPrompt = "你是规划师。汇总其他角色的结论，给出可编辑的安排草案，并说明依据；确认前不得写入。",
            catchphrase = "我来排。",
            isPlanner = true,
        ),
    )

    val user: UserProfile = UserProfile()
}

/**
 * Drives one round of the group chat.
 *
 * Latency shape: the specialists are independent readings of the same utterance, so their
 * calls run **concurrently** while their messages are still revealed one at a time; the
 * planner's two calls start as soon as the specialists' text is in hand, so they overlap
 * with the specialists' typing animation. Wall-clock time is therefore roughly one network
 * round plus the reveal, not five serial rounds.
 */
class GroupChatEngine(
    /** How long the typing state stays up before a message lands. Presentation only. */
    private val typingPauseMillis: Long = 220L,
    /**
     * A hard budget for one complete turn. The provider is someone else's server: without
     * this, a stalled call leaves "正在处理…" on screen for as long as the transport decides
     * to wait, which is a hang from the user's side and indistinguishable from a broken app.
     */
    private val turnTimeoutMillis: Long = 60_000L,
) {
    suspend fun run(
        request: GroupChatRequest,
        onEvent: suspend (GroupChatEvent) -> Unit,
    ) {
        val finished = withTimeoutOrNull(turnTimeoutMillis) { runTurn(request, onEvent) }
        // Everything produced before the wall ran out is kept; only the missing part gets
        // an explicit failure, so the user can see what was already said.
        if (finished == null) {
            onEvent(GroupChatEvent.Failed(AiAdvisorFailureReason.TIMEOUT))
        }
    }

    private suspend fun runTurn(
        request: GroupChatRequest,
        onEvent: suspend (GroupChatEvent) -> Unit,
    ) {
        onEvent(GroupChatEvent.UserSaid(request.utterance))

        if (!request.cloudEnabled) {
            onEvent(GroupChatEvent.Failed(AiAdvisorFailureReason.DISABLED))
            return
        }

        val planner = request.agents.firstOrNull { it.isPlanner } ?: request.agents.last()
        val mentioned = request.agents.filter { request.utterance.contains("@${it.name}") }
        val speaking = (if (mentioned.isNotEmpty()) mentioned else request.agents)
            .filterNot { it.isPlanner }
        val facts = DayFacts.of(request.date, request.occupiedEntries)

        coroutineScope {
            // Someone is visibly working from the first frame: the network wait would
            // otherwise look like a frozen sheet.
            speaking.firstOrNull()?.let { onEvent(GroupChatEvent.AgentTyping(it.id)) }

            // The planner's structural call needs only the utterance and today's occupancy,
            // not the specialists' prose, so it is launched alongside them instead of after.
            // Only its *sentence* has to wait for the draft: a sentence composed in parallel
            // cannot know how many items the card holds and ends up contradicting it.
            val planWork = async {
                request.refine(request.utterance, request.date, request.occupiedEntries)
            }

            val replies = speaking
                .map { agent ->
                    async {
                        agent.id to request.chat.say(
                            systemPrompt(agent, facts),
                            userPrompt(agent, request, facts, earlier = emptyList()),
                        )
                    }
                }
                .awaitAll()

            replies.firstOrNull { it.second is AgentChatResult.Failed }?.let { (agentId, result) ->
                val reason = (result as AgentChatResult.Failed).reason
                onEvent(GroupChatEvent.Failed(reason, speaking.firstOrNull { it.id == agentId }?.name))
                return@coroutineScope
            }

            val conclusions = replies.map { (agentId, result) ->
                speaking.first { it.id == agentId }.name to (result as AgentChatResult.Said).text
            }

            // Revealed one at a time, in roster order, even though the calls finished in
            // whatever order the provider answered them.
            replies.forEachIndexed { index, (agentId, result) ->
                val agent = speaking.first { it.id == agentId }
                if (index > 0) onEvent(GroupChatEvent.AgentTyping(agent.id))
                delay(typingPauseMillis)
                onEvent(
                    GroupChatEvent.AgentSaid(
                        agentId = agent.id,
                        text = withCatchphrase(agent, (result as AgentChatResult.Said).text),
                        tools = toolsFor(agent),
                    ),
                )
            }

            onEvent(GroupChatEvent.AgentTyping(planner.id))
            delay(typingPauseMillis)

            when (val plan = planWork.await()) {
                is ArrangementAssistantAdviceResult.Advice -> {
                    // Placement happens here, not in the UI, because the planner's sentence
                    // has to describe the same times the card shows. Splitting them is what
                    // produced "时间待定" prose above a card that said 08:00.
                    val allocated = allocate(plan.advice.candidates, request.occupiedEntries)
                    val prose = request.chat.say(
                        systemPrompt(planner, facts),
                        plannerPrompt(request, facts, conclusions, allocated),
                    )
                    when (prose) {
                        is AgentChatResult.Said -> onEvent(
                            GroupChatEvent.AgentSaid(
                                agentId = planner.id,
                                text = withCatchphrase(planner, prose.text),
                                tools = plannerTools(conclusions.size),
                            ),
                        )
                        // The plan itself is already computed and valid, so it is still
                        // delivered — but the missing sentence is not papered over with a
                        // canned one; the failure is shown above the card.
                        is AgentChatResult.Failed -> onEvent(GroupChatEvent.Failed(prose.reason, planner.name))
                    }
                    onEvent(GroupChatEvent.PlanReady(allocated, plan.advice.confidenceLabel))
                }

                is ArrangementAssistantAdviceResult.Unavailable ->
                    onEvent(GroupChatEvent.Failed(plan.reason, planner.name))

                is ArrangementAssistantAdviceResult.Failed ->
                    onEvent(GroupChatEvent.Failed(plan.reason, planner.name))
            }
        }
    }

    // ------------------------------------------------------------------ prompts

    private fun systemPrompt(agent: AgentProfile, facts: DayFacts): String = buildString {
        append(agent.introPrompt).append('\n')
        append("你正在一个多角色规划小组的群聊里，用户能看见你的每一条消息。\n")
        append("发言规则：\n")
        append("1. 只说你这一句，不要输出 JSON、Markdown、标题或列表符号。\n")
        append("2. 不超过 60 个字，像同事说话，先给结论。\n")
        append("3. 只能引用「今日实况」里出现的数字和时段，不许自己编。\n")
        append("4. 用中文，第一句以「").append(agent.catchphrase).append("」开头。\n")
    }

    private fun userPrompt(
        agent: AgentProfile,
        request: GroupChatRequest,
        facts: DayFacts,
        earlier: List<Pair<String, String>>,
    ): String = buildString {
        append("用户这句话：").append(request.utterance).append('\n')
        append("日期：").append(facts.date).append('\n')
        append("今日实况：\n")
        append("- 已有 ").append(facts.occupied.size).append(" 项占用，其中 ")
            .append(facts.hardBusy.size).append(" 项是课程/固定事项/休息\n")
        if (facts.hardBusy.isEmpty()) {
            append("- 没有不可占用的固定时段\n")
        } else {
            facts.hardBusy.take(8).forEach {
                append("- ").append(it.title).append(' ').append(span(it.startMinute, it.endMinute))
                    .append("（不可占用）\n")
            }
        }
        if (facts.freeWindows.isEmpty()) {
            append("- 今天没有 30 分钟以上的连续空档\n")
        } else {
            append("- 30 分钟以上的空档：")
            append(facts.freeWindows.take(6).joinToString("、") { span(it.first, it.last + 1) })
            append('\n')
        }
        if (earlier.isNotEmpty()) {
            append("前面成员已经说过：\n")
            earlier.forEach { (name, text) -> append("- ").append(name).append('：').append(text).append('\n') }
        }
        append("你这一轮只负责：").append(focus(agent.role)).append('\n')
    }

    private fun plannerPrompt(
        request: GroupChatRequest,
        facts: DayFacts,
        conclusions: List<Pair<String, String>>,
        allocated: List<ArrangementCandidate>,
    ): String = buildString {
        append("用户这句话：").append(request.utterance).append('\n')
        append("日期：").append(facts.date).append('\n')
        append("小组成员的结论：\n")
        conclusions.forEach { (name, text) -> append("- ").append(name).append('：').append(text).append('\n') }
        // The card is the authority. Handing the model the exact draft it must describe is
        // what stops "三项需确认" appearing above a two-item card, and what stops it saying
        // 时间待定 about a row that already has a time.
        append("你排好的草案共 ").append(allocated.size).append(" 项：\n")
        allocated.forEach { candidate ->
            append("- ").append(candidate.title).append(' ')
            val start = candidate.timeHint.explicitStartMinute
            val duration = candidate.durationMinutes
            if (start != null && duration != null) {
                append(span(start, start + duration))
                if (candidate.placementSource == ArrangementPlacementSource.AI_SUGGESTED) {
                    append("（你排的）")
                } else {
                    append("（用户说的）")
                }
            } else {
                append("时间待定")
            }
            append('\n')
        }
        append("你这一轮只负责：用 60 字以内说明这份草案，并指出有几项需要用户补充或确认。")
        append("提到的项数和时间必须与上面的草案完全一致，不要重复成员的结论，也不要声称已经写入。\n")
    }

    private fun focus(role: AgentRole): String = when (role) {
        AgentRole.REQUIREMENTS -> "把这句话拆成具体事项，指出缺时间或缺时长的项"
        AgentRole.AVAILABILITY -> "说出今天真正能用的空档，指出不可占用的时段"
        AgentRole.HABIT -> "说明这类事情一般需要多长、放在什么时候更合适，不要编造用户偏好"
        AgentRole.CONFLICT -> "指出这些时间和已有安排有没有冲突"
        AgentRole.PLANNER -> "汇总并给出安排思路"
    }

    // ------------------------------------------------------------------ helpers

    private fun toolsFor(agent: AgentProfile): List<ToolTrace> {
        val dataTool = when (agent.role) {
            AgentRole.REQUIREMENTS -> "解析语句"
            AgentRole.AVAILABILITY -> "读取今日日程"
            AgentRole.HABIT -> "读取偏好"
            AgentRole.CONFLICT -> "冲突扫描"
            AgentRole.PLANNER -> "汇总报告"
        }
        return listOf(ToolTrace(dataTool), ToolTrace("云端推理"))
    }

    private fun plannerTools(speakerCount: Int): List<ToolTrace> = listOf(
        ToolTrace("汇总 $speakerCount 份报告"),
        ToolTrace("云端规划"),
    )

    /**
     * Fills in the times the model deliberately did not choose.
     *
     * The result keeps the [ArrangementCandidate] shape, so the UI needs no second planning
     * pass and the planner's sentence can be written against exactly what the card shows.
     */
    private fun allocate(
        candidates: List<ArrangementCandidate>,
        occupied: List<TimelineEntry>,
    ): List<ArrangementCandidate> {
        val result = ChatDayPlanner.plan(candidates, occupied)
        return candidates.mapIndexed { index, candidate ->
            val placement = result.placementFor(index) ?: return@mapIndexed candidate
            candidate.copy(
                timeHint = candidate.timeHint.copy(explicitStartMinute = placement.startMinute),
                placementSource = if (placement.fromUser) {
                    ArrangementPlacementSource.USER_EXPLICIT
                } else {
                    ArrangementPlacementSource.AI_SUGGESTED
                },
                preferredTrackId = placement.trackId,
            )
        }
    }

    /** The model is asked to lead with the catchphrase; this makes it certain. */
    private fun withCatchphrase(agent: AgentProfile, text: String): String {
        val clean = text.trim()
        return if (clean.startsWith(agent.catchphrase)) clean else "${agent.catchphrase} $clean"
    }

    private fun span(startMinute: Int, endMinute: Int): String =
        "${label(startMinute)}-${label(endMinute)}"

    private fun label(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    /**
     * What the day actually looks like, computed once and then handed to every prompt.
     *
     * Courses, commitments and rest are "hard busy": the plan generator already refuses to
     * place work on them, so a member that suggested one would be contradicting the
     * pipeline. Free windows are the complement of the hard-busy set, so they are real.
     */
    private data class DayFacts(
        val date: LocalDate,
        val occupied: List<TimelineEntry>,
        val hardBusy: List<TimelineEntry>,
        val freeWindows: List<IntRange>,
    ) {
        companion object {
            private const val MIN_WINDOW_MINUTES = 30

            fun of(date: LocalDate, occupied: List<TimelineEntry>): DayFacts {
                val hardBusy = occupied
                    .filter { it.kind in HARD_BUSY }
                    .sortedBy(TimelineEntry::startMinute)
                val windows = mutableListOf<IntRange>()
                var cursor = 0
                hardBusy.forEach { entry ->
                    if (entry.startMinute > cursor) windows += cursor until entry.startMinute
                    if (entry.endMinute > cursor) cursor = entry.endMinute
                }
                if (cursor < MINUTES_PER_DAY) windows += cursor until MINUTES_PER_DAY
                // Clamped to the window the planner actually schedules in. Reporting
                // "00:00-24:00 可用" was true about the day and false about the plan.
                val dayStart = ChatDayPlanner.DEFAULT_DAY_START_MINUTE
                val dayEnd = ChatDayPlanner.DEFAULT_DAY_END_MINUTE
                return DayFacts(
                    date = date,
                    occupied = occupied,
                    hardBusy = hardBusy,
                    freeWindows = windows.mapNotNull { window ->
                        val from = maxOf(window.first, dayStart)
                        val to = minOf(window.last, dayEnd - 1)
                        if (to - from + 1 >= MIN_WINDOW_MINUTES) from..to else null
                    },
                )
            }

            private const val MINUTES_PER_DAY = 24 * 60
            val HARD_BUSY = setOf(TimelineKind.COURSE, TimelineKind.REST, TimelineKind.COMMITMENT)
        }
    }
}
