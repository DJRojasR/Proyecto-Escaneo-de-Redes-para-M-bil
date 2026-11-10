package com.lupadered.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lupadered.app.BuildConfig
import com.lupadered.app.ads.AdsManager
import com.lupadered.app.data.AppSettings
import com.lupadered.app.data.DarkMode
import com.lupadered.app.data.ThemeColor
import com.lupadered.app.premium.PremiumManager
import com.lupadered.app.premium.PremiumSource
import com.lupadered.app.ui.components.PremiumGold
import com.lupadered.app.ui.components.PremiumTag
import com.lupadered.app.ui.theme.swatch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.lupadered.app.data.EventType
import com.lupadered.app.data.ScanSource
import com.lupadered.app.ui.components.LockedCard
import com.lupadered.app.ui.components.rememberIsPremium
import com.lupadered.app.ui.MainViewModel
import com.lupadered.app.ui.ScreenScaffold
import com.lupadered.app.ui.components.EmptyState
import com.lupadered.app.ui.components.SectionTitle
import com.lupadered.app.ui.components.dateTime
import com.lupadered.app.ui.components.relativeTime
import com.lupadered.app.ui.theme.StatusColors

// ============================================================== Historial

private fun EventType.icon(): ImageVector = when (this) {
    EventType.NEW -> Icons.Outlined.AddCircleOutline
    EventType.BACK -> Icons.AutoMirrored.Outlined.Login
    EventType.LEFT -> Icons.AutoMirrored.Outlined.Logout
    EventType.IP_CHANGED -> Icons.Outlined.SwapHoriz
}

@Composable
fun HistoryScreen(vm: MainViewModel, onOpenDevice: (Long) -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val h by vm.history.collectAsStateWithLifecycle()
    val isPremium = rememberIsPremium()
    LaunchedEffect(home.network?.key, home.lastScan?.id, isPremium) { vm.loadHistory() }

    ScreenScaffold(title = "Historial") { padding ->
        if (h.events.isEmpty() && h.scans.isEmpty()) {
            EmptyState(
                Icons.Outlined.History,
                "Todavía no hay historial",
                "Cada vez que escanees, guardaremos qué dispositivos aparecieron, se fueron, volvieron o cambiaron de IP.",
                Modifier.padding(padding),
            )
            return@ScreenScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (h.hiddenEvents > 0 || h.hiddenScans > 0) {
                item {
                    LockedCard(
                        title = "Historial de más de ${AppSettings.FREE_HISTORY_DAYS} días",
                        text = "Tienes ${h.hiddenEvents} cambios y ${h.hiddenScans} escaneos más antiguos guardados. " +
                            "Con Premium ves todo el historial y la línea de tiempo de cada equipo.",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (h.events.isNotEmpty()) {
                item { SectionTitle("Cambios en tu red") }
                items(h.events, key = { "e" + it.first.id }) { (e, name) ->
                    val color = when (e.type) {
                        EventType.NEW -> StatusColors.warn
                        EventType.BACK -> StatusColors.good
                        EventType.LEFT -> StatusColors.idle
                        EventType.IP_CHANGED -> MaterialTheme.colorScheme.tertiary
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenDevice(e.deviceId) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(e.type.icon(), contentDescription = null, tint = color)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                e.type.label + " · " + (if (e.detail != null) "${e.detail} → ${e.ip}" else e.ip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(relativeTime(e.at), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                item {
                    Text(
                        "Aún no hay cambios. Después del primer escaneo, aquí verás cuándo un equipo aparece, se va o cambia de IP.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            if (h.scans.isNotEmpty()) {
                item { SectionTitle("Escaneos") }
                items(h.scans, key = { "s" + it.id }) { sc ->
                    ListItem(
                        headlineContent = { Text("${sc.found} dispositivos" + if (sc.newCount > 0) " · ${sc.newCount} nuevos" else "") },
                        supportingContent = {
                            Text(
                                "${dateTime(sc.startedAt)} · ${(sc.durationMs + 500) / 1000} s · ${sc.subnet}" +
                                    if (sc.source != ScanSource.MANUAL) " · ${sc.source.label}" else "",
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

// ============================================================== Ajustes

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    ads: AdsManager,
    premium: PremiumManager,
    settings: AppSettings,
    activity: Activity?,
    onPremium: () -> Unit,
    onMonitor: () -> Unit,
    onBack: () -> Unit,
) {
    val uri = LocalUriHandler.current
    var confirmClear by remember { mutableStateOf(false) }
    val p by premium.state.collectAsStateWithLifecycle()
    val appearance by settings.appearance.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Ajustes y privacidad", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle("Premium", Modifier.padding(horizontal = 16.dp))
            ListItem(
                modifier = Modifier.clickable(onClick = onPremium),
                headlineContent = { Text(if (p.isPremium) "Tienes Lupa de Red Premium" else "Hazte Premium") },
                supportingContent = {
                    Text(
                        when (p.source) {
                            PremiumSource.LIFETIME -> "Pago único: Premium para siempre."
                            PremiumSource.SUBSCRIPTION -> "Suscripción mensual activa. Toca para administrarla."
                            PremiumSource.TEST -> "Premium de prueba (versión de desarrollo)."
                            null -> "Sin anuncios, vigilancia, alerta de intrusos y más."
                        },
                    )
                },
                leadingContent = { Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = PremiumGold) },
            )
            ListItem(
                modifier = Modifier.clickable(onClick = onMonitor),
                headlineContent = { Text("Vigilancia de la red") },
                supportingContent = { Text("Escaneos automáticos y alerta de intrusos.") },
            )
            if (BuildConfig.DEBUG) {
                ListItem(
                    headlineContent = { Text("Simular Premium (solo pruebas)") },
                    supportingContent = {
                        Text("Solo aparece en la versión de desarrollo. Sirve para probar las funciones Premium sin pagar.")
                    },
                    trailingContent = {
                        Switch(checked = premium.testPremiumEnabled, onCheckedChange = { premium.setTestPremium(it) })
                    },
                )
            }

            SectionTitle("Apariencia", Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DarkMode.entries.forEach { mode ->
                    FilterChip(
                        selected = appearance.dark == mode,
                        onClick = { settings.setAppearance(appearance.copy(dark = mode)) },
                        label = { Text(mode.label) },
                    )
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemeColor.entries.forEach { c ->
                    val locked = c != ThemeColor.TEAL && !p.isPremium
                    val selected = (if (p.isPremium) appearance.color else ThemeColor.TEAL) == c
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c.swatch())
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable {
                                if (locked) onPremium() else settings.setAppearance(appearance.copy(color = c))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (locked) Icon(Icons.Outlined.Lock, contentDescription = "Premium", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                if (!p.isPremium) PremiumTag()
            }

            SectionTitle("Privacidad", Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("Tus datos se quedan en tu teléfono") },
                supportingContent = {
                    Text(
                        "Los dispositivos, nombres e historial se guardan solo en este teléfono. " +
                            "La app no envía la información de tu red a ningún servidor.",
                    )
                },
            )
            ListItem(
                modifier = Modifier.clickable { runCatching { uri.openUri(BuildConfig.PRIVACY_POLICY_URL) } },
                headlineContent = { Text("Política de privacidad") },
                supportingContent = { Text("Abre la política completa en el navegador.") },
            )
            if (ads.privacyOptionsRequired && activity != null) {
                ListItem(
                    modifier = Modifier.clickable { ads.showPrivacyOptions(activity) },
                    headlineContent = { Text("Opciones de privacidad de anuncios") },
                    supportingContent = { Text("Cambia tu consentimiento para anuncios personalizados.") },
                )
            }

            SectionTitle("Anuncios", Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(if (p.isPremium) "Sin anuncios" else "Sin anuncios que interrumpan") },
                supportingContent = {
                    Text(
                        if (p.isPremium) "Con Premium no verás ningún anuncio."
                        else "Tus primeros ${AdsManager.SCANS_PER_AD} escaneos no tienen anuncios. Después, como máximo uno cada " +
                            "${AdsManager.SCANS_PER_AD} escaneos, y el escaneo sigue trabajando detrás del anuncio para que no esperes de más.",
                    )
                },
            )

            SectionTitle("Datos", Modifier.padding(horizontal = 16.dp))
            ListItem(
                modifier = Modifier.clickable { vm.trustAll() },
                headlineContent = { Text("Marcar todos los dispositivos como conocidos") },
                supportingContent = { Text("Útil después de revisar tu red por primera vez.") },
            )
            ListItem(
                modifier = Modifier.clickable { confirmClear = true },
                headlineContent = { Text("Borrar todos los datos", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Elimina dispositivos, nombres personalizados e historial.") },
            )

            SectionTitle("Acerca de", Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("Lupa de Red ${BuildConfig.VERSION_NAME}") },
                supportingContent = {
                    Text("Escanea solo redes en las que tengas permiso: tu casa o una red que administres.")
                },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todos los datos?") },
            text = { Text("Se eliminarán los dispositivos guardados, sus nombres personalizados y todo el historial. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearAllData()
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}
