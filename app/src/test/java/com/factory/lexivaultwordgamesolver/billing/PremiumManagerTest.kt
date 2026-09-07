package com.factory.lexivaultwordgamesolver.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.billingclient.api.Purchase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `preferencesDataStore(...)` caches a single [androidx.datastore.core.DataStore] instance for the
 * lifetime of the process (by design, to guard against two DataStore instances fighting over the
 * same file) rather than per [Context], so it survives across test methods even though Robolectric
 * hands each test a fresh [ApplicationProvider] context. [resetPremiumState] clears it before every
 * test so tests don't leak state into one another.
 */
@RunWith(AndroidJUnit4::class)
class PremiumManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetPremiumState() = runTest {
        PremiumManager(context).setPremiumStatus(false, null)
    }

    @Test
    fun `default state is not premium with no product id`() = runTest {
        val manager = PremiumManager(context)

        assertFalse(manager.isPremiumFlow.first())
        assertNull(manager.premiumProductIdFlow.first())
    }

    @Test
    fun `setPremiumStatus true persists premium flag and product id`() = runTest {
        val manager = PremiumManager(context)

        manager.setPremiumStatus(true, "com.factory.lexivaultwordgamesolver.subscription.monthly")

        assertTrue(manager.isPremiumFlow.first())
        assertEquals("com.factory.lexivaultwordgamesolver.subscription.monthly", manager.premiumProductIdFlow.first())
    }

    @Test
    fun `setPremiumStatus false clears premium flag and product id`() = runTest {
        val manager = PremiumManager(context)
        manager.setPremiumStatus(true, "com.factory.lexivaultwordgamesolver.subscription.yearly")

        manager.setPremiumStatus(false, null)

        assertFalse(manager.isPremiumFlow.first())
        assertNull(manager.premiumProductIdFlow.first())
    }

    @Test
    fun `setPremiumStatus true without a product id leaves the previous product id untouched`() = runTest {
        val manager = PremiumManager(context)
        manager.setPremiumStatus(true, "com.factory.lexivaultwordgamesolver.subscription.weekly")

        manager.setPremiumStatus(true, null)

        assertTrue(manager.isPremiumFlow.first())
        assertEquals("com.factory.lexivaultwordgamesolver.subscription.weekly", manager.premiumProductIdFlow.first())
    }

    @Test
    fun `premium status persists across manager instances backed by the same context`() = runTest {
        val first = PremiumManager(context)
        first.setPremiumStatus(true, "com.factory.lexivaultwordgamesolver.subscription.lifetime")

        val second = PremiumManager(context)

        assertTrue(second.isPremiumFlow.first())
        assertEquals("com.factory.lexivaultwordgamesolver.subscription.lifetime", second.premiumProductIdFlow.first())
    }

    @Test
    fun `isValidPurchase returns true for a purchased item from this package`() {
        val manager = PremiumManager(context)
        val purchase = mockk<Purchase> {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { packageName } returns context.packageName
        }

        assertTrue(manager.isValidPurchase(purchase))
    }

    @Test
    fun `isValidPurchase returns false when purchase is not in the PURCHASED state`() {
        val manager = PremiumManager(context)
        val purchase = mockk<Purchase> {
            every { purchaseState } returns Purchase.PurchaseState.PENDING
            every { packageName } returns context.packageName
        }

        assertFalse(manager.isValidPurchase(purchase))
    }

    @Test
    fun `isValidPurchase returns false when the package name does not match`() {
        val manager = PremiumManager(context)
        val purchase = mockk<Purchase> {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { packageName } returns "com.some.other.app"
        }

        assertFalse(manager.isValidPurchase(purchase))
    }
}
