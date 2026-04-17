# PRD & Especificación Técnica: Proyecto "DriverShield"

**Versión:** 1.3  
**Fecha:** 2026-04-17  
**Rol:** Arquitecto de Software Senior (Kotlin/Android) + Orquestador  
**Metodología:** AI-First Estricto · Clean Architecture · MVVM · Plan Mode  
**Estado:** ✅ COMPLETADO — tests verdes · BUILD SUCCESSFUL · tema CASIO · Day Overrides implementado

---

## 1. Visión del Producto

**DriverShield** es una aplicación Android nativa para conductores profesionales nocturnos (VTC/Cabify) que opera en segundo plano de forma ininterrumpida. Registra con precisión turno activo, descansos y acumulado semanal, con interfaz optimizada para uso nocturno.

**Restricciones de negocio no negociables:**

- Turno máximo: **8 horas/día**
- Descanso mínimo entre turnos: **4 horas**
- Límite semanal: **40 horas** (resetea domingo fin de turno)
- Consumo de batería: debe ser mínimo y justificable ante el SO

**Requisitos Core (MVP):**

- Contadores progresivos/regresivos para turno (8h/día), descanso (4h máximo) y total semanal (40h)
- Foreground Service + Wakelocks para evitar cierre en segundo plano (Doze Mode)
- Notificaciones preventivas: alertas legales (ej: "4h de conducción continua")
- Persistencia con Room para historial de turnos
- Interfaz "AMOLED Black", botones grandes, cierre por pulsación larga (anti-errores)
- Calendario visual de libranzas

---

## 2. Stack Tecnológico Validado

| **Capa**        | **Tecnología**                                | **Versión**         | **Justificación**                                                                     |
|-----------------|-----------------------------------------------|---------------------|---------------------------------------------------------------------------------------|
| **Lenguaje**    | Kotlin                                        | 2.0                 | Tipado estricto, corrutinas nativas, null-safety                                      |
| **UI**          | Jetpack Compose + Navigation Compose          | BOM 2025.05.00      | Declarativo, hot-reload en dev, menos boilerplate que Views                           |
| **Material**    | Material 3                                    | 1.3+                | Soporte AMOLED negro puro (`dynamicColor = false`, `surface = Color.Black`)           |
| **Persistencia**| Room + DataStore Preferences                  | Room 2.7 / DS 1.1.4 | ORM oficial con `Flow<T>`; DataStore para estado efímero de sesión activa             |
| **DI**          | Hilt                                          | 2.56                | DI oficial Android, integrado con ViewModel/Service, reduce boilerplate vs Koin       |
| **Background**  | Foreground Service + Coroutines + AlarmManager| Kotlin Coroutines   | Ejecución persistente garantizada; AlarmManager para alertas exactas en Doze Mode     |
| **Tests**       | JUnit 5 + Turbine + Robolectric               | 5.11.4 / 1.2.0      | Tests unitarios de UseCases y Flow de contadores; Room in-memory en JVM               |
| **CI/CD**       | GitHub Actions + ktlint + Release Please      | —                   | Lint + tests + build + versionado semántico automático                                |
| **Procesador**  | KSP                                           | —                   | Reemplaza KAPT para Room/Hilt (2× más rápido)                                         |
| **Min SDK**     | Android 26 (Android 8.0)                      | target 35           | `java.time` nativo, `AlarmManager` exacto, `setExactAndAllowWhileIdle()`              |

**❌ Prohibido:**

- RxJava, Gson, Glide, OkHttp, Retrofit, Firebase (salvo orden explícita del Director)
- `SharedPreferences` → reemplazado por DataStore
- `Handler.postDelayed()` → reemplazado por corrutinas
- `JobScheduler` → sin garantía de timing exacto
- `Calendar.WEEK_OF_YEAR` → usar `WeekFields.ISO` de `java.time`

---

## 3. Estructura de Carpetas (Clean Architecture — Estado Actual)

```text
app/src/main/java/com/drivershield/
│
├── core/                                    # Utilidades transversales
│   ├── di/
│   │   ├── AppModule.kt                     # Hilt: Room, DataStore, AlarmManager
│   │   └── ServiceModule.kt                 # Hilt: TimerService bindings
│   ├── extensions/                          # TimeExtensions.kt, etc.  [pendiente]
│   ├── model/
│   │   ├── ShiftSession.kt                  # Alias plano para acceso transversal
│   │   └── ShiftType.kt
│   └── utils/
│       ├── Constants.kt                     # MAX_SHIFT_MS, MAX_WEEKLY_MS, etc.
│       └── Helpers.kt
│
├── data/                                    # Capa de datos
│   ├── local/
│   │   ├── db/
│   │   │   ├── AppDatabase.kt               # RoomDatabase singleton  [pendiente]
│   │   │   ├── dao/
│   │   │   │   ├── ShiftDao.kt
│   │   │   │   └── WeeklyDao.kt
│   │   │   ├── entity/
│   │   │   │   ├── ShiftSessionEntity.kt
│   │   │   │   └── WeeklyAggregateEntity.kt [pendiente]
│   │   │   └── migration/
│   │   │       └── Migration1.kt            # Schema v1 → v2
│   │   └── datastore/
│   │       └── SessionDataStore.kt          # Estado efímero de sesión activa
│   └── repository/
│       ├── impl/
│       │   ├── ShiftRepositoryImpl.kt
│       │   └── WeeklyRepositoryImpl.kt      [pendiente]
│       └── interface/
│           ├── ShiftRepository.kt
│           └── ExportRepository.kt
│
├── domain/                                  # Reglas de negocio puras (sin Android SDK)
│   ├── model/
│   │   ├── ShiftSession.kt
│   │   ├── ShiftType.kt                     # Enum: NORMAL | EXTENDED | NIGHT | SPLIT
│   │   └── WeeklySummary.kt                 [pendiente]
│   ├── repository/
│   │   ├── ShiftRepository.kt               # Interfaz de dominio
│   │   └── WeeklyRepository.kt              [pendiente]
│   └── usecase/
│       ├── StartShiftUseCase.kt
│       ├── EndShiftUseCase.kt
│       ├── CheckLegalLimitsUseCase.kt
│       ├── StartRestUseCase.kt              [pendiente]
│       ├── GetCurrentTimerStateUseCase.kt   [pendiente]
│       ├── GetWeeklySummaryUseCase.kt       [pendiente]
│       └── ResetWeeklyCounterUseCase.kt     [pendiente]
│
├── service/                                 # Foreground Service
│   ├── TimerService.kt                      # WakeLock + contadores + notificación
│   ├── TimerServiceConnection.kt            # Binding helper para UI  [pendiente]
│   └── notification/
│       ├── AlertScheduler.kt
│       └── NotificationHelper.kt            [pendiente]
│
└── presentation/                            # UI Compose
    ├── MainActivity.kt                      [pendiente]
    ├── navigation/
    │   └── AppNavHost.kt
    ├── screen/
    │   ├── dashboard/
    │   │   ├── DashboardScreen.kt
    │   │   └── DashboardViewModel.kt
    │   ├── history/
    │   │   ├── HistoryScreen.kt
    │   │   └── HistoryViewModel.kt
    │   └── calendar/
    │       ├── CalendarScreen.kt
    │       └── CalendarViewModel.kt
    ├── component/
    │   ├── LongPressButton.kt
    │   ├── TimerDisplay.kt
    │   ├── WeeklyProgressBar.kt             [pendiente]
    │   └── StatusChip.kt                   [pendiente]
    ├── theme/
    │   ├── Color.kt
    │   ├── Type.kt
    │   └── Theme.kt                        [pendiente]
    └── widget/
        └── AppWidgetProvider.kt             # Reservado para v1.1

app/src/main/res/
├── drawable/                                # ic_launcher, fondos AMOLED
└── values/
    ├── colors.xml
    ├── strings.xml
    └── themes.xml

app/src/test/java/com/drivershield/          # Unit tests  [pendiente]
    ├── usecase/
    │   ├── StartShiftUseCaseTest.kt
    │   ├── EndShiftUseCaseTest.kt
    │   └── CheckLegalLimitsUseCaseTest.kt
    └── repository/
        └── ShiftRepositoryTest.kt

app/src/androidTest/java/com/drivershield/   # Instrumented tests  [pendiente]
    └── service/
        └── TimerServiceTest.kt
```

---

## 4. Modelo de Datos (Room / SQLite)

### 4.1 Entidades

**`shift_sessions`** — Registro de cada período de actividad

```kotlin
@Entity(tableName = "shift_sessions")
data class ShiftSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTimestamp: Long,       // Epoch ms (UTC). Nunca nullable.
    val endTimestamp: Long?,        // Null = sesión activa en curso
    val type: String,               // ShiftType.name: NORMAL | EXTENDED | NIGHT | SPLIT
    val durationMs: Long?,          // Calculado al cerrar; null si abierta
    val isoWeekNumber: Int,         // 1–53 (ISO 8601)
    val isoYear: Int,               // Para semanas que cruzan año
    val notes: String? = null       // Campo libre (reservado para v2)
)
```

Índices: `(isoYear, isoWeekNumber)` para queries semanales · `(startTimestamp)` para histórico

**`day_overrides`** — Overrides manuales del calendario de libranzas

```kotlin
@Entity(tableName = "day_overrides")
data class DayOverrideEntity(
    @PrimaryKey
    val date: LocalDate,          // Fecha afectada (zona local del dispositivo)
    val isLibranza: Boolean       // true = libranza, false = forzar como laborable
)
```

Un override tiene prioridad sobre `WorkSchedule.offDays`. `ToggleDayOverrideUseCase`
lo elimina cuando el nuevo estado coincide con el estado fijo — sin filas redundantes.
`NightShiftSplitter` usa la fecha del `startTimestamp` de cada sesión para distribuir
las horas nocturnas correctamente entre días naturales al generar informes.

---

**`weekly_aggregates`** — Resumen acumulado por semana ISO

```kotlin
@Entity(tableName = "weekly_aggregates", primaryKeys = ["isoYear", "isoWeekNumber"])
data class WeeklyAggregateEntity(
    val isoYear: Int,
    val isoWeekNumber: Int,
    val totalWorkMs: Long,
    val totalRestMs: Long,
    val sessionCount: Int,
    val lastUpdated: Long           // Epoch ms del último update
)
```

### 4.2 Estado de Sesión Activa (DataStore)

Almacenado en Preferences DataStore, **no en Room** — lectura de cero latencia desde el servicio:

| Clave                    | Tipo    | Descripción                                         |
|--------------------------|---------|-----------------------------------------------------|
| `active_session_id`      | `Long`  | ID de `shift_sessions` si hay turno abierto         |
| `service_start_epoch`    | `Long`  | Epoch ms cuando arrancó el servicio                 |
| `weekly_accumulated_ms`  | `Long`  | Cache del total semanal (sincronizado con Room)     |
| `last_alert_threshold`   | `Int`   | Última alerta disparada (evita repetición)          |

---

## 5. Foreground Service — Diseño

### 5.1 Ciclo de Vida

```text
Pulsación larga "Iniciar turno"
    │
    ▼
DashboardViewModel.startShift()
    ├─► StartShiftUseCase → Room INSERT + DataStore
    └─► startForegroundService(TimerService)
            ├─► PARTIAL_WAKE_LOCK ("drivershield:timer")
            ├─► startForeground(NOTIF_ID, buildNotification())
            ├─► corrutina ticker (1s, Dispatchers.IO)
            └─► emite TimerState via StateFlow

Pulsación larga "Finalizar turno" (1 500 ms)
    │
    ▼
DashboardViewModel.endShift()
    ├─► EndShiftUseCase → Room UPDATE + WeeklyAggregateRepository.upsert()
    └─► TimerService.stopSelf()
            ├─► libera WakeLock
            ├─► cancela ticker
            └─► cancela notificación
```

### 5.2 Comunicación Service ↔ UI

Bound Service para evitar serialización entre procesos:

```text
Activity ──bindService()──► TimerService
         ◄─ StateFlow<TimerState> (SharedFlow, replay=1)
         ◄─ ViewModel.collect() → Compose recompone
```

**`TimerState`:**

```kotlin
data class TimerState(
    val elapsedWorkMs: Long,
    val elapsedRestMs: Long,
    val weeklyAccumulatedMs: Long,
    val phase: TimerPhase,          // WORK | REST | IDLE
    val alertLevel: AlertLevel      // NONE | WARNING_4H | WARNING_6H | LIMIT_8H | WARNING_38H | LIMIT_40H
)
```

### 5.3 Estrategia Anti-Doze Mode (multicapa)

| Capa | Mecanismo                                          | Cobertura                            |
|------|----------------------------------------------------|--------------------------------------|
| 1    | Foreground Service + notificación permanente       | Impide que Android elimine el proceso|
| 2    | `PARTIAL_WAKE_LOCK`                                | CPU activa con pantalla apagada      |
| 3    | `AlarmManager.setExactAndAllowWhileIdle()`         | Alertas funcionan en Doze Mode       |
| 4    | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`      | Exención de optimización al 1º uso   |
| 5    | `START_STICKY` en `onStartCommand`                 | SO reinicia el servicio si lo mata   |

### 5.4 Ticker interno

```kotlin
// Recalcula desde startEpoch para eliminar drift acumulado
scope.launch(Dispatchers.IO) {
    while (isActive) {
        delay(1_000)
        val elapsed = System.currentTimeMillis() - sessionStartEpoch
        _timerState.update { it.copy(elapsedWorkMs = elapsed, weeklyAccumulatedMs = weeklyBase + elapsed) }
        checkAndEmitAlerts(elapsed)
    }
}
```

### 5.5 Notificación persistente

- Canal: `CHANNEL_TIMER` (importancia `LOW` — sin sonido ni vibración)
- Contenido: `"Turno: 03:42:17 | Semanal: 21h 30m"`
- Acciones inline: **[Pausar]** **[Finalizar]**
- Ícono: monocromático (requerimiento Android 13+)

---

## 6. UI/UX — Especificaciones para Conductores Nocturnos

### 6.1 Paleta AMOLED

```kotlin
val AmoledBlack  = Color(0xFF000000)   // Fondo principal — negro puro OLED
val SurfaceDark  = Color(0xFF0D0D0D)   // Cards y superficies elevadas
val OnSurface    = Color(0xFFE0E0E0)   // Texto principal (alto contraste)
val Accent       = Color(0xFF00E5FF)   // Cian eléctrico — acciones primarias
val WarningAmber = Color(0xFFFFB300)   // Alertas preventivas
val DangerRed    = Color(0xFFFF1744)   // Límite alcanzado
val RestGreen    = Color(0xFF00C853)   // Estado de descanso activo
```

### 6.2 Componentes Clave

- **`TimerDisplay`**: fuente monoespaciada, mínimo 64sp, centrado en pantalla
- **`LongPressButton`**: 1 500 ms para confirmar, animación radial de progreso, sin diálogo adicional
- **`WeeklyProgressBar`**: 0–40h, verde → ámbar (>32h) → rojo (>38h), valor numérico sobre la barra
- **`StatusChip`**: estados `ACTIVO / DESCANSANDO / LIBRE`

### 6.3 Navegación (Bottom Navigation — 3 destinos)

```text
[Turno]      →  DashboardScreen   (default)
[Historial]  →  HistoryScreen
[Calendario] →  CalendarScreen    (libranzas)
```

---

## 7. Alertas Preventivas

| Evento                        | Trigger                   | Tipo                              |
|-------------------------------|---------------------------|-----------------------------------|
| 4h de conducción continua     | `elapsedWork == 4h`       | Notificación + vibración corta    |
| 6h de conducción continua     | `elapsedWork == 6h`       | Notificación + vibración larga    |
| Límite 8h alcanzado           | `elapsedWork == 8h`       | Notificación urgente + bloqueo UI |
| 38h semanales                 | `weeklyMs == 38h`         | Notificación preventiva           |
| Límite 40h alcanzado          | `weeklyMs == 40h`         | Notificación urgente              |

Todas usan `AlarmManager.setExactAndAllowWhileIdle()` programadas al iniciar sesión. No dependen del ticker interno (más fiables en Doze).

---

## 8. Permisos Requeridos

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />          <!-- Runtime, Android 13+ -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />        <!-- Android 12+, fallback a setWindow -->
```

---

## 9. Riesgos Técnicos

### 9.1 Críticos

| Riesgo                                                         | Probabilidad | Impacto  | Mitigación                                                                              |
|----------------------------------------------------------------|--------------|----------|-----------------------------------------------------------------------------------------|
| OEM mata el proceso (MIUI, OneUI, EMUI)                        | Alta         | Crítico  | Solicitar exención de batería en onboarding; documentar config manual por fabricante    |
| WakeLock no liberado → batería drenada                         | Media        | Alto     | `try/finally` garantizado; timeout de seguridad a 10h                                   |
| Doze Mode difiere AlarmManager                                 | Media        | Alto     | `setExactAndAllowWhileIdle()` obligatorio, nunca `setExact()`                           |
| Pérdida de datos si el proceso muere con sesión abierta        | Baja         | Alto     | `startTimestamp` persistido en DataStore antes de confirmar UI; `START_STICKY`          |

### 9.2 Moderados

| Riesgo                                      | Mitigación                                                                    |
|---------------------------------------------|-------------------------------------------------------------------------------|
| Drift de reloj del sistema (zonas horarias) | Siempre UTC en Room; UI convierte a zona local                                |
| Semana ISO vs semana calendario             | `WeekFields.ISO` de `java.time`; nunca `Calendar.WEEK_OF_YEAR`                |
| Room migration sin pérdida de datos         | `Migration` explícita desde v1 en `AppDatabase`; pruebas `MigrationTestHelper`|
| Restricciones background en Android 14+     | Declarar `foregroundServiceType="dataSync"` en Manifest                       |

---

## 10. CI/CD — Estado Actual

Pipeline activo en `.github/workflows/ci.yml` con 4 jobs:

```text
lint  →  unit-tests  →  build  →  release-please
```

| Job              | Trigger              | Acción                                                        |
|------------------|----------------------|---------------------------------------------------------------|
| `lint`           | push / PR            | `./gradlew lintDebug` (Android Lint — reemplazó ktlint)       |
| `unit-tests`     | tras lint            | `./gradlew testDebugUnitTest` + reporte en PR                 |
| `build`          | tras tests           | `./gradlew assembleDebug` + artefacto APK (7 días)            |
| `release-please` | push a `main`        | CHANGELOG + bump semántico + tag `vX.Y.Z`                     |

**Security Review (Claude):** comentado temporalmente. Reactivar cuando se añada `ANTHROPIC_API_KEY` en `Settings → Secrets → Actions`. Revisión manual con `qa-tester.md` antes de cada PR.

---

## 11. Decisiones Arquitectónicas Tomadas

| # | Decisión                          | Resolución                                                                          |
|---|-----------------------------------|-------------------------------------------------------------------------------------|
| 1 | ¿Soporte multiusuario?            | **No** (MVP) — un conductor por dispositivo                                         |
| 2 | ¿Exportación de datos?            | **Sí, v1.0** — PDF con hash SHA-256 + CSV con epoch timestamps, ambos implementados |
| 3 | ¿Sincronización en la nube?       | **No** — sin capa de red, sin Firebase                                              |
| 4 | API mínima                        | **26 (Android 8.0)** — `java.time` nativo, `setExactAndAllowWhileIdle()`            |
| 5 | ¿Widget de pantalla de inicio?    | **No** (v1.0) — reservado para versión futura                                       |
| 6 | ¿Tema visual?                     | **CASIO 80's** — LCD `#9BA591`, resina `#212121`, fuente Digital7, bordes `2.dp`    |
| 7 | ¿Toggle libre de libranzas?       | **Sí** — el conductor puede cambiar cualquier día sin restricción de días mínimos   |
