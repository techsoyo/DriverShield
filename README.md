# DriverShield

![CI](https://github.com/ebAutomationAi/DriverShield/actions/workflows/ci.yml/badge.svg)
![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Min SDK](https://img.shields.io/badge/min%20SDK-26-green)
![License](https://img.shields.io/badge/license-Proprietary-red)

Registro de jornada laboral para conductores VTC y camioneros. Cronómetro persistente con alertas legales en tiempo real y exportación de evidencia.

Cumple con el **RDL 8/2019** (registro horario en España), la **Directiva 2003/88/CE** (tiempo de trabajo) y el **Reglamento (CE) 561/2006** (tiempos de conducción y descanso).

---

## Qué problema resuelve

Los conductores profesionales están obligados por ley a registrar su jornada con precisión. Una app genérica de temporizador no detecta manipulaciones de reloj, no genera evidencia legal válida ni avisa cuando se acerca a los límites legales (8h/día, 40h/semana). DriverShield automatiza todo eso: registra con `WakeLock` para sobrevivir en segundo plano, marca el turno como `isTampered` si se manipula el reloj del sistema, y exporta un PDF firmado con hash SHA-256.

---

## Requisitos previos

- **Android Studio** Meerkat (2024.3.1) o superior
- **JDK 17** (el proyecto usa `jvmTarget = "17"`)
- **Android SDK** con API 35 instalada (compileSdk = 35)
- Dispositivo físico o emulador con **API ≥ 26** (Android 8.0)
- **Git** instalado en el sistema

---

## Instalación paso a paso

```bash
# 1. Clonar el repositorio
git clone https://github.com/ebAutomationAi/DriverShield.git
cd DriverShield

# 2. Compilar el APK de debug
./gradlew assembleDebug

# 3. Instalar en el dispositivo conectado por ADB
./gradlew installDebug

# 4. Abrir la app en el dispositivo
adb shell am start -n com.drivershield/.presentation.screen.main.MainActivity
```

> **Windows:** usar `gradlew.bat` en lugar de `./gradlew` si no tienes Git Bash.

### Build de release (requiere keystore)

Añadir en `local.properties` (no commitear este archivo):

```properties
RELEASE_STORE_FILE=../keystore/drivershield.jks
RELEASE_STORE_PASSWORD=tu_password
RELEASE_KEY_ALIAS=drivershield
RELEASE_KEY_PASSWORD=tu_key_password
```

```bash
./gradlew assembleRelease
```

---

## Cómo ejecutar los tests

```bash
# Todos los tests unitarios (77 tests, ~15 segundos)
./gradlew testDebugUnitTest

# Un test concreto
./gradlew testDebugUnitTest --tests "com.drivershield.domain.usecase.StartShiftUseCaseTest"

# Análisis estático (lint)
./gradlew lintDebug

# Lint + tests en un solo comando (mismo orden que CI)
./gradlew lintDebug testDebugUnitTest
```

Los resultados de los tests se generan en:
`app/build/reports/tests/testDebugUnitTest/index.html`

---

## Variables de entorno

Esta aplicación no requiere ninguna variable de entorno. Es **100% offline** — no hay servidor, no hay API key, no hay telemetría. Todos los datos se almacenan en la base de datos Room local del dispositivo.

| Variable     | Requerida | Propósito                                    |
| ------------ | --------- | -------------------------------------------- |
| _(ninguna)_  | —         | La app funciona sin configuración de entorno |

> Para CI/CD, el workflow de Release Please usa `GITHUB_TOKEN` (inyectado automáticamente por GitHub Actions — no hay que configurarlo).

---

## Política de versiones

Versionado semántico automático gestionado por [Release Please](https://github.com/googleapis/release-please).

| Tipo de commit | Impacto en versión | Ejemplo |
| --- | --- | --- |
| `feat:` | `MINOR` (0.x.0 a 0.x+1.0) | Nueva pantalla de estadísticas |
| `fix:` | `PATCH` (0.0.x a 0.0.x+1) | Corrección de timer al reiniciar |
| `feat!:` o `BREAKING CHANGE:` | `MAJOR` (x.0.0 a x+1.0.0) | Cambio de esquema de BD incompatible |
| `chore:`, `docs:`, `test:` | Sin cambio de versión | Actualización de dependencias |

El `CHANGELOG.md` y la etiqueta de release se generan automáticamente en cada merge a `main` que incluya commits de tipo `feat` o `fix`.

---

## Árbol de archivos

```text
app/src/main/java/com/drivershield/
│
├── core/                              # Utilidades y extensiones transversales
│   ├── di/                            # Módulo Hilt de utilidades comunes
│   ├── extensions/                    # Extensiones Kotlin (Context, Flow, etc.)
│   ├── model/                         # Modelos compartidos entre capas
│   └── utils/                         # Helpers de fecha, formato, etc.
│
├── data/                              # Capa de datos (Room + DataStore)
│   ├── local/
│   │   ├── db/
│   │   │   ├── dao/                   # ShiftDao, ShiftEventDao, WeeklyAggregateDao,
│   │   │   │                          # WorkScheduleDao, DayOverrideDao
│   │   │   ├── entity/                # ShiftSessionEntity, ShiftEventEntity,
│   │   │   │                          # WeeklyAggregateEntity, WorkScheduleEntity,
│   │   │   │                          # DayOverrideEntity
│   │   │   ├── migration/             # Migraciones Room v1→v7
│   │   │   └── AppDatabase.kt         # Room singleton, version 7
│   │   └── datastore/
│   │       └── SessionDataStore.kt    # Estado volátil del turno activo (cero I/O)
│   └── repository/
│       └── impl/                      # ShiftRepositoryImpl, ScheduleRepositoryImpl,
│                                      # DayOverrideRepositoryImpl
│
├── di/                                # Módulos Hilt de toda la app
│   ├── DatabaseModule.kt              # Provee AppDatabase y todos los DAOs
│   └── RepositoryModule.kt            # Vincula interfaces → implementaciones
│
├── domain/                            # Lógica de negocio pura (sin dependencias Android)
│   ├── export/
│   │   ├── PdfExporter.kt             # iText7, tablas semanales, hash SHA-256
│   │   └── CsvExporter.kt             # Epoch timestamps, datos crudos
│   ├── model/                         # ShiftSession, ShiftType, WeeklyReport,
│   │                                  # DailyReport, DayOverride, WorkSchedule
│   ├── repository/                    # Interfaces: ShiftRepository,
│   │                                  # ScheduleRepository, DayOverrideRepository
│   ├── usecase/                       # StartShiftUseCase, EndShiftUseCase,
│   │                                  # GenerateReportUseCase, ToggleDayOverrideUseCase
│   └── util/
│       ├── WorkLimits.kt              # Constantes legales (8h/día, 40h/semana)
│       ├── CycleCalculator.kt         # Cálculo de semanas ISO
│       ├── NightShiftSplitter.kt      # Redistribuye horas nocturnas al día correcto
│       └── TimeConverter.kt           # Conversión epoch ↔ LocalDate / Instant
│
├── presentation/                      # Capa UI (Compose + ViewModels)
│   ├── component/                     # CasioTimerBox, LongPressButton, DigitalDisplay
│   ├── navigation/                    # NavHost, rutas, bottom navigation
│   ├── screen/
│   │   ├── main/                      # Dashboard: timer, contadores trabajo/descanso
│   │   ├── calendar/                  # Pager 23 semanas, toggle de libranzas
│   │   ├── history/                   # Acordeón semanal, advertencia isTampered
│   │   ├── export/                    # Selector de rango, botones PDF/CSV
│   │   └── schedule/                  # Configuración de horario y días libres
│   └── theme/
│       ├── Color.kt                   # Paleta AMOLED + paleta Casio LCD
│       ├── Theme.kt                   # MaterialTheme dark con colores Casio
│       ├── CasioFont.kt               # FontFamily Digital7 (7 segmentos)
│       └── Type.kt                    # Tipografía Material3
│
└── service/                           # Servicios background
    ├── TimerService.kt                # Foreground Service, WakeLock, ticker 1s
    ├── BootReceiver.kt                # Restaura turno activo tras reinicio
    ├── TimerStateManager.kt           # StateFlow en memoria (cero I/O en tick)
    └── notification/
        ├── AlertScheduler.kt          # AlarmManager exacto, inmune a Doze Mode
        ├── AlertReceiver.kt           # Maneja alarmas legales (4h, 6h, 8h, 38h, 40h)
        └── RotationReminderReceiver.kt # Recordatorios de rotación (reservado v2)
```

---

## Permisos de Android

| Permiso | Justificación |
| --- | --- |
| `FOREGROUND_SERVICE` | Mantener el cronómetro activo en segundo plano |
| `FOREGROUND_SERVICE_DATA_SYNC` | Tipo requerido en API 34+ para servicios de datos |
| `WAKE_LOCK` | Evitar que la CPU entre en suspensión durante el turno |
| `RECEIVE_BOOT_COMPLETED` | Restaurar turno activo tras reinicio del dispositivo |
| `SCHEDULE_EXACT_ALARM` | Alarmas legales exactas (no afectadas por Doze Mode) |
| `USE_EXACT_ALARM` | Alternativa a `SCHEDULE_EXACT_ALARM` para ciertos fabricantes |
| `POST_NOTIFICATIONS` | Notificación persistente del timer (requerido Android 13+) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Solicitar excepción de batería al usuario |
| `WRITE/READ_EXTERNAL_STORAGE` | Exportación de archivos en Android ≤ 12 |

---

## Conventional Commits

```
feat:     nueva funcionalidad        → incrementa versión MINOR
fix:      corrección de bug          → incrementa versión PATCH
feat!:    cambio incompatible        → incrementa versión MAJOR
chore:    mantenimiento/build        → sin cambio de versión
docs:     documentación              → sin cambio de versión
refactor: sin cambio de lógica       → sin cambio de versión
test:     añadir o modificar tests   → sin cambio de versión
perf:     mejora de rendimiento      → sin cambio de versión
```

El `CHANGELOG.md` se genera automáticamente por Release Please en cada merge a `main`.

---

## Documentación adicional

| Documento | Descripción |
| --- | --- |
| [docs/EXPORT_FORMATS.md](docs/EXPORT_FORMATS.md) | Estructura y campos de los archivos PDF y CSV exportados |
| [spec.md](spec.md) | Especificación técnica completa: arquitectura, modelo de datos, decisiones de diseño |

---

## Licencia

Propietaria · © 2026 DriverShield · Todos los derechos reservados.
