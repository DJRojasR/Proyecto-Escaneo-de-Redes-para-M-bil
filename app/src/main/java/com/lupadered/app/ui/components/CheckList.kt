package com.lupadered.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.lupadered.app.ui.theme.StatusColors

enum class CheckStatus { PENDING, RUNNING, OK, INFO, WARN, BAD }

/** Un paso de un análisis: qué estamos revisando y qué encontramos. */
data class CheckItem(
    val id: String,
    val title: String,
    val status: CheckStatus,
    val result: String? = null,
    /** 0..1 mientras está en curso; null si no aplica. */
    val progress: Float? = null,
)

/**
 * Lista animada de pasos ("Revisando el router… ✓ 2 puertos abiertos").
 * Cada paso aparece deslizándose y su ícono "salta" al terminar, para que el
 * usuario vea todo el trabajo que hace la app.
 */
@Composable
fun CheckList(items: List<CheckItem>, modifier: Modifier = Modifier) {
    Column(modifier) {
        items.forEach { item ->
            key(item.id) { CheckRow(item) }
        }
    }
}

@Composable
private fun CheckRow(item: CheckItem) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = appear, enter = fadeIn() + expandVertically()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (item.status == CheckStatus.RUNNING) {
                    RadarSpinner(size = 24.dp)
                } else {
                    val done = item.status != CheckStatus.PENDING
                    val scale by animateFloatAsState(
                        targetValue = if (done) 1f else 0.7f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "check",
                    )
                    val (icon, tint) = when (item.status) {
                        CheckStatus.OK -> Icons.Outlined.CheckCircle to StatusColors.good
                        CheckStatus.INFO -> Icons.Outlined.Info to MaterialTheme.colorScheme.tertiary
                        CheckStatus.WARN -> Icons.Outlined.Warning to StatusColors.warn
                        CheckStatus.BAD -> Icons.Outlined.ErrorOutline to StatusColors.bad
                        else -> Icons.Outlined.RadioButtonUnchecked to StatusColors.idle
                    }
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp).scale(scale))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium)
                AnimatedContent(
                    targetState = item.result,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "result",
                ) { text ->
                    if (text != null) {
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (item.status) {
                                CheckStatus.BAD -> StatusColors.bad
                                CheckStatus.WARN -> StatusColors.warn
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                val p = item.progress
                if (item.status == CheckStatus.RUNNING && p != null) {
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
