package com.xyntra.wifiscanner.ui.heat

import com.xyntra.wifiscanner.data.HeatPoint
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Cálculos del mapa de calor, compartidos por la pantalla y por la imagen que se exporta.
 *
 * Las mediciones se guardan en coordenadas del plano (0..1 = el recuadro de la casa),
 * pero un recorrido puede salirse de ese recuadro. Aquí todo se pasa a metros y la vista
 * se ajusta para que entre todo lo medido, sin recortar nada.
 */
object HeatGeometry {

    /** Proporción del recuadro de la casa (ancho / alto). */
    const val PLAN_ASPECT = 0.75f

    /** Lado de cada baldosa del mapa, en metros. */
    const val CELL_M = 0.25f

    /** Hasta dónde se pinta alrededor de cada medición, en metros. */
    private const val PAINT_RADIUS_M = 0.9f

    /** Mediciones que cuentan para estimar cada baldosa, en metros. */
    private const val INFLUENCE_M = 1.6f

    data class Pt(val x: Float, val y: Float, val rssi: Int)
    data class Cell(val i: Int, val k: Int, val rssi: Int)

    data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        val width get() = maxX - minX
        val height get() = maxY - minY
        fun include(x: Float, y: Float) = Bounds(min(minX, x), min(minY, y), max(maxX, x), max(maxY, y))
        fun grow(m: Float) = Bounds(minX - m, minY - m, maxX + m, maxY + m)
    }

    fun heightOf(widthM: Float) = widthM / PLAN_ASPECT

    /** Pasa las mediciones a metros. */
    fun toMeters(points: List<HeatPoint>, widthM: Float): List<Pt> {
        val h = heightOf(widthM)
        return points.map { Pt(it.x * widthM, it.y * h, it.rssi) }
    }

    /**
     * Las baldosas pintadas: cada una con el promedio de las mediciones cercanas,
     * pesando más las más cercanas. Lejos de cualquier medición no se pinta nada.
     */
    fun cells(points: List<Pt>): List<Cell> {
        if (points.isEmpty()) return emptyList()
        // Agrupamos las mediciones por metro cuadrado para buscar vecinas rápido
        val buckets = HashMap<Long, MutableList<Pt>>()
        fun key(bx: Int, by: Int) = (bx.toLong() shl 32) or (by.toLong() and 0xffffffffL)
        for (p in points) buckets.getOrPut(key(floor(p.x).toInt(), floor(p.y).toInt())) { ArrayList() }.add(p)

        val todo = HashSet<Long>()
        val r = (PAINT_RADIUS_M / CELL_M).toInt() + 1
        for (p in points) {
            val ci = floor(p.x / CELL_M).toInt()
            val ck = floor(p.y / CELL_M).toInt()
            for (i in ci - r..ci + r) for (k in ck - r..ck + r) {
                if (hypot((i + 0.5f) * CELL_M - p.x, (k + 0.5f) * CELL_M - p.y) <= PAINT_RADIUS_M) todo += key(i, k)
            }
        }
        val reach = INFLUENCE_M.toInt() + 1
        val out = ArrayList<Cell>(todo.size)
        for (code in todo) {
            val i = (code shr 32).toInt()
            val k = code.toInt()
            val cx = (i + 0.5f) * CELL_M
            val cy = (k + 0.5f) * CELL_M
            val bx = floor(cx).toInt()
            val by = floor(cy).toInt()
            var sumW = 0.0
            var sum = 0.0
            for (dx in -reach..reach) for (dy in -reach..reach) {
                val list = buckets[key(bx + dx, by + dy)] ?: continue
                for (p in list) {
                    val d = hypot(cx - p.x, cy - p.y)
                    if (d > INFLUENCE_M) continue
                    val w = 1.0 / (d * d + 0.05)
                    sumW += w
                    sum += w * p.rssi
                }
            }
            if (sumW > 0) out += Cell(i, k, (sum / sumW).toInt())
        }
        return out
    }

    /** Todo lo que hay que mostrar: el recuadro de la casa y lo medido, aunque se salga. */
    fun bounds(widthM: Float, cells: List<Cell>, extra: List<Pair<Float, Float>> = emptyList()): Bounds {
        var b = Bounds(0f, 0f, widthM, heightOf(widthM))
        for (c in cells) {
            b = b.include(c.i * CELL_M, c.k * CELL_M).include((c.i + 1) * CELL_M, (c.k + 1) * CELL_M)
        }
        for ((x, y) in extra) b = b.include(x, y)
        return b.grow(0.5f)
    }

    /**
     * Cómo se dibuja: metros → píxeles, centrado y sin deformar. [zoom] y el
     * desplazamiento ([panX], [panY], en píxeles) los controla el usuario con los dedos.
     */
    class View(
        b: Bounds,
        private val canvasW: Float,
        private val canvasH: Float,
        zoom: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f,
    ) {
        private val base = min(canvasW / b.width, canvasH / b.height)
        val scale = base * zoom
        // Encajamos todo y luego ampliamos alrededor del centro de la vista
        private val fitOffX = (canvasW - b.width * base) / 2f - b.minX * base
        private val fitOffY = (canvasH - b.height * base) / 2f - b.minY * base
        private val offX = (fitOffX - canvasW / 2f) * zoom + canvasW / 2f + panX
        private val offY = (fitOffY - canvasH / 2f) * zoom + canvasH / 2f + panY
        fun sx(x: Float) = x * scale + offX
        fun sy(y: Float) = y * scale + offY
        fun mx(px: Float) = (px - offX) / scale
        fun my(py: Float) = (py - offY) / scale
    }

    /**
     * La zona con mejor y con peor señal según los colores del mapa (no un punto
     * suelto, que puede ser una lectura rara). Devuelve el centro de cada baldosa en metros.
     */
    fun extremes(cells: List<Cell>): Pair<Pt, Pt>? {
        if (cells.size < 4) return null
        val best = cells.maxBy { it.rssi }
        val worst = cells.minBy { it.rssi }
        fun center(c: Cell) = Pt((c.i + 0.5f) * CELL_M, (c.k + 0.5f) * CELL_M, c.rssi)
        return center(best) to center(worst)
    }

    /** Agrupa de 3 en 3 dBm: se ve suave y se dibuja rápido. */
    fun bin(rssi: Int) = floor(rssi / 3f).toInt() * 3
}
