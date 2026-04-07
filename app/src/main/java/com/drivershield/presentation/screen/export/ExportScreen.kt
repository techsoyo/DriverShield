package com.drivershield.presentation.screen.export

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drivershield.presentation.theme.DriverShieldColors
import com.drivershield.presentation.ui.util.DateTransformation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun ExportScreen(viewModel: ExportViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val exportState by viewModel.exportState.collectAsState()

    // Lanzar el intent de compartir cuando el estado sea Success
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.resetState() }

    LaunchedEffect(exportState) {
        if (exportState is ExportState.Success) {
            shareLauncher.launch((exportState as ExportState.Success).intent)
        }
    }

    var startText by remember { mutableStateOf("") }
    var endText by remember { mutableStateOf("") }
    var startError by remember { mutableStateOf<String?>(null) }
    var endError by remember { mutableStateOf<String?>(null) }

    val fmt = DateTimeFormatter.ofPattern("ddMMyyyy")

    fun validateAndGetDates(): Pair<LocalDate, LocalDate>? {
        startError = null
        endError = null
        val start = parseDate(startText.trim(), fmt)
        val end = parseDate(endText.trim(), fmt)
        if (start == null) { startError = "Formato inválido (DD/MM/AAAA)"; return null }
        if (end == null) { endError = "Formato inválido (DD/MM/AAAA)"; return null }
        if (end < start) { endError = "Debe ser posterior a la fecha inicio"; return null }
        return Pair(start, end)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Título ────────────────────────────────────────────────────
            Text(
                text = "Exportar historial",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Text(
                text = "Introduce el rango de fechas que quieres exportar.",
                color = Color.Gray,
                fontSize = 13.sp
            )

            // ── Campos de fecha ───────────────────────────────────────────
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
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.Gray)
                    }
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
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.Gray)
                    }
                )
                if (endError != null) {
                    Text(endError!!, color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                }
            }

            // ── Botones de exportar ───────────────────────────────────────
            val isLoading = exportState is ExportState.Loading

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // PDF
                Button(
                    onClick = {
                        val dates = validateAndGetDates() ?: return@Button
                        viewModel.exportPdf(dates.first, dates.second)
                    },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DriverShieldColors.Accent,
                        contentColor = Color.Black
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Exportar PDF", fontWeight = FontWeight.Bold)
                    }
                }

                // CSV
                OutlinedButton(
                    onClick = {
                        val dates = validateAndGetDates() ?: return@OutlinedButton
                        viewModel.exportCsv(dates.first, dates.second)
                    },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverShieldColors.Accent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DriverShieldColors.Accent)
                ) {
                    Text("Exportar CSV", fontWeight = FontWeight.Bold)
                }
            }

            // ── Descripción de formatos ───────────────────────────────────
            FormatInfoCard()

            // ── Error de exportación ──────────────────────────────────────
            if (exportState is ExportState.Error) {
                Text(
                    text = (exportState as ExportState.Error).message,
                    color = Color.Red,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun FormatInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FormatRow(
                label = "PDF",
                description = "Documento de presentación a inspector o responsable. Incluye tablas semanales, fila de Cierre del Domingo y hash de verificación."
            )
            HorizontalDivider(color = Color(0xFF1A1A1A))
            FormatRow(
                label = "CSV",
                description = "Datos crudos para importar en Excel. Incluye timestamps epoch, duration_ms y resumen semanal al pie."
            )
        }
    }
}

@Composable
private fun FormatRow(label: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            color = DriverShieldColors.Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.width(32.dp)
        )
        Text(text = description, color = Color.Gray, fontSize = 12.sp)
    }
}

private fun parseDate(text: String, formatter: DateTimeFormatter): LocalDate? =
    try { LocalDate.parse(text, formatter) } catch (_: DateTimeParseException) { null }
