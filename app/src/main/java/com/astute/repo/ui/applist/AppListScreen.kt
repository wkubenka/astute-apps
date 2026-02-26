package com.astute.repo.ui.applist

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astute.repo.ui.components.StalenessBanner
import com.astute.repo.ui.components.UnknownSourcesPermissionDialog
import com.astute.repo.ui.detail.AppDetailSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onPermissionGrantResult()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.recheckStatuses()
        onPauseOrDispose { }
    }

    LaunchedEffect(uiState.errorState) {
        val error = uiState.errorState
        if (error is ErrorState.Transient) {
            snackbarHostState.showSnackbar(error.message)
            viewModel.clearTransientError()
        }
    }

    val installIntent = uiState.installIntent
    LaunchedEffect(installIntent) {
        installIntent?.let {
            context.startActivity(it)
            viewModel.onInstallIntentLaunched()
        }
    }

    if (uiState.showPermissionDialog) {
        UnknownSourcesPermissionDialog(
            onConfirm = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
                permissionLauncher.launch(intent)
            },
            onDismiss = { viewModel.onPermissionDialogDismiss() }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Astute Repo") },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refreshManifest() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val filteredApps = remember(uiState.apps, uiState.searchQuery) {
                if (uiState.searchQuery.isBlank()) {
                    uiState.apps
                } else {
                    uiState.apps.filter {
                        it.app.name.contains(uiState.searchQuery, ignoreCase = true)
                    }
                }
            }

            when {
                // Initial loading — show skeletons
                uiState.apps.isEmpty() && uiState.isLoading -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(5) { AppCardSkeleton() }
                    }
                }

                // Empty state — no apps loaded
                uiState.apps.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No apps available",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Pull to refresh",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Normal state — show apps with search
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        // Search bar
                        if (uiState.apps.isNotEmpty() || uiState.searchQuery.isNotEmpty()) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::onSearchQueryChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                placeholder = { Text("Search apps") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.onSearchQueryChanged("") }
                                        ) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Clear search"
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(28.dp)
                            )
                        }

                        if (filteredApps.isEmpty() && uiState.searchQuery.isNotEmpty()) {
                            // Empty search results
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No results for \"${uiState.searchQuery}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Staleness banner
                                val offlineError = uiState.errorState
                                if (offlineError is ErrorState.Offline) {
                                    item(key = "staleness_banner") {
                                        StalenessBanner(
                                            lastFetchTimeMillis = offlineError.lastFetchTimeMillis,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                }

                                items(
                                    items = filteredApps,
                                    key = { it.app.id }
                                ) { appWithStatus ->
                                    AppCard(
                                        appWithStatus = appWithStatus,
                                        downloadState = uiState.downloadStates[appWithStatus.app.id],
                                        onClick = { viewModel.selectApp(appWithStatus) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.selectedApp?.let { selected ->
        AppDetailSheet(
            appWithStatus = selected,
            downloadState = uiState.downloadStates[selected.app.id],
            onDismiss = { viewModel.clearSelection() },
            onAction = { viewModel.downloadAndInstall(selected.app) },
            onCancel = { viewModel.cancelDownload(selected.app.id) }
        )
    }
}
