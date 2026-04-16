package com.drivershield.presentation.screen.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drivershield.R
import com.drivershield.domain.model.SessionReport
import com.drivershield.domain.model.ShiftSession
import com.drivershield.domain.model.ShiftType
import com.drivershield.domain.util.TimeConverter
import com.drivershield.presentation.theme.DriverShieldColors
import com.drivershield.presentation.ui.util.DateTransformation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    editViewModel: EditShiftViewModel = hiltViewModel()
) {
    val weeks by viewModel.historyState.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()
    val isEditDialogVisible by editViewModel.isEditDialogVisible.collectAsState()
    val selectedSession by editViewModel.selectedSession.collectAsState()
    var showPicker by remember { mutableStateOf(true) }
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var showCreateShiftDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

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

    // Diálogo de inserción manual de turno
    if (showCreateShiftDialog) {
        CreateShiftDialog(
            onConfirm = { date, sH, sM, eH, eM, type ->
                editViewModel.createManualShift(date, sH, sM, eH, eM, type)
                showCreateShiftDialog = false
            },
            onDismiss = { showCreateShiftDialog = false }
        )
    }

    // Diálogo secreto de reparación de emergencia
    if (showEmergencyDialog) {
        EmergencyRepairDialog(
            onConfirm = {
                editViewModel.fixEmergencyLastShift()
                showEmergencyDialog = false
            },
            onDismiss = { showEmergencyDialog = false }
        )
    }

    // Diálogo secreto de edición — solo visible cuando editViewModel lo activa
    if (isEditDialogVisible && selectedSession != null) {
        EditShiftDialog(
            session = selectedSession!!,
            onSave = { startH, startM, endH, endM, hasEnd ->
                editViewModel.saveSessionTimes(selectedSession!!.id, startH, startM, endH, endM, hasEnd)
                editViewModel.hideEditDialog()
            },
            onDismiss = { editViewModel.hideEditDialog() },
            onReopen = { editViewModel.reopenCurrentSession() },
            onDelete = { editViewModel.deleteCurrentSession() }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (dateRange != null) {
                DateRangeBanner(
                    start = dateRange!!.first,
                    end = dateRange!!.second,
                    onClick = { showPicker = true },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showEmergencyDialog = true
                    }
                )
            }

            when {
                dateRange == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.history_select_range),
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
                            text = stringResource(R.string.history_no_sessions_range),
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
                            WeekSummaryCard(
                                week = week,
                                onLongPressSession = { sessionId ->
                                    editViewModel.loadSessionAndShow(sessionId)
                                }
                            )
                        }
                    }
                }
            }
        }

        // FAB para insertar turno manual
        FloatingActionButton(
            onClick = { showCreateShiftDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = DriverShieldColors.Accent,
            contentColor = Color.Black
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.cd_insert_shift))
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
                text = stringResource(R.string.history_query_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.history_query_subtitle),
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
                        label = { Text(stringResource(R.string.label_start_date)) },
                        placeholder = { Text(stringResource(R.string.placeholder_date_format), color = Color(0xFF444444)) },
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
                        label = { Text(stringResource(R.string.label_end_date)) },
                        placeholder = { Text(stringResource(R.string.placeholder_date_format), color = Color(0xFF444444)) },
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
            val errInvalidDate = stringResource(R.string.error_invalid_date_format)
            val errEndBeforeStart = stringResource(R.string.error_end_before_start)
            TextButton(onClick = {
                val formatter = DateTimeFormatter.ofPattern("ddMMyyyy")
                val start = parseDate(startText.trim(), formatter)
                val end = parseDate(endText.trim(), formatter)

                var valid = true
                if (start == null) { startError = errInvalidDate; valid = false }
                if (end == null) { endError = errInvalidDate; valid = false }
                if (start != null && end != null && end < start) {
                    endError = errEndBeforeStart
                    valid = false
                }
                if (valid && start != null && end != null) onConfirm(start, end)
            }) {
                Text(stringResource(R.string.btn_view_history), color = DriverShieldColors.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = Color.Gray)
            }
        }
    )
}

private fun parseDate(text: String, formatter: DateTimeFormatter): LocalDate? =
    try { LocalDate.parse(text, formatter) } catch (_: DateTimeParseException) { null }

// ── Banner del rango activo ───────────────────────────────────────────────────

@Composable
fun DateRangeBanner(
    start: LocalDate,
    end: LocalDate,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress?.invoke() }
                )
            }
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
            text = stringResource(R.string.btn_change),
            color = DriverShieldColors.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    HorizontalDivider(color = Color(0xFF1A1A1A))
}

// ── Tarjeta de semana ─────────────────────────────────────────────────────────

@Composable
fun WeekSummaryCard(
    week: WeekSummary,
    onLongPressSession: (Long) -> Unit
) {
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
                        text = if (week.isCurrentWeek) stringResource(R.string.label_current_week) else stringResource(R.string.label_week_range, rangeLabel),
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
                    Text(text = stringResource(R.string.label_work), color = Color.Gray, fontSize = 10.sp)
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
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
                        DayRowAccordion(day, onLongPressSession)
                    }
                }
            }
        }
    }
}

// ── Fila de día — abre Bottom Sheet al tocar ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayRowAccordion(
    dayProgressive: DayProgressive,
    onLongPressSession: (Long) -> Unit
) {
    var showSheet by remember(dayProgressive.dayReport.date) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dayFormatter = DateTimeFormatter.ofPattern("EEEE d, MMM", Locale("es", "ES"))
    val dateText = dayProgressive.dayReport.date.format(dayFormatter).replaceFirstChar { it.uppercase() }
    val isSunday = dayProgressive.dayReport.date.dayOfWeek == DayOfWeek.SUNDAY

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSheet = true }
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
                            text = stringResource(R.string.label_cierre),
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
                            contentDescription = stringResource(R.string.cd_work_excess),
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
                    Text(text = stringResource(R.string.label_work), color = Color.Gray, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTime(dayProgressive.dayReport.totalRestMs),
                        color = Color.LightGray,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(text = stringResource(R.string.label_rest), color = Color.Gray, fontSize = 10.sp)
                }
            }
        }

        HorizontalDivider(
            color = Color(0xFF181818),
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }

    // ── Modal Bottom Sheet con desglose de sesiones ───────────────────────────
    if (showSheet) {
        val headerFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES"))
        val fullDateText = dayProgressive.dayReport.date.format(headerFormatter).replaceFirstChar { it.uppercase() }

        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF000000),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(Color(0xFF333333), RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Cabecera: fecha larga
                Text(
                    text = fullDateText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Badge de progresivo semanal
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .background(Color(0xFF0D1F19), RoundedCornerShape(8.dp))
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_progressive_weekly, formatTime(dayProgressive.progressiveWeeklyMs)),
                        color = DriverShieldColors.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                HorizontalDivider(color = Color(0xFF1A1A1A), modifier = Modifier.padding(bottom = 8.dp))

                // Lista de sesiones con long-press secreto
                if (dayProgressive.dayReport.sessions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.label_no_sessions_day),
                        color = Color(0xFF555555),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    dayProgressive.dayReport.sessions.forEach { session ->
                        SessionTimelineDetail(
                            session = session,
                            onLongPress = {
                                onLongPressSession(session.sessionId)
                            }
                        )
                        HorizontalDivider(color = Color(0xFF111111))
                    }
                }
            }
        }
    }
}

// ── Detalle de sesión (con gesto secreto) ────────────────────────────────────
// El fondo oscuro al pulsar es el único indicador de interactividad.
// No hay icono de edición ni texto que revele la función a terceros.

@Composable
fun SessionTimelineDetail(
    session: SessionReport,
    onLongPress: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val timeFmt = remember { java.time.format.DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { java.time.ZoneId.systemDefault() }

    val startLabel = remember(session.startTimestamp) {
        java.time.Instant.ofEpochMilli(session.startTimestamp).atZone(zone).format(timeFmt)
    }
    val endLabel = remember(session.endTimestamp) {
        session.endTimestamp?.let {
            java.time.Instant.ofEpochMilli(it).atZone(zone).format(timeFmt)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(session.sessionId) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
            }
            .padding(vertical = 8.dp)
    ) {
        // ── Cabecera de sesión ────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (session.isTampered) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.cd_tampered_clock),
                        tint = DriverShieldColors.DangerRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "Sesión #${session.sessionId}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
            // Resumen de horas en formato compacto HH:mm → HH:mm
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = startLabel,
                    color = DriverShieldColors.WorkGreen,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " → ",
                    color = Color(0xFF444444),
                    fontSize = 12.sp
                )
                Text(
                    text = endLabel ?: stringResource(R.string.label_active),
                    color = if (endLabel != null) DriverShieldColors.Accent else DriverShieldColors.RestAmber,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Línea de tiempo de eventos ────────────────────────────────────
        if (session.events.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            session.events.forEach { event ->
                val time = java.time.Instant.ofEpochMilli(event.timestamp)
                    .atZone(zone)
                    .format(timeFmt)

                Row(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "• ", color = DriverShieldColors.Accent, fontSize = 12.sp)
                    Text(
                        text = "$time  ${event.description}",
                        color = Color(0xFF777777),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ── Diálogo secreto de edición ────────────────────────────────────────────────
//
// Acceso: pulsación larga sobre una sesión en el historial.
// Diseño: AMOLED negro puro (#000000). Título neutro para no revelar la función.
// Propósito: corregir horas de inicio/fin sin que sea obvio para terceros.

@Composable
fun EditShiftDialog(
    session: ShiftSession,
    onSave: (startHour: Int, startMinute: Int, endHour: Int, endMinute: Int, hasEnd: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onReopen: (() -> Unit)? = null,   // solo visible cuando la sesión está cerrada
    onDelete: (() -> Unit)? = null
) {
    // Pre-carga los valores actuales del turno como estado inicial de los campos
    var startHour by remember(session.id) {
        mutableStateOf(TimeConverter.getHoursFromMillis(session.startTime.toEpochMilli()).toString().padStart(2, '0'))
    }
    var startMinute by remember(session.id) {
        mutableStateOf(TimeConverter.getMinutesFromMillis(session.startTime.toEpochMilli()).toString().padStart(2, '0'))
    }
    var endHour by remember(session.id) {
        mutableStateOf(session.endTime?.let { TimeConverter.getHoursFromMillis(it.toEpochMilli()).toString().padStart(2, '0') } ?: "")
    }
    var endMinute by remember(session.id) {
        mutableStateOf(session.endTime?.let { TimeConverter.getMinutesFromMillis(it.toEpochMilli()).toString().padStart(2, '0') } ?: "")
    }

    var startError by remember { mutableStateOf<String?>(null) }
    var endError by remember { mutableStateOf<String?>(null) }

    val timeFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color(0xFFB0B0B0),
        focusedBorderColor = DriverShieldColors.Accent,
        unfocusedBorderColor = Color(0xFF333333),
        focusedLabelColor = DriverShieldColors.Accent,
        unfocusedLabelColor = Color(0xFF666666),
        cursorColor = DriverShieldColors.Accent,
        errorBorderColor = DriverShieldColors.DangerRed,
        errorLabelColor = DriverShieldColors.DangerRed,
        focusedContainerColor = Color.Black,
        unfocusedContainerColor = Color.Black
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF000000),
        shape = RoundedCornerShape(12.dp),
        title = {
            Column {
                Text(
                    text = stringResource(R.string.history_adjust_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "ID ${session.id}",
                    color = Color(0xFF444444),
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

                // ── Hora de inicio ────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "INICIO",
                        color = Color(0xFF555555),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = startHour,
                            onValueChange = { v ->
                                val clean = v.filter { it.isDigit() }.take(2)
                                startHour = clean
                                startError = null
                            },
                            label = { Text("HH") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = timeFieldColors,
                            isError = startError != null,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                        Text(":", color = Color(0xFF555555), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = startMinute,
                            onValueChange = { v ->
                                val clean = v.filter { it.isDigit() }.take(2)
                                startMinute = clean
                                startError = null
                            },
                            label = { Text("mm") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = timeFieldColors,
                            isError = startError != null,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                    if (startError != null) {
                        Text(startError!!, color = DriverShieldColors.DangerRed, fontSize = 11.sp)
                    }
                }

                HorizontalDivider(color = Color(0xFF1A1A1A))

                // ── Hora de fin ───────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "FIN",
                        color = Color(0xFF555555),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = endHour,
                            onValueChange = { v ->
                                val clean = v.filter { it.isDigit() }.take(2)
                                endHour = clean
                                endError = null
                            },
                            label = { Text("HH") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = timeFieldColors,
                            isError = endError != null,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                        Text(":", color = Color(0xFF555555), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = endMinute,
                            onValueChange = { v ->
                                val clean = v.filter { it.isDigit() }.take(2)
                                endMinute = clean
                                endError = null
                            },
                            label = { Text("mm") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = timeFieldColors,
                            isError = endError != null,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                    if (endError != null) {
                        Text(endError!!, color = DriverShieldColors.DangerRed, fontSize = 11.sp)
                    }
                }

                // ── Acciones rápidas discretas ────────────────────────────
                // Solo visible si hay callbacks registrados. Sin texto — solo iconos.
                if (onReopen != null || onDelete != null) {
                    HorizontalDivider(color = Color(0xFF1A1A1A))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onReopen != null && session.endTime != null) {
                            IconButton(onClick = onReopen) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.cd_reopen_shift),
                                    tint = DriverShieldColors.RestAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (onDelete != null) {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_delete_session),
                                    tint = DriverShieldColors.DangerRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val errInvalidTime = stringResource(R.string.error_invalid_time)
            TextButton(
                onClick = {
                    val sh = startHour.toIntOrNull()
                    val sm = startMinute.toIntOrNull()
                    val eh = endHour.toIntOrNull()
                    val em = endMinute.toIntOrNull()

                    val hasEndInput = endHour.isNotBlank() || endMinute.isNotBlank()
                    var valid = true

                    if (sh == null || sh !in 0..23 || sm == null || sm !in 0..59) {
                        startError = errInvalidTime
                        valid = false
                    }
                    if (hasEndInput) {
                        if (eh == null || eh !in 0..23 || em == null || em !in 0..59) {
                            endError = errInvalidTime
                            valid = false
                        }
                    }
                    if (valid && sh != null && sm != null) {
                        onSave(sh, sm, eh ?: 0, em ?: 0, hasEndInput)
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.btn_save),
                    color = DriverShieldColors.Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = Color(0xFF555555))
            }
        }
    )
}

// ── Diálogo de reparación de emergencia ──────────────────────────────────────
// Acceso: pulsación larga en el banner de rango de fechas.
// Wording neutro — no revela qué hace a un observador casual.

@Composable
fun EmergencyRepairDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF000000),
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = stringResource(R.string.history_maintenance_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.history_maintenance_body),
                    color = Color(0xFF999999),
                    fontSize = 14.sp
                )
                Text(
                    text = stringResource(R.string.history_maintenance_warning),
                    color = DriverShieldColors.RestAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.btn_execute),
                    color = DriverShieldColors.DangerRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = Color(0xFF555555))
            }
        }
    )
}

private fun formatTime(ms: Long): String {
    val hrs = TimeUnit.MILLISECONDS.toHours(ms)
    val mins = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hrs > 0) "${hrs}h ${mins}min" else "${mins}min"
}

// ── Diálogo de inserción manual de turno ─────────────────────────────────────

@Composable
fun CreateShiftDialog(
    onConfirm: (date: LocalDate, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int, type: ShiftType) -> Unit,
    onDismiss: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("ddMMyyyy")
    var dateText by remember { mutableStateOf("") }
    var startHour by remember { mutableStateOf("") }
    var startMinute by remember { mutableStateOf("") }
    var endHour by remember { mutableStateOf("") }
    var endMinute by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ShiftType.NORMAL) }

    var dateError by remember { mutableStateOf<String?>(null) }
    var startError by remember { mutableStateOf<String?>(null) }
    var endError by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color(0xFFB0B0B0),
        focusedBorderColor = DriverShieldColors.Accent,
        unfocusedBorderColor = Color(0xFF333333),
        focusedLabelColor = DriverShieldColors.Accent,
        unfocusedLabelColor = Color(0xFF666666),
        cursorColor = DriverShieldColors.Accent,
        errorBorderColor = DriverShieldColors.DangerRed,
        errorLabelColor = DriverShieldColors.DangerRed,
        focusedContainerColor = Color.Black,
        unfocusedContainerColor = Color.Black
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF000000),
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = stringResource(R.string.history_insert_shift_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Fecha
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.label_fecha), color = Color(0xFF555555), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }.take(8)
                            dateText = clean
                            dateError = null
                        },
                        label = { Text(stringResource(R.string.placeholder_date_format)) },
                        placeholder = { Text(stringResource(R.string.placeholder_date_format), color = Color(0xFF333333)) },
                        isError = dateError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = DateTransformation(),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (dateError != null) Text(dateError!!, color = DriverShieldColors.DangerRed, fontSize = 11.sp)
                }

                // Tipo de turno
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.label_tipo), color = Color(0xFF555555), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShiftType.entries.forEach { type ->
                            val isSelected = selectedType == type
                            Surface(
                                onClick = { selectedType = type },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) DriverShieldColors.Accent else Color(0xFF1A1A1A),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = type.name.take(4),
                                    color = if (isSelected) Color.Black else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Hora inicio
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.label_inicio), color = Color(0xFF555555), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = startHour,
                            onValueChange = { startHour = it.filter { c -> c.isDigit() }.take(2); startError = null },
                            label = { Text("HH") },
                            singleLine = true,
                            isError = startError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, textAlign = TextAlign.Center)
                        )
                        Text(":", color = Color(0xFF555555), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = startMinute,
                            onValueChange = { startMinute = it.filter { c -> c.isDigit() }.take(2); startError = null },
                            label = { Text("mm") },
                            singleLine = true,
                            isError = startError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, textAlign = TextAlign.Center)
                        )
                    }
                    if (startError != null) Text(startError!!, color = DriverShieldColors.DangerRed, fontSize = 11.sp)
                }

                // Hora fin
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.label_fin), color = Color(0xFF555555), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = endHour,
                            onValueChange = { endHour = it.filter { c -> c.isDigit() }.take(2); endError = null },
                            label = { Text("HH") },
                            singleLine = true,
                            isError = endError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, textAlign = TextAlign.Center)
                        )
                        Text(":", color = Color(0xFF555555), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = endMinute,
                            onValueChange = { endMinute = it.filter { c -> c.isDigit() }.take(2); endError = null },
                            label = { Text("mm") },
                            singleLine = true,
                            isError = endError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, textAlign = TextAlign.Center)
                        )
                    }
                    if (endError != null) Text(endError!!, color = DriverShieldColors.DangerRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            val errInvalidDate2 = stringResource(R.string.error_invalid_date_format)
            val errInvalidTimeShort = stringResource(R.string.error_invalid_time_short)
            TextButton(onClick = {
                val date = try { LocalDate.parse(dateText, formatter) } catch (_: DateTimeParseException) { null }
                val sh = startHour.toIntOrNull()
                val sm = startMinute.toIntOrNull()
                val eh = endHour.toIntOrNull()
                val em = endMinute.toIntOrNull()

                var valid = true
                if (date == null) { dateError = errInvalidDate2; valid = false }
                if (sh == null || sh !in 0..23 || sm == null || sm !in 0..59) { startError = errInvalidTimeShort; valid = false }
                if (eh == null || eh !in 0..23 || em == null || em !in 0..59) { endError = errInvalidTimeShort; valid = false }
                // Si fin < inicio en minutos → turno cruza medianoche (fin = día siguiente)
                // No bloqueamos; ShiftRepositoryImpl detecta este caso y usa date+1
                if (valid && date != null && sh != null && sm != null && eh != null && em != null) {
                    onConfirm(date, sh, sm, eh, em, selectedType)
                }
            }) {
                Text(stringResource(R.string.btn_insert), color = DriverShieldColors.Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = Color(0xFF555555))
            }
        }
    )
}
