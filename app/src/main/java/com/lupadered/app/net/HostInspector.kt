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
        maxInFlight: Int = 128,
        onProgress: (Float) -> Unit = {},
    ): Pair<List<Int>, Boolean> = withContext(Dispatchers.IO) {
        val job = coroutineContext[Job]
        val open = sortedSetOf<Int>()
        var responded = false
        var done = 0
        NioTcpProber(maxInFlight = maxInFlight).run(
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
        /** Avisa cuando empieza (done = false) o termina (done = true, con resumen) cada paso: 0 ping, 1 puertos, 2 nombre. */
        onStep: (step: Int, done: Boolean, summary: String?, good: Boolean) -> Unit = { _, _, _, _ -> },
    ): InspectionResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val job = coroutineContext[Job]
        val isCancelled = { job?.isActive == false }

        onStep(0, false, null, true)
        onProgress(0.05f, "Midiendo cuánto tarda en responder…")
        val pings = ArrayList<Long?>()
        repeat(4) {
            pings += NetworkScanner.ping(ip, 1000)
            onProgress(0.05f + 0.05f * (it + 1), "Midiendo cuánto tarda en responder…")
        }
        val okPings = pings.filterNotNull()
        onStep(
            0, true,
            if (okPings.isEmpty()) "No respondió al ping (puede tenerlo bloqueado)."
            else "Respondió ${okPings.size} de 4 · promedio ${okPings.average().toLong()} ms",
            okPings.size == 4,
        )

        onStep(1, false, null, true)
        onProgress(0.25f, "Revisando puertos abiertos…")
        val (open, responded) = scanPorts(ip) { p -> onProgress(0.25f + 0.55f * p, "Revisando puertos abiertos…") }
        val risky = open.count { PortKnowledge.info(it).risk.ordinal >= com.lupadered.app.knowledge.Risk.MEDIUM.ordinal }
        onStep(
            1, true,
            "${PortKnowledge.COMMON_PORTS.size} puertos revisados · " + when {
                open.isEmpty() -> "ninguno abierto"
                else -> "${open.size} abiertos (${open.take(5).joinToString(", ")})" + if (risky > 0) " · $risky para revisar" else ""
            },
            risky == 0,
        )

        onStep(2, false, null, true)
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
        val found = names.second ?: names.first ?: names.third
        onStep(2, true, found?.let { "Se llama \"$it\"" } ?: "No anuncia su nombre en la red.", true)
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
