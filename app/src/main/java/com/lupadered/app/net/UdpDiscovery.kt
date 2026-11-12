package com.lupadered.app.net

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI

/** Una respuesta UDP recibida. */
class UdpReply(val sourceIp: String, val sourcePort: Int, val data: ByteArray, val length: Int)

/**
 * Envía paquetes UDP pequeños a muchas IPs con un solo socket y escucha las
 * respuestas. Se usa para NetBIOS, DNS inverso y mDNS directo.
 */
object UdpSweep {

    class Send(val ip: String, val port: Int, val payload: ByteArray)

    /** Bloqueante. Envía todo dos veces (por si se pierde un paquete) y escucha [listenMs]. */
    fun run(
        sends: List<Send>,
        listenMs: Long,
        isCancelled: () -> Boolean,
        onReply: (UdpReply) -> Unit,
    ) {
        if (sends.isEmpty()) return
        val socket = DatagramSocket()
        try {
            socket.soTimeout = 150
            val start = System.currentTimeMillis()
            val deadline = start + listenMs
            var round = 0
            var nextRound = start
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < deadline && !isCancelled()) {
                if (round < 2 && System.currentTimeMillis() >= nextRound) {
                    for (s in sends) {
                        try {
                            val addr = InetAddress.getByName(s.ip)
                            socket.send(DatagramPacket(s.payload, s.payload.size, addr, s.port))
                        } catch (_: IOException) {
                            // IP inalcanzable: la ignoramos
                        }
                    }
                    round++
                    nextRound = System.currentTimeMillis() + listenMs / 2
                }
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(pkt)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: IOException) {
                    continue
                }
                val src = pkt.address?.hostAddress ?: continue
                onReply(UdpReply(src, pkt.port, pkt.data.copyOf(pkt.length), pkt.length))
            }
        } finally {
            socket.close()
        }
    }
}

/** mDNS / Bonjour: los dispositivos anuncian su nombre y sus servicios. */
object Mdns {

    private const val GROUP = "224.0.0.251"
    const val PORT = 5353

    /** Tipos de servicio que preguntamos. Cubren la mayoría de equipos de casa. */
    val SERVICE_TYPES = listOf(
        "_services._dns-sd._udp.local",
        "_googlecast._tcp.local",
        "_airplay._tcp.local",
        "_raop._tcp.local",
        "_companion-link._tcp.local",
        "_apple-mobdev2._tcp.local",
        "_device-info._tcp.local",
        "_ipp._tcp.local",
        "_ipps._tcp.local",
        "_printer._tcp.local",
        "_pdl-datastream._tcp.local",
        "_scanner._tcp.local",
        "_http._tcp.local",
        "_smb._tcp.local",
        "_afpovertcp._tcp.local",
        "_workstation._tcp.local",
        "_ssh._tcp.local",
        "_spotify-connect._tcp.local",
        "_sonos._tcp.local",
        "_hap._tcp.local",
        "_homekit._tcp.local",
        "_matter._tcp.local",
        "_androidtvremote2._tcp.local",
        "_amzn-wplay._tcp.local",
        "_roku._tcp.local",
        "_viziocast._tcp.local",
        "_esphomelib._tcp.local",
        "_rdlink._tcp.local",
        "_sleep-proxy._udp.local",
        "_nvstream._tcp.local",
        // Android y Samsung
        "_FC9F5ED42C8A._tcp.local",   // Quick Share / Nearby Share (celulares, tablets y PCs visibles)
        "_adb-tls-connect._tcp.local", // Android con depuración inalámbrica activa
        "_samsungmsf._tcp.local",      // Televisores Samsung
        "_googlezone._tcp.local",      // Parlantes y pantallas Google/Nest
    )

    /** Servicios cuyo "nombre de instancia" es un código, no un nombre para mostrar. */
    val CODED_INSTANCES = setOf(QUICK_SHARE, "_adb-tls-connect._tcp", "_sleep-proxy._udp", "_googlezone._tcp")

    const val QUICK_SHARE = "_fc9f5ed42c8a._tcp"

    /** Lo que publica un equipo con Quick Share: su tipo y, si es visible para todos, su nombre. */
    class QuickShareInfo(val kind: String?, val name: String?)

    /**
     * Decodifica el campo TXT "n" de Quick Share (base64 URL). Byte 0: el tipo de
     * equipo en los bits 1–3 (1 celular, 2 tablet, 3 computadora). Byte 17: largo
     * del nombre, que viene a continuación en UTF-8. Si el equipo solo es visible
     * para sus contactos, el nombre no viene y solo sabemos el tipo.
     */
    fun decodeQuickShare(value: String): QuickShareInfo? = try {
        val data = java.util.Base64.getUrlDecoder().decode(value.trim().trimEnd('='))
        if (data.isEmpty()) null else {
            val kind = when ((data[0].toInt() shr 1) and 7) {
                1 -> "phone"
                2 -> "tablet"
                3 -> "computer"
                else -> null
            }
            var name: String? = null
            if (data.size > 18) {
                val len = data[17].toInt() and 0xFF
                if (len in 1..data.size - 18) {
                    val n = String(data, 18, len, Charsets.UTF_8).trim()
                    if (n.length >= 2 && n.any { it.isLetter() } && n.none { it.isISOControl() || it == '\uFFFD' }) name = n
                }
            }
            QuickShareInfo(kind, name)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Pregunta (por multicast) quién ofrece cada servicio y escucha [durationMs].
     * Usamos un puerto aleatorio: así los equipos nos responden directamente.
     */
    fun browse(
        iface: NetworkInterface?,
        durationMs: Long,
        isCancelled: () -> Boolean,
        onReply: (UdpReply) -> Unit,
    ) {
        val socket = MulticastSocket(0)
        try {
            if (iface != null) {
                try {
                    socket.networkInterface = iface
                } catch (_: IOException) {
                }
            }
            socket.timeToLive = 255
            socket.soTimeout = 150
            val group = InetAddress.getByName(GROUP)
            val packets = SERVICE_TYPES.chunked(8).map { chunk ->
                DnsMessage.buildQuery(0, chunk.map { DnsMessage.Question(it, DnsMessage.TYPE_PTR) }, unicastResponse = true)
            }
            val start = System.currentTimeMillis()
            val deadline = start + durationMs
            var sends = 0
            var nextSend = start
            val buf = ByteArray(9000)
            while (System.currentTimeMillis() < deadline && !isCancelled()) {
                if (sends < 3 && System.currentTimeMillis() >= nextSend) {
                    for (p in packets) {
                        try {
                            socket.send(DatagramPacket(p, p.size, group, PORT))
                        } catch (_: IOException) {
                        }
                    }
                    sends++
                    nextSend = System.currentTimeMillis() + 1000
                }
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(pkt)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: IOException) {
                    continue
                }
                val src = pkt.address?.hostAddress ?: continue
                onReply(UdpReply(src, pkt.port, pkt.data.copyOf(pkt.length), pkt.length))
            }
        } finally {
            socket.close()
        }
    }

    /** Consulta directa a un equipo: "¿cómo te llamas?" (PTR de su IP). */
    fun reverseQuery(ip: String, id: Int): ByteArray =
        DnsMessage.buildQuery(id, listOf(DnsMessage.Question(Ipv4.reverseName(ip), DnsMessage.TYPE_PTR)))

    private val SERVICE_REGEX = Regex("(_[A-Za-z0-9-]+\\._(tcp|udp))")

    fun serviceTypeOf(name: String): String? = SERVICE_REGEX.find(name)?.value?.lowercase()

    /** "Sala TV._googlecast._tcp.local" -> "Sala TV" */
    fun instanceOf(fullName: String): String? {
        val idx = fullName.indexOf("._")
        if (idx <= 0) return null
        return fullName.substring(0, idx).replace("\\032", " ").replace("\\.", ".").trim().takeIf { it.isNotEmpty() }
    }

    /** "iPhone-de-Ana.local." -> "iPhone-de-Ana" */
    fun cleanHost(name: String): String =
        name.trimEnd('.').removeSuffix(".local").removeSuffix(".LOCAL")
}

/** UPnP / SSDP: televisores, consolas, routers y decodificadores se anuncian por aquí. */
object Ssdp {

    private const val GROUP = "239.255.255.250"
    private const val PORT = 1900

    class Response(val ip: String, val headers: Map<String, String>) {
        val server: String? get() = headers["server"]
        val location: String? get() = headers["location"]
    }

    class Description(
        val friendlyName: String?,
        val manufacturer: String?,
        val modelName: String?,
        val modelDescription: String?,
        val deviceType: String?,
    )

    fun discover(
        iface: NetworkInterface?,
        durationMs: Long,
        isCancelled: () -> Boolean,
        onResponse: (Response) -> Unit,
    ) {
        val socket = MulticastSocket(0)
        try {
            if (iface != null) {
                try {
                    socket.networkInterface = iface
                } catch (_: IOException) {
                }
            }
            socket.timeToLive = 4
            socket.soTimeout = 150
            val group = InetAddress.getByName(GROUP)
            val queries = listOf("ssdp:all", "upnp:rootdevice").map { st ->
                ("M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: $st\r\n" +
                    "USER-AGENT: Android UPnP/1.1 LupaDeRed/1.0\r\n\r\n").toByteArray(Charsets.US_ASCII)
            }
            val start = System.currentTimeMillis()
            val deadline = start + durationMs
            var sends = 0
            var nextSend = start
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < deadline && !isCancelled()) {
                if (sends < 2 && System.currentTimeMillis() >= nextSend) {
                    for (q in queries) {
                        try {
                            socket.send(DatagramPacket(q, q.size, group, PORT))
                        } catch (_: IOException) {
                        }
                    }
                    sends++
                    nextSend = System.currentTimeMillis() + 1200
                }
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(pkt)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: IOException) {
                    continue
                }
                val src = pkt.address?.hostAddress ?: continue
                val text = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                onResponse(Response(src, parseHeaders(text)))
            }
        } finally {
            socket.close()
        }
    }

    fun parseHeaders(text: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (line in text.split("\r\n", "\n").drop(1)) {
            val i = line.indexOf(':')
            if (i <= 0) continue
            map[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        return map
    }

    /**
     * Descarga la ficha del equipo (XML) que indica en LOCATION, solo si está en
     * la misma IP que respondió. Usamos un socket simple para no depender de
     * librerías HTTP y limitar el tamaño leído.
     */
    fun fetchDescription(location: String, expectedIp: String, timeoutMs: Int = 1500): Description? {
        return try {
            val uri = URI(location)
            if (uri.scheme?.lowercase() != "http" || uri.host != expectedIp) return null
            val port = if (uri.port > 0) uri.port else 80
            val path = (uri.rawPath ?: "/").ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
            Socket().use { s ->
                s.connect(InetSocketAddress(expectedIp, port), timeoutMs)
                s.soTimeout = timeoutMs
                val req = "GET $path HTTP/1.0\r\nHost: $expectedIp:$port\r\nConnection: close\r\n\r\n"
                s.getOutputStream().write(req.toByteArray(Charsets.US_ASCII))
                val input = s.getInputStream()
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(4096)
                while (out.size() < 64 * 1024) {
                    val n = try { input.read(buf) } catch (_: SocketTimeoutException) { -1 }
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                parseDescription(out.toString("UTF-8"))
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseDescription(xml: String): Description {
        fun tag(name: String): String? =
            Regex("<$name>([^<]{1,120})</$name>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
                ?.let { unescapeXml(it).trim() }?.takeIf { it.isNotEmpty() }
        return Description(
            friendlyName = tag("friendlyName"),
            manufacturer = tag("manufacturer"),
            modelName = tag("modelName"),
            modelDescription = tag("modelDescription"),
            deviceType = tag("deviceType"),
        )
    }

    private fun unescapeXml(s: String) = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")
}
