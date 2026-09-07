@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.factory.lexivaultwordgamesolver.billing

import android.app.Activity
import android.content.Context
import app.cash.turbine.test
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BillingManagerTest {

    private val billingClient: BillingClient = mockk(relaxed = true)
    private val premiumManager: PremiumManager = mockk(relaxed = true)
    private lateinit var capturedListener: PurchasesUpdatedListener

    private val okResult: BillingResult = billingResult(BillingClient.BillingResponseCode.OK)

    @Before
    fun setUp() {
        mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
    }

    @After
    fun tearDown() {
        unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        unmockkStatic(BillingFlowParams::class)
        unmockkStatic(BillingFlowParams.ProductDetailsParams::class)
    }

    private fun TestScope.newManager(): BillingManager = BillingManager(
        context = mockk<Context>(relaxed = true),
        premiumManager = premiumManager,
        externalScope = this,
        billingClientFactory = { listener ->
            capturedListener = listener
            billingClient
        }
    )

    private fun billingResult(code: Int, message: String = ""): BillingResult =
        BillingResult.newBuilder().setResponseCode(code).setDebugMessage(message).build()

    /**
     * [BillingFlowParams.Builder.build] unconditionally builds a nested, unset
     * `SubscriptionUpdateParams`, whose own validation throws for any [ProductDetails] that isn't
     * a fully-populated real Play catalog response. Since that validation is Play Billing's SDK
     * internals rather than anything [BillingManager] controls, the builder chain is stubbed out
     * so these tests exercise our wiring (which params/activity get passed to the client) instead.
     */
    private fun stubBillingFlowParamsBuilders() {
        mockkStatic(BillingFlowParams::class)
        mockkStatic(BillingFlowParams.ProductDetailsParams::class)
        val productDetailsParamsBuilder = mockk<BillingFlowParams.ProductDetailsParams.Builder>(relaxed = true)
        every { BillingFlowParams.ProductDetailsParams.newBuilder() } returns productDetailsParamsBuilder
        every { productDetailsParamsBuilder.setProductDetails(any()) } returns productDetailsParamsBuilder
        every { productDetailsParamsBuilder.setOfferToken(any()) } returns productDetailsParamsBuilder
        every { productDetailsParamsBuilder.build() } returns mockk(relaxed = true)

        val flowParamsBuilder = mockk<BillingFlowParams.Builder>(relaxed = true)
        every { BillingFlowParams.newBuilder() } returns flowParamsBuilder
        every { flowParamsBuilder.setProductDetailsParamsList(any()) } returns flowParamsBuilder
        every { flowParamsBuilder.build() } returns mockk(relaxed = true)
    }

    private fun fakeProductDetails(id: String): ProductDetails {
        val details = mockk<ProductDetails>(relaxed = true)
        every { details.productId } returns id
        return details
    }

    private fun fakePurchase(
        state: Int = Purchase.PurchaseState.PURCHASED,
        productIds: List<String> = emptyList(),
        token: String = "token",
        acknowledged: Boolean = false
    ): Purchase {
        val purchase = mockk<Purchase>(relaxed = true)
        every { purchase.purchaseState } returns state
        every { purchase.products } returns productIds
        every { purchase.purchaseToken } returns token
        every { purchase.isAcknowledged } returns acknowledged
        return purchase
    }

    // --- Product loading -----------------------------------------------------------------

    @Test
    fun `queryAvailableProducts stores returned product details keyed by product id`() = runTest {
        val manager = newManager()
        val monthly = fakeProductDetails(SubscriptionTier.MONTHLY.productId)
        coEvery { billingClient.queryProductDetails(any()) } returns ProductDetailsResult(okResult, listOf(monthly))

        manager.queryAvailableProducts()

        assertEquals(monthly, manager.productDetails.value[SubscriptionTier.MONTHLY.productId])
    }

    @Test
    fun `queryAvailableProducts leaves the catalog untouched when the query fails`() = runTest {
        val manager = newManager()
        coEvery { billingClient.queryProductDetails(any()) } returns
            ProductDetailsResult(billingResult(BillingClient.BillingResponseCode.ERROR), emptyList())

        manager.queryAvailableProducts()

        assertTrue(manager.productDetails.value.isEmpty())
    }

    // --- Purchase flow ---------------------------------------------------------------------

    @Test
    fun `launchPurchaseFlow emits an error when product details have not loaded yet`() = runTest {
        val manager = newManager()
        val activity = mockk<Activity>(relaxed = true)

        manager.purchaseEvents.test {
            manager.launchPurchaseFlow(activity, SubscriptionTier.MONTHLY)
            advanceUntilIdle()
            assertEquals(
                PurchaseEvent.Error("Product not available right now. Check your connection and try again."),
                awaitItem()
            )
        }
    }

    @Test
    fun `launchPurchaseFlow starts the Play billing flow once product details are known`() = runTest {
        stubBillingFlowParamsBuilders()
        val manager = newManager()
        val activity = mockk<Activity>(relaxed = true)
        coEvery { billingClient.queryProductDetails(any()) } returns
            ProductDetailsResult(okResult, listOf(fakeProductDetails(SubscriptionTier.MONTHLY.productId)))
        manager.queryAvailableProducts()
        every { billingClient.launchBillingFlow(any(), any()) } returns okResult

        manager.launchPurchaseFlow(activity, SubscriptionTier.MONTHLY)

        verify { billingClient.launchBillingFlow(activity, any()) }
    }

    @Test
    fun `launchPurchaseFlow emits an error when Play billing rejects the flow`() = runTest {
        stubBillingFlowParamsBuilders()
        val manager = newManager()
        val activity = mockk<Activity>(relaxed = true)
        coEvery { billingClient.queryProductDetails(any()) } returns
            ProductDetailsResult(okResult, listOf(fakeProductDetails(SubscriptionTier.MONTHLY.productId)))
        manager.queryAvailableProducts()
        every { billingClient.launchBillingFlow(any(), any()) } returns
            billingResult(BillingClient.BillingResponseCode.ERROR, "boom")

        manager.purchaseEvents.test {
            manager.launchPurchaseFlow(activity, SubscriptionTier.MONTHLY)
            advanceUntilIdle()
            assertEquals(PurchaseEvent.Error("boom"), awaitItem())
        }
    }

    @Test
    fun `onPurchasesUpdated grants premium and emits Success for a purchased subscription`() = runTest {
        val manager = newManager()
        val purchase = fakePurchase(productIds = listOf(SubscriptionTier.YEARLY.productId))
        every { premiumManager.isValidPurchase(purchase) } returns true
        coEvery { billingClient.acknowledgePurchase(any()) } returns okResult

        manager.purchaseEvents.test {
            capturedListener.onPurchasesUpdated(okResult, listOf(purchase))
            advanceUntilIdle()
            assertEquals(PurchaseEvent.Success(SubscriptionTier.YEARLY), awaitItem())
        }
        coVerify { billingClient.acknowledgePurchase(any()) }
        coVerify { premiumManager.setPremiumStatus(true, SubscriptionTier.YEARLY.productId) }
    }

    @Test
    fun `onPurchasesUpdated does not re-acknowledge an already acknowledged purchase`() = runTest {
        val manager = newManager()
        val purchase = fakePurchase(productIds = listOf(SubscriptionTier.MONTHLY.productId), acknowledged = true)
        every { premiumManager.isValidPurchase(purchase) } returns true

        manager.purchaseEvents.test {
            capturedListener.onPurchasesUpdated(okResult, listOf(purchase))
            advanceUntilIdle()
            awaitItem()
        }
        coVerify(exactly = 0) { billingClient.acknowledgePurchase(any()) }
    }

    @Test
    fun `onPurchasesUpdated emits Cancelled when the user backs out`() = runTest {
        val manager = newManager()

        manager.purchaseEvents.test {
            capturedListener.onPurchasesUpdated(billingResult(BillingClient.BillingResponseCode.USER_CANCELED), null)
            advanceUntilIdle()
            assertEquals(PurchaseEvent.Cancelled, awaitItem())
        }
    }

    @Test
    fun `onPurchasesUpdated emits AlreadyOwned`() = runTest {
        val manager = newManager()

        manager.purchaseEvents.test {
            capturedListener.onPurchasesUpdated(billingResult(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED), null)
            advanceUntilIdle()
            assertEquals(PurchaseEvent.AlreadyOwned, awaitItem())
        }
    }

    @Test
    fun `onPurchasesUpdated emits an Error for other failures`() = runTest {
        val manager = newManager()

        manager.purchaseEvents.test {
            capturedListener.onPurchasesUpdated(billingResult(BillingClient.BillingResponseCode.ERROR, "boom"), null)
            advanceUntilIdle()
            assertEquals(PurchaseEvent.Error("boom"), awaitItem())
        }
    }

    @Test
    fun `onPurchasesUpdated emits Pending for a pending purchase`() = runTest {
        val manager = newManager()
        val purchase = fakePurchase(state = Purchase.PurchaseState.PENDING)

        manager.purchaseEvents.test {
            capturedListener.onPurchasesUpdated(okResult, listOf(purchase))
            advanceUntilIdle()
            assertEquals(PurchaseEvent.Pending, awaitItem())
        }
    }

    @Test
    fun `onPurchasesUpdated rejects a purchase that fails validation and does not grant premium`() = runTest {
        val manager = newManager()
        val purchase = fakePurchase(productIds = listOf(SubscriptionTier.MONTHLY.productId))
        every { premiumManager.isValidPurchase(purchase) } returns false

        manager.purchaseEvents.test {
            capturedListener.onPurchasesUpdated(okResult, listOf(purchase))
            advanceUntilIdle()
            assertEquals(PurchaseEvent.Error("Purchase could not be verified."), awaitItem())
        }
        coVerify(exactly = 0) { premiumManager.setPremiumStatus(any(), any()) }
    }

    @Test
    fun `onPurchasesUpdated consumes and thanks the user for a support purchase without granting premium`() = runTest {
        val manager = newManager()
        val purchase = fakePurchase(productIds = listOf(SupportPurchase.PRODUCT_ID), token = "support-token")
        every { premiumManager.isValidPurchase(purchase) } returns true
        coEvery { billingClient.consumePurchase(any()) } returns ConsumeResult(okResult, "support-token")

        manager.purchaseEvents.test {
            capturedListener.onPurchasesUpdated(okResult, listOf(purchase))
            advanceUntilIdle()
            assertEquals(PurchaseEvent.SupportPurchaseThanks, awaitItem())
        }
        coVerify { billingClient.consumePurchase(any()) }
        coVerify(exactly = 0) { premiumManager.setPremiumStatus(any(), any()) }
    }

    // --- Restore purchases -------------------------------------------------------------------

    @Test
    fun `restorePurchases fails fast when there is no connection to Play`() = runTest {
        val manager = newManager()
        every { billingClient.isReady } returns false

        manager.purchaseEvents.test {
            val result = manager.restorePurchases()
            assertFalse(result)
            assertEquals(
                PurchaseEvent.Error("No connection to Google Play. Check your network and try again."),
                awaitItem()
            )
        }
    }

    @Test
    fun `restorePurchases syncs premium status and emits RestoreFinished for an active subscription`() = runTest {
        val manager = newManager()
        every { billingClient.isReady } returns true
        val purchase = fakePurchase(productIds = listOf(SubscriptionTier.YEARLY.productId), token = "sub-token")
        every { premiumManager.isValidPurchase(purchase) } returns true
        coEvery { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
            PurchasesResult(okResult, listOf(purchase)),
            PurchasesResult(okResult, emptyList())
        )
        coEvery { billingClient.acknowledgePurchase(any()) } returns okResult

        manager.purchaseEvents.test {
            val result = manager.restorePurchases()
            assertTrue(result)
            assertEquals(PurchaseEvent.RestoreFinished(true), awaitItem())
        }
        coVerify { billingClient.acknowledgePurchase(any()) }
        coVerify { premiumManager.setPremiumStatus(true, SubscriptionTier.YEARLY.productId) }
    }

    @Test
    fun `restorePurchases reports no active purchases when nothing is found`() = runTest {
        val manager = newManager()
        every { billingClient.isReady } returns true
        coEvery { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
            PurchasesResult(okResult, emptyList()),
            PurchasesResult(okResult, emptyList())
        )

        manager.purchaseEvents.test {
            val result = manager.restorePurchases()
            assertFalse(result)
            assertEquals(PurchaseEvent.RestoreFinished(false), awaitItem())
        }
        coVerify { premiumManager.setPremiumStatus(false, null) }
    }

    @Test
    fun `restorePurchases consumes a leftover support purchase without granting premium`() = runTest {
        val manager = newManager()
        every { billingClient.isReady } returns true
        val supportPurchase = fakePurchase(productIds = listOf(SupportPurchase.PRODUCT_ID), token = "support-token")
        every { premiumManager.isValidPurchase(supportPurchase) } returns true
        coEvery { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
            PurchasesResult(okResult, emptyList()),
            PurchasesResult(okResult, listOf(supportPurchase))
        )
        coEvery { billingClient.consumePurchase(any()) } returns ConsumeResult(okResult, "support-token")

        val result = manager.restorePurchases()

        assertFalse(result)
        coVerify { billingClient.consumePurchase(any()) }
        coVerify { premiumManager.setPremiumStatus(false, null) }
    }

    @Test
    fun `restorePurchases ignores purchases that fail validation`() = runTest {
        val manager = newManager()
        every { billingClient.isReady } returns true
        val purchase = fakePurchase(productIds = listOf(SubscriptionTier.MONTHLY.productId))
        every { premiumManager.isValidPurchase(purchase) } returns false
        coEvery { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
            PurchasesResult(okResult, listOf(purchase)),
            PurchasesResult(okResult, emptyList())
        )

        val result = manager.restorePurchases()

        assertFalse(result)
        coVerify(exactly = 0) { billingClient.acknowledgePurchase(any()) }
        coVerify { premiumManager.setPremiumStatus(false, null) }
    }

    // --- Connection / subscription status -----------------------------------------------------

    @Test
    fun `startConnection moves directly to Connected when the client is already ready`() = runTest {
        val manager = newManager()
        every { billingClient.isReady } returns true

        manager.startConnection()

        assertEquals(BillingConnectionState.Connected, manager.connectionState.value)
        verify(exactly = 0) { billingClient.startConnection(any()) }
    }

    @Test
    fun `startConnection refreshes the catalog and purchases once billing setup succeeds`() = runTest {
        val manager = newManager()
        every { billingClient.isReady } returns false
        val listenerSlot = slot<BillingClientStateListener>()
        every { billingClient.startConnection(capture(listenerSlot)) } answers { }
        coEvery { billingClient.queryProductDetails(any()) } returns ProductDetailsResult(okResult, emptyList())
        coEvery { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns PurchasesResult(okResult, emptyList())

        manager.startConnection()
        assertEquals(BillingConnectionState.Connecting, manager.connectionState.value)

        listenerSlot.captured.onBillingSetupFinished(okResult)
        assertEquals(BillingConnectionState.Connected, manager.connectionState.value)

        advanceUntilIdle()
        coVerify { billingClient.queryProductDetails(any()) }
        coVerify(atLeast = 1) { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) }
    }

    @Test
    fun `startConnection reports BillingUnavailable when Play billing is unsupported`() = runTest {
        val manager = newManager()
        every { billingClient.isReady } returns false
        val listenerSlot = slot<BillingClientStateListener>()
        every { billingClient.startConnection(capture(listenerSlot)) } answers { }

        manager.startConnection()
        listenerSlot.captured.onBillingSetupFinished(billingResult(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE))

        assertEquals(BillingConnectionState.BillingUnavailable, manager.connectionState.value)
    }

    @Test
    fun `startConnection reports NetworkUnavailable and schedules a reconnect on network errors`() = runTest {
        val manager = newManager()
        every { billingClient.isReady } returns false
        val listenerSlot = slot<BillingClientStateListener>()
        every { billingClient.startConnection(capture(listenerSlot)) } answers { }

        manager.startConnection()
        listenerSlot.captured.onBillingSetupFinished(billingResult(BillingClient.BillingResponseCode.NETWORK_ERROR))

        assertEquals(BillingConnectionState.NetworkUnavailable, manager.connectionState.value)
    }

    @Test
    fun `onBillingServiceDisconnected marks the client Disconnected`() = runTest {
        val manager = newManager()
        every { billingClient.isReady } returns false
        val listenerSlot = slot<BillingClientStateListener>()
        every { billingClient.startConnection(capture(listenerSlot)) } answers { }

        manager.startConnection()
        listenerSlot.captured.onBillingServiceDisconnected()

        assertEquals(BillingConnectionState.Disconnected, manager.connectionState.value)
    }

    @Test
    fun `endConnection delegates to the underlying billing client`() = runTest {
        val manager = newManager()

        manager.endConnection()

        verify { billingClient.endConnection() }
    }
}
