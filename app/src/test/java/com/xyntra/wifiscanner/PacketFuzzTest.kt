package com.xyntra.wifiscanner

import com.xyntra.wifiscanner.net.DnsMessage
import com.xyntra.wifiscanner.net.Mdns
import com.xyntra.wifiscanner.net.NetBios
import com.xyntra.wifiscanner.net.RouterHosts
import com.xyntra.wifiscanner.net.Ssdp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueba que los parsers que reciben datos de OTROS equipos de la misma red
 * (NetBIOS, mDNS, SSDP, DNS, el panel TR-064 del router) no revienten ni se
 * cuelguen ante paquetes recortados, inventados o adversariales. Cualquier
 * dispositivo conectado al mismo WiFi puede enviar estos bytes.
 */
class PacketFuzzTest {

    // ---------------- NetBIOS ----------------

    @Test
    fun netbios_bufferMuyCorto() {
        assertNull(NetBios.parseStatusName(ByteArray(10), 10))
        assertNull(NetBios.parseStatusName(ByteArray(56), 56))
        assertNull(NetBios.parseStatusName(ByteArray(0), 0))
        assertNull(NetBios.parseStatusName(ByteArray(57), -1))
    }

    @Test
    fun netbios_countDeclaraMasEntradasQueElBuffer() {
        val buf = ByteArray(57)
        buf[12] = 0 // nombre de registro vacío -> pos avanza a 13, luego +10 = 23
        buf[23] = 100 // dice que hay 100 entradas, pero el buffer solo alcanza para una
        assertNull(NetBios.parseStatusName(buf, 57))
    }

    @Test
    fun netbios_todoCeros() {
        assertNull(NetBios.parseStatusName(ByteArray(100), 100))
    }

    @Test
    fun netbios_punteroDeCompresionEnNombreDeRegistro() {
        val buf = ByteArray(80)
        buf[12] = 0xC0.toByte() // byte con los dos bits altos en 1: se interpreta como puntero
        buf[13] = 12
        // No debe lanzar excepción, solo devolver null o un nombre válido.
        NetBios.parseStatusName(buf, 80)
    }

    // ---------------- DNS / mDNS (comparten formato) ----------------

    @Test
    fun dns_punteroComprimidoApuntaASiMismo() {
        // qdcount=0, ancount=1
        val header = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0)
        // El nombre del registro es un puntero que apunta a sí mismo (offset 12)
        val recordNamePointer = byteArrayOf(0xC0.toByte(), 12)
        val rest = byteArrayOf(0, 1, 0, 1, 0, 0, 0, 0, 0, 0) // type=A, class=IN, ttl=0, rdlength=0
        val buf = header + recordNamePointer + rest
        // readName tiene un tope de 32 saltos: esto no debe colgarse ni lanzar fuera de parse().
        assertTrue(DnsMessage.parse(buf, buf.size).isEmpty())
    }

    @Test
    fun dns_contadoresMuchoMayoresQueElPaquete() {
        // Dice tener 65535 preguntas en un paquete de 12 bytes (solo el encabezado).
        val buf = byteArrayOf(0, 0, 0, 0, 0xFF.toByte(), 0xFF.toByte(), 0, 0, 0, 0, 0, 0)
        assertTrue(DnsMessage.parse(buf, buf.size).isEmpty())
    }

    @Test
    fun dns_paquetesTruncadosNoRompen() {
        assertTrue(DnsMessage.parse(ByteArray(0)).isEmpty())
        assertTrue(DnsMessage.parse(ByteArray(11)).isEmpty())
        DnsMessage.parse(ByteArray(64) { 0xC0.toByte() })
    }

    // ---------------- mDNS: decodificación / nombres ----------------

    @Test
    fun quickShare_entradaInvalida() {
        assertNull(Mdns.decodeQuickShare(""))
        assertNull(Mdns.decodeQuickShare("***no-es-base64***"))
    }

    @Test
    fun quickShare_lenReclamaMasBytesQueLosDisponibles() {
        val data = ByteArray(20)
        data[17] = 10 // dice que el nombre mide 10 bytes, pero solo hay 2 disponibles (20-18)
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data)
        val info = Mdns.decodeQuickShare(encoded)
        assertTrue(info != null)
        assertNull(info!!.name)
    }

    @Test
    fun quickShare_nombreValidoSeRecupera() {
        val nameBytes = "Juan".toByteArray(Charsets.UTF_8)
        val data = ByteArray(18 + nameBytes.size)
        data[17] = nameBytes.size.toByte()
        nameBytes.copyInto(data, 18)
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data)
        val info = Mdns.decodeQuickShare(encoded)
        assertEquals("Juan", info?.name)
    }

    @Test
    fun mdns_nombresCasosRaros() {
        assertNull(Mdns.instanceOf("sin-separador-de-servicio"))
        assertNull(Mdns.instanceOf(""))
        assertEquals("Equipo Raro", Mdns.instanceOf("Equipo\\032Raro._airplay._tcp.local"))
        assertNull(Mdns.serviceTypeOf("texto sin servicio"))
        assertEquals("", Mdns.cleanHost(""))
        assertEquals("equipo", Mdns.cleanHost("equipo.LOCAL"))
    }

    // ---------------- SSDP ----------------

    @Test
    fun ssdp_encabezadosCasosLimite() {
        assertTrue(Ssdp.parseHeaders("").isEmpty())
        assertTrue(Ssdp.parseHeaders("SIN-DOS-PUNTOS-EN-NINGUN-LADO").isEmpty())
        val map = Ssdp.parseHeaders("HTTP/1.1 200 OK\r\nSERVER: Linux/3.0\r\nServer: Otro\r\n\r\n")
        assertEquals("Otro", map["server"]) // el último valor gana
        val longValue = "a".repeat(200_000)
        val withLongHeader = Ssdp.parseHeaders("HTTP/1.1 200 OK\r\nX: $longValue\r\n\r\n")
        assertEquals(longValue, withLongHeader["x"])
    }

    @Test
    fun ssdp_descripcionXmlCasosLimite() {
        val vacio = Ssdp.parseDescription("")
        assertNull(vacio.friendlyName)
        val conDato = Ssdp.parseDescription("<friendlyName>Mi Router</friendlyName>")
        assertEquals("Mi Router", conDato.friendlyName)
        // Entidades mal formadas o anidadas no deben lanzar excepción.
        Ssdp.parseDescription("<friendlyName>&amp;amp;&lt;prueba</friendlyName>")
        Ssdp.parseDescription("<FriendlyName>Mayúsculas</FriendlyName>")
    }

    // ---------------- RouterHosts (TR-064) ----------------

    @Test
    fun routerHosts_controlUrlMayusculasYMinusculas() {
        val xml = "<SERVICE><SERVICETYPE>urn:dslforum-org:service:Hosts:1</SERVICETYPE>" +
            "<CONTROLURL>/ctl/Hosts</CONTROLURL></SERVICE>"
        assertEquals("/ctl/Hosts", RouterHosts.controlUrlOf(xml))
    }

    @Test
    fun routerHosts_controlUrlConCaracteresFueraDeRangoEsRechazada() {
        val conEspacio = "<service><serviceType>urn:dslforum-org:service:Hosts:1</serviceType>" +
            "<controlURL>/ctl Hosts</controlURL></service>"
        assertNull(RouterHosts.controlUrlOf(conEspacio))
        val conSaltoDeLinea = "<service><serviceType>urn:dslforum-org:service:Hosts:1</serviceType>" +
            "<controlURL>/ctl\nHosts</controlURL></service>"
        assertNull(RouterHosts.controlUrlOf(conSaltoDeLinea))
    }

    @Test
    fun routerHosts_variosBloquesSoloElCorrectoCoincide() {
        val xml = "<service><serviceType>otro:servicio</serviceType><controlURL>/no</controlURL></service>" +
            "<service><serviceType>urn:dslforum-org:service:Hosts:1</serviceType><controlURL>/si</controlURL></service>"
        assertEquals("/si", RouterHosts.controlUrlOf(xml))
    }
}
