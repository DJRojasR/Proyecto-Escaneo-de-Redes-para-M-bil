package com.xyntra.wifiscanner.ads

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.xyntra.wifiscanner.BuildConfig

/**
 * Mini banner adaptable (la franja pequeña que va pegada a la barra de navegación).
 *
 * No ocupa espacio hasta que el anuncio carga, y entonces aparece con una animación
 * suave, para que la pantalla no "salte". Si no hay anuncio, simplemente no se ve.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val adSize = remember(widthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }
    var loaded by remember { mutableStateOf(false) }
    val adView = remember(adSize) {
        AdView(context).apply {
            setAdSize(adSize)
            adUnitId = BuildConfig.BANNER_AD_UNIT
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loaded = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded = false
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
    val height by animateDpAsState(if (loaded) adSize.height.dp else 0.dp, label = "banner")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // El banner se mide a su altura real aunque la caja aún esté cerrada
        key(adView) {
            AndroidView(
                factory = { adView },
                modifier = Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Top, unbounded = true),
            )
        }
    }
}
