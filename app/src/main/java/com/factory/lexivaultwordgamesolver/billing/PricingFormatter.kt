package com.factory.lexivaultwordgamesolver.billing

import com.android.billingclient.api.ProductDetails

/** Formatted, localized price for a product, falling back to the catalog price if Play hasn't returned details yet (e.g. offline). */
fun productDetailsMapFormattedPrice(productId: String, fallback: String, productDetails: Map<String, ProductDetails>): String {
    val details = productDetails[productId] ?: return fallback
    details.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.let {
        return it.formattedPrice
    }
    details.oneTimePurchaseOfferDetails?.let {
        return it.formattedPrice
    }
    return fallback
}

fun SubscriptionTier.formattedPrice(productDetails: Map<String, ProductDetails>): String =
    productDetailsMapFormattedPrice(productId, fallbackFormattedPrice, productDetails)

fun SupportPurchase.formattedPrice(productDetails: Map<String, ProductDetails>): String =
    productDetailsMapFormattedPrice(PRODUCT_ID, FALLBACK_FORMATTED_PRICE, productDetails)
