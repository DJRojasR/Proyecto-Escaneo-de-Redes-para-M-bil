package com.lupadered.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lupadered.app.LupaApp
import com.lupadered.app.data.Device
import com.lupadered.app.data.NetworkProfile
import com.lupadered.app.data.ScanRecord
import com.lupadered.app.export.ReportExporter
import com.lupadered.app.knowledge.PortKnowledge
import com.lupadered.app.knowledge.Risk
import com.lupadered.app.ui.components.CheckItem
import com.lupadered.app.ui.components.CheckStatus
import com.lupadered.app.knowledge.SecurityReport
import com.lupadered.app.knowledge.SecurityReview
import com.lupadered.app.net.HostInspector
import com.lupadered.app.net.LatencyProbe
import com.lupadered.app.net.NetworkInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ====================================================================== Seguridad (RF-29)

data class SecurityState(
    val loading: Boolean = true,
    val noNetwork: Boolean = false,
    val report: SecurityReport? = null,
    val scanning: Boolean = false,
    val progress: Float = 0f,
    /** Pasos del análisis en vivo: qué se revisó y qué se encontró. */
    val checks: List<CheckItem> = emptyList(),
    /** Resumen al terminar: "Revisé 8 equipos y 720 puertos en 25 s…". */
    val summary: String? = null,
    /** Hace cuánto fue el último escaneo de la red (para avisar si es antiguo). */
    val lastScanAt: Long? = null,
)

class SecurityViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LupaApp
    private val repo = app.repository
    private val inspector = HostInspector()
    private var job: Job? = null

    private val _state = MutableStateFlow(SecurityState())
    val state: StateFlow<SecurityState> = _state.asStateFlow()

    init {
        load()
    }

    private class Current(val devices: List<Device>, val online: Set<Long>, val lastScanAt: Long?)

    private suspend fun current(): Current? = withContext(Dispatchers.IO) {
        val net = NetworkInfoProvider.current(app) ?: return@withContext null
        val devices = repo.devices(net.key)
        val last = repo.latestScan(net.key)
        Current(devices, devices.filter { last != null && it.lastScanId == last.id }.map { it.id }.toSet(), last?.startedAt)
    }

    fun load() {
        viewModelScope.launch {
            val data = current()
            if (data == null) {
                _state.update { it.copy(loading = false, noNetwork = true, report = null) }
                return@launch
            }
            val report = withContext(Dispatchers.Default) { SecurityReview.evaluate(data.devices, data.online) }
            _state.update { it.copy(loading = false, noNetwork = false, report = report, lastScanAt = data.lastScanAt) }
        }
    }

    private fun setCheck(item: CheckItem) = _state.update { s ->
        val i = s.checks.indexOfFirst { it.id == item.id }
        s.copy(checks = if (i < 0) s.checks + item else s.checks.toMutableList().also { it[i] = item })
    }

    /** Revisión completa paso a paso: equipos, puertos de cada uno, desconocidos y puntaje. */
    fun scanAll() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val started = System.currentTimeMillis()
            _state.update { it.copy(scanning = true, progress = 0f, checks = emptyList(), summary = null) }
            var running: CheckItem? = null
            try {
                // 1) Equipos conectados
                val netItem = CheckItem("net", "Buscando los equipos conectados a tu red", CheckStatus.RUNNING)
                running = netItem
                setCheck(netItem)
                val data = current()
                delay(500)
                if (data == null) {
                    setCheck(netItem.copy(status = CheckStatus.BAD, result = "No estás conectado a un WiFi."))
                    running = null
                    return@launch
                }
                val targets = data.devices.filter { it.id in data.online && !it.isSelf }
                    .sortedWith(compareBy({ !it.isGateway }, { it.ip }))
                setCheck(
                    netItem.copy(
                        status = if (targets.isEmpty()) CheckStatus.WARN else CheckStatus.OK,
                        result = if (targets.isEmpty()) "No hay equipos en el último escaneo. Escanea tu red primero."
                        else "${targets.size} equipos en línea" +
                            (data.lastScanAt?.let { " (escaneo ${com.lupadered.app.ui.components.relativeTime(it)})" } ?: ""),
                    )
                )
                running = null

                // 2) Puertos de cada equipo
                var portsChecked = 0
                var high = 0
                var medium = 0
                targets.forEachIndexed { i, d ->
                    val item = CheckItem(
                        "d${d.id}",
                        "Revisando ${d.displayName} · ${d.ip}",
                        CheckStatus.RUNNING,
                        "Probando ${PortKnowledge.COMMON_PORTS.size} puertos…",
                        0f,
                    )
                    running = item
                    setCheck(item)
                    val (open, responded) = inspector.scanPorts(d.ip, PortKnowledge.COMMON_PORTS) { p ->
                        setCheck(item.copy(progress = p))
                        _state.update { it.copy(progress = (i + p) / (targets.size + 1)) }
                    }
                    withContext(Dispatchers.IO) {
                        repo.updatePorts(d.id, open, System.currentTimeMillis(), scanned = PortKnowledge.COMMON_PORTS)
                    }
                    portsChecked += PortKnowledge.COMMON_PORTS.size
                    val infos = open.map { PortKnowledge.info(it) }
                    val h = infos.count { it.risk == Risk.HIGH }
                    val m = infos.count { it.risk == Risk.MEDIUM }
                    high += h
                    medium += m
                    setCheck(
                        item.copy(
                            status = when {
                                h > 0 -> CheckStatus.BAD
                                m > 0 -> CheckStatus.WARN
                                !responded && open.isEmpty() -> CheckStatus.INFO
                                else -> CheckStatus.OK
                            },
                            progress = null,
                            result = when {
                                open.isEmpty() && !responded -> "No respondió (puede estar dormido)."
                                open.isEmpty() -> "Sin puertos abiertos. Bien protegido."
                                else -> "${open.size} abiertos: " +
                                    infos.take(4).joinToString(", ") { "${it.port} ${it.service}" } +
                                    (if (open.size > 4) "…" else "") +
                                    when {
                                        h > 0 -> " · $h de riesgo alto"
                                        m > 0 -> " · $m para revisar"
                                        else -> " · normales"
                                    }
                            },
                        )
                    )
                    running = null
                }

                // 3) Equipos desconocidos
                val unknownItem = CheckItem("unknown", "Buscando equipos que no conoces", CheckStatus.RUNNING)
                running = unknownItem
                setCheck(unknownItem)
                delay(400)
                val unknown = targets.count { !it.trusted }
                setCheck(
                    unknownItem.copy(
                        status = if (unknown > 0) CheckStatus.WARN else CheckStatus.OK,
                        result = if (unknown > 0) "$unknown sin revisar. Ábrelos y márcalos si son tuyos."
                        else "Todos los equipos conectados son conocidos.",
                    )
                )
                running = null

                // 4) Puntaje
                val scoreItem = CheckItem("score", "Calculando el puntaje de seguridad", CheckStatus.RUNNING)
                running = scoreItem
                setCheck(scoreItem)
                val fresh = current()
                val report = withContext(Dispatchers.Default) {
                    SecurityReview.evaluate(fresh?.devices.orEmpty(), fresh?.online.orEmpty())
                }
                delay(400)
                setCheck(
                    scoreItem.copy(
                        status = when {
                            report.score >= 85 -> CheckStatus.OK
                            report.score >= 50 -> CheckStatus.WARN
                            else -> CheckStatus.BAD
                        },
                        result = "${report.score}/100 · ${report.grade}",
                    )
                )
                running = null
                val secs = (System.currentTimeMillis() - started + 500) / 1000
                _state.update {
                    it.copy(
                        report = report,
                        progress = 1f,
                        summary = "Revisé ${targets.size} equipos y $portsChecked puertos en $secs s. " + when {
                            high > 0 -> "Encontré $high ${if (high == 1) "servicio de riesgo alto" else "servicios de riesgo alto"}" +
                                (if (medium > 0) " y $medium para revisar." else ".")
                            medium > 0 -> "Encontré $medium ${if (medium == 1) "detalle" else "detalles"} para revisar."
                            else -> "No encontré servicios riesgosos."
                        },
                    )
                }
            } finally {
                running?.let { setCheck(it.copy(status = CheckStatus.PENDING, progress = null, result = "Detenido")) }
                _state.update { it.copy(scanning = false) }
                if (_state.value.summary == null) load()
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}

// ====================================================================== Latencia (RF-30)

/** Una medición: null = no respondió (paquete perdido). */
data class LatencySample(val router: Long?, val internet: Long?)

data class LatencyStats(val avg: Long?, val min: Long?, val max: Long?, val jitter: Long?, val lossPercent: Int)

data class LatencyState(
    val running: Boolean = false,
    val noNetwork: Boolean = false,
    val gateway: String? = null,
    val samples: List<LatencySample> = emptyList(),
    val router: LatencyStats? = null,
    val internet: LatencyStats? = null,
    val verdict: String? = null,
    /** Intensidad de la señal WiFi en dBm (-30 excelente … -90 muy débil); null si no se pudo leer. */
    val signalDbm: Int? = null,
)

class LatencyViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LupaApp
    private var job: Job? = null

    private val _state = MutableStateFlow(LatencyState())
    val state: StateFlow<LatencyState> = _state.asStateFlow()

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val net = withContext(Dispatchers.IO) { NetworkInfoProvider.current(app) }
            val gw = net?.gateway
            if (gw == null) {
                _state.update { it.copy(noNetwork = true) }
                return@launch
            }
            _state.update { LatencyState(running = true, gateway = gw) }
            var tick = 0
            while (isActive) {
                val started = System.currentTimeMillis()
                val r = async(Dispatchers.IO) { LatencyProbe.router(gw) }
                val i = async(Dispatchers.IO) { LatencyProbe.internet() }
                // La señal se lee cada 2 segundos: Android la actualiza cada pocos segundos.
                val signal = if (tick++ % 2 == 0) {
                    withContext(Dispatchers.IO) { NetworkInfoProvider.current(app)?.wifiRssi }
                } else _state.value.signalDbm
                val sample = LatencySample(r.await(), i.await())
                _state.update { s ->
                    val samples = (s.samples + sample).takeLast(MAX_SAMPLES)
                    val rs = stats(samples.map { it.router })
                    val ins = stats(samples.map { it.internet })
                    s.copy(
                        samples = samples, router = rs, internet = ins,
                        verdict = verdict(samples.size, rs, ins), signalDbm = signal,
                    )
                }
                delay((1000 - (System.currentTimeMillis() - started)).coerceAtLeast(100))
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.update { it.copy(running = false) }
    }

    private fun stats(values: List<Long?>): LatencyStats {
        val ok = values.filterNotNull()
        val jitter = if (ok.size < 2) null else ok.zipWithNext { a, b -> abs(b - a) }.average().toLong()
        return LatencyStats(
            avg = ok.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            min = ok.minOrNull(),
            max = ok.maxOrNull(),
            jitter = jitter,
            lossPercent = if (values.isEmpty()) 0 else 100 * values.count { it == null } / values.size,
        )
    }

    private fun verdict(n: Int, r: LatencyStats, i: LatencyStats): String? {
        if (n < 8) return null
        val wifiBad = r.lossPercent >= 10 || (r.avg ?: 0) > 60 || (r.jitter ?: 0) > 40
        val netBad = i.lossPercent >= 10 || (i.avg ?: 0) > 150 || (i.jitter ?: 0) > 50
        return when {
            r.avg == null -> "Tu router no responde. Revisa que estés conectado al WiFi correcto o reinicia el router."
            wifiBad -> "El problema está dentro de tu casa: la conexión entre tu teléfono y el router es inestable. " +
                "Acércate al router, aléjalo de paredes gruesas y microondas, o usa un repetidor."
            i.avg == null -> "Tu WiFi funciona, pero no hay salida a Internet. El problema es del proveedor o del módem: " +
                "reinícialo y, si sigue igual, llama a tu proveedor."
            netBad -> "Tu WiFi va bien; la demora viene de tu proveedor de Internet. Si pasa seguido, " +
                "guarda una captura de este gráfico y muéstrasela a tu proveedor."
            else -> "Todo en orden: tu WiFi y tu Internet responden rápido y estable."
        }
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        const val MAX_SAMPLES = 60
    }
}

// ====================================================================== Mis redes (RF-33)

data class NetworksState(
    val loading: Boolean = true,
    val networks: List<NetworkProfile> = emptyList(),
    val currentKey: String? = null,
    /** Rango y router de la red actual, para mostrarla aunque aún no esté guardada. */
    val currentSubnet: String? = null,
    val currentGateway: String? = null,
)

data class NetworkDetailState(
    val profile: NetworkProfile? = null,
    val devices: List<Device> = emptyList(),
    val lastScan: ScanRecord? = null,
)

class NetworksViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LupaApp
    private val repo = app.repository

    private val _state = MutableStateFlow(NetworksState())
    val state: StateFlow<NetworksState> = _state.asStateFlow()

    private val _detail = MutableStateFlow(NetworkDetailState())
    val detail: StateFlow<NetworkDetailState> = _detail.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val (list, net) = withContext(Dispatchers.IO) {
                repo.networks() to NetworkInfoProvider.current(app)
            }
            _state.value = NetworksState(
                loading = false, networks = list, currentKey = net?.key,
                currentSubnet = net?.scanSubnet?.cidr, currentGateway = net?.gateway,
            )
        }
    }

    fun open(key: String) {
        viewModelScope.launch {
            _detail.value = withContext(Dispatchers.IO) {
                NetworkDetailState(
                    profile = repo.network(key),
                    devices = repo.devices(key).sortedByDescending { it.lastSeen },
                    lastScan = repo.latestScan(key),
                )
            }
        }
    }

    fun rename(key: String, name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.renameNetwork(key, name) }
            load()
            if (_detail.value.profile?.key == key) open(key)
        }
    }

    fun delete(key: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteNetwork(key) }
            _detail.value = NetworkDetailState()
            load()
        }
    }
}

// ====================================================================== Exportar (RF-34)

data class ExportState(
    val networks: List<NetworkProfile> = emptyList(),
    val selectedKey: String? = null,
    val working: Boolean = false,
    val message: String? = null,
)

class ExportViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LupaApp
    private val repo = app.repository

    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val (list, key) = withContext(Dispatchers.IO) { repo.networks() to NetworkInfoProvider.current(app)?.key }
            _state.update {
                it.copy(networks = list, selectedKey = list.firstOrNull { n -> n.key == key }?.key ?: list.firstOrNull()?.key)
            }
        }
    }

    fun select(key: String) = _state.update { it.copy(selectedKey = key) }

    fun suggestedName(ext: String): String {
        val name = _state.value.networks.firstOrNull { it.key == _state.value.selectedKey }?.displayName ?: "red"
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
        val safe = name.replace(Regex("[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ_-]+"), "_").trim('_')
        return "LupaDeRed_${safe}_$date.$ext"
    }

    fun export(uri: Uri, pdf: Boolean) {
        val key = _state.value.selectedKey ?: return
        viewModelScope.launch {
            _state.update { it.copy(working = true, message = null) }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val data = ReportExporter.collect(repo, key) ?: error("Red no encontrada")
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        if (pdf) ReportExporter.writePdf(data, out) else ReportExporter.writeCsv(data, out)
                    } ?: error("No se pudo abrir el archivo")
                }.isSuccess
            }
            _state.update {
                it.copy(
                    working = false,
                    message = if (ok) "Reporte guardado. Ábrelo desde tu carpeta de descargas o desde donde lo guardaste."
                    else "No se pudo guardar el reporte. Inténtalo de nuevo.",
                )
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
