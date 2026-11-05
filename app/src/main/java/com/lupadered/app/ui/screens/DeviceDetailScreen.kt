package com.lupadered.app.ui.screens

import androidx.compose.foundation.clickable
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
    val uri = LocalUriHandler.current

    LaunchedEffect(s.deleted) {
        if (s.deleted) {
            onChanged()
            onBack()
        }
    }

    val d = s.device
    ScreenScaffold(
        title = d?.displayName ?: "Dispositivo",
        onBack = {
            onChanged()
            onBack()
        },
        actions = {
            if (d != null) {
                IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, contentDescription = "Cambiar nombre") }
            }
        },
    ) { padding ->
        if (d == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                if (s.loading) CircularProgressIndicator() else Text("Este dispositivo ya no existe.")
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
                DeviceAvatar(d.type, Presence.ONLINE)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        d.type.label + (if (d.userType == null) " (detectado)" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { choosingType = true },
                    )
                }
            }
            Text(
                d.type.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (d.alias == null && !d.isSelf) {
                TextButton(onClick = { renaming = true }) { Text("Ponle un nombre (ej. \"Celular de mamá\")") }
            }

            // ---------- Conocido
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Es un equipo conocido", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Márcalo si sabes de quién es. Los equipos no marcados aparecen como \"sin revisar\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = d.trusted, onCheckedChange = { vm.setTrusted(it) })
                }
            }

            // ---------- Datos
            SectionTitle("Información")
            ExplainRow(
                "Dirección IP", d.ip,
                "Es su \"número de casa\" dentro de tu red. Puede cambiar con el tiempo si el router reparte las direcciones automáticamente. " +
                    "Si tiene un nombre en la red, lo reconoceremos aunque cambie de IP.",
            )
            d.name?.let {
                ExplainRow(
                    "Nombre en la red", it,
                    "Es el nombre con el que el propio equipo se presenta (por Bonjour, NetBIOS, UPnP o el router). Lo eligió el fabricante o su dueño.",
                )
            }
            val brand = listOfNotNull(d.vendor, d.model).distinct().joinToString(" · ")
            if (brand.isNotBlank()) {
                ExplainRow("Fabricante / modelo", brand, "Información que el propio equipo anunció en la red.")
            }
            ExplainRow("Tiempo de respuesta", latencyText(d.latencyMs), latencyExplanation(d.latencyMs))
            if (d.methods.isNotEmpty()) {
                ExplainRow(
                    "Cómo lo encontramos",
                    d.methods.joinToString(", ") { it.label },
                    d.methods.joinToString("\n\n") { "• ${it.label}: ${it.explanation}" },
                )
            }
            ExplainRow(
                "Visto por primera vez", dateTime(d.firstSeen),
                "La primera vez que esta app lo encontró en esta red.",
            )
            ExplainRow(
                "Visto por última vez", relativeTime(d.lastSeen),
                "La última vez que respondió en un escaneo.",
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
                    Text(if (d.isGateway) "Abrir configuración del router" else "Abrir su página web")
                }
            }

            // ---------- Servicios
            if (d.services.isNotEmpty()) {
                SectionTitle("Lo que sabe hacer")
                d.services.sorted().forEach { svc ->
                    Text("• " + DeviceClassifier.serviceLabel(svc), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            // ---------- Puertos
            SectionTitle("Puertos abiertos")
            Text(
                "Un puerto abierto es una \"puerta\" por la que el equipo ofrece un servicio. No es malo por sí mismo; " +
                    "te avisamos cuando alguno merece una revisión.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (s.portScanRunning) {
                RadarStatus("Revisando ${PortKnowledge.COMMON_PORTS.size} puertos comunes… ${(s.portScanProgress * 100).toInt()}%")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { s.portScanProgress }, modifier = Modifier.fillMaxWidth())
            } else {
                Button(onClick = vm::scanPorts) {
                    Icon(Icons.Outlined.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (d.portsScannedAt == null) "Analizar puertos" else "Volver a analizar")
                }
                d.portsScannedAt?.let {
                    Text(
                        "Último análisis: ${relativeTime(it)}",
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
                    "Aún no analizamos sus puertos a fondo.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // ---------- Historial
            if (s.events.isNotEmpty()) {
                SectionTitle("Historial")
                s.events.forEach { e ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            e.type.label + if (e.detail != null) " (antes ${e.detail} → ahora ${e.ip})" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(relativeTime(e.at), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (!d.isSelf) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Olvidar este dispositivo", color = MaterialTheme.colorScheme.error)
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
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Olvidar este dispositivo?") },
            text = { Text("Se borrarán su nombre personalizado y su historial. Si vuelve a conectarse, aparecerá como nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                }) { Text("Olvidar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
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
                Text("Consejo: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RenameDialog(d: Device, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(d.alias ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre del dispositivo") },
        text = {
            Column {
                Text(
                    "Ponle un nombre que reconozcas. Se mantiene aunque vuelvas a escanear.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    placeholder = { Text(d.name ?: "Ej. TV de la sala") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun TypeDialog(d: Device, onDismiss: () -> Unit, onSelect: (DeviceType?) -> Unit) {
    val options: List<DeviceType?> = listOf<DeviceType?>(null) +
        DeviceType.entries.filter { it != DeviceType.THIS_PHONE }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Qué tipo de equipo es?") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(t) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = d.userType == t, onClick = { onSelect(t) })
                        Text(if (t == null) "Automático (${d.autoType.label})" else t.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
