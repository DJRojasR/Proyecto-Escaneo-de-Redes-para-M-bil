package com.lupadered.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address

/** Información de la red local actual, obtenida SIN pedir permiso de ubicación. */
data class LocalNetwork(
    val selfIp: String,
    val prefix: Int,
    val scanSubnet: Subnet,
    /** true si la red es muy grande y solo escaneamos el /24 de tu teléfono. */
    val reducedRange: Boolean,
    val gateway: String?,
    val dnsServers: List<String>,
    val interfaceName: String?,
    val isWifi: Boolean,
    val wifiRssi: Int?,
    /** Velocidad de enlace WiFi (Mbps) entre el teléfono y el router; null si no se sabe. */
    val linkSpeedMbps: Int? = null,
    /** Nombre de la red WiFi (SSID); null si no hay permiso de ubicación o está apagada. */
    val ssid: String? = null,
    /** Identificador del punto de acceso concreto (BSSID): para detectar cambios de repetidor/mesh. */
    val bssid: String? = null,
) {
    /** Identifica la red para guardar sus dispositivos por separado (casa, oficina…). */
    /**
     * Identifica la red para guardar sus datos por separado. Si conocemos el nombre del
     * WiFi (SSID) lo usamos, así dos routers distintos con el mismo rango IP (192.168.1.x)
     * no se confunden. Sin permiso de ubicación caemos a la subred + gateway, como antes.
     */
    val key: String get() = ssid?.takeIf { it.isNotBlank() }?.let { "wifi:$it" }
        ?: "${scanSubnet.cidr}|${gateway ?: "-"}"

    fun toScanTarget() = ScanTarget(scanSubnet, selfIp, gateway, dnsServers, interfaceName)
}

object NetworkInfoProvider {

    /** Devuelve la red WiFi/Ethernet actual, o null si solo hay datos móviles o nada. */
    @Suppress("DEPRECATION")
    fun current(context: Context): LocalNetwork? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null

        // Preferimos WiFi o cable aunque haya una VPN activa.
        val candidates: List<Network> = buildList {
            cm.activeNetwork?.let { add(it) }
            addAll(cm.allNetworks)
        }.distinct()

        var chosen: Pair<Network, NetworkCapabilities>? = null
        for (n in candidates) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                chosen = n to caps
                break
            }
        }
        val (network, caps) = chosen ?: return null
        val lp: LinkProperties = cm.getLinkProperties(network) ?: return null

        val link = lp.linkAddresses.firstOrNull {
            it.address is Inet4Address && !it.address.isLoopbackAddress && !it.address.isLinkLocalAddress
        } ?: return null
        val selfIp = link.address.hostAddress ?: return null
        val prefix = link.prefixLength

        val gateway = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
        val dns = lp.dnsServers.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
        val (subnet, reduced) = Subnet.scanRangeFor(selfIp, prefix)
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        return LocalNetwork(
            selfIp = selfIp,
            prefix = prefix,
            scanSubnet = subnet,
            reducedRange = reduced,
            gateway = gateway,
            dnsServers = dns,
            interfaceName = lp.interfaceName,
            isWifi = isWifi,
            wifiRssi = if (isWifi) readRssi(context, caps) else null,
            linkSpeedMbps = if (isWifi) readLinkSpeed(context, caps) else null,
            ssid = if (isWifi) readSsid(context, caps) else null,
            bssid = if (isWifi) readBssid(context, caps) else null,
        )
    }

    /**
     * Velocidad de enlace WiFi en Mbps (WifiInfo.getLinkSpeed). Es la velocidad entre
     * el teléfono y el router, NO la del plan de Internet. Sirve para comparar zonas.
     */
    @Suppress("DEPRECATION")
    private fun wifiInfo(context: Context, caps: NetworkCapabilities): android.net.wifi.WifiInfo? =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps.transportInfo as? android.net.wifi.WifiInfo else null)
            ?: context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo

    /**
     * Nombre de la red (SSID). Android solo lo entrega si la app tiene permiso de
     * ubicación y la ubicación está encendida; si no, devuelve "<unknown ssid>".
     */
    @Suppress("DEPRECATION")
    private fun readSsid(context: Context, caps: NetworkCapabilities): String? =
        wifiInfo(context, caps)?.ssid?.trim('"')
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }

    /** Identificador del punto de acceso (BSSID); 02:00:00:00:00:00 = oculto por Android. */
    @Suppress("DEPRECATION")
    private fun readBssid(context: Context, caps: NetworkCapabilities): String? =
        wifiInfo(context, caps)?.bssid?.lowercase()
            ?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" && it != "00:00:00:00:00:00" }

    @Suppress("DEPRECATION")
    private fun readLinkSpeed(context: Context, caps: NetworkCapabilities): Int? {
        val info = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps.transportInfo as? android.net.wifi.WifiInfo else null)
            ?: context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo
        return info?.linkSpeed?.takeIf { it > 0 }
    }

    @Suppress("DEPRECATION")
    private fun readRssi(context: Context, caps: NetworkCapabilities): Int? {
        val value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps.signalStrength
        } else {
            context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.rssi
                ?: Int.MIN_VALUE
        }
        return value.takeIf { it in -126..-1 }
    }

    /**
     * Android filtra los paquetes multicast para ahorrar batería. Mientras
     * escaneamos pedimos recibirlos para escuchar los anuncios mDNS/SSDP.
     */
    fun acquireMulticastLock(context: Context): WifiManager.MulticastLock? = try {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
        wm?.createMulticastLock("lupa-scan")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    } catch (_: Exception) {
        null
    }
}
