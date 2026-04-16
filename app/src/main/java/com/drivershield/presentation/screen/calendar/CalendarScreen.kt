package com.drivershield.presentation.screen.calendar

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.drivershield.presentation.theme.DriverShieldColors
import androidx.compose.ui.res.stringResource
import com.drivershield.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // 1. Cabecera Principal
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DriverShieldColors.VtcHeaderBlue)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.calendar_header_vtc), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // 2. Horas Efectivas + barra de navegación semanal
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DriverShieldColors.VtcLightBlueHeader)
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
                    tint = if (pagerState.currentPage > 0) DriverShieldColors.VtcHeaderBlue
                           else DriverShieldColors.VtcHeaderBlue.copy(alpha = 0.3f)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.calendar_effective_hours, uiState.totalWeeklyHours.toInt()),
                    color = DriverShieldColors.VtcHeaderBlue,
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
                            color = DriverShieldColors.VtcHeaderBlue
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
                               DriverShieldColors.VtcHeaderBlue
                           else DriverShieldColors.VtcHeaderBlue.copy(alpha = 0.3f)
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
                TableCalendarHeader(weekDays = uiState.weekDays)
                StatusRow(weekDays = uiState.weekDays)
                Box(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                    TimelineGrid(weekDays = uiState.weekDays)
                }
            }
        }
    }
}

@Composable
private fun TableCalendarHeader(weekDays: List<DayState>) {
    val firstDate = weekDays.firstOrNull()?.date ?: LocalDate.now()
    val monthName = firstDate.format(DateTimeFormatter.ofPattern("MMMM", Locale("es"))).replaceFirstChar { it.uppercase() }
    val year = firstDate.year.toString()
    val diasLetras = listOf("L", "M", "X", "J", "V", "S", "D")

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Celda Mes/Año
        Column(
            modifier = Modifier
                .width(70.dp)
                .background(DriverShieldColors.VtcHeaderBlue)
                .border(0.5.dp, Color.White)
                .padding(4.dp), 
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(year, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(monthName, color = Color.White, fontSize = 10.sp)
        }
        // Celdas Días
        weekDays.forEachIndexed { index, day ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(DriverShieldColors.VtcHeaderBlue)
                    .border(0.5.dp, Color.White)
                    .padding(4.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(diasLetras[index], color = Color.White, fontWeight = FontWeight.Bold)
                Text(day.date.dayOfMonth.toString(), color = Color.White, fontSize = 12.sp)
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
                .background(DriverShieldColors.VtcHeaderBlue)
                .border(0.5.dp, Color.White)
                .padding(4.dp), 
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.calendar_status_label), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        weekDays.forEach { day ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .background(DriverShieldColors.VtcLightBlueHeader.copy(alpha = 0.3f))
                    .border(0.5.dp, Color.LightGray), 
                contentAlignment = Alignment.Center
            ) {
                val color = if (day.isLibranzaDay) DriverShieldColors.VtcLibranzaGray 
                           else if (day.status == DayStatus.GREEN_POINT) DriverShieldColors.VtcWorkGreen 
                           else Color.LightGray.copy(alpha = 0.5f)
                Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
            }
        }
    }
}

@Composable
private fun TimelineGrid(weekDays: List<DayState>) {
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
                        .border(0.5.dp, Color.LightGray)
                        .background(Color(0xFFF5F5F5)), 
                    contentAlignment = Alignment.Center
                ) {
                    Text(String.format("%02d:00", hour), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Celdas para cada día de la semana
                weekDays.forEachIndexed { index, day ->
                    val previousDayState = if (index > 0) weekDays.getOrNull(index - 1) else null
                    val blockType = getBlockTypeForHour(hour, day, previousDayState)
                    
                    val cellColor = when (blockType) {
                        BlockType.WORK -> DriverShieldColors.VtcWorkGreen      // Verde (✓)
                        BlockType.REST -> DriverShieldColors.VtcRestRed        // Rojo (D)
                        BlockType.LIBRANCE -> DriverShieldColors.VtcLibranzaGray // Gris (L)
                        BlockType.PLANNED -> Color(0xFFE9F7A7)               // CAMBIO: Verde Claro (Turno Configurado)
                        else -> Color.White                                   // Vacío
                    }

                    val cellText = when (blockType) {
                        BlockType.WORK -> "✓"
                        BlockType.REST -> "D"
                        BlockType.LIBRANCE -> "L"
                        else -> ""
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(cellColor)
                            .border(0.2.dp, Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cellText.isNotEmpty()) {
                            // Ajuste de color de texto: DarkGray para 'L' sobre fondo Gris, White para el resto
                            val textColor = if (blockType == BlockType.LIBRANCE) Color.DarkGray else Color.White
                            Text(cellText, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
        // Si es una hora de turno (PLANNED)
        if (event.type == BlockType.PLANNED) {
            val isOvernight = dayState.configStartHour > dayState.configEndHour
            
            // Lógica Nocturna Refinada (según reparto de colores):
            // - Si es madrugada (hora < fin del turno), el estado de libranza depende de si AYER fue libranza.
            val isLibranza = if (isOvernight && hour < dayState.configEndHour) {
                previousDayState?.isLibranzaDay ?: dayState.isLibranzaDay
            } else {
                dayState.isLibranzaDay
            }
            
            if (isLibranza) return BlockType.LIBRANCE
        }
        return event.type
    }

    return BlockType.NEUTRAL
}