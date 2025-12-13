package com.jefino.frameworkforge.core

import android.content.Context
import android.os.Build
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates Magisk/KernelSU compatible module ZIPs from patched framework JARs
 */
object ModuleGenerator {

    private const val MODULE_ID = "frameworkforge_patched"

    /**
     * Creates a Magisk module ZIP from patched JAR files
     * @param context Android context
     * @param patchedJars Map of JAR names to their patched file paths
     * @param deviceInfo Basic device info for module naming
     * @param log Callback for logging progress
     * @return Result containing the path to the generated ZIP file
     */
    suspend fun generateModule(
        context: Context,
        patchedJars: Map<String, File>,
        deviceCodename: String,
        androidVersion: String,
        log: (String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val moduleName = "FrameworkForge_${deviceCodename}_${timestamp}"
            val workDir = File("/data/local/tmp/frameworkforge/module_$timestamp")
            val outputZip = File(context.cacheDir, "$moduleName.zip")

            log("Creating module directory...")
            Shell.cmd("rm -rf ${workDir.absolutePath}").exec()
            Shell.cmd("mkdir -p ${workDir.absolutePath}").exec()

            // Create module.prop
            log("Writing module.prop...")
            val moduleProp = """
                id=$MODULE_ID
                name=FrameworkForge Patched Framework
                version=v1.0_$timestamp
                versionCode=${System.currentTimeMillis() / 1000}
                author=FrameworkForge
                description=Patched framework for $deviceCodename (Android $androidVersion)
            """.trimIndent()

            val propFile = File(context.cacheDir, "module.prop")
            propFile.writeText(moduleProp)
            Shell.cmd("cp ${propFile.absolutePath} ${workDir.absolutePath}/module.prop").exec()
            propFile.delete()

            // Create system/framework directory
            log("Setting up framework directory structure...")
            Shell.cmd("mkdir -p ${workDir.absolutePath}/system/framework").exec()

            // Copy patched JARs
            for ((name, jarFile) in patchedJars) {
                if (jarFile.exists()) {
                    log("Adding $name to module...")
                    val destPath = when {
                        name == "miui-services.jar" -> "${workDir.absolutePath}/system/system_ext/framework/$name"
                        else -> "${workDir.absolutePath}/system/framework/$name"
                    }

                    if (name == "miui-services.jar") {
                        Shell.cmd("mkdir -p ${workDir.absolutePath}/system/system_ext/framework").exec()
                    }

                    val copyResult = Shell.cmd("cp ${jarFile.absolutePath} $destPath").exec()
                    if (!copyResult.isSuccess) {
                        return@withContext Result.failure(Exception("Failed to copy $name: ${copyResult.err.joinToString()}"))
                    }
                    Shell.cmd("chmod 644 $destPath").exec()
                }
            }

            // Create customize.sh for installation
            log("Creating installation script...")
            val customizeSh = """
                #!/system/bin/sh
                # FrameworkForge Module Installer
                
                ui_print "- Installing FrameworkForge patched framework"
                ui_print "- Device: $deviceCodename"
                ui_print "- Android: $androidVersion"
                
                # Set permissions
                set_perm_recursive ${'$'}MODPATH/system 0 0 0755 0644
                
                ui_print "- Framework patched successfully!"
                ui_print "- Please reboot your device"
            """.trimIndent()

            val customizeFile = File(context.cacheDir, "customize.sh")
            customizeFile.writeText(customizeSh)
            Shell.cmd("cp ${customizeFile.absolutePath} ${workDir.absolutePath}/customize.sh").exec()
            Shell.cmd("chmod 755 ${workDir.absolutePath}/customize.sh").exec()
            customizeFile.delete()

            // Create the module ZIP
            log("Creating module ZIP...")
            
            // First try shell zip with root (user confirmed zip is available with root)
            val tempZipPath = "/data/local/tmp/frameworkforge/${outputZip.name}"
            Shell.cmd("mkdir -p /data/local/tmp/frameworkforge").exec()
            Shell.cmd("rm -f $tempZipPath").exec()
            
            // Find zip location with root
            val whichResult = Shell.cmd("su -c 'which zip'").exec()
            val zipPath = whichResult.out.firstOrNull()?.trim()
            
            var zipSuccess = false
            if (!zipPath.isNullOrBlank()) {
                log("Found zip at: $zipPath")
                val zipResult = Shell.cmd(
                    "su -c 'cd ${workDir.absolutePath} && $zipPath -r $tempZipPath .'"
                ).exec()
                
                if (zipResult.isSuccess) {
                    // Copy to app cache
                    Shell.cmd(
                        "cp $tempZipPath ${outputZip.absolutePath}",
                        "chmod 644 ${outputZip.absolutePath}"
                    ).exec()
                    zipSuccess = outputZip.exists() && outputZip.length() > 0
                    if (zipSuccess) {
                        log("ZIP created with shell: ${outputZip.length() / 1024} KB")
                    }
                }
            }
            
            // Fallback to Java ZIP
            if (!zipSuccess) {
                log("Shell zip failed, using Java ZIP...")
                val localWorkDir = File(context.cacheDir, "module_work")
                localWorkDir.deleteRecursively()
                localWorkDir.mkdirs()
                
                // Copy files via root shell to app-accessible location
                Shell.cmd(
                    "cp -r ${workDir.absolutePath}/* ${localWorkDir.absolutePath}/",
                    "chmod -R 644 ${localWorkDir.absolutePath}",
                    "chmod -R +X ${localWorkDir.absolutePath}"
                ).exec()
                
                try {
                    java.util.zip.ZipOutputStream(java.io.FileOutputStream(outputZip)).use { zos ->
                        localWorkDir.walkTopDown().forEach { file ->
                            if (file.isFile) {
                                val entryName = file.relativeTo(localWorkDir).path
                                zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                                file.inputStream().use { input ->
                                    input.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                    log("ZIP created with Java: ${outputZip.length() / 1024} KB")
                    zipSuccess = true
                } catch (e: Exception) {
                    log("Java ZIP failed: ${e.message}")
                }
                localWorkDir.deleteRecursively()
            }

            // Cleanup
            Shell.cmd("rm -rf ${workDir.absolutePath}").exec()

            // Verify ZIP was created
            if (!outputZip.exists() || outputZip.length() == 0L) {
                return@withContext Result.failure(Exception("ZIP file was not created properly"))
            }

            log("Module created: ${outputZip.name} (${outputZip.length() / 1024} KB)")
            Result.success(outputZip)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Moves the generated module to the public Downloads folder
     */
    suspend fun moveToDownloads(moduleFile: File): Result<File> {
        return RootManager.moveToDownloads(moduleFile, moduleFile.name)
    }
}
