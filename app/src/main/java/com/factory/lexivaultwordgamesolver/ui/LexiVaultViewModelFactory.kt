package com.factory.lexivaultwordgamesolver.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.factory.lexivaultwordgamesolver.billing.BillingManager
import com.factory.lexivaultwordgamesolver.billing.PremiumManager
import com.factory.lexivaultwordgamesolver.data.WordRepository
import com.factory.lexivaultwordgamesolver.ui.finder.WordFinderViewModel
import com.factory.lexivaultwordgamesolver.ui.pattern.PatternSolverViewModel
import com.factory.lexivaultwordgamesolver.ui.paywall.PaywallViewModel
import com.factory.lexivaultwordgamesolver.ui.saved.SavedWordsViewModel
import com.factory.lexivaultwordgamesolver.ui.settings.SettingsViewModel

class LexiVaultViewModelFactory(
    private val repository: WordRepository,
    private val premiumManager: PremiumManager,
    private val billingManager: BillingManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(WordFinderViewModel::class.java) ->
                WordFinderViewModel(repository, premiumManager) as T
            modelClass.isAssignableFrom(PatternSolverViewModel::class.java) ->
                PatternSolverViewModel(repository, premiumManager) as T
            modelClass.isAssignableFrom(SavedWordsViewModel::class.java) ->
                SavedWordsViewModel(repository, premiumManager) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(billingManager, premiumManager) as T
            modelClass.isAssignableFrom(PaywallViewModel::class.java) ->
                PaywallViewModel(billingManager, premiumManager) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
