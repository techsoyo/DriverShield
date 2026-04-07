# SKILL: QA Tester
> Versión: 1.0 · Proyecto: DriverShield
> Carga este skill SOLO cuando trabajes en: `test/`, `androidTest/`
> No cargar para Service, UI o BD de producción.

---

## Dominio de este Skill

JUnit 5, Turbine, Robolectric, Room in-memory, MockK, lógica de límites legales.
Objetivo único: la ley no admite errores de redondeo. 8h son 28_800_000 ms exactos.

---

## Filosofía de Testing en DriverShield

> Los tests NO son opcionales cuando la IA genera el código.
> El código generado por IA puede "funcionar" pero estar incorrecto.
> Los tests son la única garantía de que el conductor no recibe una multa por un bug.

**El desarrollador define los escenarios. La IA puede escribir el código del test. Nunca al revés.**

---

## Constantes Legales de Referencia

```kotlin
// core/utils/Constants.kt — LA VERDAD ÚNICA
object LegalLimits {
    const val MAX_DAILY_SHIFT_MS      = 8  * 60 * 60 * 1_000L  // 28_800_000 ms
    const val MIN_REST_BETWEEN_MS     = 4  * 60 * 60 * 1_000L  // 14_400_000 ms
    const val MAX_WEEKLY_MS           = 40 * 60 * 60 * 1_000L  // 144_000_000 ms
    const val WARN_CONTINUOUS_4H_MS   = 4  * 60 * 60 * 1_000L  // 14_400_000 ms
    const val WARN_CONTINUOUS_6H_MS   = 6  * 60 * 60 * 1_000L  // 21_600_000 ms
    const val WARN_WEEKLY_38H_MS      = 38 * 60 * 60 * 1_000L  // 136_800_000 ms
    const val WARN_WEEKLY_36H_MS      = 36 * 60 * 60 * 1_000L  // 129_600_000 ms
}
```

---

## Plantilla Canónica de Test (patrón GIVEN/WHEN/THEN)

```kotlin
// Nombre siempre: `GIVEN ... WHEN ... THEN ...`
@Test
fun `GIVEN elapsed is exactly 4h WHEN check THEN WARNING_4H emitted`() {
    // Arrange
    val useCase = CheckLegalLimitsUseCase()
    val exactLimit = LegalLimits.WARN_CONTINUOUS_4H_MS

    // Act
    val result = useCase(elapsedMs = exactLimit)

    // Assert
    assertThat(result).isEqualTo(AlertLevel.WARNING_4H)
}
```

---

## Suite de Tests Obligatorios por Módulo

### CheckLegalLimitsUseCase

```kotlin
@ExtendWith(CoroutineExtension::class)
class CheckLegalLimitsUseCaseTest {

    private val useCase = CheckLegalLimitsUseCase()

    // --- 4h de conducción continua ---
    @Test fun `GIVEN elapsed below 4h WHEN check THEN NONE`() {
        assertThat(useCase(elapsedMs = LegalLimits.WARN_CONTINUOUS_4H_MS - 1))
            .isEqualTo(AlertLevel.NONE)
    }

    @Test fun `GIVEN elapsed equals exactly 4h WHEN check THEN WARNING_4H`() {
        assertThat(useCase(elapsedMs = LegalLimits.WARN_CONTINUOUS_4H_MS))
            .isEqualTo(AlertLevel.WARNING_4H)
    }

    @Test fun `GIVEN elapsed above 4h below 6h WHEN check THEN WARNING_4H`() {
        assertThat(useCase(elapsedMs = LegalLimits.WARN_CONTINUOUS_4H_MS + 1))
            .isEqualTo(AlertLevel.WARNING_4H)
    }

    // --- 8h límite diario ---
    @Test fun `GIVEN elapsed equals 8h WHEN check THEN LIMIT_8H`() {
        assertThat(useCase(elapsedMs = LegalLimits.MAX_DAILY_SHIFT_MS))
            .isEqualTo(AlertLevel.LIMIT_8H)
    }

    @Test fun `GIVEN elapsed exceeds 8h WHEN check THEN LIMIT_8H`() {
        assertThat(useCase(elapsedMs = LegalLimits.MAX_DAILY_SHIFT_MS + 1))
            .isEqualTo(AlertLevel.LIMIT_8H)
    }

    // --- 40h semanales ---
    @Test fun `GIVEN weekly total below 38h WHEN check THEN NONE`() {
        assertThat(useCase(weeklyMs = LegalLimits.WARN_WEEKLY_38H_MS - 1))
            .isEqualTo(AlertLevel.NONE)
    }

    @Test fun `GIVEN weekly total equals 38h WHEN check THEN WARN_WEEKLY_38H`() {
        assertThat(useCase(weeklyMs = LegalLimits.WARN_WEEKLY_38H_MS))
            .isEqualTo(AlertLevel.WARNING_38H)
    }

    @Test fun `GIVEN weekly total equals 40h WHEN check THEN LIMIT_40H`() {
        assertThat(useCase(weeklyMs = LegalLimits.MAX_WEEKLY_MS))
            .isEqualTo(AlertLevel.LIMIT_40H)
    }
}
```

### StartShiftUseCase

```kotlin
class StartShiftUseCaseTest {

    @Test fun `GIVEN no active session WHEN start THEN session created`() { }

    @Test fun `GIVEN active session exists WHEN start THEN ShiftAlreadyActive error`() { }

    @Test fun `GIVEN rest too short WHEN start THEN RestTooShort error with remaining ms`() { }

    @Test fun `GIVEN weekly limit reached WHEN start THEN WeeklyLimitReached error`() { }
}
```

### Room (in-memory, con Robolectric)

```kotlin
@RunWith(RobolectricTestRunner::class)
class ShiftDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ShiftDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.shiftDao()
    }

    @After fun teardown() { db.close() }

    @Test fun `GIVEN empty db WHEN getActiveSession THEN null`() = runTest {
        assertThat(dao.getActiveSession()).isNull()
    }

    @Test fun `GIVEN open session WHEN getActiveSession THEN returns session`() = runTest {
        dao.insert(ShiftSessionEntity(startTimestamp = 1000L, type = ShiftType.WORK, ...))
        assertThat(dao.getActiveSession()).isNotNull()
    }

    @Test fun `GIVEN ended session WHEN getActiveSession THEN null`() = runTest { }
}
```

### Flow con Turbine

```kotlin
@Test fun `GIVEN timer running WHEN collect THEN emits elapsed state`() = runTest {
    val stateFlow = timerService.timerState

    stateFlow.test {
        val initial = awaitItem()
        assertThat(initial.phase).isEqualTo(TimerPhase.IDLE)

        timerService.startShift()
        val active = awaitItem()
        assertThat(active.phase).isEqualTo(TimerPhase.WORK)

        cancelAndIgnoreRemainingEvents()
    }
}
```

---

## Prioridades de Testing

| Categoría | Prioridad | Ejemplos |
|---|---|---|
| Límites legales exactos | 🔴 Crítico | 4h, 6h, 8h, 38h, 40h exactos |
| Autenticación / autorización | 🔴 Crítico | ¿quién puede iniciar sesión? |
| Puntos de integración | 🔴 Crítico | Room ↔ DataStore consistencia |
| Edge cases de negocio | 🟡 Importante | Turno que cruza medianoche |
| Happy path de funciones simples | 🟢 Delegable a IA | startShift con estado limpio |

---

## Reglas de Testing

```
✅ Nombres con GIVEN/WHEN/THEN siempre
✅ Tests de tiempo usan TestCoroutineScheduler (no Thread.sleep)
✅ Room tests usan inMemoryDatabaseBuilder
✅ Flow tests usan Turbine (.test { })
✅ Cada límite legal tiene: límite-1ms, límite exacto, límite+1ms
✅ Clock inyectado como interface (no System.currentTimeMillis directo)

❌ Thread.sleep() en tests
❌ Tests sin nombre descriptivo
❌ Mockear System.currentTimeMillis directamente
❌ Más de 1 assertion por test (salvo casos compuestos justificados)
```

---

## Inyección de Clock (Patrón)

```kotlin
// Interfaz para inyectar en producción y en tests
interface AppClock {
    fun nowMs(): Long
}

class RealClock : AppClock {
    override fun nowMs() = System.currentTimeMillis()
}

class FakeClock(private var currentMs: Long = 0L) : AppClock {
    override fun nowMs() = currentMs
    fun advanceBy(ms: Long) { currentMs += ms }
}

// En tests
val clock = FakeClock(startMs = 1_000_000L)
val useCase = StartShiftUseCase(repository = fakeRepo, clock = clock)
clock.advanceBy(LegalLimits.MAX_DAILY_SHIFT_MS)
// Ahora el test controla el tiempo sin delay real
```

---

## Archivos que modifica este Skill

```
app/src/test/java/com/drivershield/
├── usecase/
│   ├── CheckLegalLimitsUseCaseTest.kt  ← OWNER
│   ├── StartShiftUseCaseTest.kt        ← OWNER
│   ├── EndShiftUseCaseTest.kt          ← OWNER
│   └── GetWeeklySummaryUseCaseTest.kt  ← OWNER
└── repository/
    └── ShiftDaoTest.kt                 ← OWNER

app/src/androidTest/java/com/drivershield/
└── service/
    └── TimerServiceTest.kt             ← OWNER
```

*Fin de SKILL: qa-tester.md*