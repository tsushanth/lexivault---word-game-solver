package com.factory.lexivaultwordgamesolver.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.factory.lexivaultwordgamesolver.billing.BillingConnectionState
import com.factory.lexivaultwordgamesolver.billing.BillingManager
import com.factory.lexivaultwordgamesolver.billing.PremiumManager
import com.factory.lexivaultwordgamesolver.billing.PurchaseEvent
import com.factory.lexivaultwordgamesolver.billing.SubscriptionTier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class PaywallUiState(
    val connectionState: BillingConnectionState = BillingConnectionState.Connecting,
    val productDetails: Map<String, ProductDetails> = emptyMap(),
    val isPremium: Boolean = false,
    val isRestoring: Boolean = false,
    val purchaseInProgress: SubscriptionTier? = null
)

class PaywallViewModel(
    private val billingManager: BillingManager,
    private val premiumManager: PremiumManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        billingManager.startConnection()
        viewModelScope.launch {
            billingManager.connectionState.collectLatest { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
        viewModelScope.launch {
            billingManager.productDetails.collectLatest { details ->
                _uiState.value = _uiState.value.copy(productDetails = details)
            }
        }
        viewModelScope.launch {
            premiumManager.isPremiumFlow.collectLatest { isPremium ->
                _uiState.value = _uiState.value.copy(isPremium = isPremium)
            }
        }
        viewModelScope.launch {
            billingManager.purchaseEvents.collectLatest { event -> handleEvent(event) }
        }
    }

    fun purchase(activity: Activity, tier: SubscriptionTier) {
        _uiState.value = _uiState.value.copy(purchaseInProgress = tier)
        billingManager.launchPurchaseFlow(activity, tier)
    }

    fun purchaseSupport(activity: Activity) {
        billingManager.launchSupportPurchaseFlow(activity)
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true)
            billingManager.restorePurchases()
            _uiState.value = _uiState.value.copy(isRestoring = false)
        }
    }

    private suspend fun handleEvent(event: PurchaseEvent) {
        _uiState.value = _uiState.value.copy(purchaseInProgress = null)
        when (event) {
            is PurchaseEvent.Success -> _messages.emit("You're now Premium! Enjoy LexiVault.")
            is PurchaseEvent.SupportPurchaseThanks -> _messages.emit("Thank you for supporting LexiVault!")
            is PurchaseEvent.Cancelled -> Unit
            is PurchaseEvent.Pending -> _messages.emit("Your purchase is processing — it'll unlock as soon as it's confirmed.")
            is PurchaseEvent.AlreadyOwned -> _messages.emit("You already own this. Try Restore purchases.")
            is PurchaseEvent.Error -> _messages.emit(event.message)
            is PurchaseEvent.RestoreFinished -> {
                _messages.emit(if (event.restoredPremium) "Premium restored!" else "No active purchases found.")
            }
        }
    }
}
