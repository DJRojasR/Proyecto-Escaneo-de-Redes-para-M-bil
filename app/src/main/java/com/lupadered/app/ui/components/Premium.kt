package com.lupadered.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lupadered.app.LupaApp

/** Abre la pantalla Premium desde cualquier lugar (lo define LupaRoot). */
val LocalOpenPremium = staticCompositionLocalOf<() -> Unit> { {} }

/** Color dorado de las etiquetas Premium. */
val PremiumGold = Color(0xFFB7791F)

/** true si el usuario tiene Premium. Se actualiza solo al comprar o vencer. */
@Composable
fun rememberIsPremium(): Boolean {
    val app = LocalContext.current.applicationContext as LupaApp
    val state by app.premium.state.collectAsStateWithLifecycle()
    return state.isPremium
}

/** Etiqueta pequeña "Premium" junto a un título. */
@Composable
fun PremiumTag(modifier: Modifier = Modifier) {
    Tag("Premium", PremiumGold, modifier)
}

/**
 * Vista previa de una función Premium bloqueada (RF-23): dice qué hace y ofrece
 * desbloquearla, sin interrumpir el uso gratis.
 */
@Composable
fun LockedCard(title: String, text: String, modifier: Modifier = Modifier) {
    val openPremium = LocalOpenPremium.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = PremiumGold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                PremiumTag()
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = openPremium) {
                Icon(Icons.Outlined.WorkspacePremium, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ver Premium")
            }
        }
    }
}

/** Muestra [content] si el usuario es Premium; si no, la vista previa bloqueada. */
@Composable
fun PremiumGate(title: String, text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (rememberIsPremium()) content() else LockedCard(title, text, modifier)
}
