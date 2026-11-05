package com.lupadered.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lupadered.app.LupaApp
import com.lupadered.app.net.DiagItem
import com.lupadered.app.net.Diagnostics
import com.lupadered.app.net.HostInspector
import com.lupadered.app.net.InspectionResult
import com.lupadered.app.net.Ipv4
import com.lupadered.app.net.NetworkInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ManualState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val step: String = "",
    val result: InspectionResult? = null,
    val error: String? = null,
)

data class DiagState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val items: List<DiagItem> = emptyList(),
    val error: String? = null,
)

class ToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LupaApp
    private val inspector = HostInspector()
    private var manualJob: Job? = null

    private val _manual = MutableStateFlow(ManualState())
    val manual: StateFlow<ManualState> = _manual.asStateFlow()

    private val _diag = MutableStateFlow(DiagState())
    val diag: StateFlow<DiagState> = _diag.asStateFlow()

    /** Sugerencia para el campo de IP: "192.168.1." */
    fun ipPrefix(): String {
        val net = NetworkInfoProvider.current(app) ?: return ""
        return net.selfIp.substringBeforeLast('.') + "."
    }

    /** Valida la IP. Devuelve un mensaje de error o null si es válida. */
    fun validate(ip: String): String? = when {
        ip.isBlank() -> "Escribe una dirección IP, por ejemplo 192.168.1.10"
        !Ipv4.isValid(ip.trim()) -> "Eso no parece una IP. Debe tener 4 números entre 0 y 255 separados por puntos."
        !Ipv4.isPrivate(ip.trim()) ->
            "Solo puedes analizar direcciones de redes locales (como 192.168.x.x o 10.x.x.x). " +
                "Esta app es para revisar TU red, no equipos de Internet."
        else -> null
    }

    fun inspect(ip: String) {
        val clean = ip.trim()
        validate(clean)?.let { err ->
            _manual.update { it.copy(error = err) }
            return
        }
        if (manualJob?.isActive == true) return
        manualJob = viewModelScope.launch {
            _manual.value = ManualState(running = true, step = "Preparando…")
            try {
                val dns = withContext(Dispatchers.IO) {
                    NetworkInfoProvider.current(app)?.let { listOfNotNull(it.gateway) + it.dnsServers }.orEmpty()
                }
                val result = inspector.inspect(clean, dns) { p, step ->
                    _manual.update { it.copy(progress = p, step = step) }
                }
                app.ads.onScanCompleted()
                _manual.update { it.copy(result = result) }
            } finally {
                _manual.update { it.copy(running = false) }
            }
        }
    }

    fun cancelInspect() {
        manualJob?.cancel()
    }

    fun clearError() = _manual.update { it.copy(error = null) }

    fun runDiagnostics() {
        if (_diag.value.running) return
        viewModelScope.launch {
            _diag.value = DiagState(running = true)
            try {
                val net = withContext(Dispatchers.IO) { NetworkInfoProvider.current(app) }
                if (net == null) {
                    _diag.value = DiagState(error = "No estás conectado a un WiFi. Conéctate a tu red para hacer el diagnóstico.")
                    return@launch
                }
                val items = Diagnostics().run(net.gateway, net.dnsServers, net.wifiRssi) { p ->
                    _diag.update { it.copy(progress = p) }
                }
                _diag.value = DiagState(items = items)
            } finally {
                _diag.update { it.copy(running = false) }
            }
        }
    }
}
