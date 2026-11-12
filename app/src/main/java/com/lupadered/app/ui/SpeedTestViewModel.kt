package com.lupadered.app.ui

import com.lupadered.app.i18n.tr
import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lupadered.app.BuildConfig
import com.lupadered.app.LupaApp
import com.lupadered.app.data.AppSettings
import com.lupadered.app.data.SpeedTestRecord
import com.lupadered.app.net.NetworkInfoProvider
import com.lupadered.app.net.SpeedTest
import com.lupadered.app.ui.components.CheckItem
import com.lupadered.app.ui.components.CheckStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class SpeedPhase { IDLE, LOCATING, DOWNLOAD, UPLOAD, DONE, ERROR }

data class SpeedTestState(
    val phase: SpeedPhase = SpeedPhase.IDLE,
    /** Velocidad que marca la aguja en este momento (Mbps). */
    val liveMbps: Double = 0.0,
    val progress: Float = 0f,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val pingMs: Double? = null,
    val server: String? = null,
    val checks: List<CheckItem> = emptyList(),
    val error: String? = null,
    val history: List<SpeedTestRecord> = emptyList(),
    /** Hay que pedir permiso antes de la primera prueba (M-Lab publica la IP pública). */
    val askConsent: Boolean = false,
    /** El teléfono está usando datos móviles: la prueba puede gastar bastantes MB. */
    val askCellular: Boolean = false,
    val testsToday: Int = 0,
    val planMbps: Int = 0,
) {
    val running: Boolean get() = phase == SpeedPhase.LOCATING || phase == SpeedPhase.DOWNLOAD || phase == SpeedPhase.UPLOAD
}

/** Prueba de velocidad (bajada, subida y latencia) con historial. */
class SpeedTestViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LupaApp
    private val repo = app.repository
    private val tester = SpeedTest(BuildConfig.VERSION_NAME)
    private var job: Job? = null

    private val _state = MutableStateFlow(SpeedTestState(planMbps = app.settings.planMbps))
    val state: StateFlow<SpeedTestState> = _state.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val (list, today) = withContext(Dispatchers.IO) {
                repo.speedTests(100) to repo.countSpeedTestsSince(startOfToday())
            }
            _state.update { it.copy(history = list, testsToday = today) }
        }
    }

    /** ¿Puede hacer otra prueba hoy? (gratis: 2 por día; Premium: sin límite). */
    fun canTestNow(): Boolean =
        app.premium.isPremium || _state.value.testsToday < AppSettings.FREE_SPEED_TESTS_PER_DAY

    /** Toca "Iniciar": primero pide permiso y avisa si usa datos móviles. */
    fun requestStart() {
        if (_state.value.running || !canTestNow()) return
        when {
            !app.settings.speedTestConsent -> _state.update { it.copy(askConsent = true) }
            isOnCellular() -> _state.update { it.copy(askCellular = true) }
            else -> start()
        }
    }

    fun acceptConsent() {
        app.settings.speedTestConsent = true
        _state.update { it.copy(askConsent = false) }
        requestStart()
    }

    fun dismissDialogs() = _state.update { it.copy(askConsent = false, askCellular = false) }

    fun confirmCellular() {
        _state.update { it.copy(askCellular = false) }
        start()
    }

    fun setPlan(mbps: Int) {
        app.settings.planMbps = mbps.coerceIn(0, 10_000)
        _state.update { it.copy(planMbps = app.settings.planMbps) }
    }

    private fun setCheck(item: CheckItem) = _state.update { s ->
        val i = s.checks.indexOfFirst { it.id == item.id }
        s.copy(checks = if (i < 0) s.checks + item else s.checks.toMutableList().also { it[i] = item })
    }

    private fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update {
                it.copy(
                    phase = SpeedPhase.LOCATING, liveMbps = 0.0, progress = 0f, checks = emptyList(),
                    downloadMbps = null, uploadMbps = null, pingMs = null, server = null, error = null,
                )
            }
            var current: CheckItem? = null
            try {
                // 1) Servidor
                val locItem = CheckItem("locate", tr("Buscando el servidor de pruebas más cercano"), CheckStatus.RUNNING)
                current = locItem
                setCheck(locItem)
                val server = tester.locate()
                setCheck(locItem.copy(status = CheckStatus.OK, result = server.place))
                _state.update { it.copy(server = server.place) }

                // 2) Bajada
                val downItem = CheckItem("down", tr("Midiendo la velocidad de bajada (10 s)"), CheckStatus.RUNNING, progress = 0f)
                current = downItem
                setCheck(downItem)
                _state.update { it.copy(phase = SpeedPhase.DOWNLOAD) }
                val down = tester.download(server) { mbps, p ->
                    _state.update { it.copy(liveMbps = mbps, progress = p) }
                    setCheck(downItem.copy(progress = p, result = tr("%1\$s Mbps", formatMbps(mbps))))
                }
                setCheck(downItem.copy(status = CheckStatus.OK, progress = null, result = tr("%1\$s Mbps", formatMbps(down.mbps))))
                _state.update { it.copy(downloadMbps = down.mbps, pingMs = down.minRttMs, liveMbps = down.mbps) }

                // 3) Subida
                val upItem = CheckItem("up", tr("Midiendo la velocidad de subida (10 s)"), CheckStatus.RUNNING, progress = 0f)
                current = upItem
                setCheck(upItem)
                _state.update { it.copy(phase = SpeedPhase.UPLOAD, liveMbps = 0.0, progress = 0f) }
                val up = tester.upload(server) { mbps, p ->
                    _state.update { it.copy(liveMbps = mbps, progress = p) }
                    setCheck(upItem.copy(progress = p, result = tr("%1\$s Mbps", formatMbps(mbps))))
                }
                setCheck(upItem.copy(status = CheckStatus.OK, progress = null, result = tr("%1\$s Mbps", formatMbps(up.mbps))))
                current = null

                val ping = down.minRttMs ?: up.minRttMs
                setCheck(
                    CheckItem(
                        "ping", tr("Latencia hasta el servidor"),
                        if (ping == null) CheckStatus.INFO else CheckStatus.OK,
                        ping?.let { "${it.toInt()} ms" } ?: tr("No disponible"),
                    )
                )

                // 4) Guardar
                val netKey = withContext(Dispatchers.IO) { NetworkInfoProvider.current(app)?.key }
                withContext(Dispatchers.IO) {
                    repo.insertSpeedTest(
                        SpeedTestRecord(
                            networkKey = netKey, at = System.currentTimeMillis(),
                            downloadMbps = down.mbps, uploadMbps = up.mbps, pingMs = ping, server = server.place,
                        )
                    )
                }
                _state.update {
                    it.copy(phase = SpeedPhase.DONE, uploadMbps = up.mbps, pingMs = ping, liveMbps = down.mbps, progress = 1f)
                }
                loadHistory()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = SpeedPhase.ERROR,
                        error = tr("No se pudo completar la prueba. Revisa tu conexión a Internet e inténtalo de nuevo."),
                    )
                }
                current?.let { setCheck(it.copy(status = CheckStatus.BAD, progress = null, result = tr("Falló"))) }
                current = null
            } finally {
                current?.let { setCheck(it.copy(status = CheckStatus.PENDING, progress = null, result = tr("Detenido"))) }
                if (_state.value.running) _state.update { it.copy(phase = SpeedPhase.IDLE, liveMbps = 0.0) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private fun isOnCellular(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        fun formatMbps(v: Double): String = when {
            v >= 100 -> v.toInt().toString()
            v >= 10 -> "%.1f".format(v)
            else -> "%.2f".format(v)
        }
    }
}
