package com.example.ui.screens.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepository
import com.example.data.local.PreferencesRepository
import com.example.domain.model.AppLanguage
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

class VoiceViewModel(
    private val aiRepository: AiRepository,
    private val preferencesRepository: PreferencesRepository,
    private val speechHelper: SpeechHelper,
    private val speechRecognizerHelper: SpeechRecognizerHelper
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
                aiRepository.streamChatReply(
                    prompt = spokenQuery,
                    persona = MayaPersona.ASSISTANT,
                    history = emptyList(),
                    imageUri = null,
                    responseLength = ResponseLength.BALANCED,
                    language = lang
                ).collect { partial ->
                    latestReply = partial
                    _voiceReplyText.value = partial
                }
                _isReplying.value = false
                if (latestReply.isNotBlank()) {
                    speechHelper.toggleSpeak(
                        messageId = VOICE_INTERACTION_UTTERANCE_ID,
                        text = latestReply,
                        language = lang,
                        accent = _selectedAccent.value,
                        voicePreset = _selectedVoice.value,
                        tone = _selectedTone.value
                    )
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
    }

    class Factory(
        private val aiRepository: AiRepository,
        private val preferencesRepository: PreferencesRepository,
        private val speechHelper: SpeechHelper,
        private val speechRecognizerHelper: SpeechRecognizerHelper
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VoiceViewModel(
                aiRepository = aiRepository,
                preferencesRepository = preferencesRepository,
                speechHelper = speechHelper,
                speechRecognizerHelper = speechRecognizerHelper
            ) as T
        }
    }
}
