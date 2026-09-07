package com.factory.lexivaultwordgamesolver

import android.app.Application
import com.factory.lexivaultwordgamesolver.billing.BillingManager
import com.factory.lexivaultwordgamesolver.billing.PremiumManager
import com.factory.lexivaultwordgamesolver.data.WordDictionary
import com.factory.lexivaultwordgamesolver.data.WordRepository
import com.factory.lexivaultwordgamesolver.data.db.AppDatabase
import com.factory.lexivaultwordgamesolver.onboarding.OnboardingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LexiVaultApplication : Application() {

    lateinit var repository: WordRepository
        private set

    lateinit var premiumManager: PremiumManager
        private set

    lateinit var billingManager: BillingManager
        private set

    lateinit var onboardingManager: OnboardingManager
        private set

    /** Outlives any single screen's ViewModel so billing callbacks always have somewhere to land. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val dictionary = WordDictionary(this)
        val database = AppDatabase.getInstance(this)
        repository = WordRepository(dictionary, database.savedWordDao())
        premiumManager = PremiumManager(this)
        billingManager = BillingManager(this, premiumManager, applicationScope)
        onboardingManager = OnboardingManager(this)
        billingManager.startConnection()
    }
}
