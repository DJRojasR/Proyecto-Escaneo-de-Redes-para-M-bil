package com.lupadered.app.ui.screens

import com.lupadered.app.i18n.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WorkspacePremium
import com.lupadered.app.ui.components.PremiumGold
import com.lupadered.app.ui.components.PremiumTag
import com.lupadered.app.ui.components.rememberIsPremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lupadered.app.knowledge.Glossary
import com.lupadered.app.net.DiagStatus
import com.lupadered.app.net.InspectionResult
import com.lupadered.app.ui.ScreenScaffold
import com.lupadered.app.ui.ToolsViewModel
import com.lupadered.app.ui.components.RadarStatus
import com.lupadered.app.ui.components.CheckList
import com.lupadered.app.ui.components.ExplainRow
import com.lupadered.app.ui.components.SectionTitle
import com.lupadered.app.ui.components.StatusDot
import com.lupadered.app.ui.components.diagColor
import com.lupadered.app.ui.components.latencyExplanation
import com.lupadered.app.ui.theme.StatusColors

// ============================================================== Herramientas

/** Acciones de la pantalla Herramientas. */
class ToolsActions(
    val onManual: () -> Unit,
    val onDiagnostics: () -> Unit,
    val onLearn: () -> Unit,
    val onSettings: () -> Unit,
    val onPremium: () -> Unit,
    val onMonitor: () -> Unit,
    val onSecurity: () -> Unit,
    val onLatency: () -> Unit,
    val onNetworks: () -> Unit,
    val onExport: () -> Unit,
    val onSpeed: () -> Unit,
    val onCameras: () -> Unit,
)

@Composable
fun ToolsScreen(a: ToolsActions) {
    val isPremium = rememberIsPremium()
    ScreenScaffold(title = tr("Herramientas")) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isPremium) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = a.onPremium),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = PremiumGold)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(tr("Lupa de Red Premium"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                tr("Sin anuncios, alerta de intrusos, revisión de seguridad y más. Suscripción mensual o pago único."),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            ToolSection(
                title = tr("Herramientas gratis"),
                subtitle = tr("Disponibles para todos, sin pagar."),
                icon = Icons.Outlined.LockOpen,
                color = MaterialTheme.colorScheme.primary,
            )
            ToolCard(
                Icons.Outlined.NetworkCheck, tr("Prueba de velocidad"),
                tr("¿Cuánto Internet te llega de verdad? Mide la bajada, la subida y la latencia en 20 segundos.") +
                    if (isPremium) "" else tr(" Gratis: 2 pruebas al día."),
                a.onSpeed,
            )
            ToolCard(
                Icons.AutoMirrored.Outlined.ManageSearch, tr("Analizar una IP"),
                tr("¿Conoces una dirección IP de tu red? Revisa si responde, cuánto tarda, su nombre y sus puertos abiertos."),
                a.onManual,
            )
            ToolCard(
                Icons.Outlined.Speed, tr("Diagnóstico de conexión"),
                tr("¿Internet va lento? Descubre si el problema es la señal WiFi, el router, tu proveedor o el DNS."),
                a.onDiagnostics,
            )
            ToolCard(
                Icons.Outlined.Wifi, tr("Mis redes"),
                tr("Casa, trabajo… cada red con su propio inventario y nombre."),
                a.onNetworks,
            )

            ToolSection(
                title = tr("Herramientas Premium"),
                subtitle = if (isPremium) tr("Incluidas en tu Premium. ¡Gracias por apoyar la app!")
                else tr("Desbloquéalas todas con Premium."),
                icon = Icons.Outlined.WorkspacePremium,
                color = PremiumGold,
            )
            ToolCard(
                Icons.Outlined.NotificationsActive, tr("Vigilancia de la red"),
                tr("Escaneos automáticos, escaneo diario y aviso cuando se conecta alguien desconocido."),
                a.onMonitor, premium = !isPremium,
            )
            ToolCard(
                Icons.Outlined.Security, tr("Revisión de seguridad"),
                tr("Un puntaje de tu red y los pasos para cerrar lo riesgoso."),
                a.onSecurity, premium = !isPremium,
            )
            ToolCard(
                Icons.AutoMirrored.Outlined.ShowChart, tr("Monitor de latencia"),
                tr("Gráfico en vivo del ping a tu router y a Internet."),
                a.onLatency, premium = !isPremium,
            )
            ToolCard(
                Icons.Outlined.Description, tr("Exportar reporte"),
                tr("Inventario, seguridad e historial en PDF o CSV."),
                a.onExport, premium = !isPremium,
            )
            ToolCard(
                Icons.Outlined.Videocam, tr("Detector de cámaras ocultas"),
                tr("Busca cámaras IP conectadas a tu red y usa el sensor magnético para rastrear lentes ocultas."),
                a.onCameras, premium = !isPremium,
            )

            ToolSection(
                title = tr("Ayuda y ajustes"),
                subtitle = null,
                icon = Icons.Outlined.Settings,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToolCard(
                Icons.Outlined.School, tr("¿Qué estoy viendo?"),
                tr("Explicaciones sencillas de IP, router, puertos, latencia y más."),
                a.onLearn,
            )
            ToolCard(
                Icons.Outlined.Settings, tr("Ajustes y privacidad"),
                tr("Apariencia, privacidad de anuncios, borrar datos y política de privacidad."),
                a.onSettings,
            )
        }
    }
}

/** Subtítulo de sección en Herramientas: separa lo gratis de lo Premium. */
@Composable
private fun ToolSection(title: String, subtitle: String?, icon: ImageVector, color: Color) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = color)
        }
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp),
            )
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = color.copy(alpha = 0.35f))
    }
}

@Composable
private fun ToolCard(icon: ImageVector, title: String, text: String, onClick: () -> Unit, premium: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    if (premium) {
                        Spacer(Modifier.width(8.dp))
                        PremiumTag()
                    }
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ============================================================== Escaneo manual

@Composable
fun ManualScanScreen(vm: ToolsViewModel, initialIp: String, onBeforeScan: () -> Unit, onBack: () -> Unit) {
    val s by vm.manual.collectAsStateWithLifecycle()
    var ip by rememberSaveable { mutableStateOf(initialIp.ifBlank { vm.ipPrefix() }) }
    val focus = LocalFocusManager.current

    val start = {
        focus.clearFocus()
        if (vm.validate(ip) == null) onBeforeScan()
        vm.inspect(ip)
    }

    ScreenScaffold(title = tr("Analizar una IP"), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(
                tr("Escribe una dirección de tu red. La revisaremos a fondo: si responde, cuánto tarda, cómo se llama y qué puertos tiene abiertos."),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ip,
                onValueChange = {
                    ip = it.filter { c -> c.isDigit() || c == '.' }.take(15)
                    vm.clearError()
                },
                label = { Text(tr("Dirección IP")) },
                placeholder = { Text("192.168.1.10") },
                singleLine = true,
                isError = s.error != null,
                supportingText = { s.error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { start() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (s.running) {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = vm::cancelInspect) { Text(tr("Detener")) }
            } else {
                Button(onClick = start, modifier = Modifier.fillMaxWidth()) { Text(tr("Analizar")) }
            }
            if (s.checks.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(
                            if (s.running) tr("Analizando %1\$s…", s.result?.ip ?: ip.trim()) else tr("Lo que revisamos"),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        CheckList(s.checks)
                    }
                }
            }
            s.result?.let { InspectionView(it) }
        }
    }
}

@Composable
private fun InspectionView(r: InspectionResult) {
    Spacer(Modifier.height(16.dp))
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (r.alive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (r.alive) StatusColors.good else StatusColors.idle)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (r.alive) tr("%1\$s está en uso", r.ip) else tr("%1\$s no respondió", r.ip),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                if (r.alive) {
                    tr("Hay un equipo encendido en esta dirección.") + (r.bestName?.let { tr(" Se llama \"%1\$s\".", it) } ?: "")
                } else {
                    tr("No respondió a ping, a conexiones ni a consultas de nombre. Puede estar libre, pero también puede haber un equipo apagado, dormido o con firewall. No la uses como IP fija sin revisar tu router.")
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    if (!r.alive) return

    SectionTitle(tr("Respuesta"))
    if (r.pingOk.isNotEmpty()) {
        ExplainRow(
            tr("Tiempo de respuesta (ping)"),
            tr("promedio %1\$s ms · mín %2\$s · máx %3\$s", r.pingAvg, r.pingMin, r.pingMax),
            latencyExplanation(r.pingAvg),
        )
        ExplainRow(
            tr("Pérdida de paquetes"),
            "${r.pingLossPercent}%",
            tr("De 4 mensajes de prueba, cuántos no tuvieron respuesta. 0% es lo ideal. Una pérdida alta indica señal débil o un equipo saturado."),
        )
    } else {
        ExplainRow(
            tr("Ping"),
            tr("Bloqueado"),
            tr("El equipo no responde al ping (muchas PCs con Windows y algunos celulares lo bloquean por seguridad), pero sí lo detectamos por otros métodos."),
        )
    }
    r.mdnsHostname?.let { ExplainRow(tr("Nombre Bonjour"), it, tr("Nombre que el equipo anuncia por mDNS (típico de Apple, Android, Chromecast e impresoras).")) }
    r.netbiosName?.let { ExplainRow(tr("Nombre de Windows"), it, tr("Nombre de equipo que responde por NetBIOS (PCs con Windows o discos NAS).")) }
    r.dnsName?.let { ExplainRow(tr("Nombre según el router"), it, tr("El nombre que el equipo le dio al router al conectarse.")) }

    SectionTitle(tr("Puertos abiertos (%1\$s)", r.openPorts.size))
    if (r.openPorts.isEmpty()) {
        Text(
            tr("No encontramos puertos abiertos de nuestra lista. Significa que este equipo ofrece pocos servicios a la red, lo cual es bueno para la seguridad."),
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        r.openPorts.forEach { PortCard(it) }
    }
    Text(
        tr("Análisis completado en %1\$s s.", (r.durationMs + 500) / 1000),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

// ============================================================== Diagnóstico

@Composable
fun DiagnosticsScreen(vm: ToolsViewModel, onBack: () -> Unit) {
    val s by vm.diag.collectAsStateWithLifecycle()
    ScreenScaffold(title = tr("Diagnóstico"), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(
                tr("Revisamos tu conexión paso a paso: la señal WiFi, el router, la salida a Internet y el DNS. Así sabrás dónde está el problema."),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            if (s.running) {
                RadarStatus(tr("Probando tu conexión…"), size = 36.dp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            } else {
                Button(onClick = vm::runDiagnostics, modifier = Modifier.fillMaxWidth()) {
                    Text(if (s.items.isEmpty()) tr("Iniciar diagnóstico") else tr("Repetir diagnóstico"))
                }
            }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }

            if (s.items.isNotEmpty()) {
                val problems = s.items.filter { it.status == DiagStatus.BAD || it.status == DiagStatus.WARN }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Text(
                        if (problems.isEmpty()) tr("Todo se ve bien. Tu conexión funciona correctamente.")
                        else tr("Revisa: ") + problems.joinToString(", ") { it.title.substringBefore(" (") } +
                            tr(". Abajo te explicamos qué significa y qué puedes hacer."),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                s.items.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(diagColor(item.status))
                                Spacer(Modifier.width(8.dp))
                                Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(item.value, style = MaterialTheme.typography.labelLarge, color = diagColor(item.status))
                            }
                            Text(item.explanation, style = MaterialTheme.typography.bodySmall)
                            item.advice?.let {
                                Text(tr("Qué hacer: %1\$s", it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================== Aprende

@Composable
fun LearnScreen(onBack: () -> Unit) {
    ScreenScaffold(title = tr("¿Qué estoy viendo?"), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Toca un tema para ver la explicación completa."), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(Glossary.entries, key = { it.term }) { e ->
                var open by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { open = !open },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.term, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                        }
                        Text(e.short, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        if (open) {
                            Text(
                                e.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
