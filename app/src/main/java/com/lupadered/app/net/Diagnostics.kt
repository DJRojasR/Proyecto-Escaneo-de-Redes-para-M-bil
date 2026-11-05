package com.lupadered.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

enum class DiagStatus { GOOD, WARN, BAD, UNKNOWN }

/** Un resultado del diagnóstico, explicado para cualquier persona. */
data class DiagItem(
    val title: String,
    val status: DiagStatus,
    val value: String,
    val explanation: String,
    val advice: String? = null,
)

/**
 * Diagnóstico de la conexión: ¿el problema es el WiFi, el router, Internet o el DNS?
 */
class Diagnostics {

    suspend fun run(
        gateway: String?,
        dnsServers: List<String>,
        wifiRssi: Int?,
        onProgress: (Float) -> Unit,
    ): List<DiagItem> = withContext(Dispatchers.IO) {
        val items = ArrayList<DiagItem>()

        items += signalItem(wifiRssi)
        onProgress(0.1f)

        items += routerItem(gateway)
        onProgress(0.45f)

        val internet = internetItem()
        items += internet
        onProgress(0.75f)

        items += dnsItem(dnsServers.firstOrNull() ?: gateway)
        onProgress(1f)

        items
    }

    private fun signalItem(rssi: Int?): DiagItem {
        if (rssi == null || rssi <= -127 || rssi >= 0) {
            return DiagItem(
                "Señal WiFi", DiagStatus.UNKNOWN, "No disponible",
                "No pudimos leer la intensidad de la señal (por ejemplo, si estás conectado por cable o datos).",
            )
        }
        val (status, text) = when {
            rssi >= -55 -> DiagStatus.GOOD to "Excelente"
            rssi >= -67 -> DiagStatus.GOOD to "Buena"
            rssi >= -75 -> DiagStatus.WARN to "Regular"
            else -> DiagStatus.BAD to "Débil"
        }
        return DiagItem(
            "Señal WiFi", status, "$text ($rssi dBm)",
            "Mide qué tan fuerte llega el WiFi a tu teléfono. Cuanto más cerca de 0, mejor: -50 es excelente y -80 es muy débil.",
            if (status == DiagStatus.GOOD) null
            else "Acércate al router, evita paredes gruesas o considera un repetidor o sistema mesh."
        )
    }

    private fun routerItem(gateway: String?): DiagItem {
        if (gateway == null) {
            return DiagItem(
                "Router", DiagStatus.UNKNOWN, "No encontrado",
                "No pudimos saber cuál es tu router. Revisa que estés conectado a un WiFi.",
            )
        }
        val samples = (1..6).map { tcpOrPing(gateway) }
        val ok = samples.filterNotNull()
        val loss = 100 * samples.count { it == null } / samples.size
        if (ok.isEmpty()) {
            return DiagItem(
                "Router ($gateway)", DiagStatus.BAD, "No responde",
                "Tu teléfono no recibió respuesta del router. Puede que el router bloquee estas pruebas, o que la conexión con él esté fallando.",
                "Si además no tienes Internet, reinicia el router."
            )
        }
        val avg = ok.average().toLong()
        val jitter = (ok.maxOrNull()!! - ok.minOrNull()!!)
        val status = when {
            loss > 20 || avg > 100 -> DiagStatus.BAD
            loss > 0 || avg > 30 || jitter > 80 -> DiagStatus.WARN
            else -> DiagStatus.GOOD
        }
        return DiagItem(
            "Router ($gateway)", status, "$avg ms · pérdida $loss%",
            "Es el tiempo que tarda el router en contestar a tu teléfono. Dentro de casa lo normal es menos de 10–20 ms. " +
                "La \"pérdida\" son mensajes que nunca llegaron.",
            when (status) {
                DiagStatus.GOOD -> null
                else -> "El problema parece estar entre tu teléfono y el router (señal WiFi, interferencias o router saturado). " +
                    "Prueba acercarte, cambiar a la banda de 5 GHz o reiniciar el router."
            }
        )
    }

    private fun internetItem(): DiagItem {
        val targets = listOf("1.1.1.1" to 443, "8.8.8.8" to 443, "9.9.9.9" to 443)
        val times = targets.map { (ip, port) -> tcpConnect(ip, port, 2500) }
        val ok = times.filterNotNull()
        if (ok.isEmpty()) {
            return DiagItem(
                "Internet", DiagStatus.BAD, "Sin conexión",
                "Tu red local funciona pero no logramos llegar a Internet.",
                "Revisa si el router muestra alguna luz roja, reinícialo o consulta a tu proveedor de Internet."
            )
        }
        val best = ok.minOrNull()!!
        val status = when {
            best <= 60 -> DiagStatus.GOOD
            best <= 150 -> DiagStatus.WARN
            else -> DiagStatus.BAD
        }
        return DiagItem(
            "Internet", status, "$best ms",
            "Es lo que tarda en llegar a un servidor de Internet y volver. Menos de 60 ms es muy bueno para videollamadas y juegos.",
            if (status == DiagStatus.GOOD) null
            else "Si el router responde rápido pero esto es lento, el problema probablemente está en tu proveedor de Internet o en que alguien usa mucho la red (descargas, videos en 4K)."
        )
    }

    private fun dnsItem(server: String?): DiagItem {
        if (server == null) {
            return DiagItem("DNS", DiagStatus.UNKNOWN, "No disponible", "No encontramos tu servidor DNS.")
        }
        val name = "www.google.com"
        val time = dnsQueryTime(server, name)
        if (time == null) {
            return DiagItem(
                "DNS ($server)", DiagStatus.BAD, "No responde",
                "El DNS es como la agenda de Internet: traduce nombres como google.com en direcciones. Si falla, las páginas no cargan aunque haya conexión.",
                "Prueba reiniciar el router o configurar un DNS público (por ejemplo 1.1.1.1 u 8.8.8.8) en tu router."
            )
        }
        val status = when {
            time <= 80 -> DiagStatus.GOOD
            time <= 250 -> DiagStatus.WARN
            else -> DiagStatus.BAD
        }
        return DiagItem(
            "DNS ($server)", status, "$time ms",
            "El DNS traduce nombres como google.com en direcciones. Si es lento, las páginas tardan en empezar a cargar.",
            if (status == DiagStatus.GOOD) null
            else "Un DNS público (1.1.1.1 u 8.8.8.8) suele ser más rápido. Se configura en el router."
        )
    }

    private fun tcpOrPing(ip: String): Long? =
        NetworkScanner.ping(ip, 1000) ?: listOf(80, 443, 53).firstNotNullOfOrNull { tcpConnect(ip, it, 1000, refusedCounts = true) }

    private fun tcpConnect(ip: String, port: Int, timeoutMs: Int, refusedCounts: Boolean = false): Long? {
        val start = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
            (System.nanoTime() - start) / 1_000_000L
        } catch (e: IOException) {
            if (refusedCounts && (e.message ?: "").contains("refused", true)) (System.nanoTime() - start) / 1_000_000L
            else null
        }
    }

    private fun dnsQueryTime(server: String, name: String): Long? {
        return try {
            DatagramSocket().use { s ->
                s.soTimeout = 2000
                val id = Random.nextInt(0, 0xFFFF)
                val q = DnsMessage.buildQuery(id, listOf(DnsMessage.Question(name, DnsMessage.TYPE_A)))
                val start = System.nanoTime()
                s.send(DatagramPacket(q, q.size, InetAddress.getByName(server), 53))
                val buf = ByteArray(1500)
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                (System.nanoTime() - start) / 1_000_000L
            }
        } catch (_: IOException) {
            null
        }
    }
}
