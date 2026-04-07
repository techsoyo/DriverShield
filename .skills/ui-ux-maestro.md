# SKILL: UI/UX Maestro
> Versión: 1.0 · Proyecto: DriverShield
> Carga este skill SOLO cuando trabajes en: `presentation/`, `theme/`, `component/`
> No cargar para Service, BD o tests.

---

## Dominio de este Skill

Jetpack Compose, Material 3 AMOLED, accesibilidad para conducción nocturna.
Objetivo único: el conductor usa la app con un ojo en la carretera, a las 3 AM, con lluvia. Cada toque debe ser infalible.

---

## Sistema de Diseño Canónico

### Paleta AMOLED (theme/Color.kt)

```kotlin
object DriverShieldColors {
    // Fondos — negro puro OLED (ahorra batería + reduce fatiga visual nocturna)
    val AmoledBlack  = Color(0xFF000000)  // Fondo app principal
    val Surface      = Color(0xFF0D0D0D)  // Cards y contenedores
    val SurfaceHigh  = Color(0xFF1A1A1A)  // Superficies elevadas (dialogs)

    // Texto
    val OnSurface    = Color(0xFFE0E0E0)  // Texto primario (alto contraste)
    val OnSurfaceMid = Color(0xFF9E9E9E)  // Texto secundario

    // Acciones
    val Accent       = Color(0xFF00E5FF)  // Cian eléctrico — acciones primarias
    val AccentDim    = Color(0xFF006978)  // Accent en estado deshabilitado

    // Estados
    val WorkGreen    = Color(0xFF00C853)  // Turno activo
    val RestAmber    = Color(0xFFFFB300)  // Descanso / advertencia
    val DangerRed    = Color(0xFFFF1744)  // Límite alcanzado / error

    // Divisores
    val Divider      = Color(0xFF2A2A2A)
}
```

**Regla crítica:** El fondo DEBE ser `Color(0xFF000000)` (negro puro). `#0D0D0D` NO es negro AMOLED — los píxeles OLED solo se apagan con `#000000` exacto.

### Tamaños táctiles (TouchTargets.kt)

```kotlin
object TouchTargets {
    val Minimum    = 48.dp   // Elementos secundarios (Material mínimo)
    val Primary    = 56.dp   // Botones de acción principal
    val Critical   = 72.dp   // Iniciar / Pausar turno
    val LongPress  = 80.dp   // Botón finalizar (anti-error)
}
```

**Regla crítica:** Ningún elemento interactivo puede estar por debajo de 48dp. El conductor lleva guantes o tiene las manos en el volante.

---

## Componentes Críticos

### TimerDisplay (Contador principal)

```kotlin
// Mínimo 64sp — legible desde el soporte del móvil en el coche
@Composable
fun TimerDisplay(
    elapsedMs: Long,
    phase: TimerPhase,
    modifier: Modifier = Modifier
) {
    val color = when (phase) {
        TimerPhase.WORK  -> DriverShieldColors.WorkGreen
        TimerPhase.REST  -> DriverShieldColors.RestAmber
        TimerPhase.IDLE  -> DriverShieldColors.OnSurfaceMid
    }
    Text(
        text = elapsedMs.toHHMMSS(),
        style = TextStyle(
            fontFamily = FontFamily.Monospace,  // monoespaciado para evitar saltos
            fontSize = 72.sp,                   // nunca menos de 64sp
            fontWeight = FontWeight.Bold,
            color = color
        ),
        modifier = modifier
    )
}
```

### LongPressButton (Botón anti-error para finalizar turno)

```kotlin
// 1500ms de pulsación para confirmar — sin diálogo adicional
// El LongPress ES la confirmación (no añadir modal encima)
@Composable
fun LongPressButton(
    onConfirm: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableStateOf(0f) }
    val requiredDurationMs = 1500L

    Box(
        modifier = modifier
            .size(TouchTargets.LongPress)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { _ ->
                        val start = System.currentTimeMillis()
                        tryAwaitRelease()
                        val held = System.currentTimeMillis() - start
                        if (held >= requiredDurationMs) onConfirm()
                        progress = 0f
                    }
                )
            }
    ) {
        CircularProgressIndicator(
            progress = { progress },
            color = DriverShieldColors.DangerRed
        )
        Text(label, color = DriverShieldColors.OnSurface)
    }
}
```

### WeeklyProgressBar

```kotlin
// Verde → Ámbar (>32h) → Rojo (>38h)
@Composable
fun WeeklyProgressBar(
    accumulatedMs: Long,
    maxMs: Long = LegalLimits.MAX_WEEKLY_MS
) {
    val fraction = (accumulatedMs.toFloat() / maxMs).coerceIn(0f, 1f)
    val color = when {
        accumulatedMs >= LegalLimits.WARN_WEEKLY_38H_MS -> DriverShieldColors.DangerRed
        accumulatedMs >= LegalLimits.WARN_WEEKLY_36H_MS -> DriverShieldColors.RestAmber
        else -> DriverShieldColors.WorkGreen
    }
    LinearProgressIndicator(
        progress = { fraction },
        color = color,
        modifier = Modifier.fillMaxWidth().height(8.dp)
    )
}
```

---

## Reglas de Composición

### Navegación

```
Bottom Navigation — 3 destinos (siempre visibles):
  [Turno]      →  DashboardScreen   (default, tab 0)
  [Historial]  →  HistoryScreen     (tab 1)
  [Calendario] →  CalendarScreen    (tab 2, libranzas)
```

### Tema (theme/Theme.kt)

```kotlin
@Composable
fun DriverShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DriverShieldColors.AmoledBlack,
            surface    = DriverShieldColors.Surface,
            primary    = DriverShieldColors.Accent,
            onBackground = DriverShieldColors.OnSurface,
            onSurface    = DriverShieldColors.OnSurface
        ),
        // dynamicColor = false — SIEMPRE. El AMOLED no es dinámico.
        content = content
    )
}
```

---

## Checklist de Validación (antes de PR)

- [ ] Fondo es `Color(0xFF000000)` exacto (no `#0D0D0D`)
- [ ] `dynamicColor = false` en MaterialTheme
- [ ] Timer principal ≥ 64sp
- [ ] Botones críticos ≥ 56dp de altura táctil
- [ ] LongPressButton usa 1500ms (sin modal de confirmación adicional)
- [ ] WeeklyProgressBar cambia color a ámbar >32h y rojo >38h
- [ ] Todo elemento interactivo tiene `contentDescription` para TalkBack
- [ ] Ningún elemento depende solo del color (accesibilidad daltonismo)
- [ ] Animaciones tienen `LocalReduceMotion.current` check

---

## Prohibiciones Absolutas

```
❌ Gradientes decorativos (cada gradiente = batería extra)
❌ Fuente variable o demasiado fina (ilegible en pantalla dim)
❌ Texto blanco sobre gris claro (contraste < 4.5:1)
❌ Modales de confirmación en el flujo crítico
❌ dynamicColor = true (destruye el AMOLED)
❌ Fondo != #000000 para la pantalla principal
❌ Tamaño táctil < 48dp en cualquier elemento interactivo
```

---

## Archivos que modifica este Skill

```
app/src/main/java/com/drivershield/
├── presentation/
│   ├── theme/
│   │   ├── Color.kt           ← OWNER de este skill
│   │   ├── Type.kt
│   │   └── Theme.kt           ← OWNER de este skill
│   ├── screen/
│   │   ├── dashboard/DashboardScreen.kt
│   │   ├── history/HistoryScreen.kt
│   │   └── calendar/CalendarScreen.kt
│   └── component/
│       ├── TimerDisplay.kt    ← OWNER de este skill
│       ├── LongPressButton.kt ← OWNER de este skill
│       ├── WeeklyProgressBar.kt ← OWNER de este skill
│       └── StatusChip.kt
```

*Fin de SKILL: ui-ux-maestro.md*
