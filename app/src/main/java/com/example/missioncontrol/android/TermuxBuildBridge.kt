package com.example.missioncontrol.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Explicit, user-triggered Gradle and reviewed-patch handoff to Termux. */
object TermuxBuildBridge {
    data class BuildRequest(val workingDirectory: String, val command: String)

    private val nextRequestCode = AtomicInteger(4100)

    fun resolveSharedStorageProject(treeUri: Uri): BuildRequest? {
        if (treeUri.authority != "com.android.externalstorage.documents") return null
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        if (!documentId.startsWith("primary:")) return null
        val relative = documentId.removePrefix("primary:").trim('/')
        if (relative.isBlank()) return null
        val sharedRoot = File("/storage/emulated/0").canonicalFile
        val root = File(sharedRoot, relative).canonicalFile
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

    fun requestDebugBuild(context: Context, request: BuildRequest): Result<Unit> {
        TermuxBuildState.clear()
        return sendCommand(context, request.workingDirectory, request.command, "Maya Android debug build")
    }

    fun requestLogcatSnapshot(context: Context): Result<Unit> {
        TermuxBuildState.clear()
        return sendCommand(
            context,
            "/data/data/com.termux/files/home",
            "logcat -d -t 800",
            "Maya Android Logcat snapshot"
        )
    }

    /** Applies only the named patch file after the user explicitly approves this action. */
    fun requestApplyReviewedPatch(context: Context, request: BuildRequest, patchFileName: String): Result<Unit> {
        if (!patchFileName.matches(Regex("[A-Za-z0-9_.-]{1,80}"))) {
            return Result.failure(IllegalArgumentException("Invalid patch file name."))
        }
        val command = "git apply --check \"$patchFileName\" && git apply \"$patchFileName\""
        TermuxBuildState.clear()
        return sendCommand(context, request.workingDirectory, command, "Maya reviewed code patch")
    }

    private fun sendCommand(context: Context, workdir: String, command: String, label: String): Result<Unit> = runCatching {
        val callback = Intent(context, TermuxBuildResultReceiver::class.java).putExtra("maya_command_label", label)
        val flags = PendingIntent.FLAG_ONE_SHOT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            nextRequestCode.incrementAndGet(),
            callback,
            flags
        )
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", workdir)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", label)
            putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION", "User-approved command requested by MAYA.")
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent)
        }
        context.startService(intent)
    }
}
