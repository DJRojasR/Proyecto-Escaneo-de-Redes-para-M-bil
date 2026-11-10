package com.lupadered.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Colores de la app (RF-35). El primero es el gratuito; los demás son Premium. */
enum class ThemeColor(val label: String) {
    TEAL("Verde azulado"),
    BLUE("Azul océano"),
    PURPLE("Violeta"),
    ORANGE("Atardecer"),
}

enum class DarkMode(val label: String) {
    SYSTEM("Como el teléfono"),
    LIGHT("Claro"),
    DARK("Oscuro"),
}

data class Appearance(val color: ThemeColor = ThemeColor.TEAL, val dark: DarkMode = DarkMode.SYSTEM)

/** Configuración de la vigilancia de la red (RF-24, RF-25, RF-26). */
data class MonitorSettings(
    /** Escaneo automático cada [intervalMinutes] minutos. */
    val autoEnabled: Boolean = false,
    val intervalMinutes: Int = 30,
    /** Escaneo programado una vez al día a esta hora. */
    val dailyEnabled: Boolean = false,
    val dailyHour: Int = 23,
    val dailyMinute: Int = 0,
    /** Red vigilada: solo se escanea cuando el teléfono está conectado a ella. */
    val networkKey: String? = null,
    /** Avisar con una notificación cuando aparece un equipo desconocido. */
    val notifyIntruders: Boolean = true,
)

/** Preferencias del usuario guardadas en el teléfono. */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(readAppearance())
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    private val _monitor = MutableStateFlow(readMonitor())
    val monitor: StateFlow<MonitorSettings> = _monitor.asStateFlow()

    fun setAppearance(a: Appearance) {
        prefs.edit().putString("theme_color", a.color.name).putString("dark_mode", a.dark.name).apply()
        _appearance.value = a
    }

    fun setMonitor(m: MonitorSettings) {
        prefs.edit()
            .putBoolean("auto_enabled", m.autoEnabled)
            .putInt("interval_min", m.intervalMinutes)
            .putBoolean("daily_enabled", m.dailyEnabled)
            .putInt("daily_hour", m.dailyHour)
            .putInt("daily_minute", m.dailyMinute)
            .putString("monitor_network", m.networkKey)
            .putBoolean("notify_intruders", m.notifyIntruders)
            .apply()
        _monitor.value = m
    }

    private fun readAppearance() = Appearance(
        color = prefs.getString("theme_color", null)
            ?.let { n -> ThemeColor.entries.firstOrNull { it.name == n } } ?: ThemeColor.TEAL,
        dark = prefs.getString("dark_mode", null)
            ?.let { n -> DarkMode.entries.firstOrNull { it.name == n } } ?: DarkMode.SYSTEM,
    )

    private fun readMonitor() = MonitorSettings(
        autoEnabled = prefs.getBoolean("auto_enabled", false),
        intervalMinutes = prefs.getInt("interval_min", 30),
        dailyEnabled = prefs.getBoolean("daily_enabled", false),
        dailyHour = prefs.getInt("daily_hour", 23),
        dailyMinute = prefs.getInt("daily_minute", 0),
        networkKey = prefs.getString("monitor_network", null),
        notifyIntruders = prefs.getBoolean("notify_intruders", true),
    )

    companion object {
        /** Días de historial que ve la versión gratis (RF-27). */
        const val FREE_HISTORY_DAYS = 7
        val INTERVALS = listOf(15, 30, 60)
    }
}
