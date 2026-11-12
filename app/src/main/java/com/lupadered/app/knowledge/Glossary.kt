package com.lupadered.app.knowledge
import com.lupadered.app.i18n.tr


data class GlossaryEntry(val term: String, val short: String, val detail: String)

/** "¿Qué estoy viendo?": explicaciones sencillas de todo lo que muestra la app. */
object Glossary {

    val entries = listOf(
        GlossaryEntry(
            tr("Dirección IP"),
            tr("El \"número de casa\" de cada equipo dentro de tu red."),
            tr("Cada aparato conectado recibe una dirección como 192.168.1.23 para que los demás sepan a dónde enviarle datos. Dentro de tu casa estas direcciones son privadas: nadie en Internet puede verlas directamente.")
        ),
        GlossaryEntry(
            tr("Router (puerta de enlace)"),
            tr("El aparato que reparte el WiFi y te conecta a Internet."),
            tr("Todo lo que sale de tu casa hacia Internet pasa por el router. Normalmente su IP termina en .1 (por ejemplo 192.168.1.1) y desde ella puedes entrar a su configuración.")
        ),
        GlossaryEntry(
            tr("Rango de red (/24)"),
            tr("Cuántas direcciones IP caben en tu red."),
            tr("\"192.168.1.0/24\" significa que tu red usa las direcciones de 192.168.1.1 a 192.168.1.254: 254 lugares posibles para equipos. La app revisa todas esas direcciones.")
        ),
        GlossaryEntry(
            tr("\"Sin respuesta\" no significa \"libre\""),
            tr("Una IP que no responde puede estar ocupada por un equipo apagado o dormido."),
            tr("Cuando una dirección no responde puede ser que esté libre, que el equipo esté apagado, en reposo o que tenga un firewall que lo oculta. Por eso la app recuerda los equipos que ya vio y te muestra \"visto hace…\" en lugar de borrarlos.")
        ),
        GlossaryEntry(
            tr("Latencia (ms)"),
            tr("Lo que tarda un equipo en responder, en milésimas de segundo."),
            tr("Dentro de casa lo normal es menos de 10–20 ms. Un celular en reposo puede tardar más de 100 ms en despertar y responder; no siempre es un problema.")
        ),
        GlossaryEntry(
            tr("Puerto"),
            tr("Una \"puerta\" numerada por la que un equipo ofrece un servicio."),
            tr("Por ejemplo, el puerto 80 suele ser una página web y el 9100 sirve para imprimir. Un puerto abierto no es malo por sí mismo: solo significa que hay un servicio escuchando. La app te explica cada uno y te avisa si alguno merece revisión.")
        ),
        GlossaryEntry(
            tr("mDNS / Bonjour"),
            tr("Una forma en que los equipos anuncian solos su nombre."),
            tr("iPhone, Mac, Chromecast, impresoras y parlantes inteligentes dicen \"me llamo así y sé hacer esto\". Gracias a eso la app puede mostrarte nombres como \"Sala TV\".")
        ),
        GlossaryEntry(
            tr("UPnP / SSDP"),
            tr("Otro sistema de anuncio, común en televisores y consolas."),
            tr("Permite que los equipos se encuentren entre sí para, por ejemplo, enviar videos al televisor.")
        ),
        GlossaryEntry(
            "NetBIOS",
            tr("El nombre de las computadoras con Windows en la red."),
            tr("Las PCs con Windows y algunos discos de red responden con su nombre, por ejemplo \"LAPTOP-ANA\".")
        ),
        GlossaryEntry(
            tr("¿Por qué no veo la dirección MAC?"),
            tr("Android ya no permite a las apps leer la MAC de otros equipos."),
            tr("Desde Android 10, por privacidad, ninguna app normal puede ver las direcciones MAC de los demás dispositivos. Por eso la app identifica a los equipos por su nombre en la red y por el nombre que tú les pongas.")
        ),
        GlossaryEntry(
            tr("Aislamiento de clientes"),
            tr("Una opción del router que impide que los equipos se vean entre sí."),
            tr("Es frecuente en redes de invitados, hoteles y cafeterías. Si está activa, la app solo podrá ver al router y a tu teléfono. No es una falla de la app.")
        ),
        GlossaryEntry(
            tr("Reserva de IP (DHCP)"),
            tr("Hacer que un equipo tenga siempre la misma IP."),
            tr("En la configuración del router (sección DHCP o \"Reserva de direcciones\") puedes fijar la IP de tus equipos importantes, como la impresora. Así siempre los encontrarás en la misma dirección.")
        ),
    )
}
