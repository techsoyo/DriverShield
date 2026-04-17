package com.drivershield.service.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.drivershield.R
import com.drivershield.data.local.datastore.SessionDataStore
import com.drivershield.domain.util.CycleCalculator
import com.drivershield.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class RotationReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ROTATION_REMINDER) return

        val daysUntil = intent.getLongExtra(EXTRA_DAYS_UNTIL, 1L)
        val nextType  = intent.getStringExtra(EXTRA_NEXT_TYPE) ?: "Alterna"

        showNotification(context, daysUntil, nextType)

        // Auto-reprogramar para el próximo ciclo
        CoroutineScope(Dispatchers.IO).launch {
            val dataStore     = SessionDataStore(context)
            val refEpoch      = dataStore.nextAltReference.first()
            val weeksToRotate = dataStore.weeksToRotate.first()
            val startHour     = dataStore.startHour.first()
            scheduleRotationReminder(context, refEpoch, weeksToRotate, startHour)
        }
    }

    private fun showNotification(context: Context, daysUntil: Long, nextType: String) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios de libranza",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos antes de tu cambio de ciclo de libranza"
        }
        manager.createNotificationChannel(channel)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context, REQUEST_CODE, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val daysText = if (daysUntil == 1L) "mañana" else "en $daysUntil días"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Cambio de ciclo de libranza")
            .setContentText(
                "Atención: El próximo ciclo de libranza $nextType comienza $daysText"
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpen)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_ROTATION_REMINDER = "com.drivershield.ACTION_ROTATION_REMINDER"
        const val EXTRA_DAYS_UNTIL         = "extra_days_until"
        const val EXTRA_NEXT_TYPE          = "extra_next_type"
        const val CHANNEL_ID               = "rotation_reminder_channel"
        const val NOTIFICATION_ID          = 2001
        const val REQUEST_CODE             = 300
    }
}

// ─── Función top-level de programación ───────────────────────────────────────

/**
 * Programa una alarma Doze-friendly para notificar al conductor
 * el día antes de que comience la próxima semana alterna.
 *
 * La hora de disparo se calcula como (startHour + 1) para garantizar
 * que el conductor ya esté activo y no se interrumpa su descanso.
 * Se usa setWindow (±30 min) en lugar de setExactAndAllowWhileIdle
 * porque un recordatorio con 24 h de antelación no requiere exactitud
 * al segundo y setWindow permite al OS agrupar alarmas (menor consumo).
 */
fun scheduleRotationReminder(
    context: Context,
    nextAltReferenceEpoch: Long,
    weeksToRotate: Int,
    startHour: Int
) {
    // Desactivado temporalmente por simplificación de UI.
    return

    if (nextAltReferenceEpoch <= 0L || weeksToRotate <= 0) return

    val refDate = LocalDate.ofEpochDay(nextAltReferenceEpoch / 86_400_000L)
    val today   = LocalDate.now()

    // Días desde hoy hasta el lunes de la próxima semana alterna
    val daysUntilAlternate = CycleCalculator.daysUntilNextRotation(today, refDate, weeksToRotate)
    if (daysUntilAlternate <= 0L) return

    // Disparar el día anterior (domingo antes del lunes alterna)
    val triggerDay  = today.plusDays(daysUntilAlternate - 1)
    // 1 hora después del inicio del turno del conductor → siempre hora activa
    val triggerHour = (startHour + 1).coerceIn(0, 23)
    val triggerTimeMs = triggerDay
        .atTime(triggerHour, 0)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    if (triggerTimeMs <= System.currentTimeMillis()) return

    val intent = Intent(context, RotationReminderReceiver::class.java).apply {
        action = RotationReminderReceiver.ACTION_ROTATION_REMINDER
        putExtra(RotationReminderReceiver.EXTRA_DAYS_UNTIL, 1L)
        putExtra(RotationReminderReceiver.EXTRA_NEXT_TYPE, "Alterna")
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        RotationReminderReceiver.REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val windowMs     = 30L * 60 * 1000  // ventana de 30 min — Doze-friendly

    alarmManager.setWindow(
        AlarmManager.RTC_WAKEUP,
        triggerTimeMs,
        windowMs,
        pendingIntent
    )
}
