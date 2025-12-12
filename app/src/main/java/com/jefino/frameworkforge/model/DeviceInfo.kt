package com.jefino.frameworkforge.model

import kotlinx.serialization.Serializable

/**
 * Represents device information extracted from build.prop
 */
@Serializable
data class DeviceInfo(
    val apiLevel: Int,
    val androidVersion: String,
    val deviceCodename: String,
    val deviceName: String,
    val versionName: String,
    val hasFrameworkJar: Boolean = false,
    val hasServicesJar: Boolean = false,
    val hasMiuiServicesJar: Boolean = false
) {
    /**
     * Gets the workflow version string based on API level
     */
    val workflowVersion: String
        get() = when (apiLevel) {
            33 -> "android13"
            34 -> "android14"
            35 -> "android15"
            36 -> "android16"
            else -> "android${apiLevel - 21}" // Fallback
        }

    /**
     * Gets a safe version string for release tag matching
     */
    val safeVersionName: String
        get() = versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    companion object {
        val Empty = DeviceInfo(
            apiLevel = 0,
            androidVersion = "Unknown",
            deviceCodename = "unknown",
            deviceName = "Unknown Device",
            versionName = "unknown"
        )
    }
}
