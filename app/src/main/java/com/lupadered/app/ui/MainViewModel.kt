package com.lupadered.app.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lupadered.app.LupaApp
import com.lupadered.app.data.Device
import com.lupadered.app.data.DeviceEvent
import com.lupadered.app.data.ScanRecord
import com.lupadered.app.knowledge.DeviceClassifier
import com.lupadered.app.knowledge.DeviceHints
import com.lupadered.app.knowledge.DeviceType
import com.lupadered.app.net.HostSnapshot
import com.lupadered.app.net.Ipv4
import com.lupadered.app.net.LocalNetwork
import com.lupadered.app.net.NetworkInfoProvider
import com.lupadered.app.net.ScanListener
import com.lupadered.app.net.ScanPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class Presence { ONLINE, CHECKING, OFFLINE }

/** Una fila de la lista de dispositivos. */
data class DeviceItem(
    val id: Long?,
    val ip: String,
    val title: String,
    val subtitle: String,
    val type: DeviceType,
    val presence: Presence,
    val isNew: Boolean,
    val trusted: Boolean,
    val latencyMs: Long?,
    val lastSeen: Long?,
)

data class HomeState(
    val network: LocalNetwork? = null,
    val networkChecked: Boolean = false,
    val scanning: Boolean = false,
    val progress: Float = 0f,
    val phase: ScanPhase = ScanPhase.DONE,
    val devices: List<DeviceItem> = emptyList(),
    val onlineCount: Int = 0,
    val lastScan: ScanRecord? = null,
    val newDevices: List<Device> = emptyList(),
    val untrustedOnline: Int = 0,
)

data class HistoryState(
    val scans: List<ScanRecord> = emptyList(),
    val events: List<Pair<DeviceEvent, String>> = emptyList(),
)

/** Una fila del IP Manager: cada IP del rango con su estado. */
data class IpRow(val ip: String, val device: DeviceItem?, val presence: Presence?)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LupaApp
    private val repo = app.repository

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _history = MutableStateFlow(HistoryState())
    val history: StateFlow<HistoryState> = _history.asStateFlow()

    private var dbDevices: List<Device> = emptyList()
    private val live = ConcurrentHashMap<String, HostSnapshot>()
    private var scanJob: Job? = null

    init {
        refreshNetwork()
    }

    /** Lee la red actual y muestra al instante lo que ya conocemos de ella. */
    fun refreshNetwork() {
        viewModelScope.launch {
            val net = withContext(Dispatchers.IO) { NetworkInfoProvider.current(app) }
            _home.update { it.copy(network = net, networkChecked = true) }
            reloadFromDb()
        }
    }

    suspend fun reloadFromDb() {
        val net = _home.value.network ?: run {
            dbDevices = emptyList()
            publish()
            return
        }
        val (devices, last) = withContext(Dispatchers.IO) {
            repo.devices(net.key) to repo.latestScan(net.key)
        }
        dbDevices = devices
        _home.update { it.copy(lastScan = last) }
        publish()
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            val net = withContext(Dispatchers.IO) { NetworkInfoProvider.current(app) }
            _home.update { it.copy(network = net, networkChecked = true, newDevices = emptyList()) }
            if (net == null) return@launch
            reloadFromDb()

            live.clear()
            _home.update { it.copy(scanning = true, progress = 0f, phase = ScanPhase.PREPARING) }
            val ticker = launch {
                while (isActive) {
                    delay(300)
                    publish()
                }
            }
            val lock = NetworkInfoProvider.acquireMulticastLock(app)
            val started = System.currentTimeMillis()
            try {
                val result = app.scanner.scan(net.toScanTarget(), object : ScanListener {
                    override fun onPhase(phase: ScanPhase, progress: Float) {
                        _home.update { s -> s.copy(phase = phase, progress = maxOf(s.progress, progress)) }
                    }

                    override fun onHost(host: HostSnapshot) {
                        live[host.ip] = host
                    }
                })
                val summary = withContext(Dispatchers.IO) {
                    repo.applyScan(
                        networkKey = net.key,
                        subnet = net.scanSubnet.cidr,
                        hosts = result,
                        startedAt = started,
                        durationMs = System.currentTimeMillis() - started,
                        selfModel = phoneModel(),
                    )
                }
                app.ads.onScanCompleted()
                _home.update { it.copy(newDevices = summary.newDevices) }
            } finally {
                lock?.release()
                ticker.cancel()
                live.clear()
                _home.update { it.copy(scanning = false, progress = 1f, phase = ScanPhase.DONE) }
                withContext(NonCancellable) { reloadFromDb() }
                loadHistory()
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    fun dismissNewDevices() = _home.update { it.copy(newDevices = emptyList()) }

    fun trust(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setTrusted(id, true) }
            _home.update { s -> s.copy(newDevices = s.newDevices.filter { it.id != id }) }
            reloadFromDb()
        }
    }

    fun trustAll() {
        val net = _home.value.network ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.trustAll(net.key) }
            _home.update { it.copy(newDevices = emptyList()) }
            reloadFromDb()
        }
    }

    fun loadHistory() {
        val net = _home.value.network ?: return
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                val names = repo.devices(net.key).associate { it.id to it.displayName }
                HistoryState(
                    scans = repo.scans(net.key),
                    events = repo.events(net.key).map { it to (names[it.deviceId] ?: "Dispositivo eliminado") },
                )
            }
            _history.value = state
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.clearAll() }
            _history.value = HistoryState()
            _home.update { it.copy(lastScan = null, newDevices = emptyList()) }
            reloadFromDb()
        }
    }

    /** Filas del IP Manager: todas las IPs del rango. */
    fun ipRows(state: HomeState): List<IpRow> {
        val net = state.network ?: return emptyList()
        val byIp = HashMap<String, DeviceItem>()
        // Si dos registros comparten IP, gana el que está en línea o el visto más recientemente
        for (d in state.devices) {
            val cur = byIp[d.ip]
            if (cur == null || d.presence.ordinal < cur.presence.ordinal ||
                (d.presence == cur.presence && (d.lastSeen ?: 0) > (cur.lastSeen ?: 0))
            ) byIp[d.ip] = d
        }
        return net.scanSubnet.hosts().map { ip ->
            val d = byIp[ip]
            IpRow(ip, d, d?.presence)
        }
    }

    // ------------------------------------------------------------------ interno

    private fun publish() {
        val s = _home.value
        val scanning = s.scanning
        val lastScanId = s.lastScan?.id
        val items = ArrayList<DeviceItem>()
        val usedIps = HashSet<String>()

        for (d in dbDevices) {
            val liveHost = if (scanning) live[d.ip] else null
            val presence = when {
                scanning && liveHost != null -> Presence.ONLINE
                scanning -> Presence.CHECKING
                d.lastScanId == lastScanId -> Presence.ONLINE
                else -> Presence.OFFLINE
            }
            if (liveHost != null) usedIps += d.ip
            items += DeviceItem(
                id = d.id,
                ip = d.ip,
                title = d.displayName,
                subtitle = subtitleFor(d.type, d.ip, d.vendor, d.model, d.alias != null, d.name),
                type = d.type,
                presence = presence,
                isNew = !d.trusted,
                trusted = d.trusted,
                latencyMs = liveHost?.latencyMs ?: d.latencyMs,
                lastSeen = d.lastSeen,
            )
        }
        if (scanning) {
            for (h in live.values) {
                if (h.ip in usedIps || dbDevices.any { it.ip == h.ip }) continue
                val type = DeviceClassifier.classify(
                    DeviceHints(
                        names = listOfNotNull(h.bestName(), h.mdnsHostname, h.netbiosName, h.dnsName),
                        services = h.mdnsServices, openPorts = h.openPorts, server = h.ssdpServer,
                        manufacturer = h.manufacturer, model = h.model, isGateway = h.isGateway, isSelf = h.isSelf,
                    )
                )
                val title = when {
                    h.isSelf -> "Este teléfono (${phoneModel()})"
                    h.isGateway && h.bestName() == null -> "Router"
                    else -> h.bestName() ?: type.label
                }
                items += DeviceItem(
                    id = null, ip = h.ip, title = title,
                    subtitle = subtitleFor(type, h.ip, h.manufacturer, h.model, false, h.bestName()),
                    type = type, presence = Presence.ONLINE,
                    isNew = dbDevices.isNotEmpty() && !h.isSelf,
                    trusted = false, latencyMs = h.latencyMs, lastSeen = System.currentTimeMillis(),
                )
            }
        }

        val sorted = items.sortedWith(
            compareBy<DeviceItem> { it.presence.ordinal }
                .thenBy { if (it.type == DeviceType.ROUTER) 0 else if (it.type == DeviceType.THIS_PHONE) 1 else 2 }
                .thenBy(Ipv4.comparator) { it.ip }
        )
        val online = sorted.count { it.presence == Presence.ONLINE }
        val untrusted = sorted.count { it.presence == Presence.ONLINE && !it.trusted && it.id != null }
        _home.update { it.copy(devices = sorted, onlineCount = online, untrustedOnline = untrusted) }
    }

    private fun subtitleFor(
        type: DeviceType, ip: String, vendor: String?, model: String?, hasAlias: Boolean, name: String?,
    ): String {
        val parts = ArrayList<String>()
        parts += ip
        if (hasAlias && name != null) parts += name
        val brand = listOfNotNull(vendor, model).distinct().joinToString(" ")
        if (brand.isNotBlank()) parts += brand else if (type != DeviceType.UNKNOWN) parts += type.label
        return parts.joinToString(" · ")
    }

    companion object {
        fun phoneModel(): String {
            val m = Build.MODEL.orEmpty()
            val b = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
            return if (m.startsWith(b, ignoreCase = true)) m else "$b $m".trim()
        }
    }
}
