package com.drivershield.service.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.drivershield.R
import com.drivershield.domain.util.CycleCalculator
import com.drivershield.presentation.MainActivity

class Week5ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WEEK5_REMINDER) {
            showWeek5Reminder(context)
        }
    }

    private fun showWeek5Reminder(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios de libranza",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos antes de tu semana de descanso"
        }
        manager.createNotificationChannel(channel)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context, 200, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Semana 5 se acerca")
            .setContentText("Recuerda: Mañana comienza tu semana de libranza Domingo-Lunes")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingOpen)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_WEEK5_REMINDER = "com.drivershield.ACTION_WEEK5_REMINDER"
        const val CHANNEL_ID = "week5_reminder_channel"
        const val NOTIFICATION_ID = 2001
    }
}

fun scheduleWeek5Reminder(context: Context, cycleStartEpoch: Long) {
    if (cycleStartEpoch <= 0L) return

    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val sundayBeforeWeek5 = CycleCalculator.getNextWeek5Start(cycleStartEpoch)?.minusDays(1)
        ?: return

    val triggerTime = sundayBeforeWeek5.atTime(10, 0)
        .atZone(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    if (triggerTime <= System.currentTimeMillis()) return

    val intent = Intent(context, Week5ReminderReceiver::class.java).apply {
        action = Week5ReminderReceiver.ACTION_WEEK5_REMINDER
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context, 300, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    } else {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }
}
