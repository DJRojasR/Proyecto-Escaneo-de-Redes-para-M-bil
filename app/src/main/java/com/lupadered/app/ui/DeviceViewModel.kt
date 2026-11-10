package com.lupadered.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.lupadered.app.LupaApp
import com.lupadered.app.data.AppSettings
import com.lupadered.app.data.Device
import com.lupadered.app.data.DeviceEvent
import com.lupadered.app.knowledge.DeviceType
import com.lupadered.app.knowledge.PortKnowledge
import com.lupadered.app.net.HostInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceDetailState(
    val loading: Boolean = true,
    val device: Device? = null,
    val events: List<DeviceEvent> = emptyList(),
    /** Versión gratis: eventos de más de 7 días que no se muestran (RF-27). */
    val hiddenEvents: Int = 0,
    /** Grupos ya usados en la red, para elegir uno rápido (RF-32). */
    val groups: List<String> = emptyList(),
    val portScanRunning: Boolean = false,
    val portScanProgress: Float = 0f,
    /** Texto mientras se analizan los puertos, p. ej. "Revisando 1170 puertos…". */
    val portScanLabel: String = "",
    val portScanMessage: String? = null,
    val deleted: Boolean = false,
)

class DeviceViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {

    private val app = application as LupaApp
    private val repo = app.repository
    private val deviceId: Long = savedState.get<Long>("id") ?: -1L
    private val inspector = HostInspector()
    private var portJob: Job? = null

    private val _state = MutableStateFlow(DeviceDetailState())
    val state: StateFlow<DeviceDetailState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val premium = app.premium.isPremium
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val d = repo.device(deviceId)
                val since = if (premium) 0L
                else System.currentTimeMillis() - AppSettings.FREE_HISTORY_DAYS * MainViewModel.DAY_MS
                val events = repo.eventsForDevice(deviceId, limit = if (premium) 500 else 50, since = since)
                val hidden = if (premium) 0 else repo.countDeviceEventsBefore(deviceId, since)
                val groups = d?.let { repo.groups(it.networkKey) }.orEmpty()
                Loaded(d, events, hidden, groups)
            }
            _state.update {
                it.copy(
                    loading = false,
                    device = loaded.device,
                    events = loaded.events,
                    hiddenEvents = loaded.hidden,
                    groups = loaded.groups,
                )
            }
        }
    }

    private class Loaded(val device: Device?, val events: List<DeviceEvent>, val hidden: Int, val groups: List<String>)

    fun rename(alias: String) = mutate { repo.updateAlias(deviceId, alias) }
    fun setType(type: DeviceType?) = mutate { repo.updateUserType(deviceId, type) }
    fun setTrusted(trusted: Boolean) = mutate { repo.setTrusted(deviceId, trusted) }
    fun setGroup(group: String?) = mutate { repo.updateGroup(deviceId, group) }
    fun setIcon(key: String?) = mutate { repo.updateIcon(deviceId, key) }

    fun delete() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteDevice(deviceId) }
            _state.update { it.copy(deleted = true) }
        }
    }

    /** Análisis de los ~90 puertos más comunes (gratis). */
    fun scanPorts() = runPortScan(
        PortKnowledge.COMMON_PORTS,
        "Revisando ${PortKnowledge.COMMON_PORTS.size} puertos comunes…",
        timeoutMs = 1200, inFlight = 128,
    )

    /** Análisis extendido de más de 1100 puertos (Premium, RF-28). */
    fun scanPortsExtended() = runPortScan(
        PortKnowledge.EXTENDED_PORTS,
        "Análisis extendido: ${PortKnowledge.EXTENDED_PORTS.size} puertos…",
        timeoutMs = 900, inFlight = 256,
    )

    /** Rango elegido por el usuario, por ejemplo 8000–9000 (Premium, RF-28). */
    fun scanPortRange(from: Int, to: Int) {
        val ports = (from.coerceIn(1, 65535)..to.coerceIn(1, 65535)).toList()
        runPortScan(ports, "Revisando los puertos $from a $to (${ports.size})…", timeoutMs = 800, inFlight = 256)
    }

    private fun runPortScan(ports: List<Int>, label: String, timeoutMs: Long, inFlight: Int) {
        val ip = _state.value.device?.ip ?: return
        if (portJob?.isActive == true || ports.isEmpty()) return
        portJob = viewModelScope.launch {
            _state.update {
                it.copy(portScanRunning = true, portScanProgress = 0f, portScanMessage = null, portScanLabel = label)
            }
            try {
                val (open, responded) = inspector.scanPorts(ip, ports, timeoutMs, inFlight) { p ->
                    _state.update { it.copy(portScanProgress = p) }
                }
                withContext(Dispatchers.IO) {
                    repo.updatePorts(deviceId, open, System.currentTimeMillis(), scanned = ports)
                }
                app.ads.onScanCompleted()
                val msg = when {
                    open.isNotEmpty() -> null
                    responded -> "No encontramos puertos abiertos entre los ${ports.size} revisados. Es buena señal: ofrece pocos servicios."
                    else -> "El equipo no respondió. Puede estar apagado, dormido o haber cambiado de IP. Vuelve a escanear la red."
                }
                _state.update { it.copy(portScanMessage = msg) }
            } finally {
                _state.update { it.copy(portScanRunning = false) }
                load()
            }
        }
    }

    fun cancelPortScan() {
        portJob?.cancel()
    }

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            load()
        }
    }
}
