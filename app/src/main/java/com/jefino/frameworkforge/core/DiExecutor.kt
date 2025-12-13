package com.jefino.frameworkforge.core

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Executes DI (DynamicInstaller) job scripts with live output streaming.
 */
object DiExecutor {

    private const val DI_ENVIRONMENT = "/data/local/di/environment"

    /**
     * Runs the job's run.sh script with live log streaming.
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

        val stdoutCallback = object : CallbackList<String>() {
            override fun onAddElement(e: String) { log(e) }
        }

        val stderrCallback = object : CallbackList<String>() {
            override fun onAddElement(e: String) { log("[ERR] $e") }
        }

        log("Executing job: ${jobDir.name}")

        val diBash = "/data/tmp/di/bin/bash"
        val result = Shell.cmd("su -c '$diBash ${runScript.absolutePath}'")
            .to(stdoutCallback, stderrCallback)
            .exec()

        if (result.isSuccess) {
            log("Job script finished execution")
        } else {
            log("Job failed with exit code: ${result.code}")
        }

        result.code
    }

    /**
     * Generates a run.sh script that handles multiple JARs and verifies modification.
     */
    fun generateRunScript(
        context: android.content.Context,
        jobDir: File,
        inputFiles: Map<String, String>,
        features: List<PatchFeature>,
        apiLevel: Int,
        deviceCodename: String
    ): File {
        val runScript = File(jobDir, "run.sh")

        val scriptContent = buildString {
            appendLine("#!/data/tmp/di/bin/bash")
            appendLine("set +e")

            appendLine("# Job context")
            appendLine("export API_LEVEL=$apiLevel")
            appendLine("export DEVICE_CODENAME=\"$deviceCodename\"")
            appendLine("export JOB_DIR=\"${jobDir.absolutePath}\"")
            appendLine("export WORK_DIR=\"${jobDir.absolutePath}/work\"")
            appendLine("export OUTPUT_DIR=\"${jobDir.absolutePath}/output\"")

            // Export ALL input files so feature scripts can find them
            inputFiles.forEach { (name, path) ->
                when(name) {
                    "framework.jar" -> appendLine("export FRAMEWORK_JAR=\"$path\"")
                    "services.jar" -> appendLine("export SERVICES_JAR=\"$path\"")
                    "miui-services.jar" -> appendLine("export MIUI_SERVICES_JAR=\"$path\"")
                }
            }

            appendLine("")
            appendLine("# Create dirs")
            appendLine("mkdir -p \"\$WORK_DIR\"")
            appendLine("mkdir -p \"\$OUTPUT_DIR\"")
            appendLine("")

            // DI Environment setup
            appendLine("export DI_BIN=\"/data/tmp/di/bin\"")
            appendLine("export DI_TMP=\"/data/tmp/di\"")
            appendLine("export TMP=\"/data/tmp/di\"")
            appendLine("export TMPDIR=\"/data/tmp/di\"")
            appendLine("export PATH=\"\$DI_BIN:\$PATH\"")
            appendLine("export l=\"\$DI_BIN\"")

            appendLine("if [ -f \"\$DI_TMP/core\" ]; then . \"\$DI_TMP/core\"; else echo '[!] DI Core missing'; fi")

            // 1. Calculate initial checksums
            appendLine("echo '[*] Calculating initial checksums...'")
            inputFiles.keys.forEach { name ->
                val envVar = when(name) {
                    "framework.jar" -> "FRAMEWORK_JAR"
                    "services.jar" -> "SERVICES_JAR"
                    "miui-services.jar" -> "MIUI_SERVICES_JAR"
                    else -> ""
                }
                if (envVar.isNotEmpty()) {
                    appendLine("${envVar}_MD5_PRE=$(md5sum \"$$envVar\" | cut -d' ' -f1)")
                }
            }

            // 2. Run Features
            appendLine("")
            appendLine("echo '[*] Applying ${features.size} features...'")
            features.forEachIndexed { index, feature ->
                appendLine("echo '[*] [${index + 1}/${features.size}] Feature: ${feature.name}'")
                // Source the script. We pass FRAMEWORK_JAR as arg 1 for backward compatibility,
                // but scripts should preferably use env vars now.
                appendLine(". \"${feature.runtimePath}\"")
            }
            appendLine("")

            // 3. Verify modifications and copy to output
            appendLine("echo '[*] Verifying patches...'")
            inputFiles.keys.forEach { name ->
                val envVar = when(name) {
                    "framework.jar" -> "FRAMEWORK_JAR"
                    "services.jar" -> "SERVICES_JAR"
                    "miui-services.jar" -> "MIUI_SERVICES_JAR"
                    else -> ""
                }

                if (envVar.isNotEmpty()) {
                    appendLine("${envVar}_MD5_POST=$(md5sum \"$$envVar\" | cut -d' ' -f1)")

                    // Compare MD5
                    appendLine("if [ \"\$${envVar}_MD5_PRE\" != \"\$${envVar}_MD5_POST\" ]; then")
                    appendLine("    echo '[SUCCESS] $name was modified by patches.'")
                    appendLine("    cp \"$$envVar\" \"\$OUTPUT_DIR/$name\"")
                    appendLine("else")
                    appendLine("    echo '[WARNING] $name was NOT modified. Copying original to output.'")
                    // Still copy it so the module generator has the file, but warn the user
                    appendLine("    cp \"$$envVar\" \"\$OUTPUT_DIR/$name\"")
                    appendLine("fi")
                }
            }

            appendLine("echo '[*] Job finished'")
            appendLine("exit 0")
        }

        val tmpFile = File.createTempFile("run_", ".sh", context.cacheDir)
        tmpFile.writeText(scriptContent)

        Shell.cmd(
            "cp ${tmpFile.absolutePath} ${runScript.absolutePath}",
            "chmod 755 ${runScript.absolutePath}"
        ).exec()

        tmpFile.delete()
        return runScript
    }
}