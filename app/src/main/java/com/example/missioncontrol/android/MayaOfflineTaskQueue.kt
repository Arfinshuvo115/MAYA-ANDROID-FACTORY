package com.example.missioncontrol.android

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Persistent queue for on-device tasks that do not require network access. */
object MayaOfflineTaskQueue {
    private const val PREFS = "maya_offline_task_queue"
    private const val KEY_TASKS = "tasks"
    private const val JOB_ID = 73142

    data class Task(val id: String, val type: String, val projectPath: String, val label: String, val status: String, val detail: String)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    @Synchronized private fun read(context: Context) =
        runCatching { JSONArray(prefs(context).getString(KEY_TASKS, "[]")) }.getOrElse { JSONArray() }
    @Synchronized private fun write(context: Context, data: JSONArray) {
        prefs(context).edit().putString(KEY_TASKS, data.toString()).apply()
    }

    @Synchronized fun list(context: Context): List<Task> {
        val data = read(context)
        return (0 until data.length()).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            Task(o.optString("id"), o.optString("type"), o.optString("projectPath"),
                o.optString("label"), o.optString("status"), o.optString("detail"))
        }.takeLast(20).reversed()
    }

    @Synchronized fun enqueueOfflineBuild(context: Context, projectPath: String): Result<String> = runCatching {
        require(projectPath.startsWith("/storage/emulated/0/")) { "Only a selected primary shared-storage project is supported." }
        val id = UUID.randomUUID().toString()
        val data = read(context)
        data.put(JSONObject().apply {
            put("id", id); put("type", "OFFLINE_GRADLE_BUILD"); put("projectPath", projectPath)
            put("label", "Offline Gradle assembleDebug"); put("status", "QUEUED")
            put("detail", "Queued locally. No network connection is required.")
        })
        write(context, data)
        schedule(context)
        id
    }

    @Synchronized fun enqueueOfflineAudit(context: Context, treeUri: String): Result<String> = runCatching {
        require(treeUri.startsWith("content://")) { "Choose a project folder using the Android folder picker first." }
        val selectedUri = android.net.Uri.parse(treeUri)
        require(context.contentResolver.persistedUriPermissions.any {
            it.uri == selectedUri && it.isReadPermission
        }) { "Android has not granted persistent folder access. Re-select the folder and allow access." }
        val id = UUID.randomUUID().toString()
        val data = read(context)
        data.put(JSONObject().apply {
            put("id", id); put("type", "OFFLINE_PROJECT_AUDIT"); put("projectPath", treeUri)
            put("label", "Offline source project audit"); put("status", "QUEUED")
            put("detail", "Queued locally. This static audit does not need internet or a cloud model.")
        })
        write(context, data)
        schedule(context)
        id
    }

    @Synchronized fun nextQueued(context: Context): Task? {
        val data = read(context)
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            if (o.optString("status") == "QUEUED" || o.optString("status") == "RUNNING") {
                o.put("status", "RUNNING")
                o.put(
                    "detail",
                    if (o.optString("type") == "OFFLINE_PROJECT_AUDIT")
                        "Running local static source analysis. No network or cloud model is used."
                    else
                        "Sent to Termux; Gradle is running with --offline. Missing cached dependencies cause a truthful failure."
                )
                write(context, data)
                return Task(o.optString("id"), o.optString("type"), o.optString("projectPath"),
                    o.optString("label"), "RUNNING", o.optString("detail"))
            }
        }
        return null
    }

    @Synchronized fun finish(context: Context, id: String, success: Boolean, detail: String) {
        val data = read(context)
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            if (o.optString("id") == id) {
                o.put("status", if (success) "SUCCEEDED" else "FAILED")
                o.put("detail", detail.take(1500))
                break
            }
        }
        write(context, data)
        schedule(context)
    }

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, MayaOfflineTaskJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .setPersisted(true)
            .setMinimumLatency(0)
            .build()
        scheduler.schedule(job)
    }

    internal fun dispatch(context: Context, task: Task): Result<Unit> = runCatching {
        val callback = Intent(context, TermuxBuildResultReceiver::class.java)
            .putExtra("maya_command_label", task.label).putExtra("maya_task_id", task.id)
        val flags = PendingIntent.FLAG_ONE_SHOT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val result = PendingIntent.getBroadcast(context, task.id.hashCode(), callback, flags)
        val command = "if [ ! -f ./gradlew ]; then echo 'ERROR: gradlew not found'; exit 2; fi; bash ./gradlew --offline assembleDebug"
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", task.projectPath)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", task.label)
            putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION", "User-approved offline MAYA build task.")
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", result)
        }
        context.startService(intent)
    }
}

/** JobScheduler persists the scheduled job across process death and device reboot. */
class MayaOfflineTaskJobService : JobService() {
    private var taskScope: CoroutineScope? = null
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        taskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        runningJob = taskScope?.launch {
            val task = MayaOfflineTaskQueue.nextQueued(this@MayaOfflineTaskJobService)
            if (task == null) {
                jobFinished(params, false)
                return@launch
            }
            if (task.type == "OFFLINE_PROJECT_AUDIT") {
                runCatching {
                    val report = MayaOfflineProjectAuditor.run(
                        this@MayaOfflineTaskJobService,
                        android.net.Uri.parse(task.projectPath)
                    )
                    MayaOfflineTaskQueue.finish(this@MayaOfflineTaskJobService, task.id, true, report.asText())
                }.onFailure {
                    MayaOfflineTaskQueue.finish(
                        this@MayaOfflineTaskJobService, task.id, false,
                        "Offline audit failed: " + (it.localizedMessage ?: "unknown error")
                    )
                }
            } else if (task.type == "OFFLINE_GRADLE_BUILD") {
                if (!TermuxBuildBridge.hasTermux(this@MayaOfflineTaskJobService) ||
                    !TermuxBuildBridge.hasRunCommandPermission(this@MayaOfflineTaskJobService)
                ) {
                    MayaOfflineTaskQueue.finish(
                        this@MayaOfflineTaskJobService, task.id, false,
                        "Termux or its Run Command permission is unavailable."
                    )
                } else {
                    MayaOfflineTaskQueue.dispatch(this@MayaOfflineTaskJobService, task).onFailure {
                        MayaOfflineTaskQueue.finish(
                            this@MayaOfflineTaskJobService, task.id, false,
                            it.localizedMessage ?: "Termux dispatch failed."
                        )
                    }
                }
            } else {
                MayaOfflineTaskQueue.finish(this@MayaOfflineTaskJobService, task.id, false, "Unsupported task type.")
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        taskScope?.cancel()
        runningJob = null
        taskScope = null
        return true
    }
}
