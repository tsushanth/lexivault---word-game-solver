package com.factory.lexivaultwordgamesolver.ui.pattern

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.factory.lexivaultwordgamesolver.billing.PremiumLimits
import com.factory.lexivaultwordgamesolver.ui.components.EmptyState
import com.factory.lexivaultwordgamesolver.ui.components.PremiumUpsellBanner
import com.factory.lexivaultwordgamesolver.ui.components.ScoringSystemToggle
import com.factory.lexivaultwordgamesolver.ui.components.WordResultRow

@Composable
fun PatternSolverScreen(viewModel: PatternSolverViewModel, onUpgradeClick: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    fun runSearch() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        focusManager.clearFocus()
        viewModel.search()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Crossword Solver",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Enter a pattern using ? for unknown letters, e.g. C?T or ?????",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            OutlinedTextField(
                value = uiState.pattern,
                onValueChange = viewModel::onPatternChanged,
                label = { Text("Pattern") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Pattern. Use question mark for unknown letters." }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoringSystemToggle(
                    selected = uiState.scoringSystem,
                    onSelectedChanged = viewModel::onScoringSystemChanged,
                    isPremium = uiState.isPremium,
                    onPremiumRequired = onUpgradeClick
                )
                Button(onClick = { runSearch() }) {
                    Text("Solve")
                }
            }
        }

        HorizontalDivider()

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isSearching -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    EmptyState(
                        title = "Something went wrong",
                        subtitle = uiState.error.orEmpty(),
                        isError = true,
                        actionLabel = "Try again",
                        onAction = viewModel::search
                    )
                }
                !uiState.hasSearched -> {
                    EmptyState(
                        title = "Ready to solve",
                        subtitle = "Enter a pattern above and tap Solve"
                    )
                }
                uiState.results.isEmpty() -> {
                    EmptyState(
                        title = "No matches found",
                        subtitle = "Try a different pattern"
                    )
                }
                else -> {
                    Column {
                        Text(
                            text = "${uiState.results.size} matches found",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.visibleResults, key = { it }) { word ->
                                WordResultRow(
                                    word = word,
                                    scoringSystem = uiState.scoringSystem,
                                    isSaved = uiState.savedWords.contains(word),
                                    onToggleSaved = {
                                        if (!viewModel.toggleSaved(word)) onUpgradeClick()
                                    }
                                )
                            }
                            if (!uiState.isPremium && uiState.results.size > PremiumLimits.FREE_RESULT_LIMIT) {
                                item {
                                    PremiumUpsellBanner(
                                        title = "Showing ${PremiumLimits.FREE_RESULT_LIMIT} of ${uiState.results.size} results",
                                        subtitle = "Upgrade to Premium to see every match",
                                        buttonLabel = "Unlock unlimited results",
                                        onUpgradeClick = onUpgradeClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
