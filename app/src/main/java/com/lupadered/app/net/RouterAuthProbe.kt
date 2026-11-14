package com.lupadered.app.net

import com.lupadered.app.knowledge.RouterKnowledge
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Prueba activa de credenciales de fábrica contra el panel web del router
 * (Premium, RF-47). Solo se ejecuta con el consentimiento explícito del
 * usuario y únicamente contra el gateway de la red actual. No guarda ni
 * reporta la credencial encontrada, solo si alguna funcionó.
 */
object RouterAuthProbe {

    /** @return true si alguna credencial común fue aceptada (HTTP 200/401 con Basic Auth exitoso). */
    suspend fun probeDefaultCredentials(ip: String, timeoutMs: Int = 1500): Boolean {
        for (scheme in listOf("http", "https")) {
            for ((user, pass) in RouterKnowledge.COMMON_CREDENTIALS) {
                if (tryLogin(scheme, ip, user, pass, timeoutMs)) return true
            }
        }
        return false
    }

    private fun tryLogin(scheme: String, ip: String, user: String, pass: String, timeoutMs: Int): Boolean {
        return try {
            val url = URL("$scheme://$ip/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                if (scheme == "https" && this is javax.net.ssl.HttpsURLConnection) {
                    // El router usa certificados autofirmados; solo medimos si la credencial
                    // es aceptada, no validamos el certificado.
                    sslSocketFactory = trustAllSocketFactory()
                    hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                }
                val token = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
                setRequestProperty("Authorization", "Basic $token")
                requestMethod = "GET"
            }
            val code = conn.responseCode
            conn.disconnect()
            // 200 con Basic Auth correcto = aceptado. Algunos paneles redirigen (30x) tras login.
            code == HttpURLConnection.HTTP_OK || code in 300..399
        } catch (_: Exception) {
            false
        }
    }

    private fun trustAllSocketFactory(): javax.net.ssl.SSLSocketFactory {
        val trustAll = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
        ctx.init(null, trustAll, java.security.SecureRandom())
        return ctx.socketFactory
    }
}
