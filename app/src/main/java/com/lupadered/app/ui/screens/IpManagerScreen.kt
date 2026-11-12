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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lupadered.app.ui.IpRow
import com.lupadered.app.ui.MainViewModel
import com.lupadered.app.ui.Presence
import com.lupadered.app.ui.ScreenScaffold
import com.lupadered.app.ui.components.RadarStatus
import com.lupadered.app.ui.components.RadarSpinner
import com.lupadered.app.ui.components.EmptyState
import com.lupadered.app.ui.components.ExplainDialog
import com.lupadered.app.ui.components.StatusDot
import com.lupadered.app.ui.components.presenceColor
import com.lupadered.app.ui.components.relativeTime
import com.lupadered.app.ui.theme.MonoStyle

private enum class IpFilter(val label: String) {
    ALL(tr("Todas")),
    IN_USE(tr("En uso")),
    KNOWN(tr("Vistas antes")),
    EMPTY(tr("Sin respuesta")),
}

/**
 * IP Manager: todas las direcciones del rango con su estado. Ayuda a saber qué
 * IPs están ocupadas antes de asignar una IP fija a un equipo.
 */
@Composable
fun IpManagerScreen(
    vm: MainViewModel,
    onScan: () -> Unit,
    onOpenDevice: (Long) -> Unit,
    onInspectIp: (String) -> Unit,
) {
    val s by vm.home.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(IpFilter.IN_USE) }
    var help by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = tr("IP Manager"),
        actions = {
            IconButton(onClick = { help = true }) { Icon(Icons.Outlined.Info, contentDescription = tr("Ayuda")) }
            if (s.scanning) {
                RadarSpinner(size = 28.dp, modifier = Modifier.padding(horizontal = 12.dp))
            } else {
                IconButton(onClick = onScan, enabled = s.network != null) {
                    Icon(Icons.Outlined.Refresh, contentDescription = tr("Escanear"))
                }
            }
        },
    ) { padding ->
        if (s.network == null) {
            EmptyState(
                Icons.Outlined.Lan, tr("Sin red local"),
                tr("Conéctate a un WiFi para ver el mapa de direcciones IP."),
                Modifier.padding(padding),
            )
            return@ScreenScaffold
        }
        val rows = vm.ipRows(s)
        val inUse = rows.count { it.presence == Presence.ONLINE }
        val known = rows.count { it.presence == Presence.OFFLINE || it.presence == Presence.CHECKING }
        val empty = rows.count { it.device == null }
        val shown = when (filter) {
            IpFilter.ALL -> rows
            IpFilter.IN_USE -> rows.filter { it.presence == Presence.ONLINE }
            IpFilter.KNOWN -> rows.filter { it.presence == Presence.OFFLINE || it.presence == Presence.CHECKING }
            IpFilter.EMPTY -> rows.filter { it.device == null }
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (s.scanning) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            RadarStatus("${s.phase.label} ${(s.progress * 100).toInt()}%", size = 36.dp)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(tr("En uso"), inUse.toString(), Modifier.weight(1f))
                    Stat(tr("Vistas antes"), known.toString(), Modifier.weight(1f))
                    Stat(tr("Sin respuesta"), empty.toString(), Modifier.weight(1f))
                }
            }
            item {
                Text(
                    tr("Rango %1\$s · %2\$s direcciones", s.network?.scanSubnet?.cidr, rows.size) +
                        (s.lastScan?.let { tr(" · escaneado %1\$s", relativeTime(it.startedAt)) } ?: tr(" · aún sin escanear")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(IpFilter.entries) { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(f.label) },
                        )
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Text(
                        tr("\"Sin respuesta\" no siempre significa \"libre\": un equipo apagado o dormido tampoco responde. Antes de usar una IP fija, elige una que nunca hayamos visto ocupada y mejor aún, resérvala en tu router."),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        when {
                            s.scanning -> tr("Buscando…")
                            filter == IpFilter.IN_USE && s.lastScan == null -> tr("Aún no hay IPs en uso. Toca el botón de escanear (arriba a la derecha).")
                            else -> tr("No hay direcciones en esta categoría.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(shown, key = { it.ip }) { row ->
                IpRowItem(
                    row,
                    onClick = {
                        val id = row.device?.id
                        if (id != null) onOpenDevice(id) else onInspectIp(row.ip)
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }

    if (help) {
        ExplainDialog(
            title = tr("¿Para qué sirve el IP Manager?"),
            text = tr("Muestra TODAS las direcciones de tu red y cuáles están ocupadas.\n\n• En uso: un equipo respondió en el último escaneo.\n• Vistas antes: un equipo la usó antes, pero ahora no respondió (puede estar apagado).\n• Sin respuesta: nunca vimos un equipo ahí.\n\nToca una IP ocupada para ver el dispositivo, o una sin respuesta para analizarla a fondo.\n\nConsejo: si quieres que un equipo (por ejemplo la impresora) tenga siempre la misma IP, configura una \"reserva DHCP\" en tu router. Así no chocará con otros equipos."),
            onDismiss = { help = false },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun IpRowItem(row: IpRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(presenceColor(row.presence))
        Spacer(Modifier.width(12.dp))
        Text(
            row.ip,
            style = MonoStyle,
            fontWeight = if (row.device != null) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(120.dp),
        )
        val d = row.device
        Column(Modifier.weight(1f)) {
            Text(
                d?.title ?: tr("Sin respuesta"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (d == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (d != null && row.presence == Presence.OFFLINE) {
                Text(
                    tr("Visto %1\$s", relativeTime(d.lastSeen)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (d == null) {
            TextButton(onClick = onClick) { Text(tr("Analizar")) }
        }
    }
}
