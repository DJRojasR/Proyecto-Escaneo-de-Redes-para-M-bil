package com.xyntra.wifiscanner

import android.app.Application
import com.xyntra.wifiscanner.ads.AdsManager
import com.xyntra.wifiscanner.data.AppSettings
import com.xyntra.wifiscanner.data.DeviceRepository
import com.xyntra.wifiscanner.i18n.I18n
import com.xyntra.wifiscanner.monitor.MonitorScheduler
import com.xyntra.wifiscanner.monitor.Notifier
import com.xyntra.wifiscanner.net.NetworkScanner
import com.xyntra.wifiscanner.premium.PremiumManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** Contenedor sencillo de dependencias de toda la app. */
class XyntraApp : Application() {

    lateinit var repository: DeviceRepository
        private set
    lateinit var ads: AdsManager
        private set
    lateinit var premium: PremiumManager
        private set
    lateinit var settings: AppSettings
        private set
    val scanner = NetworkScanner()

    /** Evita que un escaneo en segundo plano y uno manual corran a la vez. */
    val scanMutex = Mutex()

    /** Para tareas que deben seguir aunque se cierre una pantalla. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // Primero el idioma: todos los textos de la app dependen de él.
        I18n.init(this)
        repository = DeviceRepository(this)
        settings = AppSettings(this)
        premium = PremiumManager(this)
        ads = AdsManager(this) { premium.isPremium }
        Notifier.createChannels(this)
        premium.start()

        // Si el usuario deja de ser Premium (o lo vuelve a ser), se ajusta la vigilancia.
        appScope.launch {
            premium.state.map { it.isPremium }.distinctUntilChanged().collect { isPremium ->
                MonitorScheduler.apply(this@XyntraApp, settings.monitor.value, isPremium, reschedule = false)
            }
        }
    }
}
