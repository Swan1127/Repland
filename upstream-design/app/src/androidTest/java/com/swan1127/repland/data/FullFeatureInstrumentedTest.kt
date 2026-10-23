package com.swan1127.repland.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.data.export.IcsExporter
import com.swan1127.repland.data.export.PlanImageExporter
import com.swan1127.repland.data.export.PlanImageTexts
import com.swan1127.repland.data.importer.IcsImporter
import com.swan1127.repland.data.importer.IcsTiming
import com.swan1127.repland.data.importer.PdfTimetableImporter
import com.swan1127.repland.data.importer.TimetableGridParser
import com.swan1127.repland.data.importer.XlsxTimetableImporter
import com.swan1127.repland.data.room.ReplandDatabase
import com.swan1127.repland.data.room.RoomDataManagementRepository
import com.swan1127.repland.data.room.RoomPlanRepository
import com.swan1127.repland.data.room.RoomProfileEvidenceRepository
import com.swan1127.repland.data.room.RoomTaskRepository
import com.swan1127.repland.data.room.RoomTimeRepository
import com.swan1127.repland.domain.model.CalendarImport
import com.swan1127.repland.domain.model.CalendarImportOutcome
import com.swan1127.repland.domain.model.CalendarSkipReason
import com.swan1127.repland.domain.model.ClassPeriodClock
import com.swan1127.repland.domain.model.CourseWeekPattern
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.DateOverrideType
import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.ImportedCourse
import com.swan1127.repland.domain.model.PlanGenerator
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.ProfileEvidenceScope
import com.swan1127.repland.domain.model.RepeatRule
import com.swan1127.repland.domain.model.SubTask
import com.swan1127.repland.domain.model.SubTasks
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import com.swan1127.repland.domain.ports.DataExportFormat
import com.swan1127.repland.domain.ports.ExportSink
import com.swan1127.repland.domain.ports.TimetableSource
import com.swan1127.repland.ui.data.DataManagementResult
import com.swan1127.repland.ui.data.DataManagementViewModel
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cross-module acceptance with the private, two-page timetable and real SQLite/Android writers.
 * Every database is in memory. Files go into the target UID's dedicated externalFilesDir/verification;
 * no production database, preferences, profile, credentials, or network are accessed.
 * This is repository/export acceptance, not a claim that Android document pickers were tapped.
 */
@RunWith(AndroidJUnit4::class)
class FullFeatureInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: ReplandDatabase
    private lateinit var tasks: RoomTaskRepository
    private lateinit var time: RoomTimeRepository
    private lateinit var plans: RoomPlanRepository
    private lateinit var data: RoomDataManagementRepository
    private lateinit var originalCourses: List<ImportedCourse>
    private lateinit var output: File
    private val zone = ZoneId.of("Asia/Shanghai")
    // Explicit test setting: the source PDF does not establish the semester's first Monday.
    private val firstMonday = LocalDate.of(2026, 8, 31)
    private val planningDate = firstMonday.plusWeeks(2)
    private val clock = Clock.fixed(planningDate.atStartOfDay(zone).toInstant(), zone)

    @Before
    fun setUp() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixtureContext = instrumentation.context
        // Instrumentation executes under the target UID. The test APK context supplies read-only
        // fixture assets; targetContext supplies writable storage, never the real app database.
        context = instrumentation.targetContext
        assumeTrue("Private timetable fixture was not supplied", fixtureContext.assets.list("")!!.contains("user-timetable.pdf"))
        database = Room.inMemoryDatabaseBuilder(context, ReplandDatabase::class.java).build()
        tasks = RoomTaskRepository(database)
        time = RoomTimeRepository(database)
        plans = RoomPlanRepository(database)
        data = RoomDataManagementRepository(database)
        originalCourses = PdfTimetableImporter(context)
            .parse(TimetableSource { fixtureContext.assets.open("user-timetable.pdf") })
        assertEquals(19, originalCourses.size)
        time.saveWeeklyBlocks(originalCourses.mapNotNull(ClassPeriodClock::toWeeklyTimeBlockDraft))
        time.saveSemesterFirstWeekMonday(firstMonday)
        output = File(requireNotNull(context.getExternalFilesDir(null)), "verification/full-feature").apply { mkdirs() }
        assertTrue(output.isDirectory)
    }

    @After
    fun closeDatabase() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun checklistAndConfirmedFeedbackProduceTraceableEditableEvidenceWithoutRewritingHistory() = runBlocking {
        tasks.save(draft("learning", "数据结构与算法复习"))
        tasks.setSubTasks("learning", listOf(
            SubTask("read", "阅读树结构", true, 1),
            SubTask("practice", "完成树遍历练习", true, 2),
        ))
        val checked = tasks.observeTasks().first().single()
        assertTrue(SubTasks.progress(checked).allDone)
        assertEquals("Checking the final item must not complete its parent", TaskStatus.NOT_STARTED, checked.status)

        tasks.confirmStatus("learning", TaskStatus.IN_PROGRESS, TaskFeedback())
        tasks.recordPartialCompletion("learning", TaskFeedback(
            actualDurationMinutes = 35, progressPercent = 50, completedContent = "已阅读树结构",
        ))
        tasks.confirmStatus("learning", TaskStatus.COMPLETED, TaskFeedback(
            actualDurationMinutes = 80, progressPercent = 100,
            completedContent = "完成二叉树遍历练习", completionResult = "通过自测",
        ))
        val completion = tasks.observeExecutionLogs("learning").first().last()
        tasks.correctExecutionLog("learning", completion.id, TaskFeedback(
            actualDurationMinutes = 75, progressPercent = 100,
            completedContent = "完成二叉树遍历练习", completionResult = "更正用时：75 分钟",
        ))

        val logs = tasks.observeExecutionLogs("learning").first()
        assertEquals(4, logs.size)
        assertEquals(completion, logs.single { it.id == completion.id })
        assertEquals(completion.id, logs.last().correctedLogId)
        assertTrue(logs.zipWithNext().all { (first, second) -> first.createdAtEpochMillis < second.createdAtEpochMillis })
        val currentTask = tasks.observeTasks().first().single()
        assertEquals(TaskStatus.COMPLETED, currentTask.status)
        assertEquals(80, currentTask.actualDurationMinutes)
        assertEquals("通过自测", currentTask.completionResult)

        val evidence = RoomProfileEvidenceRepository(database)
        evidence.generateFromConfirmedFeedback()
        val initial = evidence.observe().first()
        assertEquals(setOf(ProfileEvidenceScope.GENERAL, ProfileEvidenceScope.LEARNING), initial.map { it.scope }.toSet())
        val effectiveIds = setOf(logs[1].id, logs[3].id)
        initial.forEach { row ->
            assertEquals(effectiveIds, row.sources.map { it.executionLogId }.toSet())
            assertTrue(row.sources.all { it.taskId == "learning" && it.taskDisplayName == "数据结构与算法复习" })
        }
        assertTrue(initial.single { it.scope == ProfileEvidenceScope.GENERAL }.conclusion.contains("110 分钟"))
        evidence.generateFromConfirmedFeedback()
        assertEquals(initial.map { it.id }.toSet(), evidence.observe().first().map { it.id }.toSet())
        val learning = initial.single { it.scope == ProfileEvidenceScope.LEARNING }
        evidence.updateConclusion(learning.id, "已完成树结构练习；尚需复习复杂度。")
        val edited = evidence.observe().first().single { it.id == learning.id }
        assertTrue(edited.isUserEdited)
        assertEquals(learning.sources, edited.sources)
        assertEquals("已完成树结构练习；尚需复习复杂度。", edited.conclusion)

        val jsonFile = export(DataExportFormat.JSON, "confirmed-feedback.json")
        val json = JSONObject(jsonFile.readText())
        assertEquals(4, json.getJSONArray("executionLogs").length())
        assertEquals(2, json.getJSONArray("profileEvidence").length())
        assertEquals(2, json.getJSONArray("tasks").getJSONObject(0).getJSONArray("subtasks").length())
        assertEquals(19, json.getJSONArray("weeklyTimeBlocks").length())
    }

    @Test
    fun recurringCompletionAndReplacementKeepOccurrenceEvidenceAndOriginalIdentity() = runBlocking {
        val today = LocalDate.now()
        val scheduled = today.minusWeeks(3)
        tasks.save(draft("weekly", "每周复习").copy(
            scheduledForDate = scheduled, dueDate = scheduled.plusDays(2), repeatRule = RepeatRule.WEEKLY,
        ))
        val checklist = listOf(
            SubTask("weekly-read", "阅读教材", true, 101),
            SubTask("weekly-practice", "完成练习", true, 102),
        )
        tasks.setSubTasks("weekly", checklist)
        tasks.confirmStatus("weekly", TaskStatus.POSTPONED, TaskFeedback(postponeReason = "课程实验延期"))
        tasks.confirmStatus("weekly", TaskStatus.COMPLETED, TaskFeedback(
            actualDurationMinutes = 40, progressPercent = 100, completedContent = "完成本周复习", completionResult = "整理了错题",
        ))
        val advanced = tasks.observeTasks().first().single()
        assertEquals(TaskStatus.NOT_STARTED, advanced.status)
        assertEquals(today.plusWeeks(1), advanced.scheduledForDate)
        assertEquals(today.plusWeeks(1).plusDays(2), advanced.dueDate)
        assertEquals(0, advanced.postponeCount)
        assertNull(advanced.completionSummary)
        assertNull(advanced.completionResult)
        assertNull(advanced.actualDurationMinutes)
        assertNull(advanced.progressPercent)
        assertEquals(checklist.map { it.copy(isDone = false) }, advanced.subtasks)
        assertEquals(0, SubTasks.progress(advanced).done)
        val occurrenceLogs = tasks.observeExecutionLogs("weekly").first()
        assertEquals(listOf(TaskStatus.POSTPONED, TaskStatus.COMPLETED), occurrenceLogs.map { it.confirmedStatus })
        assertEquals(40, occurrenceLogs.last().feedback.actualDurationMinutes)

        val replacementId = tasks.replaceTask("weekly", draft("ignored-id", "每周错题复盘").copy(
            scheduledForDate = advanced.scheduledForDate, dueDate = advanced.dueDate, repeatRule = RepeatRule.WEEKLY,
        ))
        assertNotEquals("weekly", replacementId)
        assertNotEquals("ignored-id", replacementId)
        val rows = tasks.observeTasks().first().associateBy { it.id }
        assertEquals(TaskStatus.REPLACED, rows.getValue("weekly").status)
        assertEquals(TaskStatus.NOT_STARTED, rows.getValue(replacementId).status)
        assertEquals(RepeatRule.WEEKLY, rows.getValue(replacementId).repeatRule)
        val history = tasks.observeExecutionLogs("weekly").first()
        assertEquals(occurrenceLogs, history.take(2))
        assertEquals(ExecutionLogEventType.REPLACEMENT, history.last().eventType)
        assertEquals(replacementId, history.last().replacementTaskId)
        assertTrue(tasks.observeExecutionLogs(replacementId).first().isEmpty())

        val evidence = RoomProfileEvidenceRepository(database)
        evidence.generateFromConfirmedFeedback()
        assertTrue(evidence.observe().first().flatMap { it.sources }.all { it.taskId == "weekly" })
        val json = JSONObject(export(DataExportFormat.JSON, "recurrence-and-replacement.json").readText())
        assertEquals(2, json.getJSONArray("tasks").length())
        assertEquals(3, json.getJSONArray("executionLogs").length())
    }

    @Test
    fun actualCoursesConstrainConfirmedPlansAndHistoryRestoresExportableSegments() = runBlocking {
        DayOfWeek.entries.forEach { day ->
            time.saveWeeklyBlock(WeeklyTimeBlockDraft(
                title = "验收可用时间", kind = TimeBlockKind.AVAILABLE, dayOfWeek = day, startMinute = 480, endMinute = 1320,
            ))
        }
        time.saveDateOverride(DateOverrideDraft(
            title = "临时预约", type = DateOverrideType.BLOCKED, date = planningDate, startMinute = 720, endMinute = 780,
        ))
        tasks.save(draft("algorithm", "算法复习").copy(totalDurationMinutes = 240, dueDate = planningDate.plusDays(3)))
        tasks.save(draft("physics", "物理复习").copy(totalDurationMinutes = 240, dueDate = planningDate.plusDays(3)))
        val blocks = time.currentWeeklyBlocks()
        val overrides = data.snapshot().dateOverrides
        val generated = PlanGenerator.generate(
            tasks.observeTasks().first(), blocks, overrides, firstMonday, clock = clock,
        )
        assertTrue(generated.unscheduledTasks.isEmpty())
        assertEquals(480, generated.segments.sumOf { it.endMinute - it.startMinute })
        generated.segments.forEach { segment ->
            assertFalse(blocks.any { block ->
                block.kind == TimeBlockKind.COURSE && block.dayOfWeek == segment.date.dayOfWeek &&
                    CourseWeekPattern.appliesOn(block.weekPattern, segment.date, firstMonday) &&
                    segment.startMinute < block.endMinute && segment.endMinute > block.startMinute
            })
            assertFalse(segment.date == planningDate && segment.startMinute < 780 && segment.endMinute > 720)
        }
        plans.accept(generated)
        val first = requireNotNull(plans.observeCurrentPlan().first())
        plans.setSegmentLocked(first.segments.first().id, true)
        val firstLocked = requireNotNull(plans.observeCurrentPlan().first())
        tasks.save(draft("communication", "数据通信复习").copy(totalDurationMinutes = 60))
        val locks = firstLocked.segments.filter { it.isLocked }
        val regenerated = PlanGenerator.generate(
            tasks.observeTasks().first(), blocks, overrides, firstMonday, lockedSegments = locks, clock = clock,
        )
        assertTrue(regenerated.segments.containsAll(locks))
        plans.accept(regenerated)
        assertEquals(2, plans.observePlanHistory().first().size)
        assertEquals(1, plans.observePlanHistory().first().count { it.isCurrent })
        plans.restore(firstLocked.id)
        val restored = requireNotNull(plans.observeCurrentPlan().first())
        assertNotEquals(firstLocked.id, restored.id)
        assertEquals(firstLocked.segments.map(::segmentKey).toSet(), restored.segments.map(::segmentKey).toSet())
        assertEquals(firstLocked.orderedTaskIds, restored.orderedTaskIds)
        assertEquals(3, plans.observePlanHistory().first().size)
        assertEquals(1, plans.observePlanHistory().first().count { it.isCurrent })
        val taskRows = tasks.observeTasks().first()
        assertTrue(restored.segments.all { segment -> taskRows.any { it.id == segment.taskId } })

        val ics = File(output, "confirmed-plan.ics").apply { writeText(IcsExporter.build(restored, taskRows)) }
        val events = IcsImporter.parse(ics.readText(), zone)
        assertEquals(restored.segments.size, events.size)
        assertEquals(restored.segments.map { segment ->
            val name = taskRows.single { it.id == segment.taskId }.displayName
            "$name|${segment.date}|${segment.startMinute}|${segment.endMinute}"
        }.toSet(), events.map { event ->
            val timing = event.timing as IcsTiming.Timed
            "${event.summary}|${timing.start.toLocalDate()}|${timing.start.hour * 60 + timing.start.minute}|${timing.end.hour * 60 + timing.end.minute}"
        }.toSet())
        val reimport = CalendarImport.plan(events, blocks, overrides, "未命名", firstMonday) as CalendarImportOutcome.Imported
        assertEquals(0, reimport.addedCount)
        assertEquals(events.size, reimport.skipped.count { it.reason == CalendarSkipReason.OWN_EXPORT })
        val day = restored.segments.first().date
        assertLayoutFits(PlanImageExporter.layoutPlan(
            day, restored.segments.filter { it.date == day }, taskRows, imageTexts,
        ))
        writeAndReadImage("confirmed-plan.png", PlanImageExporter.renderPlan(
            day, restored.segments.filter { it.date == day }, taskRows, imageTexts,
        ))
        val json = JSONObject(export(DataExportFormat.JSON, "plan-history.json").readText())
        assertEquals(3, json.getJSONArray("planHistory").length())
    }

    @Test
    fun realTimetableExportsProduceReadableJsonXlsxAndPngWithNoLostCourseIdentity() = runBlocking {
        val title = "复习\"引号\"与\\路径；树结构"
        tasks.save(draft("export-task", title))
        tasks.setSubTasks("export-task", listOf(SubTask("export-subtask", "章节一\n原始换行", false, 10)))
        tasks.recordFeedback("export-task", TaskFeedback(
            actualDurationMinutes = 25, completedContent = "笔记包含\"引号\"、反斜杠\\和换行\n第二行",
        ))
        val json = JSONObject(export(DataExportFormat.JSON, "real-timetable.json").readText())
        assertEquals(title, json.getJSONArray("tasks").getJSONObject(0).getString("displayName"))
        assertEquals("章节一\n原始换行", json.getJSONArray("tasks").getJSONObject(0).getJSONArray("subtasks").getJSONObject(0).getString("title"))
        assertEquals(19, json.getJSONArray("weeklyTimeBlocks").length())
        assertEquals(firstMonday.toString(), json.getJSONObject("timeConstraintSettings").getString("semesterFirstWeekMonday"))
        assertEquals(1, json.getJSONArray("executionLogs").length())

        val xlsx = export(DataExportFormat.XLSX, "real-timetable.xlsx")
        ZipFile(xlsx).use { archive ->
            assertTrue(archive.getEntry("[Content_Types].xml") != null)
            assertTrue(archive.getEntry("xl/workbook.xml") != null)
            val xml = archive.getInputStream(archive.getEntry("xl/workbook.xml")).use {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
            }
            assertEquals(5, xml.getElementsByTagName("sheet").length)
            val sheets = (1..5).map { index -> archive.getEntry("xl/worksheets/sheet$index.xml") }
            assertTrue(sheets.all { it != null })
            sheets.forEach { entry ->
                archive.getInputStream(entry).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
            }
        }
        val grid = XlsxTimetableImporter().parse(TimetableSource { xlsx.inputStream() })
        val readBack = TimetableGridParser.parse(grid)
        assertEquals(19, readBack.size)
        assertEquals(originalCourses.map(::courseKey).toSet(), readBack.map(::courseKey).toSet())
        // Re-import into a second real database: exported rows are still usable timetable data.
        val copy = Room.inMemoryDatabaseBuilder(context, ReplandDatabase::class.java).build()
        try {
            val copyTime = RoomTimeRepository(copy)
            copyTime.saveWeeklyBlocks(readBack.mapNotNull(ClassPeriodClock::toWeeklyTimeBlockDraft))
            val keys = copyTime.currentWeeklyBlocks().map { block ->
                val span = requireNotNull(ClassPeriodClock.periodSpanOf(block.startMinute, block.endMinute))
                "${block.title}|${block.dayOfWeek}|${span.first}|${span.second}|${block.weekPattern}"
            }.toSet()
            assertEquals(originalCourses.map(::courseKey).toSet(), keys)
        } finally {
            copy.close()
        }
        val timetable = time.currentWeeklyBlocks()
        val layout = PlanImageExporter.layoutTimetable(timetable, imageTexts)
        assertLayoutFits(layout)
        val imageRows = layout.sections.flatMap { it.rows }.associateBy { it.sourceId }
        assertEquals("Every imported course must have its own image row", timetable.map { it.id }.toSet(), imageRows.keys)
        timetable.forEach { block ->
            val texts = imageRows.getValue(block.id).texts.map { it.text }
            assertTrue("The full course title must appear without ellipsis", block.title in texts)
            assertTrue("The complete week notation must be visible below its own course", texts.any { it.contains(requireNotNull(block.weekPattern)) })
        }
        assertTrue(layout.header.any { it.text == imageTexts.timetableOverview })
        writeAndReadImage("real-timetable.png", PlanImageExporter.renderTimetable(timetable, imageTexts))

        // Export-only layouts cannot offer a tap to reveal clipped text, so long names must wrap.
        val longName = "这是一门包含完整课程信息的长标题，需要自动换行而不能省略：".repeat(4)
        val longCourse = timetable.first().copy(id = "wrapped-course", title = longName)
        val wrappedTimetable = PlanImageExporter.layoutTimetable(listOf(longCourse), imageTexts)
        assertLayoutFits(wrappedTimetable)
        val wrappedTitle = wrappedTimetable.sections.single().rows.single().texts.single { it.text == longName }
        assertTrue(wrappedTitle.layout.lineCount > 1)
        writeAndReadImage("wrapped-timetable.png", PlanImageExporter.renderTimetable(listOf(longCourse), imageTexts))

        val task = tasks.observeTasks().first().single().copy(displayName = longName)
        val segment = PlannedSegment(taskId = task.id, date = planningDate, startMinute = 780, endMinute = 870)
        val wrappedPlan = PlanImageExporter.layoutPlan(planningDate, listOf(segment), listOf(task), imageTexts)
        assertLayoutFits(wrappedPlan)
        assertTrue(wrappedPlan.sections.single().rows.single().texts.single { it.text == longName }.layout.lineCount > 1)
        writeAndReadImage("wrapped-plan.png", PlanImageExporter.renderPlan(planningDate, listOf(segment), listOf(task), imageTexts))

        val emptyPlan = PlanImageExporter.layoutPlan(planningDate, emptyList(), emptyList(), imageTexts)
        val emptyTimetable = PlanImageExporter.layoutTimetable(emptyList(), imageTexts)
        assertLayoutFits(emptyPlan)
        assertLayoutFits(emptyTimetable)
        assertTrue(emptyPlan.header.any { it.text == imageTexts.emptyPlan })
        assertTrue(emptyTimetable.header.any { it.text == imageTexts.emptyTimetable })
        assertTrue(emptyPlan.sections.flatMap { it.rows }.isEmpty())
        assertTrue(emptyTimetable.sections.isEmpty())
        writeAndReadImage("empty-plan.png", PlanImageExporter.renderPlan(planningDate, emptyList(), emptyList(), imageTexts))
        writeAndReadImage("empty-timetable.png", PlanImageExporter.renderTimetable(emptyList(), imageTexts))
        assertEquals("Export must leave the original imported database intact", 19, time.currentWeeklyBlocks().size)
    }

    private fun assertLayoutFits(layout: PlanImageExporter.ImageLayout) {
        layout.header.zipWithNext().forEach { (first, second) -> assertTrue(second.top > first.bottom) }
        var previousBottom = layout.header.last().bottom
        val allTexts = layout.header.toMutableList()
        layout.sections.forEach { section ->
            section.heading?.let {
                assertTrue("Day heading must not overlap the image header or preceding course", it.top > previousBottom)
                previousBottom = it.bottom
                allTexts += it
            }
            section.rows.forEach { row ->
                assertTrue("The first card must not cover subtitle text, and cards must not overlap", row.card.top > previousBottom)
                row.texts.forEach { text ->
                    assertTrue(text.top >= row.card.top && text.bottom <= row.card.bottom)
                    assertTrue(text.left >= row.card.left && text.left + text.layout.width <= row.card.right)
                }
                // The clock has its own column; title and metadata share a column and must stack.
                row.texts.drop(1).zipWithNext().forEach { (first, second) -> assertTrue(second.top > first.bottom) }
                previousBottom = row.card.bottom
                allTexts += row.texts
            }
        }
        allTexts.forEach { text ->
            assertEquals(text.text.length, text.layout.getLineEnd(text.layout.lineCount - 1))
            assertTrue((0 until text.layout.lineCount).all { text.layout.getEllipsisCount(it) == 0 })
            assertTrue(text.left >= 0 && text.left + text.layout.width <= 1080)
        }
        assertTrue("All measured content must fit inside the exported bitmap", layout.height > previousBottom)
    }

    private suspend fun export(format: DataExportFormat, filename: String): File {
        val file = File(output, filename)
        val model = DataManagementViewModel(data, clock)
        withContext(Dispatchers.Main) {
            model.exportTo(format, ExportSink { file.writeBytes(it) })
        }
        val result = withTimeout(15_000) { model.uiState.first { !it.isWorking && it.result != null } }
        assertEquals(DataManagementResult.EXPORT_SUCCEEDED, result.result)
        assertTrue(file.isFile && file.length() > 0)
        return file
    }

    private fun writeAndReadImage(name: String, bitmap: android.graphics.Bitmap) {
        val file = File(output, name)
        try {
            assertTrue(PlanImageExporter.writeTo(context, bitmap, Uri.fromFile(file)))
        } finally {
            bitmap.recycle()
        }
        val decoded = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        try {
            assertEquals(1080, decoded.width)
            assertTrue(decoded.height >= 720)
            val colours = mutableSetOf<Int>()
            for (y in 0 until decoded.height step 8) {
                for (x in 0 until decoded.width step 8) colours += decoded.getPixel(x, y)
            }
            assertTrue("A saved PNG must contain rendered text/cards, not a blank bitmap", colours.size > 20)
        } finally {
            decoded.recycle()
        }
    }

    private fun draft(id: String, title: String) = TaskDraft(
        id = id, displayName = title, description = "$title：验收任务", category = TaskCategory.COURSE,
        userPriority = TaskPriority.MEDIUM, estimatedDays = 2, totalDurationMinutes = 90, dueDate = null,
    )

    private fun courseKey(course: ImportedCourse) =
        "${course.title}|${course.dayOfWeek}|${course.startPeriod}|${course.endPeriod}|${course.weekPattern}"

    private fun segmentKey(segment: PlannedSegment) =
        "${segment.taskId}|${segment.date}|${segment.startMinute}|${segment.endMinute}|${segment.isLocked}|${segment.trackId}"

    private val imageTexts = PlanImageTexts(
        datePattern = "M月d日 EEEE", locale = Locale.SIMPLIFIED_CHINESE, weekTitle = "每周课表",
        emptyPlan = "暂无计划", emptyTimetable = "暂无课程", segmentCount = "%1\$d 项安排",
        blockCount = "%1\$d 个时间块", missingTask = "未命名任务",
        timetableOverview = "各周课程总览 · 请按课程周次核对，此图不筛选当前教学周",
        weekPattern = "周次：%1\$s", unrestrictedWeeks = "每周（未限定周次）",
    )
}
