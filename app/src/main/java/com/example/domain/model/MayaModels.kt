package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppLanguage(val label: String, val shortTag: String) {
    ENGLISH("English", "EN"),
    BANGLA("বাংলা (Bangla)", "বাংলা")
}

@Serializable
enum class BanglaAccent(val label: String, val bcp47: String, val lang: String, val country: String) {
    BN_BD("বাংলাদেশ (bn-BD)", "bn-BD", "bn", "BD"),
    BN_IN("ভারত (bn-IN)", "bn-IN", "bn", "IN")
}

@Serializable
enum class GeminiVoicePreset(
    val voiceName: String,
    val trait: String,
    val genderLabel: String,
    val pitch: Float,
    val rate: Float
) {
    AOEDE("Aoede", "Breezy", "Female", 1.06f, 1.0f),
    KORE("Kore", "Firm", "Female", 0.98f, 0.96f),
    SULAFAT("Sulafat", "Warm", "Female", 1.02f, 0.94f),
    ZEPHYR("Zephyr", "Bright", "Female", 1.10f, 1.04f),
    PUCK("Puck", "Upbeat", "Male", 0.94f, 1.05f),
    ORUS("Orus", "Deep", "Male", 0.86f, 0.95f)
}

@Serializable
enum class VoiceTone(val label: String, val promptDirective: String) {
    WARM("Warm & Natural", "Speak warmly and naturally in clear Bengali"),
    CHEERFUL("Cheerful", "Speak cheerfully with upbeat energy"),
    CALM("Calm Narrator", "Speak slowly and calmly like an audiobook narrator"),
    INFORMATIVE("News Brief", "Speak crisply and clearly like a news anchor")
}

@Serializable
enum class MayaPersona(
    val displayName: String,
    val badge: String,
    val description: String
) {
    ASSISTANT(
        displayName = "Maya Core",
        badge = "Balanced",
        description = "Clear reasoning, daily planning, and thoughtful answers."
    ),
    CODER(
        displayName = "Architect",
        badge = "Kotlin & Tech",
        description = "Clean code, Compose UI patterns, and debugging help."
    ),
    CREATIVE(
        displayName = "Muse",
        badge = "Story & Copy",
        description = "Expressive writing, naming, and campaign ideation."
    ),
    ANALYST(
        displayName = "Strategist",
        badge = "Deep Briefs",
        description = "Structured breakdowns, pros/cons, and executive summaries."
    )
}

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT
}

@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestampMillis: Long,
    val persona: MayaPersona = MayaPersona.ASSISTANT,
    val imageUri: String? = null,
    val language: AppLanguage = AppLanguage.ENGLISH
)

@Serializable
data class ConversationThread(
    val id: String,
    val title: String,
    val persona: MayaPersona,
    val messages: List<ChatMessage>,
    val updatedAtMillis: Long,
    val isPinned: Boolean = false
)

@Serializable
enum class AiWorkflowTool(
    val title: String,
    val subtitle: String,
    val inputLabel: String,
    val samplePrompt: String,
    val requiresImageOptional: Boolean = false
) {
    SUMMARIZER(
        title = "Smart Summarizer",
        subtitle = "Distill notes, articles, or meeting logs into key takeaways & action items.",
        inputLabel = "Paste text or topic to summarize",
        samplePrompt = "Weekly product sync: Android launch checklist finalized, onboarding flow simplified from 5 steps to 2, QA regression starts Thursday, and release candidate ships Monday."
    ),
    CODE_COPILOT(
        title = "Code Architect",
        subtitle = "Generate clean Kotlin & Jetpack Compose patterns with architectural notes.",
        inputLabel = "Describe the component or architecture",
        samplePrompt = "Build a debounced search bar in Jetpack Compose using StateFlow and Material 3."
    ),
    COPY_FORGE(
        title = "Idea & Copy Forge",
        subtitle = "Craft taglines, launch announcements, and structured creative hooks.",
        inputLabel = "Describe your product, audience, and tone",
        samplePrompt = "A calm morning habit and focus companion app for remote designers, warm editorial tone."
    ),
    VISION_INSPECTOR(
        title = "Vision Inspector",
        subtitle = "Inspect an attached photo for composition, context, and visual details.",
        inputLabel = "What should Maya inspect in this photo?",
        samplePrompt = "Describe the visual mood, key elements, and suggested improvements.",
        requiresImageOptional = true
    )
}

@Serializable
data class StructuredSection(
    val heading: String,
    val body: String
)

@Serializable
data class StudioResult(
    val id: String,
    val tool: AiWorkflowTool,
    val inputPrompt: String,
    val imageUri: String? = null,
    val outputTitle: String,
    val sections: List<StructuredSection>,
    val rawMarkdown: String,
    val timestampMillis: Long
)

data class BanglaPhrasePreset(
    val label: String,
    val text: String,
    val tone: VoiceTone
)

data class BanglaVoiceSynthesisResult(
    val id: String,
    val spokenText: String,
    val voice: GeminiVoicePreset,
    val tone: VoiceTone,
    val accent: BanglaAccent,
    val engineLabel: String,
    val voiceNote: String
)

@Serializable
enum class ThemeMode(val label: String) {
    DARK("JARVIS HUD (Dark)"),
    LIGHT("Stark Blueprint"),
    SYSTEM("System Default")
}

@Serializable
enum class ResponseLength(val label: String) {
    CONCISE("Concise"),
    BALANCED("Balanced"),
    DETAILED("Detailed")
}

data class UserPreferences(
    val defaultPersona: MayaPersona = MayaPersona.ASSISTANT,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val responseLength: ResponseLength = ResponseLength.BALANCED,
    val autoReadAloud: Boolean = false,
    val preferredLanguage: AppLanguage = AppLanguage.BANGLA,
    val banglaAccent: BanglaAccent = BanglaAccent.BN_BD,
    val preferredVoicePreset: GeminiVoicePreset = GeminiVoicePreset.SULAFAT,
    val geminiApiKey: String = "",
    val geminiModelId: String = DEFAULT_GEMINI_MODEL
) {
    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
        val AVAILABLE_GEMINI_MODELS = listOf("gemini-3.8-flash", "gemini-3.1-pro-preview")
    }
}

sealed interface GeminiKeyStatus {
    data object Idle : GeminiKeyStatus
    data object Testing : GeminiKeyStatus
    data class Valid(val message: String) : GeminiKeyStatus
    data class Invalid(val message: String) : GeminiKeyStatus
}

sealed interface ChatStreamState {
    data object Idle : ChatStreamState
    data object Thinking : ChatStreamState
    data class Streaming(val partialText: String) : ChatStreamState
    data class Error(val message: String) : ChatStreamState
}

sealed interface StudioRunState {
    data object Idle : StudioRunState
    data object Running : StudioRunState
    data class Success(val result: StudioResult) : StudioRunState
    data class Error(val message: String) : StudioRunState
}

sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data object Preparing : VoiceUiState
    data class Ready(val result: BanglaVoiceSynthesisResult) : VoiceUiState
    data class Error(val message: String) : VoiceUiState
}

data class QuickStarter(
    val title: String,
    val prompt: String,
    val persona: MayaPersona,
    val language: AppLanguage = AppLanguage.ENGLISH
)
