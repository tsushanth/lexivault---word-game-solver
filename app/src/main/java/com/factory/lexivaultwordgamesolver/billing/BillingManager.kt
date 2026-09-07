package com.factory.lexivaultwordgamesolver.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the connection to Google Play Billing and is the single place that talks to
 * [BillingClient]. Product catalog and purchase state are exposed as [StateFlow]s; one-off
 * outcomes (purchase succeeded/cancelled/pending/failed) are exposed as a [SharedFlow] of
 * [PurchaseEvent]s for the UI to react to (snackbars, dialogs, etc).
 *
 * [externalScope] should be a long-lived scope (e.g. tied to the [Application]) since billing
 * callbacks can arrive after any single screen's ViewModel has been cleared.
 */
class BillingManager(
    context: Context,
    private val premiumManager: PremiumManager,
    private val externalScope: CoroutineScope,
    billingClientFactory: (PurchasesUpdatedListener) -> BillingClient = { listener ->
        BillingClient.newBuilder(context.applicationContext)
            .setListener(listener)
            .enablePendingPurchases()
            .build()
    }
) {

    private val appContext = context.applicationContext

    private val _connectionState = MutableStateFlow<BillingConnectionState>(BillingConnectionState.Disconnected)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private val _purchaseEvents = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 4)
    val purchaseEvents: SharedFlow<PurchaseEvent> = _purchaseEvents.asSharedFlow()

    private var reconnectAttempts = 0

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                val updated = purchases
                if (updated != null) {
                    externalScope.launch { updated.forEach { handlePurchase(it) } }
                }
            }
            BillingResponseCode.USER_CANCELED -> externalScope.launch { _purchaseEvents.emit(PurchaseEvent.Cancelled) }
            BillingResponseCode.ITEM_ALREADY_OWNED -> externalScope.launch { _purchaseEvents.emit(PurchaseEvent.AlreadyOwned) }
            else -> externalScope.launch {
                _purchaseEvents.emit(PurchaseEvent.Error(billingResult.debugMessage.ifBlank { "Purchase failed." }))
            }
        }
    }

    private val billingClient: BillingClient = billingClientFactory(purchasesUpdatedListener)

    fun startConnection() {
        if (billingClient.isReady) {
            _connectionState.value = BillingConnectionState.Connected
            return
        }
        _connectionState.value = BillingConnectionState.Connecting
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                when (billingResult.responseCode) {
                    BillingResponseCode.OK -> {
                        reconnectAttempts = 0
                        _connectionState.value = BillingConnectionState.Connected
                        externalScope.launch {
                            queryAvailableProducts()
                            refreshPurchases()
                        }
                    }
                    BillingResponseCode.BILLING_UNAVAILABLE -> {
                        _connectionState.value = BillingConnectionState.BillingUnavailable
                    }
                    BillingResponseCode.SERVICE_UNAVAILABLE, BillingResponseCode.NETWORK_ERROR -> {
                        _connectionState.value = BillingConnectionState.NetworkUnavailable
                        scheduleReconnect()
                    }
                    else -> {
                        _connectionState.value = BillingConnectionState.Disconnected
                        scheduleReconnect()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = BillingConnectionState.Disconnected
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        val delayMs = (BASE_RECONNECT_DELAY_MS shl reconnectAttempts).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        reconnectAttempts++
        externalScope.launch {
            delay(delayMs)
            startConnection()
        }
    }

    suspend fun queryAvailableProducts() {
        val subsProducts = SubscriptionTier.subscriptionProductIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val inAppProducts = (SubscriptionTier.inAppProductIds + SupportPurchase.PRODUCT_ID).map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val merged = mutableMapOf<String, ProductDetails>()
        for (productList in listOf(subsProducts, inAppProducts)) {
            if (productList.isEmpty()) continue
            val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
            val result = billingClient.queryProductDetails(params)
            if (result.billingResult.responseCode == BillingResponseCode.OK) {
                result.productDetailsList?.forEach { merged[it.productId] = it }
            }
        }
        if (merged.isNotEmpty()) {
            _productDetails.value = merged
        }
    }

    fun launchPurchaseFlow(activity: Activity, tier: SubscriptionTier) {
        launchFlowForProduct(activity, tier.productId)
    }

    fun launchSupportPurchaseFlow(activity: Activity) {
        launchFlowForProduct(activity, SupportPurchase.PRODUCT_ID)
    }

    private fun launchFlowForProduct(activity: Activity, productId: String) {
        val details = _productDetails.value[productId]
        if (details == null) {
            externalScope.launch {
                _purchaseEvents.emit(PurchaseEvent.Error("Product not available right now. Check your connection and try again."))
            }
            return
        }
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { if (offerToken != null) setOfferToken(offerToken) }
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingResponseCode.OK) {
            externalScope.launch {
                _purchaseEvents.emit(PurchaseEvent.Error(result.debugMessage.ifBlank { "Unable to start purchase." }))
            }
        }
    }

    /** Re-queries active purchases from Google Play and syncs [PremiumManager] to match. Also used for "Restore purchases". */
    suspend fun restorePurchases(): Boolean {
        if (!billingClient.isReady) {
            _purchaseEvents.emit(PurchaseEvent.Error("No connection to Google Play. Check your network and try again."))
            return false
        }
        val hasPremium = refreshPurchases()
        _purchaseEvents.emit(PurchaseEvent.RestoreFinished(hasPremium))
        return hasPremium
    }

    /**
     * Google Play only returns SUBS purchases that are currently active, so a lapsed or
     * cancelled subscription simply stops appearing here — that's how expiry is detected without
     * a server. Lifetime purchases (INAPP, non-consumable) are returned indefinitely.
     */
    private suspend fun refreshPurchases(): Boolean {
        val subsResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        val inAppResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        )
        val purchases = subsResult.purchasesList + inAppResult.purchasesList

        var hasPremium = false
        var premiumProductId: String? = null
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (!premiumManager.isValidPurchase(purchase)) continue
            val productId = purchase.products.firstOrNull() ?: continue

            if (productId == SupportPurchase.PRODUCT_ID) {
                // A tip purchase that never got consumed (e.g. app was killed mid-flow).
                billingClient.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
                continue
            }

            val tier = SubscriptionTier.fromProductId(productId) ?: continue
            if (!purchase.isAcknowledged) {
                billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                )
            }
            hasPremium = true
            premiumProductId = tier.productId
        }

        premiumManager.setPremiumStatus(hasPremium, premiumProductId)
        return hasPremium
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> _purchaseEvents.emit(PurchaseEvent.Pending)
            Purchase.PurchaseState.PURCHASED -> {
                if (!premiumManager.isValidPurchase(purchase)) {
                    _purchaseEvents.emit(PurchaseEvent.Error("Purchase could not be verified."))
                    return
                }
                val productId = purchase.products.firstOrNull() ?: return

                if (productId == SupportPurchase.PRODUCT_ID) {
                    billingClient.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
                    _purchaseEvents.emit(PurchaseEvent.SupportPurchaseThanks)
                    return
                }

                val tier = SubscriptionTier.fromProductId(productId) ?: return
                if (!purchase.isAcknowledged) {
                    billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    )
                }
                premiumManager.setPremiumStatus(true, productId)
                _purchaseEvents.emit(PurchaseEvent.Success(tier))
            }
            else -> Unit
        }
    }

    fun endConnection() {
        billingClient.endConnection()
    }

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val BASE_RECONNECT_DELAY_MS = 1_000L
        const val MAX_RECONNECT_DELAY_MS = 30_000L
    }
}
