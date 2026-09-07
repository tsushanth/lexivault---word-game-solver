package com.factory.lexivaultwordgamesolver.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isPremium: Boolean = false,
    val premiumTier: SubscriptionTier? = null,
    val isRestoring: Boolean = false
) {
    val isManageableSubscription: Boolean
        get() = isPremium && premiumTier != null && premiumTier != SubscriptionTier.LIFETIME
}

class SettingsViewModel(
    private val billingManager: BillingManager,
    premiumManager: PremiumManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(premiumManager.isPremiumFlow, premiumManager.premiumProductIdFlow) { isPremium, productId ->
                isPremium to SubscriptionTier.entries.find { it.productId == productId }
            }.collectLatest { (isPremium, tier) ->
                _uiState.value = _uiState.value.copy(isPremium = isPremium, premiumTier = tier)
            }
        }
        viewModelScope.launch {
            billingManager.purchaseEvents.collectLatest { event ->
                if (event is PurchaseEvent.RestoreFinished) {
                    _messages.emit(if (event.restoredPremium) "Premium restored!" else "No active purchases found.")
                } else if (event is PurchaseEvent.Error) {
                    _messages.emit(event.message)
                }
            }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true)
            billingManager.restorePurchases()
            _uiState.value = _uiState.value.copy(isRestoring = false)
        }
    }
}
