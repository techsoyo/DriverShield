package com.drivershield.presentation.screen.calendar

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivershield.R
import com.drivershield.presentation.theme.CasioColors
import com.drivershield.presentation.theme.DriverShieldColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cyan LCD para libranza marcada manualmente por el conductor. (reservado para uso futuro) */
private val CyanManualLibranza = Color(0xFF00E5FF)
/** Rojo pastel para horas de turno planificadas aún no fichadas. */
private val PlannedShiftRed = Color(0xFFEF9A9A)
/** Verde libranza — mismo color para días fijos y manuales. */
private val LibranzaGreen = DriverShieldColors.VtcWorkGreen

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Toast de un solo disparo cuando el toggle es rechazado por validación
    LaunchedEffect(Unit) {
        viewModel.validationEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val pagerState = rememberPagerState(
        initialPage = CalendarViewModel.INITIAL_PAGE
    ) { CalendarViewModel.TOTAL_PAGES }

    // Sincroniza la página deslizada con el ViewModel
    LaunchedEffect(pagerState.currentPage) {
        viewModel.setPage(pagerState.currentPage)
    }

    // Sincroniza los botones flecha con el pager (anima el desplazamiento visual)
    val selectedPage by viewModel.selectedPage.collectAsStateWithLifecycle()
    LaunchedEffect(selectedPage) {
        if (pagerState.currentPage != selectedPage) {
            pagerState.animateScrollToPage(selectedPage)
        }
    }

    val isCurrentWeek = pagerState.currentPage == CalendarViewModel.INITIAL_PAGE

    Column(modifier = Modifier.fillMaxSize().background(CasioColors.caseResinDark)) {
        // 1. Cabecera Principal
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CasioColors.legendBlue)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.calendar_header_vtc), color = CasioColors.legendGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // 2. Horas Efectivas + barra de navegación semanal
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CasioColors.caseResinDark)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (pagerState.currentPage > 0) {
                        viewModel.setPage(pagerState.currentPage - 1)
                    }
                },
                enabled = pagerState.currentPage > 0
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_prev_week),
                    tint = if (pagerState.currentPage > 0) CasioColors.legendGold
                           else CasioColors.legendGold.copy(alpha = 0.3f)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val target = uiState.weeklyTargetHours.toInt().coerceAtLeast(1)
                val worked = uiState.totalWeeklyHours
                // Rojo si se supera el límite, ámbar si se supera el 90%, azul VTC si está dentro
                val hoursColor = when {
                    worked > target                 -> DriverShieldColors.VtcRestRed
                    worked >= target * 0.9f        -> DriverShieldColors.RestAmber
                    else                           -> CasioColors.legendGold
                }
                Text(
                    text = stringResource(
                        R.string.calendar_effective_hours,
                        worked.toInt(),
                        target
                    ),
                    color = hoursColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (!isCurrentWeek) {
                    TextButton(
                        onClick = { viewModel.setPage(CalendarViewModel.INITIAL_PAGE) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            stringResource(R.string.calendar_go_to_current_week),
                            fontSize = 11.sp,
                            color = CasioColors.illuminatorNight
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    if (pagerState.currentPage < CalendarViewModel.TOTAL_PAGES - 1) {
                        viewModel.setPage(pagerState.currentPage + 1)
                    }
                },
                enabled = pagerState.currentPage < CalendarViewModel.TOTAL_PAGES - 1
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.cd_next_week),
                    tint = if (pagerState.currentPage < CalendarViewModel.TOTAL_PAGES - 1)
                               CasioColors.legendGold
                           else CasioColors.legendGold.copy(alpha = 0.3f)
                )
            }
        }

        // 3 + 4 + 5: Contenido semanal con deslizamiento horizontal
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            key = { page -> page }
        ) { _ ->
            Column(modifier = Modifier.fillMaxSize()) {
                val scrollState = rememberScrollState()
                TableCalendarHeader(
                    weekDays = uiState.weekDays,
                    onDayClick = { date -> viewModel.toggleDayOverride(date) }
                )
                StatusRow(weekDays = uiState.weekDays)
                Box(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                    TimelineGrid(
                        weekDays = uiState.weekDays,
                        onDayClick = { date -> viewModel.toggleDayOverride(date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TableCalendarHeader(
    weekDays: List<DayState>,
    onDayClick: (LocalDate) -> Unit
) {
    val firstDate = weekDays.firstOrNull()?.date ?: LocalDate.now()
    val monthName = firstDate.format(DateTimeFormatter.ofPattern("MMMM", Locale("es"))).replaceFirstChar { it.uppercase() }
    val year = firstDate.year.toString()
    val diasLetras = listOf("L", "M", "X", "J", "V", "S", "D")

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Celda Mes/Año
        Column(
            modifier = Modifier
                .width(70.dp)
                .background(CasioColors.legendBlue)
                .border(0.5.dp, CasioColors.legendGold)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(year, color = CasioColors.legendGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(monthName, color = CasioColors.legendGold, fontSize = 10.sp)
        }
        // Celdas Días — cada una es clicable para invertir libranza
        weekDays.forEachIndexed { index, day ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(CasioColors.legendBlue)
                    .border(0.5.dp, CasioColors.legendGold)
                    .clickable(
                        onClickLabel = "Invertir libranza ${day.date}"
                    ) { onDayClick(day.date) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(diasLetras[index], color = CasioColors.legendGold, fontWeight = FontWeight.Bold)
                Text(day.date.dayOfMonth.toString(), color = CasioColors.legendGold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatusRow(weekDays: List<DayState>) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(70.dp)
                .background(CasioColors.legendBlue)
                .border(0.5.dp, CasioColors.legendGold)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.calendar_status_label), color = CasioColors.legendGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        weekDays.forEach { day ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .background(CasioColors.caseResinDark)
                    .border(0.5.dp, CasioColors.legendBlue),
                contentAlignment = Alignment.Center
            ) {
                val dotColor = when {
                    day.isLibranzaDay                       -> LibranzaGreen                    // Verde — libre (fijo o manual)
                    day.status == DayStatus.GREEN_POINT     -> DriverShieldColors.VtcRestRed    // Rojo  — trabajó
                    else                                    -> Color.LightGray.copy(alpha = 0.5f)
                }
                Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
            }
        }
    }
}

@Composable
private fun TimelineGrid(
    weekDays: List<DayState>,
    onDayClick: (LocalDate) -> Unit
) {
    // Horas en orden natural: 00:00 a 23:00
    val hours = (0..23).toList()

    Column {
        hours.forEach { hour ->
            Row(modifier = Modifier.fillMaxWidth().height(35.dp)) {
                // Columna de hora fija a la izquierda
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .fillMaxHeight()
                        .border(0.5.dp, CasioColors.legendBlue)
                        .background(CasioColors.lcdBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(String.format("%02d:00", hour), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CasioColors.lcdTextOn)
                }

                // Celdas para cada día de la semana
                weekDays.forEachIndexed { index, day ->
                    val previousDayState = if (index > 0) weekDays.getOrNull(index - 1) else null
                    val blockType = getBlockTypeForHour(hour, day, previousDayState)

                    val cellColor = when (blockType) {
                        BlockType.WORK     -> DriverShieldColors.VtcRestRed   // Rojo       → trabajo fichado
                        BlockType.REST     -> DriverShieldColors.RestAmber    // Ámbar      → pausa dentro turno
                        BlockType.LIBRANCE -> LibranzaGreen                   // Verde      → libranza (fija o manual)
                        BlockType.PLANNED  -> PlannedShiftRed                 // Rojo pastel→ turno configurado sin fichar
                        else               -> Color(0xFFE8F5E9)               // Verde muy claro → fuera del turno
                    }

                    val cellText = when (blockType) {
                        BlockType.WORK     -> "✓"
                        BlockType.REST     -> "D"
                        BlockType.LIBRANCE -> "L"
                        else               -> ""
                    }

                    val cellTextColor = when (blockType) {
                        BlockType.PLANNED  -> Color(0xFFB71C1C)   // Rojo oscuro sobre rojo pastel
                        else               -> Color.White
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(cellColor)
                            .border(0.2.dp, Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                onClickLabel = stringResource(
                                    R.string.cd_toggle_day_override
                                )
                            ) { onDayClick(day.date) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (cellText.isNotEmpty()) {
                            Text(cellText, color = cellTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun getBlockTypeForHour(hour: Int, dayState: DayState, previousDayState: DayState?): BlockType {
    val event = dayState.timeBlocks.find { hour.toFloat() >= it.startHour && hour.toFloat() < it.endHour }

    if (event != null) {
        if (event.type == BlockType.PLANNED) {
            val isOvernight = dayState.configStartHour > dayState.configEndHour
            // Horas 00:00–configEndHour provienen del turno del día ANTERIOR (nocturno)
            // → la decisión de libranza depende del estado del día previo, no del actual.
            val isLibranza = if (isOvernight && hour < dayState.configEndHour) {
                previousDayState?.isLibranzaDay ?: dayState.isLibranzaDay
            } else {
                dayState.isLibranzaDay
            }
            if (isLibranza) return BlockType.LIBRANCE
        }
        // Trabajo o descanso real registrado → se respeta siempre (el conductor fichó)
        return event.type
    }

    // Hora fuera del turno planificado (p.ej. 06:00–18:00 en turno 18–06):
    // Si el día completo es libranza → toda la columna se pinta Cian.
    if (dayState.isLibranzaDay) return BlockType.LIBRANCE

    return BlockType.NEUTRAL
}
