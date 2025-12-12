package com.jefino.frameworkforge.core

import android.os.Build
import com.jefino.frameworkforge.model.DeviceInfo
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inspects device properties and available framework files
 */
object SystemInspector {

    private const val FRAMEWORK_JAR_PATH = "/system/framework/framework.jar"
    private const val SERVICES_JAR_PATH = "/system/framework/services.jar"
    private const val MIUI_SERVICES_JAR_PATH = "/system/system_ext/framework/miui-services.jar"

    // Alternative paths for MIUI services
    private val MIUI_SERVICES_ALTERNATIVE_PATHS = listOf(
        "/system/system_ext/framework/miui-services.jar",
        "/system_ext/framework/miui-services.jar",
        "/system/framework/miui-services.jar"
    )

    /**
     * Collects all device information
     */
    suspend fun getDeviceInfo(): DeviceInfo = withContext(Dispatchers.IO) {
        val apiLevel = Build.VERSION.SDK_INT
        val androidVersion = Build.VERSION.RELEASE
        val deviceCodename = getSystemProperty("ro.product.device") ?: Build.DEVICE
        val deviceName = getSystemProperty("ro.product.model") ?: Build.MODEL
        val versionName = getSystemProperty("ro.system.build.version.incremental")
            ?: getSystemProperty("ro.build.display.id")
            ?: getSystemProperty("ro.build.id")
            ?: Build.DISPLAY

        val hasFrameworkJar = checkFileExists(FRAMEWORK_JAR_PATH)
        val hasServicesJar = checkFileExists(SERVICES_JAR_PATH)
        val hasMiuiServicesJar = MIUI_SERVICES_ALTERNATIVE_PATHS.any { checkFileExists(it) }

        DeviceInfo(
            apiLevel = apiLevel,
            androidVersion = androidVersion,
            deviceCodename = deviceCodename,
            deviceName = deviceName,
            versionName = versionName,
            hasFrameworkJar = hasFrameworkJar,
            hasServicesJar = hasServicesJar,
            hasMiuiServicesJar = hasMiuiServicesJar
        )
    }

    /**
     * Gets a system property using root shell if available, otherwise uses Build class
     */
    private fun getSystemProperty(property: String): String? {
        return try {
            val result = Shell.cmd("getprop $property").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                result.out.first().takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if a file exists using root shell
     */
    private fun checkFileExists(path: String): Boolean {
        return try {
            val result = Shell.cmd("test -f \"$path\" && echo 'exists'").exec()
            result.isSuccess && result.out.any { it.contains("exists") }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the actual path of MIUI services jar if it exists
     */
    fun getMiuiServicesPath(): String? {
        return MIUI_SERVICES_ALTERNATIVE_PATHS.firstOrNull { checkFileExists(it) }
    }

    /**
     * Gets all available framework file paths
     */
    fun getAvailableFrameworkPaths(): Map<String, String> {
        val paths = mutableMapOf<String, String>()

        if (checkFileExists(FRAMEWORK_JAR_PATH)) {
            paths["framework.jar"] = FRAMEWORK_JAR_PATH
        }
        if (checkFileExists(SERVICES_JAR_PATH)) {
            paths["services.jar"] = SERVICES_JAR_PATH
        }
        getMiuiServicesPath()?.let { path ->
            paths["miui-services.jar"] = path
        }

        return paths
    }
}
