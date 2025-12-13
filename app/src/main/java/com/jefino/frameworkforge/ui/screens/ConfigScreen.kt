package com.jefino.frameworkforge.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jefino.frameworkforge.core.FeatureManager
import com.jefino.frameworkforge.model.PatchingMode
import com.jefino.frameworkforge.model.SelectedFile
import com.jefino.frameworkforge.ui.components.DeviceInfoCard
import com.jefino.frameworkforge.ui.components.FeatureCheckbox
import com.jefino.frameworkforge.ui.theme.AppColors
import com.jefino.frameworkforge.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onStartPatching: () -> Unit
) {
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val features by viewModel.features.collectAsState()
    val localPatchFeatures by viewModel.localPatchFeatures.collectAsState()
    val isRootAvailable by viewModel.isRootAvailable.collectAsState()
    val patchingMode by viewModel.patchingMode.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val useLocalPatching by viewModel.useLocalPatching.collectAsState()

    val context = LocalContext.current

    var currentFileType by remember { mutableStateOf("") }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else currentFileType
                    val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                    viewModel.addSelectedFile(currentFileType, uri, name, size)
                }
            }
        }
    }

    val enabledFeatures = features.filter { it.isEnabled }
    val featureSummary = FeatureManager.getEnabledFeaturesSummary(features)

    val canStartPatching = when (patchingMode) {
        PatchingMode.AUTO_EXTRACT -> isRootAvailable && deviceInfo.hasFrameworkJar && deviceInfo.hasServicesJar
        PatchingMode.MANUAL_SELECT -> selectedFiles.containsKey("framework.jar") && selectedFiles.containsKey("services.jar")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Patching", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            DeviceInfoCard(deviceInfo = deviceInfo)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Source Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = patchingMode == PatchingMode.AUTO_EXTRACT,
                    onClick = { viewModel.setPatchingMode(PatchingMode.AUTO_EXTRACT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    enabled = isRootAvailable,
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = AppColors.Primary,
                        activeContentColor = AppColors.TextPrimary,
                        inactiveContainerColor = AppColors.DarkSurfaceVariant,
                        inactiveContentColor = AppColors.TextSecondary
                    )
                ) {
                    Text("Auto Extract")
                }
                SegmentedButton(
                    selected = patchingMode == PatchingMode.MANUAL_SELECT,
                    onClick = { viewModel.setPatchingMode(PatchingMode.MANUAL_SELECT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = AppColors.Primary,
                        activeContentColor = AppColors.TextPrimary,
                        inactiveContainerColor = AppColors.DarkSurfaceVariant,
                        inactiveContentColor = AppColors.TextSecondary
                    )
                ) {
                    Text("Manual Select")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (patchingMode == PatchingMode.AUTO_EXTRACT) {
                    if (isRootAvailable) "Extract from /system using root" 
                    else "Root required"
                } else {
                    "Select files manually"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )

            AnimatedVisibility(
                visible = patchingMode == PatchingMode.MANUAL_SELECT,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    FileSelectionCard(
                        fileType = "framework.jar",
                        isRequired = true,
                        selectedFile = selectedFiles["framework.jar"],
                        onSelectClick = {
                            currentFileType = "framework.jar"
                            filePickerLauncher.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*"))
                        },
                        onRemoveClick = { viewModel.removeSelectedFile("framework.jar") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FileSelectionCard(
                        fileType = "services.jar",
                        isRequired = true,
                        selectedFile = selectedFiles["services.jar"],
                        onSelectClick = {
                            currentFileType = "services.jar"
                            filePickerLauncher.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*"))
                        },
                        onRemoveClick = { viewModel.removeSelectedFile("services.jar") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FileSelectionCard(
                        fileType = "miui-services.jar",
                        isRequired = false,
                        selectedFile = selectedFiles["miui-services.jar"],
                        onSelectClick = {
                            currentFileType = "miui-services.jar"
                            filePickerLauncher.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*"))
                        },
                        onRemoveClick = { viewModel.removeSelectedFile("miui-services.jar") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Patching Method Selection
            Text(
                text = "Patching Method",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = useLocalPatching,
                    onClick = { viewModel.setUseLocalPatching(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = AppColors.Primary,
                        activeContentColor = AppColors.TextPrimary,
                        inactiveContainerColor = AppColors.DarkSurfaceVariant,
                        inactiveContentColor = AppColors.TextSecondary
                    )
                ) {
                    Text("Local Patching")
                }
                SegmentedButton(
                    selected = !useLocalPatching,
                    onClick = { viewModel.setUseLocalPatching(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = AppColors.Primary,
                        activeContentColor = AppColors.TextPrimary,
                        inactiveContainerColor = AppColors.DarkSurfaceVariant,
                        inactiveContentColor = AppColors.TextSecondary
                    )
                ) {
                    Text("Cloud Patching")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (useLocalPatching)
                    "Patch on-device using DynamicInstaller (faster, requires root)"
                else
                    "Upload files for cloud patching (works without root)",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (useLocalPatching) "Available Patches" else "Select Features",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (useLocalPatching) 
                    "Patches from features folder (${localPatchFeatures.size} available)" 
                else 
                    "Choose which patches to apply to your framework",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (useLocalPatching) {
                    // Show local patch features from features folder
                    localPatchFeatures.forEach { feature ->
                        LocalPatchFeatureItem(
                            feature = feature,
                            onCheckedChange = { enabled ->
                                viewModel.updateLocalPatchFeature(feature.id, enabled)
                            }
                        )
                    }
                    if (localPatchFeatures.isEmpty()) {
                        Text(
                            text = "No patches found in features folder",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextMuted
                        )
                    }
                } else {
                    // Show cloud features
                    features.forEach { feature ->
                        FeatureCheckbox(
                            feature = feature,
                            onCheckedChange = { enabled ->
                                viewModel.updateFeature(feature.id, enabled)
                            },
                            enabled = !feature.requiresMiui || (patchingMode == PatchingMode.MANUAL_SELECT && selectedFiles.containsKey("miui-services.jar")) || deviceInfo.hasMiuiServicesJar
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Selected:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
                Text(
                    text = if (useLocalPatching) {
                        val enabled = localPatchFeatures.count { it.isEnabled }
                        if (enabled == 0) "None" else "$enabled patch(es)"
                    } else featureSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (useLocalPatching) {
                        viewModel.startLocalPatching()
                    } else {
                        viewModel.startPatching()
                    }
                    onStartPatching()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                enabled = canStartPatching && enabledFeatures.isNotEmpty()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(
                    text = if (useLocalPatching) "Start Local Patching" else "Start Cloud Patching",
                    modifier = Modifier.padding(vertical = 8.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!canStartPatching) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        patchingMode == PatchingMode.AUTO_EXTRACT && !isRootAvailable -> "Root access required for auto extraction"
                        patchingMode == PatchingMode.AUTO_EXTRACT && !deviceInfo.hasFrameworkJar -> "framework.jar not found on device"
                        patchingMode == PatchingMode.MANUAL_SELECT -> "Please select framework.jar and services.jar"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (useLocalPatching) 
                    "Patches will be applied locally using DynamicInstaller. A Magisk module will be created and saved to Downloads."
                else
                    "Files will be uploaded to the cloud for patching. The resulting Magisk module will be downloaded and installed.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted
            )
        }
    }
}

@Composable
private fun FileSelectionCard(
    fileType: String,
    isRequired: Boolean,
    selectedFile: SelectedFile?,
    onSelectClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selectedFile != null) AppColors.Success.copy(alpha = 0.1f) else AppColors.DarkSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedFile != null) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppColors.Success, modifier = Modifier.size(24.dp))
            } else {
                Box(
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(AppColors.DarkCard),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fileType,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextPrimary
                    )
                    if (isRequired) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Required",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.Error,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(AppColors.Error.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (selectedFile != null) {
                    Text(
                        text = "${selectedFile.name} (${selectedFile.size / 1024} KB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                } else {
                    Text(
                        text = "Tap to select file",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted
                    )
                }
            }
            if (selectedFile != null) {
                IconButton(onClick = onRemoveClick) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = AppColors.Error)
                }
            } else {
                OutlinedButton(onClick = onSelectClick, shape = RoundedCornerShape(8.dp)) {
                    Text("Select")
                }
            }
        }
    }
}

@Composable
private fun LocalPatchFeatureItem(
    feature: com.jefino.frameworkforge.core.LocalPatchFeature,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (feature.isEnabled) AppColors.Primary.copy(alpha = 0.1f)
                else AppColors.DarkSurfaceVariant.copy(alpha = 0.5f)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Checkbox(
            checked = feature.isEnabled,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.CheckboxDefaults.colors(
                checkedColor = AppColors.Primary,
                uncheckedColor = AppColors.TextMuted,
                checkmarkColor = AppColors.TextPrimary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary
            )
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )
        }
    }
}
