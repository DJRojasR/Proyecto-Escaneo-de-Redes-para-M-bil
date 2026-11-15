package com.lupadered.app.ar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lupadered.app.LupaApp
import java.util.concurrent.Executors
import com.google.ar.core.Pose
import com.lupadered.app.net.NetworkInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
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

/**
 * Una medición. Se guarda relativa al ancla más cercana (no en coordenadas fijas):
 * así, cuando ARCore corrige un desvío y mueve el ancla, la medición se mueve con ella.
 */
private class ArSample(
    val anchorId: Int,
    val local: FloatArray,
    val rssi: Int,
    var dbId: Long? = null,
    /** Posición en el mundo calculada la última vez (metros). */
    var x: Float = 0f,
    var z: Float = 0f,
)

/**
 * Una baldosa del mapa sobre el piso: el cuadrado (i, k) de [CELL_M] metros de lado
 * y la señal estimada ahí. Juntas forman la mancha de colores, como en Signal Mapper.
 */
data class ArCell(val i: Int, val k: Int, val rssi: Int)

data class ArHeatState(
    val liveRssi: Int? = null,
    val frame: ArFrameInfo? = null,
    val sampleCount: Int = 0,
    val cells: List<ArCell> = emptyList(),
    val saved: Int = 0,
    /** Hace cuánto (ms) que ARCore no reconoce el lugar; 0 si va bien. */
    val lostForMs: Long = 0,
)

/** Tamaño de cada baldosa del mapa, en metros. */
const val CELL_M = 0.2f

/** Hasta dónde se pinta alrededor de cada medición, en metros. */
private const val PAINT_RADIUS_M = 0.9f

/** Mediciones que se tienen en cuenta para estimar cada baldosa, en metros. */
private const val INFLUENCE_M = 1.6f

/** Si las mediciones se movieron más que esto (ARCore corrigió), se repinta todo. */
private const val REPAINT_SHIFT_M = 0.08f

/**
 * Recorrido con realidad aumentada. ARCore nos dice dónde está el teléfono en
 * metros; cada vez que avanzas un poco guardamos la señal WiFi en ese lugar, atada
 * al ancla más cercana. Si ARCore corrige un desvío (por ejemplo después de girar),
 * las anclas se mueven y el mapa se vuelve a pintar en su lugar correcto.
 */
class ArHeatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LupaApp
    private val repo get() = app.repository
    private var liveJob: Job? = null

    /** Todo lo que toca las mediciones y baldosas corre en este único hilo. */
    private val workerExecutor = Executors.newSingleThreadExecutor()
    private val worker = workerExecutor.asCoroutineDispatcher()

    private var floorId: Long = -1
    private var startX = 0.5f
    private var startY = 0.5f
    private var widthM = 12f

    // Solo se usan desde el hilo de OpenGL
    private var lastSampleAt = 0L
    private var lastEmitAt = 0L
    private var lastRefreshAt = 0L
    private var lastX = Float.NaN
    private var lastZ = Float.NaN
    private var prevCamX = Float.NaN
    private var prevCamZ = Float.NaN
    private var prevTrackedAt = 0L
    private var ignoreUntil = 0L
    private var lostSince = 0L

    /** El ancla del punto de partida: el plano se mide desde ahí. */
    @Volatile private var originAnchorId: Int? = null
    @Volatile private var latestAnchors: Map<Int, Pose> = emptyMap()

    // Solo se usan desde [worker]
    private val samples = ArrayList<ArSample>()
    private val cells = HashMap<Pair<Int, Int>, Int>()

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
        val now = System.currentTimeMillis()
        if (!info.tracking) {
            if (lostSince == 0L) lostSince = now
        } else {
            lostSince = 0L
        }
        // El dibujo de encima no necesita más de ~30 cuadros por segundo
        if (now - lastEmitAt >= 33) {
            lastEmitAt = now
            val lost = if (lostSince == 0L) 0L else now - lostSince
            _state.update { it.copy(frame = info, lostForMs = lost) }
        }
        if (!info.tracking) return

        val anchors = info.anchors.associate { it.id to it.pose }
        latestAnchors = anchors
        if (originAnchorId == null) originAnchorId = info.anchors.minByOrNull { it.id }?.id

        // Si al recuperar el lugar la posición "salta" de golpe, esperamos a que se asiente
        if (!prevCamX.isNaN() && now - prevTrackedAt < 600 &&
            hypot(info.camX - prevCamX, info.camZ - prevCamZ) > 1.0f
        ) {
            ignoreUntil = now + 1500
        }
        prevCamX = info.camX
        prevCamZ = info.camZ
        prevTrackedAt = now

        // Una vez por segundo revisamos si ARCore corrigió las anclas
        if (now - lastRefreshAt > 1000) {
            lastRefreshAt = now
            viewModelScope.launch(worker) { refreshPositions(anchors) }
        }

        if (now < ignoreUntil) return
        val rssi = _state.value.liveRssi ?: return
        val moved = if (lastX.isNaN()) Float.MAX_VALUE else hypot(info.camX - lastX, info.camZ - lastZ)
        // Una muestra cada ~30 cm recorridos (o cada 2 s si estás quieto en un lugar nuevo)
        if (moved < 0.3f && !(moved > 0.1f && now - lastSampleAt > 2000)) return

        // La medición se ata al ancla más cercana
        val nearest = info.anchors.minByOrNull {
            val p = it.pose
            hypot(p.tx() - info.camX, p.tz() - info.camZ)
        } ?: return
        lastX = info.camX
        lastZ = info.camZ
        lastSampleAt = now
        val local = nearest.pose.inverse().transformPoint(floatArrayOf(info.camX, info.camY, info.camZ))
        val sample = ArSample(nearest.id, local, rssi, x = info.camX, z = info.camZ)
        viewModelScope.launch(worker) {
            samples += sample
            paintAround(sample)
            publishCells()
            save(sample, anchors)
        }
    }

    /** Recalcula dónde está cada medición según las anclas (ya corregidas por ARCore). */
    private fun refreshPositions(anchors: Map<Int, Pose>) {
        if (samples.isEmpty()) return
        var maxShift = 0f
        for (s in samples) {
            val pose = anchors[s.anchorId] ?: continue
            val w = pose.transformPoint(s.local)
            maxShift = maxOf(maxShift, hypot(w[0] - s.x, w[2] - s.z))
            s.x = w[0]
            s.z = w[2]
        }
        if (maxShift < REPAINT_SHIFT_M) return
        cells.clear()
        for (s in samples) paintAround(s)
        publishCells()
    }

    private fun publishCells() {
        val list = cells.map { (key, v) -> ArCell(key.first, key.second, v) }
        _state.update { it.copy(sampleCount = samples.size, cells = list) }
    }

    /**
     * Vuelve a calcular las baldosas cercanas a una medición: cada una toma un
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

    /**
     * Pasa una posición del mundo al plano del piso: medida desde el ancla del punto
     * de partida, con X a la derecha y Z hacia atrás (abajo en el plano).
     */
    private fun toPlan(x: Float, y: Float, z: Float, anchors: Map<Int, Pose>): Pair<Float, Float>? {
        val origin = originAnchorId?.let { anchors[it] } ?: return null
        val l = origin.inverse().transformPoint(floatArrayOf(x, y, z))
        val heightM = widthM / 0.75f
        // Sin límites: si el recorrido se sale del recuadro, el mapa se agranda para mostrarlo
        return (startX + l[0] / widthM) to (startY + l[2] / heightM)
    }

    private suspend fun save(s: ArSample, anchors: Map<Int, Pose>) {
        if (floorId < 0) return
        val pose = anchors[s.anchorId] ?: return
        val w = pose.transformPoint(s.local)
        val (px, py) = toPlan(w[0], w[1], w[2], anchors) ?: return
        val id = withContext(Dispatchers.IO) { repo.addHeatPoint(floorId, px, py, s.rssi, null) }
        s.dbId = id
        _state.update { it.copy(saved = it.saved + 1) }
    }

    /**
     * Al terminar, corregimos en el plano los puntos ya guardados con la última
     * posición de las anclas: así el mapa guardado queda tan alineado como el que se vio.
     */
    suspend fun finish() {
        val anchors = latestAnchors
        if (anchors.isEmpty() || floorId < 0) return
        withContext(worker) {
            val moves = samples.mapNotNull { s ->
                val id = s.dbId ?: return@mapNotNull null
                val pose = anchors[s.anchorId] ?: return@mapNotNull null
                val w = pose.transformPoint(s.local)
                val (px, py) = toPlan(w[0], w[1], w[2], anchors) ?: return@mapNotNull null
                Triple(id, px, py)
            }
            withContext(Dispatchers.IO) { moves.forEach { (id, x, y) -> repo.moveHeatPoint(id, x, y) } }
        }
    }

    override fun onCleared() {
        super.onCleared()
        workerExecutor.shutdown()
    }
}
