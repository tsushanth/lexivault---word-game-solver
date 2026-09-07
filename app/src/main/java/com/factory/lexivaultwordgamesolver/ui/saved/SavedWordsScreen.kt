package com.factory.lexivaultwordgamesolver.ui.saved

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.factory.lexivaultwordgamesolver.billing.PremiumLimits
import com.factory.lexivaultwordgamesolver.data.db.SavedWordEntity
import com.factory.lexivaultwordgamesolver.solver.ScoringSystem
import com.factory.lexivaultwordgamesolver.ui.components.EmptyState
import com.factory.lexivaultwordgamesolver.ui.components.PremiumUpsellBanner
import com.factory.lexivaultwordgamesolver.ui.components.ScoringSystemToggle

@Composable
fun SavedWordsScreen(viewModel: SavedWordsViewModel, onUpgradeClick: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Saved Words",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Your starred words, ready for game night",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text("Filter saved words") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                ScoringSystemToggle(
                    selected = uiState.scoringSystem,
                    onSelectedChanged = viewModel::onScoringSystemChanged,
                    isPremium = uiState.isPremium,
                    onPremiumRequired = onUpgradeClick
                )
            }
        }

        HorizontalDivider()

        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.filteredWords.isEmpty()) {
                EmptyState(
                    title = if (uiState.savedWords.isEmpty()) "No saved words yet" else "No matches",
                    subtitle = if (uiState.savedWords.isEmpty()) {
                        "Star words from the finder or crossword solver to save them here"
                    } else {
                        "Try a different filter"
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.hasReachedFreeLimit) {
                        item {
                            PremiumUpsellBanner(
                                title = "You've saved ${uiState.savedWords.size} words",
                                subtitle = "Free accounts can save up to ${PremiumLimits.FREE_SAVED_WORDS_LIMIT} words — upgrade for unlimited",
                                buttonLabel = "Unlock unlimited saved words",
                                onUpgradeClick = onUpgradeClick
                            )
                        }
                    }
                    items(uiState.filteredWords, key = { it.id }) { entity ->
                        SavedWordRow(
                            entity = entity,
                            scoringSystem = uiState.scoringSystem,
                            onRemove = { viewModel.removeWord(entity) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedWordRow(
    entity: SavedWordEntity,
    scoringSystem: ScoringSystem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val score = if (scoringSystem == ScoringSystem.SCRABBLE) entity.scrabbleScore else entity.wordsWithFriendsScore
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = entity.word.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${entity.word.length} letters · $score pts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRemove()
            }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove ${entity.word} from saved words",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
