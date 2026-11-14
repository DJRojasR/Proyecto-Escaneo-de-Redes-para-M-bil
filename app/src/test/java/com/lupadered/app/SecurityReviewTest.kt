package com.lupadered.app

import com.lupadered.app.data.Device
import com.lupadered.app.knowledge.CameraKnowledge
import com.lupadered.app.knowledge.DeviceType
import com.lupadered.app.knowledge.SecurityReview
import com.lupadered.app.net.DiscoveryMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la revisión de seguridad: router (RF-47) y cámaras (RF-48). */
class SecurityReviewTest {

    private fun device(
        id: Long,
        ip: String,
        openPorts: Set<Int> = emptySet(),
        isGateway: Boolean = false,
        isSelf: Boolean = false,
        vendor: String? = null,
        model: String? = null,
        server: String? = null,
        trusted: Boolean = true,
        autoType: DeviceType = DeviceType.UNKNOWN,
    ) = Device(
        id = id,
        networkKey = "net",
        ip = ip,
        nameKey = "key$id",
        alias = null,
        userType = null,
        autoType = autoType,
        name = null,
        vendor = vendor,
        model = model,
        server = server,
        services = emptySet(),
        openPorts = openPorts,
        methods = setOf(DiscoveryMethod.TCP),
        portsScannedAt = System.currentTimeMillis(),
        firstSeen = System.currentTimeMillis(),
        lastSeen = System.currentTimeMillis(),
        lastScanId = 1,
        latencyMs = 5,
        trusted = trusted,
        isGateway = isGateway,
        isSelf = isSelf,
    )

    @Test
    fun router_telnetYUpnp_generanHallazgosAltos() {
        val gw = device(1, "192.168.1.1", openPorts = setOf(23, 80, 1900, 49152), isGateway = true)
        val report = SecurityReview.evaluate(listOf(gw), setOf(gw.id))
        assertTrue(report.findings.any { it.id == "router.telnet" })
        assertTrue(report.findings.any { it.id == "router.http" })
        assertTrue(report.findings.any { it.id == "router.upnp" })
        assertTrue(report.score < 70)
    }

    @Test
    fun router_marcaConocida_sugiereRevisarClave_sinProbarNada() {
        val gw = device(1, "192.168.1.1", openPorts = setOf(443), isGateway = true, vendor = "TP-Link")
        val report = SecurityReview.evaluate(listOf(gw), setOf(gw.id), defaultPasswordFound = null)
        assertTrue(report.findings.any { it.id == "router.brand_defaults" })
    }

    @Test
    fun router_contrasenaDeFabricaEncontrada_esHallazgoAlto() {
        val gw = device(1, "192.168.1.1", openPorts = setOf(80), isGateway = true)
        val report = SecurityReview.evaluate(listOf(gw), setOf(gw.id), defaultPasswordFound = true)
        val f = report.findings.first { it.id == "router.default_password" }
        assertEquals(com.lupadered.app.knowledge.Severity.HIGH, f.severity)
    }

    @Test
    fun camara_porPuertoRtsp_esDetectada() {
        val cam = device(2, "192.168.1.50", openPorts = setOf(554))
        assertTrue(SecurityReview.isCamera(cam))
        val report = SecurityReview.evaluate(listOf(cam), setOf(cam.id))
        assertTrue(report.findings.any { it.id == "camera.2" })
    }

    @Test
    fun camara_porFabricante_esDetectada() {
        val cam = device(3, "192.168.1.51", vendor = "Hikvision")
        assertTrue(SecurityReview.isCamera(cam))
    }

    @Test
    fun routerYSelf_nuncaSonCamara() {
        val gw = device(1, "192.168.1.1", openPorts = setOf(554), isGateway = true)
        val self = device(4, "192.168.1.5", openPorts = setOf(554), isSelf = true)
        assertFalse(SecurityReview.isCamera(gw))
        assertFalse(SecurityReview.isCamera(self))
    }

    @Test
    fun cameraKnowledge_sinIndicios_devuelveNull() {
        assertEquals(null, CameraKnowledge.reason(setOf(80, 443), "Mi laptop", null, null, null, null))
    }
}
