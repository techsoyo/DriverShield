# SKILL: System/Battery Expert
> Versión: 1.0 · Proyecto: DriverShield
> Carga este skill SOLO cuando trabajes en: `service/`, `core/`, `AndroidManifest.xml`
> No cargar para UI, BD o tests.

---

## Dominio de este Skill

Foreground Services, WakeLocks, Doze Mode, AlarmManager, Coroutines de larga duración.
Objetivo único: el servicio sobrevive 12 horas con pantalla apagada en un Xiaomi MIUI sin configuración especial.

---

## Reglas de Implementación (No Negociables)

### WakeLock
```kotlin
// CORRECTO: Tag con formato package:descriptor + timeout de seguridad
private fun acquireWakeLock() {
    val pm = getSystemService(POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "com.drivershield:timer"
    ).apply { acquire(10 * 60 * 60 * 1_000L) }  // Timeout: 10h
}

// CORRECTO: Liberación GARANTIZADA en try/finally
override fun onDestroy() {
    try {
        serviceScope.cancel()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
    } finally {
        super.onDestroy()
    }
}

// PROHIBIDO: acquire() sin timeout
wakeLock.acquire()  // ← drena batería si el servicio muere sin liberar
```

### Ticker sin drift
```kotlin
// CORRECTO: Siempre desde epoch absoluto (elimina drift acumulado ~2-5ms/ciclo)
private suspend fun runTicker(startEpoch: Long) {
    while (currentCoroutineContext().isActive) {
        delay(1_000)
        val elapsed = System.currentTimeMillis() - startEpoch  // recalcular siempre
        _timerState.update { it.copy(elapsedWorkMs = elapsed) }
    }
}

// PROHIBIDO: Acumulación de deltas
var accumulated = 0L
accumulated += 1_000  // ← drift garantizado en turnos de 8h
```

### AlarmManager (Alertas Preventivas)
```kotlin
// CORRECTO: setExactAndAllowWhileIdle para Doze Mode
fun scheduleAlert(triggerAtMs: Long, requestCode: Int) {
    val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAtMs,
        buildPendingIntent(requestCode)
    )
}

// PROHIBIDO: setExact() — no funciona en Doze Mode
// PROHIBIDO: setRepeating() — deprecated y no fiable
```

### Foreground Service (onStartCommand)
```kotlin
// CORRECTO: START_STICKY garantiza reinicio por el SO
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(NOTIF_ID, buildNotification())
    acquireWakeLock()
    launchTicker()
    return START_STICKY  // ← SIEMPRE
}
```

### AndroidManifest.xml (Permisos obligatorios)
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>

<!-- foregroundServiceType obligatorio en Android 14+ -->
<service
    android:name=".service.TimerService"
    android:foregroundServiceType="dataSync"
    android:exported="false"/>
```

---

## Estrategia Anti-Doze (5 Capas)

| Capa | Mecanismo | Cobertura |
|---|---|---|
| 1 | Foreground Service + notificación permanente | Impide eliminación del proceso |
| 2 | PARTIAL_WAKE_LOCK | CPU activa con pantalla apagada |
| 3 | setExactAndAllowWhileIdle | Alarmas funcionan en Doze |
| 4 | ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Exención en onboarding |
| 5 | START_STICKY | SO reinicia si mata el proceso |

---

## Checklist de Validación (antes de PR)

- [ ] WakeLock tiene tag formato `"packagename:descriptor"`
- [ ] WakeLock tiene timeout explícito (10h máx)
- [ ] `try/finally` garantiza `release()` en TODA rama de ejecución
- [ ] Ticker calcula desde `startEpoch` absoluto, no acumula deltas
- [ ] Alarmas usan `setExactAndAllowWhileIdle()`, NO `setExact()`
- [ ] `foregroundServiceType="dataSync"` en Manifest
- [ ] `onStartCommand` retorna `START_STICKY`
- [ ] Timeout de seguridad de WakeLock ≤ 10h

---

## Prohibiciones Absolutas

```
❌ Handler.postDelayed() como sustituto de corrutinas
❌ Timer de Java para scheduling
❌ Cualquier librería de terceros para background tasks
❌ setRepeating() de AlarmManager
❌ acquire() sin timeout
❌ Corrutina que NO comprueba isActive en el loop
```

---

## Archivos que modifica este Skill

```
app/src/main/
├── AndroidManifest.xml
├── java/com/drivershield/
│   ├── service/
│   │   ├── TimerService.kt          ← OWNER de este skill
│   │   ├── TimerServiceConnection.kt
│   │   └── notification/
│   │       ├── NotificationHelper.kt
│   │       └── AlertScheduler.kt    ← OWNER de este skill
│   └── core/
│       └── utils/Constants.kt       ← LegalLimits, timeouts
```

*Fin de SKILL: system-battery-expert.md*