//
//  PremiumStore.kt
//  QueensGame (Android)
//
//  Google Play Billing wrapper for the one-time "Royal Pass" unlock — the
//  Android twin of the iOS StoreKit `PremiumStore`. A single non-consumable
//  in-app product unlocks Royal Decrees mode. The last known entitlement is
//  cached so the app starts unlocked offline, then re-verified with Play.
//

package com.app.queensgame.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.app.queensgame.core.GameMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface PurchaseState {
    data object Idle : PurchaseState
    data object Purchasing : PurchaseState
    /** Slow payment methods / family approval — finishes later via onPurchasesUpdated. */
    data object Pending : PurchaseState
    data class Failed(val message: String) : PurchaseState
}

class PremiumStore(
    context: Context,
    private val scope: CoroutineScope,
    private val onPremiumChanged: (Boolean) -> Unit,
) : PurchasesUpdatedListener {

    companion object {
        /** Must match the in-app product configured in the Play Console (same id as iOS). */
        const val PREMIUM_PRODUCT_ID = "com.app.queensgame.premium"
        private const val CACHE_KEY = "queens.premium"
    }

    private val prefs = context.getSharedPreferences("queens", Context.MODE_PRIVATE)

    var isPremium by mutableStateOf(prefs.getBoolean(CACHE_KEY, false))
        private set
    var product by mutableStateOf<ProductDetails?>(null)
        private set
    var purchaseState by mutableStateOf<PurchaseState>(PurchaseState.Idle)
        private set
    var showPaywall by mutableStateOf(false)

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Localized price for the paywall button, e.g. "$2.99". */
    val displayPrice: String? get() = product?.oneTimePurchaseOfferDetails?.formattedPrice

    /** Whether the given mode can be played right now. */
    fun canPlay(mode: GameMode): Boolean = isPremium || !mode.requiresPremium

    private fun setPremium(value: Boolean) {
        if (isPremium == value) return
        isPremium = value
        prefs.edit().putBoolean(CACHE_KEY, value).apply()
        onPremiumChanged(value)
    }

    /** Connects, loads the product and re-reads what the account owns. */
    fun refresh() {
        scope.launch {
            if (!ensureConnected()) return@launch
            loadProduct()
            refreshEntitlements()
        }
    }

    fun endConnection() = client.endConnection()

    private suspend fun ensureConnected(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    // Reconnected lazily on the next refresh / purchase.
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    private suspend fun loadProduct() {
        if (product != null) return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        product = result.productDetailsList?.firstOrNull { it.productId == PREMIUM_PRODUCT_ID }
    }

    fun purchase(activity: Activity) {
        scope.launch {
            purchaseState = PurchaseState.Purchasing
            if (!ensureConnected()) {
                purchaseState = PurchaseState.Failed("Google Play is unavailable right now. Please try again later.")
                return@launch
            }
            loadProduct()
            val details = product
            if (details == null) {
                purchaseState = PurchaseState.Failed("The store is unavailable right now. Please try again later.")
                return@launch
            }
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
                )
                .build()
            val result = client.launchBillingFlow(activity, params)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                purchaseState = failure(result)
            }
            // Otherwise the outcome arrives in onPurchasesUpdated.
        }
    }

    /**
     * "Restore purchases": Play already knows what the signed-in account owns,
     * so restoring is a fresh entitlement query.
     */
    fun restore() {
        scope.launch {
            purchaseState = PurchaseState.Purchasing
            if (ensureConnected()) refreshEntitlements()
            purchaseState = if (isPremium) {
                PurchaseState.Idle
            } else {
                PurchaseState.Failed("No previous purchase was found for this Google account.")
            }
            if (isPremium) showPaywall = false
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        scope.launch {
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchases.orEmpty().forEach { handle(it) }
                    if (purchaseState == PurchaseState.Purchasing) purchaseState = PurchaseState.Idle
                    if (isPremium) showPaywall = false
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> purchaseState = PurchaseState.Idle
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    refreshEntitlements()
                    purchaseState = PurchaseState.Idle
                    if (isPremium) showPaywall = false
                }
                else -> purchaseState = failure(result)
            }
        }
    }

    private suspend fun refreshEntitlements() {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        // Keep the cached entitlement when Play can't be reached.
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        val owned = result.purchasesList.filter {
            PREMIUM_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        owned.forEach { acknowledge(it) }
        setPremium(owned.isNotEmpty())
    }

    private suspend fun handle(purchase: Purchase) {
        if (PREMIUM_PRODUCT_ID !in purchase.products) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                setPremium(true)
                acknowledge(purchase)
                purchaseState = PurchaseState.Idle
            }
            Purchase.PurchaseState.PENDING -> purchaseState = PurchaseState.Pending
            else -> Unit
        }
    }

    /** Play refunds purchases that aren't acknowledged within three days. */
    private suspend fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        )
    }

    private fun failure(result: BillingResult): PurchaseState.Failed = PurchaseState.Failed(
        when (result.responseCode) {
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            -> "Google Play is unavailable right now. Please try again later."
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "The Royal Pass isn't available in your store."
            else -> result.debugMessage.ifBlank { "The purchase couldn't be completed." }
        },
    )

    fun clearError() {
        if (purchaseState is PurchaseState.Failed) purchaseState = PurchaseState.Idle
    }
}
