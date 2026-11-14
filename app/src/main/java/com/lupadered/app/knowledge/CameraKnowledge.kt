package com.lupadered.app.knowledge

/**
 * Conocimiento para detectar cámaras IP / grabadores en la red (Premium, RF-48).
 * Se apoya en puertos típicos de streaming y en fabricantes/servidores conocidos.
 */
object CameraKnowledge {

    /** Puertos de streaming/administración típicos de cámaras y DVR/NVR. */
    val CAMERA_PORTS: Set<Int> = setOf(554, 8554, 37777, 34567, 9527, 8000)

    /** Puertos que por sí solos casi siempre son cámara o grabador. */
    val STRONG_PORTS: Set<Int> = setOf(554, 8554, 37777, 34567, 9527)

    private val BRANDS = listOf(
        "hikvision", "dahua", "reolink", "tapo", "wyze", "amcrest", "foscam", "axis", "vivotek",
        "ezviz", "imou", "yi ", "xiaomi camera", "mijia camera", "esp32-cam", "esp32cam", "ipcam",
        "ip camera", "webcam", "nvr", "dvr", "camera", "cámara", "blink", "arlo", "ring ", "eufy",
        "uniview", "lorex", "swann", "annke", "zosi", "tenvis", "wanscam", "hik-connect",
    )

    /** Devuelve el motivo por el que parece cámara, o null si no hay indicios. */
    fun reason(openPorts: Set<Int>, vararg textFields: String?): String? {
        val strong = openPorts.firstOrNull { it in STRONG_PORTS }
        if (strong != null) return "puerto $strong"
        val text = textFields.filterNotNull().joinToString(" ").lowercase()
        val brand = BRANDS.firstOrNull { text.contains(it) }
        if (brand != null) return brand.trim()
        return null
    }

    fun looksLikeCamera(openPorts: Set<Int>, vararg textFields: String?): Boolean =
        reason(openPorts, *textFields) != null
}
