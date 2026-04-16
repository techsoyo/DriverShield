# DriverShield

**Companion app para conductores VTC y camioneros** que automatiza el registro de jornada laboral con
precisión milimétrica, generación de evidencia inmutable y alertas legales en tiempo real.

> Cumple con el **RDL 8/2019** (registro horario en España), la **Directiva 2003/88/CE** (tiempo de trabajo)
> y el **Reglamento (CE) 561/2006** (tiempos de conducción y descanso).

---

## Visión General

| Característica | Detalle |
|---|---|
| Registro de jornada | Cronómetro de precisión con WakeLock, sobrevive a reinicios |
| Alertas legales | AlarmManager (inmune a Doze Mode) a 4h, 6h, 8h diario y 38h, 40h semanal |
| Evidencia de integridad | Flag `isTampered` si se detecta manipulación del reloj del sistema |
| Historial semanal | Agrupación ISO por semanas con acumulado progresivo |
| Exportación | PDF (iText7, estructura semanal + hash SHA-256) y CSV (datos crudos epoch) |
| Calendario | Vista semanal deslizable ±2/3 meses con grid de bloques horarios |
| Eficiencia energética | Cero escrituras a Room durante el tick; todo OLTP en memoria |

---

## Stack Tecnológico

| Capa | Tecnología |
|---|---|
| UI | Jetpack Compose · Material3 · HorizontalPager |
| Arquitectura | MVVM + Clean Architecture (domain / data / presentation) |
| Inyección de dependencias | Hilt 2.56 |
| Base de datos local | Room 2.7 (Event Sourcing ligero) |
| Persistencia de configuración | DataStore Preferences |
| Navegación | Navigation Compose 2.8 |
| Generación de PDF | iText7 (kernel + layout + io) |
| Concurrencia | Kotlin Coroutines · StateFlow · Flow |
| Testing | JUnit 5 · MockK · Turbine · Robolectric |
| Build | Gradle Kotlin DSL · KSP · AGP 8.5 |
| Min SDK | 26 (Android 8.0) |
| Compile SDK | 35 (Android 15) |
| Target SDK | 35 (Android 15) |

---

## Requisitos Previos

- **Android Studio** Hedgehog (2023.1.1) o superior
- **JDK 17**
- **Android SDK** con API 35 instalada
- Dispositivo o emulador con API ≥ 26

---

## Instalación y Ejecución

```bash
# 1. Clonar el repositorio
git clone https://github.com/tu-org/DriverShield.git
cd DriverShield

# 2. Build de debug
./gradlew assembleDebug

# 3. Instalar en dispositivo conectado por ADB
./gradlew installDebug

# 4. Ejecutar todos los tests unitarios
./gradlew test

# 5. Ejecutar un test concreto
./gradlew test --tests "com.drivershield.domain.usecase.CheckLegalLimitsUseCaseTest"

# 6. Lint
./gradlew lint

# 7. Build de release (requiere keystore configurado en local.properties)
./gradlew assembleRelease
```

### Configuración de Keystore (release)

Añadir en `local.properties` (no commitar):

```properties
RELEASE_STORE_FILE=../keystore/drivershield.jks
RELEASE_STORE_PASSWORD=tu_password
RELEASE_KEY_ALIAS=drivershield
RELEASE_KEY_PASSWORD=tu_key_password
```

---

## Mapa del Proyecto

```
app/src/main/java/com/drivershield/
│
├── data/                              # Capa de datos
│   ├── local/
│   │   ├── db/
│   │   │   ├── dao/
│   │   │   │   ├── ShiftDao.kt        # CRUD sesiones + @Transaction endSession
│   │   │   │   ├── ShiftEventDao.kt   # Log de eventos append-only
│   │   │   │   ├── WeeklyAggregateDao.kt
│   │   │   │   └── WorkScheduleDao.kt
│   │   │   ├── entity/
│   │   │   │   ├── ShiftSessionEntity.kt   # isTampered, isoYear, isoWeekNumber
│   │   │   │   └── ShiftEventEntity.kt     # timestamp + elapsedRealtime
│   │   │   └── AppDatabase.kt         # Room v7, migraciones 1→7
│   │   └── datastore/
│   │       └── SessionDataStore.kt    # Estado volátil del turno activo
│   └── repository/
│       └── impl/
│           └── ShiftRepositoryImpl.kt # getAllSessionsWithEvents() → List<DayReport>
│
├── domain/                            # Lógica de negocio pura (sin Android)
│   ├── export/
│   │   ├── PdfExporter.kt             # iText7, tablas semanales, hash SHA-256
│   │   └── CsvExporter.kt             # Epoch timestamps, resumen semanal
│   ├── model/
│   │   ├── DayReport.kt
│   │   ├── SessionReport.kt
│   │   └── DriverProfile.kt
│   ├── repository/
│   │   └── ShiftRepository.kt         # Interfaz (inversión de dependencias)
│   └── util/
│       ├── WorkLimits.kt              # Constantes y validadores legales
│       └── CycleCalculator.kt         # Cálculo de ciclos de libranza
│
├── presentation/                      # Capa UI (Compose + ViewModels)
│   ├── screen/
│   │   ├── main/
│   │   │   ├── DriverShieldApp.kt     # NavHost + DrawerLayout
│   │   │   └── MainScreen.kt          # Dashboard con contadores en tiempo real
│   │   ├── calendar/
│   │   │   ├── CalendarScreen.kt      # HorizontalPager, 23 semanas deslizables
│   │   │   └── CalendarViewModel.kt   # selectedPage, weekOffset
│   │   ├── history/
│   │   │   ├── HistoryScreen.kt       # Acordeón semanal, filtro por rango
│   │   │   └── HistoryViewModel.kt    # WeekSummary, DayProgressive, isTampered
│   │   ├── export/
│   │   │   ├── ExportScreen.kt        # Campos de fecha, botones PDF/CSV
│   │   │   └── ExportViewModel.kt     # ExportState, FileProvider intent
│   │   └── schedule/
│   │       └── ScheduleScreen.kt      # Configuración de horarios
│   └── theme/
│       └── DriverShieldColors.kt      # Paleta AMOLED (#000000 background)
│
└── service/                           # Servicios background
    ├── TimerService.kt                # Foreground service, WakeLock, ticker 1s
    ├── BootReceiver.kt                # Restaura turno activo tras reinicio
    ├── TimerStateManager.kt           # StateFlow en memoria (cero I/O en tick)
    └── notification/
        ├── AlertScheduler.kt          # setAlarmClock (inmune a Doze)
        └── AlertReceiver.kt           # Maneja las alarmas legales disparadas
```

---

## Permisos de Android

| Permiso | Justificación |
|---|---|
| `FOREGROUND_SERVICE` | Mantener el cronómetro activo |
| `FOREGROUND_SERVICE_SPECIAL_USE` | API 34: categoría de servicio primero plano |
| `WAKE_LOCK` | Evitar suspensión de CPU durante el turno |
| `RECEIVE_BOOT_COMPLETED` | Restaurar turno activo tras reinicio |
| `SCHEDULE_EXACT_ALARM` | Alarmas legales exactas (Doze-exempt) |
| `REQUEST_INSTALL_PACKAGES` | Actualizaciones in-app (si aplica) |
| `WRITE/READ_EXTERNAL_STORAGE` | Exportación en Android ≤ 9/12 |

---

## Modelo de Datos Simplificado

```
shift_sessions (cabecera del turno)
  id, startTimestamp, endTimestamp, durationMs,
  isoYear, isoWeekNumber, type, isTampered

shift_events (log inmutable append-only)
  id, sessionId → shift_sessions.id (CASCADE),
  eventType, timestamp, elapsedRealtime, isSystemTimeReliable
```

---

## Conventional Commits

El proyecto usa [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: nueva funcionalidad
fix: corrección de bug
chore: tareas de build/mantenimiento sin cambio de lógica
docs: documentación
refactor: refactorización sin cambio de comportamiento
test: añadir o modificar tests
```

El archivo `CHANGELOG.md` se genera automáticamente por el workflow de CI/CD en cada release.

---

## Licencia

Propietaria · © 2026 DriverShield Team · Todos los derechos reservados.
