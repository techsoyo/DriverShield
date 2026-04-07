package com.drivershield.presentation.screen.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivershield.data.local.datastore.SessionDataStore
import com.drivershield.domain.model.DriverProfile
import com.drivershield.domain.model.WorkSchedule
import com.drivershield.domain.repository.ScheduleRepository
import com.drivershield.service.notification.scheduleWeek5Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val cycleStartEpoch: Long = 0L
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val scheduleAndDriver = combine(
        scheduleRepository.getScheduleFlow(),
        sessionDataStore.driverFullName,
        sessionDataStore.driverDni
    ) { schedule, fullName, dni ->
        Triple(
            schedule,
            DriverProfile(fullName = fullName, dni = dni),
            schedule?.cycleStartEpoch ?: 0L
        )
    }

    private val hoursAndOffDays = combine(
        sessionDataStore.startHour,
        sessionDataStore.endHour,
        sessionDataStore.offDays
    ) { startH, endH, offD ->
        Triple(startH, endH, offD)
    }

    val uiState: StateFlow<ScheduleUiState> = combine(
        scheduleAndDriver,
        hoursAndOffDays
    ) { schedDrv, hoursOff ->
        ScheduleUiState(
            schedule = schedDrv.first,
            driverProfile = schedDrv.second,
            startHour = hoursOff.first,
            endHour = hoursOff.second,
            offDays = hoursOff.third,
            cycleStartEpoch = schedDrv.third
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
        weeklyTargetMs: Long,
        cycleStartEpoch: Long = 0L
    ) {
        viewModelScope.launch {
            scheduleRepository.saveSchedule(
                WorkSchedule(
                    startTime = startTime,
                    endTime = endTime,
                    offDays = offDays,
                    weeklyTargetMs = weeklyTargetMs,
                    dailyTargetMs = dailyTargetMs,
                    cycleStartEpoch = cycleStartEpoch
                )
            )

            if (cycleStartEpoch > 0L) {
                scheduleWeek5Reminder(context, cycleStartEpoch)
            }
        }
    }

    fun saveDriverProfile(fullName: String, dni: String) {
        viewModelScope.launch {
            sessionDataStore.saveDriverProfile(fullName, dni)
        }
    }

    fun saveConfigHours(startHour: Int, endHour: Int, offDays: List<Int>) {
        viewModelScope.launch {
            sessionDataStore.saveFullConfig(startHour, endHour, offDays)
        }
    }
}
