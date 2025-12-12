package com.jefino.frameworkforge.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jefino.frameworkforge.core.ApiKeyManager
import com.jefino.frameworkforge.core.FeatureManager
import com.jefino.frameworkforge.core.RootManager
import com.jefino.frameworkforge.core.SystemInspector
import com.jefino.frameworkforge.data.api.GitHubRelease
import com.jefino.frameworkforge.data.repository.UploadRepository
import com.jefino.frameworkforge.data.repository.WorkflowRepository
import com.jefino.frameworkforge.model.DeviceInfo
import com.jefino.frameworkforge.model.Feature
import com.jefino.frameworkforge.model.LogEntry
import com.jefino.frameworkforge.model.LogTag
import com.jefino.frameworkforge.model.PatchingMode
import com.jefino.frameworkforge.model.PatchingState
import com.jefino.frameworkforge.model.SelectedFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Main ViewModel for orchestrating the patching flow
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val workflowRepository = WorkflowRepository()
    private val uploadRepository: UploadRepository by lazy {
        UploadRepository(ApiKeyManager.getPixeldrainApiKey())
    }

    // State
    private val _deviceInfo = MutableStateFlow(DeviceInfo.Empty)
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()

    private val _magiskVersion = MutableStateFlow<String?>(null)
    val magiskVersion: StateFlow<String?> = _magiskVersion.asStateFlow()

    private val _features = MutableStateFlow<List<Feature>>(emptyList())
    val features: StateFlow<List<Feature>> = _features.asStateFlow()

    private val _patchingState = MutableStateFlow<PatchingState>(PatchingState.Idle)
    val patchingState: StateFlow<PatchingState> = _patchingState.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _downloadedModulePath = MutableStateFlow<String?>(null)
    val downloadedModulePath: StateFlow<String?> = _downloadedModulePath.asStateFlow()

    // Patching mode
    private val _patchingMode = MutableStateFlow(PatchingMode.AUTO_EXTRACT)
    val patchingMode: StateFlow<PatchingMode> = _patchingMode.asStateFlow()

    // Manually selected files
    private val _selectedFiles = MutableStateFlow<Map<String, SelectedFile>>(emptyMap())
    val selectedFiles: StateFlow<Map<String, SelectedFile>> = _selectedFiles.asStateFlow()

    // Existing releases that match this device
    private val _matchingReleases = MutableStateFlow<List<GitHubRelease>>(emptyList())
    val matchingReleases: StateFlow<List<GitHubRelease>> = _matchingReleases.asStateFlow()

    private val _isLoadingReleases = MutableStateFlow(false)
    val isLoadingReleases: StateFlow<Boolean> = _isLoadingReleases.asStateFlow()

    init {
        checkRootAndScan()
    }

    fun setPatchingMode(mode: PatchingMode) {
        _patchingMode.value = mode
        if (mode == PatchingMode.AUTO_EXTRACT) {
            _selectedFiles.value = emptyMap()
        }
    }

    fun addSelectedFile(fileType: String, uri: Uri, name: String, size: Long) {
        _selectedFiles.value = _selectedFiles.value + (fileType to SelectedFile(name, uri, size))
    }

    fun removeSelectedFile(fileType: String) {
        _selectedFiles.value = _selectedFiles.value - fileType
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyMap()
    }

    private fun checkRootAndScan() {
        viewModelScope.launch {
            _patchingState.value = PatchingState.CheckingRoot

            val hasRoot = RootManager.requestRoot()
            _isRootAvailable.value = hasRoot

            if (hasRoot) {
                _magiskVersion.value = RootManager.getMagiskVersion()
                addLog(LogTag.INFO, "Root access granted")

                _patchingState.value = PatchingState.Scanning("Scanning device...")
                val info = SystemInspector.getDeviceInfo()
                _deviceInfo.value = info
                _features.value = FeatureManager.getAvailableFeatures(info.hasMiuiServicesJar)

                addLog(LogTag.INFO, "Device: ${info.deviceName} (${info.deviceCodename})")
                addLog(LogTag.INFO, "Android ${info.androidVersion} (API ${info.apiLevel})")

                if (info.hasFrameworkJar) addLog(LogTag.INFO, "Found framework.jar")
                if (info.hasServicesJar) addLog(LogTag.INFO, "Found services.jar")
                if (info.hasMiuiServicesJar) addLog(LogTag.INFO, "Found miui-services.jar")

                // Check for existing releases
                fetchMatchingReleases(info)
            } else {
                addLog(LogTag.ERROR, "Root access denied - Manual mode available")
                _patchingState.value = PatchingState.Scanning("Collecting device info...")
                val info = SystemInspector.getDeviceInfo()
                _deviceInfo.value = info
                _features.value = FeatureManager.getAvailableFeatures(false)
                
                // Still check for releases even without root
                fetchMatchingReleases(info)
            }

            _patchingState.value = PatchingState.Idle
        }
    }

    /**
     * Fetch existing releases that match this device
     */
    fun fetchMatchingReleases(deviceInfo: DeviceInfo = _deviceInfo.value) {
        if (deviceInfo == DeviceInfo.Empty) return
        
        viewModelScope.launch {
            _isLoadingReleases.value = true
            
            val result = workflowRepository.findMatchingReleases(
                deviceCodename = deviceInfo.deviceCodename,
                versionSafe = deviceInfo.safeVersionName
            )
            
            result.fold(
                onSuccess = { releases ->
                    _matchingReleases.value = releases
                    if (releases.isNotEmpty()) {
                        addLog(LogTag.INFO, "Found ${releases.size} existing build(s) for your device")
                    }
                },
                onFailure = { 
                    _matchingReleases.value = emptyList()
                }
            )
            
            _isLoadingReleases.value = false
        }
    }

    /**
     * Download and install an existing release
     */
    fun downloadAndInstallRelease(release: GitHubRelease) {
        viewModelScope.launch {
            try {
                val asset = release.findModuleZip()
                if (asset == null) {
                    _patchingState.value = PatchingState.Error("No module zip found in this release", recoverable = true)
                    return@launch
                }

                addLog(LogTag.INFO, "Downloading existing release: ${release.tagName}")
                downloadModule(asset.browserDownloadUrl, asset.name)

            } catch (e: Exception) {
                addLog(LogTag.ERROR, "Error: ${e.message}")
                _patchingState.value = PatchingState.Error(e.message ?: "Unknown error", recoverable = true)
            }
        }
    }

    fun updateFeature(featureId: String, enabled: Boolean) {
        _features.value = FeatureManager.updateFeature(_features.value, featureId, enabled)
    }

    fun startPatching() {
        viewModelScope.launch {
            val mode = _patchingMode.value

            try {
                val extractedFiles = when (mode) {
                    PatchingMode.AUTO_EXTRACT -> {
                        val info = _deviceInfo.value
                        if (!info.hasFrameworkJar || !info.hasServicesJar) {
                            _patchingState.value = PatchingState.Error("Required framework files not found", recoverable = false)
                            return@launch
                        }
                        extractFrameworkFiles()
                    }
                    PatchingMode.MANUAL_SELECT -> {
                        val selected = _selectedFiles.value
                        if (!selected.containsKey("framework.jar") || !selected.containsKey("services.jar")) {
                            _patchingState.value = PatchingState.Error("Please select framework.jar and services.jar", recoverable = true)
                            return@launch
                        }
                        copySelectedFiles()
                    }
                }

                if (extractedFiles.isEmpty()) {
                    _patchingState.value = PatchingState.Error("No files were extracted", recoverable = false)
                    return@launch
                }

                val uploadedUrls = uploadFiles(extractedFiles)
                if (uploadedUrls.isEmpty()) {
                    _patchingState.value = PatchingState.Error("File upload failed", recoverable = true)
                    return@launch
                }

                val info = _deviceInfo.value
                val featureString = FeatureManager.buildFeatureString(_features.value)
                triggerWorkflow(info, uploadedUrls, featureString)
                waitForRelease(info)

            } catch (e: Exception) {
                addLog(LogTag.ERROR, "Error: ${e.message}")
                _patchingState.value = PatchingState.Error(e.message ?: "Unknown error", recoverable = true)
            } finally {
                RootManager.cleanup(getApplication<Application>().filesDir)
            }
        }
    }

    private suspend fun extractFrameworkFiles(): Map<String, File> {
        val files = mutableMapOf<String, File>()
        val filesDir = getApplication<Application>().filesDir
        val frameworkPaths = SystemInspector.getAvailableFrameworkPaths()
        val totalFiles = frameworkPaths.size
        var extracted = 0

        for ((name, path) in frameworkPaths) {
            _patchingState.value = PatchingState.Extracting(name, extracted, totalFiles)
            addLog(LogTag.EXTRACT, "Extracting $name...")

            val result = RootManager.extractSystemFile(path, filesDir, name)
            result.fold(
                onSuccess = { file ->
                    files[name] = file
                    extracted++
                    addLog(LogTag.EXTRACT, "Extracted $name (${file.length() / 1024} KB)")
                },
                onFailure = { error ->
                    addLog(LogTag.ERROR, "Failed to extract $name: ${error.message}")
                }
            )
        }

        return files
    }

    private suspend fun copySelectedFiles(): Map<String, File> {
        val files = mutableMapOf<String, File>()
        val filesDir = getApplication<Application>().filesDir
        val context = getApplication<Application>()
        val selected = _selectedFiles.value
        val totalFiles = selected.size
        var copied = 0

        for ((name, selectedFile) in selected) {
            _patchingState.value = PatchingState.Extracting(name, copied, totalFiles)
            addLog(LogTag.EXTRACT, "Copying $name from storage...")

            try {
                val destFile = File(filesDir, name)
                context.contentResolver.openInputStream(selectedFile.uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                files[name] = destFile
                copied++
                addLog(LogTag.EXTRACT, "Copied $name (${destFile.length() / 1024} KB)")
            } catch (e: Exception) {
                addLog(LogTag.ERROR, "Failed to copy $name: ${e.message}")
            }
        }

        return files
    }

    private suspend fun uploadFiles(files: Map<String, File>): Map<String, String> {
        val totalFiles = files.size
        var uploaded = 0

        val result = uploadRepository.uploadFiles(files) { fileName, progress ->
            _patchingState.value = PatchingState.Uploading(fileName, progress, uploaded, totalFiles)
        }

        return result.fold(
            onSuccess = { urls ->
                urls.forEach { (name, url) ->
                    uploaded++
                    addLog(LogTag.UPLOAD, "$name uploaded: $url")
                }
                urls
            },
            onFailure = { error ->
                addLog(LogTag.ERROR, "Upload failed: ${error.message}")
                emptyMap()
            }
        )
    }

    private suspend fun triggerWorkflow(
        deviceInfo: DeviceInfo,
        urls: Map<String, String>,
        features: String
    ) {
        _patchingState.value = PatchingState.TriggeringWorkflow
        addLog(LogTag.REMOTE, "Triggering cloud patching workflow...")

        val result = workflowRepository.triggerWorkflow(
            deviceInfo = deviceInfo,
            frameworkUrl = urls["framework.jar"] ?: "",
            servicesUrl = urls["services.jar"] ?: "",
            miuiServicesUrl = urls["miui-services.jar"] ?: "",
            features = features
        )

        result.fold(
            onSuccess = { response ->
                addLog(LogTag.REMOTE, "Workflow triggered! Run ID: ${response.runId}")
                _patchingState.value = PatchingState.WaitingForBuild(runId = response.runId?.toString())
            },
            onFailure = { error ->
                throw error
            }
        )
    }

    private suspend fun waitForRelease(deviceInfo: DeviceInfo) {
        addLog(LogTag.WAITING, "Waiting for build to complete...")

        val result = workflowRepository.pollForRelease(
            deviceCodename = deviceInfo.deviceCodename,
            versionSafe = deviceInfo.safeVersionName,
            onPoll = { attempt ->
                _patchingState.value = PatchingState.WaitingForBuild(
                    elapsedSeconds = attempt * 30
                )
                if (attempt % 2 == 0) {
                    addLog(LogTag.WAITING, "Still waiting... (${attempt * 30}s elapsed)")
                }
            }
        )

        result.fold(
            onSuccess = { release ->
                addLog(LogTag.SUCCESS, "Build complete! Release: ${release.tagName}")
                val asset = release.findModuleZip()
                if (asset != null) {
                    downloadModule(asset.browserDownloadUrl, asset.name)
                } else {
                    throw Exception("No module zip found in release")
                }
            },
            onFailure = { error ->
                throw error
            }
        )
    }

    private suspend fun downloadModule(url: String, fileName: String) {
        addLog(LogTag.DOWNLOAD, "Downloading module...")
        _patchingState.value = PatchingState.Downloading(0)

        val context = getApplication<Application>()
        val result = workflowRepository.downloadFile(context, url, fileName) { progress ->
            _patchingState.value = PatchingState.Downloading(progress)
        }

        result.fold(
            onSuccess = { file ->
                addLog(LogTag.DOWNLOAD, "Downloaded: ${file.absolutePath}")
                _downloadedModulePath.value = file.absolutePath
                _patchingState.value = PatchingState.ReadyToInstall(file.absolutePath)
            },
            onFailure = { error ->
                throw error
            }
        )
    }

    fun reboot() {
        viewModelScope.launch {
            RootManager.reboot()
        }
    }

    fun resetState() {
        _patchingState.value = PatchingState.Idle
        _logs.value = emptyList()
        _downloadedModulePath.value = null
    }

    private fun addLog(tag: LogTag, message: String) {
        _logs.value = _logs.value + LogEntry(tag, message)
    }

    fun refreshDeviceInfo() {
        checkRootAndScan()
    }

    /**
     * Install module directly using root shell commands (fallback for permission issues)
     */
    fun installModuleDirectly(modulePath: String) {
        viewModelScope.launch {
            try {
                _patchingState.value = PatchingState.Installing
                addLog(LogTag.INSTALL, "Installing module directly via root...")

                val result = RootManager.installMagiskModule(modulePath)

                result.fold(
                    onSuccess = {
                        addLog(LogTag.SUCCESS, "Module installed successfully!")
                        _patchingState.value = PatchingState.Success
                    },
                    onFailure = { error ->
                        addLog(LogTag.ERROR, "Installation failed: ${error.message}")
                        _patchingState.value = PatchingState.Error(error.message ?: "Installation failed", recoverable = true)
                    }
                )
            } catch (e: Exception) {
                addLog(LogTag.ERROR, "Error: ${e.message}")
                _patchingState.value = PatchingState.Error(e.message ?: "Unknown error", recoverable = true)
            }
        }
    }
}
