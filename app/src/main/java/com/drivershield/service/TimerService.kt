package com.drivershield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.drivershield.data.local.datastore.SessionDataStore
import com.drivershield.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.drivershield.service.notification.AlertScheduler
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
/**
 * Servicio en primer plano responsable de cronometrar los turnos de conducción en tiempo real.
 *
 * ## Ciclo de vida y supervivencia
 * - Se declara como `START_STICKY` para que el sistema lo reinicie si lo mata por presión de memoria.
 * - Adquiere un [PowerManager.PARTIAL_WAKE_LOCK] con TTL de 14 h (máximo turno legal) para
 *   impedir que la CPU entre en suspensión mientras el turno está activo.
 * - Antes de cualquier destrucción ([onTaskRemoved], [onDestroy]) persiste el estado en
 *   [SessionDataStore] vía `saveStateBeforeKill()`, de modo que [BootReceiver] puede
 *   restaurar el cronómetro tras un reinicio del dispositivo sin pérdida de datos.
 *
 * ## Detección de manipulación de reloj
 * Registra un [BroadcastReceiver] para [Intent.ACTION_TIME_CHANGED] y
 * [Intent.ACTION_TIMEZONE_CHANGED]. Si el conductor manipula el reloj del sistema durante
 * un turno activo, el flag `isTampered` se escribe a `true` en [SessionDataStore] y
 * posteriormente en la columna `isTampered` de `shift_sessions`. Este mecanismo constituye
 * la **evidencia de integridad** exigida para auditorías laborales.
 *
 * ## Tick unificado
 * [startUnifiedTicker] ejecuta un bucle coroutine de 1 s en [Dispatchers.IO] que actualiza
 * [TimerStateManager] (StateFlow en memoria) sin escrituras a Room en cada tick, minimizando
 * el consumo de batería. Room solo se escribe al inicio y fin de sesión.
 *
 * ## Acciones soportadas
 * | Acción                | Descripción                                     |
 * |-----------------------|-------------------------------------------------|
 * | [ACTION_START]        | Abre un turno nuevo con `sessionId` proporcionado |
 * | [ACTION_PAUSE]        | Inicia pausa de descanso                        |
 * | [ACTION_RESUME]       | Retoma el trabajo tras pausa                    |
 * | [ACTION_STOP]         | Cierra el turno y persiste en Room              |
 * | [ACTION_RECOVER_BOOT] | Restaura estado tras reinicio del dispositivo   |
 */
class TimerService : Service() {

    @Inject
    lateinit var powerManager: PowerManager

    @Inject
    lateinit var sessionDataStore: SessionDataStore

    @Inject
    lateinit var alertScheduler: AlertScheduler

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var tickerJob: Job? = null

    private val timeChangedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_CHANGED || intent?.action == Intent.ACTION_TIMEZONE_CHANGED) {
                serviceScope.launch {
                    sessionDataStore.setSessionTampered(true)
                    android.util.Log.w("TimerService", "System time tampered! Flagging active session.")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(timeChangedReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START -> {
                val sessionId = intent?.getLongExtra(EXTRA_SESSION_ID, 0L) ?: 0L
                startShift(sessionId)
            }
            ACTION_PAUSE -> pauseShift()
            ACTION_RESUME -> resumeShift()
            ACTION_STOP -> stopShift()
            ACTION_RECOVER_BOOT -> recoverFromBoot()
        }
        return START_STICKY
    }

    /**
     * Recupera el estado del turno tras un reinicio del dispositivo.
     *
     * Lee [SessionDataStore] para obtener `sessionId`, `startEpoch` y `weeklyAccumulatedMs`.
     * Si existe un turno activo (sessionId > 0) reconstruye el [TimerState] y reanuda el
     * Foreground Service, el WakeLock y el ticker. Si no hay sesión pendiente el servicio
     * se detiene limpiamente.
     */
    private fun recoverFromBoot() {
        serviceScope.launch {
            val sessionId = sessionDataStore.activeSessionId.first() ?: 0L
            val startEpoch = sessionDataStore.serviceStartEpoch.first() ?: System.currentTimeMillis()
            val weeklyBase = sessionDataStore.weeklyAccumulatedMs.first() ?: 0L

            if (sessionId > 0L) {
                val current = TimerStateManager.state.value
                if (current.state == ShiftState.DETENIDO) {
                    TimerStateManager.update {
                        TimerState(
                            state = ShiftState.TRABAJANDO,
                            startEpoch = startEpoch,
                            workRemainingMs = TimerState.MAX_WORK_MS,
                            restRemainingMs = 0L,
                            sessionId = sessionId,
                            baseWeeklyMs = weeklyBase
                        )
                    }
                    startForeground(NOTIFICATION_ID, buildNotification())
                    acquireWakeLock()
                    startUnifiedTicker()
                    updateNotification()
                    alertScheduler.scheduleShiftAlerts(0L, weeklyBase, startEpoch)
                }
            } else {
                stopSelf()
            }
        }
    }

    /**
     * Inicia un turno nuevo.
     *
     * Solo se ejecuta si el estado actual es [ShiftState.DETENIDO]. Registra el `startEpoch`
     * como `System.currentTimeMillis()` (tiempo de pared UTC), obtiene el acumulado semanal
     * previo de [SessionDataStore] y programa las alarmas legales en [AlertScheduler].
     */
    private fun startShift(sessionId: Long) {
        val current = TimerStateManager.state.value
        if (current.state != ShiftState.DETENIDO) return

        val startEpoch = System.currentTimeMillis()
        
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()

        serviceScope.launch {
            val baseWeekly = sessionDataStore.weeklyAccumulatedMs.first() ?: 0L

            TimerStateManager.update {
                TimerState(
                    state = ShiftState.TRABAJANDO,
                    startEpoch = startEpoch,
                    workRemainingMs = TimerState.MAX_WORK_MS,
                    restRemainingMs = 0L,
                    sessionId = sessionId,
                    baseWeeklyMs = baseWeekly
                )
            }
            startUnifiedTicker()
            updateNotification()
            alertScheduler.scheduleShiftAlerts(0L, baseWeekly, startEpoch)
        }
    }

    private fun pauseShift() {
        val current = TimerStateManager.state.value
        if (current.state != ShiftState.TRABAJANDO) {
            android.util.Log.w("TimerService", "pauseShift called but state is ${current.state}")
            return
        }

        alertScheduler.cancelAll()

        val now = System.currentTimeMillis()
        val workSinceStart = now - current.startEpoch
        val totalWork = current.accumulatedWorkBeforePause + workSinceStart

        TimerStateManager.update {
            it.copy(
                state = ShiftState.EN_PAUSA,
                pauseEpoch = now,
                accumulatedWorkBeforePause = totalWork,
                workProgressMs = totalWork,
                workRemainingMs = (TimerState.MAX_WORK_MS - totalWork).coerceAtLeast(0L),
                weeklyProgressMs = it.baseWeeklyMs + totalWork
            )
        }

        updateNotificationForPause()
    }

    private fun resumeShift() {
        val current = TimerStateManager.state.value
        if (current.state != ShiftState.EN_PAUSA) return

        val now = System.currentTimeMillis()
        val restSincePause = now - current.pauseEpoch
        val totalRest = current.accumulatedRestBeforePause + restSincePause

        TimerStateManager.update {
            it.copy(
                state = ShiftState.TRABAJANDO,
                startEpoch = now,
                pauseEpoch = 0L,
                accumulatedWorkBeforePause = it.accumulatedWorkBeforePause,
                accumulatedRestBeforePause = totalRest,
                restProgressMs = totalRest,
                restRemainingMs = (TimerState.MIN_REST_MS - totalRest).coerceAtLeast(0L)
            )
        }

        alertScheduler.scheduleShiftAlerts(current.accumulatedWorkBeforePause, current.baseWeeklyMs, now)
        updateNotification()
    }

    private fun stopShift() {
        tickerJob?.cancel()
        alertScheduler.cancelAll()
        releaseWakeLock()
        TimerStateManager.update { it.copy(state = ShiftState.DETENIDO) }
        serviceScope.launch { sessionDataStore.clearSession() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Ticker unificado de 1 segundo.
     *
     * Actualiza [TimerStateManager] calculando los milisegundos transcurridos desde el último
     * `startEpoch` o `pauseEpoch`. No hay escrituras a base de datos en cada tick; todo el
     * estado en tiempo real es un [kotlinx.coroutines.flow.StateFlow] en memoria, lo que
     * garantiza **cero overhead de I/O** durante la jornada.
     *
     * El bucle se rompe automáticamente cuando el estado pasa a [ShiftState.DETENIDO].
     */
    private fun startUnifiedTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (true) {
                val state = TimerStateManager.state.value

                when (state.state) {
                    ShiftState.TRABAJANDO -> {
                        val now = System.currentTimeMillis()
                        val workProgress = state.accumulatedWorkBeforePause + (now - state.startEpoch)
                        val workRemaining = (TimerState.MAX_WORK_MS - workProgress).coerceAtLeast(0L)

                        TimerStateManager.update {
                            it.copy(
                                workProgressMs = workProgress,
                                workRemainingMs = workRemaining,
                                restProgressMs = state.accumulatedRestBeforePause,
                                restRemainingMs = (TimerState.MIN_REST_MS - state.accumulatedRestBeforePause).coerceAtLeast(0L),
                                weeklyProgressMs = state.baseWeeklyMs + workProgress
                            )
                        }
                        updateNotification()
                    }

                    ShiftState.EN_PAUSA -> {
                        val now = System.currentTimeMillis()
                        val restProgress = state.accumulatedRestBeforePause + (now - state.pauseEpoch)
                        val restRemaining = (TimerState.MIN_REST_MS - restProgress).coerceAtLeast(0L)

                        TimerStateManager.update {
                            it.copy(
                                restProgressMs = restProgress,
                                restRemainingMs = restRemaining
                            )
                        }
                        updateNotificationForPause()
                    }

                    ShiftState.DETENIDO -> {
                        break
                    }
                }

                delay(1_000L)
            }
        }
    }

    private fun acquireWakeLock() {
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DriverShield::TimerService"
        ).apply {
            acquire(14 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("00:00:00")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setColor(Color.GREEN)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val state = TimerStateManager.state.value
        val timeText = formatMs(state.workProgressMs)

        val openIntent = createOpenIntent()
        val pauseIntent = createPauseAction()
        val stopIntent = createStopAction()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(timeText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setColor(Color.GREEN)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pausa", pauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationForPause() {
        val state = TimerStateManager.state.value
        val restText = formatMs(state.restProgressMs)

        val openIntent = createOpenIntent()
        val resumeIntent = createResumeAction()
        val stopIntent = createStopAction()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(restText)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setColor(Color.RED)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_play, "Retomar", resumeIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createOpenIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createPauseAction(): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply {
            action = ACTION_PAUSE
            setPackage(packageName)
        }
        return PendingIntent.getService(
            this, 100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createResumeAction(): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply {
            action = ACTION_RESUME
            setPackage(packageName)
        }
        return PendingIntent.getService(
            this, 101, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createStopAction(): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply {
            action = ACTION_STOP
            setPackage(packageName)
        }
        return PendingIntent.getService(
            this, 102, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Turno activo",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificación persistente del turno en curso"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveStateBeforeKill()
        tickerJob?.cancel()
        releaseWakeLock()
        TimerStateManager.update { it.copy(state = ShiftState.DETENIDO) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        
        try {
            unregisterReceiver(timeChangedReceiver)
        } catch (e: Exception) {
            // Ignored
        }
        
        if (TimerStateManager.state.value.state != ShiftState.DETENIDO) {
            saveStateBeforeKill()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    /**
     * Persiste el estado mínimo del turno activo en [SessionDataStore] antes de que el proceso
     * sea destruido.
     *
     * Se guarda: `sessionId`, `startEpoch` y `weeklyProgressMs`. Con estos tres valores
     * [BootReceiver] puede reconstruir un [TimerState] coherente al arrancar el servicio
     * mediante [ACTION_RECOVER_BOOT], evitando que el turno quede huérfano en la base de datos
     * con `endTimestamp = null`.
     */
    private fun saveStateBeforeKill() {
        val state = TimerStateManager.state.value
        if (state.sessionId > 0L) {
            runBlocking {
                sessionDataStore.saveActiveSessionId(state.sessionId)
                sessionDataStore.setServiceStartEpoch(state.startEpoch)
                sessionDataStore.setWeeklyAccumulatedMs(state.weeklyProgressMs)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun formatMs(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    companion object {
        const val CHANNEL_ID = "timer_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.drivershield.ACTION_START"
        const val ACTION_PAUSE = "com.drivershield.ACTION_PAUSE"
        const val ACTION_RESUME = "com.drivershield.ACTION_RESUME"
        const val ACTION_STOP = "com.drivershield.ACTION_STOP"
        const val ACTION_RECOVER_BOOT = "com.drivershield.ACTION_RECOVER_BOOT"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
