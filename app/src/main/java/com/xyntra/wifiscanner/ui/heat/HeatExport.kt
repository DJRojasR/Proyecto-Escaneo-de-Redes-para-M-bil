package com.xyntra.wifiscanner.ui.heat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.xyntra.wifiscanner.data.HeatPoint
import com.xyntra.wifiscanner.i18n.tr
import com.xyntra.wifiscanner.ui.screens.signalColor
import java.text.DateFormat
import java.util.Date

/**
 * Dibuja el mapa de calor como una imagen para compartir: fondo oscuro, el mapa con
 * sus baldosas de colores, el router, dónde está la mejor y la peor señal, la leyenda
 * y un resumen. Es lo que el usuario puede mandar por WhatsApp o guardar en su galería.
 */
object HeatExport {

    private const val W = 1080
    private const val PAD = 60f

    fun render(
        floorName: String,
        points: List<HeatPoint>,
        widthM: Float,
        routerX: Float?,
        routerY: Float?,
        summary: String,
        verdict: String,
    ): Bitmap {
        val white = paint(0xFFFFFFFF.toInt())
        val muted = paint(0xFFB8BBC4.toInt())

        // Los textos de abajo pueden ocupar varias líneas: medimos antes para que la imagen
        // tenga el alto justo y nada se encime con la firma
        val summaryLayout = layout(summary, TextPaint(white).apply { textSize = 34f; typeface = Typeface.DEFAULT_BOLD })
        val verdictLayout = layout(verdict, TextPaint(muted).apply { textSize = 31f })
        val textTop = 1260f + 60f + 24f + 66f + 40f
        val h = (textTop + summaryLayout.height + 18f + verdictLayout.height + 110f).toInt()

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF1C1D22.toInt())

        // Título
        val title = TextPaint(white).apply { textSize = 58f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText(tr("Mapa WiFi · %1\$s", floorName), PAD, 110f, title)
        val sub = TextPaint(muted).apply { textSize = 32f }
        c.drawText(DateFormat.getDateInstance(DateFormat.LONG).format(Date()), PAD, 160f, sub)

        // Mapa
        val area = RectF(PAD, 200f, W - PAD, 200f + 1060f)
        c.drawRoundRect(area, 32f, 32f, paint(0xFF2A2C33.toInt()))
        val meters = HeatGeometry.toMeters(points, widthM)
        val cells = HeatGeometry.cells(meters)
        val hM = HeatGeometry.heightOf(widthM)
        val routerM = if (routerX != null && routerY != null) routerX * widthM to routerY * hM else null
        val bounds = HeatGeometry.bounds(widthM, cells, listOfNotNull(routerM))
        val inset = 30f
        val view = HeatGeometry.View(bounds, area.width() - inset * 2, area.height() - inset * 2)
        c.save()
        c.clipRect(area)
        c.translate(area.left + inset, area.top + inset)

        // Cuadrícula de 1 m para dar escala
        val grid = paint(0x22FFFFFF).apply { strokeWidth = 2f }
        var gx = kotlin.math.floor(bounds.minX)
        while (gx <= bounds.maxX) { c.drawLine(view.sx(gx), view.sy(bounds.minY), view.sx(gx), view.sy(bounds.maxY), grid); gx += 1f }
        var gy = kotlin.math.floor(bounds.minY)
        while (gy <= bounds.maxY) { c.drawLine(view.sx(bounds.minX), view.sy(gy), view.sx(bounds.maxX), view.sy(gy), grid); gy += 1f }

        // Baldosas, agrupadas por color
        val byBin = cells.groupBy { HeatGeometry.bin(it.rssi) }
        for ((bin, list) in byBin) {
            val path = Path()
            for (cell in list) {
                val x0 = view.sx(cell.i * HeatGeometry.CELL_M)
                val y0 = view.sy(cell.k * HeatGeometry.CELL_M)
                val side = HeatGeometry.CELL_M * view.scale
                path.addRect(x0, y0, x0 + side + 0.6f, y0 + side + 0.6f, Path.Direction.CW)
            }
            c.drawPath(path, paint((signalColor(bin).toArgb() and 0x00FFFFFF) or (0xE6 shl 24)))
        }

        // Contorno de la casa (el recuadro del plano)
        c.drawRect(
            view.sx(0f), view.sy(0f), view.sx(widthM), view.sy(hM),
            paint(0x55FFFFFF).apply { style = Paint.Style.STROKE; strokeWidth = 3f; pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f) },
        )

        // Mejor y peor lugar
        if (meters.size >= 5) {
            HeatGeometry.extremes(cells)?.let { (best, worst) ->
                pin(c, view.sx(best.x), view.sy(best.y), tr("Mejor señal · %1\$s dBm", best.rssi), 0xFF1B9E4B.toInt())
                pin(c, view.sx(worst.x), view.sy(worst.y), tr("Más débil · %1\$s dBm", worst.rssi), 0xFFD7301F.toInt())
            }
        }

        // Router
        routerM?.let { (x, y) ->
            val px = view.sx(x)
            val py = view.sy(y)
            c.drawCircle(px, py, 22f, white)
            c.drawCircle(px, py, 16f, paint(0xFF1A73E8.toInt()))
            val lp = TextPaint(white).apply { textSize = 30f; typeface = Typeface.DEFAULT_BOLD }
            c.drawText(tr("Router"), px + 30f, py + 10f, lp)
        }
        c.restore()

        // Leyenda
        var y = area.bottom + 60f
        val bar = RectF(PAD, y, W - PAD, y + 24f)
        val colors = listOf(-85, -75, -67, -60, -50).map { signalColor(it).toArgb() }.toIntArray()
        c.drawRoundRect(bar, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(bar.left, 0f, bar.right, 0f, colors, null, Shader.TileMode.CLAMP)
        })
        y += 66f
        val labels = listOf(tr("Débil"), tr("Regular"), tr("Buena"), tr("Excelente"))
        val lp = TextPaint(muted).apply { textSize = 30f }
        labels.forEachIndexed { i, l ->
            val x = PAD + (W - 2 * PAD) * i / (labels.size - 1)
            val tw = lp.measureText(l)
            c.drawText(l, (x - tw * i / (labels.size - 1)), y, lp)
        }

        // Resumen y consejo
        y += 40f
        c.save()
        c.translate(PAD, y)
        summaryLayout.draw(c)
        c.translate(0f, summaryLayout.height + 18f)
        verdictLayout.draw(c)
        c.restore()

        // Firma
        val foot = TextPaint(muted).apply { textSize = 28f }
        c.drawText(tr("Hecho con XYNTRA"), PAD, h - 40f, foot)
        return bmp
    }

    private fun pin(c: Canvas, x: Float, y: Float, text: String, color: Int) {
        c.drawCircle(x, y, 14f, paint(0xFFFFFFFF.toInt()))
        c.drawCircle(x, y, 9f, paint(color))
        val tp = TextPaint(paint(0xFFFFFFFF.toInt())).apply { textSize = 28f; typeface = Typeface.DEFAULT_BOLD }
        val tw = tp.measureText(text)
        val left = x - tw / 2f - 16f
        val top = y - 70f
        c.drawRoundRect(RectF(left, top, left + tw + 32f, top + 46f), 23f, 23f, paint(color))
        c.drawText(text, left + 16f, top + 33f, tp)
    }

    private fun layout(text: String, tp: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, tp, (W - 2 * PAD).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1f)
            .build()

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
}
