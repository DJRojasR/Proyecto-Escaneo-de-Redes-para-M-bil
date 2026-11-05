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
) {
    /** Identifica la red para guardar sus dispositivos por separado (casa, oficina…). */
    val key: String get() = "${scanSubnet.cidr}|${gateway ?: "-"}"

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
        )
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
