package com.drivershield.presentation.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivershield.domain.model.ShiftSession
import com.drivershield.domain.model.ShiftType
import com.drivershield.domain.repository.ShiftRepository
import com.drivershield.domain.util.TimeConverter
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class EditShiftViewModel @Inject constructor(
    private val repository: ShiftRepository
) : ViewModel() {

    // ── Estado del diálogo ────────────────────────────────────────────────────

    private val _isEditDialogVisible = MutableStateFlow(false)
    val isEditDialogVisible = _isEditDialogVisible.asStateFlow()

    /** Sesión cargada para editar; null mientras no se ha seleccionado ninguna. */
    private val _selectedSession = MutableStateFlow<ShiftSession?>(null)
    val selectedSession = _selectedSession.asStateFlow()

    // ── Acciones del diálogo ──────────────────────────────────────────────────

    fun showEditDialog() { _isEditDialogVisible.value = true }
    fun hideEditDialog() {
        _isEditDialogVisible.value = false
        _selectedSession.value = null
    }

    /**
     * Carga la sesión de la BD y abre el diálogo.
     * Se llama desde el gesto de pulsación larga en el Bottom Sheet.
     */
    fun loadSessionAndShow(id: Long) {
        viewModelScope.launch {
            _selectedSession.value = repository.getShiftById(id)
            _isEditDialogVisible.value = true
        }
    }

    // ── Edición de horas (atómica) ────────────────────────────────────────────

    /**
     * Guarda inicio y (opcionalmente) fin en un solo write atómico.
     * Recalcula durationMs automáticamente vía el mapper toEntity().
     * Si [hasEnd] = false, se conserva el endTime original (sesión activa).
     */
    fun saveSessionTimes(
        sessionId: Long,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        hasEnd: Boolean
    ) {
        viewModelScope.launch {
            val current = repository.getShiftById(sessionId) ?: return@launch

            val newStartMillis = TimeConverter.updateTimeInTimestamp(
                current.startTime.toEpochMilli(), startHour, startMinute
            )
            val newEndInstant: Instant? = if (hasEnd) {
                val originalEndMillis = current.endTime?.toEpochMilli() ?: newStartMillis
                Instant.ofEpochMilli(
                    TimeConverter.updateTimeInTimestamp(originalEndMillis, endHour, endMinute)
                )
            } else {
                current.endTime   // sin cambios si no hay fin
            }

            repository.updateShift(
                current.copy(
                    startTime = Instant.ofEpochMilli(newStartMillis),
                    endTime = newEndInstant
                )
            )
        }
    }

    // ── Acciones rápidas ──────────────────────────────────────────────────────

    /**
     * Reabre la sesión seleccionada quitando su endTimestamp.
     * Útil para corregir cierres accidentales.
     */
    fun reopenCurrentSession() {
        val id = _selectedSession.value?.id ?: return
        viewModelScope.launch {
            repository.reopenShift(id)
            hideEditDialog()
        }
    }

    /**
     * Elimina la sesión seleccionada permanentemente.
     * Solo para eliminar sesiones accidentales.
     */
    fun deleteCurrentSession() {
        val id = _selectedSession.value?.id ?: return
        viewModelScope.launch {
            repository.deleteShiftById(id)
            hideEditDialog()
        }
    }

    // ── Limpieza de emergencia ────────────────────────────────────────────────

    /**
     * Borra las sesiones accidentales (IDs 4 y 5) y reabre la sesión 3.
     * Solo para uso administrativo interno.
     */
    fun fixEmergencyLastShift() {
        viewModelScope.launch {
            repository.deleteShiftById(4L)
            repository.deleteShiftById(5L)
            repository.reopenShift(3L)
        }
    }

    // ── Inserción manual ──────────────────────────────────────────────────────

    /**
     * Crea un turno completo para un día sin registros.
     */
    fun createManualShift(
        date: LocalDate,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        type: ShiftType
    ) {
        viewModelScope.launch {
            repository.createManualShift(date, startHour, startMinute, endHour, endMinute, type)
        }
    }
}
