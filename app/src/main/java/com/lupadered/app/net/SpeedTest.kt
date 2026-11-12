package com.lupadered.app.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Prueba de velocidad con el protocolo ndt7 de M-Lab (Measurement Lab), una
 * plataforma abierta que permite usar sus servidores desde cualquier app.
 *
 * - Bajada: el servidor envía datos durante ~10 s y medimos cuántos llegan.
 * - Subida: enviamos datos durante ~10 s y el servidor nos dice cuántos recibió.
 * - Latencia: el tiempo mínimo de ida y vuelta (MinRTT) que informa el servidor.
 *
 * Importante: M-Lab publica los resultados con la IP pública del usuario como datos
 * abiertos para investigación. Por eso la app pide permiso antes de la primera prueba.
 */
class SpeedTest(appVersion: String) {

    data class Server(val city: String?, val country: String?, val downloadUrl: String, val uploadUrl: String) {
        val place: String get() = listOfNotNull(city, country).joinToString(", ").ifEmpty { "Servidor M-Lab" }
    }

    /** Resultado de una dirección (bajada o subida). */
    data class Measure(val mbps: Double, val minRttMs: Double?)

    private val userAgent = "LupaDeRed/$appVersion ndt7-okhttp"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Busca el servidor de M-Lab más cercano y disponible. */
    suspend fun locate(): Server = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$LOCATE_URL?client_name=lupa-de-red-android")
            .header("User-Agent", userAgent)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("No hay servidores disponibles (${resp.code})")
            val json = JSONObject(resp.body?.string() ?: throw IOException("Respuesta vacía"))
            val results = json.optJSONArray("results") ?: throw IOException("No hay servidores disponibles")
            if (results.length() == 0) throw IOException("No hay servidores disponibles")
            val first = results.getJSONObject(0)
            val urls = first.getJSONObject("urls")
            val loc = first.optJSONObject("location")
            Server(
                city = loc?.optString("city")?.takeIf { it.isNotBlank() },
                country = loc?.optString("country")?.takeIf { it.isNotBlank() },
                downloadUrl = urls.getString("wss:///ndt/v7/download"),
                uploadUrl = urls.getString("wss:///ndt/v7/upload"),
            )
        }
    }

    /** Mide la bajada. [onProgress] recibe la velocidad actual en Mbps y el avance 0..1. */
    suspend fun download(server: Server, onProgress: (Double, Float) -> Unit): Measure = coroutineScope {
        val received = AtomicLong(0)
        var startNs = 0L
        var serverMbps: Double? = null
        var minRtt: Double? = null
        val opened = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Throwable?>()

        val ws = client.newWebSocket(request(server.downloadUrl), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                startNs = System.nanoTime()
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                received.addAndGet(bytes.size.toLong())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                received.addAndGet(text.length.toLong())
                parseMeasurement(text)?.let { m ->
                    m.minRttMs?.let { minRtt = it }
                    // El servidor envía: BytesAcked / ElapsedTime es la velocidad de bajada.
                    if (m.bytesAcked != null && m.tcpElapsedUs != null && m.tcpElapsedUs > 0) {
                        serverMbps = m.bytesAcked * 8.0 / m.tcpElapsedUs
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                closed.complete(null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.completeExceptionally(t)
                closed.complete(t)
            }
        })

        try {
            withTimeoutOrNull(10_000) { opened.await() } ?: throw IOException("El servidor no respondió")
            val ticker = launch {
                while (isActive) {
                    delay(250)
                    val elapsed = (System.nanoTime() - startNs) / 1e9
                    if (elapsed > 0) {
                        onProgress(received.get() * 8.0 / elapsed / 1e6, (elapsed / TEST_SECONDS).toFloat().coerceAtMost(1f))
                    }
                }
            }
            // El servidor cierra la conexión a los ~10 s; si no, la cerramos nosotros a los 13 s.
            val error = withTimeoutOrNull(13_000) { closed.await() }
            ticker.cancel()
            val elapsed = (System.nanoTime() - startNs) / 1e9
            val clientMbps = if (elapsed > 0) received.get() * 8.0 / elapsed / 1e6 else 0.0
            if (error != null && received.get() == 0L) throw IOException("La prueba de bajada falló", error)
            Measure(serverMbps ?: clientMbps, minRtt)
        } finally {
            ws.cancel()
        }
    }

    /** Mide la subida. [onProgress] recibe la velocidad actual en Mbps y el avance 0..1. */
    suspend fun upload(server: Server, onProgress: (Double, Float) -> Unit): Measure = coroutineScope {
        var serverMbps: Double? = null
        var minRtt: Double? = null
        val opened = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Throwable?>()

        val ws = client.newWebSocket(request(server.uploadUrl), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseMeasurement(text)?.let { m ->
                    m.minRttMs?.let { minRtt = it }
                    // El servidor recibe: BytesReceived / ElapsedTime es la velocidad de subida.
                    if (m.bytesReceived != null && m.tcpElapsedUs != null && m.tcpElapsedUs > 0) {
                        serverMbps = m.bytesReceived * 8.0 / m.tcpElapsedUs
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                closed.complete(null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.completeExceptionally(t)
                closed.complete(t)
            }
        })

        try {
            withTimeoutOrNull(10_000) { opened.await() } ?: throw IOException("El servidor no respondió")
            val start = System.nanoTime()
            var enqueued = 0L
            var size = 1 shl 13 // 8 KiB, como pide el protocolo
            var payload = ByteArray(size).toByteString()
            var lastReport = start

            withContext(Dispatchers.IO) {
                while (isActive && !closed.isCompleted) {
                    val now = System.nanoTime()
                    val elapsed = (now - start) / 1e9
                    if (elapsed >= TEST_SECONDS) break
                    // No llenar demasiado la cola de envío: así medimos la red, no la memoria.
                    if (ws.queueSize() < 4L * size) {
                        if (!ws.send(payload)) break
                        enqueued += size
                        // El mensaje crece cuando es pequeño frente a lo ya enviado (máx. 16 MiB).
                        if (size < MAX_MESSAGE && size < enqueued / 16) {
                            size *= 2
                            payload = ByteArray(size).toByteString()
                        }
                    } else {
                        delay(2)
                    }
                    if (now - lastReport > 250_000_000L) {
                        lastReport = now
                        val sent = enqueued - ws.queueSize()
                        val local = if (elapsed > 0) sent * 8.0 / elapsed / 1e6 else 0.0
                        onProgress(serverMbps ?: local, (elapsed / TEST_SECONDS).toFloat().coerceAtMost(1f))
                    }
                }
            }
            val elapsed = (System.nanoTime() - start) / 1e9
            val localMbps = if (elapsed > 0) (enqueued - ws.queueSize()) * 8.0 / elapsed / 1e6 else 0.0
            ws.close(1000, null)
            // Espera un momento la última medición del servidor.
            withTimeoutOrNull(2_000) { closed.await() }
            if (enqueued == 0L) throw IOException("La prueba de subida falló")
            Measure(serverMbps ?: localMbps, minRtt)
        } finally {
            ws.cancel()
        }
    }

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("Sec-WebSocket-Protocol", SUBPROTOCOL)
        .header("User-Agent", userAgent)
        .build()

    private class Measurement(
        val minRttMs: Double?,
        val bytesAcked: Long?,
        val bytesReceived: Long?,
        val tcpElapsedUs: Long?,
    )

    private fun parseMeasurement(text: String): Measurement? = try {
        val tcp = JSONObject(text).optJSONObject("TCPInfo")
        if (tcp == null) null else Measurement(
            minRttMs = tcp.optLong("MinRTT", 0).takeIf { it > 0 }?.let { it / 1000.0 },
            bytesAcked = tcp.optLong("BytesAcked", 0).takeIf { it > 0 },
            bytesReceived = tcp.optLong("BytesReceived", 0).takeIf { it > 0 },
            tcpElapsedUs = tcp.optLong("ElapsedTime", 0).takeIf { it > 0 },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        const val LOCATE_URL = "https://locate.measurementlab.net/v2/nearest/ndt/ndt7"
        const val SUBPROTOCOL = "net.measurementlab.ndt.v7"
        private const val TEST_SECONDS = 10.0
        private const val MAX_MESSAGE = 1 shl 24
    }
}
