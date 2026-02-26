package com.william.astuterepo.ui.applist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.william.astuterepo.R
import com.william.astuterepo.domain.AppWithStatus
import com.william.astuterepo.domain.DownloadState
import com.william.astuterepo.domain.InstallStatus

@Composable
fun AppCard(
    appWithStatus: AppWithStatus,
    downloadState: DownloadState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(appWithStatus.app.iconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "${appWithStatus.app.name} icon",
                placeholder = painterResource(R.drawable.ic_app_placeholder),
                error = painterResource(R.drawable.ic_app_placeholder),
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = appWithStatus.app.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${appWithStatus.app.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = appWithStatus.app.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedContent(
                targetState = when {
                    downloadState is DownloadState.Downloading -> "downloading"
                    downloadState is DownloadState.Installing ||
                        downloadState is DownloadState.Downloaded -> "installing"
                    else -> "status_${appWithStatus.status.name}"
                },
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                        fadeOut(animationSpec = tween(200))
                },
                label = "status_transition"
            ) { targetState ->
                when (targetState) {
                    "downloading" -> {
                        val state = downloadState as? DownloadState.Downloading
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { (state?.progress ?: 0) / 100f },
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "${state?.progress ?: 0}%",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    "installing" -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = "Installing",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 6.dp
                                )
                            )
                        }
                    }
                    else -> StatusBadge(status = appWithStatus.status)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: InstallStatus) {
    val (text, containerColor, contentColor) = when (status) {
        InstallStatus.NOT_INSTALLED -> Triple(
            "Install",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        InstallStatus.UP_TO_DATE -> Triple(
            "Installed",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        InstallStatus.UPDATE_AVAILABLE -> Triple(
            "Update",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
