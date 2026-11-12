package com.lupadered.app.ui.screens

import com.lupadered.app.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AssistChip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.lupadered.app.data.AppSettings
import com.lupadered.app.data.DeviceEvent
import com.lupadered.app.data.EventType
import com.lupadered.app.ui.components.DeviceIcons
import com.lupadered.app.ui.components.LocalOpenPremium
import com.lupadered.app.ui.components.LockedCard
import com.lupadered.app.ui.components.PremiumTag
import com.lupadered.app.ui.components.rememberIsPremium
import com.lupadered.app.ui.theme.StatusColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lupadered.app.data.Device
import com.lupadered.app.knowledge.DeviceClassifier
import com.lupadered.app.knowledge.DeviceType
import com.lupadered.app.knowledge.PortKnowledge
import com.lupadered.app.ui.DeviceViewModel
import com.lupadered.app.ui.Presence
import com.lupadered.app.ui.ScreenScaffold
import com.lupadered.app.ui.components.RadarStatus
import com.lupadered.app.ui.components.DeviceAvatar
import com.lupadered.app.ui.components.ExplainRow
import com.lupadered.app.ui.components.SectionTitle
import com.lupadered.app.ui.components.Tag
import com.lupadered.app.ui.components.dateTime
import com.lupadered.app.ui.components.latencyExplanation
import com.lupadered.app.ui.components.latencyText
import com.lupadered.app.ui.components.relativeTime
import com.lupadered.app.ui.components.riskColor

@Composable
fun DeviceDetailScreen(vm: DeviceViewModel, onBack: () -> Unit, onChanged: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var choosingType by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var choosingGroup by remember { mutableStateOf(false) }
    var choosingIcon by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val isPremium = rememberIsPremium()
    val openPremium = LocalOpenPremium.current

    LaunchedEffect(s.deleted) {
        if (s.deleted) {
            onChanged()
            onBack()
        }
    }

    val d = s.device
    ScreenScaffold(
        title = d?.displayName ?: tr("Dispositivo"),
        onBack = {
            onChanged()
            onBack()
        },
        actions = {
            if (d != null) {
                IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, contentDescription = tr("Cambiar nombre")) }
            }
        },
    ) { padding ->
        if (d == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                if (s.loading) CircularProgressIndicator() else Text(tr("Este dispositivo ya no existe."))
            }
            return@ScreenScaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // ---------- Cabecera
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(
                    d.type, Presence.ONLINE, iconKey = d.customIcon,
                    modifier = Modifier.clickable { if (isPremium) choosingIcon = true else openPremium() },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.displayName, style = MaterialTheme.typography.titleLarge)
                    // Tipo de equipo (qué es) — distinto de si es conocido (de quién es)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { choosingType = true },
                    ) {
                        Text(
                            d.type.label + (if (d.userType == null) tr(" (automático)") else ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.Edit, contentDescription = tr("Cambiar tipo"),
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp),
                        )
                    }
                    if (!d.isSelf) {
                        Tag(
                            if (d.trusted) tr("✓ Equipo conocido") else tr("Sin revisar"),
                            if (d.trusted) StatusColors.good else StatusColors.warn,
                            Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Text(
                if (d.type == DeviceType.UNKNOWN && d.trusted)
                    tr("Ya lo marcaste como conocido. No pudimos saber qué tipo de equipo es: toca \"Tipo sin identificar\" y elige si es un celular, una laptop, una TV… para que tenga el ícono correcto.")
                else d.type.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (d.alias == null && !d.isSelf) {
                TextButton(onClick = { renaming = true }) { Text(tr("Ponle un nombre (ej. \"Celular de mamá\")")) }
            }

            // ---------- Grupo e ícono (Premium, RF-32)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()),
            ) {
                AssistChip(
                    onClick = { if (isPremium) choosingGroup = true else openPremium() },
                    label = { Text(d.groupName?.let { tr("Grupo: %1\$s", it) } ?: tr("Agregar a un grupo")) },
                    leadingIcon = { Icon(Icons.Outlined.Category, contentDescription = null) },
                )
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = { if (isPremium) choosingIcon = true else openPremium() },
                    label = { Text(tr("Cambiar ícono")) },
                    leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                )
                if (!isPremium) {
                    Spacer(Modifier.width(8.dp))
                    PremiumTag()
                }
            }

            // ---------- Conocido
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Es un equipo conocido"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            tr("Márcalo si sabes de quién es. Los equipos no marcados aparecen como \"sin revisar\"."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = d.trusted, onCheckedChange = { vm.setTrusted(it) })
                }
            }

            // ---------- Datos
            SectionTitle(tr("Información"))
            ExplainRow(
                tr("Dirección IP"), d.ip,
                tr("Es su \"número de casa\" dentro de tu red. Puede cambiar con el tiempo si el router reparte las direcciones automáticamente. Si tiene un nombre en la red, lo reconoceremos aunque cambie de IP."),
            )
            d.name?.let {
                ExplainRow(
                    tr("Nombre en la red"), it,
                    tr("Es el nombre con el que el propio equipo se presenta (por Bonjour, NetBIOS, UPnP o el router). Lo eligió el fabricante o su dueño."),
                )
            }
            val brand = listOfNotNull(d.vendor, d.model).distinct().joinToString(" · ")
            if (brand.isNotBlank()) {
                ExplainRow(tr("Fabricante / modelo"), brand, tr("Información que el propio equipo anunció en la red."))
            }
            ExplainRow(tr("Tiempo de respuesta"), latencyText(d.latencyMs), latencyExplanation(d.latencyMs))
            if (d.methods.isNotEmpty()) {
                ExplainRow(
                    tr("Cómo lo encontramos"),
                    d.methods.joinToString(", ") { it.label },
                    d.methods.joinToString("\n\n") { "• ${it.label}: ${it.explanation}" },
                )
            }
            ExplainRow(
                tr("Visto por primera vez"), dateTime(d.firstSeen),
                tr("La primera vez que esta app lo encontró en esta red."),
            )
            ExplainRow(
                tr("Visto por última vez"), relativeTime(d.lastSeen),
                tr("La última vez que respondió en un escaneo."),
            )

            // ---------- Acceso web
            val webPort = listOf(80, 443, 8080, 8443, 5000, 5001).firstOrNull { it in d.openPorts }
            if (d.isGateway || webPort != null) {
                OutlinedButton(
                    onClick = {
                        val port = webPort ?: 80
                        val scheme = if (port == 443 || port == 8443 || port == 5001) "https" else "http"
                        val suffix = if (port == 80 || port == 443) "" else ":$port"
                        runCatching { uri.openUri("$scheme://${d.ip}$suffix") }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (d.isGateway) tr("Abrir configuración del router") else tr("Abrir su página web"))
                }
            }

            // ---------- Servicios
            if (d.services.isNotEmpty()) {
                SectionTitle(tr("Lo que sabe hacer"))
                d.services.sorted().map { DeviceClassifier.serviceLabel(it) }.distinct().forEach { label ->
                    Text("• " + label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            // ---------- Puertos
            SectionTitle(tr("Puertos abiertos"))
            Text(
                tr("Un puerto abierto es una \"puerta\" por la que el equipo ofrece un servicio. No es malo por sí mismo; te avisamos cuando alguno merece una revisión."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (s.portScanRunning) {
                RadarStatus("${s.portScanLabel} ${(s.portScanProgress * 100).toInt()}%")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { s.portScanProgress }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = vm::cancelPortScan) { Text(tr("Detener")) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = vm::scanPorts) {
                        Icon(Icons.Outlined.Security, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (d.portsScannedAt == null) tr("Analizar puertos") else tr("Volver a analizar"))
                    }
                }
                d.portsScannedAt?.let {
                    Text(
                        tr("Último análisis: %1\$s", relativeTime(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            s.portScanMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            if (d.openPorts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                d.openPorts.sorted().forEach { PortCard(it) }
            } else if (d.portsScannedAt == null) {
                Text(
                    tr("Aún no analizamos sus puertos."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // ---------- Línea de tiempo (RF-27)
            if (s.events.isNotEmpty() || s.hiddenEvents > 0) {
                SectionTitle(tr("Línea de tiempo"))
                val connections = s.events.count { it.type == EventType.NEW || it.type == EventType.BACK }
                if (isPremium && connections > 0) {
                    Text(
                        tr("Se ha conectado %1\$s %2\$s desde %3\$s.", connections, if (connections == 1) tr("vez") else tr("veces"), dateTime(d.firstSeen)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Timeline(s.events)
                if (s.hiddenEvents > 0) {
                    LockedCard(
                        title = tr("Línea de tiempo completa"),
                        text = tr("Hay %1\$s eventos de más de %2\$s días. Con Premium ves toda la historia de este equipo.", s.hiddenEvents, AppSettings.FREE_HISTORY_DAYS),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            if (!d.isSelf) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 16.dp)) {
                    Text(tr("Olvidar este dispositivo"), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (renaming && d != null) {
        RenameDialog(d, onDismiss = { renaming = false }, onSave = {
            vm.rename(it)
            renaming = false
        })
    }
    if (choosingType && d != null) {
        TypeDialog(d, onDismiss = { choosingType = false }, onSelect = {
            vm.setType(it)
            choosingType = false
        })
    }
    if (choosingGroup && d != null) {
        GroupDialog(
            current = d.groupName,
            groups = s.groups,
            onDismiss = { choosingGroup = false },
            onSelect = {
                vm.setGroup(it)
                choosingGroup = false
            },
        )
    }
    if (choosingIcon && d != null) {
        IconDialog(
            d = d,
            onDismiss = { choosingIcon = false },
            onSelect = {
                vm.setIcon(it)
                choosingIcon = false
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("¿Olvidar este dispositivo?")) },
            text = { Text(tr("Se borrarán su nombre personalizado y su historial. Si vuelve a conectarse, aparecerá como nuevo.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                }) { Text(tr("Olvidar")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar")) } },
        )
    }
}

@Composable
fun PortCard(port: Int) {
    val info = PortKnowledge.info(port)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${info.port} · ${info.service}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Tag(info.risk.label, riskColor(info.risk))
            }
            Text(info.whatItMeans, style = MaterialTheme.typography.bodyMedium)
            info.advice?.let {
                Text(tr("Consejo: %1\$s", it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RenameDialog(d: Device, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(d.alias ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Nombre del dispositivo")) },
        text = {
            Column {
                Text(
                    tr("Ponle un nombre que reconozcas. Se mantiene aunque vuelvas a escanear."),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    placeholder = { Text(d.name ?: tr("Ej. TV de la sala")) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(tr("Guardar")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } },
    )
}

@Composable
private fun TypeDialog(d: Device, onDismiss: () -> Unit, onSelect: (DeviceType?) -> Unit) {
    val options: List<DeviceType?> = listOf<DeviceType?>(null) +
        DeviceType.entries.filter { it != DeviceType.THIS_PHONE }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("¿Qué tipo de equipo es?")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(t) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = d.userType == t, onClick = { onSelect(t) })
                        Text(if (t == null) tr("Automático (%1\$s)", d.autoType.label) else t.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Cerrar")) } },
    )
}


@Composable
private fun Timeline(events: List<DeviceEvent>) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column {
        events.forEachIndexed { i, e ->
            val color = when (e.type) {
                EventType.NEW -> StatusColors.warn
                EventType.BACK -> StatusColors.good
                EventType.LEFT -> StatusColors.idle
                EventType.IP_CHANGED -> MaterialTheme.colorScheme.tertiary
            }
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(20.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    if (i < events.lastIndex) {
                        Box(Modifier.padding(top = 14.dp).width(2.dp).fillMaxHeight().background(lineColor))
                    }
                    Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(color))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
                    Text(
                        e.type.label + if (e.detail != null) tr(" (antes %1\$s → ahora %2\$s)", e.detail, e.ip) else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        dateTime(e.at) + " · " + relativeTime(e.at),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupDialog(current: String?, groups: List<String>, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Grupo")) },
        text = {
            Column {
                Text(
                    tr("Agrupa tus equipos para encontrarlos rápido, por ejemplo \"Cámaras\" o \"Cuarto de mamá\"."),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(null) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current == null, onClick = { onSelect(null) })
                            Text(tr("Sin grupo"))
                        }
                    }
                    items(groups) { g ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(g) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current == g, onClick = { onSelect(g) })
                            Text(g)
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(30) },
                    singleLine = true,
                    label = { Text(tr("Nuevo grupo")) },
                    placeholder = { Text(tr("Ej. Cámaras")) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(text.trim()) }, enabled = text.isNotBlank()) { Text(tr("Crear y usar")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } },
    )
}

@Composable
private fun IconDialog(d: Device, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Elige un ícono")) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    IconChoice(DeviceIcons.iconFor(d.type, null), tr("Automático"), d.customIcon == null) { onSelect(null) }
                }
                items(DeviceIcons.options) { o ->
                    IconChoice(o.icon, o.label, d.customIcon == o.key) { onSelect(o.key) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Cerrar")) } },
    )
}

@Composable
private fun IconChoice(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
