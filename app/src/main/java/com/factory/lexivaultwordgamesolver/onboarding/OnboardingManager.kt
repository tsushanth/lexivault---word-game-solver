package com.factory.lexivaultwordgamesolver.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_prefs")

class OnboardingManager(context: Context) {

    private val dataStore = context.applicationContext.onboardingDataStore

    val hasCompletedOnboardingFlow: Flow<Boolean> = dataStore.data.map { it[HAS_COMPLETED] ?: false }

    suspend fun hasCompletedOnboarding(): Boolean = hasCompletedOnboardingFlow.first()

    suspend fun setCompleted() {
        dataStore.edit { it[HAS_COMPLETED] = true }
    }

    private companion object {
        val HAS_COMPLETED = booleanPreferencesKey("has_completed_onboarding")
    }
}
