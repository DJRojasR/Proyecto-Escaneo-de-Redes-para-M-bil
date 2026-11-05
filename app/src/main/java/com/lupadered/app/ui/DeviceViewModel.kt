package com.lupadered.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.lupadered.app.LupaApp
import com.lupadered.app.data.Device
import com.lupadered.app.data.DeviceEvent
import com.lupadered.app.knowledge.DeviceType
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
    val portScanRunning: Boolean = false,
    val portScanProgress: Float = 0f,
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
        viewModelScope.launch {
            val (d, ev) = withContext(Dispatchers.IO) { repo.device(deviceId) to repo.eventsForDevice(deviceId) }
            _state.update { it.copy(loading = false, device = d, events = ev) }
        }
    }

    fun rename(alias: String) = mutate { repo.updateAlias(deviceId, alias) }
    fun setType(type: DeviceType?) = mutate { repo.updateUserType(deviceId, type) }
    fun setTrusted(trusted: Boolean) = mutate { repo.setTrusted(deviceId, trusted) }

    fun delete() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteDevice(deviceId) }
            _state.update { it.copy(deleted = true) }
        }
    }

    fun scanPorts() {
        val ip = _state.value.device?.ip ?: return
        if (portJob?.isActive == true) return
        portJob = viewModelScope.launch {
            _state.update { it.copy(portScanRunning = true, portScanProgress = 0f, portScanMessage = null) }
            try {
                val (open, responded) = inspector.scanPorts(ip) { p ->
                    _state.update { it.copy(portScanProgress = p) }
                }
                withContext(Dispatchers.IO) { repo.updatePorts(deviceId, open, System.currentTimeMillis()) }
                app.ads.onScanCompleted()
                val msg = when {
                    open.isNotEmpty() -> null
                    responded -> "No tiene puertos abiertos de nuestra lista. Es buena señal: ofrece pocos servicios."
                    else -> "El equipo no respondió. Puede estar apagado, dormido o haber cambiado de IP. Vuelve a escanear la red."
                }
                _state.update { it.copy(portScanMessage = msg) }
            } finally {
                _state.update { it.copy(portScanRunning = false) }
                load()
            }
        }
    }

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            load()
        }
    }
}
