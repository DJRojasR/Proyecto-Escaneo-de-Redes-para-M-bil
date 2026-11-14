package com.lupadered.app.knowledge

import com.lupadered.app.i18n.tr
import com.lupadered.app.data.Device

enum class Severity(val label: String) {
    HIGH(tr("Importante")),
    MEDIUM(tr("Revisar")),
    LOW(tr("Sugerencia")),
}

/** Un hallazgo de la revisión de seguridad, con los pasos para corregirlo. */
data class Finding(
    val severity: Severity,
    val title: String,
    val detail: String,
    val steps: List<String>,
    val deviceId: Long? = null,
    /** Identificador estable del tipo de hallazgo (p. ej. "router.telnet"); vacío = genérico por puerto. */
    val id: String = "",
    val category: FindingCategory = FindingCategory.PORT,
)

enum class FindingCategory { PORT, DEVICE, ROUTER, CAMERA }

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

    /**
     * @param defaultPasswordFound resultado de la prueba activa de credenciales del
     * router: true = el router acepta una clave de fábrica, false = no, null = no se probó.
     */
    fun evaluate(
        devices: List<Device>,
        onlineIds: Set<Long>,
        defaultPasswordFound: Boolean? = null,
    ): SecurityReport {
        val online = devices.filter { it.id in onlineIds }
        val findings = ArrayList<Finding>()
        var penalty = 0

        // Cámaras y router tienen sus propios hallazgos: no repetimos los genéricos por puerto.
        val cameras = online.filter { isCamera(it) }
        val cameraIds = cameras.map { it.id }.toSet()
        val gatewayRisky = setOf(21, 23, 80, 8080, 7547)

        for (d in online) {
            for (port in d.openPorts.sorted()) {
                if (d.id in cameraIds && port in CameraKnowledge.STRONG_PORTS) continue
                if (d.isGateway && port in gatewayRisky) continue
                val info = PortKnowledge.info(port)
                when (info.risk) {
                    Risk.HIGH -> {
                        penalty += 20
                        findings += Finding(
                            Severity.HIGH,
                            tr("%1\$s abierto en %2\$s", info.service, d.displayName),
                            tr("%1\$s, puerto %2\$s. %3\$s", d.ip, port, info.whatItMeans),
                            listOfNotNull(
                                info.advice,
                                tr("Abre la ficha del equipo y entra a su configuración para desactivar este servicio si no lo usas."),
                                tr("Cambia la contraseña de fábrica del equipo por una larga y única."),
                                tr("Si no reconoces el equipo, desconéctalo y cambia la clave de tu WiFi."),
                            ),
                            d.id,
                        )
                    }
                    Risk.MEDIUM -> {
                        penalty += 6
                        findings += Finding(
                            Severity.MEDIUM,
                            tr("%1\$s en %2\$s", info.service, d.displayName),
                            tr("%1\$s, puerto %2\$s. %3\$s", d.ip, port, info.whatItMeans),
                            listOfNotNull(
                                info.advice,
                                tr("Si no usas este servicio, desactívalo en la configuración del equipo."),
                            ),
                            d.id,
                        )
                    }
                    Risk.LOW -> if (info.advice != null) {
                        penalty += 1
                        findings += Finding(
                            Severity.LOW,
                            tr("%1\$s en %2\$s", info.service, d.displayName),
                            tr("%1\$s, puerto %2\$s. %3\$s", d.ip, port, info.whatItMeans),
                            listOf(info.advice),
                            d.id,
                        )
                    }
                    Risk.INFO -> Unit
                }
            }
        }

        for (cam in cameras) {
            penalty += 6
            val why = CameraKnowledge.reason(cam.openPorts, cam.name, cam.vendor, cam.model, cam.server, cam.alias)
            findings += Finding(
                Severity.MEDIUM,
                tr("Posible cámara de vigilancia: %1\$s", cam.displayName),
                tr("%1\$s parece una cámara o grabador (indicio: %2\$s). Verifica que sea tuya.", cam.ip, why ?: tr("tipo de equipo")),
                listOf(
                    tr("Si la instalaste tú, cambia su contraseña de fábrica y actualiza su firmware."),
                    tr("Si no la reconoces, revisa el lugar donde está conectada y desconéctala."),
                    tr("Márcala como conocida en su ficha cuando la hayas verificado."),
                ),
                cam.id,
                "camera.${cam.id}",
                FindingCategory.CAMERA,
            )
        }

        penalty += routerFindings(online.firstOrNull { it.isGateway }, defaultPasswordFound, findings)

        val unknown = online.filter { !it.trusted && !it.isSelf }
        if (unknown.isNotEmpty()) {
            penalty += (unknown.size * 5).coerceAtMost(20)
            findings += Finding(
                Severity.MEDIUM,
                if (unknown.size == 1) tr("1 equipo sin revisar está conectado")
                else tr("%1\$s equipos sin revisar están conectados", unknown.size),
                unknown.take(5).joinToString(", ") { "${it.displayName} (${it.ip})" },
                listOf(
                    tr("Abre cada equipo y márcalo como conocido si es tuyo."),
                    tr("Si no lo reconoces, apaga tus aparatos uno por uno hasta ver cuál desaparece."),
                    tr("Si sigue sin aparecer el dueño, cambia la clave de tu WiFi (usa WPA2 o WPA3)."),
                ),
            )
        }

        val candidates = online.filter { !it.isSelf }
        val reviewed = candidates.count { it.portsScannedAt != null }
        val notReviewed = candidates.size - reviewed
        if (notReviewed > 0) {
            findings += Finding(
                Severity.LOW,
                tr("Faltan revisar los puertos de %1\$s %2\$s", notReviewed, if (notReviewed == 1) tr("equipo") else tr("equipos")),
                tr("El puntaje es más preciso cuando revisamos los puertos de todos los equipos conectados."),
                listOf(tr("Toca \"Iniciar revisión completa\" arriba.")),
            )
        }

        val score = (100 - penalty).coerceIn(0, 100)
        val grade = when {
            score >= 85 -> tr("Muy buena")
            score >= 70 -> tr("Buena")
            score >= 50 -> tr("Mejorable")
            else -> tr("En riesgo")
        }
        val important = findings.count { it.severity == Severity.HIGH }
        val summary = when {
            online.isEmpty() -> tr("Escanea tu red para empezar la revisión.")
            important > 0 -> tr("Hay %1\$s %2\$s por corregir.", important, if (important == 1) tr("punto importante") else tr("puntos importantes"))
            findings.any { it.severity == Severity.MEDIUM } -> tr("Tu red está bien, pero hay detalles que conviene revisar.")
            else -> tr("No encontramos problemas en los equipos revisados.")
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

    /** ¿El equipo parece una cámara/grabador? (no el router ni este teléfono) */
    fun isCamera(d: Device): Boolean {
        if (d.isGateway || d.isSelf) return false
        if (d.type == DeviceType.CAMERA) return true
        return CameraKnowledge.looksLikeCamera(d.openPorts, d.name, d.vendor, d.model, d.server, d.alias)
    }

    /** Añade los hallazgos del router y devuelve los puntos a restar. */
    private fun routerFindings(gw: Device?, defaultPasswordFound: Boolean?, out: MutableList<Finding>): Int {
        if (gw == null) return 0
        var penalty = 0
        val name = gw.displayName
        val cat = FindingCategory.ROUTER

        if (23 in gw.openPorts) {
            penalty += 20
            out += Finding(
                Severity.HIGH, tr("Telnet abierto en tu router"),
                tr("%1\$s acepta conexiones Telnet (puerto 23): control remoto sin cifrar.", gw.ip),
                listOf(
                    tr("Entra al panel de tu router y desactiva Telnet (suele estar en Administración o Acceso remoto)."),
                    tr("Si no puedes desactivarlo, pide a tu operador que lo haga o cambia de router."),
                    tr("Cambia la contraseña de administración por una larga y única."),
                ),
                gw.id, "router.telnet", cat,
            )
        }
        if (21 in gw.openPorts) {
            penalty += 6
            out += Finding(
                Severity.MEDIUM, tr("FTP abierto en tu router"),
                tr("%1\$s ofrece FTP (puerto 21), que envía contraseñas sin cifrar.", gw.ip),
                listOf(tr("Desactiva el servidor FTP del router si no compartes un disco USB.")),
                gw.id, "router.ftp", cat,
            )
        }
        val http = 80 in gw.openPorts || 8080 in gw.openPorts
        val https = 443 in gw.openPorts || 8443 in gw.openPorts
        if (http && !https) {
            penalty += 6
            out += Finding(
                Severity.MEDIUM, tr("Panel del router sin cifrado (HTTP)"),
                tr("El panel de %1\$s solo responde por HTTP: la clave de administración viaja sin cifrar por tu WiFi.", gw.ip),
                listOf(
                    tr("Activa HTTPS en el panel del router si tiene esa opción."),
                    tr("Administra el router solo desde equipos de confianza."),
                    tr("Usa una clave de WiFi WPA2/WPA3 fuerte para que nadie extraño entre a tu red."),
                ),
                gw.id, "router.http", cat,
            )
        }
        if (gw.openPorts.any { it in RouterKnowledge.UPNP_PORTS }) {
            penalty += 6
            out += Finding(
                Severity.MEDIUM, tr("UPnP activo en tu router"),
                tr("%1\$s anuncia UPnP: cualquier programa de tu red puede abrir puertos hacia Internet sin avisarte.", gw.ip),
                listOf(
                    tr("Si no usas juegos en línea o servidores en casa, desactiva UPnP en el router."),
                    tr("Si lo necesitas, revisa la tabla de puertos abiertos del router de vez en cuando."),
                ),
                gw.id, "router.upnp", cat,
            )
        }
        if (RouterKnowledge.TR069_PORT in gw.openPorts) {
            penalty += 6
            out += Finding(
                Severity.MEDIUM, tr("Gestión remota (TR-069) visible"),
                tr("El puerto 7547 de %1\$s está abierto. Es la gestión remota del operador y ha sido muy atacada.", gw.ip),
                listOf(tr("Pregunta a tu operador si puede desactivarla o actualizar el firmware del router.")),
                gw.id, "router.tr069", cat,
            )
        }
        val outdated = RouterKnowledge.outdatedFirmware(gw.server, gw.model, gw.name)
        if (outdated != null) {
            penalty += 6
            out += Finding(
                Severity.MEDIUM, tr("Firmware del router posiblemente desactualizado"),
                tr("%1\$s usa componentes antiguos: %2\$s.", name, outdated),
                listOf(
                    tr("Entra al panel del router y busca \"Actualización de firmware\"."),
                    tr("Si ya no hay actualizaciones para tu modelo, considera cambiarlo."),
                    tr("Anota la versión actual y compárala con la última en la web del fabricante."),
                ),
                gw.id, "router.firmware", cat,
            )
        }
        val brand = RouterKnowledge.factoryDefaultBrand(gw.vendor, gw.model, gw.name, gw.server)
        when (defaultPasswordFound) {
            true -> {
                penalty += 25
                out += Finding(
                    Severity.HIGH, tr("Tu router acepta una contraseña de fábrica"),
                    tr("Se pudo entrar a %1\$s con un usuario y clave comunes. Cualquiera en tu red podría hacerlo.", gw.ip),
                    listOf(
                        tr("Entra ahora al panel del router y cambia la contraseña de administración."),
                        tr("Usa una clave larga y distinta a la de tu WiFi."),
                        tr("Revisa que no haya equipos o reglas que no reconozcas."),
                    ),
                    gw.id, "router.default_password", cat,
                )
            }
            false -> Unit
            null -> if (brand != null) {
                out += Finding(
                    Severity.LOW, tr("Revisa la contraseña de tu router (%1\$s)", brand),
                    tr("Los routers de esta marca suelen venir con usuario y clave de fábrica conocidos. No probamos nada: es solo una sugerencia."),
                    listOf(
                        tr("Cambia la contraseña de administración si nunca lo hiciste."),
                        tr("Puedes usar \"Probar contraseñas comunes\" para comprobarlo (solo en tu router)."),
                    ),
                    gw.id, "router.brand_defaults", cat,
                )
            }
        }
        return penalty
    }
}
