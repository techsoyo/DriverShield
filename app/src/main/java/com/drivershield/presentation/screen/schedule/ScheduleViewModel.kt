package com.drivershield.presentation.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivershield.data.local.datastore.SessionDataStore
import com.drivershield.domain.model.DriverProfile
import com.drivershield.domain.model.WorkSchedule
import com.drivershield.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleUiState(
    val schedule: WorkSchedule? = null,
    val driverProfile: DriverProfile = DriverProfile(),
    val startHour: Int = 8,
    val endHour: Int = 16,
    val offDays: List<Int> = listOf(6, 7),
    val alternateOffDays: List<Int> = emptyList(),
    val weeksToRotate: Int = 5,
    val nextAltReference: Long = 0L
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val scheduleAndDriver = combine(
        scheduleRepository.getScheduleFlow(),
        sessionDataStore.driverFullName,
        sessionDataStore.driverDni
    ) { schedule, fullName, dni ->
        Pair(schedule, DriverProfile(fullName = fullName, dni = dni))
    }

    private val hoursAndOffDays = combine(
        sessionDataStore.startHour,
        sessionDataStore.endHour,
        sessionDataStore.offDays
    ) { startH, endH, offD ->
        Triple(startH, endH, offD)
    }

    private val alternateConfig = combine(
        sessionDataStore.alternateOffDays,
        sessionDataStore.weeksToRotate,
        sessionDataStore.nextAltReference
    ) { altDays, weeks, nextRef -> Triple(altDays, weeks, nextRef) }

    val uiState: StateFlow<ScheduleUiState> = combine(
        scheduleAndDriver,
        hoursAndOffDays,
        alternateConfig
    ) { schedDrv, hoursOff, altConf ->
        ScheduleUiState(
            schedule = schedDrv.first,
            driverProfile = schedDrv.second,
            startHour = hoursOff.first,
            endHour = hoursOff.second,
            offDays = hoursOff.third,
            alternateOffDays = altConf.first,
            weeksToRotate = altConf.second,
            nextAltReference = altConf.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState()
    )

    fun saveSchedule(
        startTime: String,
        endTime: String,
        offDays: List<Int>,
        dailyTargetMs: Long,
        weeklyTargetMs: Long
    ) {
        viewModelScope.launch {
            val startH = startTime.substringBefore(":").toIntOrNull() ?: 8
            val endH   = endTime.substringBefore(":").toIntOrNull() ?: 16
            val current = uiState.value
            // Sincronización de Doble Verdad: Room + DataStore juntos
            sessionDataStore.saveFullConfig(
                startH, endH,
                offDays,
                current.alternateOffDays,
                current.weeksToRotate
            )
            scheduleRepository.saveSchedule(
                WorkSchedule(
                    startTime = startTime,
                    endTime = endTime,
                    offDays = offDays,
                    weeklyTargetMs = weeklyTargetMs,
                    dailyTargetMs = dailyTargetMs,
                    cycleStartEpoch = 0L
                )
            )
        }
    }

    /** Persiste de forma atómica: Nombre, DNI, horas y días de libranza fijos.
     *  Los valores de ciclos alternos se preservan en el DataStore sin modificarse. */
    fun saveAllData(
        fullName: String,
        dni: String,
        startHour: Int,
        endHour: Int,
        startTime: String,
        endTime: String,
        offDays: List<Int>,
        dailyTargetMs: Long,
        weeklyTargetMs: Long
    ) {
        viewModelScope.launch {
            sessionDataStore.saveDriverProfile(fullName, dni)
            sessionDataStore.saveFullConfig(
                startHour, endHour,
                offDays,
                uiState.value.alternateOffDays,
                uiState.value.weeksToRotate
            )
            scheduleRepository.saveSchedule(
                WorkSchedule(
                    startTime = startTime,
                    endTime = endTime,
                    offDays = offDays,
                    weeklyTargetMs = weeklyTargetMs,
                    dailyTargetMs = dailyTargetMs,
                    cycleStartEpoch = 0L
                )
            )
        }
    }

    fun saveDriverProfile(fullName: String, dni: String) {
        viewModelScope.launch {
            sessionDataStore.saveDriverProfile(fullName, dni)
        }
    }

    fun saveConfigHours(startHour: Int, endHour: Int) {
        viewModelScope.launch {
            val current = uiState.value
            sessionDataStore.saveFullConfig(
                startHour, endHour,
                current.offDays, current.alternateOffDays, current.weeksToRotate
            )
        }
    }

    fun toggleFixedOffDay(day: Int) {
        viewModelScope.launch {
            val current = uiState.value
            val newFixed = if (current.offDays.contains(day)) current.offDays - day else current.offDays + day
            val newAlternate = current.alternateOffDays - day
            sessionDataStore.saveFullConfig(
                current.startHour, current.endHour,
                newFixed, newAlternate, current.weeksToRotate
            )
        }
    }

    fun toggleAlternateOffDay(day: Int) {
        viewModelScope.launch {
            val current = uiState.value
            val newAlternate = if (current.alternateOffDays.contains(day)) current.alternateOffDays - day else current.alternateOffDays + day
            val newFixed = current.offDays - day
            sessionDataStore.saveFullConfig(
                current.startHour, current.endHour,
                newFixed, newAlternate, current.weeksToRotate
            )
        }
    }

    fun setWeeksToRotate(weeks: Int) {
        viewModelScope.launch {
            val current = uiState.value
            sessionDataStore.saveFullConfig(
                current.startHour, current.endHour,
                current.offDays, current.alternateOffDays, weeks
            )
        }
    }

    fun setNextAltReference(epochMs: Long) {
        viewModelScope.launch {
            sessionDataStore.saveNextAltReference(epochMs)
        }
    }
}
