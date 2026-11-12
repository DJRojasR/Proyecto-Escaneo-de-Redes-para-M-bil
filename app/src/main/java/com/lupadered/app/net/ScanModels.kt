package com.lupadered.app.net
import com.lupadered.app.i18n.tr


/** Cómo se descubrió un dispositivo. Se muestra al usuario con su explicación. */
enum class DiscoveryMethod(val label: String, val explanation: String) {
    SELF(
        tr("Es este teléfono"),
        tr("Es el dispositivo desde el que estás usando la app.")
    ),
    TCP(
        tr("Respondió a una conexión"),
        tr("Intentamos conectarnos a puertos comunes y el equipo contestó (aceptando o rechazando). Funciona incluso cuando el equipo tiene el ping bloqueado.")
    ),
    PING(
        tr("Respondió al ping"),
        tr("El ping es como tocar la puerta: enviamos un mensaje corto y el equipo respondió \"aquí estoy\".")
    ),
    NETBIOS(
        tr("Respondió por NetBIOS"),
        tr("Las computadoras con Windows y algunos discos de red (NAS) responden con su nombre por este protocolo.")
    ),
    MDNS(
        tr("Se anunció por Bonjour/mDNS"),
        tr("Muchos dispositivos (iPhone, Mac, Chromecast, impresoras, parlantes) anuncian solos su nombre y lo que saben hacer.")
    ),
    SSDP(
        tr("Se anunció por UPnP"),
        tr("Televisores, consolas, decodificadores y routers suelen anunciarse por UPnP para que otros los encuentren.")
    ),
}

enum class ScanPhase(val label: String) {
    PREPARING(tr("Preparando…")),
    SWEEP(tr("Buscando dispositivos…")),
    SECOND_PASS(tr("Revisando otra vez los que no respondieron…")),
    NAMES(tr("Averiguando nombres…")),
    DONE(tr("Listo")),
}

/** Lo que la app necesita saber de la red para escanearla. */
data class ScanTarget(
    val subnet: Subnet,
    val selfIp: String,
    val gateway: String?,
    val dnsServers: List<String>,
    val interfaceName: String?,
)

/** Foto inmutable de lo que sabemos de un equipo. */
data class HostSnapshot(
    val ip: String,
    val alive: Boolean,
    val latencyMs: Long?,
    val methods: Set<DiscoveryMethod>,
    val openPorts: Set<Int>,
    val netbiosName: String?,
    val mdnsHostname: String?,
    val mdnsInstances: Set<String>,
    val mdnsServices: Set<String>,
    val dnsName: String?,
    val ssdpServer: String?,
    val friendlyName: String?,
    val manufacturer: String?,
    val model: String?,
    val isSelf: Boolean,
    val isGateway: Boolean,
) {
    /** El nombre más "humano" que encontramos. */
    fun bestName(): String? =
        friendlyName ?: mdnsInstances.firstOrNull() ?: mdnsHostname ?: netbiosName ?: dnsName

    /**
     * Identificador estable del equipo (sin usar la MAC). Si el equipo cambia de
     * IP pero conserva su nombre en la red, lo reconocemos igual.
     */
    fun nameKey(): String? =
        (mdnsHostname ?: netbiosName ?: dnsName ?: mdnsInstances.minOrNull() ?: friendlyName)
            ?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }
}

/** Acumula datos de un equipo mientras dura el escaneo (seguro entre hilos). */
class HostAccumulator(val ip: String) {
    var alive = false
    var latencyMs: Long? = null
    val methods = LinkedHashSet<DiscoveryMethod>()
    val openPorts = sortedSetOf<Int>()
    var netbiosName: String? = null
    var mdnsHostname: String? = null
    val mdnsInstances = LinkedHashSet<String>()
    val mdnsServices = LinkedHashSet<String>()
    var dnsName: String? = null
    var ssdpServer: String? = null
    var ssdpLocation: String? = null
    var friendlyName: String? = null
    var manufacturer: String? = null
    var model: String? = null
    var isSelf = false
    var isGateway = false

    /** Marca el equipo como vivo. Devuelve true si es la primera vez. */
    @Synchronized
    fun markAlive(method: DiscoveryMethod, latency: Long?): Boolean {
        val first = !alive
        alive = true
        methods += method
        if (latency != null && latency >= 0) {
            latencyMs = latencyMs?.let { minOf(it, latency) } ?: latency
        }
        return first
    }

    @Synchronized
    fun <T> update(block: HostAccumulator.() -> T): T = block()

    @Synchronized
    fun snapshot() = HostSnapshot(
        ip = ip,
        alive = alive,
        latencyMs = latencyMs,
        methods = methods.toSet(),
        openPorts = openPorts.toSet(),
        netbiosName = netbiosName,
        mdnsHostname = mdnsHostname,
        mdnsInstances = mdnsInstances.toSet(),
        mdnsServices = mdnsServices.toSet(),
        dnsName = dnsName,
        ssdpServer = ssdpServer,
        friendlyName = friendlyName,
        manufacturer = manufacturer,
        model = model,
        isSelf = isSelf,
        isGateway = isGateway,
    )
}
