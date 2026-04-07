package com.drivershield.core.model

import com.drivershield.domain.model.ShiftType
import java.time.Instant

/**
 * Domain model – sesión de turno en core/model.
 * Alias o copia plana de domain.model.ShiftSession para acceso transversal.
 */
data class ShiftSession(
    val id: Long = 0,
    val type: ShiftType = ShiftType.NORMAL,
    val startTime: Instant = Instant.now(),
    val endTime: Instant? = null,
    val notes: String = ""
)
