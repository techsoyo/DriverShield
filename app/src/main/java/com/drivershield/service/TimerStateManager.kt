package com.drivershield.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object TimerStateManager {

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    fun update(updater: (TimerState) -> TimerState) {
        _state.update(updater)
    }

    fun reset() {
        _state.value = TimerState()
    }
}
