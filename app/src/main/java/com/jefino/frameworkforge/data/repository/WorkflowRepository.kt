package com.jefino.frameworkforge.data.repository

import android.content.Context
import com.jefino.frameworkforge.BuildConfig
import com.jefino.frameworkforge.data.NetworkModule
import com.jefino.frameworkforge.data.api.GitHubRelease
import com.jefino.frameworkforge.data.api.WorkflowInputs
import com.jefino.frameworkforge.data.api.WorkflowRequest
import com.jefino.frameworkforge.data.api.WorkflowResponse
import com.jefino.frameworkforge.model.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Repository for workflow operations and release management
 */
class WorkflowRepository {

    private val workflowApi = NetworkModule.workflowApi
    private val gitHubApi = NetworkModule.gitHubApi
    private val downloadClient = NetworkModule.downloadClient

    /**
     * Trigger the patching workflow
     */
    suspend fun triggerWorkflow(
        deviceInfo: DeviceInfo,
        frameworkUrl: String,
        servicesUrl: String,
        miuiServicesUrl: String,
        features: String
    ): Result<WorkflowResponse> = withContext(Dispatchers.IO) {
        try {
            val request = WorkflowRequest(
                version = deviceInfo.workflowVersion,
                inputs = WorkflowInputs(
                    apiLevel = deviceInfo.apiLevel.toString(),
                    deviceName = deviceInfo.deviceName,
                    deviceCodename = deviceInfo.deviceCodename,
                    versionName = deviceInfo.versionName,
                    frameworkUrl = frameworkUrl,
                    servicesUrl = servicesUrl,
                    miuiServicesUrl = miuiServicesUrl,
                    features = features
                )
            )

            val response = workflowApi.triggerWorkflow(request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body.error ?: body.message ?: "Workflow trigger failed"))
                }
            } else {
                Result.failure(
                    Exception("Workflow trigger failed: ${response.code()} - ${response.errorBody()?.string()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Find existing releases that match the device
     */
    suspend fun findMatchingReleases(
        deviceCodename: String,
        versionSafe: String
    ): Result<List<GitHubRelease>> = withContext(Dispatchers.IO) {
        try {
            val response = gitHubApi.getReleases(
                owner = BuildConfig.GITHUB_OWNER,
                repo = BuildConfig.GITHUB_REPO
            )

            if (response.isSuccessful) {
                val releases = response.body() ?: emptyList()
                
                // Find releases matching our device
                val matchingReleases = releases.filter { release ->
                    val hasModule = release.findModuleZip() != null
                    val matchesDevice = release.tagName.contains(deviceCodename, ignoreCase = true) ||
                                       release.tagName.contains(versionSafe.take(16), ignoreCase = true)
                    hasModule && matchesDevice
                }
                
                Result.success(matchingReleases)
            } else {
                Result.failure(Exception("Failed to fetch releases: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Poll for a matching release after workflow trigger
     */
    suspend fun pollForRelease(
        deviceCodename: String,
        versionSafe: String,
        pollIntervalMs: Long = 30_000,
        maxAttempts: Int = 60,
        onPoll: ((Int) -> Unit)? = null
    ): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        
        var lastKnownReleaseId: Long? = null
        try {
            val initialResponse = gitHubApi.getReleases(
                owner = BuildConfig.GITHUB_OWNER,
                repo = BuildConfig.GITHUB_REPO
            )
            if (initialResponse.isSuccessful) {
                lastKnownReleaseId = initialResponse.body()?.firstOrNull()?.id
            }
        } catch (e: Exception) {
            // Continue anyway
        }

        val tagPatterns = listOf(
            "${deviceCodename}_${versionSafe}",
            deviceCodename,
            versionSafe.take(20)
        )

        repeat(maxAttempts) { attempt ->
            onPoll?.invoke(attempt + 1)

            try {
                val response = gitHubApi.getReleases(
                    owner = BuildConfig.GITHUB_OWNER,
                    repo = BuildConfig.GITHUB_REPO
                )

                if (response.isSuccessful) {
                    val releases = response.body() ?: emptyList()
                    
                    val newReleases = releases.filter { release ->
                        val isNew = lastKnownReleaseId == null || release.id != lastKnownReleaseId
                        val hasModule = release.findModuleZip() != null
                        isNew && hasModule
                    }
                    
                    if (newReleases.isNotEmpty()) {
                        for (pattern in tagPatterns) {
                            val exactMatch = newReleases.firstOrNull { release ->
                                release.tagName.contains(pattern, ignoreCase = true)
                            }
                            if (exactMatch != null) {
                                return@withContext Result.success(exactMatch)
                            }
                        }
                        
                        val mostRecent = newReleases.firstOrNull()
                        if (mostRecent != null && attempt >= 3) {
                            return@withContext Result.success(mostRecent)
                        }
                    }
                    
                    val matchingRelease = releases.firstOrNull { release ->
                        tagPatterns.any { pattern ->
                            release.tagName.contains(pattern, ignoreCase = true)
                        } && release.findModuleZip() != null
                    }

                    if (matchingRelease != null) {
                        return@withContext Result.success(matchingRelease)
                    }
                }
            } catch (e: Exception) {
                // Continue polling
            }

            if (attempt < maxAttempts - 1) {
                delay(pollIntervalMs)
            }
        }

        Result.failure(Exception("Timeout waiting for release"))
    }

    /**
     * Download a file to app's cache directory (no storage permission needed)
     */
    suspend fun downloadFile(
        context: Context,
        url: String,
        fileName: String,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .build()

            val response = downloadClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Download failed: ${response.code}")
                )
            }

            val body = response.body ?: return@withContext Result.failure(
                Exception("Empty response body")
            )

            // Use public Downloads directory
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            downloadDir.mkdirs()
            val destinationFile = File(downloadDir, fileName)

            val contentLength = body.contentLength()
            var bytesWritten = 0L
            var lastProgress = -1

            body.byteStream().use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesWritten += read

                        if (contentLength > 0) {
                            val progress = ((bytesWritten * 100) / contentLength).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress?.invoke(progress)
                            }
                        }
                    }
                }
            }

            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Overload for backward compatibility
     */
    suspend fun downloadFile(
        url: String,
        fileName: String,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        // This version requires a context to be passed separately
        // For now, create a temp file in system temp dir
        try {
            val request = Request.Builder()
                .url(url)
                .build()

            val response = downloadClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Download failed: ${response.code}")
                )
            }

            val body = response.body ?: return@withContext Result.failure(
                Exception("Empty response body")
            )

            val destinationFile = File.createTempFile("module_", ".zip")

            val contentLength = body.contentLength()
            var bytesWritten = 0L
            var lastProgress = -1

            body.byteStream().use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesWritten += read

                        if (contentLength > 0) {
                            val progress = ((bytesWritten * 100) / contentLength).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress?.invoke(progress)
                            }
                        }
                    }
                }
            }

            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get recent releases
     */
    suspend fun getRecentReleases(limit: Int = 10): Result<List<GitHubRelease>> = 
        withContext(Dispatchers.IO) {
            try {
                val response = gitHubApi.getReleases(
                    owner = BuildConfig.GITHUB_OWNER,
                    repo = BuildConfig.GITHUB_REPO
                )

                if (response.isSuccessful) {
                    Result.success(response.body()?.take(limit) ?: emptyList())
                } else {
                    Result.failure(
                        Exception("Failed to fetch releases: ${response.code()}")
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
