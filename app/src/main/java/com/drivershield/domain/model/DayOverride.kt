package com.drivershield.domain.model

import java.time.LocalDate

data class DayOverride(
    val date: LocalDate,
    val isLibranza: Boolean
)
