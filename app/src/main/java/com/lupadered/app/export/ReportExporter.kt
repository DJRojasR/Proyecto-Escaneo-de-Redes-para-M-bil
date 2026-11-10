package com.lupadered.app.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.lupadered.app.data.Device
import com.lupadered.app.data.DeviceEvent
import com.lupadered.app.data.DeviceRepository
import com.lupadered.app.data.NetworkProfile
import com.lupadered.app.data.ScanRecord
import com.lupadered.app.knowledge.PortKnowledge
import com.lupadered.app.knowledge.SecurityReport
import com.lupadered.app.knowledge.SecurityReview
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date

/** Reportes del inventario y el historial en PDF o CSV (Premium, RF-34). */
object ReportExporter {

    class ReportData(
        val network: NetworkProfile,
        val devices: List<Device>,
        val lastScan: ScanRecord?,
        val events: List<DeviceEvent>,
        val security: SecurityReport,
        val createdAt: Long = System.currentTimeMillis(),
    ) {
        fun isOnline(d: Device) = lastScan != null && d.lastScanId == lastScan.id
    }

    /** Reúne todo lo necesario para el reporte. Bloqueante: llamar desde Dispatchers.IO. */
    fun collect(repo: DeviceRepository, key: String): ReportData? {
        val network = repo.network(key) ?: return null
        val devices = repo.devices(key)
        val last = repo.latestScan(key)
        val events = repo.events(key, limit = 300)
        val online = devices.filter { last != null && it.lastScanId == last.id }.map { it.id }.toSet()
        return ReportData(network, devices, last, events, SecurityReview.evaluate(devices, online))
    }

    // ------------------------------------------------------------------ CSV

    fun writeCsv(data: ReportData, out: OutputStream) {
        val sb = StringBuilder()
        // BOM para que Excel reconozca las tildes
        sb.append('﻿')
        sb.appendLine(row("Red", "Nombre", "IP", "Tipo", "Grupo", "Fabricante", "Modelo", "Estado", "Conocido",
            "Puertos abiertos", "Latencia (ms)", "Visto por primera vez", "Visto por última vez"))
        for (d in data.devices.sortedWith(compareBy({ !data.isOnline(it) }, { it.ip }))) {
            sb.appendLine(
                row(
                    data.network.displayName, d.displayName, d.ip, d.type.label, d.groupName.orEmpty(),
                    d.vendor.orEmpty(), d.model.orEmpty(),
                    if (data.isOnline(d)) "En línea" else "Visto antes",
                    if (d.trusted) "Sí" else "No",
                    d.openPorts.sorted().joinToString(" "),
                    d.latencyMs?.toString().orEmpty(),
                    date(d.firstSeen), date(d.lastSeen),
                )
            )
        }
        sb.appendLine()
        sb.appendLine(row("Historial", "Equipo", "IP", "Evento", "Detalle", "Fecha"))
        val names = data.devices.associate { it.id to it.displayName }
        for (e in data.events) {
            sb.appendLine(row("", names[e.deviceId] ?: "Eliminado", e.ip, e.type.label, e.detail.orEmpty(), date(e.at)))
        }
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun row(vararg cells: String): String = cells.joinToString(";") { c ->
        // Usamos ";" porque Excel en español lo espera así; se escapan comillas.
        if (c.contains(';') || c.contains('"') || c.contains('\n')) "\"" + c.replace("\"", "\"\"") + "\"" else c
    }

    // ------------------------------------------------------------------ PDF

    private const val PAGE_W = 595  // A4 en puntos
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private val TEAL = Color.rgb(15, 110, 95)
    private val GREY = Color.rgb(90, 98, 96)

    fun writePdf(data: ReportData, out: OutputStream) {
        val doc = PdfDocument()
        val w = PdfWriter(doc)

        w.title("Reporte de red · ${data.network.displayName}")
        w.text("Generado el ${dateTime(data.createdAt)} con Lupa de Red", 10f, GREY)
        w.space(10f)

        w.heading("Resumen")
        val online = data.devices.count { data.isOnline(it) }
        w.text("Rango: ${data.network.subnet}" + (data.network.gateway?.let { " · Router: $it" } ?: ""))
        w.text("Dispositivos guardados: ${data.devices.size} · En línea en el último escaneo: $online")
        data.lastScan?.let { w.text("Último escaneo: ${dateTime(it.startedAt)} (${(it.durationMs + 500) / 1000} s)") }
        w.text("Seguridad: ${data.security.score}/100 (${data.security.grade}). ${data.security.summary}")
        w.space(8f)

        w.heading("Dispositivos")
        w.tableRow(listOf("Nombre", "IP", "Tipo", "Estado", "Puertos"), listOf(150f, 85f, 95f, 70f, 115f), bold = true)
        for (d in data.devices.sortedWith(compareBy({ !data.isOnline(it) }, { it.ip }))) {
            w.tableRow(
                listOf(
                    d.displayName + (d.groupName?.let { " [$it]" } ?: ""),
                    d.ip,
                    d.type.label,
                    if (data.isOnline(d)) "En línea" else "Visto antes",
                    d.openPorts.sorted().joinToString(", ").ifEmpty { if (d.portsScannedAt == null) "—" else "ninguno" },
                ),
                listOf(150f, 85f, 95f, 70f, 115f),
            )
        }
        w.space(8f)

        if (data.security.findings.isNotEmpty()) {
            w.heading("Revisión de seguridad")
            for (f in data.security.findings.take(25)) {
                w.text("• [${f.severity.label}] ${f.title}", 10f, bold = true)
                w.text("   ${f.detail}", 9f, GREY)
                f.steps.firstOrNull()?.let { w.text("   Qué hacer: $it", 9f, GREY) }
            }
            w.space(8f)
        }

        val portsSeen = data.devices.flatMap { it.openPorts }.toSortedSet()
        if (portsSeen.isNotEmpty()) {
            w.heading("Qué significan los puertos encontrados")
            for (p in portsSeen) {
                val info = PortKnowledge.info(p)
                w.text("$p · ${info.service} (${info.risk.label}): ${info.whatItMeans}", 9f)
            }
            w.space(8f)
        }

        if (data.events.isNotEmpty()) {
            w.heading("Historial reciente")
            val names = data.devices.associate { it.id to it.displayName }
            for (e in data.events.take(80)) {
                w.text(
                    "${dateTime(e.at)} · ${names[e.deviceId] ?: "Eliminado"} · ${e.type.label}" +
                        (if (e.detail != null) " (${e.detail} → ${e.ip})" else " (${e.ip})"),
                    9f,
                )
            }
        }

        w.finish()
        doc.writeTo(out)
        doc.close()
    }

    /** Escribe texto línea a línea y crea páginas nuevas cuando se llena una. */
    private class PdfWriter(private val doc: PdfDocument) {
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            newPage()
        }

        private fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNumber++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            page = p
            y = MARGIN
            // Pie de página
            paint.textSize = 8f
            paint.color = GREY
            paint.typeface = Typeface.DEFAULT
            p.canvas.drawText("Lupa de Red · página $pageNumber", MARGIN, PAGE_H - 20f, paint)
        }

        private fun ensure(height: Float) {
            if (y + height > PAGE_H - 40f) newPage()
        }

        fun title(text: String) {
            paint.textSize = 18f
            paint.color = TEAL
            paint.typeface = Typeface.DEFAULT_BOLD
            ensure(26f)
            y += 20f
            page!!.canvas.drawText(text, MARGIN, y, paint)
            y += 8f
        }

        fun heading(text: String) {
            paint.textSize = 13f
            paint.color = TEAL
            paint.typeface = Typeface.DEFAULT_BOLD
            ensure(30f)
            y += 18f
            page!!.canvas.drawText(text, MARGIN, y, paint)
            paint.strokeWidth = 1f
            page!!.canvas.drawLine(MARGIN, y + 4f, PAGE_W - MARGIN, y + 4f, paint)
            y += 10f
        }

        fun text(text: String, size: Float = 10f, color: Int = Color.BLACK, bold: Boolean = false) {
            paint.textSize = size
            paint.color = color
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            for (line in wrap(text, PAGE_W - 2 * MARGIN)) {
                ensure(size + 4f)
                y += size + 3f
                page!!.canvas.drawText(line, MARGIN, y, paint)
            }
        }

        fun space(h: Float) {
            y += h
        }

        fun tableRow(cells: List<String>, widths: List<Float>, bold: Boolean = false) {
            paint.textSize = 9f
            paint.color = if (bold) TEAL else Color.BLACK
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val wrapped = cells.mapIndexed { i, c -> wrap(c, widths[i] - 6f) }
            val lines = wrapped.maxOf { it.size }
            val h = lines * 12f + 4f
            ensure(h)
            var x = MARGIN
            for ((i, cellLines) in wrapped.withIndex()) {
                cellLines.forEachIndexed { li, l -> page!!.canvas.drawText(l, x, y + 12f + li * 12f, paint) }
                x += widths[i]
            }
            y += h
            val line = Paint().apply { color = Color.rgb(220, 226, 224); strokeWidth = 0.5f }
            page!!.canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, line)
        }

        private fun wrap(text: String, maxWidth: Float): List<String> {
            val out = ArrayList<String>()
            var current = ""
            for (word in text.split(' ')) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    if (current.isNotEmpty()) out += current
                    // Palabra más larga que la línea: se corta
                    var w = word
                    while (paint.measureText(w) > maxWidth && w.length > 1) {
                        var cut = w.length
                        while (cut > 1 && paint.measureText(w, 0, cut) > maxWidth) cut--
                        out += w.substring(0, cut)
                        w = w.substring(cut)
                    }
                    current = w
                }
            }
            if (current.isNotEmpty()) out += current
            return out.ifEmpty { listOf("") }
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
        }
    }

    private fun date(ts: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))
    private fun dateTime(ts: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))
}
