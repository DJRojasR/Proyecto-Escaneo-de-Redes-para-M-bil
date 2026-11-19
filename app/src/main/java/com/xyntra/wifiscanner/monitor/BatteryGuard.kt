package com.xyntra.wifiscanner.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.xyntra.wifiscanner.data.MonitorSettings
import com.xyntra.wifiscanner.i18n.tr

/**
 * Detecta cuándo el ahorro de batería del teléfono está frenando los escaneos
 * automáticos de la vigilancia, para poder avisarle al usuario (no puede detectar
 * equipos nuevos si Android nunca deja correr el escaneo en segundo plano).
 *
 * No pedimos el permiso de "ignorar optimización de batería" (Google Play lo revisa
 * con lupa): solo abrimos los ajustes para que el usuario lo haga a mano.
 */
object BatteryGuard {

    data class Status(
        /** La app está sujeta a la optimización de batería (Doze / App Standby). */
        val optimized: Boolean,
        /** El usuario o el sistema restringió el uso en segundo plano. */
        val backgroundRestricted: Boolean,
        /** Debería haber corrido un escaneo hace rato y no corrió (Xiaomi, Huawei…). */
        val staleScan: Boolean,
    ) {
        val isBlocking: Boolean get() = optimized || backgroundRestricted || staleScan
    }

    /**
     * Devuelve el estado solo si la vigilancia automática está activada y algo la
     * estaría frenando; null si está todo bien o la vigilancia está apagada.
     */
    fun check(context: Context, monitor: MonitorSettings, lastAutoScanAt: Long): Status? {
        if (!monitor.autoEnabled) return null
        val pm = context.getSystemService(PowerManager::class.java)
        val optimized = pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)

        val am = context.getSystemService(ActivityManager::class.java)
        val backgroundRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            am != null && am.isBackgroundRestricted

        // Si tocaba escanear cada N minutos y pasó más de 3 veces ese tiempo sin lograrlo,
        // algo lo está matando (aunque Android no lo reporte). Damos margen al arranque:
        // solo avisamos si ya hubo al menos un escaneo automático alguna vez.
        val expectedMs = monitor.intervalMinutes.coerceAtLeast(15) * 60_000L
        val staleScan = lastAutoScanAt > 0L &&
            System.currentTimeMillis() - lastAutoScanAt > maxOf(expectedMs * 3, 3 * 60 * 60_000L)

        val status = Status(optimized, backgroundRestricted, staleScan)
        return status.takeIf { it.isBlocking }
    }

    /** Abre la mejor pantalla de ajustes para quitarle a la app la restricción de batería. */
    fun openBatterySettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
        for (i in intents) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(context.packageManager) != null) {
                runCatching { context.startActivity(i) }.onSuccess { return }
            }
        }
    }

    /** Pasos propios de cada marca, porque cada una esconde el ajuste en otro lado. */
    fun brandSteps(): String = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" ->
            tr("En tu teléfono (Xiaomi/Redmi): entra en Ajustes de la app, toca \"Ahorro de batería\" y elige \"Sin restricciones\". En \"Inicio automático\", actívalo también.")
        "huawei", "honor" ->
            tr("En tu teléfono (Huawei/Honor): entra en Ajustes de batería, abre \"Inicio de aplicaciones\", quita el modo automático de esta app y activa las tres opciones.")
        "oppo", "realme", "oneplus" ->
            tr("En tu teléfono (Oppo/Realme/OnePlus): en Ajustes de batería de la app, permite la actividad en segundo plano y desactiva la optimización.")
        "vivo" ->
            tr("En tu teléfono (Vivo): en Ajustes de batería, permite el consumo en segundo plano y el inicio automático de esta app.")
        "samsung" ->
            tr("En tu teléfono (Samsung): en Ajustes de batería de la app, elige \"Sin restricciones\", y quita la app de \"Aplicaciones en suspensión\".")
        else ->
            tr("Busca a esta app en los ajustes de batería de tu teléfono y permítele trabajar sin restricciones en segundo plano.")
    }
}
