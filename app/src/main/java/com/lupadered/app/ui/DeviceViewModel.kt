package com.lupadered.app.ui

import com.lupadered.app.i18n.tr
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.lupadered.app.LupaApp
import com.lupadered.app.data.AlertRule
import com.lupadered.app.data.AlertType
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
    /** Reglas de alerta configuradas para este equipo (Premium, RF-49). */
    val alertRules: List<AlertRule> = emptyList(),
    /** La vigilancia automática debe estar activa para que las alertas se disparen. */
    val monitorActive: Boolean = false,
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
                val rules = repo.alertRules(deviceId)
                Loaded(d, events, hidden, groups, rules)
            }
            val monitor = app.settings.monitor.value
            _state.update {
                it.copy(
                    loading = false,
                    device = loaded.device,
                    events = loaded.events,
                    hiddenEvents = loaded.hidden,
                    groups = loaded.groups,
                    alertRules = loaded.rules,
                    monitorActive = monitor.autoEnabled || monitor.dailyEnabled,
                )
            }
        }
    }

    private class Loaded(
        val device: Device?, val events: List<DeviceEvent>, val hidden: Int,
        val groups: List<String>, val rules: List<AlertRule>,
    )

    fun addAlertRule(type: AlertType, thresholdMin: Int = 15) = mutate {
        repo.saveAlertRule(AlertRule(deviceId = deviceId, type = type, thresholdMin = thresholdMin))
    }

    fun setAlertRuleEnabled(rule: AlertRule, enabled: Boolean) = mutate {
        repo.saveAlertRule(rule.copy(enabled = enabled))
    }

    fun deleteAlertRule(rule: AlertRule) = mutate { repo.deleteAlertRule(rule.id) }

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
        tr("Revisando %1\$s puertos comunes…", PortKnowledge.COMMON_PORTS.size),
        timeoutMs = 1200, inFlight = 128,
    )

    private fun runPortScan(ports: List<Int>, label: String, timeoutMs: Long, inFlight: Int) {
        val ip = _state.value.device?.ip ?: return
        if (portJob?.isActive == true || ports.isEmpty()) return
        portJob = viewModelScope.launch {
            _state.update {
                it.copy(portScanRunning = true, portScanProgress = 0f, portScanMessage = null, portScanLabel = label)
            }
            try {
                val before = _state.value.device?.openPorts.orEmpty().toSet()
                val (firstOpen, responded) = inspector.scanPorts(ip, ports, timeoutMs, inFlight) { p ->
                    _state.update { it.copy(portScanProgress = p) }
                }
                // Los que estaban abiertos y ahora no contestaron: los revisamos otra vez con más
                // paciencia, para no borrarlos por una respuesta lenta.
                val missing = before.filter { it in ports && it !in firstOpen }
                val rescued = if (responded && missing.isNotEmpty()) {
                    inspector.scanPorts(ip, missing, timeoutMs = 3000, maxInFlight = 32).first
                } else emptyList()
                val open = (firstOpen + rescued).distinct().sorted()
                val closed = missing.filter { it !in rescued }.sorted()

                // Si el equipo no respondió (apagado o dormido), no tocamos lo que ya sabíamos
                if (responded) {
                    withContext(Dispatchers.IO) {
                        repo.updatePorts(deviceId, open, System.currentTimeMillis(), scanned = ports)
                    }
                }
                app.ads.onScanCompleted()
                val msg = when {
                    !responded && before.isNotEmpty() ->
                        tr("El equipo no respondió, así que dejamos los puertos del análisis anterior. Puede estar apagado o dormido; inténtalo cuando esté en uso.")
                    !responded -> tr("El equipo no respondió. Puede estar apagado, dormido o haber cambiado de IP. Vuelve a escanear la red.")
                    closed.isNotEmpty() -> tr("Ya no están abiertos desde el último análisis: %1\$s.", closed.joinToString(", "))
                    open.isNotEmpty() -> null
                    else -> tr("No encontramos puertos abiertos entre los %1\$s revisados. Es buena señal: ofrece pocos servicios.", ports.size)
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
