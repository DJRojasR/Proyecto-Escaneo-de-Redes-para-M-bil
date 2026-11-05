package com.lupadered.app.knowledge

enum class Risk(val label: String) {
    INFO("Normal"),
    LOW("Riesgo bajo"),
    MEDIUM("Revisar"),
    HIGH("Riesgo alto"),
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
        PortInfo(20, "FTP (datos)", "Transferencia de archivos antigua.", Risk.MEDIUM,
            "FTP envía contraseñas sin cifrar. Si no lo usas, desactívalo."),
        PortInfo(21, "FTP", "Servidor para compartir archivos con un método antiguo.", Risk.MEDIUM,
            "FTP envía contraseñas sin cifrar. Si no lo usas, desactívalo."),
        PortInfo(22, "SSH", "Permite controlar el equipo a distancia por línea de comandos (común en Linux, NAS y routers).", Risk.LOW,
            "Está bien si lo usas tú. Asegúrate de tener una contraseña fuerte."),
        PortInfo(23, "Telnet", "Control remoto muy antiguo y sin cifrar. Lo usan algunos routers y cámaras baratas.", Risk.HIGH,
            "Cualquiera en tu red puede ver lo que se escribe, incluidas contraseñas. Desactívalo si puedes."),
        PortInfo(25, "SMTP", "Servidor de envío de correos.", Risk.MEDIUM,
            "Es raro en una casa. Si no sabes por qué está, revisa el equipo."),
        PortInfo(53, "DNS", "Traduce nombres (como google.com) a direcciones. Lo normal es que lo tenga tu router.", Risk.INFO),
        PortInfo(67, "DHCP", "Reparte las direcciones IP a los equipos que se conectan. Normal en el router.", Risk.INFO),
        PortInfo(80, "Web (HTTP)", "Tiene una página web, normalmente un panel de configuración (routers, impresoras, cámaras).", Risk.LOW,
            "Si es un panel de administración, cambia la contraseña de fábrica."),
        PortInfo(81, "Web alternativa", "Otra página web; frecuente en cámaras IP.", Risk.LOW,
            "Cambia la contraseña de fábrica del equipo."),
        PortInfo(88, "Kerberos / Web", "Autenticación de redes de empresa o un panel web.", Risk.INFO),
        PortInfo(110, "POP3", "Servidor para recibir correos.", Risk.MEDIUM),
        PortInfo(111, "RPC", "Servicio de Linux/NAS para compartir carpetas (NFS).", Risk.MEDIUM,
            "Normal en un NAS. En otros equipos, revisa si lo necesitas."),
        PortInfo(135, "Windows RPC", "Servicio interno de Windows para comunicarse con otras PCs.", Risk.MEDIUM,
            "Normal en PCs con Windows en una red privada. No debe estar abierto a Internet."),
        PortInfo(139, "Compartir archivos (NetBIOS)", "Carpetas o impresoras compartidas de Windows o Samba.", Risk.MEDIUM,
            "Comparte solo las carpetas que necesites y con contraseña."),
        PortInfo(143, "IMAP", "Servidor de correo.", Risk.MEDIUM),
        PortInfo(161, "SNMP", "Permite consultar el estado del equipo (routers, impresoras).", Risk.MEDIUM),
        PortInfo(443, "Web segura (HTTPS)", "Página web cifrada, normalmente el panel de configuración del equipo.", Risk.INFO),
        PortInfo(445, "Compartir archivos (SMB)", "Carpetas compartidas de Windows, Mac o un NAS.", Risk.MEDIUM,
            "Comparte solo lo necesario y con contraseña. Nunca debe estar abierto a Internet."),
        PortInfo(515, "Impresión (LPD)", "Recibe trabajos de impresión.", Risk.INFO),
        PortInfo(548, "AFP", "Compartir archivos de Apple (Mac antiguos, Time Capsule, NAS).", Risk.LOW),
        PortInfo(554, "Video (RTSP)", "Transmite video en vivo. Casi siempre es una cámara de seguridad o un grabador (DVR/NVR).", Risk.MEDIUM,
            "Si es una cámara, cambia la contraseña de fábrica para que nadie pueda ver el video."),
        PortInfo(631, "Impresión (IPP)", "Impresora o servidor de impresión.", Risk.INFO),
        PortInfo(853, "DNS seguro", "Consultas DNS cifradas.", Risk.INFO),
        PortInfo(1080, "Proxy SOCKS", "Reenvía conexiones de otros equipos.", Risk.HIGH,
            "Un proxy desconocido puede usarse para ocultar tráfico. Revisa qué equipo es."),
        PortInfo(1433, "SQL Server", "Base de datos de Microsoft.", Risk.MEDIUM),
        PortInfo(1883, "MQTT", "Mensajería de dispositivos inteligentes (domótica).", Risk.LOW,
            "Normal si tienes Home Assistant o domótica. Protégelo con contraseña."),
        PortInfo(1900, "UPnP", "Anuncio automático de dispositivos.", Risk.LOW),
        PortInfo(2049, "NFS", "Carpetas compartidas de Linux/NAS.", Risk.MEDIUM),
        PortInfo(3000, "Web de desarrollo", "Una aplicación web (paneles, herramientas de desarrollo).", Risk.LOW),
        PortInfo(3306, "MySQL", "Base de datos.", Risk.MEDIUM,
            "Normal en un servidor. Asegúrate de que tenga contraseña."),
        PortInfo(3389, "Escritorio remoto (RDP)", "Permite controlar la PC con Windows a distancia.", Risk.HIGH,
            "Si no lo usas, desactívalo. Es uno de los puertos más atacados cuando queda expuesto a Internet."),
        PortInfo(5000, "Web / UPnP / NAS", "Panel web (por ejemplo de un NAS Synology) o servicio AirPlay en Mac.", Risk.LOW),
        PortInfo(5001, "Web segura de NAS", "Panel de administración de un NAS.", Risk.LOW),
        PortInfo(5353, "mDNS", "Anuncio de nombre (Bonjour).", Risk.INFO),
        PortInfo(5432, "PostgreSQL", "Base de datos.", Risk.MEDIUM),
        PortInfo(5555, "Depuración Android (ADB)", "Permite controlar un Android o TV Box por completo desde otro equipo.", Risk.HIGH,
            "Desactiva la \"depuración por red\" en las opciones de desarrollador del dispositivo."),
        PortInfo(5900, "VNC", "Control remoto de pantalla.", Risk.HIGH,
            "Úsalo solo con contraseña fuerte. Si no lo usas, desactívalo."),
        PortInfo(6379, "Redis", "Base de datos en memoria.", Risk.HIGH,
            "Redis sin contraseña permite tomar control del servidor."),
        PortInfo(7000, "AirPlay", "Recibe contenido de un iPhone o Mac (Apple TV, Mac, parlantes).", Risk.INFO),
        PortInfo(8000, "Web alternativa", "Panel web o aplicación (cámaras, DVR, servidores).", Risk.LOW),
        PortInfo(8008, "Chromecast / Google", "Dispositivo Google Cast (Chromecast, Google TV, Nest).", Risk.INFO),
        PortInfo(8009, "Chromecast", "Control de transmisión de Google Cast.", Risk.INFO),
        PortInfo(8080, "Web alternativa", "Panel web o proxy. Frecuente en routers, cámaras y servidores.", Risk.LOW,
            "Si es un panel de administración, cambia la contraseña de fábrica."),
        PortInfo(8443, "Web segura alternativa", "Panel web cifrado.", Risk.INFO),
        PortInfo(8883, "MQTT seguro", "Mensajería cifrada de domótica.", Risk.INFO),
        PortInfo(9000, "Web / servicios", "Panel web u otro servicio.", Risk.LOW),
        PortInfo(9100, "Impresión directa", "Impresora de red que acepta trabajos de impresión.", Risk.INFO),
        PortInfo(32400, "Plex", "Servidor multimedia Plex.", Risk.LOW),
        PortInfo(37777, "DVR Dahua", "Grabador de cámaras de seguridad.", Risk.MEDIUM,
            "Cambia la contraseña de fábrica del grabador."),
        PortInfo(49152, "UPnP", "Servicio de anuncio automático (TV, consolas, routers).", Risk.LOW),
        PortInfo(62078, "Sincronización de iPhone/iPad", "Puerto que abre un iPhone o iPad para sincronizarse.", Risk.INFO),
    ).associateBy { it.port }

    /** Puertos que revisamos en el "análisis de puertos" de un dispositivo. */
    val COMMON_PORTS: List<Int> = (ports.keys + listOf(
        8081, 8888, 10000, 1723, 4444, 4567, 5060, 5985, 6000, 6667, 7547, 8001, 8002, 8060, 8200,
        8291, 9080, 9443, 10001, 11211, 27017, 50000,
    )).filter { it != 67 && it != 161 && it != 1900 && it != 5353 } // esos son UDP
        .distinct().sorted()

    fun info(port: Int): PortInfo = ports[port] ?: PortInfo(
        port,
        "Puerto $port",
        "Hay un servicio escuchando en este puerto. No está en nuestra lista de servicios comunes.",
        Risk.LOW,
        "Si no reconoces este equipo, revisa qué programa usa este puerto.",
    )
}
