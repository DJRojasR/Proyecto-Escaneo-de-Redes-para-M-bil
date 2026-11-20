package com.xyntra.wifiscanner.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.xyntra.wifiscanner.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reglas de "anuncios que no molestan". Están todas aquí para ajustarlas en un solo lugar
 * cuando revises los informes de AdMob y las reseñas de Google Play (paso 7 del plan).
 */
object AdPolicy {
    /** Nada de anuncios de pantalla completa durante el primer día desde que se instala la app… */
    const val GRACE_PERIOD_MS = 24 * 60 * 60 * 1000L

    /** …ni antes de que la persona haya hecho esta cantidad de escaneos. */
    const val GRACE_SCANS = 5

    /** Después, como máximo un anuncio de pantalla completa cada tantos escaneos. */
    const val SCANS_PER_AD = 4

    /** Pausa mínima entre dos anuncios de pantalla completa. */
    const val MIN_INTERVAL_MS = 5 * 60 * 1000L

    /** Tope de anuncios de pantalla completa por persona y por día. */
    const val MAX_INTERSTITIALS_PER_DAY = 3

    /** Cada cuántos anuncios cerrados se sugiere (con un aviso discreto) quitar los anuncios. */
    const val PREMIUM_HINT_EVERY = 3

    /** Pestañas donde puede aparecer el mini banner, sobre la barra de navegación. */
    val BANNER_ROUTES = setOf("red", "dispositivos", "historial")

    /**
     * Herramientas gratis: se abren después de un anuncio de pantalla completa y luego
     * quedan libres este tiempo (todas, no solo la que se abrió). "Compartir tu WiFi" nunca
     * pide anuncio. Si no hay un anuncio listo (sin Internet, sin inventario), se abren igual.
     */
    const val TOOL_UNLOCK_MINUTES = 10
    const val TOOL_UNLOCK_MS = TOOL_UNLOCK_MINUTES * 60 * 1000L
}

/**
 * Anuncios de XYNTRA:
 *
 *  - **Pantalla completa (intersticial):** solo cuando la persona toca "Escanear", y el
 *    escaneo sigue trabajando detrás: al cerrarlo, los resultados ya están. Nunca al abrir
 *    o cerrar la app, ni en medio de resultados o del detalle de un equipo.
 *  - **Mini banner:** una franja pequeña sobre la barra de navegación, solo en Inicio,
 *    Dispositivos e Historial (nunca en Herramientas, detalle, Ajustes ni Premium).
 *  - El de pantalla completa respeta el periodo de gracia del primer día / primeros escaneos
 *    ([AdPolicy]); el mini banner aparece desde el primer uso porque no interrumpe.
 *  - **Herramientas gratis:** un anuncio de pantalla completa al abrir una (Prueba de velocidad,
 *    Diagnóstico, IP Manager, Mis redes) y después quedan libres [AdPolicy.TOOL_UNLOCK_MINUTES]
 *    minutos. "Compartir tu WiFi" siempre es libre.
 *  - Si no hay un anuncio ya cargado, no se espera: se escanea o se abre la herramienta directo.
 *  - Los usuarios Premium nunca ven anuncios.
 */
class AdsManager(context: Context, private val isPremium: () -> Boolean = { false }) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("ads", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val initialized = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var consentInformation: ConsentInformation? = null

    private val _ready = MutableStateFlow(false)

    /** true cuando ya hay consentimiento y el SDK de anuncios está listo para pedir anuncios. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _premiumHint = MutableStateFlow(false)

    /** Se pone en true de vez en cuando al cerrar un anuncio, para sugerir Premium con un aviso discreto. */
    val premiumHint: StateFlow<Boolean> = _premiumHint.asStateFlow()

    init {
        // Primer uso: desde aquí cuenta el día sin anuncios.
        if (!prefs.contains(KEY_FIRST_OPEN)) {
            prefs.edit().putLong(KEY_FIRST_OPEN, System.currentTimeMillis()).apply()
        }
    }

    /** Pide el consentimiento (obligatorio en Europa y algunos países) y prepara los anuncios. */
    fun gatherConsent(activity: Activity) {
        if (isPremium()) return
        val info = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = info
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) Log.w(TAG, "Consentimiento: ${formError.message}")
                    if (info.canRequestAds()) initializeAds()
                }
            },
            { requestError -> Log.w(TAG, "Consentimiento: ${requestError.message}") }
        )
        // Si ya teníamos consentimiento de una sesión anterior, empezamos ya.
        if (info.canRequestAds()) initializeAds()
    }

    /** ¿Debe mostrarse el botón "Opciones de privacidad de anuncios" en Ajustes? */
    val privacyOptionsRequired: Boolean
        get() = consentInformation?.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { err ->
            if (err != null) Log.w(TAG, "Opciones de privacidad: ${err.message}")
        }
    }

    private fun initializeAds() {
        if (!initialized.compareAndSet(false, true)) return
        scope.launch {
            withContext(Dispatchers.IO) { MobileAds.initialize(app) {} }
            _ready.value = true
            preload()
        }
    }

    /** ¿Ya pasó el periodo de gracia (primer día y primeros escaneos)? */
    fun gracePassed(now: Long = System.currentTimeMillis()): Boolean {
        // En la versión de desarrollo (la que instala Android Studio) no hay periodo de gracia,
        // para poder ver y probar los anuncios de prueba enseguida. En Google Play sí se aplica.
        if (BuildConfig.DEBUG) return true
        val firstOpen = prefs.getLong(KEY_FIRST_OPEN, now)
        val scans = prefs.getInt(KEY_TOTAL_SCANS, 0)
        return now - firstOpen >= AdPolicy.GRACE_PERIOD_MS && scans >= AdPolicy.GRACE_SCANS
    }

    /**
     * ¿Se puede mostrar el mini banner en esta pestaña? Al ser pequeño y no tapar nada,
     * aparece desde el primer uso (el periodo de gracia es solo para los de pantalla completa).
     */
    fun bannerAllowed(route: String?): Boolean =
        route in AdPolicy.BANNER_ROUTES && _ready.value && !isPremium()

    private fun preload() {
        if (isPremium()) return
        if (!initialized.get() || interstitial != null || loading) return
        // Se carga siempre (también en el periodo de gracia): las herramientas gratis lo usan.
        // Que se MUESTRE al escanear lo deciden las reglas de maybeShowOnScanStart.
        loading = true
        InterstitialAd.load(
            app,
            BuildConfig.INTERSTITIAL_AD_UNIT,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    interstitial = null
                    Log.i(TAG, "Anuncio no disponible: ${error.message}")
                }
            }
        )
    }

    /** Llamar cada vez que un escaneo termina (de red o de una IP). */
    fun onScanCompleted() {
        prefs.edit()
            .putInt(KEY_SINCE_AD, prefs.getInt(KEY_SINCE_AD, 0) + 1)
            .putInt(KEY_TOTAL_SCANS, prefs.getInt(KEY_TOTAL_SCANS, 0) + 1)
            .apply()
        preload()
    }

    /** Anuncios de pantalla completa que ya se mostraron hoy (se reinicia cada día). */
    private fun shownToday(now: Long): Int =
        if (prefs.getString(KEY_DAY, null) == dayKey(now)) prefs.getInt(KEY_DAY_COUNT, 0) else 0

    /**
     * Llamar cuando el usuario toca "Escanear". Si toca anuncio y hay uno listo,
     * lo muestra. En ambos casos el escaneo empieza de inmediato.
     */
    fun maybeShowOnScanStart(activity: Activity) {
        if (isPremium()) return
        val now = System.currentTimeMillis()
        val since = prefs.getInt(KEY_SINCE_AD, 0)
        val last = prefs.getLong(KEY_LAST_AD_AT, 0L)
        val due = gracePassed(now) &&
            since >= AdPolicy.SCANS_PER_AD &&
            now - last >= AdPolicy.MIN_INTERVAL_MS &&
            shownToday(now) < AdPolicy.MAX_INTERSTITIALS_PER_DAY
        val ad = interstitial
        if (!due || ad == null) {
            preload()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onFullScreenAdClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                preload()
            }
        }
        interstitial = null
        val today = dayKey(now)
        prefs.edit()
            .putInt(KEY_SINCE_AD, 0)
            .putLong(KEY_LAST_AD_AT, now)
            .putString(KEY_DAY, today)
            .putInt(KEY_DAY_COUNT, shownToday(now) + 1)
            .apply()
        ad.show(activity)
    }

    /** Después de cerrar cualquier anuncio de pantalla completa (de escaneo o de herramienta). */
    private fun onFullScreenAdClosed() {
        interstitial = null
        val total = prefs.getInt(KEY_ADS_SHOWN, 0) + 1
        prefs.edit()
            .putInt(KEY_ADS_SHOWN, total)
            // Acaba de ver un anuncio: las herramientas gratis quedan libres un rato
            .putLong(KEY_TOOLS_FREE_UNTIL, System.currentTimeMillis() + AdPolicy.TOOL_UNLOCK_MS)
            .apply()
        // De vez en cuando, un aviso discreto: "¿Sin anuncios? Prueba Premium"
        if (total % AdPolicy.PREMIUM_HINT_EVERY == 0) _premiumHint.value = true
        preload()
    }

    /** Para cargar un anuncio con tiempo, al entrar a la pestaña Herramientas. */
    fun preloadForTools() {
        if (!toolsUnlocked()) preload()
    }

    /** ¿Las herramientas gratis están libres ahora (Premium o anuncio visto hace poco)? */
    fun toolsUnlocked(now: Long = System.currentTimeMillis()): Boolean =
        isPremium() || now < prefs.getLong(KEY_TOOLS_FREE_UNTIL, 0L)

    /**
     * Abrir una herramienta gratis: si toca, primero un anuncio y al cerrarlo se abre la
     * herramienta. Si ya están libres, o no hay un anuncio listo, se abre directo:
     * nunca se bloquea una herramienta porque el anuncio no cargó.
     */
    fun openFreeTool(activity: Activity?, onOpen: () -> Unit) {
        val ad = interstitial
        if (activity == null || toolsUnlocked() || ad == null) {
            if (!toolsUnlocked()) preload()
            onOpen()
            return
        }
        var opened = false
        val openOnce = {
            if (!opened) {
                opened = true
                onOpen()
            }
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onFullScreenAdClosed()
                openOnce()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                preload()
                openOnce()
            }
        }
        interstitial = null
        // Cuenta como "último anuncio": el de escaneo espera su pausa mínima después de este
        prefs.edit().putLong(KEY_LAST_AD_AT, System.currentTimeMillis()).apply()
        ad.show(activity)
    }

    fun dismissPremiumHint() {
        _premiumHint.value = false
    }

    private fun dayKey(ts: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))

    companion object {
        private const val TAG = "XyntraAds"
        private const val KEY_SINCE_AD = "scans_since_ad"
        private const val KEY_LAST_AD_AT = "last_ad_at"
        private const val KEY_FIRST_OPEN = "first_open_at"
        private const val KEY_TOTAL_SCANS = "total_scans"
        private const val KEY_DAY = "ads_day"
        private const val KEY_DAY_COUNT = "ads_day_count"
        private const val KEY_ADS_SHOWN = "ads_shown_total"
        private const val KEY_TOOLS_FREE_UNTIL = "tools_free_until"
    }
}
