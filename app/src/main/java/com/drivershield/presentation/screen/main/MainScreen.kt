package com.drivershield.presentation.screen.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivershield.presentation.component.CasioTimerBox
import com.drivershield.presentation.theme.CasioColors
import com.drivershield.service.ShiftState
import com.drivershield.service.TimerState
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit

// Colores funcionales — sólo para las etiquetas de panel, no afectan el LCD
private val LabelWork   = Color(0xFF00C853)
private val LabelRest   = Color(0xFFFF6B35)
private val LabelWeekly = Color(0xFFE0E0E0)

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    /*  CICLO ALTERNO — DESACTIVADO (simplificación de UI)
        val alternateOffDays by viewModel.alternateOffDays.collectAsStateWithLifecycle()
        val weeksToRotate by viewModel.weeksToRotate.collectAsStateWithLifecycle()
        val nextAltReference by viewModel.nextAltReference.collectAsStateWithLifecycle()
    */

    val today = LocalDate.now()
    val userOffDays      = schedule?.offDays ?: emptyList()
    val workDaysThisWeek by viewModel.weeklyWorkDays.collectAsStateWithLifecycle()
    val isOffDayToday    = userOffDays.contains(today.dayOfWeek.value)
    val dailyTarget      = schedule?.dailyTargetMs ?: (8L * 60 * 60 * 1000)
    val weeklyTarget     = if (schedule != null) dailyTarget * workDaysThisWeek else TimerState.MAX_WEEKLY_MS

    // Estado del Illuminator — puramente UI, no persiste en VM
    var illuminatorOn by remember { mutableStateOf(false) }
    val isRunning = uiState.shiftState == ShiftState.TRABAJANDO

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CasioColors.caseResinDark)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cabecera: indicadores de modo + botón LIGHT
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CasioModeIndicators(state = uiState.shiftState, illuminatorOn = illuminatorOn)
            IlluminatorButton(on = illuminatorOn, onToggle = { illuminatorOn = !illuminatorOn })
        }

        // CICLO ALTERNO: banner solo para días fijos (LIBRE); dayStatus desactivado
        if (isOffDayToday) {
            CasioOffDayBanner(illuminatorOn = illuminatorOn)
        }

        // BLOQUE TRABAJO
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CasioTimerBox(
                label = "TRABAJO PROG.", time = formatMs(uiState.workProgressMs),
                activeColor = LabelWork, isRunning = isRunning,
                illuminatorOn = illuminatorOn, modifier = Modifier.weight(1f)
            )
            CasioTimerBox(
                label = "TRABAJO REGR.", time = formatMs(uiState.workRemainingMs),
                activeColor = LabelWork, isRunning = isRunning,
                illuminatorOn = illuminatorOn, modifier = Modifier.weight(1f)
            )
        }

        // BLOQUE DESCANSO
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CasioTimerBox(
                label = "DESCANSO PROG.", time = formatMs(uiState.restProgressMs),
                activeColor = LabelRest, isRunning = isRunning,
                illuminatorOn = illuminatorOn, modifier = Modifier.weight(1f)
            )
            CasioTimerBox(
                label = "DESCANSO REGR.", time = formatMs(uiState.restRemainingMs),
                activeColor = LabelRest, isRunning = isRunning,
                illuminatorOn = illuminatorOn, modifier = Modifier.weight(1f)
            )
        }

        CasioTimerBox(
            label = "SEMANAL TOTAL", time = formatMs(uiState.weeklyProgressMs),
            activeColor = LabelWeekly, isRunning = isRunning,
            illuminatorOn = illuminatorOn, modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        CasioActionButtons(
            state    = uiState.shiftState,
            onStart  = { viewModel.startShift() },
            onPause  = { viewModel.pauseShift() },
            onResume = { viewModel.resumeShift() },
            onReset  = { viewModel.resetShift() }
        )

        CasioWeeklyFooter(
            weeklyMs      = uiState.weeklyProgressMs,
            limitMs       = weeklyTarget,
            workDays      = workDaysThisWeek,
            illuminatorOn = illuminatorOn
        )
    }
}

/**
 * Indicadores de modo tipo cristal LCD: los tres estados siempre visibles.
 * Estado activo = [lcdTextOn] negrita; inactivo = [lcdTextOff] ("fantasma").
 */
@Composable
private fun CasioModeIndicators(state: ShiftState, illuminatorOn: Boolean) {
    val textOn  = if (illuminatorOn) Color(0xFF003344) else CasioColors.lcdTextOn
    val textOff = if (illuminatorOn) Color(0xFF007090) else CasioColors.lcdTextOff
    val bgColor by animateColorAsState(
        targetValue   = if (illuminatorOn) CasioColors.illuminatorNight else CasioColors.lcdBackground,
        animationSpec = tween(300),
        label         = "modeBg"
    )
    Row(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(2.dp))
            .border(1.dp, CasioColors.legendBlue, RoundedCornerShape(2.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        ModeLabel("TRABAJO", state == ShiftState.TRABAJANDO, textOn, textOff)
        Text("·", color = textOff, fontSize = 10.sp)
        ModeLabel("PAUSA",   state == ShiftState.EN_PAUSA,   textOn, textOff)
        Text("·", color = textOff, fontSize = 10.sp)
        ModeLabel("STANDBY", state == ShiftState.DETENIDO,   textOn, textOff)
    }
}

@Composable
private fun ModeLabel(label: String, active: Boolean, colorOn: Color, colorOff: Color) {
    Text(
        text          = label,
        color         = if (active) colorOn else colorOff,
        fontSize      = 9.sp,
        fontWeight    = if (active) FontWeight.Black else FontWeight.Normal,
        letterSpacing = 1.sp,
        fontFamily    = FontFamily.Monospace
    )
}

/** Botón LIGHT — imita el iluminador lateral del Casio F-91W */
@Composable
private fun IlluminatorButton(on: Boolean, onToggle: () -> Unit) {
    val borderColor by animateColorAsState(
        targetValue   = if (on) CasioColors.illuminatorNight else CasioColors.legendGold,
        animationSpec = tween(300),
        label         = "lightBorder"
    )
    ResinPusher(
        text     = if (on) "LIGHT ●" else "LIGHT ○",
        color    = borderColor,
        onClick  = onToggle,
        modifier = Modifier
            .height(30.dp)
            .width(92.dp),
        fontSize = 9.sp
    )
}

@Composable
private fun CasioOffDayBanner(illuminatorOn: Boolean) {
    val bgColor by animateColorAsState(
        targetValue   = if (illuminatorOn) CasioColors.illuminatorNight.copy(alpha = 0.12f)
                        else CasioColors.caseResinDark,
        animationSpec = tween(300),
        label         = "bannerBg"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(2.dp))
            .border(1.dp, CasioColors.legendGold, RoundedCornerShape(2.dp))
            .padding(10.dp)
    ) {
        Text(
            text          = "ESTADO: LIBRE",
            color         = CasioColors.legendGold,
            fontWeight    = FontWeight.Bold,
            textAlign     = TextAlign.Center,
            fontSize      = 11.sp,
            letterSpacing = 2.sp,
            fontFamily    = FontFamily.Monospace,
            modifier      = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CasioActionButtons(
    state: ShiftState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit
) {
    when (state) {
        ShiftState.DETENIDO   -> ResinPusher(
            "START", Color(0xFF00C853), onStart,
            Modifier.fillMaxWidth().height(56.dp)
        )
        ShiftState.TRABAJANDO -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResinPusher("LAP",  Color(0xFFE0E0E0), onPause, Modifier.weight(1f).height(56.dp))
            ResinPusher("STOP", Color(0xFFFF3131), onReset, Modifier.weight(1f).height(56.dp))
        }
        ShiftState.EN_PAUSA   -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResinPusher("RESUME", Color(0xFF00C853), onResume, Modifier.weight(1f).height(56.dp))
            ResinPusher("FINISH", Color(0xFFFF3131), onReset,  Modifier.weight(1f).height(56.dp))
        }
    }
}

/**
 * Botón de resina estilo Casio.
 *
 * - Esquinas rígidas (4.dp) — sin CircleShape ni RoundedCornerShape(50)
 * - Feedback háptico en cada pulsación
 * - Estado presionado detectado vía [InteractionSource] (fondo semitransparente)
 * - Sin elevación ni sombra difusa Material
 */
@Composable
private fun ResinPusher(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp
) {
    val haptic            = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed         by interactionSource.collectIsPressedAsState()

    val bgColor by animateColorAsState(
        targetValue   = if (isPressed) color.copy(alpha = 0.20f) else Color.Transparent,
        animationSpec = tween(80),
        label         = "btnBg"
    )

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .border(2.dp, color, RoundedCornerShape(4.dp))
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text          = text.uppercase(),
            color         = color,
            fontSize      = fontSize,
            fontWeight    = FontWeight.Black,
            letterSpacing = 2.sp,
            fontFamily    = FontFamily.Monospace
        )
    }
}

@Composable
private fun CasioWeeklyFooter(weeklyMs: Long, limitMs: Long, workDays: Int, illuminatorOn: Boolean) {
    val progress = if (limitMs > 0) weeklyMs.toFloat() / limitMs else 0f
    val barColor = when {
        progress > 0.9f  -> Color(0xFFFF3131)
        progress > 0.75f -> CasioColors.legendGold
        else             -> Color(0xFF00C853)
    }
    val textColor by animateColorAsState(
        targetValue   = if (illuminatorOn) Color(0xFF003344) else CasioColors.lcdTextOff,
        animationSpec = tween(300),
        label         = "footerText"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress   = { progress.coerceIn(0f, 1f) },
            modifier   = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color      = barColor,
            trackColor = CasioColors.caseResinDark
        )
        Text(
            text       = "${formatMs(weeklyMs)} / ${formatMs(limitMs)} [$workDays DAYS]",
            color      = textColor,
            fontSize   = 10.sp,
            textAlign  = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.fillMaxWidth()
        )
    }
}

private fun formatMs(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
