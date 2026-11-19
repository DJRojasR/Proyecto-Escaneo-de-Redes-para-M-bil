package com.xyntra.wifiscanner.ui.qr

/** Tipo de seguridad que va dentro del QR de WiFi. */
enum class QrSecurity(val code: String) {
    /** WPA, WPA2 y WPA3 con contraseña: todos los lectores lo entienden como "WPA". */
    WPA("WPA"),
    WEP("WEP"),
    OPEN("nopass"),
}

/**
 * Texto estándar de los QR de WiFi (el que leen la cámara de Android y de iPhone):
 * WIFI:T:WPA;S:MiRed;P:clave;;
 * Sin dependencias de Android para poder probarlo en la JVM.
 */
object WifiQrPayload {

    /** Los caracteres \ ; , : " deben ir precedidos de una barra invertida. */
    fun escape(s: String): String = buildString {
        for (c in s) {
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') append('\\')
            append(c)
        }
    }

    fun build(ssid: String, password: String, security: QrSecurity, hidden: Boolean): String = buildString {
        append("WIFI:T:").append(security.code).append(';')
        append("S:").append(escape(ssid)).append(';')
        if (security != QrSecurity.OPEN) append("P:").append(escape(password)).append(';')
        if (hidden) append("H:true;")
        append(';')
    }

}
