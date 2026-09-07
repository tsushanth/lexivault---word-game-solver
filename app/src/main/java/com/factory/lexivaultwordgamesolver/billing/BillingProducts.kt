package com.factory.lexivaultwordgamesolver.billing

import com.android.billingclient.api.BillingClient

/** Premium subscription tiers, sold as [BillingClient.ProductType.SUBS] except [LIFETIME], which is a non-consumable [BillingClient.ProductType.INAPP] product. */
enum class SubscriptionTier(
    val productId: String,
    val productType: String,
    val displayName: String,
    val fallbackFormattedPrice: String,
    val badge: String? = null
) {
    WEEKLY(
        productId = "com.factory.lexivaultwordgamesolver.subscription.weekly",
        productType = BillingClient.ProductType.SUBS,
        displayName = "Weekly",
        fallbackFormattedPrice = "$4.79"
    ),
    MONTHLY(
        productId = "com.factory.lexivaultwordgamesolver.subscription.monthly",
        productType = BillingClient.ProductType.SUBS,
        displayName = "Monthly",
        fallbackFormattedPrice = "$11.99",
        badge = "Most Popular"
    ),
    YEARLY(
        productId = "com.factory.lexivaultwordgamesolver.subscription.yearly",
        productType = BillingClient.ProductType.SUBS,
        displayName = "Yearly",
        fallbackFormattedPrice = "$14.39",
        badge = "Best Value"
    ),
    LIFETIME(
        productId = "com.factory.lexivaultwordgamesolver.subscription.lifetime",
        productType = BillingClient.ProductType.INAPP,
        displayName = "Lifetime",
        fallbackFormattedPrice = "$79.99",
        badge = "Pay Once"
    );

    companion object {
        fun fromProductId(productId: String): SubscriptionTier? = entries.find { it.productId == productId }
        val allProductIds: List<String> = entries.map { it.productId }
        val subscriptionProductIds: List<String> = entries.filter { it.productType == BillingClient.ProductType.SUBS }.map { it.productId }
        val inAppProductIds: List<String> = entries.filter { it.productType == BillingClient.ProductType.INAPP }.map { it.productId }
    }
}

/** A small, one-time consumable purchase. Does not unlock premium — it's a quick way for a user to support the app. */
object SupportPurchase {
    const val PRODUCT_ID = "com.factory.lexivaultwordgamesolver.small_iap"
    const val DISPLAY_NAME = "Support the Developer"
    const val FALLBACK_FORMATTED_PRICE = "$0.99"
}

object PremiumLimits {
    const val FREE_RESULT_LIMIT = 15
    const val FREE_SAVED_WORDS_LIMIT = 10
}
