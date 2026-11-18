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
import com.example.domain.model.StructuredSection
import com.example.domain.model.StudioResult
import com.example.domain.model.VoiceTone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Local on-device implementation for Maya AI chat, studio workflows, and Bangla TTS preparation.
 */
class LocalMayaAiRepository : AiRepository {

    override fun getQuickStarters(): List<QuickStarter> = listOf(
        QuickStarter(
            title = "মায়ার সক্ষমতা",
            prompt = "তুমি কী কী কাজে সাহায্য করতে পারো? বাংলায় সংক্ষেপে দেখাও",
            persona = MayaPersona.ASSISTANT,
            language = AppLanguage.BANGLA
        ),
        QuickStarter(
            title = "বাংলা গল্প ও ভয়েস",
            prompt = "বর্ষার বিকেলে ঢাকার রাস্তার ওপর একটি ছোট্ট গল্প বলো",
            persona = MayaPersona.CREATIVE,
            language = AppLanguage.BANGLA
        ),
        QuickStarter(
            title = "Compose Motion",
            prompt = "Design a smooth spring-animated card transition in Jetpack Compose",
            persona = MayaPersona.CODER,
            language = AppLanguage.ENGLISH
        ),
        QuickStarter(
            title = "Decision Matrix",
            prompt = "Compare local-first storage vs cloud sync for a mobile productivity app",
            persona = MayaPersona.ANALYST,
            language = AppLanguage.ENGLISH
        )
    )

    override fun getBanglaVoicePresets(): List<BanglaPhrasePreset> = listOf(
        BanglaPhrasePreset(
            label = "শুভেচ্ছা",
            text = "নমস্কার! আমি মায়া এআই। আজ আপনাকে কীভাবে সাহায্য করতে পারি?",
            tone = VoiceTone.WARM
        ),
        BanglaPhrasePreset(
            label = "উৎসাহী",
            text = "দারুণ খবর! আপনার নতুন অ্যান্ড্রয়েড প্রজেক্টটি এখন পুরোপুরি প্রস্তুত।",
            tone = VoiceTone.CHEERFUL
        ),
        BanglaPhrasePreset(
            label = "গল্পকথক",
            text = "সকালের মিষ্টি রোদে নদীর তীরে বসে চুপচাপ গল্প শুনতে অসাধারণ লাগে।",
            tone = VoiceTone.CALM
        ),
        BanglaPhrasePreset(
            label = "সংবাদ পাঠ",
            text = "আজকের আবহাওয়া মনোরম থাকবে এবং বিকেলের দিকে হালকা বাতাস বইতে পারে।",
            tone = VoiceTone.INFORMATIVE
        )
    )

    override fun streamChatReply(
        prompt: String,
        persona: MayaPersona,
        history: List<ChatMessage>,
        imageUri: String?,
        responseLength: ResponseLength,
        language: AppLanguage
    ): Flow<String> = flow {
        delay(120)
        val isBangla = prompt.any { it in '\u0980'..'\u09FF' } || language == AppLanguage.BANGLA
        emit(
            if (isBangla) {
                "এই ডিভাইসে এখনো কোনো Local AI Model ইনস্টল বা সংযুক্ত নেই। তাই আমি AI উত্তর তৈরি করার ভান করছি না। " +
                    "ইন্টারনেট চালু করে Gemini API Key যুক্ত করুন, অথবা সমর্থিত Local Model Runtime সেটআপ করুন। " +
                    "আপনার লেখা ও প্রজেক্ট ফাইল এই fallback থেকে কোনো AI-তে পাঠানো হচ্ছে না।"
            } else {
                "No on-device language model is installed or connected. I will not present a scripted placeholder as an AI-generated answer. " +
                    "Connect Gemini with a valid API key, or configure a supported local model runtime. " +
                    "This fallback does not upload your prompt or project files."
            }
        )
    }

    override suspend fun synthesizeGeminiSpeech(
        text: String,
        voice: GeminiVoicePreset,
        tone: VoiceTone,
        accent: BanglaAccent
    ): Result<ByteArray> = Result.failure(
        IllegalStateException("Gemini TTS requires a configured Gemini API key.")
    )

    override suspend fun synthesizeBanglaVoice(
        text: String,
        voice: GeminiVoicePreset,
        tone: VoiceTone,
        accent: BanglaAccent
    ): BanglaVoiceSynthesisResult {
        delay(160)
        val cleanText = text.trim().ifBlank {
            getBanglaVoicePresets().first().text
        }
        return BanglaVoiceSynthesisResult(
            id = UUID.randomUUID().toString(),
            spokenText = cleanText,
            voice = voice,
            tone = tone,
            accent = accent,
            engineLabel = "On-Device Bangla TTS (${accent.bcp47})",
            voiceNote = "Playing locally with ${voice.voiceName} pitch/rate profile (${tone.label})."
        )
    }

    override suspend fun runWorkflowTool(
        tool: AiWorkflowTool,
        prompt: String,
        imageUri: String?,
        responseLength: ResponseLength
    ): StudioResult {
        val cleanPrompt = prompt.trim().ifBlank { tool.samplePrompt }
        val message = "Not executed: no on-device AI model is installed. Connect Gemini or configure a supported local model runtime before running this workflow."
        val sections = listOf(
            StructuredSection(
                heading = "AI runtime unavailable",
                body = message
            )
        )
        val markdown = "### AI runtime unavailable\n$message"
        return StudioResult(
            id = UUID.randomUUID().toString(),
            tool = tool,
            inputPrompt = cleanPrompt,
            imageUri = imageUri,
            outputTitle = tool.title + " · Not run",
            sections = sections,
            rawMarkdown = markdown,
            timestampMillis = System.currentTimeMillis()
        )
    }

}
