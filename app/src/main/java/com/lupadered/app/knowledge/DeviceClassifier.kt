package com.lupadered.app.knowledge
import com.lupadered.app.i18n.tr


/** Tipos de dispositivo, con nombre y explicación para el usuario. */
enum class DeviceType(val label: String, val description: String) {
    THIS_PHONE(tr("Este teléfono"), tr("El dispositivo desde el que usas la app.")),
    ROUTER(tr("Router"), tr("El aparato que reparte el WiFi y conecta tu casa con Internet. Todos los demás pasan por él.")),
    PHONE(tr("Celular"), tr("Un teléfono inteligente conectado a tu WiFi.")),
    TABLET(tr("Tablet"), tr("Una tablet conectada a tu WiFi.")),
    COMPUTER(tr("Computadora"), tr("Una PC o laptop.")),
    TV(tr("TV / Streaming"), tr("Un televisor inteligente, Chromecast, Fire TV, Roku, Apple TV o similar.")),
    SPEAKER(tr("Parlante"), tr("Un parlante inteligente o de música (Google/Nest, Alexa, Sonos, HomePod…).")),
    PRINTER(tr("Impresora"), tr("Una impresora o escáner de red.")),
    CAMERA(tr("Cámara"), tr("Una cámara de seguridad o grabador de video.")),
    CONSOLE(tr("Consola"), tr("Una consola de videojuegos.")),
    NAS(tr("Almacenamiento"), tr("Un disco de red (NAS) para guardar y compartir archivos.")),
    IOT(tr("Hogar inteligente"), tr("Enchufes, focos, sensores u otros aparatos inteligentes.")),
    NETWORK(tr("Equipo de red"), tr("Un repetidor, punto de acceso o switch que ayuda a llevar el WiFi a más lugares.")),
    UNKNOWN(tr("Tipo sin identificar"), tr("No pudimos identificarlo. Puede ser cualquier equipo que no se anuncia. Si no lo reconoces, revisa sus puertos o apaga tus equipos uno por uno para ver cuál desaparece.")),
}

/** Datos con los que adivinamos el tipo de un equipo. */
data class DeviceHints(
    val names: List<String>,
    val services: Set<String>,
    val openPorts: Set<Int>,
    val server: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val isGateway: Boolean = false,
    val isSelf: Boolean = false,
)

object DeviceClassifier {

    fun classify(h: DeviceHints): DeviceType {
        if (h.isSelf) return DeviceType.THIS_PHONE
        if (h.isGateway) return DeviceType.ROUTER

        val text = (h.names + listOfNotNull(h.server, h.manufacturer, h.model))
            .joinToString(" ").lowercase()
        val s = h.services
        val p = h.openPorts
        fun has(vararg words: String) = words.any { matches(text, it) }
        fun svc(vararg types: String) = types.any { it in s }

        return when {
            svc("${QS}/tablet") -> DeviceType.TABLET
            svc("${QS}/phone") -> DeviceType.PHONE
            svc("${QS}/computer") -> DeviceType.COMPUTER
            has("ipad") -> DeviceType.TABLET
            has("iphone") || svc("_apple-mobdev2._tcp") || 62078 in p && !has("ipad", "mac") -> DeviceType.PHONE
            has("playstation", "ps4", "ps5", "xbox", "nintendo") -> DeviceType.CONSOLE
            svc("_ipp._tcp", "_ipps._tcp", "_printer._tcp", "_pdl-datastream._tcp", "_scanner._tcp") ||
                9100 in p || 631 in p || 515 in p ||
                has("printer", "impresora", "laserjet", "deskjet", "officejet", "epson", "brother", "canon", "kyocera", "xerox") -> DeviceType.PRINTER
            has("synology", "diskstation", "qnap", "nas", "truenas", "unraid") || svc("_afpovertcp._tcp") && !has("mac") -> DeviceType.NAS
            svc("_googlecast._tcp", "_androidtvremote2._tcp", "_samsungmsf._tcp", "_amzn-wplay._tcp", "_roku._tcp", "_viziocast._tcp") ||
                8008 in p && 8009 in p ||
                has("tv", "chromecast", "bravia", "roku", "firetv", "fire tv", "aft", "webos", "tizen", "smarttv", "apple tv", "appletv", "android-tv", "shield") ->
                if (has("home mini", "home-mini", "nest mini", "nest-mini", "nest audio", "nest-audio", "google home", "google-home", "home max", "home-max", "speaker")) DeviceType.SPEAKER else DeviceType.TV
            svc("_spotify-connect._tcp", "_sonos._tcp", "_googlezone._tcp") ||
                has("sonos", "echo", "alexa", "homepod", "speaker", "parlante", "nest audio", "google home", "bose", "jbl") -> DeviceType.SPEAKER
            554 in p || 37777 in p ||
                has("camera", "cámara", "ipcam", "hikvision", "dahua", "ezviz", "imou", "reolink", "wyze", "dvr", "nvr", "tapo c") -> DeviceType.CAMERA
            svc("_hap._tcp", "_homekit._tcp", "_matter._tcp", "_esphomelib._tcp") || 1883 in p ||
                has("esp_", "esp-", "espressif", "tasmota", "shelly", "sonoff", "tuya", "smartlife", "smart-plug", "bulb", "hue", "wiz", "yeelight", "broadlink", "nest", "ring", "meross") -> DeviceType.IOT
            has("repeater", "extender", "mesh", "deco", "eero", "access point", "tp-link re", "orbi") -> DeviceType.NETWORK
            has("tablet", "galaxy tab", "galaxy-tab", "sm-t", "sm-x", "lenovo tab", "mediapad", "matepad") -> DeviceType.TABLET
            svc("_adb-tls-connect._tcp", QS) ||
                has("android", "galaxy", "pixel", "redmi", "xiaomi", "huawei", "moto", "oneplus", "oppo", "vivo", "realme", "honor", "poco", "sm-", "phone") -> DeviceType.PHONE
            has("macbook", "imac", "mac-mini", "macmini", "mac mini", "desktop-", "laptop-", "pc-", "windows", "thinkpad", "ubuntu", "linux") ||
                svc("_workstation._tcp", "_companion-link._tcp", "_smb._tcp", "_rdlink._tcp", "_nvstream._tcp") ||
                3389 in p || 445 in p || 139 in p -> DeviceType.COMPUTER
            svc("_airplay._tcp", "_raop._tcp") -> DeviceType.TV
            else -> DeviceType.UNKNOWN
        }
    }

    private const val QS = "_fc9f5ed42c8a._tcp"

    private val wordRegex = HashMap<String, Regex>()

    /** Palabras cortas ("tv", "ring") solo cuentan al inicio de una palabra. */
    private fun matches(text: String, word: String): Boolean {
        if (word.length > 4) return word in text
        val r = synchronized(wordRegex) {
            wordRegex.getOrPut(word) { Regex("(^|[^\\p{L}])" + Regex.escape(word)) }
        }
        return r.containsMatchIn(text)
    }

    /** Explicación de un servicio mDNS/Bonjour encontrado. */
    fun serviceLabel(type: String): String = when (type) {
        "_googlecast._tcp" -> tr("Google Cast: puedes enviarle videos o música desde tu celular")
        "_airplay._tcp" -> tr("AirPlay: recibe pantalla o video desde un iPhone/Mac")
        "_raop._tcp" -> tr("AirPlay audio: recibe música desde dispositivos Apple")
        "_companion-link._tcp" -> tr("Servicio de Apple para comunicarse con tus otros equipos Apple")
        "_apple-mobdev2._tcp" -> tr("Sincronización de iPhone/iPad por WiFi")
        "_device-info._tcp" -> tr("Publica información del modelo del equipo")
        "_ipp._tcp", "_ipps._tcp", "_printer._tcp", "_pdl-datastream._tcp" -> tr("Impresión por red")
        "_scanner._tcp" -> tr("Escaneo de documentos por red")
        "_http._tcp" -> tr("Tiene una página web (panel de configuración)")
        "_smb._tcp" -> tr("Comparte carpetas o archivos")
        "_afpovertcp._tcp" -> tr("Comparte archivos (protocolo de Apple)")
        "_workstation._tcp" -> tr("Computadora o servidor en la red")
        "_ssh._tcp" -> tr("Acceso remoto por SSH")
        "_spotify-connect._tcp" -> tr("Spotify Connect: puedes reproducir música en él desde Spotify")
        "_sonos._tcp" -> tr("Parlante Sonos")
        "_hap._tcp", "_homekit._tcp" -> tr("Accesorio de Apple Home (HomeKit)")
        "_matter._tcp" -> tr("Dispositivo de hogar inteligente compatible con Matter")
        "_androidtvremote2._tcp" -> tr("Google TV / Android TV (control remoto desde el celular)")
        "_amzn-wplay._tcp" -> tr("Amazon Fire TV / Echo")
        "_roku._tcp" -> tr("Dispositivo Roku")
        "_esphomelib._tcp" -> tr("Dispositivo ESPHome (domótica casera)")
        "_rdlink._tcp" -> tr("Escritorio remoto de Apple")
        "_sleep-proxy._udp" -> tr("Mantiene visibles a equipos Apple dormidos")
        "_nvstream._tcp" -> tr("NVIDIA GameStream / Moonlight")
        "_fc9f5ed42c8a._tcp", "_fc9f5ed42c8a._tcp/phone", "_fc9f5ed42c8a._tcp/tablet", "_fc9f5ed42c8a._tcp/computer" ->
            tr("Quick Share: puede enviar y recibir archivos con equipos cercanos")
        "_adb-tls-connect._tcp" -> tr("Android con depuración inalámbrica activada")
        "_samsungmsf._tcp" -> tr("Televisor Samsung: puedes enviarle contenido desde tu celular")
        "_googlezone._tcp" -> tr("Parlante o pantalla Google/Nest")
        else -> type.removePrefix("_").substringBefore("._")
    }
}
