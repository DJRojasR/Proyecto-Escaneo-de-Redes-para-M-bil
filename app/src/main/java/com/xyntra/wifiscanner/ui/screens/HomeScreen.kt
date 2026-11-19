package com.xyntra.wifiscanner.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyntra.wifiscanner.i18n.tr
import com.xyntra.wifiscanner.net.DiagItem
import com.xyntra.wifiscanner.net.DiagStatus
import com.xyntra.wifiscanner.net.WifiSecurityCheck
import com.xyntra.wifiscanner.ui.DeviceItem
import com.xyntra.wifiscanner.ui.HomeState
import com.xyntra.wifiscanner.ui.MainViewModel
import com.xyntra.wifiscanner.ui.Presence
import com.xyntra.wifiscanner.ui.ScreenScaffold
import com.xyntra.wifiscanner.ui.components.AppCard
import com.xyntra.wifiscanner.ui.components.BannerKind
import com.xyntra.wifiscanner.ui.components.DeviceAvatar
import com.xyntra.wifiscanner.ui.components.EmptyState
import com.xyntra.wifiscanner.ui.components.IconBadge
import com.xyntra.wifiscanner.ui.components.InfoTile
import com.xyntra.wifiscanner.ui.components.RadarSpinner
import com.xyntra.wifiscanner.ui.components.SectionHeader
import com.xyntra.wifiscanner.ui.components.StatusBanner
import com.xyntra.wifiscanner.ui.components.StatusDot
import com.xyntra.wifiscanner.ui.components.Tag
import com.xyntra.wifiscanner.ui.components.diagColor
import com.xyntra.wifiscanner.ui.components.latencyText
import com.xyntra.wifiscanner.ui.components.prefixToMask
import com.xyntra.wifiscanner.ui.components.presenceColor
import com.xyntra.wifiscanner.ui.components.relativeTime
import com.xyntra.wifiscanner.ui.theme.StatusColors
import kotlinx.coroutines.launch

/** Cuántos equipos se muestran como vista previa en Inicio (el resto, en "Dispositivos"). */
private const val PREVIEW_COUNT = 5

/**
 * Inicio: el estado de tu red de un vistazo (a qué red estás conectado, tus direcciones,
 * el rango y cuántos equipos hay), el botón de escaneo y los avisos importantes.
 * La lista completa vive en la pestaña "Dispositivos".
 */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onScan: () -> Unit,
    onOpenDevice: (Long) -> Unit,
    onLearn: () -> Unit,
    onInspectIp: () -> Unit,
    onWifiSecurity: () -> Unit,
    onOpenDevices: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val s by vm.home.collectAsStateWithLifecycle()
    // Avisos breves abajo (cambio de red, escaneo terminado), sin tarjetas grandes
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(s.networkChanged) {
        if (s.networkChanged && s.network != null) {
            val msg =
                if (s.profile == null) tr("Te conectaste a una red nueva. Escanéala para guardarla en \"Mis redes\".")
                else tr("Cambiaste a la red \"%1\$s\". Te mostramos sus equipos.", s.profile?.displayName)
            vm.dismissNetworkChanged()
            snackbar.showSnackbar(msg)
        }
    }
    LaunchedEffect(s.scanFinished, s.lastScan?.id) {
        val last = s.lastScan
        if (s.scanFinished && last != null) {
            vm.dismissScanFinished()
            snackbar.showSnackbar(
                tr("Escaneo completado: %1\$s dispositivos en %2\$s s", last.found, (last.durationMs + 500) / 1000),
            )
        }
    }
    // Revisión automática del "candado" del WiFi (también al volver a esta pantalla)
    LaunchedEffect(Unit) { vm.checkWifiSecurity() }

    val homeContext = LocalContext.current
    // Al tocar "Escanear": si falta el permiso de ubicación lo pedimos ahí mismo, para que la red
    // quede con el nombre de tu WiFi. Si no lo da, se lo explicamos y escaneamos igual.
    val scanScope = rememberCoroutineScope()
    var locationExplained by rememberSaveable { mutableStateOf(false) }
    val scanPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && !locationExplained) {
            locationExplained = true
            scanScope.launch {
                snackbar.showSnackbar(tr("Sin el permiso de ubicación, Android no deja ver el nombre del WiFi."))
            }
        }
        onScan()
    }
    val scanTapped: () -> Unit = {
        if (WifiSecurityCheck.hasLocationPermission(homeContext) || locationExplained) onScan()
        else scanPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
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
        title = tr("XYNTRA"),
        actions = {
            IconButton(onClick = onInspectIp) {
                Icon(Icons.AutoMirrored.Outlined.ManageSearch, contentDescription = tr("Analizar una IP"))
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = tr("Ajustes"))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // ---------- Sin red: explicación clara y una acción útil
        if (s.networkChecked && s.network == null && !s.scanning) {
            EmptyState(
                icon = Icons.Outlined.WifiOff,
                title = tr("No estás conectado a un WiFi"),
                text = tr("Para ver los dispositivos de tu casa, conecta este teléfono a tu red WiFi. Con datos móviles no hay una red local que revisar."),
                modifier = Modifier.padding(padding).padding(top = 24.dp),
                action = { Button(onClick = { vm.refreshNetwork() }) { Text(tr("Reintentar")) } },
            )
            return@ScreenScaffold
        }

        val online = s.devices.filter { it.presence != Presence.OFFLINE }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                NetworkHeroCard(
                    s = s,
                    onRename = { renaming = true },
                    onScan = scanTapped,
                    onCancel = vm::cancelScan,
                )
            }

            // ---------- Estados del último escaneo
            if (s.scanError) {
                item(key = "error") {
                    StatusBanner(
                        kind = BannerKind.ERROR,
                        title = tr("No se pudo completar el escaneo. Intenta de nuevo."),
                        actions = {
                            TextButton(onClick = vm::dismissScanError) { Text(tr("Cerrar")) }
                            TextButton(onClick = { vm.dismissScanError(); scanTapped() }) { Text(tr("Reintentar")) }
                        },
                    )
                }
            }
            if (s.scanCancelled && !s.scanning) {
                item(key = "cancelled") {
                    StatusBanner(
                        kind = BannerKind.INFO,
                        icon = Icons.Outlined.Stop,
                        title = tr("Escaneo detenido"),
                        text = tr("Los resultados parciales no se guardaron. Vuelve a escanear cuando quieras."),
                        actions = {
                            TextButton(onClick = vm::dismissScanCancelled) { Text(tr("Cerrar")) }
                            TextButton(onClick = { vm.dismissScanCancelled(); scanTapped() }) { Text(tr("Escanear")) }
                        },
                    )
                }
            }

            if (s.newDevices.isNotEmpty()) {
                item(key = "new") {
                    NewDevicesCard(
                        s = s,
                        onTrust = vm::trust,
                        onTrustAll = vm::trustAll,
                        onOpen = onOpenDevice,
                        onDismiss = vm::dismissNewDevices,
                    )
                }
            } else if (!s.scanning && s.untrustedOnline > 0) {
                item(key = "untrusted") {
                    StatusBanner(
                        kind = BannerKind.WARNING,
                        icon = Icons.Outlined.GppMaybe,
                        title = tr("Hay %1\$s dispositivo(s) sin revisar en línea. Ábrelos para marcarlos como conocidos.", s.untrustedOnline),
                        onClick = onOpenDevices,
                    )
                }
            }
            if (!s.scanning && s.wifiSecurity.isNotEmpty()) {
                item(key = "wifisec") { WifiSecurityCard(s.wifiSecurity, onClick = onWifiSecurity) }
            }
            if (!s.scanning) {
                item(key = "battery") { BatteryWarningCard() }
            }

            // ---------- Vista previa de dispositivos
            item(key = "devices-header") {
                SectionHeader(
                    title = if (s.scanning) tr("Encontrados (%1\$s)", s.onlineCount) else tr("Dispositivos"),
                    count = if (s.scanning) null else s.devices.size.takeIf { it > 0 },
                    action = if (s.devices.isNotEmpty()) {
                        {
                            TextButton(onClick = onOpenDevices) {
                                Text(tr("Ver todos"))
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    } else null,
                )
            }
            item(key = "devices") {
                DevicePreview(
                    s = s,
                    online = online,
                    onOpenDevice = onOpenDevice,
                    onOpenAll = onOpenDevices,
                )
            }
        }
    }
}

// ============================================================== Tarjeta principal

/**
 * La tarjeta principal: a qué red estás conectado y sus datos clave, con el escaneo
 * integrado. Al escanear, la parte de abajo se transforma en el panel de progreso.
 */
@Composable
private fun NetworkHeroCard(s: HomeState, onRename: () -> Unit, onScan: () -> Unit, onCancel: () -> Unit) {
    val net = s.network
    AppCard(contentPadding = 16.dp) {
        // --- Encabezado: red y estado de la conexión
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                when {
                    net == null -> Icons.Outlined.Wifi
                    net.isWifi -> Icons.Outlined.Wifi
                    else -> Icons.Outlined.SettingsEthernet
                },
                size = 44.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.profile?.displayName ?: net?.ssid ?: tr("Tu red"),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (net != null && s.profile == null) {
                        Spacer(Modifier.width(8.dp))
                        Tag(tr("Red nueva"), StatusColors.warn)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (net == null) {
                        RadarSpinner(size = 14.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Buscando tu red…"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        StatusDot(StatusColors.good, Modifier.size(8.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            buildString {
                                append(if (net.isWifi) tr("Conectado por WiFi") else tr("Conectado por cable"))
                                net.wifiRssi?.let { append(" · ").append(it).append(" dBm") }
                                net.linkSpeedMbps?.let { append(" · ").append(it).append(" Mbps") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (s.profile != null) {
                IconButton(onClick = onRename) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = tr("Cambiar el nombre de la red"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (net != null) {
            Spacer(Modifier.height(16.dp))
            // --- Datos clave en cuadrícula (cada uno con su explicación al tocarlo)
            val total = s.devices.size
            val onlineNow = if (s.scanning) s.onlineCount else s.devices.count { it.presence == Presence.ONLINE }
            TileRow {
                InfoTile(
                    tr("Tu teléfono"), net.selfIp, Modifier.weight(1f), icon = Icons.Outlined.Smartphone,
                    explanation = tr("Es la dirección IP que el router le dio a este teléfono dentro de tu red."),
                )
                InfoTile(
                    tr("Router"), net.gateway ?: tr("No encontrado"), Modifier.weight(1f), icon = Icons.Outlined.Router,
                    explanation = tr("El router es la \"puerta\" de tu casa hacia Internet. Desde esta dirección puedes entrar a su configuración con un navegador."),
                    valueColor = if (net.gateway == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
            }
            TileRow {
                InfoTile(
                    tr("Máscara de subred"), "${prefixToMask(net.prefix)} /${net.prefix}", Modifier.weight(1f), icon = Icons.Outlined.GridView,
                    explanation = tr("Indica qué parte de la dirección IP identifica a tu red y qué parte a cada equipo. Con /24 (255.255.255.0) caben hasta 254 equipos."),
                )
                InfoTile(
                    tr("Rango que revisamos"), net.scanSubnet.cidr, Modifier.weight(1f), icon = Icons.Outlined.Lan,
                    explanation = tr("Tu red usa estas direcciones IP. La app revisa cada una para saber si hay un equipo.") +
                        if (net.reducedRange) tr("\n\nTu red es muy grande (/%1\$s), así que revisamos solo el bloque donde está tu teléfono para que el escaneo sea rápido.", net.prefix)
                        else "",
                )
            }
            TileRow {
                InfoTile(
                    tr("En línea"),
                    if (total > onlineNow && !s.scanning) "$onlineNow / $total" else "$onlineNow",
                    Modifier.weight(1f), icon = Icons.Outlined.Devices,
                    explanation = tr("Equipos que respondieron en el último escaneo, de todos los que esta app conoce en esta red."),
                    valueColor = if (onlineNow > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                InfoTile(
                    tr("Último escaneo"),
                    s.lastScan?.let { relativeTime(it.startedAt) } ?: tr("nunca"),
                    Modifier.weight(1f), icon = Icons.Outlined.Schedule, mono = false,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        // --- Escaneo: botón en reposo, panel de progreso mientras trabaja
        AnimatedContent(
            targetState = s.scanning,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(220)) { it / 6 }) togetherWith fadeOut(tween(120))
            },
            label = "scan",
        ) { scanning ->
            if (scanning) {
                ScanProgressPanel(s, onCancel)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onScan,
                        enabled = net != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(Icons.Outlined.Radar, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Escanear red"), style = MaterialTheme.typography.titleSmall)
                    }
                    val last = s.lastScan
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (last != null) {
                            tr("Último escaneo %1\$s · %2\$s dispositivos · %3\$s s", relativeTime(last.startedAt), last.found, (last.durationMs + 500) / 1000)
                        } else {
                            tr("Escanea tu red para ver quién está conectado.")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TileRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Panel de progreso del escaneo: radar, fase, porcentaje, equipos y la última IP que respondió. */
@Composable
private fun ScanProgressPanel(s: HomeState, onCancel: () -> Unit) {
    val progress by animateFloatAsState(s.progress.coerceIn(0f, 1f), tween(400), label = "progress")
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadarSpinner(size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(targetState = s.phase.label, label = "phase", transitionSpec = { fadeIn() togetherWith fadeOut() }) { label ->
                    Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                }
                Text(
                    tr("%1\$s dispositivos encontrados", s.onlineCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val ip = s.lastFoundIp
                Text(
                    if (ip != null) tr("Última respuesta: %1\$s", ip) else (s.network?.scanSubnet?.cidr ?: ""),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(onClick = onCancel, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Detener"))
            }
        }
        Text(
            tr("Usamos varios métodos a la vez y revisamos dos veces a los que no responden, para no dejar a nadie fuera."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ============================================================== Vista previa

@Composable
private fun DevicePreview(
    s: HomeState,
    online: List<DeviceItem>,
    onOpenDevice: (Long) -> Unit,
    onOpenAll: () -> Unit,
) {
    when {
        s.devices.isEmpty() && s.scanning -> AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadarSpinner(size = 28.dp)
                Spacer(Modifier.width(12.dp))
                Text(tr("Buscando dispositivos…"), style = MaterialTheme.typography.bodyMedium)
            }
        }
        s.devices.isEmpty() -> AppCard(contentPadding = 0.dp) {
            EmptyState(
                icon = Icons.Outlined.Devices,
                title = tr("Aún no hay dispositivos"),
                text = tr("Toca el botón \"Escanear\" de arriba para descubrir quién está conectado a tu WiFi. La app recordará los dispositivos para mostrártelos al instante la próxima vez."),
            )
        }
        else -> {
            // Primero los que están en línea; si no hay, los últimos vistos
            val list = (online.ifEmpty { s.devices }).take(PREVIEW_COUNT)
            val rest = s.devices.size - list.size
            AppCard(contentPadding = 0.dp) {
                Column(Modifier.animateContentSize()) {
                    list.forEachIndexed { i, d ->
                        DeviceRow(
                            d,
                            onClick = { d.id?.let(onOpenDevice) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        if (i < list.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(start = 68.dp, end = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                    if (rest > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        TextButton(
                            onClick = onOpenAll,
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                        ) {
                            Text(tr("Ver todos") + " (${s.devices.size})")
                        }
                    }
                }
            }
        }
    }
}

// ============================================================== Avisos

/**
 * Resumen de la seguridad del WiFi en Inicio: discreto si todo está bien,
 * resaltado si hay algo que arreglar. Al tocarlo se abre la explicación completa.
 */
@Composable
private fun WifiSecurityCard(items: List<DiagItem>, onClick: () -> Unit) {
    val first = items.first()
    if (first.status == DiagStatus.UNKNOWN) return
    val problems = items.filter { it.status == DiagStatus.BAD || it.status == DiagStatus.WARN }
    val worst = when {
        items.any { it.status == DiagStatus.BAD } -> DiagStatus.BAD
        problems.isNotEmpty() -> DiagStatus.WARN
        else -> DiagStatus.GOOD
    }
    val wpsUnknown = items.any { it.status == DiagStatus.UNKNOWN }
    val color = diagColor(worst)
    val title = when (worst) {
        DiagStatus.BAD -> tr("Tu WiFi está desprotegido: %1\$s", first.value)
        DiagStatus.WARN -> tr("Tu WiFi se puede proteger mejor: %1\$s", problems.joinToString(", ") { it.title })
        else -> tr("Tu WiFi está bien protegido (%1\$s)", first.value)
    }
    val subtitle = when {
        worst != DiagStatus.GOOD -> tr("Toca para ver cómo arreglarlo.")
        wpsUnknown -> tr("Toca para revisar también el WPS del router.")
        else -> tr("Toca para ver los detalles.")
    }
    if (worst == DiagStatus.GOOD) {
        // Todo bien: una fila discreta, sin colores llamativos
        AppCard(onClick = onClick, contentPadding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.VerifiedUser, size = 36.dp, tint = color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        StatusBanner(
            kind = if (worst == DiagStatus.BAD) BannerKind.ERROR else BannerKind.WARNING,
            icon = if (worst == DiagStatus.BAD) Icons.Outlined.GppBad else Icons.Outlined.GppMaybe,
            title = title,
            text = subtitle,
            onClick = onClick,
        )
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
    AnimatedVisibility(visible = s.newDevices.isNotEmpty(), enter = fadeIn() + slideInVertically { -it / 4 }) {
        StatusBanner(
            kind = BannerKind.WARNING,
            icon = Icons.Outlined.NewReleases,
            title = if (s.newDevices.size == 1) tr("Se conectó un dispositivo nuevo")
            else tr("Se conectaron %1\$s dispositivos nuevos", s.newDevices.size),
            text = tr("No los habíamos visto antes en esta red. Si son tuyos, márcalos como conocidos. Si no los reconoces, revisa quién tiene tu clave WiFi."),
            actions = {
                Column(Modifier.fillMaxWidth()) {
                    s.newDevices.take(5).forEach { d ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(d.id) }
                                .padding(start = 32.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(d.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    d.ip,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onTrust(d.id) }) { Text(tr("Es mío")) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(tr("Más tarde")) }
                        if (s.newDevices.size > 1) {
                            OutlinedButton(onClick = onTrustAll) { Text(tr("Todos son míos")) }
                        }
                    }
                }
            },
        )
    }
}

// ============================================================== Fila de dispositivo

/**
 * Una fila de la lista de dispositivos: ícono con su estado, nombre, IP y fabricante
 * (si se conoce) y, a la derecha, el tiempo de respuesta o cuándo se vio por última vez.
 */
@Composable
fun DeviceRow(d: DeviceItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = d.id != null, onClick = onClick)
            .then(modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceAvatar(d.type, d.presence, iconKey = d.customIcon, showPresence = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (d.presence == Presence.OFFLINE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (d.isNew && d.presence == Presence.ONLINE) {
                    Spacer(Modifier.width(6.dp))
                    Tag(if (d.id == null) tr("Nuevo") else tr("Sin revisar"), StatusColors.warn)
                }
            }
            Text(
                d.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                when (d.presence) {
                    Presence.ONLINE -> latencyText(d.latencyMs)
                    Presence.CHECKING -> tr("Buscando…")
                    Presence.OFFLINE -> relativeTime(d.lastSeen)
                },
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = if (d.presence == Presence.ONLINE) presenceColor(d.presence) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (d.id != null) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 2.dp).size(18.dp),
            )
        }
    }
}

@Composable
fun NetworkNameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Nombre de la red")) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                singleLine = true,
                placeholder = { Text(tr("Ej. Casa, Router 2")) },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(tr("Guardar")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } },
    )
}


/**
 * Aviso cuando el ahorro de batería del teléfono está frenando los escaneos
 * automáticos de la vigilancia. Se revisa sola al entrar y al volver a la pantalla.
 * Si no hay problema (o la vigilancia está apagada), no muestra nada.
 */
@Composable
internal fun BatteryWarningCard() {
    val context = LocalContext.current
    val app = context.applicationContext as com.xyntra.wifiscanner.XyntraApp
    val monitor by app.settings.monitor.collectAsStateWithLifecycle()
    var refresh by remember { mutableStateOf(0) }

    // Vuelve a comprobar cada vez que el usuario regresa a esta pantalla
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val status = remember(monitor, refresh) {
        com.xyntra.wifiscanner.monitor.BatteryGuard.check(context, monitor, app.settings.lastAutoScanAt)
    } ?: return

    var showHow by remember { mutableStateOf(false) }
    StatusBanner(
        kind = BannerKind.WARNING,
        icon = Icons.Outlined.BatteryAlert,
        title = tr("La batería está frenando la vigilancia"),
        text = tr("Tu teléfono está frenando los escaneos automáticos para ahorrar batería. Por eso quizá no te avisemos cuando se conecte un equipo nuevo.") +
            if (showHow) "\n\n" + com.xyntra.wifiscanner.monitor.BatteryGuard.brandSteps() else "",
        actions = {
            TextButton(onClick = { showHow = !showHow }) {
                Text(if (showHow) tr("Ocultar pasos") else tr("¿Cómo?"))
            }
            Button(onClick = { com.xyntra.wifiscanner.monitor.BatteryGuard.openBatterySettings(context) }) {
                Text(tr("Arreglarlo"))
            }
        },
    )
}
