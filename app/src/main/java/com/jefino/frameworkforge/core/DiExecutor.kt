package com.jefino.frameworkforge.core

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Executes DI (DynamicInstaller) job scripts with live output streaming.
 * 
 * EXECUTION RULES:
 * - ONLY run.sh should be executed - never individual feature scripts directly
 * - run.sh sources the DI environment and calls features in sequence
 * - All execution happens in isolated job directories
 */
object DiExecutor {

    private const val DI_ENVIRONMENT = "/data/local/di/environment"

    /**
     * Runs the job's run.sh script with live log streaming.
     * This is the ONLY entry point for patch execution.
     * 
     * @param jobDir The job directory containing run.sh
     * @param log Callback for each log line
     * @return Exit code of the script (0 = success)
     */
    suspend fun runJob(
        jobDir: File,
        log: (String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val runScript = File(jobDir, "run.sh")
        
        if (!runScript.exists()) {
            log("ERROR: run.sh not found in ${jobDir.absolutePath}")
            return@withContext 1
        }

        // Create callback lists for live output streaming
        val stdoutCallback = object : CallbackList<String>() {
            override fun onAddElement(e: String) {
                log(e)
            }
        }
        
        val stderrCallback = object : CallbackList<String>() {
            override fun onAddElement(e: String) {
                log("[ERR] $e")
            }
        }

        log("Executing job: ${jobDir.name}")

        // Use DI's bash binary to run the script (core requires bash syntax)
        val diBash = "/data/tmp/di/bin/bash"
        val result = Shell.cmd("su -c '$diBash ${runScript.absolutePath}'")
            .to(stdoutCallback, stderrCallback)
            .exec()

        if (result.isSuccess) {
            log("Job completed successfully")
        } else {
            log("Job failed with exit code: ${result.code}")
        }

        result.code
    }

    /**
     * Generates a run.sh script for a patching job.
     * The script sources DI environment and executes features in sequence.
     * 
     * @param jobDir Job directory where run.sh will be created
     * @param frameworkJarPath Path to framework.jar within job directory
     * @param features List of features with their runtime paths
     * @param apiLevel Device API level
     * @param deviceCodename Device codename
     * @return The generated run.sh file
     */
    fun generateRunScript(
        context: android.content.Context,
        jobDir: File,
        frameworkJarPath: String,
        features: List<PatchFeature>,
        apiLevel: Int,
        deviceCodename: String
    ): File {
        val runScript = File(jobDir, "run.sh")
        
        val scriptContent = buildString {
            // Use DI's bash binary - required for core's bash syntax
            appendLine("#!/data/tmp/di/bin/bash")
            appendLine("# FrameworkForge Patch Job")
            appendLine("# Generated: ${System.currentTimeMillis()}")
            appendLine("")
            appendLine("# Job context")
            appendLine("export FRAMEWORK_JAR=\"$frameworkJarPath\"")
            appendLine("export API_LEVEL=$apiLevel")
            appendLine("export DEVICE_CODENAME=\"$deviceCodename\"")
            appendLine("export JOB_DIR=\"${jobDir.absolutePath}\"")
            appendLine("export WORK_DIR=\"${jobDir.absolutePath}/work\"")
            appendLine("export OUTPUT_DIR=\"${jobDir.absolutePath}/output\"")
            appendLine("")
            appendLine("# Create work directories")
            appendLine("mkdir -p \"\$WORK_DIR\"")
            appendLine("mkdir -p \"\$OUTPUT_DIR\"")
            appendLine("")
            appendLine("# Source DI environment and core")
            appendLine("export DI_BIN=\"/data/tmp/di/bin\"")
            appendLine("export DI_TMP=\"/data/tmp/di\"")
            appendLine("export TMP=\"/data/tmp/di\"")  // DI core requires $TMP for temp directories
            appendLine("export TMPDIR=\"/data/tmp/di\"")
            appendLine("export PATH=\"\$DI_BIN:\$PATH\"")
            appendLine("export l=\"\$DI_BIN\"")  // DI uses $l for tool path
            appendLine("")
            appendLine("# Source DI core for functions like dynamic_apktool, smali_kit")
            appendLine("if [ -f \"\$DI_TMP/core\" ]; then")
            appendLine("    . \"\$DI_TMP/core\"")
            appendLine("    echo '[*] DynamicInstaller core loaded'")
            appendLine("else")
            appendLine("    echo '[!] WARNING: DI core not found, some features may not work'")
            appendLine("fi")
            appendLine("")
            appendLine("echo '[*] Starting FrameworkForge patch job'")
            appendLine("echo '[*] Device: $deviceCodename (API $apiLevel)'")
            appendLine("echo '[*] Features: ${features.size}'")
            appendLine("")
            
            // Execute each feature script (source instead of sh to inherit DI functions)
            features.forEachIndexed { index, feature ->
                appendLine("# Feature ${index + 1}/${features.size}: ${feature.name}")
                appendLine("echo '[*] [${index + 1}/${features.size}] Applying: ${feature.name}'")
                // Source the script (.) instead of executing with sh - this runs in same shell
                // so it has access to dynamic_apktool, smali_kit, etc. from core
                appendLine(". \"${feature.runtimePath}\" \"\$FRAMEWORK_JAR\" \"\$API_LEVEL\" \"\$DEVICE_CODENAME\"")
                appendLine("echo '[*] ${feature.name} - done'")
                appendLine("")
            }
            
            appendLine("echo '[*] All patches applied successfully'")
            appendLine("")
            appendLine("# Copy patched framework to output")
            appendLine("cp \"\$FRAMEWORK_JAR\" \"\$OUTPUT_DIR/framework_patched.jar\"")
            appendLine("echo '[*] Patched framework saved to output directory'")
            appendLine("")
            appendLine("echo '[*] Job completed'")
            appendLine("exit 0")
        }
        
        // Write to temp file first, then copy via root
        val tmpFile = File.createTempFile("run_", ".sh", context.cacheDir)
        tmpFile.writeText(scriptContent)
        
        // Copy to job dir via root and make executable
        Shell.cmd(
            "cp ${tmpFile.absolutePath} ${runScript.absolutePath}",
            "chmod 755 ${runScript.absolutePath}"
        ).exec()
        
        tmpFile.delete()
        
        return runScript
    }
}
