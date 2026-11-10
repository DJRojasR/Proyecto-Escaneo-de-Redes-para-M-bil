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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import com.lupadered.app.ui.components.rememberIsPremium
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val isPremium = rememberIsPremium()
    var renaming by remember { mutableStateOf(false) }
    if (renaming) {
        NetworkNameDialog(
            current = s.profile?.name.orEmpty(),
            onDismiss = { renaming = false },
            onSave = {
                vm.renameCurrentNetwork(it)
                renaming = false
            },
        )
    }

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

        val shown = s.selectedGroup?.let { g -> s.devices.filter { it.groupName == g } } ?: s.devices
        val online = shown.filter { it.presence != Presence.OFFLINE }
        val offline = shown.filter { it.presence == Presence.OFFLINE }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (s.networkChanged && s.network != null) {
                item {
                    Card(
                        modifier = Modifier.padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (s.profile == null) "Te conectaste a una red nueva. Escanéala para guardarla en \"Mis redes\"."
                                else "Cambiaste a la red \"${s.profile?.displayName}\". Te mostramos sus equipos.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = vm::dismissNetworkChanged) { Text("OK") }
                        }
                    }
                }
            }
            item { NetworkCard(s, onRename = { renaming = true }) }
            if (!s.scanning && s.profile != null && s.profile?.name == null && !s.namePromptDismissed) {
                item {
                    NamePromptCard(
                        onSave = vm::renameCurrentNetwork,
                        onLater = vm::dismissNamePrompt,
                    )
                }
            }
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

            if (s.groups.isNotEmpty() && isPremium) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = s.selectedGroup == null,
                                onClick = { vm.selectGroup(null) },
                                label = { Text("Todos") },
                            )
                        }
                        items(s.groups) { g ->
                            FilterChip(
                                selected = s.selectedGroup == g,
                                onClick = { vm.selectGroup(if (s.selectedGroup == g) null else g) },
                                label = { Text(g) },
                            )
                        }
                    }
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
private fun NetworkCard(s: HomeState, onRename: () -> Unit) {
    val net = s.network
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    s.profile?.displayName ?: "Tu red",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (net != null && s.profile == null) {
                    Spacer(Modifier.width(8.dp))
                    Tag("Red nueva", StatusColors.warn)
                }
                Spacer(Modifier.weight(1f))
                if (s.profile != null) {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Cambiar el nombre de la red")
                    }
                }
            }
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
        DeviceAvatar(d.type, d.presence, iconKey = d.customIcon)
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


/** Pide un nombre para la red después de escanearla por primera vez ("Casa", "Router 2"…). */
@Composable
private fun NamePromptCard(onSave: (String) -> Unit, onLater: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("¿Cómo se llama esta red?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ponle un nombre para reconocerla en \"Mis redes\", por ejemplo si tienes dos routers.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Casa", "Router 2", "Trabajo").forEach { suggestion ->
                    AssistChip(onClick = { onSave(suggestion) }, label = { Text(suggestion) })
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                singleLine = true,
                placeholder = { Text("Otro nombre") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) { Text("Ahora no") }
                TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Guardar") }
            }
        }
    }
}

@Composable
fun NetworkNameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre de la red") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                singleLine = true,
                placeholder = { Text("Ej. Casa, Router 2") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
