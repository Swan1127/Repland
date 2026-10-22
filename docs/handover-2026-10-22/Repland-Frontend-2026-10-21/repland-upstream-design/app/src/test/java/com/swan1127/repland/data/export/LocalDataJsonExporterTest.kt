package com.swan1127.repland.data.export

import com.swan1127.repland.domain.model.AiPreferences
import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.LearningMemoryPointSnapshot
import com.swan1127.repland.domain.model.LearningMemorySnapshot
import com.swan1127.repland.domain.model.LocalDataSnapshot
import com.swan1127.repland.domain.model.MemoryState
import com.swan1127.repland.domain.model.ProfileEvidence
import com.swan1127.repland.domain.model.ProfileEvidenceScope
import com.swan1127.repland.domain.model.ReminderPreferences
import com.swan1127.repland.domain.model.RepeatRule
import com.swan1127.repland.domain.model.TEST_MONDAY
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TimeConstraintSettings
import com.swan1127.repland.domain.model.ToolOutputKind
import com.swan1127.repland.domain.model.ToolOutputRecord
import com.swan1127.repland.domain.model.UsageEvent
import com.swan1127.repland.domain.model.UsageEventType
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.confirmedPlan
import com.swan1127.repland.domain.model.segment
import com.swan1127.repland.domain.model.task
import com.swan1127.repland.testkit.MiniJson
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LocalDataJsonExporter] is a dependency-free, hand-written JSON writer. Its contract is
 * "the file a user keeps is valid JSON that contains what they had", so these tests parse the
 * output with a real reader rather than looking for substrings.
 */
class LocalDataJsonExporterTest {

    private fun snapshot(
        tasks: List<Task> = listOf(task("t1")),
        logs: List<TaskExecutionLog> = emptyList(),
        blocks: List<WeeklyTimeBlock> = emptyList(),
        overrides: List<DateOverride> = emptyList(),
        plans: List<ConfirmedPlan> = emptyList(),
        evidence: List<ProfileEvidence> = emptyList(),
        usage: List<UsageEvent> = emptyList(),
        toolOutputs: List<ToolOutputRecord> = emptyList(),
    ): LocalDataSnapshot = LocalDataSnapshot(
        generatedAtEpochMillis = 1_700_000_000_000L,
        tasks = tasks,
        executionLogs = logs,
        weeklyTimeBlocks = blocks,
        dateOverrides = overrides,
        timeConstraintSettings = TimeConstraintSettings(
            semesterFirstWeekMonday = TEST_MONDAY,
            updatedAtEpochMillis = 5L,
        ),
        planHistory = plans,
        categoryPreferences = CategoryPreferences.defaults,
        reminderPreferences = ReminderPreferences(isEnabled = true),
        aiPreferences = AiPreferences(isEnabled = false, consentedAtEpochMillis = null),
        profileEvidence = evidence,
        engagementMode = EngagementMode.GUIDED,
        usageEvents = usage,
        toolOutputs = toolOutputs,
    )

    private fun parse(snapshot: LocalDataSnapshot): Map<String, Any?> =
        MiniJson.parseObject(LocalDataJsonExporter.export(snapshot))

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objects(key: String): List<Map<String, Any?>> =
        this.getValue(key) as List<Map<String, Any?>>

    @Test
    fun `the export is valid JSON and declares its format version`() {
        val parsed = parse(snapshot())

        assertEquals("repland-local-export/v1", parsed["format"])
    }

    @Test
    fun `a minimal snapshot still emits every top-level section`() {
        val parsed = parse(snapshot())

        assertEquals(
            setOf(
                "format",
                "generatedAtEpochMillis",
                "tasks",
                "executionLogs",
                "weeklyTimeBlocks",
                "dateOverrides",
                "timeConstraintSettings",
                "planHistory",
                "categoryPreferences",
                "reminderPreferences",
                "aiPreferences",
                "profileEvidence",
                "toolOutputs",
                "engagementMode",
                "usageEvents",
            ),
            parsed.keys,
        )
    }

    @Test
    fun `quotes, backslashes, newlines, tabs and control characters survive a round trip`() {
        val messy = task("t1").copy(
            displayName = "他说\"你好\"",
            description = "第一行\n第二行\\反斜杠\t制表\u0001结束",
        )

        val exported = parse(snapshot(tasks = listOf(messy))).objects("tasks").single()

        assertEquals("他说\"你好\"", exported["displayName"])
        assertEquals("第一行\n第二行\\反斜杠\t制表\u0001结束", exported["description"])
    }

    @Test
    fun `a newline is written as an escape rather than a raw control character`() {
        val messy = task("t1").copy(description = "a\nb")

        val json = LocalDataJsonExporter.export(snapshot(tasks = listOf(messy)))

        // MiniJson rejects a raw control character inside a string, so the round-trip test
        // above would already fail; this pins the exact escaped form as well.
        assertTrue(json.contains("\"description\":\"a\\nb\""))
    }

    @Test
    fun `absent optional values are emitted as unquoted null`() {
        val bare = task("t1", durationMinutes = null)

        val exported = parse(snapshot(tasks = listOf(bare))).objects("tasks").single()

        assertNull(exported["dueDate"])
        assertNull(exported["totalDurationMinutes"])
        assertNull(exported["completionSummary"])
        assertNull(exported["scheduledForDate"])
        assertNull(exported["repeatRule"])
        assertEquals("Task t1", exported["displayName"])
    }

    @Test
    fun `a repeat rule is exported by name and a one-off as null`() {
        val exported = parse(
            snapshot(tasks = listOf(task("t1", repeatRule = RepeatRule.WEEKLY), task("t2"))),
        ).objects("tasks")

        // The enum name, like every other enum in this file: the export is a record, not a rendering.
        assertEquals("WEEKLY", exported[0]["repeatRule"])
        assertNull(exported[1]["repeatRule"])
    }

    @Test
    fun `a repeat rule and the occurrence it produced are both exported`() {
        // Two different facts, and both belong in the file: the rule is what the user chose, the
        // date is the occurrence the row currently holds. Exporting only the rule would lose when
        // the task is next due; exporting only the date would lose that it comes back at all.
        val exported = parse(
            snapshot(tasks = listOf(task("t1", scheduledForDate = TEST_MONDAY, repeatRule = RepeatRule.MONTHLY))),
        ).objects("tasks").single()

        assertEquals("MONTHLY", exported["repeatRule"])
        assertEquals(TEST_MONDAY.toString(), exported["scheduledForDate"])
    }

    @Test
    fun `tool writing is exported as its own array, keyed by task`() {
        val outputs = listOf(
            ToolOutputRecord(ToolOutputKind.KWL, "t1", "余项为什么收敛"),
            ToolOutputRecord(ToolOutputKind.REVIEW_POINT, "t1", "再推一遍"),
        )

        val exported = parse(snapshot(toolOutputs = outputs)).objects("toolOutputs")

        assertEquals(2, exported.size)
        assertEquals("KWL", exported[0]["kind"])
        assertEquals("t1", exported[0]["taskId"])
        assertEquals("余项为什么收敛", exported[0]["excerpt"])
        assertEquals("REVIEW_POINT", exported[1]["kind"])
    }

    @Test
    fun `an export with no tool writing still declares the array`() {
        // A reader has to be able to tell "the user wrote nothing" from "this build does not export
        // that", so the key is present and empty rather than omitted.
        assertTrue(parse(snapshot()).objects("toolOutputs").isEmpty())
    }

    @Test
    fun `tool writing is not attached to the evidence rows it relates to`() {
        // The row is stored and the writing is not, so the file must not claim a link the database
        // does not hold. They travel in separate sections and a reader joins them on task id.
        val row = ProfileEvidence(
            id = "e1",
            scope = ProfileEvidenceScope.GENERAL,
            conclusion = "已确认记录 1 条执行反馈",
            sources = emptyList(),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
            isUserEdited = false,
        )

        val exported = parse(
            snapshot(
                evidence = listOf(row),
                toolOutputs = listOf(ToolOutputRecord(ToolOutputKind.KWL, "t1", "一句话")),
            ),
        )

        assertEquals(
            setOf(
                "id",
                "scope",
                "conclusion",
                "sources",
                "createdAtEpochMillis",
                "updatedAtEpochMillis",
                "isUserEdited",
            ),
            exported.objects("profileEvidence").single().keys,
        )
    }

    @Test
    fun `an empty collection is emitted as an empty array`() {
        val parsed = parse(snapshot())

        assertTrue((parsed["executionLogs"] as List<*>).isEmpty())
        assertTrue((parsed["weeklyTimeBlocks"] as List<*>).isEmpty())
        assertTrue((parsed["planHistory"] as List<*>).isEmpty())
    }

    @Test
    fun `category preferences cover every category in a deterministic order`() {
        val prefs = parse(snapshot())["categoryPreferences"] as Map<*, *>

        // Sorted by enum name so two exports of the same data are byte-identical.
        assertEquals(listOf("COURSE", "EXTRACURRICULAR", "LEISURE", "OFFICE"), prefs.keys.toList())
        assertEquals(30.0, prefs["COURSE"] as Double, 0.0)
        assertEquals(20.0, prefs["LEISURE"] as Double, 0.0)
    }

    @Test
    fun `numbers are written unquoted so a reader gets numbers`() {
        val json = LocalDataJsonExporter.export(snapshot())

        assertTrue(json.contains("\"generatedAtEpochMillis\":1700000000000"))
        assertTrue(json.contains("\"updatedAtEpochMillis\":5"))
    }

    @Test
    fun `a plan history entry exports its segments and their locked flag`() {
        val plan = confirmedPlan(
            id = "p1",
            segments = listOf(
                segment("s1", taskId = "t1", startMinute = 9 * 60, endMinute = 10 * 60, isLocked = true),
            ),
        )

        val entry = parse(snapshot(plans = listOf(plan))).objects("planHistory").single()

        assertEquals("p1", entry["id"])
        assertEquals(true, entry["isCurrent"])
        val segmentJson = (entry["segments"] as List<*>).single() as Map<*, *>
        assertEquals("2026-03-02", segmentJson["date"])
        assertEquals(540.0, segmentJson["startMinute"] as Double, 0.0)
        assertEquals(true, segmentJson["isLocked"])
        assertEquals("focus", segmentJson["trackId"])
    }

    @Test
    fun `time constraint settings keep the semester start as a date string`() {
        val settings = parse(snapshot())["timeConstraintSettings"] as Map<*, *>

        assertEquals("2026-03-02", settings["semesterFirstWeekMonday"])
        assertEquals(5.0, settings["updatedAtEpochMillis"] as Double, 0.0)
    }

    @Test
    fun `a null semester start is emitted as JSON null rather than the text null`() {
        val withoutStart = snapshot().copy(
            timeConstraintSettings = TimeConstraintSettings(
                semesterFirstWeekMonday = null,
                updatedAtEpochMillis = 5L,
            ),
        )

        val settings = parse(withoutStart)["timeConstraintSettings"] as Map<*, *>

        assertNull(settings["semesterFirstWeekMonday"])
    }

    @Test
    fun `usage events keep their identifiers`() {
        val usage = listOf(
            UsageEvent(
                id = "u1",
                type = UsageEventType.MODE_CHANGED,
                occurredAtEpochMillis = 9L,
                mode = EngagementMode.GUIDED,
                subjectId = "timeline",
            ),
        )

        val event = parse(snapshot(usage = usage)).objects("usageEvents").single()

        assertEquals("u1", event["id"])
        assertEquals("MODE_CHANGED", event["type"])
        assertEquals("GUIDED", event["mode"])
        assertEquals("timeline", event["subjectId"])
    }

    @Test
    fun `the ai preferences record consent as null when it was never given`() {
        val ai = parse(snapshot())["aiPreferences"] as Map<*, *>

        assertEquals(false, ai["isEnabled"])
        assertNull(ai["consentedAtEpochMillis"])
    }

    @Test
    fun `legacy callers omit the learning extension rather than inventing an empty backup`() {
        assertFalse(parse(snapshot()).containsKey("learningMemory"))
    }

    @Test
    fun `learning state and user text survive a typed JSON round trip`() {
        val state = MemoryState(TEST_MONDAY, TEST_MONDAY.plusDays(4), 4.75, 6.25, 5.5, 4, 3, 1, 8)
        val expected = LearningMemorySnapshot(
            referenceEnabled = true,
            gain = 1.15,
            points = listOf(
                LearningMemoryPointSnapshot(
                    "point-1", "t1", "复习\"余项\"", "第一行\n第二行\\提示\t\u0001", TEST_MONDAY, 3, state,
                ),
            ),
        )

        val exported = parse(snapshot().copy(learningMemory = expected))["learningMemory"] as Map<*, *>
        val point = (exported["points"] as List<*>).single() as Map<*, *>
        val memory = point["memory"] as Map<*, *>
        val restored = LearningMemorySnapshot(
            version = (exported["version"] as Number).toInt(),
            referenceEnabled = exported["referenceEnabled"] as Boolean,
            gain = (exported["gain"] as Number).toDouble(),
            points = listOf(
                LearningMemoryPointSnapshot(
                    id = point["id"] as String,
                    taskId = point["taskId"] as String,
                    taskTitle = point["taskTitle"] as String,
                    content = point["content"] as String,
                    createdOn = LocalDate.parse(point["createdOn"] as String),
                    stage = (point["stage"] as Number).toInt(),
                    memory = MemoryState(
                        capturedOn = LocalDate.parse(memory["capturedOn"] as String),
                        lastEventOn = (memory["lastEventOn"] as String?)?.let(LocalDate::parse),
                        stabilityDays = (memory["stabilityDays"] as Number).toDouble(),
                        difficulty = (memory["difficulty"] as Number).toDouble(),
                        difficultySeed = (memory["difficultySeed"] as Number).toDouble(),
                        importance = (memory["importance"] as Number).toInt(),
                        reviews = (memory["reviews"] as Number).toInt(),
                        lapses = (memory["lapses"] as Number).toInt(),
                        exposures = (memory["exposures"] as Number).toInt(),
                    ),
                ),
            ),
        )

        assertEquals(expected, restored)
    }

    @Test
    fun `disabled reference still backs up saved learning feedback`() {
        val learning = LearningMemorySnapshot(
            referenceEnabled = false,
            gain = 0.85,
            points = listOf(LearningMemoryPointSnapshot("old", "t1", "标题", "已毕业", TEST_MONDAY, 5)),
        )

        val exported = parse(snapshot().copy(learningMemory = learning))["learningMemory"] as Map<*, *>
        val point = (exported["points"] as List<*>).single() as Map<*, *>

        assertEquals(false, exported["referenceEnabled"])
        assertEquals(1.0, exported["version"])
        assertEquals(5.0, point["stage"])
        assertEquals("已毕业", point["content"])
        assertNull(point["memory"])
    }

    @Test
    fun `a wired empty learning store is explicit and versioned`() {
        val learning = LearningMemorySnapshot(referenceEnabled = false, gain = 1.0, points = emptyList())

        val exported = parse(snapshot().copy(learningMemory = learning))["learningMemory"] as Map<*, *>

        assertEquals(1.0, exported["version"])
        assertEquals(emptyList<Any>(), exported["points"])
    }

    @Test
    fun `learning export whitelists committed fields rather than provider or team state`() {
        val learning = LearningMemorySnapshot(
            referenceEnabled = true,
            gain = 1.0,
            points = listOf(
                LearningMemoryPointSnapshot(
                    "point", "t1", "标题", "内容", TEST_MONDAY, 0,
                    MemoryState(TEST_MONDAY, null, 1.0, 5.5, 5.5, 3, 0, 0, 0),
                ),
            ),
        )

        val exported = parse(snapshot().copy(learningMemory = learning))
        val section = exported["learningMemory"] as Map<*, *>
        val point = (section["points"] as List<*>).single() as Map<*, *>
        val state = point["memory"] as Map<*, *>

        assertEquals(setOf("version", "referenceEnabled", "gain", "points"), section.keys)
        assertEquals(setOf("id", "taskId", "taskTitle", "content", "createdOn", "stage", "memory"), point.keys)
        assertEquals(
            setOf("capturedOn", "lastEventOn", "stabilityDays", "difficulty", "difficultySeed", "importance", "reviews", "lapses", "exposures"),
            state.keys,
        )
        assertNull(state["lastEventOn"])
        assertFalse(exported.containsKey("apiKey"))
        assertFalse(exported.containsKey("teamLog"))
        assertFalse(exported.containsKey("agentWorkspace"))
    }

    @Test
    fun `non finite learning values fail rather than produce an invalid backup`() {
        val state = MemoryState(TEST_MONDAY, null, Double.NaN, 5.5, 5.5, 3, 0, 0, 0)
        val invalidState = LearningMemorySnapshot(
            referenceEnabled = false,
            gain = 1.0,
            points = listOf(LearningMemoryPointSnapshot("point", "t1", "标题", "内容", TEST_MONDAY, 0, state)),
        )
        val invalidGain = invalidState.copy(points = emptyList(), gain = Double.POSITIVE_INFINITY)

        listOf(invalidState, invalidGain).forEach { learning ->
            assertTrue(runCatching { LocalDataJsonExporter.export(snapshot().copy(learningMemory = learning)) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
