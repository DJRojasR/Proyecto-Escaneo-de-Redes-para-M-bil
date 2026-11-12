package com.lupadered.app

import android.app.Application
import com.lupadered.app.ads.AdsManager
import com.lupadered.app.data.AppSettings
import com.lupadered.app.data.DeviceRepository
import com.lupadered.app.i18n.I18n
import com.lupadered.app.monitor.MonitorScheduler
import com.lupadered.app.monitor.Notifier
import com.lupadered.app.net.NetworkScanner
import com.lupadered.app.premium.PremiumManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** Contenedor sencillo de dependencias de toda la app. */
class LupaApp : Application() {

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
                MonitorScheduler.apply(this@LupaApp, settings.monitor.value, isPremium, reschedule = false)
            }
        }
    }
}
