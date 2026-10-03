package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class TaskPlanMembershipTest {
    private val today = LocalDate.of(2026, 10, 3)
    private val task = Task("a", "", "事项", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 45,
        null, TaskStatus.NOT_STARTED, null, null, null, 0, 0, 0)
    private fun slot(date: LocalDate) = listOf(PlannedSegment(taskId = "a", date = date, startMinute = 1200, endMinute = 1245))
    @Test fun confirmed_today_is_not_unarranged() {
        assertTrue(TaskPlanMembership.isToday(task, slot(today), today))
        assertFalse(TaskPlanMembership.isInbox(task, slot(today), today))
        assertNull(task.scheduledForDate)
    }
    @Test fun confirmed_future_is_not_today_or_inbox() {
        assertFalse(TaskPlanMembership.isToday(task, slot(today.plusDays(1)), today))
        assertFalse(TaskPlanMembership.isInbox(task, slot(today.plusDays(1)), today))
    }
    @Test fun past_only_plan_does_not_make_task_currently_arranged() {
        assertFalse(TaskPlanMembership.isToday(task, slot(today.minusDays(1)), today))
        assertTrue(TaskPlanMembership.isInbox(task, slot(today.minusDays(1)), today))
    }
    @Test fun user_dates_still_work_without_a_confirmed_plan() {
        assertTrue(TaskPlanMembership.isToday(task.copy(dueDate = today), emptyList(), today))
        assertTrue(TaskPlanMembership.isToday(task.copy(scheduledForDate = today), emptyList(), today))
        assertFalse(TaskPlanMembership.isInbox(task.copy(scheduledForDate = today.plusDays(1)), emptyList(), today))
    }
}
