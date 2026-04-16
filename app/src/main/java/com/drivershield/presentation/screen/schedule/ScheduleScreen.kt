package com.drivershield.presentation.screen.schedule

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivershield.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var startTime by remember { mutableStateOf(formatHour(uiState.startHour)) }
    var endTime by remember { mutableStateOf(formatHour(uiState.endHour)) }
    var driverName by remember { mutableStateOf(uiState.driverProfile.fullName) }
    var driverDni by remember { mutableStateOf(uiState.driverProfile.dni) }

    LaunchedEffect(uiState.driverProfile) {
        driverName = uiState.driverProfile.fullName
        driverDni = uiState.driverProfile.dni
    }

    LaunchedEffect(uiState.startHour, uiState.endHour) {
        startTime = formatHour(uiState.startHour)
        endTime = formatHour(uiState.endHour)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Configuración",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        DriverProfileCard(
            fullName = driverName,
            dni = driverDni,
            onNameChange = { driverName = it },
            onDniChange = { driverDni = it },
            onSave = {
                viewModel.saveDriverProfile(driverName, driverDni)
                Toast.makeText(context, context.getString(R.string.toast_driver_profile_saved), Toast.LENGTH_SHORT).show()
            }
        )

        TimePickerCard(
            label = "Hora de inicio",
            value = startTime,
            onValueChange = { startTime = it }
        )

        TimePickerCard(
            label = "Hora de fin",
            value = endTime,
            onValueChange = { endTime = it }
        )

        OffDaysSelector(
            title = "Días de libranza fijos",
            subtitle = "Selecciona días libres semanales (opcional)",
            selectedDays = uiState.offDays,
            disabledDays = uiState.alternateOffDays,
            onDayToggle = { viewModel.toggleFixedOffDay(it) }
        )

        OffDaysSelector(
            title = "Días de libranza alternos",
            subtitle = "Se alternarán con los días fijos según el ciclo",
            selectedDays = uiState.alternateOffDays,
            disabledDays = uiState.offDays,
            onDayToggle = { viewModel.toggleAlternateOffDay(it) }
        )

        WeeksToRotateCard(
            weeks = uiState.weeksToRotate,
            onWeeksChange = { viewModel.setWeeksToRotate(it) }
        )

        AltReferenceDateCard(
            nextAltReference = uiState.nextAltReference,
            onDateSelected = { viewModel.setNextAltReference(it) }
        )

        Button(
            onClick = {
                val startH = startTime.substringBefore(":").toIntOrNull() ?: 8
                val endH = endTime.substringBefore(":").toIntOrNull() ?: 16
                val dailyMs = parseTimeToMs(endTime) - parseTimeToMs(startTime)

                viewModel.saveConfigHours(startH, endH)
                viewModel.saveSchedule(
                    startTime = startTime,
                    endTime = endTime,
                    offDays = uiState.offDays,
                    dailyTargetMs = dailyMs.coerceAtLeast(0L),
                    weeklyTargetMs = dailyMs.coerceAtLeast(0L) * (7 - uiState.offDays.size)
                )
                Toast.makeText(context, context.getString(R.string.toast_config_saved), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(stringResource(R.string.btn_save_schedule), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (uiState.schedule != null) {
            CurrentScheduleCard(
                schedule = uiState.schedule!!,
                alternateOffDays = uiState.alternateOffDays,
                weeksToRotate = uiState.weeksToRotate,
                nextAltReference = uiState.nextAltReference
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AltReferenceDateCard(
    nextAltReference: Long,
    onDateSelected: (Long) -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val displayText = if (nextAltReference > 0L) {
        LocalDate.ofEpochDay(nextAltReference / 86_400_000L).format(formatter)
    } else {
        "No configurada"
    }

    var showDialog by remember { mutableStateOf(false) }

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
            Text(
                text = "Fecha de próxima libranza alterna",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Permite calcular qué semanas son alternas y cuáles fijas",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayText,
                    color = if (nextAltReference > 0L)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { showDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(stringResource(R.string.btn_select), fontSize = 13.sp)
                }
            }
        }
    }

    if (showDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (nextAltReference > 0L) nextAltReference else null
        )
        val amoledColors = DatePickerDefaults.colors(
            containerColor = Color(0xFF000000),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            headlineContentColor = MaterialTheme.colorScheme.secondary,
            weekdayContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            subheadContentColor = MaterialTheme.colorScheme.onSurface,
            navigationContentColor = MaterialTheme.colorScheme.onSurface,
            yearContentColor = MaterialTheme.colorScheme.onSurface,
            currentYearContentColor = MaterialTheme.colorScheme.secondary,
            selectedYearContainerColor = MaterialTheme.colorScheme.secondary,
            selectedYearContentColor = Color(0xFF000000),
            dayContentColor = MaterialTheme.colorScheme.onSurface,
            selectedDayContainerColor = MaterialTheme.colorScheme.secondary,
            selectedDayContentColor = Color(0xFF000000),
            todayContentColor = MaterialTheme.colorScheme.secondary,
            todayDateBorderColor = MaterialTheme.colorScheme.secondary,
            dayInSelectionRangeContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
            dayInSelectionRangeContentColor = MaterialTheme.colorScheme.secondary
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                        showDialog = false
                    }
                ) { Text(stringResource(R.string.btn_accept), color = MaterialTheme.colorScheme.secondary) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.btn_cancel), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            },
            colors = amoledColors
        ) {
            DatePicker(state = datePickerState, colors = amoledColors)
        }
    }
}

@Composable
private fun TimePickerCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 14.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val parts = value.split(":")
                        val h = parts[0].toIntOrNull() ?: 0
                        val newH = ((h - 1 + 24) % 24)
                        onValueChange("${String.format(Locale.US, "%02d", newH)}:${parts[1]}")
                    },
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text("-", fontSize = 18.sp)
                }

                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Button(
                    onClick = {
                        val parts = value.split(":")
                        val h = parts[0].toIntOrNull() ?: 0
                        val newH = (h + 1) % 24
                        onValueChange("${String.format(Locale.US, "%02d", newH)}:${parts[1]}")
                    },
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text("+", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun OffDaysSelector(
    title: String,
    subtitle: String,
    selectedDays: List<Int>,
    disabledDays: List<Int>,
    onDayToggle: (Int) -> Unit
) {
    val days = listOf(
        1 to "L", 2 to "M", 3 to "X", 4 to "J", 5 to "V", 6 to "S", 7 to "D"
    )

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
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                days.forEach { (day, label) ->
                    val isSelected = selectedDays.contains(day)
                    val isDisabled = disabledDays.contains(day)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = when {
                                    isDisabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = !isDisabled) { onDayToggle(day) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = when {
                                isDisabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            },
                            fontWeight = if (isSelected && !isDisabled) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeksToRotateCard(
    weeks: Int,
    onWeeksChange: (Int) -> Unit
) {
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
            Text(
                text = "Núm. semanas para cambio días libranza",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Cada N semanas se alternarán los días fijos con los días alternos",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (weeks > 1) onWeeksChange(weeks - 1) },
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text("-", fontSize = 20.sp)
                }

                Text(
                    text = "$weeks sem.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = { if (weeks < 52) onWeeksChange(weeks + 1) },
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text("+", fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun CurrentScheduleCard(
    schedule: com.drivershield.domain.model.WorkSchedule,
    alternateOffDays: List<Int>,
    weeksToRotate: Int,
    nextAltReference: Long
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
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
            Text(
                text = "Horario Actual",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${schedule.startTime} - ${schedule.endTime}",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            if (schedule.offDays.isNotEmpty()) {
                val offDayNames = schedule.offDays.map { dayName(it) }.joinToString(", ")
                Text(
                    text = "Libranzas fijas: $offDayNames",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }

            if (alternateOffDays.isNotEmpty()) {
                val altDayNames = alternateOffDays.map { dayName(it) }.joinToString(", ")
                Text(
                    text = "Libranzas alternas: $altDayNames",
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
                Text(
                    text = "Rotación cada $weeksToRotate semanas",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            if (nextAltReference > 0L) {
                val refDate = LocalDate.ofEpochDay(nextAltReference / 86_400_000L)
                Text(
                    text = "Referencia alterna: ${refDate.format(formatter)}",
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun dayName(day: Int): String = when (day) {
    1 -> "Lunes"; 2 -> "Martes"; 3 -> "Miércoles"
    4 -> "Jueves"; 5 -> "Viernes"; 6 -> "Sábado"
    7 -> "Domingo"; else -> ""
}

@Composable
private fun DriverProfileCard(
    fullName: String,
    dni: String,
    onNameChange: (String) -> Unit,
    onDniChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Datos del Conductor",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Nombre y DNI aparecerán en los informes PDF y CSV exportados",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Nombre Completo",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                BasicTextField(
                    value = fullName,
                    onValueChange = onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    ),
                    decorationBox = { innerTextField ->
                        if (fullName.isEmpty()) {
                            Text(
                                text = "Ej: Juan García López",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                )

                Text(
                    text = "DNI / Identificación",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                BasicTextField(
                    value = dni,
                    onValueChange = onDniChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    ),
                    decorationBox = { innerTextField ->
                        if (dni.isEmpty()) {
                            Text(
                                text = "Ej: 12345678A",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.btn_save_driver), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun parseTimeToMs(time: String): Long {
    val parts = time.split(":")
    val hours = parts.getOrNull(0)?.toLongOrNull() ?: 0L
    val minutes = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    return (hours * 60 + minutes) * 60 * 1000L
}

private fun formatHour(hour: Int): String {
    return String.format(Locale.US, "%02d:00", hour)
}
