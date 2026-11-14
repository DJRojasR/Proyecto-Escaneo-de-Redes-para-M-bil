package com.lupadered.app.monitor

import com.lupadered.app.i18n.tr
import com.lupadered.app.data.AlertRule
import com.lupadered.app.data.AlertType
import com.lupadered.app.data.Device
import com.lupadered.app.data.DeviceEvent
import com.lupadered.app.data.DeviceRepository
import com.lupadered.app.data.EventType

/** Una alerta que debe mostrarse al usuario. */
data class FiredAlert(val rule: AlertRule, val device: Device, val message: String)

/**
 * Evalúa las reglas de alerta por dispositivo (Premium, RF-49) tras cada
 * escaneo en segundo plano. La granularidad real es la del intervalo de
 * vigilancia (mínimo 15 min): una regla de "desconexión" más corta que el
 * intervalo no puede dispararse más seguido que eso.
 */
object AlertEngine {

    /** ¿El equipo lleva desconectado al menos [thresholdMin] minutos? */
    fun offlineExceeded(lastSeen: Long, now: Long, thresholdMin: Int): Boolean =
        now - lastSeen >= thresholdMin * 60_000L

    /** Puertos que aparecen en [current] y no estaban en [previous]. */
    fun newPorts(previous: Set<Int>, current: Set<Int>): Set<Int> = current - previous

    private fun parsePorts(text: String?): Set<Int> =
        text.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    /**
     * Evalúa todas las reglas habilitadas de una red y devuelve las alertas que
     * deben notificarse. Lee y actualiza el estado de cada regla con [repo] para
     * no repetir la misma alerta en cada vuelta.
     */
    fun evaluate(
        repo: DeviceRepository,
        networkKey: String,
        devices: List<Device>,
        eventsThisScan: List<DeviceEvent>,
        now: Long,
    ): List<FiredAlert> {
        val rules = repo.enabledAlertRules(networkKey)
        if (rules.isEmpty()) return emptyList()
        val devicesById = devices.associateBy { it.id }
        val eventsByDevice = eventsThisScan.groupBy { it.deviceId }
        val fired = ArrayList<FiredAlert>()

        for (rule in rules) {
            val device = devicesById[rule.deviceId] ?: continue
            val (lastFiredAt, lastState) = repo.alertState(rule.id)
            when (rule.type) {
                AlertType.OFFLINE_FOR -> {
                    val isOffline = offlineExceeded(device.lastSeen, now, rule.thresholdMin)
                    val wasAlready = lastState == "OFFLINE"
                    if (isOffline && !wasAlready) {
                        fired += FiredAlert(
                            rule, device,
                            tr("%1\$s lleva más de %2\$s min desconectado.", device.displayName, rule.thresholdMin),
                        )
                        repo.setAlertState(rule.id, now, "OFFLINE")
                    } else if (!isOffline && wasAlready) {
                        repo.setAlertState(rule.id, lastFiredAt, "ONLINE")
                    } else if (lastState == null) {
                        repo.setAlertState(rule.id, lastFiredAt, if (isOffline) "OFFLINE" else "ONLINE")
                    }
                }
                AlertType.NEW_PORT -> {
                    val previous = parsePorts(lastState)
                    val current = device.openPorts
                    val added = newPorts(previous, current)
                    if (lastState != null && added.isNotEmpty()) {
                        fired += FiredAlert(
                            rule, device,
                            tr("%1\$s tiene %2\$s nuevo: puerto %3\$s.", device.displayName,
                                if (added.size == 1) tr("un puerto") else tr("puertos"),
                                added.sorted().joinToString(", ")),
                        )
                        repo.setAlertState(rule.id, now, current.sorted().joinToString(","))
                    } else if (lastState == null || current != previous) {
                        repo.setAlertState(rule.id, lastFiredAt, current.sorted().joinToString(","))
                    }
                }
                AlertType.BACK_ONLINE -> {
                    val back = eventsByDevice[rule.deviceId]?.any { it.type == EventType.BACK } == true
                    if (back) {
                        fired += FiredAlert(rule, device, tr("%1\$s volvió a conectarse.", device.displayName))
                        repo.setAlertState(rule.id, now, "FIRED")
                    }
                }
                AlertType.IP_CHANGED -> {
                    val changed = eventsByDevice[rule.deviceId]?.firstOrNull { it.type == EventType.IP_CHANGED }
                    if (changed != null) {
                        fired += FiredAlert(
                            rule, device,
                            tr("%1\$s cambió de IP: ahora es %2\$s.", device.displayName, device.ip),
                        )
                        repo.setAlertState(rule.id, now, "FIRED")
                    }
                }
            }
        }
        return fired
    }
}
