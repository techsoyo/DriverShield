package com.drivershield.presentation.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivershield.domain.model.WorkSchedule
import com.drivershield.domain.util.CycleCalculator
import com.drivershield.presentation.theme.DriverShieldColors
import com.drivershield.service.ShiftState
import com.drivershield.service.TimerState
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    val weekFields = WeekFields.ISO
    val currentIsoYear = today.get(weekFields.weekBasedYear())
    val currentWeekNumber = today.get(weekFields.weekOfWeekBasedYear())
    val todayDayOfWeek = today.dayOfWeek.value
    val mondayThisWeek = today.with(java.time.DayOfWeek.MONDAY)

    val cycleStartEpoch = schedule?.cycleStartEpoch ?: 0L
    val userOffDays = schedule?.offDays ?: emptyList()

    val workDaysThisWeek = if (cycleStartEpoch > 0L) {
        CycleCalculator.getWorkDaysInWeek(mondayThisWeek, cycleStartEpoch, userOffDays)
    } else {
        val offDays = schedule?.offDays ?: listOf(6, 7)
        (1..7).filter { !offDays.contains(it) }.count()
    }

    val isWeek5 = cycleStartEpoch > 0L && CycleCalculator.isWeek5(today, cycleStartEpoch)
    val dailyTarget = schedule?.dailyTargetMs ?: (8L * 60 * 60 * 1000)
    val weeklyTarget = if (schedule != null) dailyTarget * workDaysThisWeek else TimerState.MAX_WEEKLY_MS

    val isOffDayToday = CycleCalculator.isOffDay(today, cycleStartEpoch, userOffDays)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusChip(state = uiState.shiftState)

        if (isOffDayToday) {
            OffDayBanner(isWeek5 = isWeek5)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CounterCard(
                title = "Trabajo Progresivo",
                valueMs = uiState.workProgressMs,
                color = DriverShieldColors.WorkGreen,
                modifier = Modifier.weight(1f)
            )
            CounterCard(
                title = "Trabajo Regresivo",
                valueMs = uiState.workRemainingMs,
                color = DriverShieldColors.WorkGreen,
                isCountdown = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CounterCard(
                title = "Descanso Progresivo",
                valueMs = uiState.restProgressMs,
                color = DriverShieldColors.DangerRed,
                modifier = Modifier.weight(1f)
            )
            CounterCard(
                title = "Descanso Regresivo",
                valueMs = uiState.restRemainingMs,
                color = DriverShieldColors.DangerRed,
                isCountdown = true,
                modifier = Modifier.weight(1f)
            )
        }

        CounterCard(
            title = "Semanal Progresivo",
            valueMs = uiState.weeklyProgressMs,
            color = MaterialTheme.colorScheme.onSurface,
            isWeekly = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        ActionButtons(
            state = uiState.shiftState,
            onStart = { viewModel.startShift() },
            onPause = { viewModel.pauseShift() },
            onResume = { viewModel.resumeShift() },
            onReset = { viewModel.resetShift() }
        )

        WeeklyProgressBar(
            weeklyMs = uiState.weeklyProgressMs,
            limitMs = weeklyTarget,
            workDays = workDaysThisWeek,
            isOffDayToday = isOffDayToday,
            isWeek5 = isWeek5,
            schedule = schedule
        )
    }
}

@Composable
private fun StatusChip(state: ShiftState) {
    val (text, color) = when (state) {
        ShiftState.TRABAJANDO -> "TRABAJANDO" to MaterialTheme.colorScheme.secondary
        ShiftState.EN_PAUSA -> "EN PAUSA" to MaterialTheme.colorScheme.primary
        ShiftState.DETENIDO -> "DETENIDO" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun CounterCard(
    title: String,
    valueMs: Long,
    color: androidx.compose.ui.graphics.Color,
    isCountdown: Boolean = false,
    isWeekly: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatMs(valueMs),
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = if (isWeekly) 32.sp else 28.sp,
                fontWeight = FontWeight.Bold
            )

            if (isWeekly) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Límite: ${formatMs(TimerState.MAX_WEEKLY_MS)}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    state: ShiftState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit
) {
    when (state) {
        ShiftState.DETENIDO -> {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Iniciar Turno", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        ShiftState.TRABAJANDO -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPause,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Pausa", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onReset,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reset", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        ShiftState.EN_PAUSA -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Retomar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onReset,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Finalizar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OffDayBanner(isWeek5: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isWeek5)
                MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isWeek5) "🔴" else "🛏",
                fontSize = 24.sp
            )
            Column {
                Text(
                    text = if (isWeek5) "Semana 5: Libranza Dom-Lun" else "Hoy es día de libranza",
                    color = if (isWeek5)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = if (isWeek5)
                        "Semana de descanso automático. La barra semanal se ajusta a 5 días laborables."
                    else
                        "No se espera trabajo. La barra semanal se ajusta a los días laborables.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun WeeklyProgressBar(
    weeklyMs: Long,
    limitMs: Long,
    workDays: Int,
    isOffDayToday: Boolean,
    isWeek5: Boolean,
    schedule: com.drivershield.domain.model.WorkSchedule?
) {
    val progress = if (limitMs > 0) weeklyMs.toFloat() / limitMs else 0f

    val cycleStartEpoch = schedule?.cycleStartEpoch ?: 0L
    val daysUntilWeek5 = if (cycleStartEpoch > 0L) CycleCalculator.getDaysUntilWeek5(cycleStartEpoch) else -1
    val isSundayBeforeWeek5 = cycleStartEpoch > 0L && CycleCalculator.isSundayBeforeWeek5(cycleStartEpoch)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val title = when {
                    isWeek5 -> "Semanal (Sem 5)"
                    isOffDayToday -> "Semanal (libranza)"
                    else -> "Semanal"
                }
                Text(
                    text = title,
                    color = if (isWeek5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(progress * 100).coerceAtMost(100f).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = when {
                    isWeek5 -> MaterialTheme.colorScheme.error
                    progress > 0.95f -> MaterialTheme.colorScheme.error
                    progress > 0.8f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.secondary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                text = "${formatMs(weeklyMs)} / ${formatMs(limitMs)} ($workDays días laborables)",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (isSundayBeforeWeek5) {
                Text(
                    text = "⚠️ Mañana comienza tu semana de libranza Dom-Lun",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (daysUntilWeek5 > 0 && daysUntilWeek5 <= 7 && !isWeek5) {
                Text(
                    text = "Semana 5 en $daysUntilWeek5 días",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}
