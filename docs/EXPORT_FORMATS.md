# Formatos de exportación — DriverShield

DriverShield exporta los datos de jornada en dos formatos. Cada uno tiene un propósito distinto:

| Formato | Propósito | Público |
| --- | --- | --- |
| **PDF** | Evidencia legal imprimible con hash de integridad | Inspecciones de trabajo, auditorías, juicios laborales |
| **CSV** | Datos crudos para análisis externo o importación | Asesorías laborales, hojas de cálculo, sistemas de RRHH |

Ambos archivos se generan en `context.cacheDir/exports/` y se comparten con otras apps mediante `FileProvider` (no requieren permiso `WRITE_EXTERNAL_STORAGE` en Android 13+).

---

## PDF

### Cómo se genera

**Clase responsable:** `PdfExporter` ([domain/export/PdfExporter.kt](../app/src/main/java/com/drivershield/domain/export/PdfExporter.kt))

**Método de entrada:**
```kotlin
PdfExporter.export(
    context: Context,
    driver: DriverProfile,
    dayReports: List<DayReport>,  // salida de ShiftRepository.getAllSessionsWithEvents()
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    fileName: String = "DriverShield_${LocalDate.now()}.pdf"
): File
```

El archivo se escribe en `context.cacheDir/exports/<fileName>`. La carpeta se crea automáticamente si no existe.

**Dependencia de librería:** iText 7 (`com.itextpdf:kernel`, `layout`, `io`, versión 7.2.5).

---

### Estructura del archivo

```
┌──────────────────────────────────────────────────────┐
│  INFORME DE JORNADA LABORAL           (título, 18pt) │
│  Generado: 17 abril 2026 · 01/04/2026 – 30/04/2026  │
├─────────────────────┬────────────────────────────────┤
│ Conductor: Juan García │ Documento: 12345678A        │
├──────────────────────────────────────────────────────┤
│  Semana 14/04/2026 – 20/04/2026                      │
│  ┌────────────┬──────┬─────────┬─────────┬──────┬──────────────┐ │
│  │ Fecha      │ Día  │ Trabajo │Descanso │Prog. │ Alertas      │ │
│  ├────────────┼──────┼─────────┼─────────┼──────┼──────────────┤ │
│  │ 14/04/2026 │ Lun  │ 8h 5min │ 0h 30min│8h 5m │ Exc. Jornada │ │
│  │ 15/04/2026 │ Mar  │ 7h 0min │ 1h 0min │15h 5m│              │ │
│  │ ...        │ ...  │ ...     │ ...     │ ...  │              │ │
│  │ 20/04/2026 │ Dom  │ 0h 0min │ 0h 0min │ ...  │ Cierre       │ │
│  ├────────────┴──────┴─────────┴─────────┴──────┴──────────────┤ │
│  │ TOTAL SEMANA          │ 38h 0min │ 4h 30min │   │           │ │
│  └──────────────────────────────────────────────────────────────┘ │
│  ... (una tabla por semana en el rango)                          │
├──────────────────────────────────────────────────────┤
│  Verificación: A3F9B2C1D4E5F6A7   (pie, 7pt, gris)  │
└──────────────────────────────────────────────────────┘
```

---

### Columnas de la tabla semanal

| Columna | Origen en el modelo | Formato |
| --- | --- | --- |
| **Fecha** | `DayReport.date` | `dd/MM/yyyy` |
| **Día** | `DayReport.date.dayOfWeek` | Abreviatura en español (Lun, Mar…) |
| **Trabajo** | `DayReport.totalWorkMs` | `Xh Ymin` |
| **Descanso** | `DayReport.totalRestMs` | `Xh Ymin` |
| **Progresivo** | Acumulado `totalWorkMs` de lunes a ese día | `Xh Ymin` |
| **Alertas** | Calculado sobre los límites de `WorkLimits` | Texto (ver tabla de alertas) |

---

### Códigos de alerta en el PDF

| Texto en celda | Condición | Norma |
| --- | --- | --- |
| `Exc. Jornada` | `DayReport.totalWorkMs > 28_800_000` (8 h) | RDL 8/2019 |
| `Exc. Descanso` | `DayReport.totalRestMs > 14_400_000` (4 h) | Directiva 2003/88/CE |
| `Cierre` | El día es domingo (`DayOfWeek.SUNDAY`) | — |

Las celdas con alerta se resaltan en fondo rojo claro (`RGB 255, 220, 220`) y el valor infractor aparece en rojo (`RGB 180, 0, 0`). El domingo usa fondo gris (`RGB 200, 200, 200`).

> **Nota:** El flag `isTampered` de una sesión **no aparece visualmente en el PDF**.
> El PDF es el documento de evidencia legal; la alteración de reloj se registra
> exclusivamente en el CSV (columna `is_tampered`) y en la base de datos Room.
> Si un registro tampizado necesita aparecer en un documento legal, debe generarse
> el CSV complementario.

---

### Hash de verificación

El pie del documento incluye un hash SHA-256 truncado a 16 caracteres hexadecimales en mayúsculas.

**Datos que entran en el hash (en orden):**

```
{driver.fullName}|{driver.dni}|{genDate}|{date1}:{workMs1}:{restMs1}|{date2}:{workMs2}:{restMs2}|...
```

Ejemplo de cadena de entrada:
```
Juan García|12345678A|17 abril 2026|2026-04-14:29100000:1800000|2026-04-15:25200000:3600000
```

Ejemplo de hash resultante en el pie del PDF:
```
Verificación: A3F9B2C1D4E5F6A7
```

El hash permite detectar si el archivo PDF fue modificado a mano después de generarse. No es una firma criptográfica con clave privada; es una huella de consistencia interna.

---

## CSV

### Cómo se genera

**Clase responsable:** `CsvExporter` ([domain/export/CsvExporter.kt](../app/src/main/java/com/drivershield/domain/export/CsvExporter.kt))

**Método de entrada:**
```kotlin
CsvExporter.export(
    context: Context,
    driver: DriverProfile,
    dayReports: List<DayReport>,  // misma fuente que el PDF
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    fileName: String = "DriverShield_${LocalDate.now()}.csv"
): File
```

El archivo se escribe en `context.cacheDir/exports/<fileName>`, codificado en **UTF-8** con saltos de línea `\n`.

---

### Estructura del archivo

El CSV tiene tres bloques separados por líneas en blanco:

```
# DriverShield - Informe de Jornada (Datos de Gestión)
# Generado,17/04/2026
# Conductor,Juan García
# Documento,12345678A
# Periodo,01/04/2026,30/04/2026

date,day_of_week,session_id,start_timestamp,end_timestamp,duration_ms,work_ms,rest_ms,is_tampered,alerts
14/04/2026,lunes,42,1744624800000,1744653600000,28800000,26100000,1800000,false,EXC_JORNADA
15/04/2026,martes,43,1744711200000,1744736400000,25200000,25200000,0,false,
16/04/2026,miércoles,44,1744797600000,1744822800000,25200000,22500000,2700000,true,RELOJ_ALTERADO

# RESUMEN SEMANAL
week_start,week_end,total_work_ms,total_rest_ms,total_work_fmt,total_rest_fmt,excess_days
14/04/2026,20/04/2026,136800000,16200000,38h 0min,4h 30min,1
```

---

### Bloque 1 — Metadatos (líneas `#`)

Las líneas que empiezan con `#` son comentarios de metadatos. La mayoría de parsers CSV las ignoran. Contienen:

| Clave | Valor |
| --- | --- |
| `Generado` | Fecha de exportación en `dd/MM/yyyy` |
| `Conductor` | `DriverProfile.fullName` (entrecomillado si contiene comas) |
| `Documento` | `DriverProfile.dni` |
| `Periodo` | Rango solicitado: `rangeStart` y `rangeEnd` en `dd/MM/yyyy` |

---

### Bloque 2 — Sesiones (tabla principal)

Una fila por sesión. Si un día tiene dos sesiones (p. ej. turno partido), aparecen en dos filas consecutivas con la misma fecha.

| Columna | Tipo | Origen | Descripción |
| --- | --- | --- | --- |
| `date` | `String` | `DayReport.date` | Fecha en `dd/MM/yyyy` |
| `day_of_week` | `String` | `DayReport.date.dayOfWeek` | Nombre completo en español en minúsculas |
| `session_id` | `Long` | `SessionReport.sessionId` | PK de Room en `shift_sessions` |
| `start_timestamp` | `Long` | Primer evento del log | Epoch-millis UTC del primer `START_WORK` |
| `end_timestamp` | `Long` | Último evento `END_SHIFT` | Epoch-millis UTC, `0` si la sesión no está cerrada |
| `duration_ms` | `Long` | `end_timestamp - start_timestamp` | `0` si la sesión no está cerrada |
| `work_ms` | `Long` | `SessionReport.totalWorkMs` | Milisegundos de trabajo efectivo |
| `rest_ms` | `Long` | `SessionReport.totalRestMs` | Milisegundos de descanso dentro del turno |
| `is_tampered` | `Boolean` | `SessionReport.isTampered` | `true` si se detectó cambio de reloj durante la sesión |
| `alerts` | `String` | Calculado | Códigos separados por `\|` (ver tabla de alertas) |

> **Diferencia importante entre `start_timestamp` y `duration_ms`:**
> `start_timestamp` y `end_timestamp` se extraen del **log de eventos** (`shift_events`),
> no de `shift_sessions.startTimestamp`. Esto garantiza que, si la sesión fue reabierta
> o editada manualmente, los timestamps del CSV reflejan los eventos reales, no la
> cabecera de la sesión.

---

### Códigos de alerta en el CSV

| Código en columna `alerts` | Condición |
| --- | --- |
| `EXC_JORNADA` | `SessionReport.totalWorkMs > 28_800_000` (8 h) |
| `EXC_DESCANSO` | `SessionReport.totalRestMs > 14_400_000` (4 h) |
| `RELOJ_ALTERADO` | `SessionReport.isTampered == true` |

Si hay varias alertas se concatenan con `|`: `EXC_JORNADA|RELOJ_ALTERADO`.
Si no hay alertas, la celda queda vacía.

---

### Bloque 3 — Resumen semanal

Una fila por semana ISO dentro del rango exportado.

| Columna | Tipo | Descripción |
| --- | --- | --- |
| `week_start` | `String` | Lunes de la semana en `dd/MM/yyyy` |
| `week_end` | `String` | Domingo de la semana en `dd/MM/yyyy` |
| `total_work_ms` | `Long` | Suma de `totalWorkMs` de todos los días de la semana |
| `total_rest_ms` | `Long` | Suma de `totalRestMs` de todos los días de la semana |
| `total_work_fmt` | `String` | `total_work_ms` formateado como `Xh Ymin` |
| `total_rest_fmt` | `String` | `total_rest_ms` formateado como `Xh Ymin` |
| `excess_days` | `Int` | Número de días en la semana que superaron las 8 h |

---

### Escapado de valores

Los campos `Conductor` y `Documento` del bloque de metadatos se escapan según RFC 4180:
si el valor contiene una coma (`,`), una comilla doble (`"`) o un salto de línea, el campo
se encierra entre comillas dobles y las comillas internas se duplican (`""`)

Ejemplo: `García, Juan "El rápido"` → `"García, Juan ""El rápido"""`

---

## Resumen comparativo

| Característica | PDF | CSV |
| --- | --- | --- |
| Granularidad | Por día (agrupando sesiones) | Por sesión individual |
| `isTampered` visible | No | Sí (`is_tampered` + código `RELOJ_ALTERADO`) |
| Timestamps epoch | No | Sí (`start_timestamp`, `end_timestamp`) |
| Hash de integridad | Sí (SHA-256 truncado, 16 chars) | No |
| Agrupación semanal | Sí (tabla por semana + totales) | Sí (bloque resumen al final) |
| Apto para auditoría | Sí | Como complemento al PDF |
| Apto para análisis en Excel | No recomendado | Sí |
