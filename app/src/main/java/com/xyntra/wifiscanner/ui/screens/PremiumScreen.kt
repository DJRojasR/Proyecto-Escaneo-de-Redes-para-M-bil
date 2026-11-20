package com.xyntra.wifiscanner.ui.screens

import com.xyntra.wifiscanner.i18n.tr
import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyntra.wifiscanner.BuildConfig
import com.xyntra.wifiscanner.premium.PremiumManager
import com.xyntra.wifiscanner.premium.PremiumSource
import com.xyntra.wifiscanner.ui.ScreenScaffold
import com.xyntra.wifiscanner.ui.components.PremiumGold
import com.xyntra.wifiscanner.ui.components.Tag

private data class Benefit(val icon: ImageVector, val title: String, val text: String)

private val benefits = listOf(
    Benefit(Icons.Outlined.Block, tr("Sin anuncios"), tr("Escanea todas las veces que quieras sin interrupciones.")),
    Benefit(Icons.Outlined.Bolt, tr("Herramientas sin anuncios"), tr("Prueba de velocidad, diagnóstico y las demás herramientas se abren al instante.")),
    Benefit(Icons.Outlined.Speed, tr("Historial de velocidad"), tr("Guarda todas tus pruebas de velocidad con un gráfico para ver cómo cambia tu Internet.")),
    Benefit(Icons.Outlined.NotificationsActive, tr("Alerta de intrusos"), tr("Revisamos tu red cada 15, 30 o 60 minutos y te avisamos si se conecta alguien desconocido.")),
    Benefit(Icons.Outlined.CalendarMonth, tr("Escaneos programados"), tr("Un escaneo automático cada día a la hora que elijas.")),
    Benefit(Icons.Outlined.History, tr("Historial completo"), tr("Todo el historial, no solo 7 días, y la línea de tiempo de cada equipo.")),
    Benefit(Icons.Outlined.Security, tr("Revisión de seguridad"), tr("Un puntaje de tu red y los pasos para corregir lo riesgoso.")),
    Benefit(Icons.AutoMirrored.Outlined.ShowChart, tr("Monitor de latencia"), tr("Gráfico en vivo para saber si la culpa es del WiFi o del proveedor.")),
    Benefit(Icons.Outlined.Map, tr("Mapa de calor WiFi"), tr("Mide la señal en cada habitación y cada piso, y encuentra las zonas muertas.")),
    Benefit(Icons.Outlined.Wifi, tr("Varias redes"), tr("Casa, trabajo, casa de los abuelos: cada una con su inventario.")),
    Benefit(Icons.Outlined.Description, tr("Reportes PDF y CSV"), tr("Exporta el inventario y el historial.")),
    Benefit(Icons.Outlined.Palette, tr("Widget"), tr("Ve cuántos equipos hay conectados desde la pantalla de inicio de tu teléfono.")),
)

@Composable
fun PremiumScreen(premium: PremiumManager, activity: Activity?, onBack: () -> Unit) {
    val s by premium.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current

    ScreenScaffold(title = tr("XYNTRA Premium"), onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = PremiumGold, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    if (s.isPremium) tr("Ya eres Premium") else tr("Cuida tu red sin estar pendiente"),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    when (s.source) {
                        PremiumSource.LIFETIME -> tr("Tienes Premium de por vida. ¡Gracias por apoyar la app!")
                        PremiumSource.SUBSCRIPTION -> tr("Tu suscripción mensual está activa.")
                        null -> tr("El escaneo de tu red siempre será gratis. Premium suma vigilancia, análisis y orden.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (!s.isPremium) {
                if (s.pendingPayment) {
                    Text(
                        tr("Tu pago está pendiente. Activaremos Premium apenas Google Play lo confirme."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PremiumGold,
                    )
                }
                var lifetimeSelected by rememberSaveable { mutableStateOf(false) }
                val monthlyPrice = s.monthly?.price?.takeIf { it.isNotBlank() } ?: tr("US$ 2 al mes")
                val lifetimePrice = s.lifetime?.price?.takeIf { it.isNotBlank() }
                    ?.let { tr("%1\$s una sola vez", it) } ?: tr("US$ 10 una sola vez")
                Column(
                    Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlanOption(
                        title = tr("Mensual"),
                        price = monthlyPrice,
                        detail = s.monthly?.trial ?: tr("Cancela cuando quieras desde Google Play."),
                        tag = s.monthly?.trial?.let { tr("Prueba gratis") },
                        selected = !lifetimeSelected,
                        onSelect = { lifetimeSelected = false },
                    )
                    PlanOption(
                        title = tr("Pro de por vida"),
                        price = lifetimePrice,
                        detail = tr("Pagas una vez y tienes todo Premium para siempre, sin suscripción."),
                        tag = tr("Mejor valor"),
                        selected = lifetimeSelected,
                        onSelect = { lifetimeSelected = true },
                    )
                }
                Button(
                    onClick = { activity?.let { premium.buy(it, lifetime = lifetimeSelected) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    AnimatedContent(
                        targetState = lifetimeSelected,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 })
                                .togetherWith(fadeOut(tween(150)))
                        },
                        label = "planButton",
                    ) { lifetime ->
                        Text(
                            when {
                                lifetime -> tr("Comprar de por vida")
                                s.monthly?.trial != null -> tr("Empezar prueba gratis")
                                else -> tr("Suscribirme")
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                if (!s.storeReady) {
                    Text(
                        tr("Conectando con Google Play… Los precios finales se muestran en tu moneda antes de pagar."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(tr("Qué incluye"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            benefits.forEach { b ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        if (s.isPremium) Icons.Outlined.CheckCircle else b.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(b.title, style = MaterialTheme.typography.titleSmall)
                        Text(b.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            if (s.source == PremiumSource.SUBSCRIPTION) {
                OutlinedButton(onClick = { runCatching { uri.openUri(premium.manageSubscriptionUrl()) } }) {
                    Text(tr("Administrar o cancelar la suscripción"))
                }
            }
            TextButton(onClick = { premium.restore() }) { Text(tr("Restaurar compras")) }
            Text(
                tr("Los pagos los procesa Google Play con tu cuenta de Google. La suscripción se renueva cada mes hasta que la canceles en Google Play › Pagos y suscripciones. Si cambias de celular, Premium vuelve solo al iniciar sesión con la misma cuenta."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    s.message?.let { msg ->
        AlertDialog(
            onDismissRequest = premium::clearMessage,
            title = { Text(tr("XYNTRA Premium")) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = premium::clearMessage) { Text(tr("Entendido")) } },
        )
    }
}

/** Una opción de plan que se elige tocándola; la elegida se resalta con una transición suave. */
@Composable
private fun PlanOption(
    title: String,
    price: String,
    detail: String,
    tag: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val anim = tween<androidx.compose.ui.graphics.Color>(260)
    val container by animateColorAsState(
        if (selected) colors.primaryContainer else colors.surfaceContainerLow, anim, label = "planBg",
    )
    val borderColor by animateColorAsState(
        if (selected) colors.primary else colors.outlineVariant, anim, label = "planBorder",
    )
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, tween(260), label = "planBorderW")
    val dot by animateDpAsState(if (selected) 10.dp else 0.dp, tween(220), label = "planDot")
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .border(2.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(dot).background(colors.primary, CircleShape))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    tag?.let { Tag(it, PremiumGold) }
                }
                Text(price, style = MaterialTheme.typography.titleLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
