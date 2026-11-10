package com.lupadered.app.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lupadered.app.LupaApp
import com.lupadered.app.data.AppSettings
import com.lupadered.app.data.MonitorSettings
import com.lupadered.app.monitor.MonitorScheduler
import com.lupadered.app.monitor.Notifier
import com.lupadered.app.net.NetworkInfoProvider
import com.lupadered.app.ui.ScreenScaffold
import com.lupadered.app.ui.components.LockedCard
import com.lupadered.app.ui.components.SectionTitle
import com.lupadered.app.ui.components.rememberIsPremium
import com.lupadered.app.ui.theme.StatusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Vigilancia de la red: escaneo automático, alerta de intrusos y escaneo diario (RF-24 a RF-26). */
@Composable
fun MonitorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LupaApp
    val isPremium = rememberIsPremium()
    val m by app.settings.monitor.collectAsStateWithLifecycle()

    var currentKey by remember { mutableStateOf<String?>(null) }
    var currentName by remember { mutableStateOf<String?>(null) }
    var watchedName by remember { mutableStateOf<String?>(null) }
    var canNotify by remember { mutableStateOf(Notifier.canNotify(context)) }

    LaunchedEffect(m.networkKey) {
        withContext(Dispatchers.IO) {
            val net = NetworkInfoProvider.current(app)
            currentKey = net?.key
            currentName = net?.let { app.repository.network(it.key)?.displayName ?: "Red ${it.scanSubnet.cidr}" }
            watchedName = m.networkKey?.let { k -> app.repository.network(k)?.displayName }
        }
    }

    fun save(new: MonitorSettings) {
        app.settings.setMonitor(new)
        MonitorScheduler.apply(app, new, isPremium, reschedule = true)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted && Notifier.canNotify(context)
    }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Activa algo de la vigilancia: si aún no hay red elegida, se vigila la actual. */
    fun enable(transform: (MonitorSettings) -> MonitorSettings) {
        val base = if (m.networkKey == null) m.copy(networkKey = currentKey) else m
        save(transform(base))
        if (base.notifyIntruders) ensureNotificationPermission()
    }

    ScreenScaffold(title = "Vigilancia de la red", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Lupa de Red puede revisar tu red sola, aunque no abras la app, y avisarte si se conecta alguien que no conoces.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (!isPremium) {
                LockedCard(
                    title = "Vigilancia y alerta de intrusos",
                    text = "Escaneos automáticos cada 15, 30 o 60 minutos, un escaneo diario a la hora que elijas " +
                        "y una notificación cuando aparece un equipo desconocido.",
                    modifier = Modifier.padding(top = 12.dp),
                )
                return@Column
            }

            // ---------- Red vigilada
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Red vigilada", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        when {
                            m.networkKey == null -> "Aún ninguna. Al activar la vigilancia usaremos la red a la que estás conectado."
                            m.networkKey == currentKey -> "${watchedName ?: currentName ?: "Tu red"} (estás conectado a ella)"
                            else -> "${watchedName ?: "Otra red"}. Ahora estás en otra red: los escaneos automáticos " +
                                "esperarán a que vuelvas a ella."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (currentKey != null && m.networkKey != null && m.networkKey != currentKey) {
                        Button(onClick = { save(m.copy(networkKey = currentKey)) }) {
                            Text("Vigilar la red actual" + (currentName?.let { " ($it)" } ?: ""))
                        }
                    }
                    if (currentKey == null && m.networkKey == null) {
                        Text(
                            "Conéctate a tu WiFi para elegir la red que quieres vigilar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusColors.warn,
                        )
                    }
                }
            }

            // ---------- Escaneo automático
            SectionTitle("Escaneo automático")
            ListItem(
                headlineContent = { Text("Revisar mi red automáticamente") },
                supportingContent = { Text("Cada ${m.intervalMinutes} minutos, solo cuando estás conectado a la red vigilada.") },
                trailingContent = {
                    Switch(
                        checked = m.autoEnabled,
                        enabled = m.networkKey != null || currentKey != null,
                        onCheckedChange = { on -> if (on) enable { it.copy(autoEnabled = true) } else save(m.copy(autoEnabled = false)) },
                    )
                },
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSettings.INTERVALS.forEach { min ->
                    FilterChip(
                        selected = m.intervalMinutes == min,
                        onClick = { save(m.copy(intervalMinutes = min)) },
                        label = { Text(if (min == 60) "1 hora" else "$min min") },
                    )
                }
            }

            // ---------- Escaneo programado
            SectionTitle("Escaneo diario")
            ListItem(
                modifier = Modifier.clickable {
                    TimePickerDialog(context, { _, h, min ->
                        save(m.copy(dailyHour = h, dailyMinute = min))
                    }, m.dailyHour, m.dailyMinute, true).show()
                },
                headlineContent = { Text("Escanear todos los días a las %02d:%02d".format(m.dailyHour, m.dailyMinute)) },
                supportingContent = { Text("Toca para cambiar la hora. Útil para revisar la red de noche, cuando todos duermen.") },
                trailingContent = {
                    Switch(
                        checked = m.dailyEnabled,
                        enabled = m.networkKey != null || currentKey != null,
                        onCheckedChange = { on -> if (on) enable { it.copy(dailyEnabled = true) } else save(m.copy(dailyEnabled = false)) },
                    )
                },
            )

            // ---------- Alertas
            SectionTitle("Alerta de intrusos")
            ListItem(
                headlineContent = { Text("Avisarme si se conecta un equipo desconocido") },
                supportingContent = {
                    Text(
                        if (m.notifyIntruders && !canNotify)
                            "Las notificaciones están desactivadas. Toca el interruptor de nuevo o actívalas en los ajustes del teléfono."
                        else "Recibirás una notificación con el nombre y la IP del equipo nuevo.",
                    )
                },
                trailingContent = {
                    Switch(
                        checked = m.notifyIntruders,
                        onCheckedChange = { on ->
                            save(m.copy(notifyIntruders = on))
                            if (on) ensureNotificationPermission()
                        },
                    )
                },
            )

            Text(
                "Para ahorrar batería, Android puede retrasar un poco los escaneos automáticos y no los hace " +
                    "con la batería baja. Cada escaneo dura unos segundos y usa muy pocos datos (todo queda dentro de tu red).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
