package com.drivershield.presentation.screen.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drivershield.domain.model.SessionReport
import com.drivershield.presentation.theme.DriverShieldColors
import com.drivershield.presentation.ui.util.DateTransformation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val weeks by viewModel.historyState.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()
    var showPicker by remember { mutableStateOf(true) }

    if (showPicker) {
        DateRangePickerDialog(
            initialRange = dateRange,
            onConfirm = { start, end ->
                viewModel.setDateRange(start, end)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Banner del rango activo (siempre visible si hay rango)
            if (dateRange != null) {
                DateRangeBanner(
                    start = dateRange!!.first,
                    end = dateRange!!.second,
                    onClick = { showPicker = true }
                )
            }

            when {
                dateRange == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Selecciona un rango de fechas\npara consultar el historial.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
                weeks.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Sin turnos en el rango seleccionado.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(weeks, key = { "${it.isoYear}-${it.isoWeek}" }) { week ->
                            WeekSummaryCard(week)
                        }
                    }
                }
            }
        }
    }
}

// ── Dialog de selección de rango ─────────────────────────────────────────────

@Composable
fun DateRangePickerDialog(
    initialRange: Pair<LocalDate, LocalDate>?,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val displayFormatter = DateTimeFormatter.ofPattern("ddMMyyyy")
    var startText by remember { mutableStateOf(initialRange?.first?.format(displayFormatter) ?: "") }
    var endText by remember { mutableStateOf(initialRange?.second?.format(displayFormatter) ?: "") }
    var startError by remember { mutableStateOf<String?>(null) }
    var endError by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.LightGray,
        focusedBorderColor = DriverShieldColors.Accent,
        unfocusedBorderColor = Color.DarkGray,
        focusedLabelColor = DriverShieldColors.Accent,
        unfocusedLabelColor = Color.Gray,
        cursorColor = DriverShieldColors.Accent,
        errorBorderColor = Color.Red,
        errorLabelColor = Color.Red
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Consultar historial",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Introduce el rango de fechas a visualizar (día/mes/año):",
                    color = Color.Gray,
                    fontSize = 13.sp
                )

                Column {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }
                            if (clean.length <= 8) startText = clean
                            startError = null
                        },
                        label = { Text("Fecha inicio") },
                        placeholder = { Text("DD/MM/AAAA", color = Color(0xFF444444)) },
                        isError = startError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = DateTransformation(),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (startError != null) {
                        Text(startError!!, color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                }

                Column {
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }
                            if (clean.length <= 8) endText = clean
                            endError = null
                        },
                        label = { Text("Fecha fin") },
                        placeholder = { Text("DD/MM/AAAA", color = Color(0xFF444444)) },
                        isError = endError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = DateTransformation(),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (endError != null) {
                        Text(endError!!, color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val formatter = DateTimeFormatter.ofPattern("ddMMyyyy")
                val start = parseDate(startText.trim(), formatter)
                val end = parseDate(endText.trim(), formatter)

                var valid = true
                if (start == null) { startError = "Formato inválido (DD/MM/AAAA)"; valid = false }
                if (end == null) { endError = "Formato inválido (DD/MM/AAAA)"; valid = false }
                if (start != null && end != null && end < start) {
                    endError = "Debe ser posterior a la fecha inicio"
                    valid = false
                }
                if (valid && start != null && end != null) onConfirm(start, end)
            }) {
                Text("Ver historial", color = DriverShieldColors.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.Gray)
            }
        }
    )
}

private fun parseDate(text: String, formatter: DateTimeFormatter): LocalDate? =
    try { LocalDate.parse(text, formatter) } catch (_: DateTimeParseException) { null }

// ── Banner del rango activo ───────────────────────────────────────────────────

@Composable
fun DateRangeBanner(start: LocalDate, end: LocalDate, onClick: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = DriverShieldColors.Accent,
                modifier = Modifier.size(16.dp).padding(end = 4.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${start.format(fmt)}  →  ${end.format(fmt)}",
                color = Color.White,
                fontSize = 14.sp
            )
        }
        Text(
            text = "Cambiar",
            color = DriverShieldColors.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    HorizontalDivider(color = Color(0xFF1A1A1A))
}

// ── Tarjeta de semana ─────────────────────────────────────────────────────────

@Composable
fun WeekSummaryCard(week: WeekSummary) {
    var isExpanded by remember(week.isoYear, week.isoWeek) { mutableStateOf(week.isCurrentWeek) }

    val headerFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))
    val rangeLabel = "${week.weekStart.format(headerFormatter)} – ${week.weekEnd.format(headerFormatter)}"
    val yearLabel = week.weekStart.year.toString()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (week.isCurrentWeek) "Semana actual" else "Semana $rangeLabel",
                        color = if (week.isCurrentWeek) DriverShieldColors.Accent else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (week.isCurrentWeek) "$rangeLabel · $yearLabel" else yearLabel,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                    Text(
                        text = formatTime(week.totalWeeklyWorkMs),
                        color = DriverShieldColors.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(text = "trabajo", color = Color.Gray, fontSize = 10.sp)
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                    tint = Color.Gray
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    HorizontalDivider(color = Color(0xFF1A1A1A), thickness = 1.dp)
                    week.days.forEach { day ->
                        DayRowAccordion(day)
                    }
                }
            }
        }
    }
}

// ── Fila de día con acordeón ──────────────────────────────────────────────────

@Composable
fun DayRowAccordion(dayProgressive: DayProgressive) {
    var isExpanded by remember(dayProgressive.dayReport.date) { mutableStateOf(false) }

    val dayFormatter = DateTimeFormatter.ofPattern("EEEE d, MMM", Locale("es", "ES"))
    val dateText = dayProgressive.dayReport.date.format(dayFormatter).replaceFirstChar { it.uppercase() }
    val isSunday = dayProgressive.dayReport.date.dayOfWeek == DayOfWeek.SUNDAY

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSunday) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Cierre semanal",
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateText,
                        color = if (isSunday) Color(0xFFFFD700) else Color.White,
                        fontWeight = if (isSunday) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                    if (isSunday) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CIERRE",
                            color = Color(0xFFFFD700),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0xFF2A2000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (dayProgressive.dayReport.hasWorkExcess) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Exceso de jornada",
                            tint = Color.Red,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTime(dayProgressive.dayReport.totalWorkMs),
                        color = DriverShieldColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(text = "trabajo", color = Color.Gray, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTime(dayProgressive.dayReport.totalRestMs),
                        color = Color.LightGray,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(text = "descanso", color = Color.Gray, fontSize = 10.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color(0xFF1E3A2F), RoundedCornerShape(8.dp))
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Progresivo semanal hasta aquí: ${formatTime(dayProgressive.progressiveWeeklyMs)}",
                        color = DriverShieldColors.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                dayProgressive.dayReport.sessions.forEach { session ->
                    SessionTimelineDetail(session)
                }
            }
        }

        HorizontalDivider(
            color = Color(0xFF181818),
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// ── Detalle de sesión ─────────────────────────────────────────────────────────

@Composable
fun SessionTimelineDetail(session: SessionReport) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (session.isTampered) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Reloj Alterado",
                    tint = Color.Red,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                )
            }
            Text("Sesión #${session.sessionId}", color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(4.dp))

        session.events.forEach { event ->
            val time = java.time.Instant.ofEpochMilli(event.timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

            Row(modifier = Modifier.padding(start = 12.dp).padding(vertical = 2.dp)) {
                Text(
                    text = "• ",
                    color = DriverShieldColors.Accent,
                    fontSize = 14.sp
                )
                Text(
                    text = "$time - ${event.description}",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val hrs = TimeUnit.MILLISECONDS.toHours(ms)
    val mins = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hrs > 0) "${hrs}h ${mins}min" else "${mins}min"
}
