package com.lupadered.app.knowledge
import com.lupadered.app.i18n.tr


enum class Risk(val label: String) {
    INFO(tr("Normal")),
    LOW(tr("Riesgo bajo")),
    MEDIUM(tr("Revisar")),
    HIGH(tr("Riesgo alto")),
}

/** Explicación en lenguaje sencillo de un puerto abierto. */
data class PortInfo(
    val port: Int,
    val service: String,
    val whatItMeans: String,
    val risk: Risk,
    val advice: String? = null,
)

object PortKnowledge {

    private val ports: Map<Int, PortInfo> = listOf(
        PortInfo(20, tr("FTP (datos)"), tr("Transferencia de archivos antigua."), Risk.MEDIUM,
            tr("FTP envía contraseñas sin cifrar. Si no lo usas, desactívalo.")),
        PortInfo(21, "FTP", tr("Servidor para compartir archivos con un método antiguo."), Risk.MEDIUM,
            tr("FTP envía contraseñas sin cifrar. Si no lo usas, desactívalo.")),
        PortInfo(22, "SSH", tr("Permite controlar el equipo a distancia por línea de comandos (común en Linux, NAS y routers)."), Risk.LOW,
            tr("Está bien si lo usas tú. Asegúrate de tener una contraseña fuerte.")),
        PortInfo(23, tr("Telnet"), tr("Control remoto muy antiguo y sin cifrar. Lo usan algunos routers y cámaras baratas."), Risk.HIGH,
            tr("Cualquiera en tu red puede ver lo que se escribe, incluidas contraseñas. Desactívalo si puedes.")),
        PortInfo(25, "SMTP", tr("Servidor de envío de correos."), Risk.MEDIUM,
            tr("Es raro en una casa. Si no sabes por qué está, revisa el equipo.")),
        PortInfo(53, "DNS", tr("Traduce nombres (como google.com) a direcciones. Lo normal es que lo tenga tu router."), Risk.INFO),
        PortInfo(67, "DHCP", tr("Reparte las direcciones IP a los equipos que se conectan. Normal en el router."), Risk.INFO),
        PortInfo(80, tr("Web (HTTP)"), tr("Tiene una página web, normalmente un panel de configuración (routers, impresoras, cámaras)."), Risk.LOW,
            tr("Si es un panel de administración, cambia la contraseña de fábrica.")),
        PortInfo(81, tr("Web alternativa"), tr("Otra página web; frecuente en cámaras IP."), Risk.LOW,
            tr("Cambia la contraseña de fábrica del equipo.")),
        PortInfo(88, tr("Kerberos / Web"), tr("Autenticación de redes de empresa o un panel web."), Risk.INFO),
        PortInfo(110, "POP3", tr("Servidor para recibir correos."), Risk.MEDIUM),
        PortInfo(111, "RPC", tr("Servicio de Linux/NAS para compartir carpetas (NFS)."), Risk.MEDIUM,
            tr("Normal en un NAS. En otros equipos, revisa si lo necesitas.")),
        PortInfo(135, tr("Windows RPC"), tr("Servicio interno de Windows para comunicarse con otras PCs."), Risk.MEDIUM,
            tr("Normal en PCs con Windows en una red privada. No debe estar abierto a Internet.")),
        PortInfo(139, tr("Compartir archivos (NetBIOS)"), tr("Carpetas o impresoras compartidas de Windows o Samba."), Risk.MEDIUM,
            tr("Comparte solo las carpetas que necesites y con contraseña.")),
        PortInfo(143, "IMAP", tr("Servidor de correo."), Risk.MEDIUM),
        PortInfo(161, "SNMP", tr("Permite consultar el estado del equipo (routers, impresoras)."), Risk.MEDIUM),
        PortInfo(443, tr("Web segura (HTTPS)"), tr("Página web cifrada, normalmente el panel de configuración del equipo."), Risk.INFO),
        PortInfo(445, tr("Compartir archivos (SMB)"), tr("Carpetas compartidas de Windows, Mac o un NAS."), Risk.MEDIUM,
            tr("Comparte solo lo necesario y con contraseña. Nunca debe estar abierto a Internet.")),
        PortInfo(515, tr("Impresión (LPD)"), tr("Recibe trabajos de impresión."), Risk.INFO),
        PortInfo(548, "AFP", tr("Compartir archivos de Apple (Mac antiguos, Time Capsule, NAS)."), Risk.LOW),
        PortInfo(554, tr("Video (RTSP)"), tr("Transmite video en vivo. Casi siempre es una cámara de seguridad o un grabador (DVR/NVR)."), Risk.MEDIUM,
            tr("Si es una cámara, cambia la contraseña de fábrica para que nadie pueda ver el video.")),
        PortInfo(631, tr("Impresión (IPP)"), tr("Impresora o servidor de impresión."), Risk.INFO),
        PortInfo(853, tr("DNS seguro"), tr("Consultas DNS cifradas."), Risk.INFO),
        PortInfo(1080, tr("Proxy SOCKS"), tr("Reenvía conexiones de otros equipos."), Risk.HIGH,
            tr("Un proxy desconocido puede usarse para ocultar tráfico. Revisa qué equipo es.")),
        PortInfo(1433, tr("SQL Server"), tr("Base de datos de Microsoft."), Risk.MEDIUM),
        PortInfo(1883, "MQTT", tr("Mensajería de dispositivos inteligentes (domótica)."), Risk.LOW,
            tr("Normal si tienes Home Assistant o domótica. Protégelo con contraseña.")),
        PortInfo(1900, "UPnP", tr("Anuncio automático de dispositivos."), Risk.LOW),
        PortInfo(2049, "NFS", tr("Carpetas compartidas de Linux/NAS."), Risk.MEDIUM),
        PortInfo(3000, tr("Web de desarrollo"), tr("Una aplicación web (paneles, herramientas de desarrollo)."), Risk.LOW),
        PortInfo(3306, "MySQL", tr("Base de datos."), Risk.MEDIUM,
            tr("Normal en un servidor. Asegúrate de que tenga contraseña.")),
        PortInfo(3389, tr("Escritorio remoto (RDP)"), tr("Permite controlar la PC con Windows a distancia."), Risk.HIGH,
            tr("Si no lo usas, desactívalo. Es uno de los puertos más atacados cuando queda expuesto a Internet.")),
        PortInfo(5000, tr("Web / UPnP / NAS"), tr("Panel web (por ejemplo de un NAS Synology) o servicio AirPlay en Mac."), Risk.LOW),
        PortInfo(5001, tr("Web segura de NAS"), tr("Panel de administración de un NAS."), Risk.LOW),
        PortInfo(5353, "mDNS", tr("Anuncio de nombre (Bonjour)."), Risk.INFO),
        PortInfo(5432, "PostgreSQL", tr("Base de datos."), Risk.MEDIUM),
        PortInfo(5555, tr("Depuración Android (ADB)"), tr("Permite controlar un Android o TV Box por completo desde otro equipo."), Risk.HIGH,
            tr("Desactiva la \"depuración por red\" en las opciones de desarrollador del dispositivo.")),
        PortInfo(5900, "VNC", tr("Control remoto de pantalla."), Risk.HIGH,
            tr("Úsalo solo con contraseña fuerte. Si no lo usas, desactívalo.")),
        PortInfo(6379, tr("Redis"), tr("Base de datos en memoria."), Risk.HIGH,
            tr("Redis sin contraseña permite tomar control del servidor.")),
        PortInfo(7000, "AirPlay", tr("Recibe contenido de un iPhone o Mac (Apple TV, Mac, parlantes)."), Risk.INFO),
        PortInfo(8000, tr("Web alternativa"), tr("Panel web o aplicación (cámaras, DVR, servidores)."), Risk.LOW),
        PortInfo(8008, tr("Chromecast / Google"), tr("Dispositivo Google Cast (Chromecast, Google TV, Nest)."), Risk.INFO),
        PortInfo(8009, tr("Chromecast"), tr("Control de transmisión de Google Cast."), Risk.INFO),
        PortInfo(8080, tr("Web alternativa"), tr("Panel web o proxy. Frecuente en routers, cámaras y servidores."), Risk.LOW,
            tr("Si es un panel de administración, cambia la contraseña de fábrica.")),
        PortInfo(8443, tr("Web segura alternativa"), tr("Panel web cifrado."), Risk.INFO),
        PortInfo(8883, tr("MQTT seguro"), tr("Mensajería cifrada de domótica."), Risk.INFO),
        PortInfo(9000, tr("Web / servicios"), tr("Panel web u otro servicio."), Risk.LOW),
        PortInfo(9100, tr("Impresión directa"), tr("Impresora de red que acepta trabajos de impresión."), Risk.INFO),
        PortInfo(32400, tr("Plex"), tr("Servidor multimedia Plex."), Risk.LOW),
        PortInfo(37777, tr("DVR Dahua"), tr("Grabador de cámaras de seguridad."), Risk.MEDIUM,
            tr("Cambia la contraseña de fábrica del grabador.")),
        PortInfo(49152, "UPnP", tr("Servicio de anuncio automático (TV, consolas, routers)."), Risk.LOW),
        PortInfo(62078, tr("Sincronización de iPhone/iPad"), tr("Puerto que abre un iPhone o iPad para sincronizarse."), Risk.INFO),
    ).associateBy { it.port }

    /** Puertos que revisamos en el "análisis de puertos" de un dispositivo. */
    val COMMON_PORTS: List<Int> = (ports.keys + listOf(
        8081, 8888, 10000, 1723, 4444, 4567, 5060, 5985, 6000, 6667, 7547, 8001, 8002, 8060, 8200,
        8291, 9080, 9443, 10001, 11211, 27017, 50000,
    )).filter { it != 67 && it != 161 && it != 1900 && it != 5353 } // esos son UDP
        .distinct().sorted()

    /**
     * Análisis extendido (Premium, RF-28): los 1024 puertos "conocidos" más los
     * puertos altos que más usan routers, cámaras, NAS, servidores y juegos.
     */
    val EXTENDED_PORTS: List<Int> = ((1..1024).toList() + COMMON_PORTS + listOf(
        1194, 1521, 1701, 2000, 2049, 2082, 2083, 2086, 2087, 2095, 2096, 2181, 2222, 2375, 2376,
        2483, 2484, 3000, 3050, 3128, 3260, 3268, 3306, 3389, 3478, 3690, 4000, 4040, 4369, 4443,
        4711, 4712, 4993, 5001, 5005, 5061, 5222, 5269, 5357, 5432, 5555, 5601, 5631, 5666, 5672,
        5800, 5900, 5901, 5938, 5984, 5986, 6001, 6379, 6443, 6514, 6566, 6646, 6881, 7001, 7070,
        7100, 7443, 7777, 8000, 8008, 8009, 8010, 8069, 8082, 8083, 8086, 8088, 8089, 8090, 8096,
        8123, 8181, 8333, 8384, 8443, 8444, 8500, 8554, 8800, 8834, 8843, 8880, 8883, 8889, 9000,
        9001, 9042, 9050, 9090, 9091, 9100, 9200, 9300, 9500, 9999, 10243, 12345, 15672, 16992,
        16993, 17500, 18080, 19999, 20000, 25565, 28017, 32400, 32469, 34567, 37777, 49153, 49154,
        51413, 52869, 55442, 55443, 62078,
    )).filter { it != 67 && it != 161 && it != 1900 && it != 5353 }.distinct().sorted()

    fun info(port: Int): PortInfo = ports[port] ?: PortInfo(
        port,
        tr("Puerto %1\$s", port),
        tr("Hay un servicio escuchando en este puerto. No está en nuestra lista de servicios comunes."),
        Risk.LOW,
        tr("Si no reconoces este equipo, revisa qué programa usa este puerto."),
    )
}
