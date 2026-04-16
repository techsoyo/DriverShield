package com.drivershield.presentation.screen.main

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivershield.data.local.datastore.SessionDataStore
import com.drivershield.domain.model.EventType
import com.drivershield.domain.model.ShiftType
import com.drivershield.domain.model.WorkSchedule
import com.drivershield.domain.repository.ScheduleRepository
import com.drivershield.domain.repository.ShiftRepository
import com.drivershield.service.ShiftState
import com.drivershield.service.TimerService
import com.drivershield.service.TimerState
import com.drivershield.service.TimerStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val shiftState: ShiftState = ShiftState.DETENIDO,
    val workProgressMs: Long = 0L,
    val workRemainingMs: Long = 0L,
    val restProgressMs: Long = 0L,
    val restRemainingMs: Long = 0L,
    val weeklyProgressMs: Long = 0L,
    val sessionId: Long = 0L
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shiftRepository: ShiftRepository,
    scheduleRepository: ScheduleRepository,
    sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val schedule: StateFlow<WorkSchedule?> = scheduleRepository
        .getScheduleFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val alternateOffDays: StateFlow<List<Int>> = sessionDataStore.alternateOffDays
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val weeksToRotate: StateFlow<Int> = sessionDataStore.weeksToRotate
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 5
        )

    val nextAltReference: StateFlow<Long> = sessionDataStore.nextAltReference
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0L
        )

    init {
        observeServiceState()
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            TimerStateManager.state.collect { serviceState ->
                _uiState.update {
                    it.copy(
                        shiftState = serviceState.state,
                        workProgressMs = serviceState.workProgressMs,
                        workRemainingMs = serviceState.workRemainingMs,
                        restProgressMs = serviceState.restProgressMs,
                        restRemainingMs = serviceState.restRemainingMs,
                        weeklyProgressMs = serviceState.weeklyProgressMs,
                        sessionId = serviceState.sessionId
                    )
                }
            }
        }
    }

    fun startShift() {
        viewModelScope.launch {
            val sessionId = shiftRepository.startSession(ShiftType.NORMAL)

            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_START
                putExtra(TimerService.EXTRA_SESSION_ID, sessionId)
            }
            context.startForegroundService(intent)
        }
    }

    fun pauseShift() {
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId
            if (sessionId > 0L) {
                shiftRepository.recordEvent(sessionId, EventType.START_PAUSE)
            }

            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_PAUSE
            }
            context.startService(intent)
        }
    }

    fun resumeShift() {
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId
            if (sessionId > 0L) {
                shiftRepository.recordEvent(sessionId, EventType.END_PAUSE)
            }

            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_RESUME
            }
            context.startService(intent)
        }
    }

    fun resetShift() {
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId
            if (sessionId > 0L) {
                shiftRepository.endSession(sessionId)
            }

            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP
            }
            context.startService(intent)

            TimerStateManager.reset()
        }
    }

    fun stopShift() {
        resetShift()
    }
}
