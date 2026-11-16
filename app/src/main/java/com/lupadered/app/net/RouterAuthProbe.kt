package com.lupadered.app.net

import com.lupadered.app.knowledge.RouterKnowledge
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Prueba activa de credenciales de fábrica contra el panel web del router
 * (Premium, RF-47). Solo se ejecuta con el consentimiento explícito del
 * usuario (ver el diálogo en PremiumToolScreens) y únicamente contra el gateway
 * (IP privada) de la red actual. No guarda ni reporta la credencial encontrada,
 * solo si alguna funcionó, y nunca se loggea el intento ni las credenciales.
 *
 * Seguridad:
 * - Solo HTTP. Antes se probaba también HTTPS aceptando cualquier certificado, algo
 *   que Google Play marca como inseguro; los paneles con usuario/contraseña básicos
 *   de fábrica responden por HTTP.
 * - Para no dar falsos positivos, primero se comprueba que el panel PIDA usuario y
 *   contraseña (respuesta 401). Si el panel usa un formulario web, no podemos saberlo
 *   y respondemos "no" en vez de inventar un riesgo.
 * - Las ~10 credenciales de [RouterKnowledge.COMMON_CREDENTIALS] se prueban como un
 *   solo lote (una confirmación del usuario, no una por credencial): todas viajan en
 *   menos de un par de segundos al mismo panel que el usuario ya autorizó explorar,
 *   así que pedir confirmación por cada una sería solo fricción sin reducir el riesgo.
 * - Cooldown: no se reintenta contra el mismo gateway antes de 5 minutos, para no
 *   arriesgar que el router bloquee la cuenta de administrador por "demasiados
 *   intentos fallidos" si el usuario pulsa "Probar" varias veces seguidas.
 */
object RouterAuthProbe {

    private val lastAttempt = ConcurrentHashMap<String, Long>()
    private const val COOLDOWN_MS = 5 * 60_000L

    /** @return true solo si el panel pide clave (401) y alguna credencial de fábrica entra (200). */
    suspend fun probeDefaultCredentials(ip: String, timeoutMs: Int = 1500): Boolean {
        if (!Ipv4.isPrivate(ip)) return false
        val now = System.currentTimeMillis()
        val last = lastAttempt[ip]
        if (last != null && now - last < COOLDOWN_MS) return false
        lastAttempt[ip] = now
        // Sin credenciales debe pedir autenticación básica; si no, no sabemos cómo entra
        if (status(ip, null, timeoutMs) != HttpURLConnection.HTTP_UNAUTHORIZED) return false
        for ((user, pass) in RouterKnowledge.COMMON_CREDENTIALS) {
            if (status(ip, "$user:$pass", timeoutMs) == HttpURLConnection.HTTP_OK) return true
        }
        return false
    }

    /** Cuánto falta (en ms) para poder reintentar contra este gateway; 0 si ya se puede. */
    fun cooldownRemainingMs(ip: String): Long {
        val last = lastAttempt[ip] ?: return 0L
        val remaining = COOLDOWN_MS - (System.currentTimeMillis() - last)
        return remaining.coerceAtLeast(0L)
    }

    /**
     * Petición HTTP mínima con un socket: Android bloquea HTTP sin cifrar en
     * HttpURLConnection, pero el panel del router solo está en la red local.
     * Devuelve el código de respuesta (200, 401…) o null.
     */
    private fun status(ip: String, credentials: String?, timeoutMs: Int): Int? = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, 80), timeoutMs)
            s.soTimeout = timeoutMs
            val auth = credentials?.let { "Authorization: Basic " + Base64.getEncoder().encodeToString(it.toByteArray()) + "\r\n" } ?: ""
            val req = "GET / HTTP/1.0\r\nHost: $ip\r\n${auth}Connection: close\r\n\r\n"
            s.getOutputStream().write(req.toByteArray(Charsets.US_ASCII))
            // Solo nos interesa la primera línea: "HTTP/1.1 401 Unauthorized"
            val line = s.getInputStream().bufferedReader(Charsets.ISO_8859_1).readLine() ?: return@use null
            line.split(' ').getOrNull(1)?.toIntOrNull()
        }
    } catch (_: Exception) {
        null
    }
}
