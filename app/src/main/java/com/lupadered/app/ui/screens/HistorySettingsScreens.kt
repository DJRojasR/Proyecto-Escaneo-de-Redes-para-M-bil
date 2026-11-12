package com.lupadered.app.ui.screens

import com.lupadered.app.i18n.tr
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.outlined.Language
import com.lupadered.app.i18n.I18n
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

    ScreenScaffold(title = tr("Historial")) { padding ->
        if (h.events.isEmpty() && h.scans.isEmpty()) {
            EmptyState(
                Icons.Outlined.History,
                tr("Todavía no hay historial"),
                tr("Cada vez que escanees, guardaremos qué dispositivos aparecieron, se fueron, volvieron o cambiaron de IP."),
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
                        title = tr("Historial de más de %1\$s días", AppSettings.FREE_HISTORY_DAYS),
                        text = tr("Tienes %1\$s cambios y %2\$s escaneos más antiguos guardados. Con Premium ves todo el historial y la línea de tiempo de cada equipo.", h.hiddenEvents, h.hiddenScans),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (h.events.isNotEmpty()) {
                item { SectionTitle(tr("Cambios en tu red")) }
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
                        tr("Aún no hay cambios. Después del primer escaneo, aquí verás cuándo un equipo aparece, se va o cambia de IP."),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            if (h.scans.isNotEmpty()) {
                item { SectionTitle(tr("Escaneos")) }
                items(h.scans, key = { "s" + it.id }) { sc ->
                    ListItem(
                        headlineContent = { Text(tr("%1\$s dispositivos", sc.found) + if (sc.newCount > 0) tr(" · %1\$s nuevos", sc.newCount) else "") },
                        supportingContent = {
                            Text(
                                tr("%1\$s · %2\$s s · %3\$s", dateTime(sc.startedAt), (sc.durationMs + 500) / 1000, sc.subnet) +
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
    var choosingLanguage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val p by premium.state.collectAsStateWithLifecycle()
    val appearance by settings.appearance.collectAsStateWithLifecycle()

    ScreenScaffold(title = tr("Ajustes y privacidad"), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle(tr("Premium"), Modifier.padding(horizontal = 16.dp))
            ListItem(
                modifier = Modifier.clickable(onClick = onPremium),
                headlineContent = { Text(if (p.isPremium) tr("Tienes Lupa de Red Premium") else tr("Hazte Premium")) },
                supportingContent = {
                    Text(
                        when (p.source) {
                            PremiumSource.LIFETIME -> tr("Pago único: Premium para siempre.")
                            PremiumSource.SUBSCRIPTION -> tr("Suscripción mensual activa. Toca para administrarla.")
                            PremiumSource.TEST -> tr("Premium de prueba (versión de desarrollo).")
                            null -> tr("Sin anuncios, vigilancia, alerta de intrusos y más.")
                        },
                    )
                },
                leadingContent = { Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = PremiumGold) },
            )
            ListItem(
                modifier = Modifier.clickable(onClick = onMonitor),
                headlineContent = { Text(tr("Vigilancia de la red")) },
                supportingContent = { Text(tr("Escaneos automáticos y alerta de intrusos.")) },
            )
            if (BuildConfig.DEBUG) {
                ListItem(
                    headlineContent = { Text(tr("Simular Premium (solo pruebas)")) },
                    supportingContent = {
                        Text(tr("Solo aparece en la versión de desarrollo. Sirve para probar las funciones Premium sin pagar."))
                    },
                    trailingContent = {
                        Switch(checked = premium.testPremiumEnabled, onCheckedChange = { premium.setTestPremium(it) })
                    },
                )
            }

            SectionTitle(tr("Idioma"), Modifier.padding(horizontal = 16.dp))
            val languageChoice = remember { I18n.selected(context) }
            ListItem(
                modifier = Modifier.clickable { choosingLanguage = true },
                headlineContent = {
                    Text(
                        if (languageChoice == I18n.AUTO) tr("Automático (idioma del teléfono)")
                        else I18n.nameOf(languageChoice),
                    )
                },
                supportingContent = { Text(tr("Ahora: %1\$s. Toca para cambiarlo.", I18n.nameOf(I18n.current))) },
                leadingContent = { Icon(Icons.Outlined.Language, contentDescription = null) },
            )

            SectionTitle(tr("Apariencia"), Modifier.padding(horizontal = 16.dp))
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
                    val selected = appearance.color == c
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
                                settings.setAppearance(appearance.copy(color = c))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                    }
                }
            }

            SectionTitle(tr("Privacidad"), Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(tr("Tus datos se quedan en tu teléfono")) },
                supportingContent = {
                    Text(
                        tr("Los dispositivos, nombres e historial se guardan solo en este teléfono. La app no envía la información de tu red a ningún servidor."),
                    )
                },
            )
            ListItem(
                modifier = Modifier.clickable { runCatching { uri.openUri(BuildConfig.PRIVACY_POLICY_URL) } },
                headlineContent = { Text(tr("Política de privacidad")) },
                supportingContent = { Text(tr("Abre la política completa en el navegador.")) },
            )
            if (ads.privacyOptionsRequired && activity != null) {
                ListItem(
                    modifier = Modifier.clickable { ads.showPrivacyOptions(activity) },
                    headlineContent = { Text(tr("Opciones de privacidad de anuncios")) },
                    supportingContent = { Text(tr("Cambia tu consentimiento para anuncios personalizados.")) },
                )
            }

            SectionTitle(tr("Anuncios"), Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(if (p.isPremium) tr("Sin anuncios") else tr("Sin anuncios que interrumpan")) },
                supportingContent = {
                    Text(
                        if (p.isPremium) tr("Con Premium no verás ningún anuncio.")
                        else tr("Tus primeros %1\$s escaneos no tienen anuncios. Después, como máximo uno cada %2\$s escaneos, y el escaneo sigue trabajando detrás del anuncio para que no esperes de más.", AdsManager.SCANS_PER_AD, AdsManager.SCANS_PER_AD),
                    )
                },
            )

            SectionTitle(tr("Datos"), Modifier.padding(horizontal = 16.dp))
            ListItem(
                modifier = Modifier.clickable { vm.trustAll() },
                headlineContent = { Text(tr("Marcar todos los dispositivos como conocidos")) },
                supportingContent = { Text(tr("Útil después de revisar tu red por primera vez.")) },
            )
            ListItem(
                modifier = Modifier.clickable { confirmClear = true },
                headlineContent = { Text(tr("Borrar todos los datos"), color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text(tr("Elimina dispositivos, nombres personalizados e historial.")) },
            )

            SectionTitle(tr("Acerca de"), Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(tr("Lupa de Red %1\$s", BuildConfig.VERSION_NAME)) },
                supportingContent = {
                    Text(tr("Escanea solo redes en las que tengas permiso: tu casa o una red que administres."))
                },
            )
        }
    }

    if (choosingLanguage) {
        val current = remember { I18n.selected(context) }
        AlertDialog(
            onDismissRequest = { choosingLanguage = false },
            title = { Text(tr("Idioma")) },
            text = {
                Column {
                    Text(
                        tr("La app se reiniciará para aplicar el idioma."),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        item {
                            LanguageRow(
                                tr("Automático (idioma del teléfono)"),
                                current == I18n.AUTO,
                            ) {
                                choosingLanguage = false
                                if (current != I18n.AUTO) activity?.let { I18n.selectAndRestart(it, I18n.AUTO) }
                            }
                        }
                        items(I18n.languages) { lang ->
                            LanguageRow(lang.nativeName, current == lang.code) {
                                choosingLanguage = false
                                if (current != lang.code) activity?.let { I18n.selectAndRestart(it, lang.code) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingLanguage = false }) { Text(tr("Cancelar")) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(tr("¿Borrar todos los datos?")) },
            text = { Text(tr("Se eliminarán los dispositivos guardados, sus nombres personalizados y todo el historial. No se puede deshacer.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearAllData()
                }) { Text(tr("Borrar")) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Cancelar")) } },
        )
    }
}


@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
