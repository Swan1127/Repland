package com.swan1127.repland.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.data.room.ReplandDatabase
import com.swan1127.repland.data.room.RoomTimeRepository
import com.swan1127.repland.data.room.RoomArrangementCommitRepository
import com.swan1127.repland.domain.model.ClassPeriodClock
import com.swan1127.repland.domain.model.CourseWeekPattern
import com.swan1127.repland.domain.model.ImportedCourse
import com.swan1127.repland.domain.model.PlanGenerator
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskPlacement
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import com.swan1127.repland.domain.ports.TimetableSource
import com.swan1127.repland.ui.time.TimeViewModel
import com.swan1127.repland.ui.time.TimetableImportState
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The private PDF fixture is test-only, ignored by Git, and never included in the application APK. */
@RunWith(AndroidJUnit4::class)
class UserTimetableImportTest {
    private lateinit var database: ReplandDatabase
    private lateinit var repository: RoomTimeRepository
    private lateinit var importer: PdfTimetableImporter
    private lateinit var testContext: Context
    private lateinit var expectedCourses: Set<String>
    private val firstMonday = LocalDate.of(2026, 8, 31) // Test setting, not inferred from the source PDF.

    @Before
    fun setUp() {
        testContext = InstrumentationRegistry.getInstrumentation().context
        val fixtures = testContext.assets.list("")!!.toSet()
        assumeTrue("Private timetable PDF and expected manifest were not supplied",
            fixtures.containsAll(setOf("user-timetable.pdf", "user-timetable.expected.txt")))
        expectedCourses = testContext.assets.open("user-timetable.expected.txt")
            .bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.toSet() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ReplandDatabase::class.java).build()
        repository = RoomTimeRepository(database)
        importer = PdfTimetableImporter(context)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun parsesBothPagesIntoThePrivatelyVerifiedCourseSlots() = runBlocking {
        val courses = importer.parse(source())
        assertEquals(expectedCourses, courses.map(::key).toSet())
        assertEquals(expectedCourses.size, courses.size)
    }

    @Test
    fun importsIntoRealRoomAndSkipsARepeatEvenBeforeTheUiHasSubscribed() = runBlocking {
        val model = TimeViewModel(repository, importer, XlsxTimetableImporter())
        model.readTimetable(source()).join()
        val firstRows = repository.currentWeeklyBlocks()
        assertEquals(expectedCourses.size, firstRows.size)
        assertEquals(expectedCourses, firstRows.map { row ->
            val span = requireNotNull(ClassPeriodClock.periodSpanOf(row.startMinute, row.endMinute))
            "${row.dayOfWeek.value}|${span.first}-${span.second}|${row.title}|${row.weekPattern}"
        }.toSet())

        model.readTimetable(source()).join()

        assertEquals(firstRows.map { it.id }.toSet(), repository.currentWeeklyBlocks().map { it.id }.toSet())
        val state = withTimeout(10_000) { model.uiState.first { !it.isLoading } }
        assertEquals(TimetableImportState.Done(added = 0, skipped = expectedCourses.size), state.timetableImport)
    }

    @Test
    fun preservesDisjointAndOddWeekPatternsAndPlannerAvoidsActiveCourses() = runBlocking {
        val courses = importer.parse(source())
        repository.saveWeeklyBlocks(courses.mapNotNull(ClassPeriodClock::toWeeklyTimeBlockDraft))
        assertEquals(expectedCourses, courses.map(::key).toSet())
        DayOfWeek.entries.forEach { day ->
            repository.saveWeeklyBlock(WeeklyTimeBlockDraft(
                title = "可用", kind = TimeBlockKind.AVAILABLE, dayOfWeek = day, startMinute = 480, endMinute = 1320,
            ))
        }
        val start = firstMonday.plusWeeks(2)
        val task = Task("work", "验证课程保护", "验证课程保护", TaskCategory.COURSE, TaskPriority.MEDIUM,
            5, 1_440, start.plusDays(10), TaskStatus.NOT_STARTED, null, null, null, 0, 0, 0)
        val blocks = repository.currentWeeklyBlocks()
        val plan = PlanGenerator.generate(
            tasks = listOf(task), weeklyBlocks = blocks, dateOverrides = emptyList(),
            semesterFirstWeekMonday = firstMonday,
            clock = Clock.fixed(start.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai")),
        )
        assertTrue(plan.segments.isNotEmpty())
        assertFalse(plan.segments.any { segment -> blocks.any { block ->
            block.kind == TimeBlockKind.COURSE && block.dayOfWeek == segment.date.dayOfWeek &&
                CourseWeekPattern.appliesOn(block.weekPattern, segment.date, firstMonday) &&
                segment.startMinute < block.endMinute && segment.endMinute > block.startMinute
        } })
    }

    @Test
    fun aRealDatabaseFailureReportsFailureAndKeepsTheExistingTimetable() = runBlocking {
        repository.saveWeeklyBlock(WeeklyTimeBlockDraft(
            title = "原有课程", kind = TimeBlockKind.COURSE, dayOfWeek = DayOfWeek.MONDAY, startMinute = 480, endMinute = 525,
        ))
        val previous = repository.currentWeeklyBlocks()
        val rejectedTitle = importer.parse(source()).first().title.replace("'", "''")
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_pdf_import BEFORE INSERT ON weekly_time_blocks " +
                "WHEN NEW.title = '$rejectedTitle' BEGIN SELECT RAISE(ABORT, 'forced timetable write failure'); END",
        )
        val model = TimeViewModel(repository, importer, XlsxTimetableImporter())

        model.readTimetable(source()).join()

        assertEquals(previous, repository.currentWeeklyBlocks())
        val state = withTimeout(10_000) { model.uiState.first { !it.isLoading } }
        assertTrue(state.timetableImport is TimetableImportState.Failed)
    }

    @Test
    fun rejectsAnEntireChatBatchThatOverlapsAnActiveCourseFromTheRealPdf() = runBlocking {
        repository.saveWeeklyBlocks(importer.parse(source()).mapNotNull(ClassPeriodClock::toWeeklyTimeBlockDraft))
        repository.saveSemesterFirstWeekMonday(firstMonday)
        val arrangements = RoomArrangementCommitRepository(database)
        val (activeDate, course) = firstActiveCourseSlot()

        val result = runCatching {
            arrangements.commit(
                listOf(arrangementDraft("free"), arrangementDraft("collision")),
                listOf(TaskPlacement("free", activeDate, 0, 30),
                    TaskPlacement("collision", activeDate, course.startMinute, course.startMinute + 30)),
            )
        }

        assertTrue(result.isFailure)
        assertTrue(database.taskDao().getAll().isEmpty())
        assertTrue(database.planDao().getAllPlansWithSegments().isEmpty())
        assertEquals(expectedCourses.size, repository.currentWeeklyBlocks().size)
    }

    @Test
    fun rejectsAStaleChatPreviewWhenCoursesAreImportedAfterItWasCreated() = runBlocking {
        val (date, course) = firstActiveCourseSlot()
        val drafts = listOf(arrangementDraft("stale-preview"))
        val previewPlacements = listOf(TaskPlacement("stale-preview", date, course.startMinute, course.startMinute + 30))
        // The preview predates the PDF import. No semester has been set, so Monday is protected conservatively.
        repository.saveWeeklyBlocks(importer.parse(source()).mapNotNull(ClassPeriodClock::toWeeklyTimeBlockDraft))

        val result = runCatching { RoomArrangementCommitRepository(database).commit(drafts, previewPlacements) }

        assertTrue(result.isFailure)
        assertTrue(database.taskDao().getAll().isEmpty())
        assertTrue(database.planDao().getAllPlansWithSegments().isEmpty())
        assertEquals(expectedCourses.size, repository.currentWeeklyBlocks().size)
    }

    private fun arrangementDraft(id: String) = TaskDraft(
        id = id, displayName = id, description = "已审核的聊天安排", category = TaskCategory.COURSE,
        userPriority = TaskPriority.MEDIUM, estimatedDays = 1, totalDurationMinutes = 30, dueDate = null,
    )

    private fun source() = TimetableSource { testContext.assets.open("user-timetable.pdf") }
    private fun key(course: ImportedCourse) = "${course.dayOfWeek.value}|${course.startPeriod}-${course.endPeriod}|${course.title}|${course.weekPattern}"

    private suspend fun firstActiveCourseSlot(): Pair<LocalDate, WeeklyTimeBlockDraft> {
        val course = requireNotNull(ClassPeriodClock.toWeeklyTimeBlockDraft(importer.parse(source()).first()))
        val date = (0L..182L).asSequence().map(firstMonday::plusDays).first {
            it.dayOfWeek == course.dayOfWeek && CourseWeekPattern.appliesOn(course.weekPattern, it, firstMonday)
        }
        return date to course
    }
}
