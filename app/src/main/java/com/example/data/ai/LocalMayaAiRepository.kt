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
        delay(180)
        val containsBengali = prompt.any { it in '\u0980'..'\u09FF' }
        val effectiveLanguage = if (containsBengali) AppLanguage.BANGLA else language

        val fullResponse = if (effectiveLanguage == AppLanguage.BANGLA) {
            buildBanglaChatResponse(
                prompt = prompt.trim(),
                persona = persona,
                turnCount = history.size + 1,
                hasImage = !imageUri.isNullOrBlank(),
                responseLength = responseLength
            )
        } else {
            buildEnglishChatResponse(
                prompt = prompt.trim(),
                persona = persona,
                turnCount = history.size + 1,
                hasImage = !imageUri.isNullOrBlank(),
                responseLength = responseLength
            )
        }

        val words = fullResponse.split(" ")
        val builder = StringBuilder()
        for ((index, word) in words.withIndex()) {
            if (index > 0) builder.append(' ')
            builder.append(word)
            emit(builder.toString())
            delay(18)
        }
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
        delay(350)
        val cleanPrompt = prompt.trim().ifBlank { tool.samplePrompt }
        val topicPreview = cleanPrompt.take(72) + if (cleanPrompt.length > 72) "…" else ""

        val sections = when (tool) {
            AiWorkflowTool.SUMMARIZER -> listOf(
                StructuredSection(
                    heading = "Executive Summary",
                    body = "Structured digest for \"$topicPreview\": Core objectives are aligned, scope is focused for clarity, and immediate milestones are ready for execution."
                ),
                StructuredSection(
                    heading = "Key Takeaways",
                    body = "• Priority items separated from optional backlog\n• Cross-functional handoffs clearly sequenced\n• Risks reduced by validating early checkpoints"
                ),
                StructuredSection(
                    heading = "Recommended Next Actions",
                    body = "1. Confirm owner for each milestone.\n2. Lock target review date.\n3. Continue in Chat to expand or refine any section."
                )
            )

            AiWorkflowTool.CODE_COPILOT -> listOf(
                StructuredSection(
                    heading = "Architecture Notes",
                    body = "Pattern suggested for \"$topicPreview\": Keep UI stateless with StateFlow, hoist events cleanly, and isolate side effects inside a repository interface."
                ),
                StructuredSection(
                    heading = "Kotlin Snippet",
                    body = """
                        |@Composable
                        |fun MayaPanel(state: UiState, onAction: () -> Unit) {
                        |    ElevatedCard {
                        |        Text(text = state.summary)
                        |        Button(onClick = onAction) { Text("Run") }
                        |    }
                        |}
                    """.trimMargin()
                ),
                StructuredSection(
                    heading = "Best Practice",
                    body = "Use `collectAsStateWithLifecycle()` in Compose screens and keep business logic inside your ViewModel."
                )
            )

            AiWorkflowTool.COPY_FORGE -> listOf(
                StructuredSection(
                    heading = "Headline Concepts",
                    body = "• Built for quiet focus.\n• Clarity in every morning block.\n• Less noise, deeper craft."
                ),
                StructuredSection(
                    heading = "Launch Micro-Copy",
                    body = "Draft inspired by \"$topicPreview\": Meet a calmer workspace designed around rhythm, intention, and effortless momentum."
                ),
                StructuredSection(
                    heading = "Call-to-Action Options",
                    body = "Primary: \"Start Your Flow\" · Secondary: \"Explore Studio\""
                )
            )

            AiWorkflowTool.VISION_INSPECTOR -> {
                val mediaStatus = if (!imageUri.isNullOrBlank()) {
                    "Photo attached from Android Photo Picker."
                } else {
                    "No photo attached yet — select an image using the Photo Picker above."
                }
                listOf(
                    StructuredSection(
                        heading = "Visual Context",
                        body = "$mediaStatus\nInspection focus: \"$topicPreview\""
                    ),
                    StructuredSection(
                        heading = "Composition Checklist",
                        body = "• Subject separation: Balanced focal point with clean negative space\n• Contrast: Clear foreground-to-background hierarchy\n• Framing tip: Keep primary subject near rule-of-thirds intersections"
                    )
                )
            }
        }

        val depthFilteredSections = when (responseLength) {
            ResponseLength.CONCISE -> sections.take(2)
            ResponseLength.BALANCED, ResponseLength.DETAILED -> sections
        }

        val markdown = buildString {
            depthFilteredSections.forEach { section ->
                appendLine("### ${section.heading}")
                appendLine(section.body)
                appendLine()
            }
        }.trim()

        return StudioResult(
            id = UUID.randomUUID().toString(),
            tool = tool,
            inputPrompt = cleanPrompt,
            imageUri = imageUri,
            outputTitle = tool.title,
            sections = depthFilteredSections,
            rawMarkdown = markdown,
            timestampMillis = System.currentTimeMillis()
        )
    }

    private fun buildBanglaChatResponse(
        prompt: String,
        persona: MayaPersona,
        turnCount: Int,
        hasImage: Boolean,
        responseLength: ResponseLength
    ): String {
        val topic = prompt.ifBlank { "আপনার প্রশ্ন" }.take(90)
        val imageNote = if (hasImage) {
            "আপনার সংযুক্ত ছবিটি ফটো পিকার থেকে প্রিভিউ করা হয়েছে।\n\n"
        } else {
            ""
        }

        val thoughtBlock = "<!-- thought -->\nপ্রশ্নের মূল বিষয়বস্তু বিশ্লেষণ করা হচ্ছে: \"$topic\"। ব্যবহারকারীর ভাষা বাংলা হওয়ায় সহজ ও গোছানো বাংলায় উত্তর সাজানো হয়েছে।\n<!-- /thought -->\n\n"

        val isCapabilitiesQuery = prompt.contains("সক্ষমতা") ||
            prompt.contains("কী কী") ||
            prompt.contains("capabilities", ignoreCase = true)

        val coreBody = when {
            isCapabilitiesQuery -> """
                |${imageNote}মায়া এআই অ্যাপে আপনার জন্য প্রস্তুত প্রধান সক্ষমতাগুলো:
                |
                |---
                |
                |### ১. কোডিং ও আর্কিটেকচার
                |* **কোটলিন ও জেটপ্যাক কম্পোজ:** ক্লিন UI প্যাটার্ন, স্টেট ম্যানেজমেন্ট ও ডিবাগিং সহায়তা।
                |* **সিনট্যাক্স হাইলাইটিং:** কোড ব্লক সরাসরি কপি করার সুবিধা।
                |
                |### ২. বাংলা ভয়েস ও TTS
                |* **বাংলা উচ্চারণ:** `bn-BD` ও `bn-IN` অ্যাকসেন্টে অন-ডিভাইস ভয়েস প্লেব্যাক।
                |* **ভয়েস প্রোফাইল:** ভয়েস ট্যাবে `Aoede`, `Kore`, `Sulafat` ইত্যাদি টোন ও পিচ প্রিসেট।
                |
                |### ৩. স্টুডিও ওয়ার্কফ্লো ও ইতিহাস
                |* **AI স্টুডিও:** স্মার্ট সামারাইজার, কপি ফোর্জ ও ভিশন ইন্সপেক্টর।
                |* **লোকাল হিস্ট্রি:** কথোপকথন সংরক্ষণ, পিন ও সার্চ সুবিধা।
            """.trimMargin()

            persona == MayaPersona.ASSISTANT ->
                "${imageNote}### \"$topic\" বিষয়ে দিকনির্দেশনা\n* প্রথমে সবচেয়ে গুরুত্বপূর্ণ লক্ষ্যটি স্থির করুন।\n* কাজটিকে ২–৩টি ছোট ধাপে ভাগ করে শুরু করুন।\n* স্পিকার আইকনে ট্যাপ করে **বাংলা ভয়েসে** উত্তরটি শুনতে পারেন।"

            persona == MayaPersona.CODER ->
                "${imageNote}### Jetpack Compose প্যাটার্ন\n```kotlin\nval uiState by viewModel.uiState.collectAsStateWithLifecycle()\n```\n* স্টেটগুলো `StateFlow`-তে অপরিবর্তনীয় রাখুন।\n* সব এআই ও ভয়েস কল `AiRepository` ইন্টারফেসের মাধ্যমে পরিচালনা করুন।"

            persona == MayaPersona.CREATIVE ->
                "${imageNote}### সৃজনশীল বাংলা খসড়া\n* **দৃশ্যপট:** বর্ষার বিকেলে ভেজা পিচঢালা রাস্তায় লাল-নীল আলোর প্রতিফলন।\n* **সংলাপ:** \"অপেক্ষার এই নীরব মুহূর্তটাই তো গল্পের আসল প্রাণ।\"\n* ভয়েস ট্যাবে গিয়ে `Calm Narrator` টোনে এটি শুনতে পারেন।"

            else ->
                "${imageNote}### বিশ্লেষণ সারসংক্ষেপ (ধাপ $turnCount)\n* **সুবিধা:** দ্রুত কাজ শুরু করা যায় এবং স্থানীয়ভাবে ডেটা সুরক্ষিত থাকে।\n* **বিবেচ্য বিষয়:** অফলাইন অভিজ্ঞতা ও ব্যবহারকারীর সহজলভ্যতা।\n* **পরামর্শ:** প্রথমে মূল ব্যবহারকারীর অভিজ্ঞতা যাচাই করুন।"
        }

        val suffix = when (responseLength) {
            ResponseLength.CONCISE -> ""
            ResponseLength.BALANCED -> ""
            ResponseLength.DETAILED ->
                "\n\n---\n### অতিরিক্ত তথ্য\n* কথোপকথনের ধাপ: `$turnCount`\n* স্পিকার বাটনে ট্যাপ করলে ডিভাইসের বাংলা TTS দিয়ে শোনা যাবে।"
        }
        return thoughtBlock + coreBody + suffix
    }

    private fun buildEnglishChatResponse(
        prompt: String,
        persona: MayaPersona,
        turnCount: Int,
        hasImage: Boolean,
        responseLength: ResponseLength
    ): String {
        val topic = prompt.ifBlank { "your request" }.take(90)
        val imageNote = if (hasImage) {
            "Photo attached from the Android Photo Picker.\n\n"
        } else {
            ""
        }

        val coreBody = when (persona) {
            MayaPersona.ASSISTANT ->
                "${imageNote}### Perspective on \"$topic\"\n* Clarify the single highest-impact outcome first.\n* Break the work into 2–3 small, verifiable steps.\n* Keep feedback loops fast and observable."

            MayaPersona.CODER ->
                "${imageNote}### Clean Compose Pattern\n```kotlin\nval uiState by viewModel.uiState.collectAsStateWithLifecycle()\n```\n* Keep state immutable in a `StateFlow`.\n* Isolate async work cleanly in `viewModelScope`."

            MayaPersona.CREATIVE ->
                "${imageNote}### Creative Angles for \"$topic\"\n* **Warm & Human:** Lead with how the moment feels, not just features.\n* **Crisp & Bold:** Three-word rhythm with high contrast.\n* **Story Hook:** Open with a vivid contrast between before and after."

            MayaPersona.ANALYST ->
                "${imageNote}### Decision Brief (Turn $turnCount)\n* **Strengths:** Fast iteration, clear ownership, low complexity.\n* **Trade-offs:** Evaluate latency, offline resilience, and maintenance overhead.\n* **Recommendation:** Validate the core loop before expanding scope."
        }

        val suffix = when (responseLength) {
            ResponseLength.CONCISE, ResponseLength.BALANCED -> ""
            ResponseLength.DETAILED ->
                "\n\n---\n* Local conversation turns tracked: `$turnCount`\n* Active response depth: `${responseLength.label}`"
        }

        return coreBody + suffix
    }
}
