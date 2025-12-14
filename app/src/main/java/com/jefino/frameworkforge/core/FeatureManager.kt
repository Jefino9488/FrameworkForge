package com.jefino.frameworkforge.core

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Represents a patch feature script with metadata
 */
data class PatchFeature(
    val id: String,
    val name: String,
    val description: String,
    val runtimePath: String, // Path in the safe runtime directory
    val isUserFeature: Boolean
)

/**
 * Represents a local patch feature for UI display (toggleable)
 */
data class LocalPatchFeature(
    val id: String,
    val name: String,
    val description: String,
    val requiredJars: List<String> = listOf("framework.jar"),
    val isEnabled: Boolean = true,
    val isUserFeature: Boolean = false
)

/**
 * Manages feature discovery and deployment to safe runtime directories
 * 
 * All feature scripts are copied to /data/local/tmp/frameworkforge/features/
 * with proper chmod 755 to ensure they can be executed regardless of SELinux context.
 */
object FeatureManager {

    private const val BUILTIN_ASSETS_PATH = "features"
    private const val USER_STORAGE_PATH = "features_user"
    
    // Safe runtime directories (noexec-safe)
    private const val FEATURES_RUNTIME_DIR = "/data/local/tmp/frameworkforge/features"
    private const val BUILTIN_RUNTIME_DIR = "$FEATURES_RUNTIME_DIR/builtin"
    private const val USER_RUNTIME_DIR = "$FEATURES_RUNTIME_DIR/user"

    /**
     * Gets available patch features from assets without deploying them
     * Used for UI display in local patching mode
     */
    fun getLocalPatchFeatures(context: Context): List<LocalPatchFeature> {
        val result = mutableListOf<LocalPatchFeature>()
        
        // Get built-in features from assets
        val files = context.assets.list(BUILTIN_ASSETS_PATH) ?: emptyArray()
        files.filter { it.endsWith(".sh") }.forEach { filename ->
            try {
                val cacheFile = File(context.cacheDir, filename)
                context.assets.open("$BUILTIN_ASSETS_PATH/$filename").use { input ->
                    cacheFile.outputStream().use { os -> input.copyTo(os) }
                }
                val metadata = parseFeatureMetadata(cacheFile)
                cacheFile.delete()
                
                result.add(LocalPatchFeature(
                    id = filename.removeSuffix(".sh"),
                    name = metadata.name,
                    description = metadata.description,
                    requiredJars = metadata.requiredJars,
                    isEnabled = true,
                    isUserFeature = false
                ))
            } catch (_: Exception) { }
        }
        
        // Get user features
        val userDir = File(context.filesDir, USER_STORAGE_PATH)
        userDir.listFiles()?.filter { it.name.endsWith(".sh") }?.forEach { file ->
            try {
                val metadata = parseFeatureMetadata(file)
                result.add(LocalPatchFeature(
                    id = file.name.removeSuffix(".sh"),
                    name = metadata.name,
                    description = metadata.description,
                    requiredJars = metadata.requiredJars,
                    isEnabled = true,
                    isUserFeature = true
                ))
            } catch (_: Exception) { }
        }
        
        return result
    }

    /**
     * Deploys all features to the safe runtime directory and returns their references
     * This must be called BEFORE executing any patches
     */
    fun deployFeatures(context: Context): List<PatchFeature> {
        // Create runtime directories with proper permissions
        Shell.cmd(
            "mkdir -p $BUILTIN_RUNTIME_DIR",
            "mkdir -p $USER_RUNTIME_DIR",
            "chmod -R 755 $FEATURES_RUNTIME_DIR"
        ).exec()
        
        val result = mutableListOf<PatchFeature>()
        result += deployBuiltinFeatures(context)
        result += deployUserFeatures(context)
        return result
    }

    /**
     * Deploys built-in features from assets to runtime directory
     */
    private fun deployBuiltinFeatures(context: Context): List<PatchFeature> {
        val files = context.assets.list(BUILTIN_ASSETS_PATH) ?: return emptyList()
        
        return files.filter { it.endsWith(".sh") }.mapNotNull { filename ->
            try {
                // First copy to cache (accessible from app context)
                val cacheFile = File(context.cacheDir, filename)
                context.assets.open("$BUILTIN_ASSETS_PATH/$filename").use { input ->
                    cacheFile.outputStream().use { os -> input.copyTo(os) }
                }
                
                // Parse metadata from cache file
                val metadata = parseFeatureMetadata(cacheFile)
                
                // Copy to safe runtime directory via root
                val runtimePath = "$BUILTIN_RUNTIME_DIR/$filename"
                val copyResult = Shell.cmd(
                    "cp ${cacheFile.absolutePath} $runtimePath",
                    "chmod 755 $runtimePath"
                ).exec()
                
                // Clean up cache file
                cacheFile.delete()
                
                if (copyResult.isSuccess) {
                    PatchFeature(
                        id = filename.removeSuffix(".sh"),
                        name = metadata.name,
                        description = metadata.description,
                        runtimePath = runtimePath,
                        isUserFeature = false
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Deploys user-imported features to runtime directory
     */
    private fun deployUserFeatures(context: Context): List<PatchFeature> {
        val userDir = File(context.filesDir, USER_STORAGE_PATH)
        if (!userDir.exists()) userDir.mkdirs()

        return userDir.listFiles()?.filter { it.name.endsWith(".sh") }?.mapNotNull { file ->
            try {
                val metadata = parseFeatureMetadata(file)
                
                // Copy to safe runtime directory
                val runtimePath = "$USER_RUNTIME_DIR/${file.name}"
                val copyResult = Shell.cmd(
                    "cp ${file.absolutePath} $runtimePath",
                    "chmod 755 $runtimePath"
                ).exec()
                
                if (copyResult.isSuccess) {
                    PatchFeature(
                        id = file.name.removeSuffix(".sh"),
                        name = metadata.name,
                        description = metadata.description,
                        runtimePath = runtimePath,
                        isUserFeature = true
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }

    /**
     * Feature metadata holder
     */
    data class FeatureMetadata(
        val name: String,
        val description: String,
        val requiredJars: List<String>
    )

    /**
     * Parses feature metadata from script header comments
     * Supports comma-separated requires (e.g., #@requires framework.jar,services.jar)
     */
    private fun parseFeatureMetadata(file: File): FeatureMetadata {
        var name = file.nameWithoutExtension.replace("_", " ")
        var desc = "No description"
        val requires = mutableListOf<String>()

        file.forEachLine { line ->
            when {
                line.startsWith("#@name") -> name = line.removePrefix("#@name").trim()
                line.startsWith("#@description") -> desc = line.removePrefix("#@description").trim()
                line.startsWith("#@requires") -> {
                    // Support both single and comma-separated requires
                    val reqValue = line.removePrefix("#@requires").trim()
                    requires.addAll(reqValue.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                }
            }
        }

        // Default to framework.jar if no requires specified
        if (requires.isEmpty()) requires.add("framework.jar")

        return FeatureMetadata(name, desc, requires)
    }

    /**
     * Gets enabled feature scripts matching the selected UI feature IDs
     */
    fun getEnabledScripts(context: Context, enabledFeatureIds: List<String>): List<PatchFeature> {
        val allFeatures = deployFeatures(context)
        return allFeatures.filter { feature ->
            enabledFeatureIds.any { id ->
                feature.id.contains(id, ignoreCase = true) ||
                feature.name.replace(" ", "_").lowercase().contains(id.lowercase())
            }
        }
    }

    /**
     * Deletes a user-imported feature script
     */
    fun deleteUserFeature(context: Context, featureId: String): Boolean {
        val userDir = File(context.filesDir, USER_STORAGE_PATH)
        val file = File(userDir, "$featureId.sh")
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Cleans up runtime feature directory
     */
    fun cleanup() {
        Shell.cmd("rm -rf $FEATURES_RUNTIME_DIR").exec()
    }

    /**
     * Builds a comma-separated string of feature names for logging
     */
    fun buildFeatureString(features: List<PatchFeature>): String {
        return features.joinToString(", ") { it.name }
    }

    /**
     * Builds a comma-separated string of feature IDs for workflow (for UI Feature model)
     */
    @JvmName("buildFeatureIdString")
    fun buildFeatureString(features: List<com.jefino.frameworkforge.model.Feature>): String {
        return features.filter { it.isEnabled }.joinToString(",") { it.id }
    }

    /**
     * Gets available UI features based on device capabilities
     */
    fun getAvailableFeatures(hasMiuiServices: Boolean): List<com.jefino.frameworkforge.model.Feature> {
        return com.jefino.frameworkforge.model.Feature.getAllFeatures().map { feature ->
            if (feature.requiresMiui && !hasMiuiServices) {
                feature.copy(isEnabled = false)
            } else {
                feature
            }
        }
    }

    /**
     * Updates a feature's enabled state
     */
    fun updateFeature(
        features: List<com.jefino.frameworkforge.model.Feature>,
        featureId: String,
        enabled: Boolean
    ): List<com.jefino.frameworkforge.model.Feature> {
        return features.map { feature ->
            if (feature.id == featureId) {
                feature.copy(isEnabled = enabled)
            } else {
                feature
            }
        }
    }

    /**
     * Gets a summary string of enabled features for display
     */
    fun getEnabledFeaturesSummary(features: List<com.jefino.frameworkforge.model.Feature>): String {
        val enabled = features.filter { it.isEnabled }
        return when {
            enabled.isEmpty() -> "None selected"
            enabled.size == 1 -> enabled.first().displayName
            else -> "${enabled.size} features selected"
        }
    }
}
