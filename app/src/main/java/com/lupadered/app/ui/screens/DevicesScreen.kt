package com.lupadered.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lupadered.app.i18n.tr
import com.lupadered.app.ui.DeviceItem
import com.lupadered.app.ui.MainViewModel
import com.lupadered.app.ui.Presence
import com.lupadered.app.ui.ScreenScaffold
import com.lupadered.app.ui.components.EmptyState
import com.lupadered.app.ui.components.RadarSpinner
import com.lupadered.app.ui.components.SectionHeader
import com.lupadered.app.ui.components.rememberIsPremium

/** Filtros rápidos de la lista de dispositivos. */
private enum class DeviceFilter { ALL, ONLINE, UNREVIEWED, OFFLINE }

/**
 * Pestaña "Dispositivos": la lista completa de la red, con búsqueda, filtros rápidos,
 * grupos (Premium) y "desliza hacia abajo para escanear".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    vm: MainViewModel,
    onScan: () -> Unit,
    onOpenDevice: (Long) -> Unit,
    onInspectIp: () -> Unit,
    onOpenIps: () -> Unit,
) {
    val s by vm.home.collectAsStateWithLifecycle()
    val isPremium = rememberIsPremium()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(DeviceFilter.ALL) }

    ScreenScaffold(
        title = tr("Dispositivos"),
        actions = {
            IconButton(onClick = onOpenIps) { Icon(Icons.Outlined.Lan, contentDescription = tr("IP Manager")) }
            IconButton(onClick = onInspectIp) {
                Icon(Icons.AutoMirrored.Outlined.ManageSearch, contentDescription = tr("Analizar una IP"))
            }
            if (s.scanning) {
                RadarSpinner(size = 26.dp, modifier = Modifier.padding(horizontal = 12.dp))
            } else {
                IconButton(onClick = onScan, enabled = s.network != null) {
                    Icon(Icons.Outlined.Refresh, contentDescription = tr("Escanear"))
                }
            }
        },
    ) { padding ->
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

        val byGroup = s.selectedGroup?.let { g -> s.devices.filter { it.groupName == g } } ?: s.devices
        val q = query.trim()
        val searched = if (q.isEmpty()) byGroup else byGroup.filter { it.matches(q) }
        val counts = mapOf(
            DeviceFilter.ALL to searched.size,
            DeviceFilter.ONLINE to searched.count { it.presence != Presence.OFFLINE },
            DeviceFilter.UNREVIEWED to searched.count { it.isUnreviewed() },
            DeviceFilter.OFFLINE to searched.count { it.presence == Presence.OFFLINE },
        )
        val shown = when (filter) {
            DeviceFilter.ALL -> searched
            DeviceFilter.ONLINE -> searched.filter { it.presence != Presence.OFFLINE }
            DeviceFilter.UNREVIEWED -> searched.filter { it.isUnreviewed() }
            DeviceFilter.OFFLINE -> searched.filter { it.presence == Presence.OFFLINE }
        }
        val online = shown.filter { it.presence != Presence.OFFLINE }
        val offline = shown.filter { it.presence == Presence.OFFLINE }

        PullToRefreshBox(
            isRefreshing = s.scanning,
            onRefresh = { if (s.network != null) onScan() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                // ---------- Búsqueda
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(40) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        placeholder = { Text(tr("Buscar por nombre, IP o fabricante")) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = tr("Borrar")) }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
                // ---------- Filtros
                item(key = "filters") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        val labels = listOf(
                            DeviceFilter.ALL to tr("Todos"),
                            DeviceFilter.ONLINE to tr("En línea"),
                            DeviceFilter.UNREVIEWED to tr("Sin revisar"),
                            DeviceFilter.OFFLINE to tr("Sin respuesta"),
                        )
                        items(labels, key = { it.first.name }) { (f, label) ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = { Text("$label · ${counts[f] ?: 0}") },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                        // Grupos creados por el usuario (Premium, RF-32)
                        if (s.groups.isNotEmpty() && isPremium) {
                            items(s.groups, key = { "g-$it" }) { g ->
                                FilterChip(
                                    selected = s.selectedGroup == g,
                                    onClick = { vm.selectGroup(if (s.selectedGroup == g) null else g) },
                                    label = { Text(g) },
                                    shape = RoundedCornerShape(50),
                                )
                            }
                        }
                    }
                }

                // ---------- Estados vacíos
                if (s.devices.isEmpty()) {
                    item(key = "empty") {
                        if (s.scanning) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadarSpinner(size = 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(tr("Buscando dispositivos…"), style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            EmptyState(
                                icon = Icons.Outlined.Devices,
                                title = tr("Aún no hay dispositivos"),
                                text = tr("Escanea tu red para ver quién está conectado."),
                                action = {
                                    Button(onClick = onScan, enabled = s.network != null) {
                                        Icon(Icons.Outlined.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(tr("Escanear red"))
                                    }
                                },
                            )
                        }
                    }
                } else if (shown.isEmpty()) {
                    item(key = "no-results") {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = tr("Sin resultados"),
                            text = if (q.isNotEmpty()) tr("Ningún dispositivo coincide con \"%1\$s\".", q)
                            else tr("No hay dispositivos con este filtro."),
                            action = {
                                TextButton(onClick = {
                                    query = ""
                                    filter = DeviceFilter.ALL
                                    vm.selectGroup(null)
                                }) { Text(tr("Ver todos")) }
                            },
                        )
                    }
                }

                // ---------- En línea
                if (online.isNotEmpty()) {
                    item(key = "h-on") {
                        SectionHeader(
                            tr("En línea"),
                            count = online.size,
                        )
                    }
                    groupedItems(online, keyPrefix = "on-", onOpenDevice = onOpenDevice)
                }
                // ---------- Vistos antes
                if (offline.isNotEmpty()) {
                    item(key = "h-off") {
                        SectionHeader(tr("Vistos antes"), count = offline.size)
                        Text(
                            tr("No respondieron en el último escaneo. Pueden estar apagados, dormidos o fuera de casa."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        )
                    }
                    groupedItems(offline, keyPrefix = "off-", onOpenDevice = onOpenDevice)
                }

                if (!s.scanning && s.devices.isNotEmpty()) {
                    item(key = "inspect") {
                        OutlinedButton(
                            onClick = onInspectIp,
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ManageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("¿No aparece un equipo? Analízalo por su IP"))
                        }
                    }
                }
                item(key = "end") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

/**
 * Pinta una lista de equipos como un único bloque redondeado (primera y última fila con
 * esquinas), separados por líneas finas. Cada fila se anima al aparecer o cambiar de lugar.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.groupedItems(
    list: List<DeviceItem>,
    keyPrefix: String,
    onOpenDevice: (Long) -> Unit,
) {
    val last = list.lastIndex
    items(list.size, key = { keyPrefix + (list[it].id?.toString() ?: list[it].ip) }) { i ->
        val d = list[i]
        val r = 20.dp
        val shape: Shape = when {
            last == 0 -> RoundedCornerShape(r)
            i == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
            i == last -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
            else -> RoundedCornerShape(0.dp)
        }
        Box(
            Modifier
                .animateItem()
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            DeviceRow(d, onClick = { d.id?.let(onOpenDevice) }, modifier = Modifier.padding(horizontal = 12.dp))
            if (i < last) {
                HorizontalDivider(
                    Modifier.align(Alignment.BottomStart).padding(start = 68.dp, end = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

private fun DeviceItem.isUnreviewed(): Boolean = !trusted && id != null && presence != Presence.OFFLINE

private fun DeviceItem.matches(q: String): Boolean =
    title.contains(q, ignoreCase = true) ||
        ip.contains(q) ||
        subtitle.contains(q, ignoreCase = true) ||
        type.label.contains(q, ignoreCase = true) ||
        (groupName?.contains(q, ignoreCase = true) == true)
