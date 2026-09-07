@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.factory.lexivaultwordgamesolver.ui.settings

import app.cash.turbine.test
import com.factory.lexivaultwordgamesolver.billing.BillingManager
import com.factory.lexivaultwordgamesolver.billing.PremiumManager
import com.factory.lexivaultwordgamesolver.billing.PurchaseEvent
import com.factory.lexivaultwordgamesolver.billing.SubscriptionTier
import com.factory.lexivaultwordgamesolver.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val billingManager: BillingManager = mockk(relaxed = true)
    private val premiumManager: PremiumManager = mockk()
    private val isPremiumFlow = MutableStateFlow(false)
    private val premiumProductIdFlow = MutableStateFlow<String?>(null)
    private val purchaseEventsFlow = MutableSharedFlow<PurchaseEvent>()

    private fun createViewModel(): SettingsViewModel {
        every { premiumManager.isPremiumFlow } returns isPremiumFlow
        every { premiumManager.premiumProductIdFlow } returns premiumProductIdFlow
        every { billingManager.purchaseEvents } returns purchaseEventsFlow
        return SettingsViewModel(billingManager, premiumManager)
    }

    @Test
    fun `initial state is not premium with no tier and not restoring`() {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isPremium)
        assertNull(state.premiumTier)
        assertFalse(state.isRestoring)
    }

    @Test
    fun `premium flow updates isPremium and premiumTier together`() {
        val viewModel = createViewModel()

        isPremiumFlow.value = true
        premiumProductIdFlow.value = SubscriptionTier.MONTHLY.productId

        val state = viewModel.uiState.value
        assertTrue(state.isPremium)
        assertEquals(SubscriptionTier.MONTHLY, state.premiumTier)
    }

    @Test
    fun `isManageableSubscription is false for a lifetime purchase`() {
        val viewModel = createViewModel()

        isPremiumFlow.value = true
        premiumProductIdFlow.value = SubscriptionTier.LIFETIME.productId

        assertFalse(viewModel.uiState.value.isManageableSubscription)
    }

    @Test
    fun `isManageableSubscription is true for an active recurring subscription`() {
        val viewModel = createViewModel()

        isPremiumFlow.value = true
        premiumProductIdFlow.value = SubscriptionTier.YEARLY.productId

        assertTrue(viewModel.uiState.value.isManageableSubscription)
    }

    @Test
    fun `restorePurchases delegates to the billing manager and clears the restoring flag`() = runTest {
        val viewModel = createViewModel()
        coEvery { billingManager.restorePurchases() } returns true

        viewModel.restorePurchases()

        coVerify { billingManager.restorePurchases() }
        assertFalse(viewModel.uiState.value.isRestoring)
    }

    @Test
    fun `RestoreFinished with premium posts a success message`() = runTest {
        val viewModel = createViewModel()

        viewModel.messages.test {
            purchaseEventsFlow.emit(PurchaseEvent.RestoreFinished(true))
            assertEquals("Premium restored!", awaitItem())
        }
    }

    @Test
    fun `RestoreFinished without premium posts a not-found message`() = runTest {
        val viewModel = createViewModel()

        viewModel.messages.test {
            purchaseEventsFlow.emit(PurchaseEvent.RestoreFinished(false))
            assertEquals("No active purchases found.", awaitItem())
        }
    }

    @Test
    fun `Error events are surfaced verbatim as messages`() = runTest {
        val viewModel = createViewModel()

        viewModel.messages.test {
            purchaseEventsFlow.emit(PurchaseEvent.Error("Network unavailable"))
            assertEquals("Network unavailable", awaitItem())
        }
    }

    @Test
    fun `unrelated purchase events do not post a message`() = runTest {
        val viewModel = createViewModel()

        viewModel.messages.test {
            purchaseEventsFlow.emit(PurchaseEvent.Cancelled)
            expectNoEvents()
        }
    }
}
