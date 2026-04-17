package com.drivershield.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fragmenta el intervalo [startMs, endMs) en segmentos de día natural (00:00–23:59).
 *
 * Permite imputar las horas de un turno nocturno de forma proporcional
 * entre los dos (o más) días naturales que abarca, cumpliendo el requisito
 * de cómputo estrictamente diario del calendario DriverShield.
 */
object NightShiftSplitter {

    /**
     * Devuelve un mapa [LocalDate → milisegundos] con las horas que
     * corresponden a cada día natural dentro del intervalo dado.
     *
     * @param startMs  Epoch-millis del inicio del intervalo (inclusive).
     * @param endMs    Epoch-millis del fin del intervalo (exclusive).
     * @param zoneId   Zona horaria para determinar los límites de cada día (por defecto la del sistema).
     */
    fun msPerDay(
        startMs: Long,
        endMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, Long> {
        require(endMs >= startMs) { "endMs ($endMs) debe ser >= startMs ($startMs)" }
        if (endMs == startMs) return emptyMap()

        val result = mutableMapOf<LocalDate, Long>()
        var currentMs = startMs
        var currentDate: LocalDate = Instant.ofEpochMilli(startMs).atZone(zoneId).toLocalDate()

        while (currentMs < endMs) {
            val nextMidnightMs: Long = currentDate.plusDays(1L)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
            val segmentEndMs = minOf(endMs, nextMidnightMs)
            val segmentMs = segmentEndMs - currentMs
            if (segmentMs > 0L) {
                result[currentDate] = (result[currentDate] ?: 0L) + segmentMs
            }
            if (segmentEndMs >= endMs) break
            currentDate = currentDate.plusDays(1L)
            currentMs = nextMidnightMs
        }

        return result
    }
}
