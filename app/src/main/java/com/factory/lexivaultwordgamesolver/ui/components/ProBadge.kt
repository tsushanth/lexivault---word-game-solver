package com.factory.lexivaultwordgamesolver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small gold "PRO" tag used to mark premium-only controls throughout the app. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Text(
        text = "PRO",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSecondary,
        modifier = modifier
            .background(color = MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
