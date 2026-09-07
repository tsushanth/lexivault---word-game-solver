@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.factory.lexivaultwordgamesolver.ui.paywall

import android.app.Activity
import app.cash.turbine.test
import com.android.billingclient.api.ProductDetails
import com.factory.lexivaultwordgamesolver.billing.BillingConnectionState
import com.factory.lexivaultwordgamesolver.billing.BillingManager
import com.factory.lexivaultwordgamesolver.billing.PremiumManager
import com.factory.lexivaultwordgamesolver.billing.PurchaseEvent
import com.factory.lexivaultwordgamesolver.billing.SubscriptionTier
import com.factory.lexivaultwordgamesolver.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PaywallViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val billingManager: BillingManager = mockk(relaxed = true)
    private val premiumManager: PremiumManager = mockk()
    private val connectionStateFlow = MutableStateFlow<BillingConnectionState>(BillingConnectionState.Connecting)
    private val productDetailsFlow = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    private val isPremiumFlow = MutableStateFlow(false)
    private val purchaseEventsFlow = MutableSharedFlow<PurchaseEvent>()

    private fun createViewModel(): PaywallViewModel {
        every { billingManager.connectionState } returns connectionStateFlow
        every { billingManager.productDetails } returns productDetailsFlow
        every { billingManager.purchaseEvents } returns purchaseEventsFlow
        every { premiumManager.isPremiumFlow } returns isPremiumFlow
        return PaywallViewModel(billingManager, premiumManager)
    }

    @Test
    fun `initializing the view model starts the billing connection`() {
        createViewModel()

        verify { billingManager.startConnection() }
    }

    @Test
    fun `initial state mirrors connection, catalog and premium flows`() {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(BillingConnectionState.Connecting, state.connectionState)
        assertTrue(state.productDetails.isEmpty())
        assertFalse(state.isPremium)
        assertNull(state.purchaseInProgress)
    }

    @Test
    fun `state reflects connection state changes`() {
        val viewModel = createViewModel()

        connectionStateFlow.value = BillingConnectionState.Connected

        assertEquals(BillingConnectionState.Connected, viewModel.uiState.value.connectionState)
    }

    @Test
    fun `state reflects the product catalog`() {
        val viewModel = createViewModel()
        val details = mockk<ProductDetails>(relaxed = true)

        productDetailsFlow.value = mapOf(SubscriptionTier.MONTHLY.productId to details)

        assertEquals(details, viewModel.uiState.value.productDetails[SubscriptionTier.MONTHLY.productId])
    }

    @Test
    fun `purchase marks the tier as in progress and launches the purchase flow`() {
        val viewModel = createViewModel()
        val activity = mockk<Activity>(relaxed = true)

        viewModel.purchase(activity, SubscriptionTier.YEARLY)

        assertEquals(SubscriptionTier.YEARLY, viewModel.uiState.value.purchaseInProgress)
        verify { billingManager.launchPurchaseFlow(activity, SubscriptionTier.YEARLY) }
    }

    @Test
    fun `purchaseSupport launches the support purchase flow`() {
        val viewModel = createViewModel()
        val activity = mockk<Activity>(relaxed = true)

        viewModel.purchaseSupport(activity)

        verify { billingManager.launchSupportPurchaseFlow(activity) }
    }

    @Test
    fun `restorePurchases delegates to the billing manager and clears the restoring flag`() = runTest {
        val viewModel = createViewModel()
        coEvery { billingManager.restorePurchases() } returns false

        viewModel.restorePurchases()

        coVerify { billingManager.restorePurchases() }
        assertFalse(viewModel.uiState.value.isRestoring)
    }

    @Test
    fun `a Success event clears purchaseInProgress and posts a message`() = runTest {
        val viewModel = createViewModel()
        val activity = mockk<Activity>(relaxed = true)
        viewModel.purchase(activity, SubscriptionTier.MONTHLY)

        viewModel.messages.test {
            purchaseEventsFlow.emit(PurchaseEvent.Success(SubscriptionTier.MONTHLY))
            assertEquals("You're now Premium! Enjoy LexiVault.", awaitItem())
        }
        assertNull(viewModel.uiState.value.purchaseInProgress)
    }

    @Test
    fun `a Cancelled event clears purchaseInProgress without posting a message`() = runTest {
        val viewModel = createViewModel()
        val activity = mockk<Activity>(relaxed = true)
        viewModel.purchase(activity, SubscriptionTier.MONTHLY)

        viewModel.messages.test {
            purchaseEventsFlow.emit(PurchaseEvent.Cancelled)
            expectNoEvents()
        }
        assertNull(viewModel.uiState.value.purchaseInProgress)
    }

    @Test
    fun `an Error event posts its message verbatim`() = runTest {
        val viewModel = createViewModel()

        viewModel.messages.test {
            purchaseEventsFlow.emit(PurchaseEvent.Error("Something went wrong"))
            assertEquals("Something went wrong", awaitItem())
        }
    }

    @Test
    fun `AlreadyOwned suggests restoring purchases`() = runTest {
        val viewModel = createViewModel()

        viewModel.messages.test {
            purchaseEventsFlow.emit(PurchaseEvent.AlreadyOwned)
            assertEquals("You already own this. Try Restore purchases.", awaitItem())
        }
    }

    @Test
    fun `SupportPurchaseThanks thanks the user`() = runTest {
        val viewModel = createViewModel()

        viewModel.messages.test {
            purchaseEventsFlow.emit(PurchaseEvent.SupportPurchaseThanks)
            assertEquals("Thank you for supporting LexiVault!", awaitItem())
        }
    }
}
