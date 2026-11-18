package com.example.missioncontrol.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * Explicit, user-triggered Gradle build handoff to Termux.
 * Only shared-storage SAF folders can be mapped safely to Termux filesystem paths.
 */
object TermuxBuildBridge {
    data class BuildRequest(val workingDirectory: String, val command: String)

    fun resolveSharedStorageProject(treeUri: Uri): BuildRequest? {
        if (treeUri.authority != "com.android.externalstorage.documents") return null
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        if (!documentId.startsWith("primary:")) return null
        val relative = documentId.removePrefix("primary:").trim('/')
        if (relative.isBlank()) return null
        val root = File("/storage/emulated/0", relative).canonicalFile
        val sharedRoot = File("/storage/emulated/0").canonicalFile
        if (!root.path.startsWith(sharedRoot.path + File.separator)) return null
        return BuildRequest(
            workingDirectory = root.absolutePath,
            command = "if [ ! -f ./gradlew ]; then echo 'ERROR: gradlew not found in selected project root'; exit 2; fi; bash ./gradlew assembleDebug"
        )
    }

    fun hasTermux(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage("com.termux") != null

    fun hasRunCommandPermission(context: Context): Boolean =
        context.checkSelfPermission("com.termux.permission.RUN_COMMAND") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun requestDebugBuild(context: Context, request: BuildRequest): Result<Unit> = runCatching {
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra(
                "com.termux.RUN_COMMAND_ARGUMENTS",
                arrayOf("-lc", request.command)
            )
            putExtra("com.termux.RUN_COMMAND_WORKDIR", request.workingDirectory)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        }
        context.startService(intent)
    }
}
