package com.example.missioncontrol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MissionTask(
    val id: String,
    val title: String,
    val details: String,
    val status: String,
    val createdAtMillis: Long
)

/**
 * Device-local mission inbox. This persists user-created missions only;
 * it does not claim that a remote agent has executed them.
 */
class MissionQueueRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "maya_mission_queue",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun getAll(): List<MissionTask> {
        val source = preferences.getString(KEY_TASKS, "[]") ?: "[]"
        val array = runCatching { JSONArray(source) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title").trim()
            if (title.isEmpty()) return@mapNotNull null
            MissionTask(
                id = item.optString("id"),
                title = title,
                details = item.optString("details"),
                status = item.optString("status", STATUS_QUEUED),
                createdAtMillis = item.optLong("createdAtMillis")
            )
        }.sortedByDescending { it.createdAtMillis }
    }

    @Synchronized
    fun add(title: String, details: String): MissionTask {
        val task = MissionTask(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            details = details.trim(),
            status = STATUS_QUEUED,
            createdAtMillis = System.currentTimeMillis()
        )
        require(task.title.isNotEmpty()) { "Mission title is required." }
        val updated = getAll().toMutableList().apply { add(task) }
        save(updated)
        return task
    }

    @Synchronized
    fun setStatus(id: String, status: String): Boolean {
        require(status in setOf(STATUS_QUEUED, STATUS_ACTIVE, STATUS_PAUSED, STATUS_COMPLETED))
        val tasks = getAll()
        if (tasks.none { it.id == id }) return false
        save(tasks.map { if (it.id == id) it.copy(status = status) else it })
        return true
    }

    private fun save(tasks: List<MissionTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("details", task.details)
                    .put("status", task.status)
                    .put("createdAtMillis", task.createdAtMillis)
            )
        }
        check(preferences.edit().putString(KEY_TASKS, array.toString()).commit()) {
            "Could not persist the mission queue."
        }
    }

    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_COMPLETED = "COMPLETED"
        private const val KEY_TASKS = "tasks_json"
    }
}
