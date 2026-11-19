package com.example.missioncontrol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MissionAgentWorkItem(
    val id: String,
    val missionId: String,
    val title: String,
    val instructions: String,
    val agentId: String,
    val status: String,
    val createdAtMillis: Long,
    val handoff: String = "",
    val lastError: String = ""
)

/**
 * Persistent device-local assignment board. It records explicit assignments and
 * handoffs; it is not a remote worker service or a multi-device synchronization layer.
 */
class MissionAgentTaskRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "maya_agent_assignments",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun getForMission(missionId: String): List<MissionAgentWorkItem> =
        readAll().filter { it.missionId == missionId }.sortedBy { it.createdAtMillis }

    @Synchronized
    fun create(
        missionId: String,
        title: String,
        instructions: String,
        agentId: String
    ): MissionAgentWorkItem {
        require(missionId.isNotBlank()) { "Select a mission before assigning work." }
        require(title.isNotBlank()) { "Task title is required." }
        require(MissionAgentRoster.agents.any { it.id == agentId }) { "Choose a valid specialist agent." }
        val item = MissionAgentWorkItem(
            id = UUID.randomUUID().toString(),
            missionId = missionId,
            title = title.trim().take(180),
            instructions = instructions.trim().take(5000),
            agentId = agentId,
            status = STATUS_QUEUED,
            createdAtMillis = System.currentTimeMillis()
        )
        save(readAll() + item)
        return item
    }

    @Synchronized
    fun setStatus(id: String, status: String): Boolean {
        require(status in setOf(STATUS_QUEUED, STATUS_RUNNING, STATUS_COMPLETED, STATUS_FAILED))
        val items = readAll()
        if (items.none { it.id == id }) return false
        save(items.map { if (it.id == id) it.copy(status = status, lastError = if (status == STATUS_QUEUED) "" else it.lastError) else it })
        return true
    }

    @Synchronized
    fun complete(id: String, handoff: String): Boolean {
        val items = readAll()
        if (items.none { it.id == id }) return false
        save(items.map {
            if (it.id == id) it.copy(status = STATUS_COMPLETED, handoff = handoff.take(MAX_HANDOFF_CHARS), lastError = "")
            else it
        })
        return true
    }

    @Synchronized
    fun fail(id: String, error: String): Boolean {
        val items = readAll()
        if (items.none { it.id == id }) return false
        save(items.map {
            if (it.id == id) it.copy(status = STATUS_FAILED, lastError = error.take(1200))
            else it
        })
        return true
    }

    @Synchronized
    fun previousHandoffs(item: MissionAgentWorkItem): String {
        val ordered = getForMission(item.missionId)
        val currentIndex = ordered.indexOfFirst { it.id == item.id }
        if (currentIndex <= 0) return ""
        return ordered.take(currentIndex)
            .filter { it.status == STATUS_COMPLETED && it.handoff.isNotBlank() }
            .takeLast(MAX_HANDOFF_ITEMS)
            .joinToString("\n\n") { previous ->
                val agent = MissionAgentRoster.agents.firstOrNull { it.id == previous.agentId }?.displayName ?: previous.agentId
                "Completed task: ${previous.title}\nAssigned specialist: $agent\nHandoff:\n${previous.handoff}"
            }
            .take(MAX_CONTEXT_CHARS)
    }

    private fun readAll(): List<MissionAgentWorkItem> {
        val raw = preferences.getString(KEY_ITEMS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val missionId = json.optString("missionId")
            val title = json.optString("title").trim()
            val agentId = json.optString("agentId")
            if (missionId.isBlank() || title.isBlank() || MissionAgentRoster.agents.none { it.id == agentId }) {
                return@mapNotNull null
            }
            MissionAgentWorkItem(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                missionId = missionId,
                title = title,
                instructions = json.optString("instructions"),
                agentId = agentId,
                status = json.optString("status", STATUS_QUEUED).takeIf {
                    it in setOf(STATUS_QUEUED, STATUS_RUNNING, STATUS_COMPLETED, STATUS_FAILED)
                } ?: STATUS_QUEUED,
                createdAtMillis = json.optLong("createdAtMillis"),
                handoff = json.optString("handoff"),
                lastError = json.optString("lastError")
            )
        }
    }

    private fun save(items: List<MissionAgentWorkItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("missionId", item.missionId)
                    .put("title", item.title)
                    .put("instructions", item.instructions)
                    .put("agentId", item.agentId)
                    .put("status", item.status)
                    .put("createdAtMillis", item.createdAtMillis)
                    .put("handoff", item.handoff)
                    .put("lastError", item.lastError)
            )
        }
        check(preferences.edit().putString(KEY_ITEMS, array.toString()).commit()) {
            "Could not save the agent assignment."
        }
    }

    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        private const val KEY_ITEMS = "assignment_items"
        private const val MAX_HANDOFF_CHARS = 8000
        private const val MAX_HANDOFF_ITEMS = 8
        private const val MAX_CONTEXT_CHARS = 16000
    }
}
