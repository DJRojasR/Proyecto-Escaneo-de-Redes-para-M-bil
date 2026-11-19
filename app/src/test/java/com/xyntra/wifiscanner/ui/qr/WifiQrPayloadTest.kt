package com.xyntra.wifiscanner.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El SSID/clave que escribe el usuario puede contener los caracteres que el
 * formato WIFI: usa como separadores (`;` `:` `,` `"` `\`). Si [WifiQrPayload.escape]
 * no los protege bien, el QR generado se lee mal en otro teléfono (SSID/clave
 * cortados o mezclados). Estas pruebas reconstruyen los campos del payload
 * igual que lo haría un lector de QR real, para comprobar que el dato original
 * se recupera sin cambios.
 */
class WifiQrPayloadTest {

    /** Separa por ';' no escapado y deshace el escape, como haría un lector real. */
    private fun splitFields(payload: String): List<String> {
        val fields = mutableListOf<StringBuilder>()
        var current = StringBuilder()
        var i = 0
        while (i < payload.length) {
            val c = payload[i]
            when {
                c == '\\' && i + 1 < payload.length -> {
                    current.append(payload[i + 1])
                    i += 2
                }
                c == ';' -> {
                    fields += current
                    current = StringBuilder()
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        fields += current
        return fields.map { it.toString() }
    }

    @Test
    fun caracteresEspeciales_individualesSobrevivenElRoundTrip() {
        val casos = listOf(
            "Mi;Red" to "clave:normal",
            "Red\"Casa" to "pa\\ss,word",
            "Red,Con,Comas" to "clave;con;puntoycoma",
            "Red:Dos:Puntos" to "simple123",
            "Red;Con\"Todo:Junto,Aqui" to "cl\\a:v;e\",con,todo",
        )
        for ((ssid, password) in casos) {
            val payload = WifiQrPayload.build(ssid, password, QrSecurity.WPA, hidden = false)
            val fields = splitFields(payload)
            val ssidField = fields.first { it.startsWith("S:") }.removePrefix("S:")
            val passField = fields.first { it.startsWith("P:") }.removePrefix("P:")
            assertEquals(ssid, ssidField)
            assertEquals(password, passField)
        }
    }

    @Test
    fun backslashLiteralSeguidoDePuntoYComaSeEscapaCorrectamente() {
        // Entrada con una barra invertida literal seguida de un punto y coma: "a\;b"
        val password = "a\\;b"
        val payload = WifiQrPayload.build("Red", password, QrSecurity.WPA, hidden = false)
        val expectedEscaped = "a" + "\\".repeat(3) + ";b" // \ -> \\, ; -> \; (no deben fundirse en un \; suelto)
        assertTrue(payload.contains("P:$expectedEscaped"))
        val passField = splitFields(payload).first { it.startsWith("P:") }.removePrefix("P:")
        assertEquals(password, passField)
    }

    @Test
    fun redAbierta_noIncluyeCampoDeClave() {
        val payload = WifiQrPayload.build("RedAbierta", "", QrSecurity.OPEN, hidden = false)
        assertTrue(payload.startsWith("WIFI:T:nopass;S:RedAbierta;;"))
        assertFalse(splitFields(payload).any { it.startsWith("P:") })
    }

    @Test
    fun redOculta_incluyeCampoHidden() {
        val payload = WifiQrPayload.build("Red", "clave1234", QrSecurity.WPA, hidden = true)
        assertTrue(payload.contains("H:true;"))
    }

    @Test
    fun ssidYClaveVaciasNoRompen() {
        val payload = WifiQrPayload.build("", "", QrSecurity.WEP, hidden = false)
        assertEquals("WIFI:T:WEP;S:;P:;;", payload)
    }
}
