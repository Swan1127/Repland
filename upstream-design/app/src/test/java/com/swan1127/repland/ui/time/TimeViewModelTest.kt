package com.swan1127.repland.ui.time

import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.DateOverrideType
import com.swan1127.repland.domain.model.ImportedCourse
import com.swan1127.repland.domain.model.TimeArrangementSnapshot
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.TimeConstraintSettings
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import com.swan1127.repland.domain.ports.SpreadsheetGridImporter
import com.swan1127.repland.domain.ports.TimeRepository
import com.swan1127.repland.domain.ports.TimetableImporter
import com.swan1127.repland.domain.ports.TimetableSource
import com.swan1127.repland.testkit.FakeTimeRepository
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.withCollected
import java.io.ByteArrayInputStream
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TimeViewModel` used to be untestable: its constructor took two concrete importers that each
 * needed an Android `Context`, and both import entry points took an `android.net.Uri` — a class
 * with no usable constructor off-device. Both are now ports, so everything below runs on a plain
 * JVM with no Robolectric.
 *
 * The import path is the reason this class is worth testing at all. It is the one place where the
 * app decides *what counts as a class the user already has*, and getting that wrong is invisible:
 * a duplicate row looks exactly like a successful import.
 *
 * Every test subscribes through [withCollected], which cancels its collector in a `finally`. That
 * is not style: the collector is a child of the test coroutine and never finishes, so a forgotten
 * `cancel()` makes `runTest` sit out its timeout instead of failing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimeViewModelTest : ViewModelTestBase() {

    private val repository = FakeTimeRepository()
    private val importer = FakeTimetableImporter()
    private val spreadsheet = FakeSpreadsheetImporter()

    private fun viewModel() = TimeViewModel(repository, importer, spreadsheet)

    /** A source whose bytes the fakes never read; its identity is what gets asserted. */
    private fun source() = TimetableSource { ByteArrayInputStream(ByteArray(0)) }

    // ---------------------------------------------------------------- fixtures

    private fun course(
        id: String = "c1",
        title: String = "高等数学",
        day: DayOfWeek = DayOfWeek.MONDAY,
        startPeriod: Int = 1,
        endPeriod: Int = 2,
        weekPattern: String? = null,
    ) = ImportedCourse(id, title, day, startPeriod, endPeriod, weekPattern)

    private fun block(
        id: String,
        title: String = "高等数学",
        day: DayOfWeek = DayOfWeek.MONDAY,
        start: Int = PERIOD_1_2_START,
        end: Int = PERIOD_1_2_END,
        weekPattern: String? = null,
    ) = WeeklyTimeBlock(
        id = id,
        title = title,
        kind = TimeBlockKind.COURSE,
        dayOfWeek = day,
        startMinute = start,
        endMinute = end,
        weekPattern = weekPattern,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )

    private fun draft(
        title: String = "新课程",
        day: DayOfWeek = DayOfWeek.TUESDAY,
        start: Int = 600,
        end: Int = 695,
        weekPattern: String? = null,
    ) = WeeklyTimeBlockDraft(
        title = title,
        kind = TimeBlockKind.COURSE,
        dayOfWeek = day,
        startMinute = start,
        endMinute = end,
        weekPattern = weekPattern,
    )

    // ---------------------------------------------------------------- the observed state

    @Test
    fun `before collection the screen is loading with nothing in it`() = runTest(dispatcher) {
        repository.blocks.value = listOf(block("b1"))

        val vm = viewModel()

        assertEquals(emptyList<WeeklyTimeBlock>(), vm.uiState.value.weeklyBlocks)
        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun `collecting reports the stored blocks and stops loading`() = runTest(dispatcher) {
        repository.blocks.value = listOf(block("b1", title = "线性代数"))
        val vm = viewModel()

        withCollected(vm.uiState) {
            assertEquals(
                listOf("线性代数"),
                vm.uiState.value.weeklyBlocks.map(WeeklyTimeBlock::title),
            )
            assertFalse(vm.uiState.value.isLoading)
        }
    }

    @Test
    fun `the semester first week and its timestamp come from the constraint settings`() =
        runTest(dispatcher) {
            val monday = LocalDate.of(2026, 3, 2)
            repository.constraintSettings.value = TimeConstraintSettings(monday, 1_700L)
            val vm = viewModel()

            withCollected(vm.uiState) {
                assertEquals(monday, vm.uiState.value.semesterFirstWeekMonday)
                assertEquals(1_700L, vm.uiState.value.timeConstraintsUpdatedAtEpochMillis)
            }
        }

    @Test
    fun `a repository that throws leaves the screen usable rather than crashing`() =
        runTest(dispatcher) {
            val vm = TimeViewModel(
                timeRepository = ThrowingTimeRepository(),
                timetableImporter = importer,
                xlsxImporter = spreadsheet,
            )

            // A blank timetable beats a screen that never finishes loading.
            withCollected(vm.uiState) {
                assertFalse(vm.uiState.value.isLoading)
                assertEquals(emptyList<WeeklyTimeBlock>(), vm.uiState.value.weeklyBlocks)
            }
        }

    // ---------------------------------------------------------------- the write guards

    @Test
    fun `an invalid weekly block never reaches the repository`() = runTest(dispatcher) {
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.saveWeeklyBlock(draft(title = "   "))
            advanceUntilIdle()

            assertEquals(emptyList<WeeklyTimeBlockDraft>(), repository.savedBlocks)
        }
    }

    @Test
    fun `a block ending before it starts never reaches the repository`() = runTest(dispatcher) {
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.saveWeeklyBlock(draft(start = 600, end = 500))
            advanceUntilIdle()

            assertEquals(emptyList<WeeklyTimeBlockDraft>(), repository.savedBlocks)
        }
    }

    @Test
    fun `a valid weekly block is saved`() = runTest(dispatcher) {
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.saveWeeklyBlock(draft(title = "英语"))
            advanceUntilIdle()

            assertEquals(listOf("英语"), repository.savedBlocks.map(WeeklyTimeBlockDraft::title))
        }
    }

    @Test
    fun `an invalid date override never reaches the repository`() = runTest(dispatcher) {
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.saveDateOverride(
                DateOverrideDraft(
                    title = "校运会",
                    type = DateOverrideType.BLOCKED,
                    date = LocalDate.of(2026, 3, 2),
                    startMinute = 600,
                    endMinute = 600,
                ),
            )
            advanceUntilIdle()

            assertEquals(emptyList<DateOverrideDraft>(), repository.savedOverrides)
        }
    }

    @Test
    fun `deleting hands back the job so a caller can wait for the write`() = runTest(dispatcher) {
        repository.blocks.value = listOf(block("b1"), block("b2"))
        val vm = viewModel()

        withCollected(vm.uiState) {
            // The undo stack's whole correctness rests on being able to await this.
            vm.deleteWeeklyBlock("b1").join()

            assertEquals(listOf("b1"), repository.deletedBlockIds)
            assertEquals(listOf("b2"), repository.blocks.value.map(WeeklyTimeBlock::id))
        }
    }

    // ---------------------------------------------------------------- replacing the timetable

    @Test
    fun `replacing deletes every existing block before inserting the new set`() =
        runTest(dispatcher) {
            repository.blocks.value = listOf(block("b1"), block("b2"))
            val vm = viewModel()

            withCollected(vm.uiState) {
                vm.replaceTimetable(listOf(draft(title = "新课"))).join()

                assertEquals(
                    listOf("deleteBlock(b1)", "deleteBlock(b2)", "saveBlocks(1)"),
                    repository.events,
                )
            }
        }

    @Test
    fun `replacing with nothing still clears what was there`() = runTest(dispatcher) {
        repository.blocks.value = listOf(block("b1"))
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.replaceTimetable(emptyList()).join()

            assertEquals(listOf("b1"), repository.deletedBlockIds)
            assertEquals(emptyList<WeeklyTimeBlock>(), repository.blocks.value)
        }
    }

    // ---------------------------------------------------------------- the undo seam

    @Test
    fun `the current arrangement is read from the repository, not from the observed state`() =
        runTest(dispatcher) {
            val vm = viewModel()

            withCollected(vm.uiState) {
                // Written after the last emission, so a peek at uiState would miss it.
                repository.blocks.value = listOf(block("b1", title = "刚写进去的"))

                val snapshot = vm.currentArrangement()

                assertEquals(
                    listOf("刚写进去的"),
                    snapshot.blocks.map(WeeklyTimeBlockDraft::title),
                )
            }
        }

    @Test
    fun `restoring puts a snapshot back through the repository`() = runTest(dispatcher) {
        val vm = viewModel()

        withCollected(vm.uiState) {
            val snapshot = TimeArrangementSnapshot(
                blocks = listOf(draft(title = "恢复的")),
                overrides = emptyList(),
            )
            vm.restoreArrangement(snapshot)

            assertEquals(listOf(snapshot), repository.restoredSnapshots)
        }
    }

    // ---------------------------------------------------------------- import: the happy path

    @Test
    fun `a recognized course becomes a block on the period clock`() = runTest(dispatcher) {
        importer.courses = listOf(course(title = "高等数学"))
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetable(source())
            advanceUntilIdle()

            val saved = repository.savedBlocks.single()
            assertEquals("高等数学", saved.title)
            assertEquals(DayOfWeek.MONDAY, saved.dayOfWeek)
            assertEquals(PERIOD_1_2_START, saved.startMinute)
            assertEquals(PERIOD_1_2_END, saved.endMinute)
            assertEquals(
                TimetableImportState.Done(added = 1, skipped = 0),
                vm.uiState.value.timetableImport,
            )
        }
    }

    @Test
    fun `the source the caller passed is the one the importer was given`() = runTest(dispatcher) {
        val vm = viewModel()

        withCollected(vm.uiState) {
            val picked = source()
            vm.readTimetable(picked)
            advanceUntilIdle()

            assertSame(picked, importer.lastSource)
        }
    }

    @Test
    fun `a course the clock cannot place is dropped and nothing is invented`() =
        runTest(dispatcher) {
            // Period 99 is not something either importer can produce: `TimetableGridParser` and the
            // PDF stripper both reject anything outside 1..12, and `ClassPeriodClock` covers exactly
            // 1..12. So this branch is unreachable from a real file, and the test only pins what it
            // does *not* do — invent a block, or report a course it silently discarded. If it ever
            // did become reachable, `Done(0, 0)` ("imported nothing, skipped nothing") would be the
            // wrong thing to show, and it would need its own message.
            importer.courses = listOf(course(startPeriod = 99, endPeriod = 99))
            val vm = viewModel()

            withCollected(vm.uiState) {
                vm.readTimetable(source())
                advanceUntilIdle()

                assertEquals(emptyList<WeeklyTimeBlockDraft>(), repository.savedBlocks)
                assertEquals(
                    TimetableImportState.Done(added = 0, skipped = 0),
                    vm.uiState.value.timetableImport,
                )
            }
        }

    @Test
    fun `a course that ends before it starts is dropped`() = runTest(dispatcher) {
        importer.courses = listOf(course(startPeriod = 5, endPeriod = 1))
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetable(source())
            advanceUntilIdle()

            assertEquals(emptyList<WeeklyTimeBlockDraft>(), repository.savedBlocks)
        }
    }

    // ---------------------------------------------------------------- import: the failure paths

    @Test
    fun `an unrecognized file says so instead of reporting success`() = runTest(dispatcher) {
        importer.courses = emptyList()
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetable(source())
            advanceUntilIdle()

            val state = vm.uiState.value.timetableImport
            assertTrue(state is TimetableImportState.Failed)
            assertTrue((state as TimetableImportState.Failed).message.contains("PDF"))
        }
    }

    @Test
    fun `a parser failure is reported with its own message`() = runTest(dispatcher) {
        importer.failWith = IllegalStateException("课表页数过多")
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetable(source())
            advanceUntilIdle()

            assertEquals(
                TimetableImportState.Failed("课表页数过多"),
                vm.uiState.value.timetableImport,
            )
        }
    }

    @Test
    fun `a parser failure with no message still gets wording the user can act on`() =
        runTest(dispatcher) {
            importer.failWith = IllegalStateException()
            val vm = viewModel()

            withCollected(vm.uiState) {
                vm.readTimetable(source())
                advanceUntilIdle()

                val state = vm.uiState.value.timetableImport as TimetableImportState.Failed
                assertTrue(state.message.isNotBlank())
            }
        }

    @Test
    fun `reading is reported before the work, not after`() = runTest(dispatcher) {
        importer.courses = listOf(course())
        importer.gate = CompletableDeferred()
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetable(source())
            advanceUntilIdle()

            assertEquals(TimetableImportState.Reading, vm.uiState.value.timetableImport)

            importer.gate!!.complete(Unit)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.timetableImport is TimetableImportState.Done)
        }
    }

    @Test
    fun `reading is held until the write has landed, not dropped to idle in between`() =
        runTest(dispatcher) {
            importer.courses = listOf(course())
            repository.saveBlocksGate = CompletableDeferred()
            val vm = viewModel()

            withCollected(vm.uiState) {
                vm.readTimetable(source())
                advanceUntilIdle()

                // The parse is finished but the insert is still in flight. Reporting Idle here
                // would make the screen flicker "reading…" and then say nothing at all.
                assertEquals(TimetableImportState.Reading, vm.uiState.value.timetableImport)

                repository.saveBlocksGate!!.complete(Unit)
                advanceUntilIdle()
                assertEquals(
                    TimetableImportState.Done(added = 1, skipped = 0),
                    vm.uiState.value.timetableImport,
                )
            }
        }

    @Test
    fun `clearing the import returns to idle`() = runTest(dispatcher) {
        importer.courses = listOf(course())
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetable(source())
            advanceUntilIdle()
            vm.clearTimetableImport()
            advanceUntilIdle()

            assertEquals(TimetableImportState.Idle, vm.uiState.value.timetableImport)
        }
    }

    // ------------------------------------------------------------ import: what counts as a duplicate

    @Test
    fun `a file that repeats a row within itself stores it once`() = runTest(dispatcher) {
        importer.courses = listOf(course(id = "a"), course(id = "b"))
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetable(source())
            advanceUntilIdle()

            assertEquals(1, repository.savedBlocks.size)
            // The file's own repeat is not "already in the timetable" — nothing was there.
            assertEquals(
                TimetableImportState.Done(added = 1, skipped = 0),
                vm.uiState.value.timetableImport,
            )
        }
    }

    @Test
    fun `a row already in the timetable is skipped and reported`() = runTest(dispatcher) {
        repository.blocks.value = listOf(block("b1"))
        importer.courses = listOf(course())
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetable(source())
            advanceUntilIdle()

            assertEquals(emptyList<WeeklyTimeBlockDraft>(), repository.savedBlocks)
            assertEquals(
                TimetableImportState.Done(added = 0, skipped = 1),
                vm.uiState.value.timetableImport,
            )
        }
    }

    @Test
    fun `a row already there is still recognized when the parser pads the title`() =
        runTest(dispatcher) {
            // The writer trims on the way in, so the stored row reads "高等数学". A parser handing
            // back "高等数学 " describes the same class: the user must not get a second row for it,
            // and the import must not claim it added something.
            repository.blocks.value = listOf(block("b1", title = "高等数学"))
            importer.courses = listOf(course(title = "高等数学 "))
            val vm = viewModel()

            withCollected(vm.uiState) {
                vm.readTimetable(source())
                advanceUntilIdle()

                assertEquals(
                    TimetableImportState.Done(added = 0, skipped = 1),
                    vm.uiState.value.timetableImport,
                )
                assertEquals(emptyList<WeeklyTimeBlockDraft>(), repository.savedBlocks)
            }
        }

    @Test
    fun `a blank week pattern in the file matches a stored row with none`() = runTest(dispatcher) {
        // The writer folds a blank pattern to null, and the within-file dedup already treats the
        // two as one row. The "already there" check has to use the same rule, or the same course
        // is imported a second time.
        repository.blocks.value = listOf(block("b1", weekPattern = null))
        importer.courses = listOf(course(weekPattern = "  "))
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetable(source())
            advanceUntilIdle()

            assertEquals(
                TimetableImportState.Done(added = 0, skipped = 1),
                vm.uiState.value.timetableImport,
            )
        }
    }

    @Test
    fun `a genuinely different week pattern is a different row`() = runTest(dispatcher) {
        repository.blocks.value = listOf(block("b1", weekPattern = "1-8周"))
        importer.courses = listOf(course(weekPattern = "9-16周"))
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetable(source())
            advanceUntilIdle()

            // Same title, same slot, different weeks: two real rows, not a duplicate.
            assertEquals(
                TimetableImportState.Done(added = 1, skipped = 0),
                vm.uiState.value.timetableImport,
            )
        }
    }

    @Test
    fun `a course on a different day is not a duplicate`() = runTest(dispatcher) {
        repository.blocks.value = listOf(block("b1", day = DayOfWeek.MONDAY))
        importer.courses = listOf(course(day = DayOfWeek.TUESDAY))
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetable(source())
            advanceUntilIdle()

            assertEquals(
                TimetableImportState.Done(added = 1, skipped = 0),
                vm.uiState.value.timetableImport,
            )
        }
    }

    @Test
    fun `added and skipped always account for every recognized row`() = runTest(dispatcher) {
        repository.blocks.value = listOf(block("b1"))
        importer.courses = listOf(
            course(id = "a"), // already there
            course(id = "b", day = DayOfWeek.TUESDAY), // new
            course(id = "c", day = DayOfWeek.WEDNESDAY), // new
        )
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetable(source())
            advanceUntilIdle()

            assertEquals(
                TimetableImportState.Done(added = 2, skipped = 1),
                vm.uiState.value.timetableImport,
            )
        }
    }

    // ---------------------------------------------------------------- import: the clipboard

    @Test
    fun `clipboard text is recognized through the same grid rules as a file`() =
        runTest(dispatcher) {
            val vm = viewModel()

            withCollected(vm.uiState) {
                vm.readTimetableFromClipboard(
                    listOf(
                        "节次\t星期一\t星期二\t星期三",
                        "1-2\t高等数学(1-16周)\t\t英语",
                        "3-4\t\t物理\t",
                    ).joinToString("\n"),
                )
                advanceUntilIdle()

                assertEquals(
                    listOf("高等数学", "英语", "物理"),
                    repository.savedBlocks.map(WeeklyTimeBlockDraft::title),
                )
                assertEquals(
                    listOf("1-16周", null, null),
                    repository.savedBlocks.map(WeeklyTimeBlockDraft::weekPattern),
                )
            }
        }

    @Test
    fun `clipboard text with no recognizable grid is reported as failed`() = runTest(dispatcher) {
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetableFromClipboard("这不是课表\n只是一些文字")
            advanceUntilIdle()

            assertTrue(vm.uiState.value.timetableImport is TimetableImportState.Failed)
            assertEquals(emptyList<WeeklyTimeBlockDraft>(), repository.savedBlocks)
        }
    }

    @Test
    fun `an empty clipboard flows through the same failure state as a bad file`() =
        runTest(dispatcher) {
            val vm = viewModel()

            withCollected(vm.uiState) {
                vm.readTimetableFromClipboard("")
                advanceUntilIdle()

                assertTrue(vm.uiState.value.timetableImport is TimetableImportState.Failed)
            }
        }

    @Test
    fun `the clipboard path never touches either file importer`() = runTest(dispatcher) {
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readTimetableFromClipboard("节次\t星期一\t星期二\t星期三\n1-2\t数学\t\t")
            advanceUntilIdle()

            assertEquals(0, importer.callCount)
            assertEquals(0, spreadsheet.callCount)
        }
    }

    // ---------------------------------------------------------------- import: the spreadsheet

    @Test
    fun `a spreadsheet grid goes through the same recognition as the clipboard`() =
        runTest(dispatcher) {
            spreadsheet.grid = listOf(
                listOf("节次", "星期一", "星期二", "星期三"),
                listOf("1-2", "高等数学", "", ""),
            )
            val vm = viewModel()

            withCollected(vm.uiState) {
                val picked = source()
                vm.readSpreadsheet(picked)
                advanceUntilIdle()

                assertSame(picked, spreadsheet.lastSource)
                assertEquals(
                    listOf("高等数学"),
                    repository.savedBlocks.map(WeeklyTimeBlockDraft::title),
                )
            }
        }

    @Test
    fun `a spreadsheet with no weekday header says what is wrong`() = runTest(dispatcher) {
        spreadsheet.grid = listOf(listOf("随便", "一些", "单元格"))
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.readSpreadsheet(source())
            advanceUntilIdle()

            val state = vm.uiState.value.timetableImport as TimetableImportState.Failed
            assertTrue(state.message.contains("星期"))
        }
    }

    // ---------------------------------------------------------------- the semester first week

    @Test
    fun `clearing the semester first week is passed through as null`() = runTest(dispatcher) {
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.saveSemesterFirstWeekMonday(LocalDate.of(2026, 3, 2))
            vm.saveSemesterFirstWeekMonday(null)
            advanceUntilIdle()

            assertEquals(listOf(LocalDate.of(2026, 3, 2), null), repository.savedFirstWeekMondays)
        }
    }

    // ---------------------------------------------------------------- calendar import

    /** A `.ics` document with CRLF endings, as the spec requires. */
    private fun calendarSource(vararg body: String): TimetableSource {
        val text = (listOf("BEGIN:VCALENDAR", "VERSION:2.0") + body + "END:VCALENDAR")
            .joinToString("\r\n") + "\r\n"
        return TimetableSource { ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun calendarEvent(
        summary: String = "例会",
        uid: String = "e1",
        start: String = "20260919T100000",
        end: String = "20260919T110000",
        extra: List<String> = emptyList(),
    ): String = (
        listOf("BEGIN:VEVENT", "UID:$uid", "DTSTART:$start", "DTEND:$end", "SUMMARY:$summary") +
            extra + "END:VEVENT"
        ).joinToString("\r\n")

    private fun calendarDone(vm: TimeViewModel): TimetableImportState.CalendarDone =
        vm.uiState.value.timetableImport as TimetableImportState.CalendarDone

    @Test
    fun `a calendar import turns events into occupied time`() = runTest(dispatcher) {
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.importCalendar(
                calendarSource(
                    calendarEvent(summary = "看牙医"),
                    calendarEvent(
                        summary = "组会",
                        uid = "e2",
                        extra = listOf("RRULE:FREQ=WEEKLY;BYDAY=MO"),
                    ),
                ),
                fallbackTitle = "（无标题安排）",
            )
            advanceUntilIdle()

            val state = calendarDone(vm)
            assertEquals(1, state.overrides)
            assertEquals(1, state.blocks)
            assertEquals(2, state.added)
            assertEquals(0, state.duplicates)
            assertEquals(0, state.ignored)
            assertEquals("看牙医", repository.savedOverrides.single().title)
            assertEquals(DayOfWeek.MONDAY, repository.savedBlocks.single().dayOfWeek)
        }
    }

    @Test
    fun `a calendar import reads the repository rather than the observed screen state`() =
        runTest(dispatcher) {
            // The observed flow stays empty while the direct read holds the row, which is exactly
            // the difference between `uiState.value.weeklyBlocks` and `currentWeeklyBlocks()`. Using
            // the screen state would find "nothing there" and write a second copy of a row the
            // database already has — and the duplicate would be invisible, because it looks like a
            // successful import.
            val existing = DateOverride(
                id = "o1",
                title = "例会",
                type = DateOverrideType.BLOCKED,
                date = LocalDate.of(2026, 9, 19),
                startMinute = 10 * 60,
                endMinute = 11 * 60,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
            )
            val lagging = LaggingTimeRepository(listOf(existing))
            val vm = TimeViewModel(lagging, importer, spreadsheet)

            withCollected(vm.uiState) {
                vm.importCalendar(calendarSource(calendarEvent()), fallbackTitle = "（无标题安排）")
                advanceUntilIdle()

                assertEquals(1, calendarDone(vm).duplicates)
                assertTrue(lagging.savedOverrides.isEmpty())
            }
        }

    @Test
    fun `an event already in the timetable is a duplicate and nothing is written`() =
        runTest(dispatcher) {
            repository.overrides.value = listOf(
                DateOverride(
                    id = "o1",
                    title = "例会",
                    type = DateOverrideType.BLOCKED,
                    date = LocalDate.of(2026, 9, 19),
                    startMinute = 10 * 60,
                    endMinute = 11 * 60,
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                ),
            )
            val vm = viewModel()

            withCollected(vm.uiState) {
                vm.importCalendar(calendarSource(calendarEvent()), fallbackTitle = "（无标题安排）")
                advanceUntilIdle()

                assertEquals(1, calendarDone(vm).duplicates)
                assertEquals(0, calendarDone(vm).added)
                assertTrue(repository.savedOverrides.isEmpty())
            }
        }

    @Test
    fun `a file that is not a calendar fails with a format message`() = runTest(dispatcher) {
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.importCalendar(
                TimetableSource { ByteArrayInputStream("{\"not\":\"a calendar\"}".toByteArray()) },
                fallbackTitle = "（无标题安排）",
            )
            advanceUntilIdle()

            val state = vm.uiState.value.timetableImport as TimetableImportState.Failed
            assertTrue(state.message.contains("VCALENDAR"))
            assertTrue(repository.savedBlocks.isEmpty())
        }
    }

    @Test
    fun `an unreadable stream fails instead of reporting a successful import`() =
        runTest(dispatcher) {
            val vm = viewModel()

            withCollected(vm.uiState) {
                vm.importCalendar(TimetableSource { null }, fallbackTitle = "（无标题安排）")
                advanceUntilIdle()

                assertTrue(vm.uiState.value.timetableImport is TimetableImportState.Failed)
            }
        }

    @Test
    fun `an empty calendar fails rather than reporting a successful import`() =
        runTest(dispatcher) {
            val vm = viewModel()

            withCollected(vm.uiState) {
                vm.importCalendar(calendarSource(), fallbackTitle = "（无标题安排）")
                advanceUntilIdle()

                assertTrue(vm.uiState.value.timetableImport is TimetableImportState.Failed)
            }
        }

    @Test
    fun `the fallback title is used for an event with no summary`() = runTest(dispatcher) {
        val vm = viewModel()

        withCollected(vm.uiState) {
            vm.importCalendar(calendarSource(calendarEvent(summary = "")), fallbackTitle = "（无标题安排）")
            advanceUntilIdle()

            assertEquals("（无标题安排）", repository.savedOverrides.single().title)
        }
    }

    @Test
    fun `an event from this app's own export is ignored rather than blocking time`() =
        runTest(dispatcher) {
            val vm = viewModel()

            withCollected(vm.uiState) {
                vm.importCalendar(
                    calendarSource(calendarEvent(uid = "repland-seg-1@local")),
                    fallbackTitle = "（无标题安排）",
                )
                advanceUntilIdle()

                // Export → import must not turn the user's own plan into outside commitments.
                assertEquals(1, calendarDone(vm).ignored)
                assertTrue(repository.savedOverrides.isEmpty())
            }
        }

    @Test
    fun `a weekly event goes in as one batch and its overrides one at a time`() =
        runTest(dispatcher) {
            val vm = viewModel()

            withCollected(vm.uiState) {
                vm.importCalendar(
                    calendarSource(
                        calendarEvent(summary = "组会", extra = listOf("RRULE:FREQ=WEEKLY;BYDAY=MO")),
                        calendarEvent(summary = "看牙医", uid = "e2"),
                    ),
                    fallbackTitle = "（无标题安排）",
                )
                advanceUntilIdle()

                assertEquals(1, repository.savedBlockBatches.size)
                assertEquals(1, repository.savedOverrides.size)
            }
        }

    @Test
    fun `pdf reimport reads committed rows even without a ui subscriber`() = runTest(dispatcher) {
        importer.courses = listOf(course())
        val vm = viewModel()

        vm.readTimetable(source()).join()
        vm.readTimetable(source()).join()

        assertEquals(1, repository.blocks.value.size)
        assertEquals(1, repository.savedBlocks.size)
        withCollected(vm.uiState) {
            assertEquals(TimetableImportState.Done(added = 0, skipped = 1), vm.uiState.value.timetableImport)
        }
    }

    @Test
    fun `a failed timetable save is surfaced without leaving the import reading forever`() = runTest(dispatcher) {
        importer.courses = listOf(course())
        val failing = object : FakeTimeRepository() {
            override suspend fun saveWeeklyBlocks(drafts: List<WeeklyTimeBlockDraft>) {
                error("storage full")
            }
        }
        val vm = TimeViewModel(failing, importer, spreadsheet)

        vm.readTimetable(source()).join()

        withCollected(vm.uiState) {
            assertEquals(TimetableImportState.Failed("storage full"), vm.uiState.value.timetableImport)
            assertTrue(failing.blocks.value.isEmpty())
        }
    }

    @Test
    fun `a local reset cancels an in-flight pdf parse before it can write courses back`() = runTest(dispatcher) {
        importer.courses = listOf(course())
        importer.gate = CompletableDeferred()
        val vm = viewModel()
        val pending = vm.readTimetable(source())
        advanceUntilIdle()

        vm.cancelPendingMutations()
        advanceUntilIdle()
        importer.gate!!.complete(Unit)
        advanceUntilIdle()

        assertTrue(pending.isCancelled)
        assertTrue(repository.blocks.value.isEmpty())
        assertTrue(repository.savedBlocks.isEmpty())
    }

    private companion object {
        /** Periods 1..2 are 08:00–09:35 on the default class-period clock. */
        const val PERIOD_1_2_START = 8 * 60
        const val PERIOD_1_2_END = 9 * 60 + 35
    }
}

/** Records what it was asked for, so a test can assert the source was passed through untouched. */
private class FakeTimetableImporter : TimetableImporter {
    var courses: List<ImportedCourse> = emptyList()
    var failWith: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null
    var callCount = 0
    var lastSource: TimetableSource? = null

    override suspend fun parse(source: TimetableSource): List<ImportedCourse> {
        callCount += 1
        lastSource = source
        gate?.await()
        failWith?.let { throw it }
        return courses
    }
}

private class FakeSpreadsheetImporter : SpreadsheetGridImporter {
    var grid: List<List<String>> = emptyList()
    var failWith: Throwable? = null
    var callCount = 0
    var lastSource: TimetableSource? = null

    override suspend fun parse(source: TimetableSource): List<List<String>> {
        callCount += 1
        lastSource = source
        failWith?.let { throw it }
        return grid
    }
}

/**
 * A repository whose *observed* overrides stay empty while its *direct read* holds a row.
 *
 * That split is the whole point: `uiState.weeklyBlocks` is built from the observed flows, so a test
 * that uses one source for both cannot tell `uiState.value` apart from `currentDateOverrides()`. A
 * lagging fake makes the two answers disagree, which is the only way to pin the choice.
 */
private class LaggingTimeRepository(
    private val storedOverrides: List<DateOverride>,
) : FakeTimeRepository() {

    override fun observeDateOverrides(): Flow<List<DateOverride>> = MutableStateFlow(emptyList())

    override suspend fun currentDateOverrides(): List<DateOverride> = storedOverrides
}

/** A repository whose very first observation throws, to reach the `catch` arm. */
private class ThrowingTimeRepository : TimeRepository {
    override fun observeWeeklyBlocks(): Flow<List<WeeklyTimeBlock>> =
        flow { error("database unavailable") }

    override fun observeDateOverrides(): Flow<List<DateOverride>> =
        flow { error("database unavailable") }

    override fun observeTimeConstraintSettings(): Flow<TimeConstraintSettings> =
        flow { error("database unavailable") }

    override suspend fun currentWeeklyBlocks(): List<WeeklyTimeBlock> = emptyList()

    override suspend fun currentDateOverrides(): List<DateOverride> = emptyList()

    override suspend fun restoreArrangement(snapshot: TimeArrangementSnapshot) = Unit

    override suspend fun saveWeeklyBlock(draft: WeeklyTimeBlockDraft) = Unit

    override suspend fun saveWeeklyBlocks(drafts: List<WeeklyTimeBlockDraft>) = Unit

    override suspend fun deleteWeeklyBlock(id: String) = Unit

    override suspend fun saveDateOverride(draft: DateOverrideDraft) = Unit

    override suspend fun deleteDateOverride(id: String) = Unit

    override suspend fun saveSemesterFirstWeekMonday(date: LocalDate?) = Unit
}
