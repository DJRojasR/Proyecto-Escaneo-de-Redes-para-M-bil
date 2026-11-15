package com.lupadered.app.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.lupadered.app.ui.heat.HeatExport
import com.lupadered.app.ui.heat.HeatTexts
import java.io.File
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lupadered.app.LupaApp
import com.lupadered.app.data.HeatFloor
import com.lupadered.app.data.HeatPoint
import com.lupadered.app.i18n.tr
import com.lupadered.app.net.LatencyProbe
import com.lupadered.app.net.NetworkInfoProvider
import com.google.ar.core.ArCoreApk
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Resumen de un piso para comparar pisos entre sí. */
data class FloorSummary(val floorId: Long, val count: Int, val avg: Int?, val worst: Int?)

data class HeatMapState(
    val loading: Boolean = true,
    val noWifi: Boolean = false,
    val networkKey: String? = null,
    val gateway: String? = null,
    val floors: List<HeatFloor> = emptyList(),
    val selectedFloorId: Long? = null,
    val points: List<HeatPoint> = emptyList(),
    val summaries: List<FloorSummary> = emptyList(),
    /** Señal WiFi ahora mismo (dBm), actualizada cada segundo. */
    val liveRssi: Int? = null,
    /** Lugar del plano donde el usuario dice que está (0..1). */
    val cursorX: Float? = null,
    val cursorY: Float? = null,
    val selectedPointId: Long? = null,
    val placingRouter: Boolean = false,
    val measuring: Boolean = false,
    val measureProgress: Float = 0f,
    val message: String? = null,
    /** El teléfono puede usar realidad aumentada (ARCore). */
    val arSupported: Boolean = false,
    /** Modo caminata: seguimos tus pasos y la brújula. */
    val walking: Boolean = false,
    val walkX: Float? = null,
    val walkY: Float? = null,
    /** Hacia dónde caminas, en radianes respecto a la parte de arriba del plano. */
    val walkHeading: Float = 0f,
    val steps: Int = 0,
) {
    val selectedFloor: HeatFloor? get() = floors.firstOrNull { it.id == selectedFloorId }
    val selectedPoint: HeatPoint? get() = points.firstOrNull { it.id == selectedPointId }
}

class HeatMapViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LupaApp
    private val repo get() = app.repository
    private var liveJob: Job? = null
    private var measureJob: Job? = null

    private val _state = MutableStateFlow(HeatMapState())
    val state: StateFlow<HeatMapState> = _state.asStateFlow()

    /** Se llama al abrir la pantalla: carga los pisos de la red actual y empieza a leer la señal. */
    fun start() {
        if (liveJob?.isActive == true) return
        liveJob = viewModelScope.launch {
            val net = withContext(Dispatchers.IO) { NetworkInfoProvider.current(app) }
            if (net == null || !net.isWifi) {
                _state.update { it.copy(loading = false, noWifi = true) }
                return@launch
            }
            val key = net.key
            withContext(Dispatchers.IO) {
                if (repo.heatFloors(key).isEmpty()) repo.addHeatFloor(key, tr("Piso 1"))
            }
            _state.update { it.copy(networkKey = key, gateway = net.gateway, noWifi = false) }
            reload(selectFirst = _state.value.selectedFloorId == null)
            launch { checkAr() }
            while (isActive) {
                val rssi = withContext(Dispatchers.IO) { NetworkInfoProvider.current(app)?.wifiRssi }
                _state.update { it.copy(liveRssi = rssi) }
                delay(1000)
            }
        }
    }

    fun stop() {
        liveJob?.cancel()
        measureJob?.cancel()
        stopWalk()
        _state.update { it.copy(measuring = false) }
    }

    /** ARCore a veces tarda en responder si el teléfono es compatible: preguntamos unas veces. */
    private suspend fun checkAr() {
        repeat(10) {
            val a = try { ArCoreApk.getInstance().checkAvailability(app) } catch (_: Throwable) { return }
            if (!a.isTransient) {
                _state.update { it.copy(arSupported = a.isSupported) }
                return
            }
            delay(300)
        }
    }

    private suspend fun reload(selectFirst: Boolean = false) {
        val key = _state.value.networkKey ?: return
        val (floors, summaries) = withContext(Dispatchers.IO) {
            val f = repo.heatFloors(key)
            f to f.map { floor ->
                val pts = repo.heatPoints(floor.id)
                FloorSummary(
                    floor.id, pts.size,
                    pts.takeIf { it.isNotEmpty() }?.map { it.rssi }?.average()?.roundToInt(),
                    pts.minOfOrNull { it.rssi },
                )
            }
        }
        val current = _state.value.selectedFloorId
        val selected = if (selectFirst || floors.none { it.id == current }) floors.firstOrNull()?.id else current
        val points = selected?.let { id -> withContext(Dispatchers.IO) { repo.heatPoints(id) } }.orEmpty()
        _state.update {
            it.copy(
                loading = false, floors = floors, summaries = summaries, selectedFloorId = selected, points = points,
                selectedPointId = it.selectedPointId?.takeIf { id -> points.any { p -> p.id == id } },
            )
        }
    }

    fun selectFloor(id: Long) {
        if (_state.value.measuring) return
        stopWalk()
        _state.update { it.copy(selectedFloorId = id, cursorX = null, cursorY = null, selectedPointId = null, placingRouter = false, message = null) }
        viewModelScope.launch { reload() }
    }

    fun addFloor(name: String) {
        val key = _state.value.networkKey ?: return
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { repo.addHeatFloor(key, name.trim().ifEmpty { tr("Piso %1\$s", _state.value.floors.size + 1) }) }
            _state.update { it.copy(selectedFloorId = id, cursorX = null, cursorY = null, selectedPointId = null) }
            reload()
        }
    }

    fun renameFloor(name: String) {
        val id = _state.value.selectedFloorId ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.renameHeatFloor(id, name.trim()) }
            reload()
        }
    }

    fun deleteFloor() {
        val id = _state.value.selectedFloorId ?: return
        val key = _state.value.networkKey ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.deleteHeatFloor(id)
                if (repo.heatFloors(key).isEmpty()) repo.addHeatFloor(key, tr("Piso 1"))
            }
            reload(selectFirst = true)
        }
    }

    fun clearFloor() {
        val id = _state.value.selectedFloorId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.clearHeatPoints(id) }
            _state.update { it.copy(selectedPointId = null) }
            reload()
        }
    }

    fun startPlacingRouter() = _state.update { it.copy(placingRouter = true, selectedPointId = null, message = null) }

    fun cancelPlacingRouter() = _state.update { it.copy(placingRouter = false) }

    fun removeRouter() {
        val id = _state.value.selectedFloorId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setHeatRouter(id, null, null) }
            reload()
        }
    }

    /**
     * Toque en el plano. Si cae sobre un punto ya medido, lo selecciona; si estamos
     * marcando el router, lo ubica ahí; si no, mueve la cruz a ese lugar.
     */
    fun tap(x: Float, y: Float, hitPointId: Long?) {
        val s = _state.value
        if (s.measuring) return
        if (s.walking) {
            // Durante el recorrido, tocar el plano corrige dónde estás
            _state.update { it.copy(walkX = x, walkY = y) }
            lastSampleX = x
            lastSampleY = y
            return
        }
        if (s.placingRouter) {
            val id = s.selectedFloorId ?: return
            viewModelScope.launch {
                withContext(Dispatchers.IO) { repo.setHeatRouter(id, x, y) }
                _state.update { it.copy(placingRouter = false) }
                reload()
            }
            return
        }
        if (hitPointId != null) {
            _state.update { it.copy(selectedPointId = if (it.selectedPointId == hitPointId) null else hitPointId, message = null) }
        } else {
            _state.update { it.copy(cursorX = x, cursorY = y, selectedPointId = null, message = null) }
        }
    }

    fun setFloorWidth(widthM: Float) {
        val id = _state.value.selectedFloorId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setHeatFloorWidth(id, widthM.coerceIn(3f, 60f)) }
            reload()
        }
    }

    // ------------------------------------------------------------ modo caminata

    private val sensors = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var listener: SensorEventListener? = null
    private var azimuth = 0f
    private var refAzimuth: Float? = null
    private var accFiltered = SensorManager.GRAVITY_EARTH
    private var aboveThreshold = false
    private var lastStepAt = 0L
    private var lastSampleX = 0f
    private var lastSampleY = 0f
    private var lastSampleAt = 0L
    private val rotation = FloatArray(9)

    /**
     * Empieza en la cruz. La dirección en que miras al empezar es la "parte de
     * arriba" del plano; desde ahí contamos pasos (con el acelerómetro) y giros
     * (con el giroscopio/brújula) para saber por dónde vas.
     */
    fun startWalk() {
        val s = _state.value
        // Si no tocaste el plano, empezamos en el centro
        val x = s.cursorX ?: 0.5f
        val y = s.cursorY ?: 0.5f
        val sm = sensors ?: return
        val rot = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (rot == null || acc == null) {
            _state.update { it.copy(message = tr("Tu teléfono no tiene los sensores necesarios para el modo caminata. Mide punto por punto.")) }
            return
        }
        refAzimuth = null
        accFiltered = SensorManager.GRAVITY_EARTH
        aboveThreshold = false
        lastSampleX = x
        lastSampleY = y
        lastSampleAt = 0L
        _state.update {
            it.copy(walking = true, walkX = x, walkY = y, steps = 0, cursorX = null, cursorY = null, selectedPointId = null, message = null)
        }
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> onAccel(e.values)
                    else -> onRotation(e.values)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = l
        sm.registerListener(l, rot, SensorManager.SENSOR_DELAY_GAME)
        sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME)
        // La primera muestra, justo donde empiezas
        sampleAt(x, y, force = true)
    }

    fun stopWalk() {
        listener?.let { sensors?.unregisterListener(it) }
        listener = null
        if (_state.value.walking) _state.update { it.copy(walking = false) }
    }

    private fun onRotation(v: FloatArray) {
        SensorManager.getRotationMatrixFromVector(rotation, v)
        // "Adelante" = el borde superior del teléfono (si lo llevas plano) más la
        // dirección de la cámara (si lo llevas parado): sirve en cualquier postura.
        val east = rotation[1] - rotation[2]
        val north = rotation[4] - rotation[5]
        if (hypot(east, north) < 0.2f) return
        azimuth = atan2(east, north)
        val ref = refAzimuth ?: azimuth.also { refAzimuth = it }
        _state.update { it.copy(walkHeading = azimuth - ref) }
    }

    private fun onAccel(v: FloatArray) {
        val mag = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        accFiltered = accFiltered * 0.8f + mag * 0.2f
        val now = System.currentTimeMillis()
        if (!aboveThreshold && accFiltered > STEP_HIGH) {
            aboveThreshold = true
            if (now - lastStepAt > 300) {
                lastStepAt = now
                onStep()
            }
        } else if (aboveThreshold && accFiltered < STEP_LOW) {
            aboveThreshold = false
        }
    }

    private fun onStep() {
        val s = _state.value
        val floor = s.selectedFloor ?: return
        val x0 = s.walkX ?: return
        val y0 = s.walkY ?: return
        val heading = s.walkHeading
        val widthM = floor.widthM
        val heightM = widthM / 0.75f
        // Sin límites: si caminas fuera del recuadro, el mapa se agranda para mostrarlo
        val x = x0 + sin(heading) * STEP_M / widthM
        val y = y0 - cos(heading) * STEP_M / heightM
        _state.update { it.copy(walkX = x, walkY = y, steps = it.steps + 1) }
        sampleAt(x, y, force = false)
    }

    /** Guarda la señal en el lugar actual si avanzaste lo suficiente desde la última muestra. */
    private fun sampleAt(x: Float, y: Float, force: Boolean) {
        val s = _state.value
        val floor = s.selectedFloor ?: return
        val rssi = s.liveRssi ?: return
        val dist = hypot((x - lastSampleX) * floor.widthM, (y - lastSampleY) * floor.widthM / 0.75f)
        val now = System.currentTimeMillis()
        if (!force && dist < 0.6f && now - lastSampleAt < 4000) return
        lastSampleX = x
        lastSampleY = y
        lastSampleAt = now
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { repo.addHeatPoint(floor.id, x, y, rssi, null) }
            val point = HeatPoint(id, x, y, rssi, null, now)
            _state.update { if (it.selectedFloorId == floor.id) it.copy(points = it.points + point) else it }
        }
    }

    // ------------------------------------------------------------ exportar imagen

    private suspend fun renderImage(): Bitmap? {
        val s = _state.value
        val floor = s.selectedFloor ?: return null
        if (s.points.isEmpty()) return null
        return withContext(Dispatchers.Default) {
            HeatExport.render(
                floorName = floor.name,
                points = s.points,
                widthM = floor.widthM,
                routerX = floor.routerX,
                routerY = floor.routerY,
                summary = HeatTexts.summary(s.points).orEmpty(),
                verdict = HeatTexts.verdict(s.points),
            )
        }
    }

    /** Guarda la imagen del mapa donde el usuario eligió (galería, Descargas…). */
    fun saveImage(uri: Uri) {
        viewModelScope.launch {
            val bmp = renderImage() ?: return@launch
            val ok = withContext(Dispatchers.IO) {
                try {
                    app.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
                } catch (_: Exception) {
                    false
                }
            }
            _state.update { it.copy(message = if (ok) tr("Imagen guardada.") else tr("No se pudo guardar la imagen. Inténtalo de nuevo.")) }
        }
    }

    /** Prepara la imagen para compartirla (WhatsApp, correo…) y devuelve su dirección. */
    fun shareImage(onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val bmp = renderImage() ?: return@launch
            val uri = withContext(Dispatchers.IO) {
                try {
                    val dir = File(app.cacheDir, "shared").apply { mkdirs() }
                    val file = File(dir, "mapa-wifi.png")
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                } catch (_: Exception) {
                    null
                }
            }
            if (uri != null) onReady(uri)
            else _state.update { it.copy(message = tr("No se pudo preparar la imagen. Inténtalo de nuevo.")) }
        }
    }

    /** Al volver de la realidad aumentada, recargamos lo que se guardó. */
    fun refresh() {
        viewModelScope.launch { reload() }
    }

    fun deleteSelectedPoint() {
        val id = _state.value.selectedPointId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteHeatPoint(id) }
            _state.update { it.copy(selectedPointId = null) }
            reload()
        }
    }

    /**
     * Mide en el lugar de la cruz: lee la señal varias veces durante unos
     * segundos (Android la actualiza cada poco) y hace ping al router.
     */
    fun measureHere() {
        val s = _state.value
        val floorId = s.selectedFloorId ?: return
        val x = s.cursorX ?: return
        val y = s.cursorY ?: return
        if (measureJob?.isActive == true) return
        measureJob = viewModelScope.launch {
            _state.update { it.copy(measuring = true, measureProgress = 0f, message = null) }
            try {
                val readings = ArrayList<Int>()
                val pings = ArrayList<Long>()
                val steps = 8
                for (i in 0 until steps) {
                    val started = System.currentTimeMillis()
                    withContext(Dispatchers.IO) { NetworkInfoProvider.current(app)?.wifiRssi }?.let { readings += it }
                    if (i % 3 == 0) {
                        s.gateway?.let { gw -> withContext(Dispatchers.IO) { LatencyProbe.router(gw) } }?.let { pings += it }
                    }
                    _state.update { it.copy(measureProgress = (i + 1f) / steps) }
                    delay((500 - (System.currentTimeMillis() - started)).coerceAtLeast(50))
                }
                if (readings.isEmpty()) {
                    _state.update { it.copy(message = tr("No pudimos leer la señal WiFi. Revisa que sigas conectado a tu WiFi.")) }
                    return@launch
                }
                // Usamos la mediana: un valor raro aislado no cambia el resultado
                val rssi = readings.sorted()[readings.size / 2]
                val ping = pings.takeIf { it.isNotEmpty() }?.sorted()?.get(pings.size / 2)?.toInt()
                withContext(Dispatchers.IO) { repo.addHeatPoint(floorId, x, y, rssi, ping) }
                _state.update { it.copy(cursorX = null, cursorY = null) }
                reload()
            } finally {
                _state.update { it.copy(measuring = false) }
            }
        }
    }

    override fun onCleared() {
        stopWalk()
    }

    private companion object {
        /** Largo promedio de un paso, en metros. */
        const val STEP_M = 0.65f
        /** Umbrales de aceleración (m/s²) para reconocer un paso. */
        const val STEP_HIGH = 10.8f
        const val STEP_LOW = 9.9f
    }
}
