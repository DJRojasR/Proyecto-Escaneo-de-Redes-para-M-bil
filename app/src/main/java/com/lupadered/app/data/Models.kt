package com.lupadered.app.data

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
) {
    val type: DeviceType get() = userType ?: autoType

    /** Lo que mostramos como título: tu nombre > nombre en la red > tipo. */
    val displayName: String
        get() = alias?.takeIf { it.isNotBlank() }
            ?: when {
                isSelf -> "Este teléfono" + (name?.let { " ($it)" } ?: "")
                isGateway && name == null -> "Router"
                else -> name ?: type.label
            }
}

enum class EventType(val label: String) {
    NEW("Nuevo dispositivo"),
    BACK("Volvió a conectarse"),
    LEFT("Se desconectó"),
    IP_CHANGED("Cambió de IP"),
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

data class ScanRecord(
    val id: Long,
    val networkKey: String,
    val subnet: String,
    val startedAt: Long,
    val durationMs: Long,
    val found: Int,
    val newCount: Int,
)

data class ScanSummary(
    val scanId: Long,
    val found: Int,
    val newDevices: List<Device>,
    val firstScan: Boolean,
)
