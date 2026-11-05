package com.lupadered.app.ads

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
import com.lupadered.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Política de anuncios pensada para no frustrar al usuario:
 *
 *  - Los primeros [SCANS_PER_AD] escaneos son siempre sin anuncios.
 *  - Después, se muestra como máximo UN anuncio cada [SCANS_PER_AD] escaneos.
 *  - El anuncio aparece justo cuando el usuario toca "Escanear", y el escaneo
 *    corre DETRÁS del anuncio. Al cerrarlo, los resultados ya están (o casi):
 *    el anuncio no le hace esperar ni un segundo extra.
 *  - Nunca aparece en medio de los resultados ni mientras lee un dispositivo.
 *  - Pausa mínima de [MIN_INTERVAL_MS] entre anuncios.
 *  - Si no hay un anuncio ya cargado, no se espera: se escanea directo.
 */
class AdsManager(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("ads", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val initialized = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var consentInformation: ConsentInformation? = null

    /** Pide el consentimiento (obligatorio en Europa y algunos países) y prepara los anuncios. */
    fun gatherConsent(activity: Activity) {
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
            preload()
        }
    }

    private fun preload() {
        if (!initialized.get() || interstitial != null || loading) return
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
        prefs.edit().putInt(KEY_SINCE_AD, prefs.getInt(KEY_SINCE_AD, 0) + 1).apply()
        preload()
    }

    /**
     * Llamar cuando el usuario toca "Escanear". Si toca anuncio y hay uno listo,
     * lo muestra. En ambos casos el escaneo empieza de inmediato.
     */
    fun maybeShowOnScanStart(activity: Activity) {
        val since = prefs.getInt(KEY_SINCE_AD, 0)
        val last = prefs.getLong(KEY_LAST_AD_AT, 0L)
        val now = System.currentTimeMillis()
        val due = since >= SCANS_PER_AD && now - last >= MIN_INTERVAL_MS
        val ad = interstitial
        if (!due || ad == null) {
            preload()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                preload()
            }
        }
        interstitial = null
        prefs.edit().putInt(KEY_SINCE_AD, 0).putLong(KEY_LAST_AD_AT, now).apply()
        ad.show(activity)
    }

    companion object {
        private const val TAG = "LupaAds"
        const val SCANS_PER_AD = 3
        const val MIN_INTERVAL_MS = 3 * 60 * 1000L
        private const val KEY_SINCE_AD = "scans_since_ad"
        private const val KEY_LAST_AD_AT = "last_ad_at"
    }
}
