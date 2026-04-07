package com.drivershield.domain.model

data class DriverProfile(
    val fullName: String = "",
    val dni: String = ""
) {
    fun isValid(): Boolean = fullName.isNotBlank() && dni.isNotBlank()
}
