package com.drivershield.domain.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Utilidad para convertir entre Timestamps (milisegundos) y
 * formato humano (Horas:Minutos) para la gestión de turnos.
 */
object TimeConverter {

    /**
     * Convierte milisegundos a un String legible "HH:mm" (Hora local).
     */
    fun millisToTimeLabel(millis: Long?): String {
        if (millis == null || millis == 0L) return "--:--"
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis

        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)

        return String.format(java.util.Locale.getDefault(), "%02d:%02d", hours, minutes)
    }

    /**
     * Extrae solo las HORAS de un timestamp.
     */
    fun getHoursFromMillis(millis: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        return calendar.get(Calendar.HOUR_OF_DAY)
    }

    /**
     * Extrae solo los MINUTOS de un timestamp.
     */
    fun getMinutesFromMillis(millis: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        return calendar.get(Calendar.MINUTE)
    }

    /**
     * Crea un nuevo Timestamp manteniendo el DÍA original pero cambiando HORA y MINUTO.
     * Útil para cuando editas la hora de inicio/fin en el historial.
     */
    fun updateTimeInTimestamp(originalMillis: Long, newHour: Int, newMinute: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = originalMillis
        calendar.set(Calendar.HOUR_OF_DAY, newHour)
        calendar.set(Calendar.MINUTE, newMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

}