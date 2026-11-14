package com.lupadered.app.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lupadered.app.LupaApp
import com.lupadered.app.data.ScanSource
import com.lupadered.app.net.HostSnapshot
import com.lupadered.app.net.NetworkInfoProvider
import com.lupadered.app.net.ScanListener
import com.lupadered.app.net.ScanPhase
import com.lupadered.app.ui.MainViewModel
import com.lupadered.app.widget.DevicesWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Escanea la red vigilada sin abrir la app (RF-24 / RF-26) y avisa si aparece
 * un equipo desconocido (RF-25). Solo trabaja si el usuario es Premium y el
 * teléfono está conectado a la red que eligió vigilar.
 */
class BackgroundScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as LupaApp
        if (!app.premium.isPremium) return Result.success()

        val settings = app.settings.monitor.value
        val source = inputData.getString(KEY_SOURCE)
            ?.let { n -> ScanSource.entries.firstOrNull { it.name == n } } ?: ScanSource.AUTO
        val net = withContext(Dispatchers.IO) { NetworkInfoProvider.current(app) } ?: return Result.success()
        // Si estás en otra red (la del trabajo, un café…) no escaneamos nada.
        if (settings.networkKey != null && net.key != settings.networkKey) return Result.success()

        // Si el usuario está escaneando a mano en este momento, esta vuelta se salta.
        if (!app.scanMutex.tryLock()) return Result.success()
        val lock = NetworkInfoProvider.acquireMulticastLock(app)
        try {
            val started = System.currentTimeMillis()
            val hosts = app.scanner.scan(net.toScanTarget(), object : ScanListener {
                override fun onPhase(phase: ScanPhase, progress: Float) = Unit
                override fun onHost(host: HostSnapshot) = Unit
            })
            val summary = withContext(Dispatchers.IO) {
                app.repository.applyScan(
                    networkKey = net.key,
                    subnet = net.scanSubnet.cidr,
                    hosts = hosts,
                    startedAt = started,
                    durationMs = System.currentTimeMillis() - started,
                    selfModel = MainViewModel.phoneModel(),
                    source = source,
                    gateway = net.gateway,
                )
            }
            if (settings.notifyIntruders && summary.newDevices.isNotEmpty()) {
                val netName = withContext(Dispatchers.IO) { app.repository.network(net.key)?.displayName }
                Notifier.notifyNewDevices(app, summary.newDevices, netName)
            }
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val devices = app.repository.devices(net.key)
                val eventsThisScan = app.repository.eventsBetween(net.key, started, now)
                val alerts = AlertEngine.evaluate(app.repository, net.key, devices, eventsThisScan, now)
                for (a in alerts) {
                    Notifier.notifyDeviceAlert(app, a.device.id, a.device.displayName, a.message)
                }
            }
            DevicesWidget.updateAll(app)
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Un fallo de red puntual no debe insistir: la próxima vuelta lo intentará de nuevo.
            return Result.success()
        } finally {
            lock?.release()
            app.scanMutex.unlock()
        }
    }

    companion object {
        const val KEY_SOURCE = "source"
    }
}
