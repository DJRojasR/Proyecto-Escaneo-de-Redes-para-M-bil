package com.lupadered.app.net

/** Utilidades para direcciones IPv4 representadas como Int (32 bits). */
object Ipv4 {

    fun toInt(ip: String): Int? {
        val parts = ip.trim().split('.')
        if (parts.size != 4) return null
        var result = 0
        for (p in parts) {
            if (p.isEmpty() || p.length > 3 || !p.all { it.isDigit() }) return null
            val v = p.toInt()
            if (v > 255) return null
            result = (result shl 8) or v
        }
        return result
    }

    fun fromInt(v: Int): String =
        "${(v ushr 24) and 255}.${(v ushr 16) and 255}.${(v ushr 8) and 255}.${v and 255}"

    fun isValid(ip: String): Boolean = toInt(ip) != null

    /**
     * Solo permitimos analizar direcciones privadas/locales: la app está pensada
     * para TU red. Así evitamos que se use para escanear equipos en Internet.
     */
    fun isPrivate(ip: String): Boolean {
        val v = toInt(ip) ?: return false
        val a = (v ushr 24) and 255
        val b = (v ushr 16) and 255
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254)
    }

    /** Ordena IPs numéricamente (192.168.1.2 antes que 192.168.1.10). */
    val comparator: Comparator<String> = Comparator { x, y ->
        val a = toInt(x)?.toLong()?.and(0xFFFFFFFFL) ?: Long.MAX_VALUE
        val b = toInt(y)?.toLong()?.and(0xFFFFFFFFL) ?: Long.MAX_VALUE
        a.compareTo(b)
    }

    /** "1.168.192.in-addr.arpa" para consultas DNS inversas. */
    fun reverseName(ip: String): String {
        val p = ip.split('.')
        return "${p[3]}.${p[2]}.${p[1]}.${p[0]}.in-addr.arpa"
    }
}

/** Un rango de red, por ejemplo 192.168.1.0/24. */
data class Subnet(val network: Int, val prefix: Int) {

    init {
        require(prefix in 0..32) { "Prefijo inválido: $prefix" }
    }

    val mask: Int get() = if (prefix == 0) 0 else (-1 shl (32 - prefix))
    val broadcast: Int get() = network or mask.inv()
    val cidr: String get() = "${Ipv4.fromInt(network)}/$prefix"

    /** Número de direcciones utilizables por equipos (sin red ni broadcast). */
    val usableCount: Long
        get() = when {
            prefix >= 31 -> (1L shl (32 - prefix))
            else -> (1L shl (32 - prefix)) - 2
        }

    fun contains(ip: String): Boolean {
        val v = Ipv4.toInt(ip) ?: return false
        return (v and mask) == network
    }

    /** Todas las IPs utilizables del rango, en orden. */
    fun hosts(): List<String> {
        if (prefix >= 31) {
            return (0 until (1 shl (32 - prefix))).map { Ipv4.fromInt(network + it) }
        }
        val count = usableCount.toInt()
        return (1..count).map { Ipv4.fromInt(network + it) }
    }

    companion object {
        fun of(ip: String, prefix: Int): Subnet? {
            val v = Ipv4.toInt(ip) ?: return null
            if (prefix !in 0..32) return null
            val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
            return Subnet(v and mask, prefix)
        }

        fun parse(cidr: String): Subnet? {
            val parts = cidr.split('/')
            if (parts.size != 2) return null
            return of(parts[0], parts[1].toIntOrNull() ?: return null)
        }

        /** Máximo de IPs que escaneamos de una vez (redes /22 o más pequeñas). */
        const val MAX_SCAN_PREFIX = 22

        /**
         * Si la red es enorme (por ejemplo /16 = 65.534 IPs) escanearla entera
         * tardaría mucho. En ese caso escaneamos el /24 donde está tu teléfono.
         */
        fun scanRangeFor(selfIp: String, prefix: Int): Pair<Subnet, Boolean> {
            val effective = if (prefix < MAX_SCAN_PREFIX) 24 else prefix.coerceAtMost(30)
            return Pair(of(selfIp, effective)!!, effective != prefix)
        }
    }
}
