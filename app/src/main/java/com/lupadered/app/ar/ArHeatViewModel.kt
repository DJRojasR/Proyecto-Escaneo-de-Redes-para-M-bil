package com.lupadered.app.ar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lupadered.app.LupaApp
import com.lupadered.app.net.NetworkInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/** Una medición en el espacio real (metros). */
data class ArSample(val x: Float, val z: Float, val rssi: Int)

/**
 * Una baldosa del mapa sobre el piso: el cuadrado (i, k) de [CELL_M] metros de lado
 * y la señal estimada ahí. Juntas forman la mancha de colores, como en Signal Mapper.
 */
data class ArCell(val i: Int, val k: Int, val rssi: Int)

data class ArHeatState(
    val liveRssi: Int? = null,
    val frame: ArFrameInfo? = null,
    val samples: List<ArSample> = emptyList(),
    val cells: List<ArCell> = emptyList(),
    val saved: Int = 0,
)

/** Tamaño de cada baldosa del mapa, en metros. */
const val CELL_M = 0.2f

/** Hasta dónde se pinta alrededor de cada medición, en metros. */
private const val PAINT_RADIUS_M = 0.9f

/** Mediciones que se tienen en cuenta para estimar cada baldosa, en metros. */
private const val INFLUENCE_M = 1.6f

/**
 * Recorrido con realidad aumentada. ARCore nos dice dónde está el teléfono en
 * metros; cada vez que avanzas un poco guardamos la señal WiFi en ese lugar. Las
 * mediciones también se pasan al plano del piso elegido, partiendo del punto
 * donde empezaste y con la parte de arriba del plano hacia donde mirabas.
 */
class ArHeatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LupaApp
    private val repo get() = app.repository
    private var liveJob: Job? = null

    private var floorId: Long = -1
    private var startX = 0.5f
    private var startY = 0.5f
    private var widthM = 12f
    private var lastSampleAt = 0L
    private var lastEmitAt = 0L
    private var lastX = Float.NaN
    private var lastZ = Float.NaN
    private var originX = Float.NaN
    private var originZ = Float.NaN

    private val _state = MutableStateFlow(ArHeatState())
    val state: StateFlow<ArHeatState> = _state.asStateFlow()

    fun setup(floorId: Long, startX: Float, startY: Float) {
        if (this.floorId == floorId) return
        this.floorId = floorId
        this.startX = startX
        this.startY = startY
        viewModelScope.launch {
            widthM = withContext(Dispatchers.IO) { repo.heatFloor(floorId)?.widthM } ?: 12f
        }
    }

    fun start() {
        if (liveJob?.isActive == true) return
        liveJob = viewModelScope.launch {
            while (isActive) {
                val rssi = withContext(Dispatchers.IO) { NetworkInfoProvider.current(app)?.wifiRssi }
                _state.update { it.copy(liveRssi = rssi) }
                delay(700)
            }
        }
    }

    fun stop() {
        liveJob?.cancel()
    }

    /** Llega desde el hilo de OpenGL en cada cuadro. */
    fun onFrame(info: ArFrameInfo) {
        val t = System.currentTimeMillis()
        // El dibujo de encima no necesita más de ~30 cuadros por segundo
        if (t - lastEmitAt >= 33) {
            lastEmitAt = t
            _state.update { it.copy(frame = info) }
        }
        if (!info.tracking) return
        if (originX.isNaN()) {
            originX = info.camX
            originZ = info.camZ
        }
        val rssi = _state.value.liveRssi ?: return
        val now = System.currentTimeMillis()
        val moved = if (lastX.isNaN()) Float.MAX_VALUE else hypot(info.camX - lastX, info.camZ - lastZ)
        // Una muestra cada ~30 cm recorridos (o cada 2 s si estás quieto en un lugar nuevo)
        if (moved < 0.3f && !(moved > 0.1f && now - lastSampleAt > 2000)) return
        lastX = info.camX
        lastZ = info.camZ
        lastSampleAt = now
        val sample = ArSample(info.camX, info.camZ, rssi)
        samples += sample
        paintAround(sample)
        _state.update { it.copy(samples = samples.toList(), cells = cells.map { (key, v) -> ArCell(key.first, key.second, v) }) }
        save(sample)
    }

    private val samples = ArrayList<ArSample>()
    private val cells = HashMap<Pair<Int, Int>, Int>()

    /**
     * Vuelve a calcular las baldosas cercanas a la nueva medición: cada una toma un
     * promedio de las mediciones de alrededor, pesando más las más cercanas.
     */
    private fun paintAround(s: ArSample) {
        val r = (PAINT_RADIUS_M / CELL_M).toInt() + 1
        val ci = kotlin.math.floor(s.x / CELL_M).toInt()
        val ck = kotlin.math.floor(s.z / CELL_M).toInt()
        val nearby = samples.filter { hypot(it.x - s.x, it.z - s.z) <= PAINT_RADIUS_M + INFLUENCE_M }
        for (i in ci - r..ci + r) for (k in ck - r..ck + r) {
            val cx = (i + 0.5f) * CELL_M
            val cz = (k + 0.5f) * CELL_M
            if (hypot(cx - s.x, cz - s.z) > PAINT_RADIUS_M) continue
            var sumW = 0.0
            var sum = 0.0
            for (p in nearby) {
                val d = hypot(cx - p.x, cz - p.z)
                if (d > INFLUENCE_M) continue
                val w = 1.0 / (d * d + 0.05)
                sumW += w
                sum += w * p.rssi
            }
            if (sumW > 0) cells[i to k] = (sum / sumW).toInt()
        }
    }

    /** Pasa la muestra al plano: X a la derecha y Z hacia atrás (abajo en el plano). */
    private fun save(s: ArSample) {
        if (floorId < 0) return
        val heightM = widthM / 0.75f
        // Sin límites: si el recorrido se sale del recuadro, el mapa se agranda para mostrarlo
        val px = startX + (s.x - originX) / widthM
        val py = startY + (s.z - originZ) / heightM
        viewModelScope.launch(Dispatchers.IO) {
            repo.addHeatPoint(floorId, px, py, s.rssi, null)
            _state.update { it.copy(saved = it.saved + 1) }
        }
    }
}
