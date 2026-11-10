package com.lupadered.app.ui.screens

import android.app.Activity
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
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.TravelExplore
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
import com.lupadered.app.BuildConfig
import com.lupadered.app.premium.PremiumManager
import com.lupadered.app.premium.PremiumSource
import com.lupadered.app.ui.ScreenScaffold
import com.lupadered.app.ui.components.PremiumGold
import com.lupadered.app.ui.components.Tag

private data class Benefit(val icon: ImageVector, val title: String, val text: String)

private val benefits = listOf(
    Benefit(Icons.Outlined.Block, "Sin anuncios", "Escanea todas las veces que quieras sin interrupciones."),
    Benefit(Icons.Outlined.NotificationsActive, "Alerta de intrusos", "Revisamos tu red cada 15, 30 o 60 minutos y te avisamos si se conecta alguien desconocido."),
    Benefit(Icons.Outlined.CalendarMonth, "Escaneos programados", "Un escaneo automático cada día a la hora que elijas."),
    Benefit(Icons.Outlined.History, "Historial completo", "Todo el historial, no solo 7 días, y la línea de tiempo de cada equipo."),
    Benefit(Icons.Outlined.TravelExplore, "Puertos a fondo", "Más de 1100 puertos o el rango que tú elijas."),
    Benefit(Icons.Outlined.Security, "Revisión de seguridad", "Un puntaje de tu red y los pasos para corregir lo riesgoso."),
    Benefit(Icons.AutoMirrored.Outlined.ShowChart, "Monitor de latencia", "Gráfico en vivo para saber si la culpa es del WiFi o del proveedor."),
    Benefit(Icons.Outlined.Category, "Grupos e íconos", "Ordena tus equipos: \"Cámaras\", \"Cuarto de mamá\"…"),
    Benefit(Icons.Outlined.Wifi, "Varias redes", "Casa, trabajo, casa de los abuelos: cada una con su inventario."),
    Benefit(Icons.Outlined.Description, "Reportes PDF y CSV", "Exporta el inventario y el historial."),
    Benefit(Icons.Outlined.Palette, "Widget y temas", "Ve tus equipos conectados desde la pantalla de inicio y elige tu color."),
)

@Composable
fun PremiumScreen(premium: PremiumManager, activity: Activity?, onBack: () -> Unit) {
    val s by premium.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current

    ScreenScaffold(title = "Lupa de Red Premium", onBack = onBack) { padding ->
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
                    if (s.isPremium) "Ya eres Premium" else "Cuida tu red sin estar pendiente",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    when (s.source) {
                        PremiumSource.LIFETIME -> "Tienes Premium de por vida. ¡Gracias por apoyar la app!"
                        PremiumSource.SUBSCRIPTION -> "Tu suscripción mensual está activa."
                        PremiumSource.TEST -> "Premium de prueba activado (solo en la versión de desarrollo)."
                        null -> "El escaneo de tu red siempre será gratis. Premium suma vigilancia, análisis y orden."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (!s.isPremium) {
                if (s.pendingPayment) {
                    Text(
                        "Tu pago está pendiente. Activaremos Premium apenas Google Play lo confirme.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PremiumGold,
                    )
                }
                PlanCard(
                    title = "Mensual",
                    price = s.monthly?.price?.takeIf { it.isNotBlank() } ?: "S/ 4.90 al mes",
                    detail = s.monthly?.trial ?: "Cancela cuando quieras desde Google Play.",
                    tag = s.monthly?.trial?.let { "Prueba gratis" },
                    button = if (s.monthly?.trial != null) "Empezar prueba gratis" else "Suscribirme",
                    highlighted = false,
                    onClick = { activity?.let { premium.buy(it, lifetime = false) } },
                )
                PlanCard(
                    title = "Pro de por vida",
                    price = s.lifetime?.price?.takeIf { it.isNotBlank() }?.let { "$it una sola vez" } ?: "S/ 14.90 una sola vez",
                    detail = "Pagas una vez y tienes todo Premium para siempre, sin suscripción.",
                    tag = "Pago único",
                    button = "Comprar de por vida",
                    highlighted = true,
                    onClick = { activity?.let { premium.buy(it, lifetime = true) } },
                )
                if (!s.storeReady) {
                    Text(
                        "Conectando con Google Play… Los precios finales se muestran en tu moneda antes de pagar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text("Qué incluye", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
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
                    Text("Administrar o cancelar la suscripción")
                }
            }
            TextButton(onClick = { premium.refresh() }) { Text("Restaurar compras") }
            if (BuildConfig.DEBUG && s.source == PremiumSource.TEST) {
                OutlinedButton(onClick = { premium.setTestPremium(false) }) {
                    Text("Quitar Premium de prueba (volver a la versión gratis)")
                }
            }
            if (BuildConfig.DEBUG && !s.isPremium) {
                Text(
                    "Versión de pruebas: los botones de compra activan Premium sin cobrar nada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PremiumGold,
                )
            }
            Text(
                "Los pagos los procesa Google Play con tu cuenta de Google. La suscripción se renueva cada mes " +
                    "hasta que la canceles en Google Play › Pagos y suscripciones. Si cambias de celular, " +
                    "Premium vuelve solo al iniciar sesión con la misma cuenta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    s.message?.let { msg ->
        AlertDialog(
            onDismissRequest = premium::clearMessage,
            title = { Text("Lupa de Red Premium") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = premium::clearMessage) { Text("Entendido") } },
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    detail: String,
    tag: String?,
    button: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                tag?.let { Tag(it, PremiumGold) }
            }
            Text(price, style = MaterialTheme.typography.headlineSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(button) }
        }
    }
}
