package com.drivershield.presentation.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivershield.domain.util.CycleCalculator
import com.drivershield.presentation.component.CasioTimerBox
import com.drivershield.service.ShiftState
import com.drivershield.service.TimerState
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit

// Paleta Casio Retro - UI/UX Maestro
private val CasioGreen = Color(0xFF00FF41)
private val CasioRed = Color(0xFFFF3131)
private val CasioWhite = Color(0xFFE0E0E0)
private val CasioBorg = Color(0xFF1A1A1A)

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val alternateOffDays by viewModel.alternateOffDays.collectAsStateWithLifecycle()
    val weeksToRotate by viewModel.weeksToRotate.collectAsStateWithLifecycle()
    val nextAltReference by viewModel.nextAltReference.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    val mondayThisWeek = today.with(java.time.DayOfWeek.MONDAY)
    val userOffDays = schedule?.offDays ?: emptyList()
    
    val refDate: LocalDate? = if (nextAltReference > 0L)
        LocalDate.ofEpochDay(nextAltReference / 86_400_000L)
    else null

    val workDaysThisWeek = CycleCalculator.getWorkDaysInWeek(
        mondayThisWeek, refDate, userOffDays.toSet(), alternateOffDays.toSet(), weeksToRotate
    )

    val dayStatus = CycleCalculator.getDayStatus(today, refDate, userOffDays.toSet(), alternateOffDays.toSet(), weeksToRotate)
    val isOffDayToday = CycleCalculator.isOffDay(today, refDate, userOffDays.toSet(), alternateOffDays.toSet(), weeksToRotate)
    
    val dailyTarget = schedule?.dailyTargetMs ?: (8L * 60 * 60 * 1000)
    val weeklyTarget = if (schedule != null) dailyTarget * workDaysThisWeek else TimerState.MAX_WEEKLY_MS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Indicador de Modo LCD
        CasioStatusIndicator(state = uiState.shiftState)

        if (isOffDayToday) {
            CasioOffDayBanner(statusName = dayStatus.name)
        }

        // BLOQUE TRABAJO
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CasioTimerBox("TRABAJO PROG.", formatMs(uiState.workProgressMs), CasioGreen, Modifier.weight(1f))
            CasioTimerBox("TRABAJO REGR.", formatMs(uiState.workRemainingMs), CasioGreen, Modifier.weight(1f))
        }

        // BLOQUE DESCANSO
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CasioTimerBox("DESCANSO PROG.", formatMs(uiState.restProgressMs), CasioRed, Modifier.weight(1f))
            CasioTimerBox("DESCANSO REGR.", formatMs(uiState.restRemainingMs), CasioRed, Modifier.weight(1f))
        }

        CasioTimerBox("SEMANAL TOTAL", formatMs(uiState.weeklyProgressMs), CasioWhite, Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(4.dp))

        CasioActionButtons(
            state = uiState.shiftState,
            onStart = { viewModel.startShift() },
            onPause = { viewModel.pauseShift() },
            onResume = { viewModel.resumeShift() },
            onReset = { viewModel.resetShift() }
        )

        CasioWeeklyFooter(
            weeklyMs = uiState.weeklyProgressMs,
            limitMs = weeklyTarget,
            workDays = workDaysThisWeek
        )
    }
}

@Composable
private fun CasioStatusIndicator(state: ShiftState) {
    val (text, color) = when (state) {
        ShiftState.TRABAJANDO -> "MODE: TRABAJO" to CasioGreen
        ShiftState.EN_PAUSA -> "MODE: PAUSA" to CasioWhite
        ShiftState.DETENIDO -> "MODE: STANDBY" to Color.Gray
    }
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun CasioOffDayBanner(statusName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CasioBorg)
            .border(1.dp, Color.DarkGray)
            .padding(10.dp)
    ) {
        Text(
            text = "ESTADO: $statusName",
            color = CasioWhite,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
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
        ShiftState.DETENIDO -> CasioButton("START / INICIAR", CasioGreen, onStart)
        ShiftState.TRABAJANDO -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CasioButton("LAP / PAUSA", CasioWhite, onPause, Modifier.weight(1f))
            CasioButton("STOP / RESET", CasioRed, onReset, Modifier.weight(1f))
        }
        ShiftState.EN_PAUSA -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CasioButton("RESUME", CasioGreen, onResume, Modifier.weight(1f))
            CasioButton("FINISH", CasioRed, onReset, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CasioButton(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Text(text = text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CasioWeeklyFooter(weeklyMs: Long, limitMs: Long, workDays: Int) {
    val progress = if (limitMs > 0) weeklyMs.toFloat() / limitMs else 0f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (progress > 0.9f) CasioRed else CasioGreen,
            trackColor = CasioBorg
        )
        Text(
            text = "${formatMs(weeklyMs)} / ${formatMs(limitMs)} ($workDays DAYS)",
            color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatMs(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}