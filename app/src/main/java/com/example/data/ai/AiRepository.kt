package com.example.data.ai

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
import com.example.domain.model.StudioResult
import com.example.domain.model.VoiceTone
import kotlinx.coroutines.flow.Flow

interface AiRepository {
    fun getQuickStarters(): List<QuickStarter>

    fun getBanglaVoicePresets(): List<BanglaPhrasePreset>

    fun streamChatReply(
        prompt: String,
        persona: MayaPersona,
        history: List<ChatMessage>,
        imageUri: String?,
        responseLength: ResponseLength,
        language: AppLanguage
    ): Flow<String>

    suspend fun runWorkflowTool(
        tool: AiWorkflowTool,
        prompt: String,
        imageUri: String?,
        responseLength: ResponseLength
    ): StudioResult

    suspend fun synthesizeBanglaVoice(
        text: String,
        voice: GeminiVoicePreset,
        tone: VoiceTone,
        accent: BanglaAccent
    ): BanglaVoiceSynthesisResult
}
