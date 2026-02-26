package com.william.astuterepo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.william.astuterepo.domain.DownloadState

@Composable
fun DownloadProgressIndicator(
    state: DownloadState.Downloading,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress / 100f,
        label = "download_progress"
    )

    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth()
        )
        if (showLabel) {
            Text(
                text = "Downloading… ${state.progress}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
