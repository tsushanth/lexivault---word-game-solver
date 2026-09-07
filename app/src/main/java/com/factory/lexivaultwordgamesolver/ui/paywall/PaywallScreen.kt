package com.factory.lexivaultwordgamesolver.ui.paywall

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.factory.lexivaultwordgamesolver.billing.BillingConnectionState
import com.factory.lexivaultwordgamesolver.billing.SubscriptionTier
import com.factory.lexivaultwordgamesolver.billing.SupportPurchase
import com.factory.lexivaultwordgamesolver.billing.formattedPrice

private const val TERMS_OF_SERVICE_URL = "https://www.lexivaultapp.com/terms"
private const val PRIVACY_POLICY_URL = "https://www.lexivaultapp.com/privacy"

private val PREMIUM_FEATURES = listOf(
    "Unlimited word finder & crossword solver results",
    "Advanced filters — starts with, contains, ends with",
    "Words With Friends scoring, alongside Scrabble",
    "Unlimited saved words",
    "Custom sort order for every search"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(viewModel: PaywallViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(uiState.isPremium) {
        if (uiState.isPremium) {
            snackbarHostState.showSnackbar("You're Premium — enjoy LexiVault!")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClose()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "Unlock LexiVault Premium",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Find more words, save more, and play smarter.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            if (uiState.connectionState == BillingConnectionState.NetworkUnavailable) {
                ConnectionIssueBanner(message = "No internet connection. Prices may be out of date.")
            } else if (uiState.connectionState == BillingConnectionState.BillingUnavailable) {
                ConnectionIssueBanner(message = "Google Play Billing isn't available on this device.")
            }

            if (uiState.isPremium) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Text("You're already Premium. Thank you!", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                PREMIUM_FEATURES.forEach { feature -> FeatureRow(feature) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SubscriptionTier.entries.forEach { tier ->
                    TierButton(
                        tier = tier,
                        formattedPrice = tier.formattedPrice(uiState.productDetails),
                        isLoading = uiState.purchaseInProgress == tier,
                        enabled = !uiState.isPremium && uiState.purchaseInProgress == null && activity != null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            activity?.let { viewModel.purchase(it, tier) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    activity?.let { viewModel.purchaseSupport(it) }
                },
                enabled = activity != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("☕ ${SupportPurchase.DISPLAY_NAME} — ${SupportPurchase.formattedPrice(uiState.productDetails)}")
            }

            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.restorePurchases()
                },
                enabled = !uiState.isRestoring,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isRestoring) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (uiState.isRestoring) "Restoring..." else "Restore purchases")
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { openUrl(context, TERMS_OF_SERVICE_URL) }) {
                    Text("Terms of Service", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { openUrl(context, PRIVACY_POLICY_URL) }) {
                    Text("Privacy Policy", style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(
                text = "Subscriptions renew automatically until cancelled. Manage or cancel anytime in Google Play.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ConnectionIssueBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TierButton(
    tier: SubscriptionTier,
    formattedPrice: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = tier.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    tier.badge?.let { badge ->
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = formattedPrice + if (tier != SubscriptionTier.LIFETIME) " / ${tier.displayName.lowercase()}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .semantics { contentDescription = "Purchasing ${tier.displayName}" },
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(
                    onClick = onClick,
                    enabled = enabled,
                    modifier = Modifier.semantics {
                        contentDescription = "Select ${tier.displayName}, $formattedPrice"
                    }
                ) {
                    Text("Select")
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
