package com.lupadered.app.ui

import com.lupadered.app.i18n.tr
import android.app.Application
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.lupadered.app.data.NetworkProfile
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
import com.lupadered.app.data.AppSettings
import com.lupadered.app.widget.DevicesWidget
import kotlinx.coroutines.sync.withLock
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
    val customIcon: String? = null,
    val groupName: String? = null,
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
    /** Grupos creados por el usuario en esta red (RF-32). */
    val groups: List<String> = emptyList(),
    /** Grupo elegido en el filtro; null = todos. */
    val selectedGroup: String? = null,
    /** La red actual tal como está guardada en "Mis redes"; null si aún no se escaneó nunca. */
    val profile: NetworkProfile? = null,
    /** Se acaba de detectar que el teléfono cambió a otra red. */
    val networkChanged: Boolean = false,
    /** El usuario eligió "Ahora no" al pedirle un nombre para esta red. */
    val namePromptDismissed: Boolean = false,
)

data class HistoryState(
    val scans: List<ScanRecord> = emptyList(),
    val events: List<Pair<DeviceEvent, String>> = emptyList(),
    /** Versión gratis: cuántos eventos y escaneos de más de 7 días quedan ocultos (RF-27). */
    val hiddenEvents: Int = 0,
    val hiddenScans: Int = 0,
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

    /** Detecta cuando el teléfono se conecta a otro WiFi (otro router) o se desconecta. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleRefresh()
        override fun onLost(network: Network) = scheduleRefresh()
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = scheduleRefresh()
    }
    private var refreshJob: Job? = null

    init {
        refreshNetwork()
        runCatching {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build()
            app.getSystemService(ConnectivityManager::class.java)?.registerNetworkCallback(request, networkCallback)
        }
    }

    override fun onCleared() {
        runCatching { app.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback) }
    }

    /** Espera un momento a que la red se estabilice (el router da la IP) antes de leerla. */
    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(1500)
            refreshNetwork()
        }
    }

    /** Lee la red actual y muestra al instante lo que ya conocemos de ella. */
    fun refreshNetwork() {
        viewModelScope.launch {
            val net = withContext(Dispatchers.IO) { NetworkInfoProvider.current(app) }
            val previous = _home.value.network?.key
            val changed = _home.value.networkChecked && previous != net?.key
            if (changed && scanJob?.isActive == true) {
                // Los resultados de un escaneo a medias de la red anterior no sirven.
                scanJob?.cancel()
            }
            _home.update {
                it.copy(
                    network = net,
                    networkChecked = true,
                    networkChanged = if (changed) net != null else it.networkChanged,
                    newDevices = if (changed) emptyList() else it.newDevices,
                    selectedGroup = if (changed) null else it.selectedGroup,
                    namePromptDismissed = if (changed) false else it.namePromptDismissed,
                )
            }
            reloadFromDb()
        }
    }

    fun dismissNetworkChanged() = _home.update { it.copy(networkChanged = false) }

    fun dismissNamePrompt() = _home.update { it.copy(namePromptDismissed = true) }

    /** Pone nombre a la red actual ("Casa", "Router 2"…). */
    fun renameCurrentNetwork(name: String) {
        val key = _home.value.network?.key ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.renameNetwork(key, name) }
            reloadFromDb()
        }
    }

    suspend fun reloadFromDb() {
        val net = _home.value.network ?: run {
            dbDevices = emptyList()
            _home.update { it.copy(profile = null, lastScan = null) }
            publish()
            return
        }
        val (devices, last, profile) = withContext(Dispatchers.IO) {
            Triple(repo.devices(net.key), repo.latestScan(net.key), repo.network(net.key))
        }
        dbDevices = devices
        val groups = devices.mapNotNull { it.groupName }.distinct().sorted()
        _home.update { s ->
            s.copy(
                lastScan = last,
                profile = profile,
                groups = groups,
                selectedGroup = s.selectedGroup?.takeIf { it in groups },
            )
        }
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
                // Si justo corre un escaneo automático en segundo plano, esperamos a que termine.
                val summary = app.scanMutex.withLock {
                    val result = app.scanner.scan(net.toScanTarget(), object : ScanListener {
                        override fun onPhase(phase: ScanPhase, progress: Float) {
                            _home.update { s -> s.copy(phase = phase, progress = maxOf(s.progress, progress)) }
                        }

                        override fun onHost(host: HostSnapshot) {
                            live[host.ip] = host
                        }
                    })
                    withContext(Dispatchers.IO) {
                        repo.applyScan(
                            networkKey = net.key,
                            subnet = net.scanSubnet.cidr,
                            hosts = result,
                            startedAt = started,
                            durationMs = System.currentTimeMillis() - started,
                            selfModel = phoneModel(),
                            gateway = net.gateway,
                        )
                    }
                }
                app.ads.onScanCompleted()
                DevicesWidget.updateAll(app)
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

    /** Filtra la lista por grupo (RF-32). null = todos. */
    fun selectGroup(group: String?) {
        _home.update { it.copy(selectedGroup = group) }
    }

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
        val premium = app.premium.isPremium
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                val names = repo.devices(net.key).associate { it.id to it.displayName }
                // Versión gratis: últimos 7 días. Los datos antiguos se conservan y
                // aparecen al activar Premium (RF-27).
                val since = if (premium) 0L
                else System.currentTimeMillis() - AppSettings.FREE_HISTORY_DAYS * DAY_MS
                HistoryState(
                    scans = repo.scans(net.key, limit = if (premium) 500 else 50, since = since),
                    events = repo.events(net.key, limit = if (premium) 1000 else 200, since = since)
                        .map { it to (names[it.deviceId] ?: tr("Dispositivo eliminado")) },
                    hiddenEvents = if (premium) 0 else repo.countEventsBefore(net.key, since),
                    hiddenScans = if (premium) 0 else repo.countScansBefore(net.key, since),
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
                customIcon = d.customIcon,
                groupName = d.groupName,
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
                    h.isSelf -> tr("Este teléfono (%1\$s)", phoneModel())
                    h.isGateway && h.bestName() == null -> tr("Router")
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
        const val DAY_MS = 24L * 60 * 60 * 1000

        fun phoneModel(): String {
            val m = Build.MODEL.orEmpty()
            val b = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
            return if (m.startsWith(b, ignoreCase = true)) m else "$b $m".trim()
        }
    }
}
