package com.example.missioncontrol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MissionWorkflowStep(val id: String, val type: String, val title: String, val instruction: String, val position: Int)
data class MissionWorkflowDefinition(val id: String, val name: String, val createdAtMillis: Long, val updatedAtMillis: Long, val steps: List<MissionWorkflowStep>)

/** Local-first workflow drafts. Saving a definition never executes external actions. */
class MissionWorkflowDefinitionRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("maya_workflow_definitions", Context.MODE_PRIVATE)

    @Synchronized fun getAll(): List<MissionWorkflowDefinition> = readAll().sortedByDescending { it.updatedAtMillis }

    @Synchronized fun create(name: String): MissionWorkflowDefinition {
        val safeName = name.trim().take(MAX_NAME_CHARS)
        require(safeName.isNotBlank()) { "Workflow name is required." }
        val all = readAll()
        require(all.size < MAX_WORKFLOWS) { "Workflow limit reached." }
        require(all.none { it.name.equals(safeName, ignoreCase = true) }) { "A workflow with this name already exists." }
        val now = System.currentTimeMillis()
        val item = MissionWorkflowDefinition(UUID.randomUUID().toString(), safeName, now, now, emptyList())
        save(all + item)
        return item
    }

    @Synchronized fun addStep(workflowId: String, type: String, title: String, instruction: String): MissionWorkflowDefinition {
        require(type in ALLOWED_STEP_TYPES) { "Unsupported workflow step type." }
        val safeTitle = title.trim().take(MAX_STEP_TITLE_CHARS)
        require(safeTitle.isNotBlank()) { "Step title is required." }
        val all = readAll()
        val current = all.firstOrNull { it.id == workflowId } ?: throw IllegalArgumentException("Workflow was not found.")
        require(current.steps.size < MAX_STEPS) { "Workflow step limit reached." }
        val updated = current.copy(
            updatedAtMillis = System.currentTimeMillis(),
            steps = current.steps + MissionWorkflowStep(UUID.randomUUID().toString(), type, safeTitle, instruction.trim().take(MAX_INSTRUCTION_CHARS), current.steps.size)
        )
        save(all.map { if (it.id == workflowId) updated else it })
        return updated
    }

    @Synchronized fun removeStep(workflowId: String, stepId: String): MissionWorkflowDefinition? {
        val all = readAll()
        val current = all.firstOrNull { it.id == workflowId } ?: return null
        if (current.steps.none { it.id == stepId }) return current
        val remaining = current.steps.filterNot { it.id == stepId }.mapIndexed { index, step -> step.copy(position = index) }
        val updated = current.copy(updatedAtMillis = System.currentTimeMillis(), steps = remaining)
        save(all.map { if (it.id == workflowId) updated else it })
        return updated
    }

    private fun readAll(): List<MissionWorkflowDefinition> {
        val array = runCatching { JSONArray(preferences.getString(KEY_WORKFLOWS, "[]") ?: "[]") }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val id = json.optString("id")
            val name = json.optString("name").trim()
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            val stepArray = json.optJSONArray("steps") ?: JSONArray()
            val steps = (0 until stepArray.length()).mapNotNull { stepIndex ->
                val step = stepArray.optJSONObject(stepIndex) ?: return@mapNotNull null
                val stepId = step.optString("id")
                val type = step.optString("type")
                val title = step.optString("title").trim()
                if (stepId.isBlank() || type !in ALLOWED_STEP_TYPES || title.isBlank()) return@mapNotNull null
                MissionWorkflowStep(stepId, type, title.take(MAX_STEP_TITLE_CHARS), step.optString("instruction").take(MAX_INSTRUCTION_CHARS), stepIndex)
            }.take(MAX_STEPS)
            MissionWorkflowDefinition(id, name.take(MAX_NAME_CHARS), json.optLong("createdAtMillis"), json.optLong("updatedAtMillis"), steps)
        }.take(MAX_WORKFLOWS)
    }

    private fun save(workflows: List<MissionWorkflowDefinition>) {
        val array = JSONArray()
        workflows.take(MAX_WORKFLOWS).forEach { workflow ->
            val steps = JSONArray()
            workflow.steps.take(MAX_STEPS).forEach { step ->
                steps.put(JSONObject().put("id", step.id).put("type", step.type).put("title", step.title).put("instruction", step.instruction))
            }
            array.put(JSONObject().put("id", workflow.id).put("name", workflow.name).put("createdAtMillis", workflow.createdAtMillis).put("updatedAtMillis", workflow.updatedAtMillis).put("steps", steps))
        }
        check(preferences.edit().putString(KEY_WORKFLOWS, array.toString()).commit()) { "Could not save workflow drafts." }
    }

    companion object {
        const val TYPE_TRIGGER = "TRIGGER"
        const val TYPE_AI_TASK = "AI_TASK"
        const val TYPE_CONDITION = "CONDITION"
        const val TYPE_APPROVAL = "APPROVAL"
        const val TYPE_NOTIFICATION = "NOTIFICATION"
        val ALLOWED_STEP_TYPES = setOf(TYPE_TRIGGER, TYPE_AI_TASK, TYPE_CONDITION, TYPE_APPROVAL, TYPE_NOTIFICATION)
        private const val KEY_WORKFLOWS = "workflow_definitions"
        private const val MAX_WORKFLOWS = 30
        private const val MAX_STEPS = 40
        private const val MAX_NAME_CHARS = 80
        private const val MAX_STEP_TITLE_CHARS = 120
        private const val MAX_INSTRUCTION_CHARS = 2000
    }
}
