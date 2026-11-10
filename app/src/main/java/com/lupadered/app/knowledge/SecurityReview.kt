package com.lupadered.app.knowledge

import com.lupadered.app.data.Device

enum class Severity(val label: String) {
    HIGH("Importante"),
    MEDIUM("Revisar"),
    LOW("Sugerencia"),
}

/** Un hallazgo de la revisión de seguridad, con los pasos para corregirlo. */
data class Finding(
    val severity: Severity,
    val title: String,
    val detail: String,
    val steps: List<String>,
    val deviceId: Long? = null,
)

data class SecurityReport(
    /** 0 a 100: mientras más alto, más segura la red. */
    val score: Int,
    val grade: String,
    val summary: String,
    val findings: List<Finding>,
    /** Equipos en línea cuyos puertos ya revisamos / total en línea. */
    val reviewedDevices: Int,
    val onlineDevices: Int,
)

/**
 * Revisión de seguridad de la red (Premium, RF-29). Usa lo que ya sabemos de
 * cada equipo (puertos abiertos y si es conocido) para dar un puntaje y una
 * lista de cosas por corregir, de la más importante a la menos.
 */
object SecurityReview {

    fun evaluate(devices: List<Device>, onlineIds: Set<Long>): SecurityReport {
        val online = devices.filter { it.id in onlineIds }
        val findings = ArrayList<Finding>()
        var penalty = 0

        for (d in online) {
            for (port in d.openPorts.sorted()) {
                val info = PortKnowledge.info(port)
                when (info.risk) {
                    Risk.HIGH -> {
                        penalty += 20
                        findings += Finding(
                            Severity.HIGH,
                            "${info.service} abierto en ${d.displayName}",
                            "${d.ip}, puerto $port. ${info.whatItMeans}",
                            listOfNotNull(
                                info.advice,
                                "Abre la ficha del equipo y entra a su configuración para desactivar este servicio si no lo usas.",
                                "Cambia la contraseña de fábrica del equipo por una larga y única.",
                                "Si no reconoces el equipo, desconéctalo y cambia la clave de tu WiFi.",
                            ),
                            d.id,
                        )
                    }
                    Risk.MEDIUM -> {
                        penalty += 6
                        findings += Finding(
                            Severity.MEDIUM,
                            "${info.service} en ${d.displayName}",
                            "${d.ip}, puerto $port. ${info.whatItMeans}",
                            listOfNotNull(
                                info.advice,
                                "Si no usas este servicio, desactívalo en la configuración del equipo.",
                            ),
                            d.id,
                        )
                    }
                    Risk.LOW -> if (info.advice != null) {
                        penalty += 1
                        findings += Finding(
                            Severity.LOW,
                            "${info.service} en ${d.displayName}",
                            "${d.ip}, puerto $port. ${info.whatItMeans}",
                            listOf(info.advice),
                            d.id,
                        )
                    }
                    Risk.INFO -> Unit
                }
            }
        }

        val unknown = online.filter { !it.trusted && !it.isSelf }
        if (unknown.isNotEmpty()) {
            penalty += (unknown.size * 5).coerceAtMost(20)
            findings += Finding(
                Severity.MEDIUM,
                if (unknown.size == 1) "1 equipo sin revisar está conectado"
                else "${unknown.size} equipos sin revisar están conectados",
                unknown.take(5).joinToString(", ") { "${it.displayName} (${it.ip})" },
                listOf(
                    "Abre cada equipo y márcalo como conocido si es tuyo.",
                    "Si no lo reconoces, apaga tus aparatos uno por uno hasta ver cuál desaparece.",
                    "Si sigue sin aparecer el dueño, cambia la clave de tu WiFi (usa WPA2 o WPA3).",
                ),
            )
        }

        val candidates = online.filter { !it.isSelf }
        val reviewed = candidates.count { it.portsScannedAt != null }
        val notReviewed = candidates.size - reviewed
        if (notReviewed > 0) {
            findings += Finding(
                Severity.LOW,
                "Faltan revisar los puertos de $notReviewed ${if (notReviewed == 1) "equipo" else "equipos"}",
                "El puntaje es más preciso cuando revisamos los puertos de todos los equipos conectados.",
                listOf("Toca \"Analizar todos los equipos\" arriba."),
            )
        }

        val score = (100 - penalty).coerceIn(0, 100)
        val grade = when {
            score >= 85 -> "Muy buena"
            score >= 70 -> "Buena"
            score >= 50 -> "Mejorable"
            else -> "En riesgo"
        }
        val important = findings.count { it.severity == Severity.HIGH }
        val summary = when {
            online.isEmpty() -> "Escanea tu red para empezar la revisión."
            important > 0 -> "Hay $important ${if (important == 1) "punto importante" else "puntos importantes"} por corregir."
            findings.any { it.severity == Severity.MEDIUM } -> "Tu red está bien, pero hay detalles que conviene revisar."
            else -> "No encontramos problemas en los equipos revisados."
        }
        return SecurityReport(
            score = score,
            grade = grade,
            summary = summary,
            findings = findings.sortedBy { it.severity.ordinal },
            reviewedDevices = reviewed,
            onlineDevices = candidates.size,
        )
    }
}
