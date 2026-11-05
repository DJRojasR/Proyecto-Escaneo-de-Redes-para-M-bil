package com.lupadered.app

import com.lupadered.app.knowledge.DeviceClassifier
import com.lupadered.app.knowledge.DeviceHints
import com.lupadered.app.knowledge.DeviceType
import com.lupadered.app.net.DnsMessage
import com.lupadered.app.net.Ipv4
import com.lupadered.app.net.Mdns
import com.lupadered.app.net.NetworkScanner
import com.lupadered.app.net.NioTcpProber
import com.lupadered.app.net.Subnet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

/** Pruebas del núcleo de red. Ejecuta con: gradlew test */
class NetCoreTest {

    @Test
    fun ipv4_idaYVuelta() {
        assertEquals("192.168.1.10", Ipv4.fromInt(Ipv4.toInt("192.168.1.10")!!))
        assertNull(Ipv4.toInt("256.1.1.1"))
        assertNull(Ipv4.toInt("1.2.3"))
    }

    @Test
    fun subred24_tiene254Hosts() {
        val s = Subnet.of("192.168.1.77", 24)!!
        assertEquals("192.168.1.0/24", s.cidr)
        assertEquals(254, s.hosts().size)
        assertTrue(s.contains("192.168.1.200"))
        assertFalse(s.contains("192.168.2.1"))
    }

    @Test
    fun redGrande_seReduceA24() {
        val (s, reduced) = Subnet.scanRangeFor("10.0.5.9", 16)
        assertEquals("10.0.5.0/24", s.cidr)
        assertTrue(reduced)
    }

    @Test
    fun soloIpsPrivadas() {
        assertTrue(Ipv4.isPrivate("192.168.0.1"))
        assertTrue(Ipv4.isPrivate("172.20.0.1"))
        assertFalse(Ipv4.isPrivate("8.8.8.8"))
        assertFalse(Ipv4.isPrivate("172.32.0.1"))
    }

    @Test
    fun dns_preguntaSeLeeIgual() {
        val q = DnsMessage.buildQuery(1, listOf(DnsMessage.Question("_googlecast._tcp.local", DnsMessage.TYPE_PTR)))
        assertEquals("_googlecast._tcp.local", DnsMessage.readName(q, q.size, 12).first)
    }

    @Test
    fun dns_paqueteBasuraNoRompe() {
        DnsMessage.parse(ByteArray(64) { 0xC0.toByte() })
        DnsMessage.parse(ByteArray(5))
    }

    @Test
    fun mdns_nombres() {
        assertEquals("Sala TV", Mdns.instanceOf("Sala TV._googlecast._tcp.local"))
        assertEquals("_googlecast._tcp", Mdns.serviceTypeOf("Sala TV._googlecast._tcp.local"))
        assertEquals("iPhone-de-Ana", Mdns.cleanHost("iPhone-de-Ana.local."))
        assertEquals("192.168.1.5", NetworkScanner.ipFromReverse("5.1.168.192.in-addr.arpa."))
    }

    @Test
    fun clasificador() {
        assertEquals(DeviceType.TV, DeviceClassifier.classify(DeviceHints(listOf("Sala TV"), setOf("_googlecast._tcp"), emptySet())))
        assertEquals(DeviceType.PHONE, DeviceClassifier.classify(DeviceHints(listOf("iPhone-de-Ana"), emptySet(), emptySet())))
        assertEquals(DeviceType.PRINTER, DeviceClassifier.classify(DeviceHints(emptyList(), emptySet(), setOf(9100))))
        assertEquals(DeviceType.COMPUTER, DeviceClassifier.classify(DeviceHints(listOf("LAPTOP-ANA"), emptySet(), setOf(445))))
        // "tv" no debe coincidir dentro de otras palabras
        assertEquals(DeviceType.UNKNOWN, DeviceClassifier.classify(DeviceHints(listOf("abctvx"), emptySet(), emptySet())))
        assertEquals(DeviceType.ROUTER, DeviceClassifier.classify(DeviceHints(emptyList(), emptySet(), emptySet(), isGateway = true)))
    }

    @Test
    fun tcp_abiertoYRechazado() {
        val server = ServerSocket(0)
        val closed = ServerSocket(0).let { val p = it.localPort; it.close(); p }
        val results = HashMap<Int, NioTcpProber.State>()
        NioTcpProber(16).run(
            listOf(NioTcpProber.Target("127.0.0.1", server.localPort), NioTcpProber.Target("127.0.0.1", closed)),
            1000, { false },
        ) { results[it.target.port] = it.state }
        server.close()
        assertEquals(NioTcpProber.State.OPEN, results[server.localPort])
        // Un puerto cerrado prueba que el equipo existe
        assertEquals(NioTcpProber.State.REFUSED, results[closed])
    }
}
