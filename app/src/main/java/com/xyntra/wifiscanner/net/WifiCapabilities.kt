package com.xyntra.wifiscanner.net

/** Tipo de protección de una red WiFi, de peor a mejor. */
enum class WifiProtection { OPEN, WEP, WPA, OWE, WPA2, WPA2_WPA3, WPA3, ENTERPRISE, UNKNOWN }

/** Lo que se sabe de la seguridad de la red a la que estás conectado. */
data class WifiSecurityInfo(
    val protection: WifiProtection,
    /** null = no se pudo saber (falta el permiso de ubicación o la red no apareció). */
    val wps: Boolean?,
    /** Usa el cifrado antiguo TKIP (solo o mezclado con AES). */
    val tkip: Boolean = false,
    /** Acepta también equipos con WPA (el primero, de 2003). */
    val allowsWpa1: Boolean = false,
    /** Texto crudo que da Android, para el "dato técnico". */
    val raw: String? = null,
)

/**
 * Lee la cadena de capacidades que Android da para cada red, por ejemplo
 * "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS][WPS]" o "[RSN-PSK+SAE-CCMP][ESS]".
 * Sin dependencias de Android para poder probarlo en la JVM.
 */
object WifiCapabilities {

    fun parse(capabilities: String): WifiSecurityInfo {
        val tokens = Regex("\\[([^\\]]*)]").findAll(capabilities.uppercase()).map { it.groupValues[1] }.toList()
        val wps = tokens.any { it == "WPS" }
        val tkip = tokens.any { it.contains("TKIP") }

        // Bloques de seguridad: "WPA-…", "WPA2-…", "RSN-…"
        val wpa1 = tokens.filter { it.startsWith("WPA-") }
        val rsn = tokens.filter { it.startsWith("WPA2-") || it.startsWith("RSN-") }
        val keyMgmt = (wpa1 + rsn).flatMap { block ->
            // "RSN-PSK+SAE-CCMP" → "PSK+SAE"; "RSN-EAP/SHA1-CCMP" → "EAP/SHA1"
            block.split('-').getOrNull(1)?.split('+', '/')?.toList().orEmpty()
        }.toSet()
        val rsnMgmt = rsn.flatMap { b -> b.split('-').getOrNull(1)?.split('+', '/')?.toList().orEmpty() }.toSet()

        val protection = when {
            tokens.any { it.startsWith("WEP") } -> WifiProtection.WEP
            keyMgmt.any { it.startsWith("EAP") || it.startsWith("SUITE") } -> WifiProtection.ENTERPRISE
            "SAE" in rsnMgmt && "PSK" in rsnMgmt -> WifiProtection.WPA2_WPA3
            "SAE" in rsnMgmt -> WifiProtection.WPA3
            "OWE" in rsnMgmt -> WifiProtection.OWE
            "PSK" in rsnMgmt -> WifiProtection.WPA2
            wpa1.isNotEmpty() -> WifiProtection.WPA
            rsn.isEmpty() -> WifiProtection.OPEN
            else -> WifiProtection.UNKNOWN
        }
        return WifiSecurityInfo(
            protection = protection,
            wps = wps,
            tkip = tkip,
            allowsWpa1 = wpa1.isNotEmpty() && rsn.isNotEmpty(),
            raw = capabilities,
        )
    }

    /**
     * Android 12+ informa el tipo de seguridad sin pedir ubicación
     * (WifiInfo.getCurrentSecurityType). No dice si hay WPS ni distingue WPA de WPA2.
     */
    fun fromSecurityType(type: Int): WifiProtection = when (type) {
        0 -> WifiProtection.OPEN          // SECURITY_TYPE_OPEN
        1 -> WifiProtection.WEP           // SECURITY_TYPE_WEP
        2 -> WifiProtection.WPA2          // SECURITY_TYPE_PSK (WPA o WPA2)
        3, 5, 9 -> WifiProtection.ENTERPRISE // EAP, EAP WPA3 192 bits, EAP WPA3
        4 -> WifiProtection.WPA3          // SECURITY_TYPE_SAE
        6 -> WifiProtection.OWE           // SECURITY_TYPE_OWE
        else -> WifiProtection.UNKNOWN
    }
}
