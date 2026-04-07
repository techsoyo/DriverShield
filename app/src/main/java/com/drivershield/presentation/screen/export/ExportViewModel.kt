package com.drivershield.presentation.screen.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivershield.data.local.datastore.SessionDataStore
import com.drivershield.domain.export.CsvExporter
import com.drivershield.domain.export.PdfExporter
import com.drivershield.domain.model.DayReport
import com.drivershield.domain.model.DriverProfile
import com.drivershield.domain.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed class ExportState {
    object Idle : ExportState()
    object Loading : ExportState()
    data class Success(val intent: Intent) : ExportState()
    data class Error(val message: String) : ExportState()
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val sessionDataStore: SessionDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun exportPdf(rangeStart: LocalDate, rangeEnd: LocalDate) {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            runCatching {
                val dayReports = getDayReportsInRange(rangeStart, rangeEnd)
                val driver = getDriverProfile()
                PdfExporter.export(context, driver, dayReports, rangeStart, rangeEnd)
            }.onSuccess { file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _exportState.value = ExportState.Success(Intent.createChooser(intent, "Compartir informe PDF"))
            }.onFailure { e ->
                _exportState.value = ExportState.Error(e.message ?: "Error al generar el PDF")
            }
        }
    }

    fun exportCsv(rangeStart: LocalDate, rangeEnd: LocalDate) {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            runCatching {
                val dayReports = getDayReportsInRange(rangeStart, rangeEnd)
                val driver = getDriverProfile()
                CsvExporter.export(context, driver, dayReports, rangeStart, rangeEnd)
            }.onSuccess { file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _exportState.value = ExportState.Success(Intent.createChooser(intent, "Compartir datos CSV"))
            }.onFailure { e ->
                _exportState.value = ExportState.Error(e.message ?: "Error al generar el CSV")
            }
        }
    }

    fun resetState() {
        _exportState.value = ExportState.Idle
    }

    private suspend fun getDayReportsInRange(start: LocalDate, end: LocalDate): List<DayReport> =
        shiftRepository.getAllSessionsWithEvents().first()
            .filter { it.date >= start && it.date <= end }
            .sortedBy { it.date }

    private suspend fun getDriverProfile(): DriverProfile {
        val name = sessionDataStore.driverFullName.first()
        val dni = sessionDataStore.driverDni.first()
        return DriverProfile(fullName = name, dni = dni)
    }
}
