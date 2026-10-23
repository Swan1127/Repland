package com.swan1127.repland

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.ui.schedule.TimelineDashboard
import com.swan1127.repland.ui.schedule.TimelineEventObject
import com.swan1127.repland.ui.theme.ReplandTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineDashboardUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun empty_day_can_create_a_named_track_with_a_stable_id() {
        val tracks = androidx.compose.runtime.mutableStateOf(com.swan1127.repland.domain.model.RhythmTracks.defaults)
        rule.setContent { TimelineTestTheme { TimelineDashboard(
                    clockNow = LocalDate.now().atTime(8, 0),
            entries = emptyList(), mode = EngagementMode.GUIDED, onOpenEntry = {}, onOpenTask = {},
            persistedTracks = tracks.value, onTracksChanged = { tracks.value = it },
        ) } }
        rule.onNodeWithTag("schedule-display-options").performScrollTo().performClick()
        rule.onNodeWithTag("add-track-trigger").performScrollTo().performClick()
        rule.onNodeWithText("新轨道名称").performTextInput("语言学习")
        rule.onNodeWithTag("add-track").performClick()
        val added = tracks.value.last()
        assertEquals("语言学习", added.name)
        org.junit.Assert.assertTrue(added.id.startsWith("track-"))
        rule.onNodeWithText("课程 · 专注 · 休息 · 语言学习").assertExists()
    }

    @Test fun tapping_a_track_block_opens_detail_with_note_edit_and_task_actions() {
        val day = LocalDate.now()
        var opened = ""
        var inspected = ""
        var editRequested = ""
        rule.setContent {
            TimelineTestTheme {
                TimelineDashboard(
                    clockNow = LocalDate.now().atTime(8, 0),
                    entries = listOf(
                        TimelineEntry("course", "高数", TimelineKind.COURSE, day, 540, 600, lane = 0),
                        TimelineEntry("work", "复习高数", TimelineKind.TASK, day, 570, 630,
                            taskId = "task-one", lane = 1),
                    ),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = { inspected = it },
                    onOpenTask = { opened = it },
                    onEditEntry = { editRequested = it.id },
                    onMoveEntry = { _, _, _ -> },
                )
            }
        }
        rule.onNodeWithTag("schedule-detail-mode").performClick()
        rule.onNodeWithTag("timeline-work").performScrollTo().performClick()
        rule.onNodeWithTag("timeline-entry-detail-work", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("timeline-note-work", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("open-task-work", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals("work", inspected)
        assertEquals("task-one", opened)

        rule.onNodeWithTag("timeline-course").performScrollTo().performClick()
        rule.onNodeWithTag("timeline-note-course", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals("course", editRequested)
    }

    @Test fun timeline_detail_can_adjust_an_exact_time_without_forcing_note_entry() {
        val day = LocalDate.now()
        var savedStart = -1
        var savedEnd = -1
        rule.setContent {
            TimelineTestTheme {
                TimelineDashboard(
                    clockNow = LocalDate.now().atTime(8, 0),
                    entries = listOf(TimelineEntry("work", "复习高数", TimelineKind.TASK, day, 570, 630, taskId = "task-one")),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                    onUpdateEntryTime = { _, startMinute, endMinute, saved -> saved()
                        savedStart = startMinute
                        savedEnd = endMinute
                    },
                )
            }
        }
        rule.onNodeWithTag("schedule-detail-mode").performClick()
        rule.onNodeWithTag("timeline-work").performScrollTo().performClick()
        rule.onNodeWithTag("timeline-time-editor-work", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("timeline-start-work", useUnmergedTree = true).performTextClearance()
        rule.onNodeWithTag("timeline-start-work", useUnmergedTree = true).performTextInput("10:00")
        rule.onNodeWithTag("timeline-end-work", useUnmergedTree = true).performTextClearance()
        rule.onNodeWithTag("timeline-end-work", useUnmergedTree = true).performTextInput("11:15")
        rule.onNodeWithTag("timeline-time-save-work", useUnmergedTree = true).performClick()
        assertEquals(10 * 60, savedStart)
        assertEquals(11 * 60 + 15, savedEnd)
    }

    @Test fun canAddNamedTrackWithoutRebuildingExistingSchedule() {
        val day = LocalDate.now()
        rule.setContent {
            TimelineTestTheme {
                TimelineDashboard(
                    clockNow = LocalDate.now().atTime(8, 0),
                    entries = listOf(TimelineEntry("course", "高数", TimelineKind.COURSE, day, 540, 600)),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                    onMoveEntry = { _, _, _ -> },
                )
            }
        }
        rule.onNodeWithTag("schedule-display-options").performScrollTo().performClick()
        rule.onNodeWithTag("add-track-trigger").performScrollTo().performClick()
        rule.onNodeWithTag("track-name-input").performTextInput("运动")
        rule.onNodeWithTag("add-track").performClick()
        rule.onNodeWithText("课程 · 运动").assertExists()
        rule.onNodeWithTag("schedule-detail-mode").performScrollTo().performClick()
        rule.onNodeWithTag("timeline-course").assertExists()
            .assert(hasText("高数") and hasText("09:00", substring = true) and hasText("10:00", substring = true))
    }

    @Test fun mergedViewGroupsOverlappingEntriesWithoutDiscardingTheirTimes() {
        val day = LocalDate.now()
        rule.setContent {
            TimelineTestTheme {
                TimelineDashboard(
                    clockNow = LocalDate.now().atTime(8, 0),
                    entries = listOf(
                        TimelineEntry("course", "高数", TimelineKind.COURSE, day, 540, 630, lane = 0),
                        TimelineEntry("work", "复习", TimelineKind.TASK, day, 570, 660, taskId = "task-one", lane = 1),
                    ),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                )
            }
        }
        rule.onNodeWithTag("schedule-summary-mode").assertExists()
        rule.onNodeWithTag("priority-slot-570").onChildren().assertCountEquals(2)
        rule.onNodeWithTag("summary-540-course").assertContentDescriptionEquals("高数，课程，09:00至10:30")
        rule.onNodeWithTag("summary-630-work").assertContentDescriptionEquals("复习，未设置，09:30至11:00")
    }

    @Test fun courseCanBeCreatedWithAnExactManualTime() {
        val day = LocalDate.now()
        var createdTitle = ""
        var createdStart = -1
        rule.setContent {
            TimelineTestTheme {
                TimelineDashboard(
                    clockNow = LocalDate.now().atTime(8, 0),
                    entries = listOf(TimelineEntry("course", "高数", TimelineKind.COURSE, day, 540, 600)),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                    onCreateCourse = { request, saved -> saved()
                        createdTitle = request.title
                        createdStart = request.startMinute
                    },
                )
            }
        }
        rule.onNodeWithTag("schedule-display-options").performScrollTo().performClick()
        rule.onNodeWithTag("add-course-trigger").performScrollTo().performClick()
        rule.onNodeWithTag("course-title-input").performTextInput("数据库")
        rule.onNodeWithTag("course-duration-input").performTextClearance()
        rule.onNodeWithTag("course-duration-input").performTextInput("30")
        rule.onNodeWithText("手动设时间").performClick()
        rule.onNodeWithText("保存课程").performClick()
        assertEquals("数据库", createdTitle)
        assertEquals(8 * 60, createdStart)
    }

    @Test fun empty_day_keeps_explicit_event_and_course_entry_points() {
        val day = LocalDate.now()
        var placedId = ""
        var createdTitle = ""
        val placedEntries = androidx.compose.runtime.mutableStateOf(emptyList<TimelineEntry>())
        rule.setContent {
            TimelineTestTheme {
                TimelineDashboard(
                    clockNow = LocalDate.now().atTime(8, 0),
                    entries = placedEntries.value,
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                    scheduleDate = day,
                    eventObjects = listOf(TimelineEventObject("event-1", "跑步", 30)),
                    onPlaceEvent = { event, track, minute ->
                        placedId = event.id
                        placedEntries.value = listOf(TimelineEntry("placed", event.title, TimelineKind.TASK, day,
                            minute, minute + 30, taskId = event.id, trackId = track))
                    },
                    onCreateCourse = { request, saved -> saved(); createdTitle = request.title },
                )
            }
        }
        rule.onNodeWithTag("empty-event-library-trigger").performClick()
        rule.onNodeWithTag("event-object-event-1", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("event-place-confirm", useUnmergedTree = true).performClick()
        assertEquals("event-1", placedId)

        rule.onNodeWithTag("schedule-display-options").performScrollTo().performClick()
        rule.onNodeWithTag("add-course-trigger").performScrollTo().performClick()
        rule.onNodeWithTag("course-title-input").performTextInput("数据库")
        rule.onNodeWithText("手动设时间").performClick()
        rule.onNodeWithText("时").performTextClearance()
        rule.onNodeWithText("时").performTextInput("09")
        rule.onNodeWithText("保存课程").performClick()
        assertEquals("数据库", createdTitle)
    }

    @Test fun event_object_requires_explicit_time_and_track_confirmation() {
        val day = LocalDate.now()
        var placedId = ""
        rule.setContent {
            TimelineTestTheme {
                TimelineDashboard(
                    clockNow = LocalDate.now().atTime(8, 0),
                    entries = listOf(TimelineEntry("course", "高数", TimelineKind.COURSE, day, 540, 600)),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                    eventObjects = listOf(TimelineEventObject("event-1", "跑步", 30)),
                    onPlaceEvent = { event, _, _ -> placedId = event.id },
                    onMoveEntry = { _, _, _ -> },
                )
            }
        }
        rule.onNodeWithTag("schedule-arrange-task").performClick()
        rule.onNodeWithTag("event-object-event-1").performClick()
        assertEquals("", placedId)
        rule.onNodeWithTag("event-place-confirm").performClick()
        assertEquals("event-1", placedId)
    }

    @Test fun event_object_cannot_overlap_on_the_same_track() {
        val day = LocalDate.now()
        rule.setContent {
            TimelineTestTheme {
                TimelineDashboard(
                    clockNow = LocalDate.now().atTime(8, 0),
                    entries = listOf(TimelineEntry("course", "高数", TimelineKind.COURSE, day, 540, 600)),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                    eventObjects = listOf(TimelineEventObject("event-1", "跑步", 30)),
                    onMoveEntry = { _, _, _ -> },
                )
            }
        }
        rule.onNodeWithTag("schedule-arrange-task").performClick()
        rule.onNodeWithTag("event-object-event-1").performClick()
        rule.onNodeWithTag("event-hour-input").performTextClearance()
        rule.onNodeWithTag("event-hour-input").performTextInput("9")
        rule.onNodeWithTag("event-minute-input").performTextClearance()
        rule.onNodeWithTag("event-minute-input").performTextInput("00")
        rule.onNodeWithTag("event-place-confirm").assertIsNotEnabled()
        rule.onNodeWithText("这个时间与已有任务、课程或休息冲突，请调整时间。", substring = true).assertExists()
    }
}

@Composable
private fun TimelineTestTheme(content: @Composable () -> Unit) {
    ReplandTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content() }
    }
}
