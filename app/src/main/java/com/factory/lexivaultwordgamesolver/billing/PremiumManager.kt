package com.factory.lexivaultwordgamesolver.billing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.premiumDataStore by preferencesDataStore(name = "premium_prefs")

/**
 * Tracks whether the user currently holds an active premium entitlement, persisted across
 * launches. The source of truth for *whether* an entitlement exists is always Google Play
 * (via [BillingManager]'s purchase queries) — this class only caches the last known result so
 * the UI can render the correct state instantly on cold start, before billing reconnects.
 */
class PremiumManager(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.premiumDataStore

    val isPremiumFlow: Flow<Boolean> = dataStore.data.map { it[IS_PREMIUM] ?: false }
    val premiumProductIdFlow: Flow<String?> = dataStore.data.map { it[PREMIUM_PRODUCT_ID] }

    suspend fun setPremiumStatus(isPremium: Boolean, productId: String?) {
        dataStore.edit { prefs ->
            prefs[IS_PREMIUM] = isPremium
            if (isPremium && productId != null) {
                prefs[PREMIUM_PRODUCT_ID] = productId
            } else if (!isPremium) {
                prefs.remove(PREMIUM_PRODUCT_ID)
            }
        }
    }

    /**
     * A purchase is only a valid entitlement if it's actually in the PURCHASED state and was
     * made for this app's package (guards against replaying a purchase token from another app).
     * Full protection against tampered receipts requires server-side verification against the
     * Play Developer API, which is out of scope for on-device checks.
     */
    fun isValidPurchase(purchase: Purchase): Boolean {
        return purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            purchase.packageName == appContext.packageName
    }

    private companion object {
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val PREMIUM_PRODUCT_ID = stringPreferencesKey("premium_product_id")
    }
}
