package com.example.ui.screens.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepository
import com.example.data.local.PreferencesRepository
import com.example.data.local.ConversationStore
import com.example.missioncontrol.android.MayaAppCommandRouter
import com.example.domain.model.AppLanguage
import com.example.domain.model.ChatMessage
import com.example.domain.model.ConversationThread
import com.example.domain.model.MessageRole
import com.example.domain.model.BanglaAccent
import com.example.domain.model.BanglaPhrasePreset
import com.example.domain.model.GeminiVoicePreset
import com.example.domain.model.MayaPersona
import com.example.domain.model.ResponseLength
import com.example.domain.model.UserPreferences
import com.example.domain.model.VoiceTone
import com.example.domain.model.VoiceUiState
import com.example.util.SpeechHelper
import com.example.util.SpeechRecognizerHelper
import com.example.util.SpeechToTextState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class VoiceViewModel(
    private val aiRepository: AiRepository,
    private val preferencesRepository: PreferencesRepository,
    private val conversationStore: ConversationStore,
    private val speechHelper: SpeechHelper,
    private val speechRecognizerHelper: SpeechRecognizerHelper,
    private val appCommandRouter: MayaAppCommandRouter
) : ViewModel() {

    val presets: List<BanglaPhrasePreset> = aiRepository.getBanglaVoicePresets()
    val speakingMessageId: StateFlow<String?> = speechHelper.speakingMessageId
    val banglaVoiceAvailable: StateFlow<Boolean> = speechHelper.banglaVoiceAvailable
    val speechToTextState: StateFlow<SpeechToTextState> = speechRecognizerHelper.state

    private val _inputText = MutableStateFlow(presets.first().text)
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedVoice = MutableStateFlow(GeminiVoicePreset.SULAFAT)
    val selectedVoice: StateFlow<GeminiVoicePreset> = _selectedVoice.asStateFlow()

    private val _selectedTone = MutableStateFlow(VoiceTone.WARM)
    val selectedTone: StateFlow<VoiceTone> = _selectedTone.asStateFlow()

    private val _selectedAccent = MutableStateFlow(BanglaAccent.BN_BD)
    val selectedAccent: StateFlow<BanglaAccent> = _selectedAccent.asStateFlow()

    private val _lastSpokenQuery = MutableStateFlow<String?>(null)
    val lastSpokenQuery: StateFlow<String?> = _lastSpokenQuery.asStateFlow()

    private val _voiceReplyText = MutableStateFlow<String?>(null)
    val voiceReplyText: StateFlow<String?> = _voiceReplyText.asStateFlow()

    private val _isReplying = MutableStateFlow(false)
    val isReplying: StateFlow<Boolean> = _isReplying.asStateFlow()

    private val _uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var userChangedVoiceOrAccent = false
    private var voiceInteractionJob: Job? = null

    init {
        preferencesRepository.preferences.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserPreferences()
        ).let { flow ->
            viewModelScope.launch {
                flow.collectLatest { prefs ->
                    if (!userChangedVoiceOrAccent) {
                        _selectedVoice.value = prefs.preferredVoicePreset
                        _selectedAccent.value = prefs.banglaAccent
                    }
                }
            }
        }
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun applyPreset(preset: BanglaPhrasePreset) {
        _inputText.value = preset.text
        _selectedTone.value = preset.tone
    }

    fun selectVoice(voice: GeminiVoicePreset) {
        userChangedVoiceOrAccent = true
        _selectedVoice.value = voice
        viewModelScope.launch {
            preferencesRepository.setPreferredVoicePreset(voice)
        }
    }

    fun selectTone(tone: VoiceTone) {
        _selectedTone.value = tone
    }

    fun selectAccent(accent: BanglaAccent) {
        userChangedVoiceOrAccent = true
        _selectedAccent.value = accent
        viewModelScope.launch {
            preferencesRepository.setBanglaAccent(accent)
        }
    }

    fun startVoiceInteraction() {
        speechHelper.stop()
        speechRecognizerHelper.startListening(languageTag = _selectedAccent.value.bcp47) { transcript ->
            triggerVoiceInteractionResponse(transcript)
        }
    }

    fun stopVoiceListening() {
        speechRecognizerHelper.stopListening()
    }

    fun cancelVoiceListening() {
        speechRecognizerHelper.cancel()
    }

    fun reportMicPermissionDenied() {
        speechRecognizerHelper.reportPermissionDenied()
    }

    fun clearSpeechError() {
        speechRecognizerHelper.clearError()
    }


    private fun persistVoiceThread(
        threadId: String,
        existing: ConversationThread?,
        messages: List<ChatMessage>
    ) {
        val thread = ConversationThread(
            id = threadId,
            title = existing?.title ?: "Maya Voice Conversations",
            persona = MayaPersona.ASSISTANT,
            messages = messages.takeLast(100),
            updatedAtMillis = System.currentTimeMillis(),
            isPinned = true
        )
        viewModelScope.launch { conversationStore.upsertThread(thread) }
    }

    private fun isVideoProductionRequest(text: String): Boolean {
        val normalized = text.lowercase()
        val mentionsVideo = normalized.contains("ভিডিও") || normalized.contains("video")
        val asksToCreate = listOf("বানাও", "তৈরি", "করো", "স্ক্রিপ্ট", "make", "create", "generate", "script")
            .any(normalized::contains)
        return mentionsVideo && asksToCreate
    }

    private suspend fun speakReply(text: String, language: AppLanguage) {
        if (text.isBlank()) return
        val accent = _selectedAccent.value
        val audio = aiRepository.synthesizeGeminiSpeech(
            text = text,
            voice = _selectedVoice.value,
            tone = _selectedTone.value,
            accent = accent
        )
        if (audio.isSuccess) {
            speechHelper.playGeminiAudio(audio.getOrThrow(), VOICE_INTERACTION_UTTERANCE_ID)
        } else {
            speechHelper.toggleSpeak(
                messageId = VOICE_INTERACTION_UTTERANCE_ID,
                text = text,
                language = language,
                accent = accent,
                voicePreset = _selectedVoice.value,
                tone = _selectedTone.value
            )
        }
    }

    private fun triggerVoiceInteractionResponse(spokenQuery: String) {
        _lastSpokenQuery.value = spokenQuery
        _inputText.value = spokenQuery
        voiceInteractionJob?.cancel()
        voiceInteractionJob = viewModelScope.launch {
            _isReplying.value = true
            _voiceReplyText.value = ""
            var latestReply = ""
            try {
                val containsBengali = spokenQuery.any { it in '\u0980'..'\u09FF' }
                val lang = if (containsBengali) AppLanguage.BANGLA else AppLanguage.ENGLISH
                val voiceThreadId = VOICE_MEMORY_THREAD_ID
                val savedThread = conversationStore.threads.value.firstOrNull { it.id == voiceThreadId }
                val historySnapshot = savedThread?.messages.orEmpty().takeLast(12)
                val userMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.USER,
                    content = spokenQuery,
                    timestampMillis = System.currentTimeMillis(),
                    persona = MayaPersona.ASSISTANT,
                    language = lang
                )
                val updatedHistory = historySnapshot + userMessage
                persistVoiceThread(voiceThreadId, savedThread, updatedHistory)
                val localCommandReply = appCommandRouter.execute(spokenQuery)
                if (localCommandReply != null) {
                    _voiceReplyText.value = localCommandReply
                    _isReplying.value = false
                    speakReply(localCommandReply, lang)
                    return@launch
                }
                val videoRequest = isVideoProductionRequest(spokenQuery)
                val effectivePrompt = if (videoRequest) {
                    listOf(
                        "You are Maya, the user's video production manager. The user asked:",
                        spokenQuery,
                        "Create a complete, practical video production script in the same language as the user's request.",
                        "Include: title and concept; target audience and platform assumption; a strong opening hook;",
                        "complete narration/dialogue; 6-10 numbered scenes with timestamps, visual direction, camera framing,",
                        "on-screen text and sound/music direction; subtitles/captions; thumbnail concept; production checklist.",
                        "If duration or platform is unspecified, state reasonable assumptions.",
                        "Never claim a video file has been rendered, exported, uploaded, or published.",
                        "Clearly state that this is the script/production plan only; actual rendering requires a connected video-generation service."
                    ).joinToString("\\n")
                } else spokenQuery
                aiRepository.streamChatReply(
                    prompt = effectivePrompt,
                    persona = MayaPersona.ASSISTANT,
                    history = updatedHistory,
                    imageUri = null,
                    responseLength = ResponseLength.BALANCED,
                    language = lang
                ).collect { partial ->
                    latestReply = partial
                    _voiceReplyText.value = partial
                }
                _isReplying.value = false
                if (latestReply.isNotBlank()) {
                    val assistantMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.ASSISTANT,
                        content = latestReply,
                        timestampMillis = System.currentTimeMillis(),
                        persona = MayaPersona.ASSISTANT,
                        language = lang
                    )
                    persistVoiceThread(voiceThreadId, savedThread, updatedHistory + assistantMessage)
                    speakReply(latestReply, lang)
                }
            } catch (e: CancellationException) {
                _isReplying.value = false
                throw e
            } catch (e: Exception) {
                _isReplying.value = false
                _uiState.value = VoiceUiState.Error(
                    e.localizedMessage ?: "Voice interaction failed."
                )
            }
        }
    }

    fun speakOrStop() {
        if (speakingMessageId.value == VOICE_STUDIO_UTTERANCE_ID ||
            speakingMessageId.value == VOICE_INTERACTION_UTTERANCE_ID
        ) {
            speechHelper.stop()
            return
        }

        val textToSpeak = _inputText.value.trim()
        if (textToSpeak.isBlank()) {
            _uiState.value = VoiceUiState.Error("বাংলা অথবা ইংরেজি টেক্সট লিখুন (Enter text to speak).")
            return
        }

        viewModelScope.launch {
            _uiState.value = VoiceUiState.Preparing
            try {
                val geminiAudio = aiRepository.synthesizeGeminiSpeech(
                    text = textToSpeak,
                    voice = _selectedVoice.value,
                    tone = _selectedTone.value,
                    accent = _selectedAccent.value
                )
                if (geminiAudio.isSuccess) {
                    speechHelper.playGeminiAudio(geminiAudio.getOrThrow(), VOICE_STUDIO_UTTERANCE_ID)
                    _uiState.value = VoiceUiState.Ready(
                        aiRepository.synthesizeBanglaVoice(
                            text = textToSpeak,
                            voice = _selectedVoice.value,
                            tone = _selectedTone.value,
                            accent = _selectedAccent.value
                        )
                    )
                } else {
                    val synthesis = aiRepository.synthesizeBanglaVoice(
                        text = textToSpeak,
                        voice = _selectedVoice.value,
                        tone = _selectedTone.value,
                        accent = _selectedAccent.value
                    )
                    _uiState.value = VoiceUiState.Ready(synthesis)
                    speechHelper.toggleSpeak(
                        messageId = VOICE_STUDIO_UTTERANCE_ID,
                        text = synthesis.spokenText,
                        language = AppLanguage.BANGLA,
                        accent = synthesis.accent,
                        voicePreset = synthesis.voice,
                        tone = synthesis.tone
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = VoiceUiState.Error(
                    e.localizedMessage ?: "Unable to prepare Bangla voice."
                )
            }
        }
    }

    fun stopSpeaking() {
        speechHelper.stop()
    }

    companion object {
        const val VOICE_STUDIO_UTTERANCE_ID = "bangla_voice_studio_player"
        const val VOICE_INTERACTION_UTTERANCE_ID = "bangla_voice_interaction_player"
        const val VOICE_MEMORY_THREAD_ID = "maya_persistent_voice_memory"
    }

    class Factory(
        private val aiRepository: AiRepository,
        private val preferencesRepository: PreferencesRepository,
        private val conversationStore: ConversationStore,
        private val speechHelper: SpeechHelper,
        private val speechRecognizerHelper: SpeechRecognizerHelper,
        private val appCommandRouter: MayaAppCommandRouter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VoiceViewModel(
                aiRepository = aiRepository,
                preferencesRepository = preferencesRepository,
                conversationStore = conversationStore,
                speechHelper = speechHelper,
                speechRecognizerHelper = speechRecognizerHelper,
                appCommandRouter = appCommandRouter
            ) as T
        }
    }
}
