package com.lupadered.app

import android.app.Application
import com.lupadered.app.ads.AdsManager
import com.lupadered.app.data.DeviceRepository
import com.lupadered.app.net.NetworkScanner

/** Contenedor sencillo de dependencias de toda la app. */
class LupaApp : Application() {

    lateinit var repository: DeviceRepository
        private set
    lateinit var ads: AdsManager
        private set
    val scanner = NetworkScanner()

    override fun onCreate() {
        super.onCreate()
        repository = DeviceRepository(this)
        ads = AdsManager(this)
    }
}
