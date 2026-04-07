# SKILL: Data Guardian
> Versión: 1.0 · Proyecto: DriverShield
> Carga este skill SOLO cuando trabajes en: `data/`, `domain/model/`, `domain/repository/`
> No cargar para Service, UI o tests.

---

## Dominio de este Skill

Room 2.7, DataStore Preferences, RepositoryImpl, mappers, semana ISO.
Objetivo único: los datos de turnos son evidencia legal. Una sesión corrupta puede costar una multa al conductor. Toda escritura es atómica.

---

## Regla de Oro

> **Nunca se elimina un registro de turno.** El historial es inmutable.
> Los registros se marcan, no se borran (soft delete reservado para v2).
> Si hay duda entre velocidad y integridad: siempre integridad.

---

## Contratos de Repository (Interfaces de Dominio)

```kotlin
// domain/repository/ShiftRepository.kt
interface ShiftRepository {
    suspend fun startSession(type: ShiftType): Long       // Retorna ID insertado
    suspend fun endSession(id: Long): ShiftSession        // Retorna sesión cerrada
    fun getActiveSession(): Flow<ShiftSession?>
    fun getSessionsByWeek(isoYear: Int, week: Int): Flow<List<ShiftSession>>
    suspend fun getWeeklyWorkMs(isoYear: Int, week: Int): Long
}

// domain/repository/ExportRepository.kt ← SOLO INTERFAZ en MVP
interface ExportRepository {
    suspend fun exportToCsv(isoYear: Int, week: Int): Uri
    suspend fun exportToPdf(isoYear: Int, week: Int): Uri
}
// NOTA: ExportRepositoryImpl NO se implementa en v1.0
// La interfaz existe para no romper Clean Architecture cuando se añada en v1.1
```

---

## Reglas de Zona Horaria (Crítico)

```kotlin
// CORRECTO: Siempre UTC en BD, convertir solo en presentación
val epochUtcMs: Long = System.currentTimeMillis()

// CORRECTO: Semana ISO — nunca Calendar.WEEK_OF_YEAR
val weekFields = WeekFields.ISO
val now = LocalDate.now()
val isoWeek = now.get(weekFields.weekOfWeekBasedYear())
val isoYear = now.get(weekFields.weekBasedYear())  // ← isoYear ≠ year en semanas dic/ene

// PROHIBIDO
val cal = Calendar.getInstance()
val week = cal.get(Calendar.WEEK_OF_YEAR)  // ← Roto en semanas que cruzan año
```

---

## Reglas de DataStore vs Room

| Dato | Dónde va | Por qué |
|---|---|---|
| `startEpoch` de sesión activa | DataStore | Latencia 0ms desde el Service |
| `weeklyAccumulatedMs` (cache) | DataStore | No necesita query en cada tick |
| Histórico de turnos | Room | Persistencia, queries complejas |
| Agregados semanales | Room | Integridad referencial |

**Regla:** El DataStore persiste `startEpoch` ANTES de confirmar en la UI. Si el proceso muere entre el INSERT de Room y la actualización del DataStore, el Service puede recuperar el estado al reiniciar.

---

## Workflow de Escritura Atómica

```
Usuario pulsa "Iniciar turno"
    │
    ▼
1. DataStore.set(active_session_id = PENDING)    ← Primero
2. Room.insert(ShiftSessionEntity)               ← Segundo
3. DataStore.set(active_session_id = realId)     ← Confirmar ID
4. DataStore.set(service_start_epoch = now)
    │
    ▼
SI el proceso muere entre pasos 1 y 3:
    → Service.onStartCommand lee DataStore
    → Detecta PENDING → consulta Room por sesión sin endTimestamp
    → Reanuda desde startEpoch real
```

---

## Reglas de DAO

```kotlin
// REGLA: Toda función DAO es suspend o retorna Flow<T>
// REGLA: Nunca runBlocking en DAO o Repository

@Dao
interface ShiftDao {
    @Insert
    suspend fun insert(session: ShiftSessionEntity): Long

    @Update
    suspend fun update(session: ShiftSessionEntity)

    // Flow para UI reactiva
    @Query("SELECT * FROM shift_sessions WHERE endTimestamp IS NULL LIMIT 1")
    fun getActiveSessionFlow(): Flow<ShiftSessionEntity?>

    // suspend para uso desde Service
    @Transaction
    suspend fun endSession(id: Long, endTs: Long, durationMs: Long) {
        val session = getById(id) ?: return
        update(session.copy(endTimestamp = endTs, durationMs = durationMs))
    }

    @Query("""
        SELECT SUM(durationMs) FROM shift_sessions
        WHERE isoYear = :year AND isoWeekNumber = :week AND type = 'WORK'
    """)
    fun getWeeklyWorkMs(year: Int, week: Int): Flow<Long?>
}
```

---

## Reglas de Migración

```kotlin
// SIEMPRE definir Migration antes de cambiar el schema
// NUNCA usar fallbackToDestructiveMigration en producción

@Database(
    entities = [ShiftSessionEntity::class, WeeklyAggregateEntity::class],
    version = 1,
    exportSchema = true  // ← true para auditoría de migraciones
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Definir antes de incrementar version
            }
        }
    }
}
```

---

## Checklist de Validación (antes de PR)

- [ ] Toda función DAO es `suspend` o retorna `Flow<T>`
- [ ] Transacciones multi-tabla usan `@Transaction`
- [ ] `isoWeekNumber` calculado con `WeekFields.ISO`
- [ ] Timestamps almacenados en UTC (epoch ms)
- [ ] `Migration` definida antes de cambiar el schema
- [ ] `exportSchema = true` en `@Database`
- [ ] DataStore persiste `startEpoch` antes de confirmar UI
- [ ] NO hay `runBlocking` en código de producción
- [ ] NO hay DELETE físico de registros de turno

---

## Prohibiciones Absolutas

```
❌ runBlocking en código de producción
❌ DELETE físico de registros de turno
❌ Timestamps en zona local almacenados en BD
❌ Calendar.WEEK_OF_YEAR (usar WeekFields.ISO)
❌ SharedPreferences (reemplazado por DataStore)
❌ fallbackToDestructiveMigration en producción
❌ exportSchema = false
```

---

## Archivos que modifica este Skill

```
app/src/main/java/com/drivershield/
├── data/
│   ├── local/db/
│   │   ├── AppDatabase.kt          ← OWNER
│   │   ├── dao/ShiftDao.kt         ← OWNER
│   │   ├── dao/WeeklyAggregateDao.kt ← OWNER
│   │   └── entity/
│   │       ├── ShiftSessionEntity.kt ← OWNER
│   │       └── WeeklyAggregateEntity.kt ← OWNER
│   ├── local/datastore/
│   │   └── SessionDataStore.kt     ← OWNER
│   └── repository/
│       ├── ShiftRepositoryImpl.kt  ← OWNER
│       └── WeeklyRepositoryImpl.kt ← OWNER
└── domain/
    ├── model/
    │   ├── ShiftSession.kt
    │   ├── ShiftType.kt
    │   └── WeeklySummary.kt
    └── repository/
        ├── ShiftRepository.kt      ← OWNER (interfaz)
        └── ExportRepository.kt     ← OWNER (interfaz, sin impl en v1.0)
```

*Fin de SKILL: data-guardian.md*