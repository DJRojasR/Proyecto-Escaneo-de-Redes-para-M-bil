package com.lupadered.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.lupadered.app.data.Appearance
import com.lupadered.app.data.DarkMode
import com.lupadered.app.data.ThemeColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6E5F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB9EFDF),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A635D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E0),
    onSecondaryContainer = Color(0xFF06201B),
    tertiary = Color(0xFF3D6377),
    tertiaryContainer = Color(0xFFC2E8FF),
    onTertiaryContainer = Color(0xFF001E2C),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF6F8F7),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F2),
    surfaceContainer = Color(0xFFEAEFEC),
    surfaceContainerHigh = Color(0xFFE4E9E7),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DD6C4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFFA9F2DF),
    secondary = Color(0xFFB1CCC4),
    secondaryContainer = Color(0xFF334B45),
    onSecondaryContainer = Color(0xFFCCE8E0),
    tertiary = Color(0xFFA5CCE3),
    tertiaryContainer = Color(0xFF244C5E),
    onTertiaryContainer = Color(0xFFC2E8FF),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4946),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** true si la app se está mostrando en modo oscuro (puede no coincidir con el sistema). */
val LocalIsDark = staticCompositionLocalOf { false }

/** Colores con significado (bien / atención / mal), coherentes en claro y oscuro. */
object StatusColors {
    val good: Color
        @Composable get() = if (LocalIsDark.current) Color(0xFF7BD88F) else Color(0xFF1B7F3B)
    val warn: Color
        @Composable get() = if (LocalIsDark.current) Color(0xFFF2C14E) else Color(0xFF9A6700)
    val bad: Color
        @Composable get() = if (LocalIsDark.current) Color(0xFFFF8A80) else Color(0xFFB3261E)
    val idle: Color
        @Composable get() = MaterialTheme.colorScheme.outline
}

val MonoStyle = TextStyle(fontFeatureSettings = "tnum", fontSize = 13.sp)

/** Color principal de cada tema: (claro, contenedor claro, texto sobre contenedor claro, oscuro, contenedor oscuro). */
private class Accent(
    val primary: Color, val container: Color, val onContainer: Color,
    val darkPrimary: Color, val darkContainer: Color, val darkOnContainer: Color, val darkOnPrimary: Color,
)

private val accents = mapOf(
    ThemeColor.BLUE to Accent(
        Color(0xFF2B5EA7), Color(0xFFD6E3FF), Color(0xFF001B3E),
        Color(0xFFA9C7FF), Color(0xFF0E4785), Color(0xFFD6E3FF), Color(0xFF003063),
    ),
    ThemeColor.PURPLE to Accent(
        Color(0xFF6B4FA0), Color(0xFFEBDCFF), Color(0xFF25005A),
        Color(0xFFD3BBFF), Color(0xFF523786), Color(0xFFEBDCFF), Color(0xFF3B1F6E),
    ),
    ThemeColor.ORANGE to Accent(
        Color(0xFF9A4A12), Color(0xFFFFDBC8), Color(0xFF341100),
        Color(0xFFFFB68B), Color(0xFF7A3400), Color(0xFFFFDBC8), Color(0xFF552000),
    ),
)

/** Color de muestra para el selector de temas. */
fun ThemeColor.swatch(): Color = accents[this]?.primary ?: LightColors.primary

private fun schemeFor(color: ThemeColor, dark: Boolean): ColorScheme {
    val base = if (dark) DarkColors else LightColors
    val a = accents[color] ?: return base
    return if (dark) {
        base.copy(
            primary = a.darkPrimary, onPrimary = a.darkOnPrimary,
            primaryContainer = a.darkContainer, onPrimaryContainer = a.darkOnContainer,
        )
    } else {
        base.copy(
            primary = a.primary, onPrimary = Color.White,
            primaryContainer = a.container, onPrimaryContainer = a.onContainer,
        )
    }
}

/**
 * Tema de la app. Todos los colores y el modo oscuro son gratis.
 */
@Composable
fun LupaTheme(
    appearance: Appearance = Appearance(),
    premium: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (appearance.dark) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val color = appearance.color
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(
            colorScheme = schemeFor(color, dark),
            typography = AppTypography,
            content = content,
        )
    }
}
