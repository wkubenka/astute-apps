package com.astute.repo.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.astute.repo.R
import com.astute.repo.domain.AppWithStatus
import com.astute.repo.domain.DownloadState
import com.astute.repo.domain.InstallStatus
import com.astute.repo.ui.components.DownloadProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailSheet(
    appWithStatus: AppWithStatus,
    downloadState: DownloadState?,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onCancel: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val app = appWithStatus.app

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: icon + name + version
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(app.iconUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "${app.name} icon",
                    placeholder = painterResource(R.drawable.ic_app_placeholder),
                    error = painterResource(R.drawable.ic_app_placeholder),
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "v${app.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = appWithStatus.status)
            }

            // Version comparison
            if (appWithStatus.status == InstallStatus.UPDATE_AVAILABLE && appWithStatus.installedVersionName != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Installed: v${appWithStatus.installedVersionName} \u2192 Available: v${app.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // Description
            Spacer(Modifier.height(16.dp))
            Text(
                text = app.description,
                style = MaterialTheme.typography.bodyMedium
            )

            // Changelog
            if (!app.changelog.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Changelog",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = app.changelog,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action button
            Spacer(Modifier.height(24.dp))
            when {
                downloadState is DownloadState.Downloading -> {
                    DownloadProgressIndicator(
                        state = downloadState,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
                downloadState is DownloadState.Installing ||
                    downloadState is DownloadState.Downloaded -> {
                    OutlinedButton(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Installing…")
                    }
                }
                downloadState is DownloadState.Failed -> {
                    Text(
                        text = downloadState.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry")
                    }
                }
                appWithStatus.status == InstallStatus.NOT_INSTALLED -> {
                    Button(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Install")
                    }
                }
                appWithStatus.status == InstallStatus.UPDATE_AVAILABLE -> {
                    Button(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Update")
                    }
                }
                appWithStatus.status == InstallStatus.UP_TO_DATE -> {
                    OutlinedButton(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Installed")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: InstallStatus) {
    val (text, containerColor, contentColor) = when (status) {
        InstallStatus.NOT_INSTALLED -> Triple(
            "Not Installed",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        InstallStatus.UP_TO_DATE -> Triple(
            "Up to Date",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        InstallStatus.UPDATE_AVAILABLE -> Triple(
            "Update Available",
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
