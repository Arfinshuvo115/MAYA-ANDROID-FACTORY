package com.example.missioncontrol

import android.content.Context
import com.example.data.ai.GeminiRestClient
import com.example.data.local.PreferencesRepository
import kotlinx.coroutines.flow.first

data class AgentStageResult(
    val agentName: String,
    val output: String
)

data class AgentPipelineResult(
    val modelId: String,
    val stages: List<AgentStageResult>,
    val completedAtMillis: Long
) {
    fun toMarkdown(): String = buildString {
        appendLine("# Four-Agent Mission Report")
        appendLine()
        appendLine("Model: $modelId")
        appendLine()
        stages.forEach { stage ->
            appendLine("## ${stage.agentName}")
            appendLine()
            appendLine(stage.output)
            appendLine()
        }
    }.trim()
}

/**
 * Runs an explicit, sequential four-agent analysis using the configured Gemini API.
 * It produces a reviewed plan/report; it does not silently edit files or execute device actions.
 */
class MissionAgentPipeline(context: Context) {
    private val preferencesRepository = PreferencesRepository(context.applicationContext)
    private val geminiClient = GeminiRestClient(context.applicationContext)

    suspend fun run(task: MissionTask): AgentPipelineResult {
        val preferences = preferencesRepository.preferences.first()
        val apiKey = preferences.geminiApiKey.trim()
        require(apiKey.isNotEmpty()) {
            "Gemini API key is not configured. Add and verify it in Settings before running agents."
        }

        val stages = listOf(
            Stage(
                name = "Planner Agent",
                instruction = "You are the Planner Agent. Break the mission into a clear objective, scope, assumptions, dependencies, risks, and ordered steps. Do not claim work has been performed."
            ),
            Stage(
                name = "Researcher Agent",
                instruction = "You are the Researcher Agent. Analyze the mission and the Planner output. Separate known facts from assumptions, identify information that requires external verification, and give evidence requirements. Do not invent sources or claim live browsing."
            ),
            Stage(
                name = "Developer Agent",
                instruction = "You are the Developer Agent. Turn the plan and research into a concrete implementation or execution proposal. If software is involved, name likely files, interfaces, acceptance criteria, and safe incremental changes. Do not claim to have edited files or run commands."
            ),
            Stage(
                name = "Reviewer Agent",
                instruction = "You are the independent Reviewer Agent. Critically review all previous outputs for missing steps, unsupported claims, security risks, and test gaps. Return a corrected final action plan and clearly state what still requires human approval or real execution."
            )
        )

        val results = mutableListOf<AgentStageResult>()
        var previousContext = "Mission title: ${task.title}\nMission details: ${task.details.ifBlank { "No additional details supplied." }}"
        stages.forEach { stage ->
            val response = geminiClient.generateText(
                apiKey = apiKey,
                modelId = preferences.geminiModelId,
                systemPrompt = stage.instruction + " Respond in clear Bengali when the mission is written in Bengali; otherwise match the mission language. Be precise and do not fabricate completed actions.",
                userPrompt = "Mission brief and prior agent handoff:\n\n$previousContext\n\nYour role: ${stage.name}. Produce your stage output and handoff.",
                imageUri = null
            ).getOrThrow().trim()
            require(response.isNotBlank()) { "${stage.name} returned an empty result." }
            results += AgentStageResult(stage.name, response)
            previousContext += "\n\n--- ${stage.name} output ---\n$response"
        }

        return AgentPipelineResult(
            modelId = preferences.geminiModelId,
            stages = results,
            completedAtMillis = System.currentTimeMillis()
        )
    }

    private data class Stage(val name: String, val instruction: String)
}
