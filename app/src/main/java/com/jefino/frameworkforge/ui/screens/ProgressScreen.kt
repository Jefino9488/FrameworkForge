package com.jefino.frameworkforge.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import com.jefino.frameworkforge.core.RootManager
import com.jefino.frameworkforge.model.PatchingState
import com.jefino.frameworkforge.ui.components.ProgressCard
import com.jefino.frameworkforge.ui.components.StatusBanner
import com.jefino.frameworkforge.ui.components.TerminalLog
import com.jefino.frameworkforge.ui.theme.AppColors
import com.jefino.frameworkforge.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onComplete: () -> Unit
) {
    val patchingState by viewModel.patchingState.collectAsState()
    val logs by viewModel.logs.collectAsState()

    var showCancelDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Show success dialog when patching completes
    if (patchingState is PatchingState.Success && !showSuccessDialog) {
        showSuccessDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patching Progress", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { showCancelDialog = true }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.DarkBackground,
                    titleContentColor = AppColors.TextPrimary
                )
            )
        },
        containerColor = AppColors.DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status card based on current state
            when (val state = patchingState) {
                is PatchingState.Extracting -> {
                    ProgressCard(
                        title = "Extracting Files",
                        subtitle = state.currentFile,
                        progress = state.filesExtracted.toFloat() / state.totalFiles
                    )
                }
                is PatchingState.Uploading -> {
                    ProgressCard(
                        title = "Uploading ${state.currentFile}",
                        subtitle = "File ${state.filesUploaded + 1} of ${state.totalFiles}",
                        progress = state.progress / 100f
                    )
                }
                is PatchingState.TriggeringWorkflow -> {
                    StatusBanner(
                        title = "Triggering Workflow",
                        subtitle = "Sending request to cloud patcher..."
                    )
                }
                is PatchingState.WaitingForBuild -> {
                    val elapsed = state.elapsedSeconds
                    val minutes = elapsed / 60
                    val seconds = elapsed % 60
                    StatusBanner(
                        title = "Building in Cloud",
                        subtitle = "Elapsed: ${minutes}m ${seconds}s${state.runId?.let { " • Run: $it" } ?: ""}"
                    )
                }
                is PatchingState.Downloading -> {
                    ProgressCard(
                        title = "Downloading Module",
                        subtitle = "Fetching patched module...",
                        progress = state.progress / 100f
                    )
                }
                is PatchingState.ReadyToInstall -> {
                    val context = LocalContext.current
                    
                    LaunchedEffect(Unit) {
                        try {
                            val file = File(state.filePath)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/zip")
                                clipData = ClipData.newRawUri(null, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                RootManager.getManagerPackageName()?.let { pkg ->
                                    setPackage(pkg)
                                    context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                }
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback or ignore
                        }
                    }

                    Column {
                        StatusBanner(
                            title = "Download Complete",
                            subtitle = "Please complete installation in your Root Manager"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val file = File(state.filePath)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/zip")
                                        clipData = ClipData.newRawUri(null, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        RootManager.getManagerPackageName()?.let { pkg ->
                                            setPackage(pkg)
                                            context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        }
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) {
                            Text("Open Root Manager Again")
                        }
                         Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onComplete,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    }
                }
                is PatchingState.Installing -> {
                    StatusBanner(
                        title = "Installing Module",
                        subtitle = "Installing via Magisk..."
                    )
                }
                is PatchingState.Error -> {
                    StatusBanner(
                        title = "Error Occurred",
                        subtitle = state.message,
                        isError = true
                    )
                }
                is PatchingState.Success -> {
                    StatusBanner(
                        title = "Success!",
                        subtitle = "Module installed successfully"
                    )
                }
                else -> {
                    StatusBanner(
                        title = "Initializing",
                        subtitle = "Starting patching process..."
                    )
                }
            }

            // Terminal log
            Text(
                text = "Log Output",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextSecondary
            )

            TerminalLog(
                logs = logs,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Action buttons
            AnimatedVisibility(
                visible = patchingState is PatchingState.Error,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if ((patchingState as? PatchingState.Error)?.recoverable == true) {
                        Button(
                            onClick = { viewModel.startPatching() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) {
                            Text("Retry", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.resetState()
                            onNavigateBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Go Back", modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }

    // Cancel dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Patching?") },
            text = { Text("Are you sure you want to cancel the patching process?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        viewModel.resetState()
                        onNavigateBack()
                    }
                ) {
                    Text("Cancel Process", color = AppColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Continue")
                }
            },
            containerColor = AppColors.DarkSurface
        )
    }

    // Success dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { 
                Text(
                    "Module Installed!",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                ) 
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "The patched framework module has been installed successfully.",
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "A reboot is required to apply changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reboot()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text("Reboot Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        viewModel.resetState()
                        onComplete()
                    }
                ) {
                    Text("Later")
                }
            },
            containerColor = AppColors.DarkSurface
        )
    }
}
