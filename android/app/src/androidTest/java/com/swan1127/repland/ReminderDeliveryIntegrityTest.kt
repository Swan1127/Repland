package com.swan1127.repland

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.reminders.LocalReminderReceiver
import com.swan1127.repland.reminders.LocalReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** OS broadcast -> real receiver -> OS notification; this is not an alarm timing test. */
class ReminderDeliveryIntegrityTest {
    @Test fun delivered_reminder_does_not_start_or_complete_and_disabled_delivery_is_ignored() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<ReplandApplication>()
        check(context.packageName == "com.swan1127.repland.qa")
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = context.appContainer
        val preferences = app.reminderSettingsRepository.observe().first()
        val id = "qa-notification-${UUID.randomUUID()}"
        val day = LocalDate.now().plusDays(4)
        val tasks = app.taskRepository.observeTasks().first()
        val entries = ScheduleTimeline.entries(day, app.timeRepository.observeWeeklyBlocks().first(),
            app.timeRepository.observeDateOverrides().first(), app.planRepository.observeCurrentPlan().first()?.segments.orEmpty(),
            tasks, app.timeRepository.observeTimeConstraintSettings().first().semesterFirstWeekMonday)
        val start = (0..22).map { it * 60 }.first { minute -> entries.none { minute < it.endMinute && minute + 30 > it.startMinute } }
        app.taskRepository.save(TaskDraft(id = id, displayName = "QA reminder only", totalDurationMinutes = 30))
        app.planRepository.placeTask(id, day, start, start + 30, "focus")
        val plan = app.planRepository.observeCurrentPlan().first()!!
        val segment = plan.segments.single { it.taskId == id }
        val originalTask = app.taskRepository.observeTasks().first().single { it.id == id }
        val logs = app.taskRepository.observeExecutionLogs(id).first()
        val session = app.executionSessionRepository.observeActive().first()
        val key = "${plan.id}:${segment.id}"
        val uri = Uri.Builder().scheme("repland").authority("local-reminder")
            .appendPath(ReminderKind.SEGMENT_START.name).appendPath(key).build()
        val notificationId = uri.toString().hashCode()
        val manager = context.getSystemService(NotificationManager::class.java)
        val intent = Intent(context, LocalReminderReceiver::class.java).apply {
            action = LocalReminderScheduler.ACTION_DELIVER_REMINDER
            data = uri
            putExtra(LocalReminderScheduler.EXTRA_KIND, ReminderKind.SEGMENT_START.name)
            putExtra(LocalReminderScheduler.EXTRA_PLAN_ID, plan.id)
            putExtra(LocalReminderScheduler.EXTRA_SEGMENT_ID, segment.id)
            putExtra(LocalReminderScheduler.EXTRA_TASK_ID, id)
            putExtra(LocalReminderScheduler.EXTRA_TASK_NAME, originalTask.displayName)
        }
        val delivery = PendingIntent.getBroadcast(context, 10434, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            LocalReminderScheduler.createNotificationChannel(context)
            app.reminderSettingsRepository.setEnabled(true)
            delivery.send()
            val deadline = System.currentTimeMillis() + 10_000
            while (manager.activeNotifications.none { it.id == notificationId } && System.currentTimeMillis() < deadline) Thread.sleep(25)
            assertTrue("Real OS notification must be posted", manager.activeNotifications.any { it.id == notificationId })
            assertEquals(originalTask, app.taskRepository.observeTasks().first().single { it.id == id })
            assertEquals(logs, app.taskRepository.observeExecutionLogs(id).first())
            assertEquals(session, app.executionSessionRepository.observeActive().first())
            assertEquals(plan, app.planRepository.observeCurrentPlan().first())
            manager.cancel(notificationId)
            app.reminderSettingsRepository.setEnabled(false)
            val completed = java.util.concurrent.CountDownLatch(1)
            delivery.send(context, 0, null, PendingIntent.OnFinished { _, _, _, _, _ -> completed.countDown() },
                android.os.Handler(android.os.Looper.getMainLooper()))
            assertTrue("Disabled broadcast must finish", completed.await(10, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(manager.activeNotifications.none { it.id == notificationId })
            assertEquals(originalTask, app.taskRepository.observeTasks().first().single { it.id == id })
            assertEquals(logs, app.taskRepository.observeExecutionLogs(id).first())
            assertEquals(session, app.executionSessionRepository.observeActive().first())
        } finally {
            delivery.cancel()
            manager.cancel(notificationId)
            app.reminderSettingsRepository.setEnabled(preferences.isEnabled)
            app.planRepository.observeCurrentPlan().first()?.segments?.find { it.taskId == id }?.let { app.planRepository.removePlacement(it.id) }
        }
    }
}
