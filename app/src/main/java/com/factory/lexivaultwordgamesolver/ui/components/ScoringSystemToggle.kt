package com.factory.lexivaultwordgamesolver.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.factory.lexivaultwordgamesolver.solver.ScoringSystem

/**
 * Words With Friends scoring is a premium feature. Free users can still see the option, but
 * selecting it routes to [onPremiumRequired] instead of applying the change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoringSystemToggle(
    selected: ScoringSystem,
    onSelectedChanged: (ScoringSystem) -> Unit,
    isPremium: Boolean,
    onPremiumRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val options = ScoringSystem.entries
    val haptic = LocalHapticFeedback.current
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, system ->
            val locked = !isPremium && system == ScoringSystem.WORDS_WITH_FRIENDS
            SegmentedButton(
                selected = selected == system,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (locked) onPremiumRequired() else onSelectedChanged(system)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (system == ScoringSystem.SCRABBLE) "Scrabble" else "Words With Friends")
                    if (locked) {
                        ProBadge()
                    }
                }
            }
        }
    }
}
