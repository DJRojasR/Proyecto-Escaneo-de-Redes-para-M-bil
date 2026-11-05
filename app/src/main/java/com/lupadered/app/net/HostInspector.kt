package com.lupadered.app.net

import com.lupadered.app.knowledge.PortKnowledge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Resultado del análisis a fondo de una sola IP. */
data class InspectionResult(
    val ip: String,
    val alive: Boolean,
    val pingSamples: List<Long?>,
    val openPorts: List<Int>,
    val respondedToTcp: Boolean,
    val netbiosName: String?,
    val mdnsHostname: String?,
    val dnsName: String?,
    val durationMs: Long,
) {
    val pingOk: List<Long> get() = pingSamples.filterNotNull()
    val pingAvg: Long? get() = pingOk.takeIf { it.isNotEmpty() }?.average()?.toLong()
    val pingMin: Long? get() = pingOk.minOrNull()
    val pingMax: Long? get() = pingOk.maxOrNull()
    val pingLossPercent: Int
        get() = if (pingSamples.isEmpty()) 0 else (100 * pingSamples.count { it == null } / pingSamples.size)
    val bestName: String? get() = mdnsHostname ?: netbiosName ?: dnsName
}

/** Analiza un equipo concreto: latencia, puertos abiertos y nombres. */
class HostInspector {

    /** Escanea solo puertos. [onProgress] recibe 0..1. Devuelve (abiertos, respondió). */
    suspend fun scanPorts(
        ip: String,
        ports: List<Int> = PortKnowledge.COMMON_PORTS,
        timeoutMs: Long = 1200,
        onProgress: (Float) -> Unit = {},
    ): Pair<List<Int>, Boolean> = withContext(Dispatchers.IO) {
        val job = coroutineContext[Job]
        val open = sortedSetOf<Int>()
        var responded = false
        var done = 0
        NioTcpProber(maxInFlight = 128).run(
            ports.map { NioTcpProber.Target(ip, it) }, timeoutMs, { job?.isActive == false }
        ) { o ->
            if (o.state == NioTcpProber.State.OPEN) open += o.target.port
            if (o.hostAlive) responded = true
            done++
            onProgress(done.toFloat() / ports.size)
        }
        Pair(open.toList(), responded)
    }

    suspend fun inspect(
        ip: String,
        dnsServers: List<String>,
        onProgress: (Float, String) -> Unit,
    ): InspectionResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val job = coroutineContext[Job]
        val isCancelled = { job?.isActive == false }

        onProgress(0.05f, "Midiendo cuánto tarda en responder…")
        val pings = ArrayList<Long?>()
        repeat(4) {
            pings += NetworkScanner.ping(ip, 1000)
            onProgress(0.05f + 0.05f * (it + 1), "Midiendo cuánto tarda en responder…")
        }

        onProgress(0.25f, "Revisando puertos abiertos…")
        val (open, responded) = scanPorts(ip) { p -> onProgress(0.25f + 0.55f * p, "Revisando puertos abiertos…") }

        onProgress(0.82f, "Buscando su nombre…")
        val names = coroutineScope {
            val nb = async {
                var name: String? = null
                UdpSweep.run(listOf(UdpSweep.Send(ip, NetBios.PORT, NetBios.buildStatusQuery(0x4C45))), 1200, isCancelled) { r ->
                    if (r.sourceIp == ip) name = name ?: NetBios.parseStatusName(r.data, r.length)
                }
                name
            }
            val md = async {
                var name: String? = null
                UdpSweep.run(listOf(UdpSweep.Send(ip, Mdns.PORT, Mdns.reverseQuery(ip, 0x3001))), 1200, isCancelled) { r ->
                    for (rec in DnsMessage.parse(r.data, r.length)) {
                        if (rec.type == DnsMessage.TYPE_PTR && rec.data.trimEnd('.').endsWith(".local", true)) {
                            name = name ?: Mdns.cleanHost(rec.data)
                        }
                    }
                }
                name
            }
            val dns = async {
                var name: String? = null
                val q = DnsMessage.buildQuery(0x3002, listOf(DnsMessage.Question(Ipv4.reverseName(ip), DnsMessage.TYPE_PTR)))
                val targets = dnsServers.filter { Ipv4.isPrivate(it) }.distinct()
                UdpSweep.run(targets.map { UdpSweep.Send(it, 53, q) }, 1200, isCancelled) { r ->
                    for (rec in DnsMessage.parse(r.data, r.length)) {
                        if (rec.type == DnsMessage.TYPE_PTR) name = name ?: NetworkScanner.cleanDnsName(rec.data)
                    }
                }
                name
            }
            Triple(nb.await(), md.await(), dns.await())
        }
        onProgress(1f, "Listo")

        InspectionResult(
            ip = ip,
            alive = pings.any { it != null } || responded || names.first != null || names.second != null,
            pingSamples = pings,
            openPorts = open,
            respondedToTcp = responded,
            netbiosName = names.first,
            mdnsHostname = names.second,
            dnsName = names.third,
            durationMs = System.currentTimeMillis() - start,
        )
    }
}
