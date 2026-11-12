package com.lupadered.app.premium

import com.lupadered.app.i18n.tr
import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.lupadered.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** De dónde viene el Premium del usuario. */
enum class PremiumSource { SUBSCRIPTION, LIFETIME, TEST }

/** Un plan que se muestra en la pantalla Premium, con el precio que da Google Play. */
data class PlanInfo(
    val productId: String,
    val price: String,
    /** "Prueba gratis de 7 días" u otro detalle; null si no aplica. */
    val trial: String?,
)

data class PremiumState(
    val isPremium: Boolean = false,
    val source: PremiumSource? = null,
    /** true cuando ya hablamos con Google Play y tenemos los precios. */
    val storeReady: Boolean = false,
    val monthly: PlanInfo? = null,
    val lifetime: PlanInfo? = null,
    val pendingPayment: Boolean = false,
    val message: String? = null,
)

/**
 * Compras de Lupa de Red Premium con Google Play Billing (RF-20, RF-21).
 *
 * Productos que debes crear en Play Console con estos mismos IDs:
 *  - Suscripción [SUB_MONTHLY] con el plan base [BASE_PLAN_MONTHLY] (S/ 4.90 al mes) y
 *    una oferta de prueba gratis de 7 días.
 *  - Producto de compra única [LIFETIME] ("Pro de por vida", S/ 14.90).
 *
 * El estado Premium se guarda en el teléfono para que funcione sin Internet y se
 * vuelve a comprobar con Google Play cada vez que se abre la app, así el usuario
 * lo recupera al reinstalar o al cambiar de celular con la misma cuenta.
 */
class PremiumManager(context: Context) : PurchasesUpdatedListener {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("premium", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<PremiumState> = _state.asStateFlow()

    val isPremium: Boolean get() = _state.value.isPremium

    private var monthlyDetails: ProductDetails? = null
    private var lifetimeDetails: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    // ------------------------------------------------------------------ conexión

    @Volatile
    private var connecting = false

    fun start() {
        if (client.isReady) {
            refresh()
            return
        }
        if (connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingResponseCode.OK) {
                    refresh()
                } else {
                    Log.i(TAG, "Google Play Billing no disponible: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                // enableAutoServiceReconnection() vuelve a conectar solo en la próxima llamada.
            }
        })
    }

    /** Vuelve a pedir precios y compras a Google Play (RF-21: restaurar compras). */
    fun refresh() {
        if (!client.isReady) {
            start()
            return
        }
        queryProducts()
        queryPurchases()
    }

    private fun queryProducts() {
        val subs = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SUB_MONTHLY).setProductType(ProductType.SUBS).build()
            )
        ).build()
        client.queryProductDetailsAsync(subs) { result, details ->
            if (result.responseCode == BillingResponseCode.OK) {
                monthlyDetails = details.productDetailsList.firstOrNull { it.productId == SUB_MONTHLY }
                publishPlans()
            }
        }
        val inapp = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(LIFETIME).setProductType(ProductType.INAPP).build()
            )
        ).build()
        client.queryProductDetailsAsync(inapp) { result, details ->
            if (result.responseCode == BillingResponseCode.OK) {
                lifetimeDetails = details.productDetailsList.firstOrNull { it.productId == LIFETIME }
                publishPlans()
            }
        }
    }

    private fun publishPlans() {
        val monthly = monthlyDetails?.let { pd ->
            val offer = pickSubscriptionOffer(pd)
            val phases = offer?.pricingPhases?.pricingPhaseList.orEmpty()
            val recurring = phases.lastOrNull()
            val trialPhase = phases.firstOrNull { it.priceAmountMicros == 0L }
            PlanInfo(
                productId = pd.productId,
                price = recurring?.formattedPrice?.let { tr("%1\$s al mes", it) } ?: "",
                trial = trialPhase?.let { tr("Prueba gratis de %1\$s", periodText(it.billingPeriod)) },
            )
        }
        val lifetime = lifetimeDetails?.let { pd ->
            PlanInfo(pd.productId, pd.oneTimePurchaseOfferDetails?.formattedPrice ?: "", null)
        }
        _state.update { it.copy(storeReady = true, monthly = monthly, lifetime = lifetime) }
    }

    private fun queryPurchases() {
        val all = ArrayList<Purchase>()
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
        ) { subsResult, subs ->
            if (subsResult.responseCode != BillingResponseCode.OK) return@queryPurchasesAsync
            all += subs
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
            ) { inappResult, inapp ->
                if (inappResult.responseCode != BillingResponseCode.OK) return@queryPurchasesAsync
                all += inapp
                applyPurchases(all, fromQuery = true)
            }
        }
    }

    // ------------------------------------------------------------------ compra

    /** Abre la hoja de pago de Google Play. [lifetime] = pago único; si no, suscripción mensual. */
    fun buy(activity: Activity, lifetime: Boolean) {
        // Fase de pruebas: en la versión de desarrollo la compra es solo representativa.
        if (BuildConfig.DEBUG) {
            setTestPremium(true)
            _state.update {
                it.copy(
                    message = tr("Compra simulada (") + (if (lifetime) tr("Pro de por vida") else "mensual") + tr("). Ya puedes probar todas las funciones Premium. No se cobró nada: en la versión de pruebas los pagos no pasan por Google Play."),
                )
            }
            return
        }
        val pd = if (lifetime) lifetimeDetails else monthlyDetails
        if (pd == null) {
            _state.update {
                it.copy(message = tr("Aún no podemos conectar con Google Play. Revisa tu Internet e inténtalo de nuevo."))
            }
            refresh()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd)
        if (!lifetime) {
            val offer = pickSubscriptionOffer(pd) ?: return
            productParams.setOfferToken(offer.offerToken)
        }
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()
        val result = client.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingResponseCode.OK) {
            _state.update { it.copy(message = tr("No se pudo abrir el pago (%1\$s).", result.debugMessage)) }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> applyPurchases(purchases.orEmpty(), fromQuery = false)
            BillingResponseCode.USER_CANCELED -> Unit
            BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> _state.update { it.copy(message = tr("El pago no se completó. Inténtalo de nuevo.")) }
        }
    }

    private fun applyPurchases(purchases: List<Purchase>, fromQuery: Boolean) {
        var source: PremiumSource? = null
        var pending = false
        for (p in purchases) {
            val ours = p.products.any { it == SUB_MONTHLY || it == LIFETIME }
            if (!ours) continue
            when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    source = if (LIFETIME in p.products) PremiumSource.LIFETIME
                    else source ?: PremiumSource.SUBSCRIPTION
                    if (!p.isAcknowledged) acknowledge(p)
                }
                Purchase.PurchaseState.PENDING -> pending = true
                else -> Unit
            }
        }
        // Una consulta completa sin compras significa que la suscripción terminó.
        // Una compra nueva sin resultado no quita el Premium que ya tenía.
        if (source != null || fromQuery) savePurchased(source)
        _state.update {
            val test = prefs.getBoolean(KEY_TEST, false) && BuildConfig.DEBUG
            it.copy(
                isPremium = source != null || test,
                source = source ?: if (test) PremiumSource.TEST else null,
                pendingPayment = pending,
                message = when {
                    !fromQuery && source != null -> tr("¡Gracias! Ya tienes Lupa de Red Premium.")
                    pending -> tr("Tu pago está pendiente. Activaremos Premium apenas Google Play lo confirme.")
                    else -> it.message
                },
            )
        }
    }

    private fun acknowledge(p: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        client.acknowledgePurchase(params) { r ->
            if (r.responseCode != BillingResponseCode.OK) Log.w(TAG, "No se pudo confirmar la compra: ${r.debugMessage}")
        }
    }

    // ------------------------------------------------------------------ utilidades

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Enlace a la página de Google Play donde el usuario cancela o cambia su suscripción. */
    fun manageSubscriptionUrl(): String =
        "https://play.google.com/store/account/subscriptions?sku=$SUB_MONTHLY&package=${app.packageName}"

    /** Solo en builds de prueba: activa Premium sin pagar para probar las funciones. */
    fun setTestPremium(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit().putBoolean(KEY_TEST, enabled).apply()
        _state.update {
            val bought = savedSource()
            it.copy(
                isPremium = bought != null || enabled,
                source = bought ?: if (enabled) PremiumSource.TEST else null,
            )
        }
    }

    val testPremiumEnabled: Boolean get() = BuildConfig.DEBUG && prefs.getBoolean(KEY_TEST, false)

    private fun initialState(): PremiumState {
        val bought = savedSource()
        val test = BuildConfig.DEBUG && prefs.getBoolean(KEY_TEST, false)
        return PremiumState(
            isPremium = bought != null || test,
            source = bought ?: if (test) PremiumSource.TEST else null,
        )
    }

    private fun savedSource(): PremiumSource? =
        prefs.getString(KEY_SOURCE, null)?.let { n -> PremiumSource.entries.firstOrNull { it.name == n } }

    private fun savePurchased(source: PremiumSource?) {
        prefs.edit().putString(KEY_SOURCE, source?.name).apply()
    }

    /** Elige la oferta con prueba gratis si el usuario puede usarla; si no, el plan base. */
    private fun pickSubscriptionOffer(pd: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val offers = pd.subscriptionOfferDetails.orEmpty().filter { it.basePlanId == BASE_PLAN_MONTHLY }
            .ifEmpty { pd.subscriptionOfferDetails.orEmpty() }
        return offers.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
            ?: offers.firstOrNull { it.offerId == null }
            ?: offers.firstOrNull()
    }

    private fun periodText(iso: String): String {
        // Formato ISO 8601: P7D, P1W, P1M…
        val n = iso.filter { it.isDigit() }.toIntOrNull() ?: return iso
        return when {
            iso.endsWith("D") -> if (n == 1) tr("1 día") else tr("%1\$s días", n)
            iso.endsWith("W") -> if (n == 1) tr("1 semana") else tr("%1\$s semanas", n)
            iso.endsWith("M") -> if (n == 1) tr("1 mes") else tr("%1\$s meses", n)
            else -> iso
        }
    }

    companion object {
        private const val TAG = "LupaPremium"
        const val SUB_MONTHLY = "premium_mensual"
        const val BASE_PLAN_MONTHLY = "mensual"
        const val LIFETIME = "premium_de_por_vida"
        private const val KEY_SOURCE = "purchased_source"
        private const val KEY_TEST = "test_premium"
    }
}
