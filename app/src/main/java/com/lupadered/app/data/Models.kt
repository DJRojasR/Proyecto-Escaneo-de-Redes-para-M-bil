package com.lupadered.app.data

import com.lupadered.app.i18n.tr
import com.lupadered.app.knowledge.DeviceType
import com.lupadered.app.net.DiscoveryMethod

/** Un dispositivo guardado en el inventario de una red. */
data class Device(
    val id: Long,
    val networkKey: String,
    val ip: String,
    val nameKey: String?,
    val alias: String?,
    val userType: DeviceType?,
    val autoType: DeviceType,
    val name: String?,
    val vendor: String?,
    val model: String?,
    val server: String?,
    val services: Set<String>,
    val openPorts: Set<Int>,
    val methods: Set<DiscoveryMethod>,
    val portsScannedAt: Long?,
    val firstSeen: Long,
    val lastSeen: Long,
    val lastScanId: Long,
    val latencyMs: Long?,
    val trusted: Boolean,
    val isGateway: Boolean,
    val isSelf: Boolean,
    /** Grupo elegido por el usuario, p. ej. "Cámaras" (Premium, RF-32). */
    val groupName: String? = null,
    /** Ícono elegido por el usuario (clave de DeviceIcons); null = el del tipo (RF-32). */
    val customIcon: String? = null,
) {
    val type: DeviceType get() = userType ?: autoType

    /** Lo que mostramos como título: tu nombre > nombre en la red > tipo. */
    val displayName: String
        get() = alias?.takeIf { it.isNotBlank() }
            ?: when {
                isSelf -> tr("Este teléfono") + (name?.let { " ($it)" } ?: "")
                isGateway && name == null -> tr("Router")
                else -> name ?: type.label
            }
}

enum class EventType(val label: String) {
    NEW(tr("Nuevo dispositivo")),
    BACK(tr("Volvió a conectarse")),
    LEFT(tr("Se desconectó")),
    IP_CHANGED(tr("Cambió de IP")),
}

data class DeviceEvent(
    val id: Long,
    val networkKey: String,
    val deviceId: Long,
    val type: EventType,
    val ip: String,
    val detail: String?,
    val at: Long,
)

/** Quién inició un escaneo. */
enum class ScanSource(val label: String) {
    MANUAL(tr("Manual")),
    AUTO(tr("Vigilancia automática")),
    SCHEDULED(tr("Programado")),
}

data class ScanRecord(
    val id: Long,
    val networkKey: String,
    val subnet: String,
    val startedAt: Long,
    val durationMs: Long,
    val found: Int,
    val newCount: Int,
    val source: ScanSource = ScanSource.MANUAL,
)

/** Una red guardada (casa, trabajo…) con su propio inventario (RF-33). */
data class NetworkProfile(
    val key: String,
    val name: String?,
    val subnet: String,
    val gateway: String?,
    val firstSeen: Long,
    val lastSeen: Long,
    val deviceCount: Int,
    val lastFound: Int?,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: tr("Red %1\$s", subnet)
}

data class ScanSummary(
    val scanId: Long,
    val found: Int,
    val newDevices: List<Device>,
    val firstScan: Boolean,
)

/** Tipo de alerta personalizada por dispositivo (Premium, RF-49). */
enum class AlertType(val label: String) {
    OFFLINE_FOR(tr("Se desconecta por más de N minutos")),
    NEW_PORT(tr("Aparece un puerto nuevo abierto")),
    BACK_ONLINE(tr("Vuelve a conectarse")),
    IP_CHANGED(tr("Cambia de IP")),
}

/** Una regla de alerta que el usuario configuró para un equipo (RF-49). */
data class AlertRule(
    val id: Long = 0,
    val deviceId: Long,
    val type: AlertType,
    /** Minutos de umbral; solo se usa en OFFLINE_FOR. */
    val thresholdMin: Int = 15,
    val enabled: Boolean = true,
    val createdAt: Long = 0,
)

/** Una prueba de velocidad guardada en el historial. */
data class SpeedTestRecord(
    val id: Long = 0,
    val networkKey: String?,
    val at: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double?,
    val server: String?,
)
