package com.lupadered.app.ui.components

import com.lupadered.app.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lupadered.app.knowledge.DeviceType
import com.lupadered.app.knowledge.Risk
import com.lupadered.app.net.DiagStatus
import com.lupadered.app.ui.Presence
import com.lupadered.app.ui.theme.StatusColors
import java.text.DateFormat
import java.util.Date

fun DeviceType.icon(): ImageVector = when (this) {
    DeviceType.THIS_PHONE -> Icons.Outlined.Smartphone
    DeviceType.ROUTER -> Icons.Outlined.Router
    DeviceType.PHONE -> Icons.Outlined.PhoneAndroid
    DeviceType.TABLET -> Icons.Outlined.TabletAndroid
    DeviceType.COMPUTER -> Icons.Outlined.Computer
    DeviceType.TV -> Icons.Outlined.Tv
    DeviceType.SPEAKER -> Icons.Outlined.Speaker
    DeviceType.PRINTER -> Icons.Outlined.Print
    DeviceType.CAMERA -> Icons.Outlined.Videocam
    DeviceType.CONSOLE -> Icons.Outlined.SportsEsports
    DeviceType.NAS -> Icons.Outlined.Storage
    DeviceType.IOT -> Icons.Outlined.Lightbulb
    DeviceType.NETWORK -> Icons.Outlined.DeviceHub
    DeviceType.UNKNOWN -> Icons.Outlined.Devices
}

@Composable
fun DeviceAvatar(type: DeviceType, presence: Presence, modifier: Modifier = Modifier, iconKey: String? = null) {
    val online = presence == Presence.ONLINE
    val bg = if (online) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (online) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(DeviceIcons.iconFor(type, iconKey), contentDescription = type.label, tint = fg)
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
fun presenceColor(p: Presence?): Color = when (p) {
    Presence.ONLINE -> StatusColors.good
    Presence.CHECKING -> StatusColors.warn
    Presence.OFFLINE -> StatusColors.idle
    null -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
fun riskColor(r: Risk): Color = when (r) {
    Risk.INFO -> StatusColors.good
    Risk.LOW -> MaterialTheme.colorScheme.tertiary
    Risk.MEDIUM -> StatusColors.warn
    Risk.HIGH -> StatusColors.bad
}

@Composable
fun diagColor(s: DiagStatus): Color = when (s) {
    DiagStatus.GOOD -> StatusColors.good
    DiagStatus.WARN -> StatusColors.warn
    DiagStatus.BAD -> StatusColors.bad
    DiagStatus.UNKNOWN -> StatusColors.idle
}

/** Etiqueta pequeña de color (p. ej. "Nuevo", "Riesgo alto"). */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Una fila "dato: valor" con un botón (i) que explica qué significa.
 * Es la pieza central de "que el usuario entienda lo que está viendo".
 */
@Composable
fun ExplainRow(label: String, value: String, explanation: String?, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .then(if (explanation != null) Modifier.clickable { open = true } else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (explanation != null) {
            IconButton(onClick = { open = true }) {
                Icon(Icons.Outlined.Info, contentDescription = tr("¿Qué significa?"), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (open && explanation != null) {
        ExplainDialog(title = label, text = explanation, onDismiss = { open = false })
    }
}

@Composable
fun ExplainDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Entendido")) } },
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

// ------------------------------------------------------------------ formatos

fun relativeTime(ts: Long?, now: Long = System.currentTimeMillis()): String {
    if (ts == null || ts <= 0) return tr("nunca")
    val diff = (now - ts).coerceAtLeast(0)
    val min = diff / 60_000
    return when {
        min < 1 -> tr("hace un momento")
        min < 60 -> tr("hace %1\$s min", min)
        min < 24 * 60 -> tr("hace %1\$s h", min / 60)
        min < 48 * 60 -> tr("ayer")
        min < 7 * 24 * 60 -> tr("hace %1\$s días", min / (24 * 60))
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))
    }
}

fun dateTime(ts: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))

fun latencyText(ms: Long?): String = when {
    ms == null -> "—"
    ms < 1 -> tr("<1 ms")
    else -> "$ms ms"
}

/** Explicación de una latencia concreta, para el botón (i). */
fun latencyExplanation(ms: Long?): String {
    val base = tr("Es lo que tardó el equipo en responder, en milésimas de segundo (ms). ")
    return base + when {
        ms == null -> tr("No pudimos medirlo: el equipo se detectó porque se anunció solo, sin responder a nuestras pruebas.")
        ms < 10 -> tr("Menos de 10 ms es excelente dentro de casa.")
        ms < 50 -> tr("Es un valor normal.")
        ms < 200 -> tr("Es algo lento. Puede estar lejos del router, con señal débil o en reposo (los celulares tardan en despertar).")
        else -> tr("Es muy lento. Puede estar en reposo, muy lejos del router o con la red saturada.")
    }
}
