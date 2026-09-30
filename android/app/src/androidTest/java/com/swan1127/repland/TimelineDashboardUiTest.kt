package com.swan1127.repland

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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

    @Test fun tapping_a_track_block_opens_detail_with_note_edit_and_task_actions() {
        val day = LocalDate.now()
        var opened = ""
        var inspected = ""
        var editRequested = ""
        rule.setContent {
            ReplandTheme {
                TimelineDashboard(
                    entries = listOf(
                        TimelineEntry("course", "高数", TimelineKind.COURSE, day, 540, 600, lane = 0),
                        TimelineEntry("work", "复习高数", TimelineKind.TASK, day, 570, 630,
                            taskId = "task-one", lane = 1),
                    ),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = { inspected = it },
                    onOpenTask = { opened = it },
                    onEditEntry = { editRequested = it.id },
                    onMoveEntry = { _, _ -> },
                )
            }
        }
        rule.onNodeWithText("并行时段 · 2 条轨道").assertExists()
        rule.onNodeWithTag("timeline-work").performClick()
        rule.onNodeWithTag("timeline-entry-detail-work", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("timeline-note-work", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("open-task-work", useUnmergedTree = true).performClick()
        assertEquals("work", inspected)
        assertEquals("task-one", opened)

        rule.onNodeWithTag("timeline-course").performClick()
        rule.onNodeWithTag("timeline-note-course", useUnmergedTree = true).performClick()
        assertEquals("course", editRequested)
    }

    @Test fun timeline_detail_can_adjust_an_exact_time_without_forcing_note_entry() {
        val day = LocalDate.now()
        var savedStart = -1
        var savedEnd = -1
        rule.setContent {
            ReplandTheme {
                TimelineDashboard(
                    entries = listOf(TimelineEntry("work", "复习高数", TimelineKind.TASK, day, 570, 630, taskId = "task-one")),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                    onUpdateEntryTime = { _, startMinute, endMinute ->
                        savedStart = startMinute
                        savedEnd = endMinute
                    },
                )
            }
        }
        rule.onNodeWithTag("timeline-work").performClick()
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
            ReplandTheme {
                TimelineDashboard(
                    entries = listOf(TimelineEntry("course", "高数", TimelineKind.COURSE, day, 540, 600)),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                    onMoveEntry = { _, _ -> },
                )
            }
        }
        rule.onNodeWithTag("add-track-trigger").performClick()
        rule.onNodeWithTag("track-name-input").performTextInput("运动")
        rule.onNodeWithTag("add-track").performClick()
        assertEquals(1, rule.onAllNodesWithText("运动").fetchSemanticsNodes().size)
        rule.onNodeWithText("并行时段 · 2 条轨道").assertExists()
    }

    @Test fun mergedViewGroupsOverlappingEntriesWithoutDiscardingTheirTimes() {
        val day = LocalDate.now()
        rule.setContent {
            ReplandTheme {
                TimelineDashboard(
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
        rule.onNodeWithText("合轨").performClick()
        rule.onNodeWithText("并行 2 项", substring = true).assertExists()
        rule.onNodeWithText("09:00–10:30").assertExists()
        rule.onNodeWithText("09:30–11:00").assertExists()
    }

    @Test fun courseCanBeCreatedWithAnExactManualTime() {
        val day = LocalDate.now()
        var createdTitle = ""
        var createdStart = -1
        rule.setContent {
            ReplandTheme {
                TimelineDashboard(
                    entries = listOf(TimelineEntry("course", "高数", TimelineKind.COURSE, day, 540, 600)),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                    onCreateCourse = { request ->
                        createdTitle = request.title
                        createdStart = request.startMinute
                    },
                )
            }
        }
        rule.onNodeWithTag("add-course-trigger").performClick()
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
        rule.setContent {
            ReplandTheme {
                TimelineDashboard(
                    entries = emptyList(),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                    scheduleDate = day,
                    eventObjects = listOf(TimelineEventObject("event-1", "跑步", 30)),
                    onPlaceEvent = { event, _, _ -> placedId = event.id },
                    onCreateCourse = { request -> createdTitle = request.title },
                )
            }
        }
        rule.onNodeWithTag("empty-event-library-trigger").performClick()
        rule.onNodeWithTag("event-object-event-1", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("event-place-confirm", useUnmergedTree = true).performClick()
        assertEquals("event-1", placedId)

        rule.onNodeWithTag("empty-add-course-trigger").performClick()
        rule.onNodeWithTag("course-title-input").performTextInput("数据库")
        rule.onNodeWithText("保存课程").performClick()
        assertEquals("数据库", createdTitle)
    }

    @Test fun event_object_requires_explicit_time_and_track_confirmation() {
        val day = LocalDate.now()
        var placedId = ""
        rule.setContent {
            ReplandTheme {
                TimelineDashboard(
                    entries = listOf(TimelineEntry("course", "高数", TimelineKind.COURSE, day, 540, 600)),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                    eventObjects = listOf(TimelineEventObject("event-1", "跑步", 30)),
                    onPlaceEvent = { event, _, _ -> placedId = event.id },
                    onMoveEntry = { _, _ -> },
                )
            }
        }
        rule.onNodeWithTag("event-library-trigger").performClick()
        rule.onNodeWithTag("event-object-event-1").performClick()
        assertEquals("", placedId)
        rule.onNodeWithTag("event-place-confirm").performClick()
        assertEquals("event-1", placedId)
    }

    @Test fun event_object_cannot_overlap_on_the_same_track() {
        val day = LocalDate.now()
        rule.setContent {
            ReplandTheme {
                TimelineDashboard(
                    entries = listOf(TimelineEntry("course", "高数", TimelineKind.COURSE, day, 540, 600)),
                    mode = EngagementMode.GUIDED,
                    onOpenEntry = {},
                    onOpenTask = {},
                    eventObjects = listOf(TimelineEventObject("event-1", "跑步", 30)),
                    onMoveEntry = { _, _ -> },
                )
            }
        }
        rule.onNodeWithTag("event-library-trigger").performClick()
        rule.onNodeWithTag("event-object-event-1").performClick()
        rule.onNodeWithTag("event-hour-input").performTextClearance()
        rule.onNodeWithTag("event-hour-input").performTextInput("9")
        rule.onNodeWithTag("event-minute-input").performTextClearance()
        rule.onNodeWithTag("event-minute-input").performTextInput("00")
        rule.onNodeWithTag("event-place-confirm").assertIsNotEnabled()
        rule.onNodeWithText("该轨道这个时段已有事件；选择另一条轨道即可并行。", substring = true).assertExists()
    }
}
