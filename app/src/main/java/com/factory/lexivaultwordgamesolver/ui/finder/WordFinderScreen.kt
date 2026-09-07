package com.factory.lexivaultwordgamesolver.ui.finder

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.factory.lexivaultwordgamesolver.ui.components.ProBadge
import com.factory.lexivaultwordgamesolver.ui.components.ScoringSystemToggle
import com.factory.lexivaultwordgamesolver.ui.components.WordResultRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordFinderScreen(viewModel: WordFinderViewModel, onUpgradeClick: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val startsWithFocusRequester = remember { FocusRequester() }
    val containsFocusRequester = remember { FocusRequester() }
    val endsWithFocusRequester = remember { FocusRequester() }

    fun runSearch() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        focusManager.clearFocus()
        viewModel.search()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Word Finder",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Enter your rack letters — use ? for a blank tile",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            OutlinedTextField(
                value = uiState.rack,
                onValueChange = viewModel::onRackChanged,
                label = { Text("Your letters") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Your rack letters. Use question mark for a blank tile." }
            )

            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "Advanced filters", style = MaterialTheme.typography.labelLarge)
                if (!uiState.isPremium) ProBadge()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable(enabled = !uiState.isPremium) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUpgradeClick()
                    }
                    .then(
                        if (!uiState.isPremium) {
                            Modifier.semantics {
                                contentDescription = "Advanced filters, Premium feature. Double tap to upgrade."
                            }
                        } else {
                            Modifier
                        }
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.startsWith,
                    onValueChange = viewModel::onStartsWithChanged,
                    label = { Text("Starts with") },
                    enabled = uiState.isPremium,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { containsFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(startsWithFocusRequester)
                )
                OutlinedTextField(
                    value = uiState.contains,
                    onValueChange = viewModel::onContainsChanged,
                    label = { Text("Contains") },
                    enabled = uiState.isPremium,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { endsWithFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(containsFocusRequester)
                )
                OutlinedTextField(
                    value = uiState.endsWith,
                    onValueChange = viewModel::onEndsWithChanged,
                    label = { Text("Ends with") },
                    enabled = uiState.isPremium,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(endsWithFocusRequester)
                )
            }

            Text(
                text = "Word length: ${uiState.minLength} – ${uiState.maxLength}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            RangeSlider(
                value = uiState.minLength.toFloat()..uiState.maxLength.toFloat(),
                onValueChange = { range ->
                    viewModel.onLengthRangeChanged(range.start.toInt(), range.endInclusive.toInt())
                },
                valueRange = 2f..15f,
                steps = 12,
                modifier = Modifier.semantics {
                    contentDescription = "Word length range, ${uiState.minLength} to ${uiState.maxLength} letters"
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoringSystemToggle(
                    selected = uiState.scoringSystem,
                    onSelectedChanged = viewModel::onScoringSystemChanged,
                    isPremium = uiState.isPremium,
                    onPremiumRequired = onUpgradeClick
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SortOrderDropdown(
                    selected = uiState.sortOrder,
                    onSelectedChanged = viewModel::onSortOrderChanged,
                    isPremium = uiState.isPremium,
                    onPremiumRequired = onUpgradeClick
                )
                Button(onClick = { runSearch() }) {
                    Text("Find words")
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
                        title = "Ready to find words",
                        subtitle = "Enter your letters above and tap Find words"
                    )
                }
                uiState.results.isEmpty() -> {
                    EmptyState(
                        title = "No words found",
                        subtitle = "Try different letters or loosen your filters"
                    )
                }
                else -> {
                    Column {
                        Text(
                            text = "${uiState.results.size} words found",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortOrderDropdown(
    selected: SortOrder,
    onSelectedChanged: (SortOrder) -> Unit,
    isPremium: Boolean,
    onPremiumRequired: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sort by") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                val locked = !isPremium && order != SortOrder.SCORE_DESC
                DropdownMenuItem(
                    text = { Text(order.label) },
                    trailingIcon = if (locked) { { ProBadge() } } else null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (locked) onPremiumRequired() else onSelectedChanged(order)
                        expanded = false
                    }
                )
            }
        }
    }
}
