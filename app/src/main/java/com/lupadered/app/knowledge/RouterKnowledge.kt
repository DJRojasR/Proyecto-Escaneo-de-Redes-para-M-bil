package com.lupadered.app.knowledge

/**
 * Base local de conocimiento sobre routers (Premium, RF-47): marcas que suelen
 * traer clave de fábrica conocida, firmwares/servidores web antiguos y la
 * lista corta de credenciales que se prueban (solo con permiso del usuario).
 */
object RouterKnowledge {

    /** Puertos que se revisan específicamente en el router. */
    val ROUTER_PORTS: List<Int> = listOf(21, 22, 23, 53, 80, 443, 1900, 5000, 7547, 8080, 8443, 49152, 52869)

    /** Puertos que delatan UPnP/IGD activo en el router (1900 es UDP y no se sondea por TCP). */
    val UPNP_PORTS: Set<Int> = setOf(5000, 49152, 52869)

    /** Puerto TR-069 (gestión remota del operador). */
    const val TR069_PORT = 7547

    /** Marcas cuyos modelos de operador suelen salir con usuario/clave de fábrica conocidos. */
    private val DEFAULT_CRED_BRANDS = listOf(
        "tp-link", "tplink", "d-link", "dlink", "netgear", "linksys", "huawei", "zte", "zyxel",
        "tenda", "belkin", "arris", "technicolor", "sagemcom", "movistar", "askey", "ubee", "mercusys",
    )

    /** Firmas de servidores web embebidos antiguos o con historial de fallos graves. */
    private val OUTDATED_SIGNATURES: List<Pair<Regex, String>> = listOf(
        Regex("boa/0\\.9", RegexOption.IGNORE_CASE) to "Boa 0.9x (servidor web abandonado desde 2005)",
        Regex("goahead-?webs?/?([12]\\.|3\\.[0-5])", RegexOption.IGNORE_CASE) to "GoAhead antiguo",
        Regex("mini_httpd/1\\.[0-9]\\b", RegexOption.IGNORE_CASE) to "mini_httpd antiguo",
        Regex("micro_httpd", RegexOption.IGNORE_CASE) to "micro_httpd",
        Regex("lighttpd/1\\.[0-3]\\.", RegexOption.IGNORE_CASE) to "lighttpd muy antiguo",
        Regex("miniupnpd/1\\.[0-5]\\b", RegexOption.IGNORE_CASE) to "miniupnpd antiguo",
        Regex("linux/2\\.[0-6]\\.", RegexOption.IGNORE_CASE) to "kernel Linux 2.x",
        Regex("upnp/1\\.0", RegexOption.IGNORE_CASE) to "UPnP 1.0 (pila sin actualizar)",
        Regex("dropbear[ _]?(2012|2013|2014|0\\.5)", RegexOption.IGNORE_CASE) to "Dropbear antiguo",
        Regex("rompager", RegexOption.IGNORE_CASE) to "RomPager (vulnerable a Misfortune Cookie en versiones viejas)",
    )

    /** Credenciales comunes que se prueban con Basic Auth. Se limita a 10 intentos. */
    val COMMON_CREDENTIALS: List<Pair<String, String>> = listOf(
        "admin" to "admin",
        "admin" to "password",
        "admin" to "1234",
        "admin" to "",
        "admin" to "12345",
        "root" to "root",
        "root" to "admin",
        "user" to "user",
        "admin" to "motorola",
        "support" to "support",
    )

    /** ¿La marca/modelo suele traer clave de fábrica conocida? Devuelve la marca detectada. */
    fun factoryDefaultBrand(vararg fields: String?): String? {
        val text = fields.filterNotNull().joinToString(" ").lowercase()
        if (text.isBlank()) return null
        return DEFAULT_CRED_BRANDS.firstOrNull { text.contains(it) }
    }

    /** Descripción del componente antiguo detectado en el servidor/firmware, o null. */
    fun outdatedFirmware(vararg fields: String?): String? {
        val text = fields.filterNotNull().joinToString(" | ")
        if (text.isBlank()) return null
        return OUTDATED_SIGNATURES.firstOrNull { it.first.containsMatchIn(text) }?.second
    }
}
