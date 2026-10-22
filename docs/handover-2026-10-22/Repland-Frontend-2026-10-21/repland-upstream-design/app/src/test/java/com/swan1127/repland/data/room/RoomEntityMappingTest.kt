package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.DateOverrideType
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.ProfileEvidenceScope
import com.swan1127.repland.domain.model.RecurrenceAdvance
import com.swan1127.repland.domain.model.RepeatRule
import com.swan1127.repland.domain.model.TEST_MONDAY
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.UsageEventType
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The entity-to-domain mappers are the persistence boundary: a field dropped here does not
 * fail loudly, it makes data disappear on the next read. Every mapper is therefore asserted
 * field by field rather than by comparing whole objects, so a newly added column that the
 * mapper forgets to carry shows up as a specific missing assertion.
 *
 * These are all public (or internal) top-level functions, so no Room runtime is needed —
 * they are pure conversions and can be driven directly.
 */
class RoomEntityMappingTest {

    // ------------------------------------------------------------ tasks

    @Test
    fun `a task entity carries every field into the domain model`() {
        val entity = taskEntity()
        val task = entity.toDomain()

        assertEquals("task-1", task.id)
        assertEquals("复习高数", task.description)
        assertEquals("高数", task.displayName)
        assertEquals(TaskCategory.COURSE, task.category)
        assertEquals(TaskPriority.HIGH, task.userPriority)
        assertEquals(3, task.estimatedDays)
        assertEquals(120, task.totalDurationMinutes)
        assertEquals(TEST_MONDAY, task.dueDate)
        assertEquals(TaskStatus.IN_PROGRESS, task.status)
        assertEquals("完成了前三章", task.completionSummary)
        assertEquals(45, task.actualDurationMinutes)
        assertEquals(60, task.progressPercent)
        assertEquals(2, task.postponeCount)
        assertEquals(1_000L, task.createdAtEpochMillis)
        assertEquals(2_000L, task.updatedAtEpochMillis)
        assertEquals("待订正", task.completionResult)
        assertEquals(TEST_MONDAY.plusDays(1), task.scheduledForDate)
    }

    @Test
    fun `a task entity with no dates maps them to null rather than epoch day zero`() {
        val task = taskEntity(dueDateEpochDay = null, scheduledForEpochDay = null).toDomain()

        // Epoch day 0 is 1970-01-01, so a mapper that forgot the null check would produce a
        // task due in 1970 instead of one with no due date.
        assertNull(task.dueDate)
        assertNull(task.scheduledForDate)
    }

    @Test
    fun `a task entity reads its enums by name`() {
        val task = taskEntity(
            category = TaskCategory.LEISURE.name,
            userPriority = TaskPriority.REQUIRED.name,
            status = TaskStatus.CANCELLED.name,
        ).toDomain()

        assertEquals(TaskCategory.LEISURE, task.category)
        assertEquals(TaskPriority.REQUIRED, task.userPriority)
        assertEquals(TaskStatus.CANCELLED, task.status)
    }

    @Test
    fun `an unknown task enum name throws instead of falling back to a default`() {
        // Deliberate: silently substituting a default would quietly change a task's priority
        // or resurrect a cancelled task. Throwing surfaces the corruption at the boundary.
        assertThrows(IllegalArgumentException::class.java) {
            taskEntity(category = "NOT_A_CATEGORY").toDomain()
        }
        assertThrows(IllegalArgumentException::class.java) {
            taskEntity(userPriority = "URGENT").toDomain()
        }
        assertThrows(IllegalArgumentException::class.java) {
            taskEntity(status = "DONE").toDomain()
        }
    }

    @Test
    fun `a task entity carries its repeat rule into the domain model`() {
        val task = taskEntity(repeatRule = RepeatRule.WEEKLY.name).toDomain()

        assertEquals(RepeatRule.WEEKLY, task.repeatRule)
    }

    @Test
    fun `a task entity with no repeat rule reads as a one-off`() {
        assertNull(taskEntity(repeatRule = null).toDomain().repeatRule)
    }

    @Test
    fun `an unknown repeat rule name throws instead of reading as a one-off`() {
        // The dangerous default. Falling back to null would turn a task the user set to repeat into
        // a silent one-off: the row still looks fine, it just stops coming back. Throwing surfaces
        // the corruption at the boundary instead.
        assertThrows(IllegalArgumentException::class.java) {
            taskEntity(repeatRule = "FORTNIGHTLY").toDomain()
        }
    }

    @Test
    fun `every repeat rule name round-trips`() {
        RepeatRule.entries.forEach { rule ->
            assertEquals(rule, taskEntity(repeatRule = rule.name).toDomain().repeatRule)
        }
    }

    // ------------------------------------------------------------ the repeat roll-forward

    @Test
    fun `a roll-forward moves both dates and reopens the task`() {
        val advanced = taskEntity(
            dueDateEpochDay = TEST_MONDAY.plusDays(2).toEpochDay(),
            scheduledForEpochDay = TEST_MONDAY.toEpochDay(),
            repeatRule = RepeatRule.WEEKLY.name,
        ).withRecurrenceAdvanced(
            RecurrenceAdvance(
                steps = 1,
                scheduledForDate = TEST_MONDAY.plusWeeks(1),
                dueDate = TEST_MONDAY.plusWeeks(1).plusDays(2),
            ),
            now = 5_000L,
        )

        assertEquals(TaskStatus.NOT_STARTED.name, advanced.status)
        assertEquals(TEST_MONDAY.plusWeeks(1).toEpochDay(), advanced.scheduledForEpochDay)
        assertEquals(TEST_MONDAY.plusWeeks(1).plusDays(2).toEpochDay(), advanced.dueDateEpochDay)
        assertEquals(5_000L, advanced.updatedAtEpochMillis)
    }

    @Test
    fun `a roll-forward clears the outcome of the occurrence that just ended`() {
        // That occurrence's outcome is already in the execution log. Leaving it on a row that reads
        // NOT_STARTED would describe the *next* occurrence with the previous one's result — the
        // "sentence that is false" this project keeps finding.
        val advanced = taskEntity().withRecurrenceAdvanced(
            RecurrenceAdvance(steps = 1, scheduledForDate = TEST_MONDAY, dueDate = null),
            now = 5_000L,
        )

        assertNull(advanced.completionSummary)
        assertNull(advanced.completionResult)
        assertNull(advanced.actualDurationMinutes)
        assertNull(advanced.progressPercent)
        assertEquals("postponements belong to the occurrence that ended", 0, advanced.postponeCount)
    }

    @Test
    fun `a roll-forward keeps the task's identity and its rule`() {
        // The row *is* the next occurrence: same task, same cadence, new dates. A roll-forward that
        // cleared the rule would make the task repeat exactly once.
        val entity = taskEntity(repeatRule = RepeatRule.DAILY.name)

        val advanced = entity.withRecurrenceAdvanced(
            RecurrenceAdvance(steps = 1, scheduledForDate = TEST_MONDAY.plusDays(1), dueDate = null),
            now = 5_000L,
        )

        assertEquals(entity.id, advanced.id)
        assertEquals(entity.createdAtEpochMillis, advanced.createdAtEpochMillis)
        assertEquals(entity.displayName, advanced.displayName)
        assertEquals(RepeatRule.DAILY.name, advanced.repeatRule)
    }

    @Test
    fun `a roll-forward of an undated task leaves the deadline absent`() {
        val advanced = taskEntity(dueDateEpochDay = null, scheduledForEpochDay = null).withRecurrenceAdvanced(
            RecurrenceAdvance(steps = 1, scheduledForDate = TEST_MONDAY.plusDays(1), dueDate = null),
            now = 5_000L,
        )

        assertEquals(TEST_MONDAY.plusDays(1).toEpochDay(), advanced.scheduledForEpochDay)
        assertNull(advanced.dueDateEpochDay)
    }

    @Test
    fun `a roll-forward of a deadline-only task clears the planned day rather than keeping the old one`() {
        // The advance says "no planned day" and the row must obey it. A mapper that only wrote the
        // fields the advance *has* would leave the previous planned day behind, pointing at a date
        // that has already happened.
        val advanced = taskEntity(
            scheduledForEpochDay = TEST_MONDAY.toEpochDay(),
            dueDateEpochDay = TEST_MONDAY.toEpochDay(),
        ).withRecurrenceAdvanced(
            RecurrenceAdvance(steps = 1, scheduledForDate = null, dueDate = TEST_MONDAY.plusWeeks(1)),
            now = 5_000L,
        )

        assertNull(advanced.scheduledForEpochDay)
        assertEquals(TEST_MONDAY.plusWeeks(1).toEpochDay(), advanced.dueDateEpochDay)
    }

    // ------------------------------------------------------------ execution logs

    @Test
    fun `an execution log entity carries every field into the domain model`() {
        val log = executionLogEntity().toDomain()

        assertEquals("log-1", log.id)
        assertEquals("task-1", log.taskId)
        assertEquals(ExecutionLogEventType.FEEDBACK, log.eventType)
        assertEquals(TaskStatus.IN_PROGRESS, log.confirmedStatus)
        assertEquals("corrected-log", log.correctedLogId)
        assertEquals("replacement-task", log.replacementTaskId)
        assertEquals(3_000L, log.createdAtEpochMillis)
    }

    @Test
    fun `an execution log entity flattens its feedback columns into the feedback object`() {
        val log = executionLogEntity().toDomain()

        assertEquals(45, log.feedback.actualDurationMinutes)
        assertEquals(60, log.feedback.progressPercent)
        assertEquals("前三章", log.feedback.completedContent)
        assertEquals("待订正", log.feedback.completionResult)
        assertEquals("时间不够", log.feedback.postponeReason)
    }

    @Test
    fun `an execution log entity always produces a feedback object even when every column is null`() {
        val log = executionLogEntity(
            actualDurationMinutes = null,
            progressPercent = null,
            completedContent = null,
            completionResult = null,
            postponeReason = null,
        ).toDomain()

        // The domain type has a non-null feedback, so the mapper must materialise an empty one
        // rather than leaving a null behind.
        assertEquals(null, log.feedback.actualDurationMinutes)
        assertEquals(null, log.feedback.progressPercent)
        assertEquals(null, log.feedback.completedContent)
        assertEquals(null, log.feedback.completionResult)
        assertEquals(null, log.feedback.postponeReason)
    }

    @Test
    fun `an unknown execution log event type throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            executionLogEntity(eventType = "STARTED").toDomain()
        }
    }

    @Test
    fun `every execution log event type name round-trips`() {
        // The writer stores `eventType.name`, so a renamed constant would orphan existing rows.
        ExecutionLogEventType.entries.forEach { type ->
            assertEquals(type, executionLogEntity(eventType = type.name).toDomain().eventType)
        }
    }

    // ------------------------------------------------------------ weekly time blocks

    @Test
    fun `a weekly block entity carries every field into the domain model`() {
        val block = weeklyBlockEntity().toDomain()

        assertEquals("block-1", block.id)
        assertEquals("高数", block.title)
        assertEquals(TimeBlockKind.COURSE, block.kind)
        assertEquals(DayOfWeek.MONDAY, block.dayOfWeek)
        assertEquals(480, block.startMinute)
        assertEquals(540, block.endMinute)
        assertEquals("1-16", block.weekPattern)
        assertEquals(1_000L, block.createdAtEpochMillis)
        assertEquals(2_000L, block.updatedAtEpochMillis)
        assertEquals("course", block.trackId)
        assertEquals("带课本", block.note)
    }

    @Test
    fun `a weekly block maps the stored day number onto the right weekday`() {
        // java.time.DayOfWeek numbers Monday as 1 through Sunday as 7. An off-by-one here
        // moves every class to the wrong day, which is invisible until the user notices.
        val expected = mapOf(
            1 to DayOfWeek.MONDAY,
            2 to DayOfWeek.TUESDAY,
            3 to DayOfWeek.WEDNESDAY,
            4 to DayOfWeek.THURSDAY,
            5 to DayOfWeek.FRIDAY,
            6 to DayOfWeek.SATURDAY,
            7 to DayOfWeek.SUNDAY,
        )
        expected.forEach { (stored, weekday) ->
            assertEquals(weekday, weeklyBlockEntity(dayOfWeek = stored).toDomain().dayOfWeek)
        }
    }

    @Test
    fun `a stored day number outside one to seven throws rather than wrapping`() {
        // DayOfWeek.of rejects 0 and 8; a mapper that used `% 7` would silently move the block
        // to a different day instead of surfacing the corrupt row.
        assertThrows(DateTimeException::class.java) { weeklyBlockEntity(dayOfWeek = 0).toDomain() }
        assertThrows(DateTimeException::class.java) { weeklyBlockEntity(dayOfWeek = 8).toDomain() }
    }

    @Test
    fun `an unknown time block kind throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            weeklyBlockEntity(kind = "LECTURE").toDomain()
        }
    }

    @Test
    fun `every time block kind name round-trips`() {
        TimeBlockKind.entries.forEach { kind ->
            assertEquals(kind, weeklyBlockEntity(kind = kind.name).toDomain().kind)
        }
    }

    // ------------------------------------------------------------ date overrides

    @Test
    fun `a date override entity carries every field into the domain model`() {
        val override = dateOverrideEntity().toDomain()

        assertEquals("override-1", override.id)
        assertEquals("调课", override.title)
        assertEquals(DateOverrideType.BLOCKED, override.type)
        assertEquals(TEST_MONDAY, override.date)
        assertEquals(480, override.startMinute)
        assertEquals(540, override.endMinute)
        assertEquals(1_000L, override.createdAtEpochMillis)
        assertEquals(2_000L, override.updatedAtEpochMillis)
        assertEquals("学院会议", override.note)
    }

    @Test
    fun `a date override reads its date back from the epoch day`() {
        assertEquals(
            LocalDate.of(2026, 3, 2),
            dateOverrideEntity(dateEpochDay = LocalDate.of(2026, 3, 2).toEpochDay()).toDomain().date,
        )
    }

    @Test
    fun `every date override type name round-trips`() {
        DateOverrideType.entries.forEach { type ->
            assertEquals(type, dateOverrideEntity(type = type.name).toDomain().type)
        }
    }

    @Test
    fun `an unknown date override type throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            dateOverrideEntity(type = "HOLIDAY").toDomain()
        }
    }

    // ------------------------------------------------------------ small settings rows

    @Test
    fun `ai settings carry the switch and the consent timestamp`() {
        val preferences = AiSettingsEntity(isEnabled = true, consentedAtEpochMillis = 1_700_000_000_000L).toDomain()

        assertEquals(true, preferences.isEnabled)
        assertEquals(1_700_000_000_000L, preferences.consentedAtEpochMillis)
        assertEquals(true, preferences.hasExplicitConsent)
    }

    @Test
    fun `the ai settings row id is not part of the domain model`() {
        // The row is a singleton keyed by a constant, so the id is storage detail. Pinned so a
        // future reader does not expect it to survive the mapping.
        val preferences = AiSettingsEntity(id = "something-else", isEnabled = true).toDomain()

        assertEquals(true, preferences.isEnabled)
        assertEquals(AI_SETTINGS_ID, "bounded-ai-advisor")
    }

    @Test
    fun `default ai settings carry no consent`() {
        val preferences = AiSettingsEntity().toDomain()

        assertEquals(false, preferences.isEnabled)
        assertNull(preferences.consentedAtEpochMillis)
        assertEquals(false, preferences.hasExplicitConsent)
    }

    @Test
    fun `a category preference maps to its category and weight`() {
        assertEquals(
            TaskCategory.OFFICE to 70,
            CategoryPreferenceEntity(category = TaskCategory.OFFICE.name, weight = 70).toDomainPair(),
        )
    }

    @Test
    fun `an unknown category preference name throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            CategoryPreferenceEntity(category = "SPORT", weight = 10).toDomainPair()
        }
    }

    @Test
    fun `reminder settings carry the switch`() {
        assertEquals(true, ReminderSettingsEntity(isEnabled = true).toDomain().isEnabled)
        assertEquals(false, ReminderSettingsEntity().toDomain().isEnabled)
        assertEquals(REMINDER_SETTINGS_ID, "local-reminders")
    }

    @Test
    fun `a usage event carries every field`() {
        val event = UsageEventEntity(
            id = "event-1",
            type = UsageEventType.TIMELINE_OPENED.name,
            occurredAtEpochMillis = 5_000L,
            mode = EngagementMode.GUIDED.name,
            subjectId = "task-1",
        ).toDomain()

        assertEquals("event-1", event.id)
        assertEquals(UsageEventType.TIMELINE_OPENED, event.type)
        assertEquals(5_000L, event.occurredAtEpochMillis)
        assertEquals(EngagementMode.GUIDED, event.mode)
        assertEquals("task-1", event.subjectId)
    }

    @Test
    fun `a usage event with no subject keeps it null`() {
        assertNull(
            UsageEventEntity(
                id = "event-2",
                type = UsageEventType.MODE_CHANGED.name,
                occurredAtEpochMillis = 5_000L,
                mode = EngagementMode.EXECUTOR.name,
                subjectId = null,
            ).toDomain().subjectId,
        )
    }

    @Test
    fun `every usage event type and mode name round-trips`() {
        UsageEventType.entries.forEach { type ->
            assertEquals(
                type,
                UsageEventEntity("e", type.name, 1L, EngagementMode.CO_PLANNER.name, null).toDomain().type,
            )
        }
        EngagementMode.entries.forEach { mode ->
            assertEquals(
                mode,
                UsageEventEntity("e", UsageEventType.MODE_CHANGED.name, 1L, mode.name, null).toDomain().mode,
            )
        }
    }

    // ------------------------------------------------------------ profile evidence

    @Test
    fun `profile evidence carries the conclusion, scope and edit flag`() {
        val evidence = profileEvidenceEntity().toDomain(sources = emptyList())

        assertEquals("evidence-1", evidence.id)
        assertEquals(ProfileEvidenceScope.LEARNING, evidence.scope)
        assertEquals("晚上更适合做数学", evidence.conclusion)
        assertEquals(1_000L, evidence.createdAtEpochMillis)
        assertEquals(2_000L, evidence.updatedAtEpochMillis)
        assertEquals(true, evidence.isUserEdited)
    }

    @Test
    fun `profile evidence takes its sources from the caller not from the stored id list`() {
        // The stored column is the raw pipe-delimited id list; the domain object wants fully
        // resolved sources. The mapper must not leak the raw string through.
        val resolved = listOf(
            com.swan1127.repland.domain.model.ProfileEvidenceSource(
                executionLogId = "log-1",
                taskId = "task-1",
                taskDisplayName = "高数",
                eventType = ExecutionLogEventType.FEEDBACK,
                createdAtEpochMillis = 1L,
            ),
        )
        val evidence = profileEvidenceEntity().toDomain(sources = resolved)

        assertEquals(resolved, evidence.sources)
    }

    @Test
    fun `the stored source id list splits on the pipe`() {
        assertEquals(
            listOf("a", "b", "c"),
            profileEvidenceEntity(sourceLogIds = "a|b|c").sourceLogIdList(),
        )
    }

    @Test
    fun `an empty stored source id list yields no ids`() {
        assertEquals(emptyList<String>(), profileEvidenceEntity(sourceLogIds = "").sourceLogIdList())
    }

    @Test
    fun `blank entries in the stored source id list are dropped`() {
        // A leading, trailing or doubled separator must not produce a blank id that would then
        // be looked up (and reported as a missing source) by the join.
        assertEquals(
            listOf("a", "b"),
            profileEvidenceEntity(sourceLogIds = "|a||b|").sourceLogIdList(),
        )
    }

    @Test
    fun `every profile evidence scope name round-trips`() {
        ProfileEvidenceScope.entries.forEach { scope ->
            assertEquals(scope, profileEvidenceEntity(scope = scope.name).toDomain(emptyList()).scope)
        }
    }

    @Test
    fun `an unknown profile evidence scope throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            profileEvidenceEntity(scope = "SLEEP").toDomain(emptyList())
        }
    }

    // ------------------------------------------------------------ builders

    private fun taskEntity(
        category: String = TaskCategory.COURSE.name,
        userPriority: String = TaskPriority.HIGH.name,
        status: String = TaskStatus.IN_PROGRESS.name,
        dueDateEpochDay: Long? = TEST_MONDAY.toEpochDay(),
        scheduledForEpochDay: Long? = TEST_MONDAY.plusDays(1).toEpochDay(),
        repeatRule: String? = null,
    ): TaskEntity = TaskEntity(
        id = "task-1",
        description = "复习高数",
        displayName = "高数",
        category = category,
        userPriority = userPriority,
        estimatedDays = 3,
        totalDurationMinutes = 120,
        dueDateEpochDay = dueDateEpochDay,
        status = status,
        completionSummary = "完成了前三章",
        actualDurationMinutes = 45,
        progressPercent = 60,
        postponeCount = 2,
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 2_000L,
        completionResult = "待订正",
        scheduledForEpochDay = scheduledForEpochDay,
        repeatRule = repeatRule,
    )

    private fun executionLogEntity(
        eventType: String = ExecutionLogEventType.FEEDBACK.name,
        actualDurationMinutes: Int? = 45,
        progressPercent: Int? = 60,
        completedContent: String? = "前三章",
        completionResult: String? = "待订正",
        postponeReason: String? = "时间不够",
    ): ExecutionLogEntity = ExecutionLogEntity(
        id = "log-1",
        taskId = "task-1",
        eventType = eventType,
        confirmedStatus = TaskStatus.IN_PROGRESS.name,
        actualDurationMinutes = actualDurationMinutes,
        progressPercent = progressPercent,
        completedContent = completedContent,
        completionResult = completionResult,
        postponeReason = postponeReason,
        correctedLogId = "corrected-log",
        replacementTaskId = "replacement-task",
        createdAtEpochMillis = 3_000L,
    )

    private fun weeklyBlockEntity(
        kind: String = TimeBlockKind.COURSE.name,
        dayOfWeek: Int = DayOfWeek.MONDAY.value,
    ): WeeklyTimeBlockEntity = WeeklyTimeBlockEntity(
        id = "block-1",
        title = "高数",
        kind = kind,
        dayOfWeek = dayOfWeek,
        startMinute = 480,
        endMinute = 540,
        weekPattern = "1-16",
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 2_000L,
        trackId = "course",
        note = "带课本",
    )

    private fun dateOverrideEntity(
        type: String = DateOverrideType.BLOCKED.name,
        dateEpochDay: Long = TEST_MONDAY.toEpochDay(),
    ): DateOverrideEntity = DateOverrideEntity(
        id = "override-1",
        title = "调课",
        type = type,
        dateEpochDay = dateEpochDay,
        startMinute = 480,
        endMinute = 540,
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 2_000L,
        note = "学院会议",
    )

    private fun profileEvidenceEntity(
        scope: String = ProfileEvidenceScope.LEARNING.name,
        sourceLogIds: String = "log-1|log-2",
    ): ProfileEvidenceEntity = ProfileEvidenceEntity(
        id = "evidence-1",
        scope = scope,
        conclusion = "晚上更适合做数学",
        sourceLogIds = sourceLogIds,
        sourceFingerprint = "fingerprint-1",
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 2_000L,
        isUserEdited = true,
    )
}
