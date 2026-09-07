package com.factory.lexivaultwordgamesolver.billing

/** State of the connection to the Play Store billing service. */
sealed interface BillingConnectionState {
    data object Connecting : BillingConnectionState
    data object Connected : BillingConnectionState
    /** No network connection, or the billing service could not be reached. */
    data object NetworkUnavailable : BillingConnectionState
    /** Play Store billing is unavailable on this device (unsupported API, no Play Store, etc). */
    data object BillingUnavailable : BillingConnectionState
    data object Disconnected : BillingConnectionState
}

/** One-off events surfaced to the UI while a purchase is attempted or restored. */
sealed interface PurchaseEvent {
    data class Success(val tier: SubscriptionTier) : PurchaseEvent
    data object SupportPurchaseThanks : PurchaseEvent
    data object Cancelled : PurchaseEvent
    /** Purchase is in Google's PENDING state (e.g. paying with cash at a store) — not yet granted. */
    data object Pending : PurchaseEvent
    data object AlreadyOwned : PurchaseEvent
    data class Error(val message: String) : PurchaseEvent
    data class RestoreFinished(val restoredPremium: Boolean) : PurchaseEvent
}
