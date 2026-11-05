package com.lupadered.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lupadered.app.ui.DeviceItem
import com.lupadered.app.ui.HomeState
import com.lupadered.app.ui.MainViewModel
import com.lupadered.app.ui.Presence
import com.lupadered.app.ui.ScreenScaffold
import com.lupadered.app.ui.components.RadarSpinner
import com.lupadered.app.ui.components.DeviceAvatar
import com.lupadered.app.ui.components.EmptyState
import com.lupadered.app.ui.components.ExplainRow
import com.lupadered.app.ui.components.SectionTitle
import com.lupadered.app.ui.components.StatusDot
import com.lupadered.app.ui.components.Tag
import com.lupadered.app.ui.components.latencyText
import com.lupadered.app.ui.components.presenceColor
import com.lupadered.app.ui.components.relativeTime
import com.lupadered.app.ui.theme.StatusColors

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onScan: () -> Unit,
    onOpenDevice: (Long) -> Unit,
    onLearn: () -> Unit,
) {
    val s by vm.home.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Lupa de Red",
        actions = {
            IconButton(onClick = onLearn) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "¿Qué estoy viendo?")
            }
        },
    ) { padding ->
        if (s.networkChecked && s.network == null && !s.scanning) {
            EmptyState(
                icon = Icons.Outlined.WifiOff,
                title = "No estás conectado a un WiFi",
                text = "Para ver los dispositivos de tu casa, conecta este teléfono a tu red WiFi. " +
                    "Con datos móviles no hay una red local que revisar.",
                modifier = Modifier.padding(padding),
                action = { Button(onClick = { vm.refreshNetwork() }) { Text("Reintentar") } },
            )
            return@ScreenScaffold
        }

        val online = s.devices.filter { it.presence != Presence.OFFLINE }
        val offline = s.devices.filter { it.presence == Presence.OFFLINE }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item { NetworkCard(s) }
            item { Spacer(Modifier.height(12.dp)) }
            item { ScanCard(s, onScan = onScan, onCancel = vm::cancelScan) }

            if (s.newDevices.isNotEmpty()) {
                item {
                    NewDevicesCard(
                        s = s,
                        onTrust = vm::trust,
                        onTrustAll = vm::trustAll,
                        onOpen = onOpenDevice,
                        onDismiss = vm::dismissNewDevices,
                    )
                }
            }

            if (s.devices.isEmpty() && !s.scanning) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Devices,
                        title = "Aún no hay dispositivos",
                        text = "Toca \"Escanear red\" para descubrir quién está conectado a tu WiFi. " +
                            "La app recordará los dispositivos para mostrártelos al instante la próxima vez.",
                    )
                }
            }

            if (online.isNotEmpty()) {
                item { SectionTitle(if (s.scanning) "Encontrados (${s.onlineCount})" else "En línea (${online.size})") }
                items(online, key = { "on-" + (it.id?.toString() ?: it.ip) }) { d ->
                    DeviceRow(d, onClick = { d.id?.let(onOpenDevice) })
                }
            }
            if (offline.isNotEmpty()) {
                item {
                    SectionTitle("Vistos antes (${offline.size})")
                    Text(
                        "No respondieron en el último escaneo. Pueden estar apagados, dormidos o fuera de casa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(offline, key = { "off-" + (it.id?.toString() ?: it.ip) }) { d ->
                    DeviceRow(d, onClick = { d.id?.let(onOpenDevice) })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun NetworkCard(s: HomeState) {
    val net = s.network
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Tu red", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            if (net == null) {
                Text("Buscando tu red…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            ExplainRow(
                "Rango que revisamos",
                net.scanSubnet.cidr + " · ${net.scanSubnet.usableCount} direcciones",
                "Tu red usa estas direcciones IP. La app revisa cada una para saber si hay un equipo." +
                    if (net.reducedRange) "\n\nTu red es muy grande (/${net.prefix}), así que revisamos solo el bloque donde está tu teléfono para que el escaneo sea rápido."
                    else "",
            )
            ExplainRow(
                "Router",
                net.gateway ?: "No encontrado",
                "El router es la \"puerta\" de tu casa hacia Internet. Desde esta dirección puedes entrar a su configuración con un navegador.",
            )
            ExplainRow(
                "Tu teléfono",
                net.selfIp,
                "Es la dirección IP que el router le dio a este teléfono dentro de tu red.",
            )
        }
    }
}

@Composable
private fun ScanCard(s: HomeState, onScan: () -> Unit, onCancel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (s.scanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadarSpinner(size = 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(s.phase.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${(s.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${s.onlineCount} dispositivos encontrados",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text("Detener") }
                }
                Text(
                    "Usamos varios métodos a la vez y revisamos dos veces a los que no responden, para no dejar a nadie fuera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            } else {
                Button(
                    onClick = onScan,
                    enabled = s.network != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Escanear red")
                }
                val last = s.lastScan
                if (last != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Último escaneo ${relativeTime(last.startedAt)} · ${last.found} dispositivos · ${(last.durationMs + 500) / 1000} s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (s.untrustedOnline > 0 && s.newDevices.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Hay ${s.untrustedOnline} dispositivo(s) sin revisar en línea. Ábrelos para marcarlos como conocidos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusColors.warn,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewDevicesCard(
    s: HomeState,
    onTrust: (Long) -> Unit,
    onTrustAll: () -> Unit,
    onOpen: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible = s.newDevices.isNotEmpty()) {
        Card(
            modifier = Modifier.padding(top = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NewReleases, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (s.newDevices.size == 1) "Se conectó un dispositivo nuevo"
                        else "Se conectaron ${s.newDevices.size} dispositivos nuevos",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    "No los habíamos visto antes en esta red. Si son tuyos, márcalos como conocidos. Si no los reconoces, revisa quién tiene tu clave WiFi.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                s.newDevices.take(5).forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(d.id) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(d.ip, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onTrust(d.id) }) { Text("Es mío") }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Más tarde") }
                    if (s.newDevices.size > 1) {
                        OutlinedButton(onClick = onTrustAll) { Text("Todos son míos") }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceRow(d: DeviceItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = d.id != null, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceAvatar(d.type, d.presence)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (d.isNew && d.presence == Presence.ONLINE) {
                    Spacer(Modifier.width(6.dp))
                    Tag(if (d.id == null) "Nuevo" else "Sin revisar", StatusColors.warn)
                }
            }
            Text(
                d.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            StatusDot(presenceColor(d.presence))
            Spacer(Modifier.height(4.dp))
            Text(
                when (d.presence) {
                    Presence.ONLINE -> latencyText(d.latencyMs)
                    Presence.CHECKING -> "buscando…"
                    Presence.OFFLINE -> relativeTime(d.lastSeen)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
