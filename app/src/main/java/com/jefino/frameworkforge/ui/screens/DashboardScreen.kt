package com.jefino.frameworkforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import com.jefino.frameworkforge.data.api.GitHubRelease
import com.jefino.frameworkforge.model.PatchingState
import com.jefino.frameworkforge.ui.components.DeviceInfoCard
import com.jefino.frameworkforge.ui.components.RootStatusCard
import com.jefino.frameworkforge.ui.components.StatusBanner
import com.jefino.frameworkforge.ui.theme.AppColors
import com.jefino.frameworkforge.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToConfig: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProgress: () -> Unit = {}
) {
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isRootAvailable by viewModel.isRootAvailable.collectAsState()
    val magiskVersion by viewModel.magiskVersion.collectAsState()
    val patchingState by viewModel.patchingState.collectAsState()
    val matchingReleases by viewModel.matchingReleases.collectAsState()
    val isLoadingReleases by viewModel.isLoadingReleases.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Proceed regardless of result - repository has fallback logic
        // But we want to try getting permission first
    }

    val isLoading = patchingState is PatchingState.CheckingRoot || patchingState is PatchingState.Scanning

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "FrameworkForge",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshDeviceInfo() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.DarkBackground,
                    titleContentColor = AppColors.TextPrimary
                )
            )
        },
        floatingActionButton = {
            if (!isLoading) {
                FloatingActionButton(
                    onClick = onNavigateToConfig,
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.TextPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Start")
                }
            }
        },
        containerColor = AppColors.DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                AppColors.GradientStart.copy(alpha = 0.2f),
                                AppColors.GradientEnd.copy(alpha = 0.1f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Cloud Framework Patcher",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Extract, patch, and install framework modifications",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                }
            }

            // Root status
            RootStatusCard(
                isRootAvailable = isRootAvailable,
                magiskVersion = magiskVersion
            )

            // Manual mode hint if root not available
            if (!isRootAvailable && !isLoading) {
                StatusBanner(
                    title = "Manual Mode Available",
                    subtitle = "You can still use manual file selection without root"
                )
            }

            // Device info
            if (!isLoading && deviceInfo != com.jefino.frameworkforge.model.DeviceInfo.Empty) {
                DeviceInfoCard(deviceInfo = deviceInfo)
            }

            // Existing Releases Section
            if (!isLoading && (matchingReleases.isNotEmpty() || isLoadingReleases)) {
                ExistingReleasesSection(
                    releases = matchingReleases,
                    isLoading = isLoadingReleases,
                    onDownloadClick = { release ->
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        viewModel.downloadAndInstallRelease(release)
                        onNavigateToProgress()
                    },
                    onRefreshClick = { viewModel.fetchMatchingReleases() }
                )
            }

            // Loading state
            if (isLoading) {
                StatusBanner(
                    title = when (patchingState) {
                        is PatchingState.CheckingRoot -> "Checking root access..."
                        is PatchingState.Scanning -> "Scanning device..."
                        else -> "Loading..."
                    },
                    subtitle = "Please wait"
                )
            }

            // Error state
            if (patchingState is PatchingState.Error) {
                StatusBanner(
                    title = "Error",
                    subtitle = (patchingState as PatchingState.Error).message,
                    isError = true
                )
            }

            // Start button
            if (!isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onNavigateToConfig,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        if (isRootAvailable) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        } else {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRootAvailable) "Configure & Start Patching" else "Manual Mode - Select Files",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
        }
    }
}

@Composable
private fun ExistingReleasesSection(
    releases: List<GitHubRelease>,
    isLoading: Boolean,
    onDownloadClick: (GitHubRelease) -> Unit,
    onRefreshClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Existing Builds",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                IconButton(onClick = onRefreshClick, modifier = Modifier.size(32.dp)) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AppColors.Primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = AppColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Found ${releases.size} compatible build(s) - download and install directly",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            releases.take(5).forEach { release ->
                ReleaseItem(
                    release = release,
                    onDownloadClick = { onDownloadClick(release) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ReleaseItem(
    release: GitHubRelease,
    onDownloadClick: () -> Unit
) {
    val moduleAsset = release.findModuleZip()
    val sizeText = moduleAsset?.let { 
        val sizeMB = it.size / (1024.0 * 1024.0)
        "%.1f MB".format(sizeMB)
    } ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.DarkSurfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = AppColors.Success,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (sizeText.isNotEmpty()) {
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted
                    )
                }
            }
            OutlinedButton(
                onClick = onDownloadClick,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Install")
            }
        }
    }
}
