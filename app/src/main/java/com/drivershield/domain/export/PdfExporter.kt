package com.drivershield.domain.export

import android.content.Context
import com.drivershield.domain.model.DayReport
import com.drivershield.domain.model.DriverProfile
import com.drivershield.domain.util.WorkLimits
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.TimeUnit

object PdfExporter {

    private val COLOR_HEADER_BG    = DeviceRgb(220, 220, 220)
    private val COLOR_WEEK_TITLE   = DeviceRgb(30, 30, 30)
    private val COLOR_SUNDAY_BG    = DeviceRgb(200, 200, 200)   // Cierre semanal
    private val COLOR_ALERT_BG     = DeviceRgb(255, 220, 220)
    private val COLOR_RED          = DeviceRgb(180, 0, 0)
    private val COLOR_DARK_TEXT    = DeviceRgb(30, 30, 30)

    /**
     * Genera el PDF en la carpeta de cache de la app (accesible via FileProvider)
     * y devuelve el File resultante.
     */
    fun export(
        context: Context,
        driver: DriverProfile,
        dayReports: List<DayReport>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        fileName: String = "DriverShield_${LocalDate.now()}.pdf"
    ): File {
        val exportsDir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val outputFile = File(exportsDir, fileName)

        val pdfWriter = PdfWriter(outputFile)
        val pdf = PdfDocument(pdfWriter)
        val document = Document(pdf)
        document.setMargins(36f, 36f, 50f, 36f)

        val genDate = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("es")))

        // ── Cabecera del documento ────────────────────────────────────────
        document.add(
            Paragraph("DriverShield · Informe de Jornada")
                .setBold().setFontSize(18f)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2f)
        )
        document.add(
            Paragraph("Generado: $genDate   ·   Período: ${fmt(rangeStart)} – ${fmt(rangeEnd)}")
                .setFontSize(9f).setTextAlignment(TextAlignment.CENTER)
                .setFontColor(DeviceRgb(100, 100, 100))
                .setMarginBottom(14f)
        )

        // ── Datos del conductor ───────────────────────────────────────────
        val driverTable = Table(floatArrayOf(1f, 1f))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(18f)

        driverTable.addCell(infoCell("Conductor: ${driver.fullName.ifBlank { "—" }}"))
        driverTable.addCell(infoCell("Documento: ${driver.dni.ifBlank { "—" }}"))
        document.add(driverTable)

        // ── Agrupar días por semana ISO ───────────────────────────────────
        val weekFields = WeekFields.ISO
        val grouped = dayReports
            .sortedBy { it.date }
            .groupBy { Pair(it.date.get(weekFields.weekBasedYear()), it.date.get(weekFields.weekOfWeekBasedYear())) }

        // Pre-calcular hash data acumulando texto plano
        val hashInput = StringBuilder()
        hashInput.append("${driver.fullName}|${driver.dni}|$genDate")

        grouped.forEach { (_, weekDays) ->
            val monday = weekDays.first().date.with(DayOfWeek.MONDAY)
            val sunday = monday.plusDays(6)

            // Título de semana
            document.add(
                Paragraph("Semana ${fmt(monday)} – ${fmt(sunday)}")
                    .setBold().setFontSize(11f)
                    .setFontColor(COLOR_WEEK_TITLE)
                    .setMarginTop(10f).setMarginBottom(4f)
            )

            // Tabla de la semana: Fecha | Día | Trabajo | Descanso | Progresivo | Alertas
            val table = Table(floatArrayOf(1.8f, 1.4f, 1.4f, 1.4f, 1.6f, 2.4f))
                .setWidth(UnitValue.createPercentValue(100f))
                .setMarginBottom(6f)

            listOf("Fecha", "Día", "Trabajo", "Descanso", "Progresivo", "Alertas")
                .forEach { table.addHeaderCell(headerCell(it)) }

            var weekAccumulatedMs = 0L
            weekDays.sortedBy { it.date }.forEach { report ->
                weekAccumulatedMs += report.totalWorkMs
                val isSunday = report.date.dayOfWeek == DayOfWeek.SUNDAY
                val hasWorkExcess = WorkLimits.hasWorkDayExcess(report.totalWorkMs)
                val hasRestExcess = WorkLimits.hasRestShiftExcess(report.totalRestMs)
                val isAlert = hasWorkExcess || hasRestExcess

                val bgColor = when {
                    isSunday -> COLOR_SUNDAY_BG
                    isAlert -> COLOR_ALERT_BG
                    else -> null
                }

                val dayName = report.date.dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale("es"))
                    .replaceFirstChar { it.uppercase() }

                val alertText = buildString {
                    if (isSunday) append("CIERRE")
                    if (hasWorkExcess) { if (isNotEmpty()) append(" | "); append("EXC.JORNADA") }
                    if (hasRestExcess) { if (isNotEmpty()) append(" | "); append("EXC.DESCANSO") }
                }

                table.addCell(dataCell(fmt(report.date), bgColor, if (isSunday) 9f else 10f, isSunday))
                table.addCell(dataCell(dayName, bgColor, if (isSunday) 9f else 10f, isSunday))
                table.addCell(dataCell(fmtMs(report.totalWorkMs), bgColor, 10f, hasWorkExcess, if (hasWorkExcess) COLOR_RED else COLOR_DARK_TEXT))
                table.addCell(dataCell(fmtMs(report.totalRestMs), bgColor, 10f, hasRestExcess, if (hasRestExcess) COLOR_RED else COLOR_DARK_TEXT))
                table.addCell(dataCell(fmtMs(weekAccumulatedMs), bgColor, 10f))
                table.addCell(dataCell(alertText, bgColor, 8f, isAlert || isSunday, if (isAlert) COLOR_RED else COLOR_DARK_TEXT))

                hashInput.append("|${report.date}:${report.totalWorkMs}:${report.totalRestMs}")
            }

            // Fila de total semanal
            val totalWork = weekDays.sumOf { it.totalWorkMs }
            val totalRest = weekDays.sumOf { it.totalRestMs }
            val totalBg = DeviceRgb(210, 210, 210)
            table.addCell(totalCell("TOTAL SEMANA", totalBg, 3))
            table.addCell(totalCell(fmtMs(totalWork), totalBg, 1))
            table.addCell(totalCell(fmtMs(totalRest), totalBg, 1))
            table.addCell(totalCell("", totalBg, 1))

            document.add(table)
        }

        // ── Hash de verificación (SHA-256 truncado a 16 chars) ────────────
        val hash = sha256(hashInput.toString()).take(16).uppercase()
        document.add(
            Paragraph("Hash de verificación: $hash   ·   DriverShield v1.0")
                .setFontSize(7f)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(DeviceRgb(150, 150, 150))
                .setMarginTop(20f)
        )

        document.close()
        return outputFile
    }

    // ── Helpers de celda ─────────────────────────────────────────────────────

    private fun headerCell(text: String): Cell =
        Cell().add(Paragraph(text).setBold().setFontSize(9f))
            .setBackgroundColor(COLOR_HEADER_BG)
            .setPadding(5f)
            .setTextAlignment(TextAlignment.CENTER)

    private fun dataCell(
        text: String,
        bg: DeviceRgb? = null,
        fontSize: Float = 10f,
        bold: Boolean = false,
        color: DeviceRgb = COLOR_DARK_TEXT
    ): Cell {
        val p = Paragraph(text).setFontSize(fontSize).setFontColor(color)
        if (bold) p.setBold()
        val cell = Cell().add(p).setPadding(5f).setTextAlignment(TextAlignment.CENTER)
        if (bg != null) cell.setBackgroundColor(bg)
        return cell
    }

    private fun totalCell(text: String, bg: DeviceRgb, colspan: Int): Cell =
        Cell(1, colspan)
            .add(Paragraph(text).setBold().setFontSize(9f).setFontColor(COLOR_DARK_TEXT))
            .setBackgroundColor(bg).setPadding(5f)
            .setTextAlignment(TextAlignment.CENTER)

    private fun infoCell(text: String): Cell =
        Cell().add(Paragraph(text).setFontSize(10f))
            .setBackgroundColor(COLOR_HEADER_BG).setPadding(6f)

    // ── Utilidades ───────────────────────────────────────────────────────────

    private fun fmt(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    private fun fmtMs(ms: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return "${h}h ${m}min"
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
