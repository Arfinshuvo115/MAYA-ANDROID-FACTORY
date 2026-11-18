package com.example.data.ai

import com.example.data.local.PreferencesRepository
import com.example.domain.model.AiWorkflowTool
import com.example.domain.model.AppLanguage
import com.example.domain.model.BanglaAccent
import com.example.domain.model.BanglaPhrasePreset
import com.example.domain.model.BanglaVoiceSynthesisResult
import com.example.domain.model.ChatMessage
import com.example.domain.model.GeminiVoicePreset
import com.example.domain.model.MayaPersona
import com.example.domain.model.QuickStarter
import com.example.domain.model.ResponseLength
import com.example.domain.model.StructuredSection
import com.example.domain.model.StudioResult
import com.example.domain.model.VoiceTone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Routes AI requests to the user's configured Gemini API key when present in Settings,
 * and falls back cleanly to LocalMayaAiRepository when no API key is configured.
 */
class HybridMayaAiRepository(
    private val preferencesRepository: PreferencesRepository,
    private val localRepository: LocalMayaAiRepository,
    private val geminiRestClient: GeminiRestClient
) : AiRepository {

    override fun getQuickStarters(): List<QuickStarter> = localRepository.getQuickStarters()

    override fun getBanglaVoicePresets(): List<BanglaPhrasePreset> = localRepository.getBanglaVoicePresets()

    override fun streamChatReply(
        prompt: String,
        persona: MayaPersona,
        history: List<ChatMessage>,
        imageUri: String?,
        responseLength: ResponseLength,
        language: AppLanguage
    ): Flow<String> = flow {
        val prefs = preferencesRepository.preferences.first()
        val apiKey = prefs.geminiApiKey.trim()
        if (apiKey.isNotEmpty()) {
            emitAll(
                geminiRestClient.streamChatReply(
                    apiKey = apiKey,
                    modelId = prefs.geminiModelId,
                    prompt = prompt,
                    persona = persona,
                    history = history,
                    imageUri = imageUri,
                    responseLength = responseLength,
                    language = language
                )
            )
        } else {
            emitAll(
                localRepository.streamChatReply(
                    prompt = prompt,
                    persona = persona,
                    history = history,
                    imageUri = imageUri,
                    responseLength = responseLength,
                    language = language
                )
            )
        }
    }

    override suspend fun runWorkflowTool(
        tool: AiWorkflowTool,
        prompt: String,
        imageUri: String?,
        responseLength: ResponseLength
    ): StudioResult {
        val prefs = preferencesRepository.preferences.first()
        val apiKey = prefs.geminiApiKey.trim()
        if (apiKey.isEmpty()) {
            return localRepository.runWorkflowTool(
                tool = tool,
                prompt = prompt,
                imageUri = imageUri,
                responseLength = responseLength
            )
        }

        val cleanPrompt = prompt.trim().ifBlank { tool.samplePrompt }
        val depthNote = when (responseLength) {
            ResponseLength.CONCISE -> "Keep each section concise (2-3 lines)."
            ResponseLength.BALANCED -> "Keep each section balanced and actionable."
            ResponseLength.DETAILED -> "Provide thorough, detailed guidance."
        }
        val systemPrompt = """
            You are Maya Studio executing the "${tool.title}" workflow (${tool.subtitle}).
            Format the output using clear markdown level-3 headings (### Section Title) with concise content under each heading.
            $depthNote
        """.trimIndent()

        val rawMarkdown = geminiRestClient.generateText(
            apiKey = apiKey,
            modelId = prefs.geminiModelId,
            systemPrompt = systemPrompt,
            userPrompt = cleanPrompt,
            imageUri = imageUri
        ).getOrThrow()

        val sections = parseMarkdownSections(rawMarkdown, tool.title)
        return StudioResult(
            id = UUID.randomUUID().toString(),
            tool = tool,
            inputPrompt = cleanPrompt,
            imageUri = imageUri,
            outputTitle = "${tool.title} · ${prefs.geminiModelId}",
            sections = sections,
            rawMarkdown = rawMarkdown,
            timestampMillis = System.currentTimeMillis()
        )
    }


    override suspend fun synthesizeGeminiSpeech(
        text: String,
        voice: GeminiVoicePreset,
        tone: VoiceTone,
        accent: BanglaAccent
    ): Result<ByteArray> {
        val prefs = preferencesRepository.preferences.first()
        if (prefs.geminiApiKey.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API key is not configured; using device TTS fallback."))
        }
        val style = when (tone) {
            VoiceTone.WARM -> "Warm, affectionate, natural young adult female Bengali voice; friendly, cute, emotionally expressive, conversational and clear."
            VoiceTone.CHEERFUL -> "Bright, cheerful, playful and cute female Bengali voice, smiling delivery, lively but natural."
            VoiceTone.CALM -> "Soft, soothing, caring female Bengali voice, gentle pace and reassuring warmth."
            VoiceTone.INFORMATIVE -> "Clear, confident female Bengali voice, natural conversational rhythm and precise pronunciation."
        }
        return geminiRestClient.synthesizeSpeech(
            apiKey = prefs.geminiApiKey,
            text = text,
            voiceName = voice.voiceName,
            style = style,
            languageCode = accent.bcp47
        )
    }

    override suspend fun synthesizeBanglaVoice(
        text: String,
        voice: GeminiVoicePreset,
        tone: VoiceTone,
        accent: BanglaAccent
    ): BanglaVoiceSynthesisResult = localRepository.synthesizeBanglaVoice(
        text = text,
        voice = voice,
        tone = tone,
        accent = accent
    )

    private fun parseMarkdownSections(markdown: String, fallbackTitle: String): List<StructuredSection> {
        val sections = mutableListOf<StructuredSection>()
        var currentHeading = fallbackTitle
        val currentBody = StringBuilder()

        for (line in markdown.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ")) {
                if (currentBody.isNotBlank()) {
                    sections.add(StructuredSection(currentHeading, currentBody.toString().trim()))
                    currentBody.clear()
                }
                currentHeading = trimmed.trimStart('#').trim()
            } else {
                currentBody.appendLine(line)
            }
        }
        if (currentBody.isNotBlank()) {
            sections.add(StructuredSection(currentHeading, currentBody.toString().trim()))
        }
        return sections.ifEmpty {
            listOf(StructuredSection(fallbackTitle, markdown.trim()))
        }
    }
}
