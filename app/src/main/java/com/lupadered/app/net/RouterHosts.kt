package com.lupadered.app.net

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI

/**
 * Le pide al router su lista de equipos conectados, si la comparte.
 *
 * Muchos routers (FRITZ!Box, varios TP-Link, Huawei y ZTE de operador) traen el
 * estándar TR-064, que incluye el servicio "Hosts": la misma lista que ves en
 * su panel, con la IP y el nombre que cada equipo dio al conectarse. Algunos la
 * entregan sin contraseña dentro de la red; si el router no la tiene o la
 * protege, simplemente no obtenemos nada.
 *
 * Solo se conecta a la IP del router, nunca a otra dirección.
 */
object RouterHosts {

    private val noSupport = java.util.concurrent.ConcurrentHashMap<String, Long>()

    class Entry(val ip: String, val name: String?, val active: Boolean)

    private const val SERVICE = "urn:dslforum-org:service:Hosts:1"
    private val FIXED_PATHS = listOf(
        49000 to "/tr64desc.xml",
        49000 to "/igddesc.xml",
        37215 to "/tr64desc.xml",
        8000 to "/tr64desc.xml",
    )

    /** Bloqueante (unos segundos como máximo). [locations]: URLs que el router anunció por UPnP. */
    fun fetch(gateway: String, locations: Collection<String>, isCancelled: () -> Boolean): List<Entry> {
        if (!Ipv4.isPrivate(gateway)) return emptyList()
        val deadline = System.currentTimeMillis() + 6000
        val candidates = LinkedHashSet<Pair<Int, String>>()
        for (loc in locations) {
            try {
                val uri = URI(loc)
                if (uri.scheme?.lowercase() == "http" && uri.host == gateway) {
                    candidates += (if (uri.port > 0) uri.port else 80) to ((uri.rawPath ?: "/").ifEmpty { "/" })
                }
            } catch (_: Exception) {
            }
        }
        // Si hace poco vimos que este router no comparte su lista, no insistimos en los puertos fijos
        val recentlyFailed = (noSupport[gateway] ?: 0L) > System.currentTimeMillis() - 10 * 60_000L
        if (!recentlyFailed) candidates += FIXED_PATHS
        for ((port, path) in candidates) {
            if (isCancelled() || System.currentTimeMillis() > deadline) break
            val desc = get(gateway, port, path) ?: continue
            val control = controlUrlOf(desc) ?: continue
            val list = readHosts(gateway, port, control, deadline, isCancelled)
            if (list.isNotEmpty()) {
                noSupport.remove(gateway)
                return list
            }
        }
        if (!isCancelled()) noSupport[gateway] = System.currentTimeMillis()
        return emptyList()
    }

    /** Busca el bloque <service> del servicio Hosts y devuelve su controlURL. */
    fun controlUrlOf(xml: String): String? {
        val blocks = Regex("<service>(.*?)</service>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        for (m in blocks.findAll(xml)) {
            val b = m.groupValues[1]
            val type = tag(b, "serviceType") ?: continue
            if (!type.equals(SERVICE, ignoreCase = true)) continue
            val url = tag(b, "controlURL") ?: continue
            return if (url.startsWith("/")) url else "/$url"
        }
        return null
    }

    private fun readHosts(
        gateway: String, port: Int, control: String, deadline: Long, isCancelled: () -> Boolean,
    ): List<Entry> {
        val countXml = soap(gateway, port, control, "GetHostNumberOfEntries", "") ?: return emptyList()
        val count = tag(countXml, "NewHostNumberOfEntries")?.toIntOrNull() ?: return emptyList()
        val out = ArrayList<Entry>()
        for (i in 0 until count.coerceAtMost(300)) {
            if (isCancelled() || System.currentTimeMillis() > deadline) break
            val xml = soap(gateway, port, control, "GetGenericHostEntry", "<NewIndex>$i</NewIndex>") ?: break
            val ip = tag(xml, "NewIPAddress")?.trim() ?: continue
            if (!Ipv4.isValid(ip)) continue
            val name = tag(xml, "NewHostName")?.trim()?.takeIf { usefulName(it, ip) }
            val active = tag(xml, "NewActive")?.trim().let { it == "1" || it.equals("true", true) }
            out += Entry(ip, name, active)
        }
        return out
    }

    /** Descarta nombres vacíos o que solo repiten la IP o la MAC. */
    fun usefulName(name: String, ip: String): Boolean {
        if (name.isEmpty() || name == ip || name == "*" || name.equals("unknown", true)) return false
        if (name.matches(Regex("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}"))) return false
        if (name.startsWith("PC-") && name.removePrefix("PC-").replace('-', '.') == ip) return false
        return true
    }

    private fun soap(gateway: String, port: Int, control: String, action: String, args: String): String? {
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"$SERVICE\">$args</u:$action></s:Body></s:Envelope>"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "POST $control HTTP/1.0\r\n" +
            "Host: $gateway:$port\r\n" +
            "Content-Type: text/xml; charset=\"utf-8\"\r\n" +
            "SOAPAction: \"$SERVICE#$action\"\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        return request(gateway, port, head.toByteArray(Charsets.US_ASCII) + bytes)
    }

    private fun get(gateway: String, port: Int, path: String): String? =
        request(gateway, port, "GET $path HTTP/1.0\r\nHost: $gateway:$port\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))

    /** Petición HTTP mínima. Devuelve el cuerpo solo si la respuesta es 200. */
    private fun request(ip: String, port: Int, raw: ByteArray, timeoutMs: Int = 900): String? = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, port), timeoutMs)
            s.soTimeout = timeoutMs
            s.getOutputStream().write(raw)
            val input = s.getInputStream()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (out.size() < 256 * 1024) {
                val n = try { input.read(buf) } catch (_: SocketTimeoutException) { -1 }
                if (n < 0) break
                out.write(buf, 0, n)
            }
            val text = out.toString("UTF-8")
            val split = text.indexOf("\r\n\r\n")
            if (split < 0) return@use null
            val headers = text.substring(0, split)
            val status = headers.substringBefore("\r\n").split(' ').getOrNull(1)
            if (status != "200") return@use null
            val bodyText = text.substring(split + 4)
            if (headers.contains("transfer-encoding: chunked", ignoreCase = true)) dechunk(bodyText) else bodyText
        }
    } catch (_: Exception) {
        null
    }

    private fun dechunk(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val lineEnd = s.indexOf("\r\n", i)
            if (lineEnd < 0) break
            val size = s.substring(i, lineEnd).substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) break
            val start = lineEnd + 2
            val end = (start + size).coerceAtMost(s.length)
            sb.append(s, start, end)
            i = end + 2
        }
        return sb.toString()
    }

    private fun tag(xml: String, name: String): String? =
        Regex("<(?:[A-Za-z0-9]+:)?$name>([^<]*)</(?:[A-Za-z0-9]+:)?$name>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.get(1)
            ?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&quot;", "\"")
            ?.replace("&apos;", "'")?.replace("&amp;", "&")
            ?.trim()?.takeIf { it.isNotEmpty() }
}
