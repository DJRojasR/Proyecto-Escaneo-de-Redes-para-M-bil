package com.lupadered.app.net

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Recibe el avance del escaneo. Puede llamarse desde varios hilos a la vez. */
interface ScanListener {
    fun onPhase(phase: ScanPhase, progress: Float)
    fun onHost(host: HostSnapshot)
}

/**
 * Motor de escaneo. Para no "perder" dispositivos combina varios métodos al
 * mismo tiempo y hace una segunda pasada más paciente:
 *
 *  1. Barrido rápido: conexiones TCP a puertos comunes + ping + NetBIOS +
 *     escucha de anuncios mDNS (Bonjour) y SSDP (UPnP).
 *  2. Segunda pasada: solo las IPs que no respondieron, con más tiempo de espera
 *     (para celulares en reposo o equipos lentos).
 *  3. Nombres: DNS inverso del router, mDNS directo y fichas UPnP.
 *
 * Los resultados se van entregando en vivo a [ScanListener.onHost].
 */
class NetworkScanner {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val pingDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(128)

    suspend fun scan(target: ScanTarget, listener: ScanListener): List<HostSnapshot> =
        withContext(Dispatchers.IO) {
            val job = coroutineContext[Job]
            val isCancelled = { job?.isActive == false }
            val subnet = target.subnet
            val hosts = subnet.hosts()
            val acc = ConcurrentHashMap<String, HostAccumulator>()
            val iface = target.interfaceName?.let {
                try {
                    NetworkInterface.getByName(it)
                } catch (_: Exception) {
                    null
                }
            }

            fun host(ip: String): HostAccumulator = acc.computeIfAbsent(ip) { HostAccumulator(it) }
            fun publish(h: HostAccumulator) {
                if (h.update { alive }) listener.onHost(h.snapshot())
            }
            fun alive(ip: String, method: DiscoveryMethod, latency: Long?) {
                if (!subnet.contains(ip)) return
                val h = host(ip)
                h.markAlive(method, latency)
                publish(h)
            }

            listener.onPhase(ScanPhase.PREPARING, 0f)

            // Tu propio teléfono y el router
            host(target.selfIp).apply {
                update { isSelf = true }
                markAlive(DiscoveryMethod.SELF, 0)
                publish(this)
            }
            target.gateway?.takeIf { subnet.contains(it) }?.let { gw -> host(gw).update { isGateway = true } }

            // ---------------- Fase 1: barrido ----------------
            listener.onPhase(ScanPhase.SWEEP, 0.02f)
            val others = hosts.filter { it != target.selfIp }
            val tcpTargets = others.flatMap { ip -> DISCOVERY_PORTS.map { NioTcpProber.Target(ip, it) } }
            val totalUnits = (tcpTargets.size + others.size).coerceAtLeast(1)
            val done = AtomicInteger(0)
            fun tick(phase: ScanPhase, start: Float, span: Float, total: Int) {
                val d = done.incrementAndGet()
                if (d % 64 == 0) listener.onPhase(phase, start + span * minOf(d, total) / total)
            }

            coroutineScope {
                launch {
                    Mdns.browse(iface, 3500, isCancelled) { r -> handleMdns(r, subnet, ::host, ::alive) }
                }
                launch {
                    Ssdp.discover(iface, 3500, isCancelled) { r ->
                        if (subnet.contains(r.ip)) {
                            host(r.ip).update {
                                if (ssdpServer == null) ssdpServer = r.server
                                if (ssdpLocation == null) ssdpLocation = r.location
                            }
                            alive(r.ip, DiscoveryMethod.SSDP, null)
                        }
                    }
                }
                launch {
                    val q = NetBios.buildStatusQuery(0x4C44)
                    UdpSweep.run(others.map { UdpSweep.Send(it, NetBios.PORT, q) }, 2500, isCancelled) { r ->
                        if (r.sourcePort == NetBios.PORT) {
                            val name = NetBios.parseStatusName(r.data, r.length)
                            if (name != null && subnet.contains(r.sourceIp)) host(r.sourceIp).update { netbiosName = name }
                            alive(r.sourceIp, DiscoveryMethod.NETBIOS, null)
                        }
                    }
                }
                launch {
                    NioTcpProber(maxInFlight = 1024).run(tcpTargets, 800, isCancelled) { o ->
                        if (o.hostAlive) {
                            if (o.state == NioTcpProber.State.OPEN) host(o.target.ip).update { openPorts += o.target.port }
                            alive(o.target.ip, DiscoveryMethod.TCP, o.rttMs)
                        }
                        tick(ScanPhase.SWEEP, 0.02f, 0.68f, totalUnits)
                    }
                }
                val sem = Semaphore(128)
                for (ip in others) {
                    launch(pingDispatcher) {
                        sem.withPermit {
                            ping(ip, 800)?.let { alive(ip, DiscoveryMethod.PING, it) }
                            tick(ScanPhase.SWEEP, 0.02f, 0.68f, totalUnits)
                        }
                    }
                }
            }

            // ---------------- Fase 2: segunda pasada ----------------
            val missing = others.filter { acc[it]?.update { alive } != true }
            if (missing.isNotEmpty() && !isCancelled()) {
                listener.onPhase(ScanPhase.SECOND_PASS, 0.70f)
                done.set(0)
                val retryTargets = missing.flatMap { ip -> SECOND_PASS_PORTS.map { NioTcpProber.Target(ip, it) } }
                val total2 = (retryTargets.size + missing.size).coerceAtLeast(1)
                coroutineScope {
                    launch {
                        NioTcpProber(maxInFlight = 1024).run(retryTargets, 1800, isCancelled) { o ->
                            if (o.hostAlive) {
                                if (o.state == NioTcpProber.State.OPEN) host(o.target.ip).update { openPorts += o.target.port }
                                alive(o.target.ip, DiscoveryMethod.TCP, o.rttMs)
                            }
                            tick(ScanPhase.SECOND_PASS, 0.70f, 0.15f, total2)
                        }
                    }
                    val sem = Semaphore(128)
                    for (ip in missing) {
                        launch(pingDispatcher) {
                            sem.withPermit {
                                ping(ip, 1500)?.let { alive(ip, DiscoveryMethod.PING, it) }
                                tick(ScanPhase.SECOND_PASS, 0.70f, 0.15f, total2)
                            }
                        }
                    }
                }
            }

            // ---------------- Fase 3: nombres ----------------
            if (!isCancelled()) {
                listener.onPhase(ScanPhase.NAMES, 0.86f)
                val aliveIps = acc.values.filter { it.update { alive } && !it.isSelf }.map { it.ip }
                val dnsTargets = (listOfNotNull(target.gateway) + target.dnsServers)
                    .filter { Ipv4.isPrivate(it) }.distinct()
                coroutineScope {
                    launch {
                        // DNS inverso: muchos routers saben el nombre que cada equipo dio al conectarse
                        val sends = ArrayList<UdpSweep.Send>()
                        aliveIps.forEachIndexed { i, ip ->
                            val q = DnsMessage.buildQuery(0x1000 + i, listOf(DnsMessage.Question(Ipv4.reverseName(ip), DnsMessage.TYPE_PTR)))
                            dnsTargets.forEach { dns -> sends += UdpSweep.Send(dns, 53, q) }
                        }
                        UdpSweep.run(sends, 1500, isCancelled) { r ->
                            for (rec in DnsMessage.parse(r.data, r.length)) {
                                if (rec.type != DnsMessage.TYPE_PTR) continue
                                val ip = ipFromReverse(rec.name) ?: continue
                                val name = cleanDnsName(rec.data) ?: continue
                                if (subnet.contains(ip)) host(ip).update { if (dnsName == null) dnsName = name }
                            }
                        }
                    }
                    launch {
                        // mDNS directo: le preguntamos a cada equipo su nombre .local
                        val sends = aliveIps.mapIndexed { i, ip -> UdpSweep.Send(ip, Mdns.PORT, Mdns.reverseQuery(ip, 0x2000 + i)) }
                        UdpSweep.run(sends, 1500, isCancelled) { r ->
                            handleMdns(r, subnet, ::host, ::alive)
                        }
                    }
                    for (ip in aliveIps) {
                        val location = acc[ip]?.update { ssdpLocation } ?: continue
                        launch {
                            Ssdp.fetchDescription(location, ip)?.let { d ->
                                host(ip).update {
                                    if (friendlyName == null) friendlyName = d.friendlyName
                                    if (manufacturer == null) manufacturer = d.manufacturer
                                    if (model == null) model = d.modelName ?: d.modelDescription
                                }
                            }
                        }
                    }
                }
                for (h in acc.values) publish(h)
            }

            listener.onPhase(ScanPhase.DONE, 1f)
            acc.values.filter { it.update { alive } }.map { it.snapshot() }
                .sortedWith(compareBy(Ipv4.comparator) { it.ip })
        }

    companion object {
        /** Puertos que prueban casi todos los tipos de equipo de una casa. */
        val DISCOVERY_PORTS = listOf(
            80, 443, 22, 445, 139, 8080, 62078, 8008, 8009, 7000, 554, 9100, 5000, 3389, 53, 49152,
        )
        val SECOND_PASS_PORTS = listOf(80, 443, 62078, 445, 8080, 7000)

        /** Ping (ICMP). Devuelve la latencia en ms o null si no respondió. */
        fun ping(ip: String, timeoutMs: Int): Long? {
            return try {
                val start = System.nanoTime()
                val ok = InetAddress.getByName(ip).isReachable(timeoutMs)
                if (ok) (System.nanoTime() - start) / 1_000_000L else null
            } catch (_: IOException) {
                null
            }
        }

        fun ipFromReverse(name: String): String? {
            val n = name.trimEnd('.').lowercase()
            if (!n.endsWith(".in-addr.arpa")) return null
            val parts = n.removeSuffix(".in-addr.arpa").split('.')
            if (parts.size != 4) return null
            val ip = parts.reversed().joinToString(".")
            return if (Ipv4.isValid(ip)) ip else null
        }

        /** "laptop-ana.lan." -> "laptop-ana". Descarta nombres genéricos. */
        fun cleanDnsName(raw: String): String? {
            val n = raw.trimEnd('.')
            if (n.isEmpty()) return null
            val first = n.substringBefore('.')
            if (first.all { it.isDigit() || it == '-' }) return null
            if (first.equals("localhost", true)) return null
            return first
        }

        private val TXT_FRIENDLY = setOf("fn", "n", "name")
        private val TXT_MODEL = setOf("md", "model", "am", "ty", "rpmd", "product")

        fun handleMdns(
            r: UdpReply,
            subnet: Subnet,
            host: (String) -> HostAccumulator,
            alive: (String, DiscoveryMethod, Long?) -> Unit,
        ) {
            val src = r.sourceIp
            if (!Ipv4.isValid(src) || !subnet.contains(src)) return
            val records = DnsMessage.parse(r.data, r.length)
            if (records.isEmpty()) return
            val h = host(src)
            for (rec in records) {
                when (rec.type) {
                    DnsMessage.TYPE_A -> {
                        val name = Mdns.cleanHost(rec.name)
                        if (!rec.name.trimEnd('.').endsWith(".local", ignoreCase = true)) continue
                        if (rec.data == src) {
                            h.update { if (mdnsHostname == null) mdnsHostname = name }
                        } else if (subnet.contains(rec.data)) {
                            // Un "proxy de reposo" (p. ej. Apple TV) responde por un equipo dormido
                            host(rec.data).update { if (mdnsHostname == null) mdnsHostname = name }
                        }
                    }
                    DnsMessage.TYPE_PTR -> {
                        when {
                            rec.name.endsWith(".in-addr.arpa", ignoreCase = true) ||
                                rec.name.endsWith(".in-addr.arpa.", ignoreCase = true) -> {
                                if (rec.data.trimEnd('.').endsWith(".local", ignoreCase = true)) {
                                    h.update { if (mdnsHostname == null) mdnsHostname = Mdns.cleanHost(rec.data) }
                                }
                            }
                            rec.name.startsWith("_services._dns-sd", ignoreCase = true) -> {
                                Mdns.serviceTypeOf(rec.data)?.let { s -> h.update { mdnsServices += s } }
                            }
                            else -> {
                                val svc = Mdns.serviceTypeOf(rec.name)
                                val inst = Mdns.instanceOf(rec.data)
                                h.update {
                                    if (svc != null) mdnsServices += svc
                                    if (inst != null && svc != "_sleep-proxy._udp") mdnsInstances += inst
                                }
                            }
                        }
                    }
                    DnsMessage.TYPE_SRV -> {
                        val svc = Mdns.serviceTypeOf(rec.name)
                        h.update {
                            if (svc != null) mdnsServices += svc
                            if (mdnsHostname == null && rec.data.trimEnd('.').endsWith(".local", true)) {
                                mdnsHostname = Mdns.cleanHost(rec.data)
                            }
                        }
                    }
                    DnsMessage.TYPE_TXT -> {
                        for (kv in rec.data.split(';')) {
                            val i = kv.indexOf('=')
                            if (i <= 0) continue
                            val k = kv.substring(0, i).lowercase()
                            val v = kv.substring(i + 1).trim()
                            if (v.isEmpty() || v.length > 80) continue
                            h.update {
                                if (k in TXT_FRIENDLY && friendlyName == null) friendlyName = v
                                if (k in TXT_MODEL && model == null) model = v
                            }
                        }
                    }
                }
            }
            alive(src, DiscoveryMethod.MDNS, null)
        }
    }
}
