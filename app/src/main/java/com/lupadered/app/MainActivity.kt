package com.lupadered.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.lupadered.app.i18n.I18n
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lupadered.app.ui.LupaRoot
import com.lupadered.app.ui.theme.LupaTheme

class MainActivity : ComponentActivity() {

    /** Pantalla que se pide abrir desde una notificación o el widget (p. ej. "historial"). */
    private val openRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LupaApp
        app.ads.gatherConsent(this)
        openRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            val appearance by app.settings.appearance.collectAsStateWithLifecycle()
            val premium by app.premium.state.collectAsStateWithLifecycle()
            LupaTheme(appearance = appearance, premium = premium.isPremium) {
                // El árabe se lee de derecha a izquierda
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (I18n.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    LupaRoot(
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
        (application as LupaApp).premium.refresh()
    }

    companion object {
        const val EXTRA_ROUTE = "lupa_route"
    }
}
