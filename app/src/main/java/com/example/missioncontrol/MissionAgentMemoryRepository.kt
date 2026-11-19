package com.example.missioncontrol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MissionAgentMemoryItem(
    val id: String,
    val missionId: String,
    val sourceTaskId: String,
    val sourceTaskTitle: String,
    val sourceAgentId: String,
    val createdAtMillis: Long,
    val content: String,
    val tags: List<String>
)

/**
 * Explicit, mission-scoped memory derived only from successful specialist handoffs.
 * Stored locally in ordinary SharedPreferences; it is not encrypted or synchronized.
 */
class MissionAgentMemoryRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "maya_agent_shared_memory",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun getForMission(missionId: String): List<MissionAgentMemoryItem> =
        readAll().filter { it.missionId == missionId }.sortedByDescending { it.createdAtMillis }

    @Synchronized
    fun rememberCompletedHandoff(item: MissionAgentWorkItem, handoff: String): MissionAgentMemoryItem? {
        val content = handoff.trim().take(MAX_CONTENT_CHARS)
        if (item.missionId.isBlank() || item.id.isBlank() || content.isBlank()) return null
        val all = readAll()
        all.firstOrNull { it.sourceTaskId == item.id && it.missionId == item.missionId }?.let { return it }
        val memory = MissionAgentMemoryItem(
            id = UUID.randomUUID().toString(),
            missionId = item.missionId,
            sourceTaskId = item.id,
            sourceTaskTitle = item.title.take(180),
            sourceAgentId = item.agentId,
            createdAtMillis = System.currentTimeMillis(),
            content = content,
            tags = listOf(item.agentId, "completed-handoff").distinct()
        )
        val updated = (all + memory).let { entries ->
            val keepIds = entries.filter { it.missionId == item.missionId }
                .sortedByDescending { it.createdAtMillis }
                .take(MAX_ITEMS_PER_MISSION)
                .map { it.id }.toSet()
            entries.filter { it.missionId != item.missionId || it.id in keepIds }
        }
        save(updated)
        return memory
    }

    @Synchronized
    fun contextFor(item: MissionAgentWorkItem): String {
        val relevant = getForMission(item.missionId)
            .filter { it.sourceTaskId != item.id }
            .take(MAX_ITEMS_PER_MISSION)
            .asReversed()
        return relevant.joinToString("\n\n") { memory ->
            val agent = MissionAgentRoster.agents.firstOrNull { it.id == memory.sourceAgentId }?.displayName
                ?: memory.sourceAgentId
            "Mission memory from completed task: ${memory.sourceTaskTitle}\nSource specialist: $agent\nTags: ${memory.tags.joinToString(", ")}\nMemory:\n${memory.content}"
        }.take(MAX_CONTEXT_CHARS)
    }

    @Synchronized
    fun remove(id: String, missionId: String): Boolean {
        val all = readAll()
        if (all.none { it.id == id && it.missionId == missionId }) return false
        save(all.filterNot { it.id == id && it.missionId == missionId })
        return true
    }

    private fun readAll(): List<MissionAgentMemoryItem> {
        val raw = preferences.getString(KEY_ITEMS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val missionId = json.optString("missionId")
            val taskId = json.optString("sourceTaskId")
            val agentId = json.optString("sourceAgentId")
            val content = json.optString("content").take(MAX_CONTENT_CHARS)
            if (missionId.isBlank() || taskId.isBlank() || content.isBlank() ||
                MissionAgentRoster.agents.none { it.id == agentId }) return@mapNotNull null
            val tagsJson = json.optJSONArray("tags") ?: JSONArray()
            MissionAgentMemoryItem(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                missionId = missionId,
                sourceTaskId = taskId,
                sourceTaskTitle = json.optString("sourceTaskTitle").take(180),
                sourceAgentId = agentId,
                createdAtMillis = json.optLong("createdAtMillis"),
                content = content,
                tags = (0 until tagsJson.length()).map { tagsJson.optString(it).take(48) }.take(8)
            )
        }
    }

    private fun save(items: List<MissionAgentMemoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            val tags = JSONArray()
            item.tags.forEach(tags::put)
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("missionId", item.missionId)
                    .put("sourceTaskId", item.sourceTaskId)
                    .put("sourceTaskTitle", item.sourceTaskTitle)
                    .put("sourceAgentId", item.sourceAgentId)
                    .put("createdAtMillis", item.createdAtMillis)
                    .put("content", item.content)
                    .put("tags", tags)
            )
        }
        check(preferences.edit().putString(KEY_ITEMS, array.toString()).commit()) {
            "Could not save mission memory."
        }
    }

    companion object {
        private const val KEY_ITEMS = "memory_items"
        private const val MAX_ITEMS_PER_MISSION = 12
        private const val MAX_CONTENT_CHARS = 6000
        private const val MAX_CONTEXT_CHARS = 12000
    }
}
