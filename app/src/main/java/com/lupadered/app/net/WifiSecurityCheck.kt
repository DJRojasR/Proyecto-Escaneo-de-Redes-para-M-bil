package com.lupadered.app.net

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.lupadered.app.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Revisa la protección de la red WiFi a la que estás conectado (función gratis).
 *
 * - Con permiso de ubicación (regla de Android para ver detalles de redes WiFi)
 *   leemos la lista de redes cercanas y buscamos la tuya: así sabemos el tipo
 *   exacto (WEP, WPA, WPA2, WPA3), si usa TKIP y si el router tiene WPS activo.
 * - Sin ese permiso, en Android 12+ igual sabemos el tipo de protección, pero no WPS.
 *
 * La ubicación no se usa ni se guarda: Android la exige solo por ver redes WiFi.
 */
object WifiSecurityCheck {

    data class Result(
        val info: WifiSecurityInfo?,
        val ssid: String?,
        val hasLocationPermission: Boolean,
        val locationOn: Boolean,
    )

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationOn(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val wm = app.getSystemService(WifiManager::class.java)
        val hasPerm = hasLocationPermission(app)
        val locOn = isLocationOn(app)
        val conn = runCatching { wm?.connectionInfo }.getOrNull()
        val ssid = conn?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }

        var info: WifiSecurityInfo? = null
        if (wm != null && hasPerm && locOn) {
            val bssid = conn?.bssid?.lowercase()?.takeIf { it != "02:00:00:00:00:00" }
            fun find() = runCatching { wm.scanResults }.getOrNull().orEmpty().firstOrNull { r ->
                if (bssid != null) r.BSSID?.lowercase() == bssid
                else ssid != null && r.SSID == ssid
            }
            var match = find()
            if (match == null) {
                // La lista guardada puede ser vieja: pedimos una nueva (Android la limita a veces)
                runCatching { wm.startScan() }
                delay(3500)
                match = find()
            }
            match?.capabilities?.let { info = WifiCapabilities.parse(it) }
        }
        if (info == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && conn != null) {
            val p = WifiCapabilities.fromSecurityType(conn.currentSecurityType)
            if (p != WifiProtection.UNKNOWN) info = WifiSecurityInfo(p, wps = null)
        }
        Result(info, ssid, hasPerm, locOn)
    }

    /**
     * Datos rápidos para rellenar el QR: nombre de la red (si Android lo deja ver)
     * y tipo de protección. No pide permisos ni escanea.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun quick(context: Context): Pair<String?, WifiProtection?> {
        val app = context.applicationContext
        val wm = app.getSystemService(WifiManager::class.java) ?: return null to null
        val conn = runCatching { wm.connectionInfo }.getOrNull() ?: return null to null
        val ssid = conn.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        var protection: WifiProtection? = null
        if (hasLocationPermission(app)) {
            val bssid = conn.bssid?.lowercase()
            runCatching { wm.scanResults }.getOrNull().orEmpty()
                .firstOrNull { it.BSSID?.lowercase() == bssid }
                ?.let { protection = WifiCapabilities.parse(it.capabilities ?: "").protection }
        }
        if (protection == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            protection = WifiCapabilities.fromSecurityType(conn.currentSecurityType).takeIf { it != WifiProtection.UNKNOWN }
        }
        return ssid to protection
    }

    // ------------------------------------------------------------ Resultado en palabras simples

    fun items(r: Result, gateway: String?): List<DiagItem> {
        val info = r.info
        val enter = if (gateway != null)
            tr("Entra a tu router: escribe %1\$s en el navegador y usa el usuario y la contraseña de la etiqueta del router.", gateway)
        else tr("Entra a tu router: la dirección, el usuario y la contraseña suelen venir en la etiqueta del router.")
        val out = mutableListOf<DiagItem>()

        val protectionExplain = tr("La protección es el tipo de “candado” que usa tu WiFi. De más antigua a más moderna: ninguna, WEP, WPA, WPA2 y WPA3.")
        val changeIt = enter + " " + tr("Luego, en «Seguridad inalámbrica» (Wireless Security), elige WPA2-PSK (AES) o WPA3 y pon una contraseña larga.")
        val tech = info?.let { tr("Dato técnico: %1\$s", it.raw ?: it.protection.name) }

        out += when (info?.protection) {
            WifiProtection.OPEN -> DiagItem(
                tr("Protección del WiFi"), DiagStatus.BAD, tr("Sin contraseña"), protectionExplain, changeIt,
                meaning = tr("Tu WiFi no tiene contraseña: cualquiera que esté cerca puede conectarse, usar tu Internet y ver lo que no vaya cifrado."),
                technical = tech,
            )
            WifiProtection.WEP -> DiagItem(
                tr("Protección del WiFi"), DiagStatus.BAD, tr("WEP (muy antigua)"), protectionExplain, changeIt,
                meaning = tr("Tu WiFi usa una protección de 1999 que se puede romper en pocos minutos. Es casi como no tener contraseña."),
                technical = tech,
            )
            WifiProtection.WPA -> DiagItem(
                tr("Protección del WiFi"), DiagStatus.BAD, tr("WPA (antigua)"), protectionExplain, changeIt,
                meaning = tr("Tu WiFi usa una protección antigua (de 2003) con fallos conocidos. Además hace más lenta la red."),
                technical = tech,
            )
            WifiProtection.OWE -> DiagItem(
                tr("Protección del WiFi"), DiagStatus.WARN, tr("Abierta cifrada"), protectionExplain, changeIt,
                meaning = tr("Tu WiFi no pide contraseña, pero cifra la conexión. Lo que envías está protegido, pero cualquiera puede conectarse y usar tu Internet."),
                technical = tech,
            )
            WifiProtection.WPA2 -> DiagItem(
                tr("Protección del WiFi"), DiagStatus.GOOD, "WPA2", protectionExplain, null,
                meaning = tr("Tu WiFi usa WPA2, la protección más común hoy. Está bien protegido si la contraseña es larga y difícil de adivinar."),
                technical = tech,
            )
            WifiProtection.WPA2_WPA3 -> DiagItem(
                tr("Protección del WiFi"), DiagStatus.GOOD, "WPA2/WPA3", protectionExplain, null,
                meaning = tr("Tu WiFi usa WPA3 con los equipos que lo permiten y WPA2 con los más antiguos. Muy buena protección."),
                technical = tech,
            )
            WifiProtection.WPA3 -> DiagItem(
                tr("Protección del WiFi"), DiagStatus.GOOD, "WPA3", protectionExplain, null,
                meaning = tr("Tu WiFi usa WPA3, la protección más moderna. Excelente."),
                technical = tech,
            )
            WifiProtection.ENTERPRISE -> DiagItem(
                tr("Protección del WiFi"), DiagStatus.GOOD, tr("Empresarial"), protectionExplain, null,
                meaning = tr("Esta red pide un usuario y una contraseña propios a cada persona (WPA Enterprise), como en empresas y universidades. Es muy segura."),
                technical = tech,
            )
            else -> DiagItem(
                tr("Protección del WiFi"), DiagStatus.UNKNOWN, tr("No se pudo saber"), protectionExplain, null,
                meaning = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !(r.hasLocationPermission && r.locationOn))
                    tr("En tu versión de Android necesitamos el permiso de ubicación para verlo (abajo te explicamos por qué).")
                else tr("Tu teléfono no nos dijo qué protección usa esta red. Prueba revisar de nuevo en unos segundos."),
            )
        }

        val p = info?.protection
        val modern = p == WifiProtection.WPA2 || p == WifiProtection.WPA2_WPA3
        if (info != null && modern && (info.tkip || info.allowsWpa1)) {
            out += DiagItem(
                tr("Modo compatible antiguo"), DiagStatus.WARN, tr("Activado"),
                tr("Algunos routers aceptan también la protección vieja para equipos muy antiguos."),
                enter + " " + tr("Luego, en «Seguridad inalámbrica», elige solo «WPA2-PSK (AES)» o «WPA2/WPA3», no «WPA/WPA2» ni «TKIP»."),
                meaning = tr("Tu router acepta también la protección antigua (WPA/TKIP). Eso debilita un poco tu red y puede hacerla más lenta."),
                technical = tech,
            )
        }

        if (info != null && p != WifiProtection.OPEN && p != WifiProtection.OWE && p != WifiProtection.ENTERPRISE) {
            val wpsExplain = tr("WPS es un atajo para conectar equipos sin escribir la contraseña: con un botón del router o un PIN de 8 números.")
            out += when (info.wps) {
                true -> DiagItem(
                    tr("WPS"), DiagStatus.WARN, tr("Activado"), wpsExplain,
                    enter + " " + tr("Luego busca «WPS» y desactívalo. Podrás seguir conectando equipos con la contraseña normal."),
                    meaning = tr("Tu router tiene WPS activo. En muchos routers ese PIN se puede adivinar y así saltarse tu contraseña."),
                )
                false -> DiagItem(
                    tr("WPS"), DiagStatus.GOOD, tr("Desactivado"), wpsExplain, null,
                    meaning = tr("Tu router no tiene WPS activo. Bien: nadie puede saltarse tu contraseña con el PIN."),
                )
                null -> DiagItem(
                    tr("WPS"), DiagStatus.UNKNOWN, tr("Sin revisar"), wpsExplain, null,
                    meaning = tr("Para revisarlo, Android pide el permiso de ubicación (abajo te explicamos por qué)."),
                )
            }
        }
        return out
    }

    /** Frase principal: "Tu WiFi está bien protegido" o "…cámbiala así". */
    fun verdict(items: List<DiagItem>): Pair<DiagStatus, String> {
        val first = items.firstOrNull() ?: return DiagStatus.UNKNOWN to tr("No pudimos revisar tu WiFi.")
        return when {
            first.status == DiagStatus.UNKNOWN -> DiagStatus.UNKNOWN to tr("No pudimos saber qué protección usa tu WiFi.")
            items.any { it.status == DiagStatus.BAD } ->
                DiagStatus.BAD to tr("Tu WiFi usa una protección antigua o ninguna. Cámbiala así: sigue los pasos de abajo.")
            items.any { it.status == DiagStatus.WARN } ->
                DiagStatus.WARN to tr("Tu WiFi está protegido, pero puedes mejorarlo con un cambio en el router.")
            else -> DiagStatus.GOOD to tr("Tu WiFi está bien protegido.")
        }
    }
}
