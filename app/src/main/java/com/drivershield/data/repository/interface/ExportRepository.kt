package com.drivershield.data.repository.`interface`

import java.io.File

interface ExportRepository {
    /** Exporta los turnos del rango dado en formato CSV */
    suspend fun exportToCsv(fromEpoch: Long, toEpoch: Long): File

    /** Exporta los turnos del rango dado en formato PDF */
    suspend fun exportToPdf(fromEpoch: Long, toEpoch: Long): File
}
