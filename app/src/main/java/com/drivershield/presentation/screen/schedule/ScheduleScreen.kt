package com.drivershield.presentation.screen.schedule

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    var selectedOffDays by remember { mutableStateOf(uiState.offDays) }

    val cycleStart = uiState.cycleStartEpoch.takeIf { it > 0L }
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    var cycleStartDate by remember { mutableStateOf(cycleStart) }

    var driverName by remember { mutableStateOf(uiState.driverProfile.fullName) }
    var driverDni by remember { mutableStateOf(uiState.driverProfile.dni) }

    LaunchedEffect(uiState.driverProfile) {
        driverName = uiState.driverProfile.fullName
        driverDni = uiState.driverProfile.dni
    }

    LaunchedEffect(uiState.startHour, uiState.endHour, uiState.offDays, uiState.cycleStartEpoch) {
        startTime = formatHour(uiState.startHour)
        endTime = formatHour(uiState.endHour)
        selectedOffDays = uiState.offDays
        cycleStartDate = uiState.cycleStartEpoch.takeIf { it > 0L }
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
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
                Toast.makeText(context, "Datos del conductor guardados", Toast.LENGTH_SHORT).show()
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
            selectedDays = selectedOffDays,
            onDayToggle = { day ->
                val newDays = if (selectedOffDays.contains(day)) {
                    selectedOffDays - day
                } else {
                    selectedOffDays + day
                }
                selectedOffDays = newDays
                val startH = startTime.substringBefore(":").toIntOrNull() ?: 8
                val endH = endTime.substringBefore(":").toIntOrNull() ?: 16
                viewModel.saveConfigHours(startH, endH, newDays)
            }
        )

        CycleStartPicker(
            selectedDate = cycleStartDate,
            onDateSelected = { cycleStartDate = it }
        )

        Button(
            onClick = {
                val startH = startTime.substringBefore(":").toIntOrNull() ?: 8
                val endH = endTime.substringBefore(":").toIntOrNull() ?: 16
                val dailyMs = parseTimeToMs(endTime) - parseTimeToMs(startTime)
                val cycleEpoch = cycleStartDate
                    ?.atStartOfDay(ZoneId.systemDefault())
                    ?.toInstant()
                    ?.toEpochMilli()
                    ?: 0L

                viewModel.saveConfigHours(startH, endH, selectedOffDays)
                viewModel.saveSchedule(
                    startTime = startTime,
                    endTime = endTime,
                    offDays = selectedOffDays,
                    dailyTargetMs = dailyMs.coerceAtLeast(0L),
                    weeklyTargetMs = dailyMs.coerceAtLeast(0L) * (7 - selectedOffDays.size),
                    cycleStartEpoch = cycleEpoch
                )
                Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("Guardar Horario", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (uiState.schedule != null) {
            CurrentScheduleCard(uiState.schedule!!, cycleStartDate)
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
    selectedDays: List<Int>,
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
                text = "Días de libranza fijos",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Selecciona días libres semanales (opcional)",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                days.forEach { (day, label) ->
                    val isSelected = selectedDays.contains(day)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onDayToggle(day) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleStartPicker(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("es"))

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
                text = "Inicio de ciclo de 5 semanas",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Selecciona un Domingo como inicio. Cada 5 semanas, Domingo y Lunes serán libranza automática.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedDate?.format(formatter) ?: "Sin definir",
                    color = if (selectedDate != null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontSize = 14.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val today = LocalDate.now()
                            val prevSunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
                            onDateSelected(prevSunday)
                        },
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Domingo actual", fontSize = 11.sp)
                    }

                    if (selectedDate != null) {
                        Button(
                            onClick = { onDateSelected(LocalDate.now()) },
                            modifier = Modifier.height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                            )
                        ) {
                            Text("Reset", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentScheduleCard(schedule: com.drivershield.domain.model.WorkSchedule, cycleStartDate: LocalDate?) {
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
                val offDayNames = schedule.offDays.map {
                    when (it) {
                        1 -> "Lunes"
                        2 -> "Martes"
                        3 -> "Miércoles"
                        4 -> "Jueves"
                        5 -> "Viernes"
                        6 -> "Sábado"
                        7 -> "Domingo"
                        else -> ""
                    }
                }.joinToString(", ")
                Text(
                    text = "Libranzas fijas: $offDayNames",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }

            if (schedule.hasCycle() && cycleStartDate != null) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("es"))
                Text(
                    text = "Ciclo 5 semanas: desde ${cycleStartDate.format(formatter)}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Domingo y Lunes son libranza automática cada 5 semanas",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
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
                Text("Guardar Datos del Conductor", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun parseTimeToMs(time: String): Long {
    val parts = time.split(":")
    val hours = parts[0].toIntOrNull() ?: 0
    val minutes = parts[1].toIntOrNull() ?: 0
    return (hours * 60 + minutes) * 60 * 1000L
}

private fun formatHour(hour: Int): String {
    return String.format("%02d:00", hour)
}
