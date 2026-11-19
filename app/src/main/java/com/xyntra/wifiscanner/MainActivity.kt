package com.xyntra.wifiscanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.xyntra.wifiscanner.ui.theme.LocalIsDark
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.xyntra.wifiscanner.i18n.I18n
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyntra.wifiscanner.ui.XyntraRoot
import com.xyntra.wifiscanner.ui.theme.XyntraTheme

class MainActivity : ComponentActivity() {

    /** Pantalla que se pide abrir desde una notificación o el widget (p. ej. "historial"). */
    private val openRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as XyntraApp
        app.ads.gatherConsent(this)
        openRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            val appearance by app.settings.appearance.collectAsStateWithLifecycle()
            val premium by app.premium.state.collectAsStateWithLifecycle()
            XyntraTheme(appearance = appearance, premium = premium.isPremium) {
                // Íconos de la barra de estado y de navegación legibles en claro y en oscuro,
                // aunque el tema elegido en la app no coincida con el del teléfono.
                val isDark = LocalIsDark.current
                LaunchedEffect(isDark) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    val style = if (isDark) SystemBarStyle.dark(transparent)
                    else SystemBarStyle.light(transparent, transparent)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                // El árabe se lee de derecha a izquierda
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (I18n.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    XyntraRoot(
                        openRoute = openRoute.value,
                        onRouteOpened = { openRoute.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { openRoute.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Comprueba compras al volver a la app (p. ej. después de pagar o cancelar en Google Play)
        (application as XyntraApp).premium.refresh()
    }

    companion object {
        const val EXTRA_ROUTE = "xyntra_route"
    }
}
