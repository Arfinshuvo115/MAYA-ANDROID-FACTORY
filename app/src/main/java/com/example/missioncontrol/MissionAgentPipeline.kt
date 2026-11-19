package com.example.missioncontrol

import android.content.Context
import com.example.data.ai.GeminiRestClient
import com.example.data.local.PreferencesRepository
import kotlinx.coroutines.CancellationException
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
        appendLine("# 16-Role Mission Report")
        appendLine()
        appendLine("Model: $modelId")
        appendLine("Completed specialist passes: ${stages.size}/${MissionAgentRoster.agents.size}")
        appendLine()
        stages.forEachIndexed { index, stage ->
            appendLine("## ${index + 1}. ${stage.agentName}")
            appendLine()
            appendLine(stage.output)
            appendLine()
        }
    }.trim()
}

/**
 * Runs sixteen specialist role passes sequentially through the configured Gemini model.
 * This is a role-orchestrated workflow, not sixteen independent AI services.
 * It creates a report only; it does not silently edit files or execute device actions.
 */
class MissionAgentPipeline(context: Context) {
    private val preferencesRepository = PreferencesRepository(context.applicationContext)
    private val geminiClient = GeminiRestClient(context.applicationContext)
    private val tavilyClient = TavilyResearchClient()

    suspend fun run(task: MissionTask): AgentPipelineResult {
        val preferences = preferencesRepository.preferences.first()
        val apiKey = preferences.geminiApiKey.trim()
        require(apiKey.isNotEmpty()) {
            "Gemini API key is not configured. Add and verify it in Settings before running agents."
        }

        val liveResearchContext = if (preferences.tavilyApiKey.isNotBlank()) {
            val researchQuery = listOf(task.title, task.details)
                .filter { it.isNotBlank() }.joinToString(" ").take(1000)
            tavilyClient.search(preferences.tavilyApiKey, researchQuery).fold(
                onSuccess = { found ->
                    if (found.isEmpty()) "LIVE WEB SEARCH: Tavily returned no usable sources."
                    else buildString {
                        appendLine("LIVE WEB SEARCH RESULTS (Tavily snippets are not independently verified):")
                        found.forEachIndexed { index, item ->
                            appendLine("${index + 1}. ${item.title}")
                            appendLine("URL: ${item.url}")
                            appendLine("Snippet: ${item.content.take(1200)}")
                        }
                    }.take(8000)
                },
                onFailure = { failure ->
                    "LIVE WEB SEARCH FAILED: ${failure.localizedMessage ?: "Unknown Tavily error"}. Do not claim browsing succeeded."
                }
            )
        } else {
            "LIVE WEB SEARCH NOT CONFIGURED: no Tavily API key is saved. Do not claim external browsing."
        }

        val results = mutableListOf<AgentStageResult>()
        var handoff = "Mission title: ${task.title}\nMission details: ${task.details.ifBlank { "No additional details supplied." }}"
        for ((index, agent) in MissionAgentRoster.agents.withIndex()) {
            val contextForAgent = buildString {
                appendLine("Mission brief:")
                appendLine(handoff.takeLast(12000))
                if (agent.id == "researcher") {
                    appendLine()
                    appendLine(liveResearchContext)
                }
                appendLine()
                appendLine("Specialist pass ${index + 1} of 16.")
            }
            val response = try {
                geminiClient.generateText(
                    apiKey = apiKey,
                    modelId = preferences.geminiModelId,
                    systemPrompt = "You are the ${agent.displayName} specialist in Maya's 16-role workflow. Responsibility: ${agent.responsibility}. ${agent.instruction} Respond in clear Bengali when the mission is written in Bengali; otherwise match the mission language. Be precise. Never fabricate completed actions, test results, external sources, or integrations.",
                    userPrompt = "$contextForAgent\nYour task: produce your specialist analysis and a concise handoff for the next role. Maximum 5,000 characters.",
                    imageUri = null
                ).getOrThrow().trim()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                throw IllegalStateException("${agent.displayName} failed at pass ${index + 1}/16: ${failure.localizedMessage ?: "AI request failed"}", failure)
            }
            require(response.isNotBlank()) { "${agent.displayName} returned an empty result at pass ${index + 1}/16." }
            val boundedOutput = response.take(6000)
            results += AgentStageResult(agent.displayName, boundedOutput)
            handoff += "\n\n--- ${agent.displayName} handoff ---\n$boundedOutput"
            handoff = handoff.takeLast(16000)
        }

        check(results.size == 16) { "The specialist workflow did not complete all 16 roles." }
        return AgentPipelineResult(
            modelId = preferences.geminiModelId,
            stages = results,
            completedAtMillis = System.currentTimeMillis()
        )
    }
}
