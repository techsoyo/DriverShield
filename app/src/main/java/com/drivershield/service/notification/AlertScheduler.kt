package com.drivershield.service.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Programa y cancela alarmas de límite legal usando [AlarmManager.setAlarmClock].
 *
 * ## Por qué AlarmManager y no WorkManager o CoroutineDelay
 * Android impone restricciones agresivas al background en modo Doze (API 23+):
 * - Los `Handler.postDelayed` y `coroutine delay` se detienen.
 * - WorkManager con `PeriodicWork` tiene una ventana mínima de 15 min.
 *
 * `setAlarmClock` es la **única API que Doze Mode no puede diferir**: el sistema
 * garantiza la entrega exacta mostrando el icono de alarma en la barra de estado.
 * Esto es crítico para cumplir el art. 10 del Reglamento (CE) 561/2006 sobre tiempos
 * de conducción y descanso, donde un retraso de minutos en la notificación puede
 * suponer una infracción grave.
 *
 * ## Línea de tiempo de alertas por turno
 * ```
 * 0h ----[4h ALERTA]----[6h ALERTA]----[8h LÍMITE DIARIO]
 * Semana: --------[38h AVISO]-----------[40h LÍMITE SEMANAL]
 * ```
 * Las alarmas se recalculan en cada inicio/reanudación de turno, descontando el trabajo
 * ya acumulado (`baseWorkMs`).
 *
 * ## Cancelación segura
 * [cancelAll] usa `FLAG_NO_CREATE`: si la alarma no existe no lanza excepción,
 * es idempotente y segura para llamar desde cualquier estado del servicio.
 */
class AlertScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleShiftAlerts(baseWorkMs: Long, baseWeeklyMs: Long, startEpoch: Long) {
        val limits = listOf(
            Triple(ACTION_ALERT_4H, 4L * 60 * 60 * 1000, 404),
            Triple(ACTION_ALERT_6H, 6L * 60 * 60 * 1000, 406),
            Triple(ACTION_ALERT_8H, 8L * 60 * 60 * 1000, 408)
        )

        for ((action, thresholdMs, reqCode) in limits) {
            if (baseWorkMs < thresholdMs) {
                val offset = thresholdMs - baseWorkMs
                val triggerAtMillis = startEpoch + offset
                scheduleAlarmClock(reqCode, triggerAtMillis, action)
            }
        }

        val weeklyLimits = listOf(
            Triple(ACTION_ALERT_38H, 38L * 60 * 60 * 1000, 438),
            Triple(ACTION_ALERT_40H, 40L * 60 * 60 * 1000, 440)
        )

        for ((action, thresholdMs, reqCode) in weeklyLimits) {
            val totalProjectedWeekly = baseWeeklyMs + baseWorkMs
            if (totalProjectedWeekly < thresholdMs) {
                val offset = thresholdMs - totalProjectedWeekly
                val triggerAtMillis = startEpoch + offset
                scheduleAlarmClock(reqCode, triggerAtMillis, action)
            }
        }
    }

    private fun scheduleAlarmClock(requestCode: Int, triggerAtMillis: Long, action: String) {
        val intent = Intent(context, AlertReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
        alarmManager.setAlarmClock(clockInfo, pendingIntent)
        Log.d("AlertScheduler", "Programada alarma legal $action en $triggerAtMillis")
    }

    fun cancelAll() {
        val allCodes = listOf(
            ACTION_ALERT_4H to 404, 
            ACTION_ALERT_6H to 406, 
            ACTION_ALERT_8H to 408,
            ACTION_ALERT_38H to 438,
            ACTION_ALERT_40H to 440
        )
        
        for ((action, code) in allCodes) {
            val intent = Intent(context, AlertReceiver::class.java).apply {
                this.action = action
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                Log.d("AlertScheduler", "Alarma $action cancelada.")
            }
        }
    }

    companion object {
        const val ACTION_ALERT_4H = "com.drivershield.ACTION_ALERT_4H"
        const val ACTION_ALERT_6H = "com.drivershield.ACTION_ALERT_6H"
        const val ACTION_ALERT_8H = "com.drivershield.ACTION_ALERT_8H"
        const val ACTION_ALERT_38H = "com.drivershield.ACTION_ALERT_38H"
        const val ACTION_ALERT_40H = "com.drivershield.ACTION_ALERT_40H"
    }
}
