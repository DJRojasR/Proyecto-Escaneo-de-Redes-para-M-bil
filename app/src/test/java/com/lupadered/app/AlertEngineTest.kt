package com.lupadered.app

import com.lupadered.app.monitor.AlertEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la lógica pura de alertas por dispositivo (RF-49). */
class AlertEngineTest {

    @Test
    fun desconexion_sePasaElUmbral() {
        val now = 1_000_000L
        val lastSeen = now - 20 * 60_000L // 20 minutos atrás
        assertTrue(AlertEngine.offlineExceeded(lastSeen, now, thresholdMin = 15))
        assertFalse(AlertEngine.offlineExceeded(lastSeen, now, thresholdMin = 30))
    }

    @Test
    fun desconexion_justoEnElLimite() {
        val now = 1_000_000L
        val lastSeen = now - 15 * 60_000L
        assertTrue(AlertEngine.offlineExceeded(lastSeen, now, thresholdMin = 15))
    }

    @Test
    fun puertoNuevo_seDetectaPorDiferencia() {
        val previous = setOf(80, 443)
        val current = setOf(80, 443, 23)
        assertEquals(setOf(23), AlertEngine.newPorts(previous, current))
    }

    @Test
    fun puertoNuevo_sinCambios_noHayDiferencia() {
        val ports = setOf(80, 443)
        assertTrue(AlertEngine.newPorts(ports, ports).isEmpty())
    }

    @Test
    fun puertoNuevo_puertoQueDesaparecioNoCuenta() {
        val previous = setOf(80, 443, 23)
        val current = setOf(80, 443)
        assertTrue(AlertEngine.newPorts(previous, current).isEmpty())
    }
}
