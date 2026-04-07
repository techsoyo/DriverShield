# AGENT.md — DriverShield Persistent Context (v2.0)
> Versión: 1.0 · Validado: 2026-03-29
> Director del Proyecto: Orquestador humano
> Ejecutor: Arquitecto de Software Senior (Kotlin/Android)
> **PLAN MODE: SIEMPRE ACTIVO** — ningún archivo se crea, modifica o elimina sin OK explícito.

---

## 0. Reglas de Oro (No Negociables)

```markdown

REGLA 0.1 — Protocolo de Memoria (Persistence)
 Antes de cerrar la sesión, el EJECUTOR debe resumir en 2 líneas el estado actual en la sección 6.1 "Pendientes Inmediatos".
 Al iniciar, el EJECUTOR leerá esa sección para proponer la continuación natural.

REGLA 1 — PLAN BEFORE CODE
  Antes de cualquier acción: detallar cambios → esperar OK → ejecutar.
  Nunca generar código funcional sin confirmación del Director.

REGLA 2 — SINGLE RESPONSIBILITY PER SESSION
  Cada sesión trabaja UN módulo. No mezclar Service + UI + BD en el mismo turno.

REGLA 3 — BATTERY FIRST
  Toda decisión técnica se evalúa primero por impacto en batería.
  Si una librería de terceros puede reemplazarse con Jetpack: siempre Jetpack.

REGLA 4 — ZERO EXTERNAL HEAVY LIBS
  PROHIBIDO: Rx Java, Gson, Glide, OkHttp, Retrofit, Firebase (salvo orden explícita).
  PERMITIDO: Kotlin Stdlib, Jetpack BOM, Hilt, kotlinx.serialization, Turbine (tests).

REGLA 5 — STRICT KOTLIN TYPING
  Sin `Any`, sin `!!`, sin `lateinit` no justificado.
  Toda función con efectos de red o BD debe ser `suspend` o retornar `Flow<T>`.

REGLA 6 — Pendientes Inmediatos (Memoria de Sesión)
  [x] Infraestructura Gradle COMPLETADA (libs.versions.toml, Java 17, SDK 35, AndroidX, repositorios)
  [x] DB Room v1 COMPLETADA (shift_sessions + weekly_aggregates + shift_events + work_schedule, Hilt DI, sin DELETE físico)
  [x] UI System COMPLETADO (Tema AMOLED, 5 contadores, Start/Pause/Reset, TimerService con estados)
  [x] Navigation Drawer COMPLETADO (4 secciones: Panel, Calendario, Historial, Configuración)
  [x] Historial de Eventos COMPLETADO (DayReport con cronología, export PDF/CSV, alertas de exceso)
  [x] Calendario con Vista Semanal COMPLETADO (Agenda 24h×7d, toggle Mes/Semana, ciclo 5 semanas)
  [x] Configuración PERSISTENTE COMPLETADA (DataStore: horas, offDays, conductor, ciclo — doble combine)
  [ ] Siguiente paso crítico: Testing integral en emulador + verificación de persistencia post-reinicio
```

---

## 1. Decisiones Estratégicas Validadas (Sección 10 del spec.md)

| Decisión | Valor | Impacto arquitectónico |
|---|---|---|
| Multiusuario | ❌ NO | Schema Room sin tabla `users`; sin particionamiento por userId |
| Exportación datos | ✅ SÍ (CSV/PDF) | `ExportRepository` interface reservada en domain; permisos WRITE_EXTERNAL preparados |
| Sincronización cloud | ❌ NO | Sin capa de red; sin WorkManager para sync; privacidad total |
| API mínima | 26 (Android 8.0) | `java.time` nativo OK; `setExactAndAllowWhileIdle` disponible; minSdk = 26 |
| Widget pantalla inicio | 🔜 v1.1 | `AppWidgetProvider` + `RemoteViews` — espacio reservado en `presentation/widget/` |

---

## 2. Stack Canónico (Referencia Inmutable)

```kotlin
// build.gradle.kts (app) — versiones de referencia
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 26          // Android 8.0
        targetSdk = 35
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false   // java.time nativo en API 26+
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Core
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    // DI
    implementation("com.google.dagger:hilt-android:2.56")
    ksp("com.google.dagger:hilt-compiler:2.56")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Persistencia
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.4")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("androidx.room:room-testing:2.7.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.mockk:mockk:1.13.14")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
```

---

## 3. Skills — Agentes Especializados

El Ejecutor activa el Skill correspondiente según el módulo en curso.
Solo un Skill activo por sesión. El Director indica el cambio de Skill.

---

### 🔋 SKILL 1 — System/Battery Expert

**Dominio:** `service/`, `core/`, `AndroidManifest.xml`, Doze Mode, WakeLocks.

**Mentalidad:** El servicio debe sobrevivir 12 horas ininterrumpidas con pantalla apagada en un Xiaomi MIUI sin configuración especial. Todo lo que no sea estrictamente necesario se elimina.

**Checklist de activación:**
- [ ] ¿El `WakeLock` tiene tag con formato `"packagename:descriptor"`?
- [ ] ¿Hay un `try/finally` que garantice `release()` en toda rama de ejecución?
- [ ] ¿El ticker usa `startEpoch` absoluto (no acumulativo)?
- [ ] ¿Las alarmas usan `setExactAndAllowWhileIdle()` y NO `setExact()`?
- [ ] ¿El servicio declara `foregroundServiceType="dataSync"` en Manifest?
- [ ] ¿`onStartCommand` retorna `START_STICKY`?
- [ ] ¿Hay timeout de seguridad de WakeLock a 10h (36_000_000 ms)?

**Patrones canónicos de este Skill:**

```kotlin
// CORRECTO — ticker con epoch absoluto (sin drift)
private suspend fun runTicker(startEpoch: Long) {
    while (currentCoroutineContext().isActive) {
        delay(1_000)
        val elapsed = System.currentTimeMillis() - startEpoch
        _timerState.update { it.copy(elapsedWorkMs = elapsed) }
    }
}

// CORRECTO — WakeLock con protección garantizada
private fun acquireWakeLock() {
    val pm = getSystemService(POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "com.drivershield:timer"
    ).apply { acquire(10 * 60 * 60 * 1000L) }  // timeout 10h
}

// CORRECTO — liberación garantizada
override fun onDestroy() {
    try {
        serviceScope.cancel()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
    } finally {
        super.onDestroy()
    }
}
```

**Prohibiciones en este Skill:**
- `Handler.postDelayed()` como sustituto de corrutinas
- `Timer` de Java
- Cualquier librería de terceros para scheduling
- `setRepeating()` de AlarmManager (no funciona en Doze)

---

### 🖥️ SKILL 2 — UI/UX Maestro

**Dominio:** `presentation/`, `theme/`, `component/`, Jetpack Compose.

**Mentalidad:** El conductor usa la app con un ojo en la carretera, a las 3 AM, con lluvia. Cada toque debe ser infalible. El negro AMOLED no es estética: es ahorro de batería y reducción de fatiga visual.

**Checklist de activación:**
- [ ] ¿El fondo usa `Color(0xFF000000)` (negro puro, no `#0D0D0D`)?
- [ ] ¿Los botones críticos tienen mínimo 56dp de altura táctil?
- [ ] ¿El `LongPressButton` tiene feedback háptico (`HapticFeedbackType.LongPress`)?
- [ ] ¿El tamaño de fuente del timer principal es ≥ 64sp?
- [ ] ¿Ningún elemento crítico depende solo del color (accesibilidad daltonismo)?
- [ ] ¿Toda navegación tiene `contentDescription` para TalkBack?

**Sistema de diseño canónico:**

```kotlin
// theme/Color.kt
object DriverShieldColors {
    val AmoledBlack  = Color(0xFF000000)  // Fondo app — negro puro OLED
    val Surface      = Color(0xFF0D0D0D)  // Cards y contenedores
    val SurfaceHigh  = Color(0xFF1A1A1A)  // Superficies elevadas (dialogs)
    val OnSurface    = Color(0xFFE0E0E0)  // Texto primario
    val OnSurfaceMid = Color(0xFF9E9E9E)  // Texto secundario
    val Accent       = Color(0xFF00E5FF)  // Acciones primarias (cian eléctrico)
    val AccentDim    = Color(0xFF006978)  // Accent en estado deshabilitado
    val WorkGreen    = Color(0xFF00C853)  // Estado: turno activo
    val RestAmber    = Color(0xFFFFB300)  // Estado: descanso / advertencia
    val DangerRed    = Color(0xFFFF1744)  // Estado: límite alcanzado
    val Divider      = Color(0xFF2A2A2A)  // Separadores
}

// Tamaños táctiles — nunca por debajo de estos valores
object TouchTargets {
    val Minimum    = 48.dp   // Elementos secundarios
    val Primary    = 56.dp   // Botones de acción principal
    val Critical   = 72.dp   // Iniciar/Finalizar turno
    val LongPress  = 80.dp   // Botón de cierre (anti-error)
}
```

**Prohibiciones en este Skill:**
- Gradientes decorativos (cada gradiente = batería)
- Animaciones sin `LocalReduceMotion.current` check
- Texto blanco sobre fondo gris claro (contraste < 4.5:1)
- Modales de confirmación en el flujo crítico (el LongPress ES la confirmación)

---

### 🗄️ SKILL 3 — Data Guardian

**Dominio:** `data/`, `domain/model/`, `domain/repository/`, Room, DataStore.

**Mentalidad:** Los datos de turnos son evidencia legal. Una sesión corrupta o perdida puede costar una multa al conductor. Toda escritura es atómica. Nunca se elimina un registro; se marca como `isDeleted = false` (soft delete reservado para v2).

**Checklist de activación:**
- [ ] ¿Toda función DAO es `suspend` o retorna `Flow<T>`?
- [ ] ¿Las transacciones multi-tabla usan `@Transaction`?
- [ ] ¿El `isoWeekNumber` se calcula con `WeekFields.ISO` y NO con `Calendar.WEEK_OF_YEAR`?
- [ ] ¿Los timestamps se almacenan en UTC (epoch ms)?
- [ ] ¿Hay `Migration` definida antes de cambiar el schema?
- [ ] ¿El `DataStore` persiste `startEpoch` antes de confirmar la UI?

**Contratos de Repository (interfaces de dominio):**

```kotlin
// domain/repository/ShiftRepository.kt
interface ShiftRepository {
    suspend fun startSession(type: ShiftType): Long          // Retorna ID insertado
    suspend fun endSession(id: Long): ShiftSession           // Retorna sesión cerrada
    fun getActiveSession(): Flow<ShiftSession?>
    fun getSessionsByWeek(isoYear: Int, week: Int): Flow<List<ShiftSession>>
    suspend fun getWeeklyWorkMs(isoYear: Int, week: Int): Long
}

// domain/repository/ExportRepository.kt  ← RESERVADO para exportación
interface ExportRepository {
    suspend fun exportToCsv(isoYear: Int, week: Int): Uri    // Uri del archivo generado
    suspend fun exportToPdf(isoYear: Int, week: Int): Uri
}
// NOTA: ExportRepositoryImpl NO se implementa en MVP.
// La interfaz existe para no romper Clean Architecture cuando se añada en v1.1.
```

**Regla de zona horaria:**

```kotlin
// CORRECTO — siempre UTC en BD, convertir en capa de presentación
val epochUtcMs: Long = System.currentTimeMillis()

// CORRECTO — semana ISO (nunca Calendar.WEEK_OF_YEAR)
val weekFields = WeekFields.ISO
val now = LocalDate.now()
val isoWeek = now.get(weekFields.weekOfWeekBasedYear())
val isoYear = now.get(weekFields.weekBasedYear())

// PROHIBIDO
val cal = Calendar.getInstance()
val week = cal.get(Calendar.WEEK_OF_YEAR)  // ← Roto en semanas que cruzan año
```

**Prohibiciones en este Skill:**
- `runBlocking` en código de producción
- DELETE físico de registros de turno
- Timestamps en zona local almacenados en BD
- `SharedPreferences` (reemplazado por DataStore)

---

### 🧪 SKILL 4 — QA Tester

**Dominio:** `test/`, `androidTest/`, lógica de límites legales, regresiones de contador.

**Mentalidad:** La ley no admite errores de redondeo. 8h es 28_800_000 ms exactos. Las pruebas son la única garantía de que el conductor no recibirá una multa por un bug del software.

**Checklist de activación:**
- [ ] ¿Cada UseCase tiene al menos: caso feliz + caso límite + caso de error?
- [ ] ¿Los tests de tiempo usan `TestCoroutineScheduler` (no `Thread.sleep`)?
- [ ] ¿Los tests de Room usan base de datos en memoria (`Room.inMemoryDatabaseBuilder`)?
- [ ] ¿Los Flow se testean con `turbine` (`.test { }`) y NO con `collect`?
- [ ] ¿Los límites legales tienen test con valor `límite - 1ms`, `límite` y `límite + 1ms`?

**Límites legales a testear (constantes de referencia):**

```kotlin
// core/utils/Constants.kt
object LegalLimits {
    const val MAX_DAILY_SHIFT_MS      = 8 * 60 * 60 * 1_000L      // 28_800_000 ms
    const val MIN_REST_BETWEEN_MS     = 4 * 60 * 60 * 1_000L      // 14_400_000 ms
    const val MAX_WEEKLY_MS           = 40 * 60 * 60 * 1_000L     // 144_000_000 ms
    const val WARN_CONTINUOUS_4H_MS   = 4 * 60 * 60 * 1_000L      // 14_400_000 ms
    const val WARN_CONTINUOUS_6H_MS   = 6 * 60 * 60 * 1_000L      // 21_600_000 ms
    const val WARN_WEEKLY_38H_MS      = 38 * 60 * 60 * 1_000L     // 136_800_000 ms
    const val WARN_WEEKLY_36H_MS      = 36 * 60 * 60 * 1_000L     // 129_600_000 ms
}
```

**Plantilla de test canónica:**

```kotlin
// Ejemplo: CheckLegalLimitsUseCaseTest.kt
@ExtendWith(CoroutineExtension::class)
class CheckLegalLimitsUseCaseTest {

    private val useCase = CheckLegalLimitsUseCase()

    @Test fun `GIVEN elapsed is below 4h WHEN check THEN no alert`() {
        val result = useCase(elapsedMs = LegalLimits.WARN_CONTINUOUS_4H_MS - 1)
        assertThat(result).isEqualTo(AlertLevel.NONE)
    }

    @Test fun `GIVEN elapsed equals exactly 4h WHEN check THEN WARNING_4H`() {
        val result = useCase(elapsedMs = LegalLimits.WARN_CONTINUOUS_4H_MS)
        assertThat(result).isEqualTo(AlertLevel.WARNING_4H)
    }

    @Test fun `GIVEN weekly total equals 40h WHEN check THEN LIMIT_40H`() {
        val result = useCase(weeklyMs = LegalLimits.MAX_WEEKLY_MS)
        assertThat(result).isEqualTo(AlertLevel.LIMIT_40H)
    }
}
```

**Prohibiciones en este Skill:**
- `Thread.sleep()` en tests (usar `TestCoroutineScheduler`)
- Tests sin nombre descriptivo con patrón `GIVEN/WHEN/THEN`
- Mockear el sistema de tiempo directamente (`System.currentTimeMillis`); inyectar un `Clock` interface

---

## 4. Convenciones de Clean Code (Kotlin)

### 4.1 Nombrado
```text
Clases/Objects  → PascalCase              → TimerService, ShiftRepository
Funciones       → camelCase, verbo        → startShift(), getWeeklySummary()
Constantes      → UPPER_SNAKE_CASE        → MAX_WEEKLY_MS
Flows/StateFlow → prefijo _private + public → _timerState / timerState
Coroutine scope → sufijo Scope            → serviceScope, viewModelScope
Booleanos       → prefijo is/has/can      → isRunning, hasActiveSession
```

### 4.2 Tipado Estricto
```kotlin
// PROHIBIDO — tipos implícitos ambiguos
val result = repository.getSession()   // ¿Qué tipo retorna?

// CORRECTO — siempre explicitar en firmas públicas
suspend fun getActiveSession(): ShiftSession?
fun getWeeklySummary(): Flow<WeeklySummary>

// PROHIBIDO — operador !! (crash garantizado en producción nocturna)
val session = repository.getActiveSession()!!

// CORRECTO — manejo explícito de nulabilidad
val session = repository.getActiveSession()
    ?: return Result.failure(NoActiveSessionException())
```

### 4.3 Estructura de Funciones
```kotlin
// Máximo de responsabilidades por función: 1
// Máximo de líneas por función: 30 (excepción justificada: máx. 50)
// Máximo de parámetros: 4 (si hay más → crear data class)

// PROHIBIDO — función con múltiples responsabilidades
fun startShiftAndUpdateUiAndScheduleAlarm() { ... }

// CORRECTO — una responsabilidad, composición limpia
suspend fun startShift(type: ShiftType): Result<ShiftSession> {
    val id = repository.startSession(type)
    alertScheduler.schedule(id)
    return Result.success(repository.getSession(id))
}
```

### 4.4 Manejo de Errores
```kotlin
// Toda operación de BD/IO retorna Result<T> en UseCase
sealed class DriverShieldError : Exception() {
    object NoActiveSession : DriverShieldError()
    object ShiftAlreadyActive : DriverShieldError()
    data class RestTooShort(val remainingMs: Long) : DriverShieldError()
    object WeeklyLimitReached : DriverShieldError()
}

// ViewModels exponen UiState, nunca excepciones crudas
sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Active(val timerState: TimerState) : DashboardUiState()
    data class Error(val error: DriverShieldError) : DashboardUiState()
    object Idle : DashboardUiState()
}
```

---

## 5. Flujo de Sesión de Desarrollo (Plan Mode)

Cada sesión sigue este protocolo sin excepción:

```text
1. DIRECTOR indica módulo y Skill activo
       │
       ▼
2. EJECUTOR lee contexto relevante del spec.md
       │
       ▼
3. EJECUTOR presenta PLAN detallado:
   - Archivos a crear/modificar/eliminar
   - Cambios específicos por archivo
   - Tests afectados
   - Riesgos detectados
       │
       ▼
4. DIRECTOR evalúa y responde:
   ├─ "OK" → EJECUTOR genera el código
   ├─ "OK con cambios: [X]" → EJECUTOR ajusta y genera
   └─ "Rechazado: [motivo]" → volver a paso 3
       │
       ▼
5. EJECUTOR entrega código + explicación de decisiones
       │
       ▼
6. DIRECTOR integra en repo local y reporta resultado
       │
       ▼
7. EJECUTOR registra decisión en sección 6 de este AGENT.md
```

---

## 6. Log de Decisiones Arquitectónicas (ADR)

*Se actualiza al finalizar cada sesión de desarrollo.*

| ID | Fecha | Decisión | Justificación | Estado |
|---|---|---|---|---|
| ADR-001 | 2026-03-29 | KSP en lugar de KAPT | 2x compilación más rápida; KAPT deprecated en Kotlin 2.x | ✅ Validado |
| ADR-002 | 2026-03-29 | DataStore para estado de sesión activa | Lectura 0ms desde el Service; Room introduce latencia de I/O | ✅ Validado |
| ADR-003 | 2026-03-29 | Ticker desde epoch absoluto | Elimina drift acumulado (~2-5ms/ciclo en `delay()`) | ✅ Validado |
| ADR-004 | 2026-03-29 | ExportRepository solo interfaz en MVP | Preserva Clean Architecture sin implementar en v1.0 | ✅ Validado |
| ADR-005 | 2026-03-29 | Sin cloud sync | Ligereza + privacidad total del conductor | ✅ Validado |
| ADR-006 | 2026-03-29 | Widget reservado en v1.1 | Espacio en `presentation/widget/`; `AppWidgetProvider` sin implementar | ✅ Validado |
| ADR-007 | 2026-04-01 | DB Room v1 creada con 2 tablas: shift_sessions + weekly_aggregates | Schema inmutable, sin DELETE físico, exportSchema=true, Hilt DI, ISO week | ✅ Validado |
| ADR-008 | 2026-04-01 | Infraestructura Gradle completada | libs.versions.toml creado, Java 17, SDK 35, AndroidX habilitado, repositorios configurados | ✅ Validado |
| ADR-009 | 2026-04-01 | UI System implementado | Tema AMOLED, LongPressButton 120dp, TimerDisplay 64sp monospace, MainScreen con resumen semanal | ✅ Validado |
| ADR-010 | 2026-04-01 | Foreground Service implementado | TimerService con WakeLock, ticker desde epoch, notificación persistente, START_STICKY, integración con Room | ✅ Validado |
| ADR-011 | 2026-04-02 | Sistema de 5 contadores + estados TRABAJANDO/EN_PAUSA | ShiftEventEntity para registro de eventos, TimerService con 5 Flows, UI con Start/Pause/Reset | ✅ Validado |
| ADR-012 | 2026-04-02 | Calendario y Horario configurables | WorkScheduleEntity, ScheduleScreen, CalendarScreen con libranzas, barra semanal ajustada a días laborables | ✅ Validado |
| ADR-013 | 2026-04-02 | Ciclo de 5 semanas para libranzas rotativas | CycleCalculator, cycleStartEpoch en WorkSchedule, DB v4, 🔴 icono para libranzas de ciclo | ✅ Validado |
| ADR-014 | 2026-04-02 | Ciclo cerrado 5 semanas: Sem 1-4 offDays usuario, Sem 5 Dom-Lun | WorkDays dinámicos por semana, aviso Domingo previo a Semana 5, barra semanal adaptativa | ✅ Validado |
| ADR-015 | 2026-04-02 | Navigation Drawer + Historial de Eventos | ModalNavigationDrawer con 4 secciones, DayReport con cronología START/PAUSE/RESUME/END, totales trabajo/descanso por día | ✅ Validado |
| ADR-016 | 2026-04-02 | Persistencia de configuración operativa | SessionDataStore: intPreferencesKey(START_HOUR/END_HOUR) + stringPreferencesKey(OFF_DAYS) con joinToString/split. ViewModel: estrategia 'Doble Combine' con Triple para superar límite de 5 parámetros de combine(). uiState alimentado directamente desde Flows de DataStore. Configuración marcada como OPERATIVA Y PERSISTENTE. | ✅ Validado |
| ADR-017 | 2026-04-04 | Resolución de reseteo de contadores en `Retomar` | Eliminación de variables base cacheadas localmente en `while` del `startUnifiedTicker`. Se añadió `baseWeeklyMs` a `TimerState` para que el acumulador semanal preserve el historial exacto previo sumando con el trabajo de la sesión actual. | ✅ Validado |

---

## 7. Permisos Requeridos (Manifest)

```xml
<!-- AndroidManifest.xml — referencia completa -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
```

---

## 8. Restricciones de Orquestación (AI-Specific)

```markdown
CONTEXTO: Si el chat supera los 15 mensajes, el EJECUTOR debe avisar al DIRECTOR para reiniciar el contexto y evitar "alucinaciones".
PRECISIÓN: Si una instrucción es ambigua, el EJECUTOR tiene prohibido "asumir"; debe preguntar.
```

---

*Fin de AGENT.md v1.0 — No modificar sin orden explícita del Director.*