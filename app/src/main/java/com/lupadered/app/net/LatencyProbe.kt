package com.lupadered.app.net

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Mediciones rápidas de tiempo de respuesta para el monitor de latencia (RF-30).
 * Bloqueantes: llamarlas desde Dispatchers.IO.
 */
object LatencyProbe {

    /** Servidores públicos muy estables, por IP para no depender del DNS. */
    private val INTERNET_TARGETS = listOf("1.1.1.1" to 443, "8.8.8.8" to 443, "9.9.9.9" to 443)

    /** Ping al router; si lo bloquea, se mide cuánto tarda en aceptar o rechazar una conexión. */
    fun router(ip: String, timeoutMs: Int = 1000): Long? =
        NetworkScanner.ping(ip, timeoutMs)
            ?: listOf(80, 443, 53).firstNotNullOfOrNull { tcp(ip, it, timeoutMs, refusedCounts = true) }

    /** Tiempo de conexión a Internet (ida y vuelta hasta un servidor público). */
    fun internet(timeoutMs: Int = 1500): Long? =
        INTERNET_TARGETS.firstNotNullOfOrNull { (ip, port) -> tcp(ip, port, timeoutMs) }

    private fun tcp(ip: String, port: Int, timeoutMs: Int, refusedCounts: Boolean = false): Long? {
        val start = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
            (System.nanoTime() - start) / 1_000_000L
        } catch (e: IOException) {
            if (refusedCounts && (e.message ?: "").contains("refused", true)) (System.nanoTime() - start) / 1_000_000L
            else null
        }
    }
}
