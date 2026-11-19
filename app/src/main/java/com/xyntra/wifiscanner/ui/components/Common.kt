package com.xyntra.wifiscanner.ui.components

import com.xyntra.wifiscanner.i18n.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xyntra.wifiscanner.knowledge.DeviceType
import com.xyntra.wifiscanner.knowledge.Risk
import com.xyntra.wifiscanner.net.DiagStatus
import com.xyntra.wifiscanner.ui.Presence
import com.xyntra.wifiscanner.ui.theme.LocalIsDark
import com.xyntra.wifiscanner.ui.theme.StatusColors
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

/**
 * Ícono del equipo en un cuadro redondeado. Con [showPresence] se le pone un punto de
 * estado en la esquina (en línea / buscando / sin respuesta), como un indicador de LED.
 */
@Composable
fun DeviceAvatar(
    type: DeviceType,
    presence: Presence,
    modifier: Modifier = Modifier,
    iconKey: String? = null,
    size: Dp = 44.dp,
    showPresence: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val online = presence != Presence.OFFLINE
    val bg = when {
        !online -> cs.surfaceContainerHigh
        type == DeviceType.ROUTER || type == DeviceType.NETWORK -> cs.primary.copy(alpha = if (LocalIsDark.current) 0.22f else 0.14f)
        type == DeviceType.THIS_PHONE -> cs.tertiary.copy(alpha = if (LocalIsDark.current) 0.24f else 0.13f)
        else -> cs.primaryContainer.copy(alpha = if (LocalIsDark.current) 0.55f else 0.7f)
    }
    val fg = when {
        !online -> cs.outline
        type == DeviceType.THIS_PHONE -> cs.tertiary
        type == DeviceType.ROUTER || type == DeviceType.NETWORK -> cs.primary
        else -> cs.onPrimaryContainer
    }
    Box(modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.3f))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                DeviceIcons.iconFor(type, iconKey),
                contentDescription = type.label,
                tint = fg,
                modifier = Modifier.size(size * 0.52f),
            )
        }
        if (showPresence) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(presenceColor(presence)),
            )
        }
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
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = if (LocalIsDark.current) 0.18f else 0.12f),
        contentColor = color,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** Etiqueta neutra (gris) para datos secundarios: métodos de detección, servicios… */
@Composable
fun NeutralTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 22.dp, bottom = 8.dp, start = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Título de sección con un contador y una acción opcional a la derecha ("Ver todos"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

/**
 * Tarjeta base de la app: superficie plana, sin sombra y con un borde muy sutil.
 * Se usa en todas las pantallas para que los bloques se vean iguales.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    border: Boolean = true,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, spring(stiffness = 700f), label = "press")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.large,
        color = color,
        border = if (border) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)) else null,
    ) {
        Column(
            Modifier
                .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = ripple(), onClick = onClick) else Modifier)
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Tipo de mensaje de [StatusBanner]. */
enum class BannerKind { INFO, SUCCESS, WARNING, ERROR }

/**
 * Mensaje de estado uniforme (éxito, aviso, error o información) con ícono,
 * texto y acciones opcionales. Reemplaza las tarjetas de colores sueltas.
 */
@Composable
fun StatusBanner(
    kind: BannerKind,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val color = when (kind) {
        BannerKind.INFO -> MaterialTheme.colorScheme.primary
        BannerKind.SUCCESS -> StatusColors.good
        BannerKind.WARNING -> StatusColors.warn
        BannerKind.ERROR -> StatusColors.bad
    }
    val ic = icon ?: when (kind) {
        BannerKind.INFO -> Icons.Outlined.Info
        BannerKind.SUCCESS -> Icons.Outlined.CheckCircle
        BannerKind.WARNING -> Icons.Outlined.WarningAmber
        BannerKind.ERROR -> Icons.Outlined.ErrorOutline
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = color.copy(alpha = if (LocalIsDark.current) 0.14f else 0.09f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
    ) {
        Column(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(ic, contentDescription = null, tint = color, modifier = Modifier.padding(top = 1.dp).size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    if (text != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (actions != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) { actions() }
            }
        }
    }
}

/**
 * Un dato técnico compacto (etiqueta arriba, valor abajo) para las cuadrículas de
 * información. Si tiene [explanation], al tocarlo se abre su explicación.
 */
@Composable
fun InfoTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    explanation: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    mono: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (explanation != null) Modifier.clickable { open = true } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = if (mono) MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum")
            else MaterialTheme.typography.titleSmall,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (open && explanation != null) {
        ExplainDialog(title = label, text = explanation, onDismiss = { open = false })
    }
}

/**
 * Sección que se abre y se cierra con una animación suave. Recuerda su estado al
 * rotar la pantalla.
 */
@Composable
fun ExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    initiallyExpanded: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    AppCard(modifier = modifier.padding(top = 12.dp), contentPadding = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconBadge(icon, size = 32.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (summary != null && !expanded) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), content = content)
        }
    }
}

/** Ícono dentro de un cuadrito tintado (para encabezados de sección y herramientas). */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(tint.copy(alpha = if (LocalIsDark.current) 0.18f else 0.11f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * Una fila "dato: valor" con un botón (i) que explica qué significa.
 * Es la pieza central de "que el usuario entienda lo que está viendo".
 * Si el dato no está disponible se pasa [missing] = true y se muestra atenuado.
 */
@Composable
fun ExplainRow(
    label: String,
    value: String,
    explanation: String?,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    missing: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (explanation != null) Modifier.clickable { open = true } else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(1.dp))
            Text(
                value,
                style = when {
                    missing -> MaterialTheme.typography.bodyMedium
                    mono -> MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
                    else -> MaterialTheme.typography.bodyLarge
                },
                color = if (missing) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (explanation != null) {
            IconButton(onClick = { open = true }) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = tr("¿Qué significa?"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
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
        icon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Entendido")) } },
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (LocalIsDark.current) 0.14f else 0.08f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
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

/** Máscara de subred en formato clásico (255.255.255.0) a partir del prefijo (/24). */
fun prefixToMask(prefix: Int): String {
    val p = prefix.coerceIn(0, 32)
    val mask = if (p == 0) 0L else (0xFFFFFFFFL shl (32 - p)) and 0xFFFFFFFFL
    return "${(mask shr 24) and 255}.${(mask shr 16) and 255}.${(mask shr 8) and 255}.${mask and 255}"
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
