package com.drivershield.domain.export

import android.content.Context
import com.drivershield.domain.model.DayReport
import com.drivershield.domain.model.DriverProfile
import com.drivershield.domain.model.EventType
import com.drivershield.domain.util.WorkLimits
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

object CsvExporter {

    /**
     * Genera el CSV en la carpeta cache/exports/ (accesible via FileProvider).
     * Formato orientado a gestión: datos crudos por sesión con timestamps epoch.
     */
    fun export(
        context: Context,
        driver: DriverProfile,
        dayReports: List<DayReport>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        fileName: String = "DriverShield_${LocalDate.now()}.csv"
    ): File {
        val exportsDir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val outputFile = File(exportsDir, fileName)

        val genDate = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es")))

        val sb = StringBuilder()

        // ── Cabecera de metadatos ─────────────────────────────────────────
        sb.appendLine("# DriverShield - Informe de Jornada (Datos de Gestión)")
        sb.appendLine("# Generado,$genDate")
        sb.appendLine("# Conductor,${csvEscape(driver.fullName)}")
        sb.appendLine("# Documento,${csvEscape(driver.dni)}")
        sb.appendLine("# Periodo,${fmt(rangeStart)},${fmt(rangeEnd)}")
        sb.appendLine()

        // ── Tabla de sesiones crudas ──────────────────────────────────────
        sb.appendLine("date,day_of_week,session_id,start_timestamp,end_timestamp,duration_ms,work_ms,rest_ms,is_tampered,alerts")

        dayReports.sortedBy { it.date }.forEach { day ->
            day.sessions.forEach { session ->
                val hasWorkExcess = WorkLimits.hasWorkDayExcess(session.totalWorkMs)
                val hasRestExcess = WorkLimits.hasRestShiftExcess(session.totalRestMs)
                val alerts = buildList {
                    if (hasWorkExcess) add("EXC_JORNADA")
                    if (hasRestExcess) add("EXC_DESCANSO")
                    if (session.isTampered) add("RELOJ_ALTERADO")
                }.joinToString("|")

                val startTs = session.events.firstOrNull()?.timestamp ?: 0L
                val endTs = session.events.lastOrNull { it.type == EventType.END_SHIFT }?.timestamp ?: 0L
                val durationMs = if (endTs > startTs) endTs - startTs else 0L
                val dayOfWeek = day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale("es"))

                sb.appendLine(
                    "${fmt(day.date)}," +
                    "$dayOfWeek," +
                    "${session.sessionId}," +
                    "$startTs," +
                    "$endTs," +
                    "$durationMs," +
                    "${session.totalWorkMs}," +
                    "${session.totalRestMs}," +
                    "${session.isTampered}," +
                    "$alerts"
                )
            }
        }

        sb.appendLine()

        // ── Resumen semanal ───────────────────────────────────────────────
        sb.appendLine("# RESUMEN SEMANAL")
        sb.appendLine("week_start,week_end,total_work_ms,total_rest_ms,total_work_fmt,total_rest_fmt,excess_days")

        val weekFields = java.time.temporal.WeekFields.ISO
        dayReports.sortedBy { it.date }
            .groupBy {
                Pair(
                    it.date.get(weekFields.weekBasedYear()),
                    it.date.get(weekFields.weekOfWeekBasedYear())
                )
            }
            .forEach { (_, weekDays) ->
                val monday = weekDays.minByOrNull { it.date }!!.date
                    .with(java.time.DayOfWeek.MONDAY)
                val sunday = monday.plusDays(6)
                val totalWork = weekDays.sumOf { it.totalWorkMs }
                val totalRest = weekDays.sumOf { it.totalRestMs }
                val excessDays = weekDays.count { WorkLimits.hasWorkDayExcess(it.totalWorkMs) }

                sb.appendLine(
                    "${fmt(monday)}," +
                    "${fmt(sunday)}," +
                    "$totalWork," +
                    "$totalRest," +
                    "${fmtMs(totalWork)}," +
                    "${fmtMs(totalRest)}," +
                    "$excessDays"
                )
            }

        outputFile.writeText(sb.toString(), Charsets.UTF_8)
        return outputFile
    }

    private fun fmt(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    private fun fmtMs(ms: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return "${h}h ${m}min"
    }

    /**
     * Escapa un valor CSV: encierra en comillas si contiene coma, comilla o salto de línea.
     */
    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}
