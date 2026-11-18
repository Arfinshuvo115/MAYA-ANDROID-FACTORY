package com.example.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepository
import com.example.data.local.ConversationStore
import com.example.data.local.PreferencesRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatStreamState
import com.example.domain.model.ConversationThread
import com.example.domain.model.MayaPersona
import com.example.domain.model.MessageRole
import com.example.domain.model.QuickStarter
import com.example.domain.model.UserPreferences
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

class ChatViewModel(
    private val aiRepository: AiRepository,
    private val conversationStore: ConversationStore,
    private val preferencesRepository: PreferencesRepository,
    private val speechHelper: SpeechHelper,
    private val speechRecognizerHelper: SpeechRecognizerHelper
) : ViewModel() {

    val quickStarters: List<QuickStarter> = aiRepository.getQuickStarters()
    val speakingMessageId: StateFlow<String?> = speechHelper.speakingMessageId
    val speechToTextState: StateFlow<SpeechToTextState> = speechRecognizerHelper.state

    private val preferencesState = preferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserPreferences()
    )

    private val _activeThreadId = MutableStateFlow(UUID.randomUUID().toString())
    val activeThreadId: StateFlow<String> = _activeThreadId.asStateFlow()

    private val _selectedPersona = MutableStateFlow(MayaPersona.ASSISTANT)
    val selectedPersona: StateFlow<MayaPersona> = _selectedPersona.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(AppLanguage.BANGLA)
    val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    private val _attachedImageUri = MutableStateFlow<String?>(null)
    val attachedImageUri: StateFlow<String?> = _attachedImageUri.asStateFlow()

    private val _streamState = MutableStateFlow<ChatStreamState>(ChatStreamState.Idle)
    val streamState: StateFlow<ChatStreamState> = _streamState.asStateFlow()

    private var generationJob: Job? = null
    private var userExplicitlyChosePersona = false

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collectLatest { prefs ->
                _selectedLanguage.value = prefs.preferredLanguage
                if (!userExplicitlyChosePersona && _messages.value.isEmpty()) {
                    _selectedPersona.value = prefs.defaultPersona
                }
            }
        }
    }

    fun selectPersona(persona: MayaPersona) {
        userExplicitlyChosePersona = true
        _selectedPersona.value = persona
    }

    fun toggleLanguage() {
        val next = if (_selectedLanguage.value == AppLanguage.BANGLA) {
            AppLanguage.ENGLISH
        } else {
            AppLanguage.BANGLA
        }
        _selectedLanguage.value = next
        viewModelScope.launch {
            preferencesRepository.setPreferredLanguage(next)
        }
    }

    fun updateComposerText(text: String) {
        _composerText.value = text
    }

    fun attachImage(uriString: String?) {
        _attachedImageUri.value = uriString
    }

    fun startVoiceListening() {
        speechHelper.stop()
        val langTag = if (_selectedLanguage.value == AppLanguage.BANGLA) {
            preferencesState.value.banglaAccent.bcp47
        } else {
            "en-US"
        }
        speechRecognizerHelper.startListening(languageTag = langTag) { transcript ->
            sendMessage(
                overridePrompt = transcript,
                speakReplyAfter = true
            )
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

    fun startNewChat() {
        generationJob?.cancel()
        speechHelper.stop()
        speechRecognizerHelper.cancel()
        _streamState.value = ChatStreamState.Idle
        _activeThreadId.value = UUID.randomUUID().toString()
        _messages.value = emptyList()
        _composerText.value = ""
        _attachedImageUri.value = null
        userExplicitlyChosePersona = false
        _selectedPersona.value = preferencesState.value.defaultPersona
    }

    fun loadThread(threadId: String) {
        val thread = conversationStore.threads.value.firstOrNull { it.id == threadId } ?: return
        generationJob?.cancel()
        speechHelper.stop()
        speechRecognizerHelper.cancel()
        _streamState.value = ChatStreamState.Idle
        _activeThreadId.value = thread.id
        _selectedPersona.value = thread.persona
        _messages.value = thread.messages
        _attachedImageUri.value = null
        userExplicitlyChosePersona = true
    }

    fun injectStudioContext(title: String, markdownContent: String) {
        startNewChat()
        val seedMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.ASSISTANT,
            content = "Loaded from AI Studio ($title):\n\n$markdownContent",
            timestampMillis = System.currentTimeMillis(),
            persona = _selectedPersona.value,
            language = _selectedLanguage.value
        )
        _messages.value = listOf(seedMessage)
        persistCurrentThread(customTitle = title)
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        val currentPartial = (_streamState.value as? ChatStreamState.Streaming)?.partialText
        if (!currentPartial.isNullOrBlank()) {
            commitAssistantMessage(currentPartial, _selectedLanguage.value)
        }
        _streamState.value = ChatStreamState.Idle
    }

    fun sendMessage(
        overridePrompt: String? = null,
        overridePersona: MayaPersona? = null,
        overrideLanguage: AppLanguage? = null,
        speakReplyAfter: Boolean = false
    ) {
        val promptToSend = (overridePrompt ?: _composerText.value).trim()
        val imageToSend = if (overridePrompt == null) _attachedImageUri.value else null
        if (promptToSend.isBlank() && imageToSend == null) return

        if (overridePersona != null) {
            _selectedPersona.value = overridePersona
            userExplicitlyChosePersona = true
        }
        if (overrideLanguage != null) {
            _selectedLanguage.value = overrideLanguage
            viewModelScope.launch {
                preferencesRepository.setPreferredLanguage(overrideLanguage)
            }
        }

        val activeLanguage = overrideLanguage ?: _selectedLanguage.value
        val effectivePrompt = promptToSend.ifBlank {
            if (activeLanguage == AppLanguage.BANGLA) "এই সংযুক্ত ছবিটি বিশ্লেষণ করো।" else "Please analyze this attached photo."
        }
        val activePersona = _selectedPersona.value

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = effectivePrompt,
            timestampMillis = System.currentTimeMillis(),
            persona = activePersona,
            imageUri = imageToSend,
            language = activeLanguage
        )

        val historySnapshot = _messages.value
        _messages.value = historySnapshot + userMessage
        if (overridePrompt == null) {
            _composerText.value = ""
            _attachedImageUri.value = null
        }

        persistCurrentThread()

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _streamState.value = ChatStreamState.Thinking
            var latestText = ""
            try {
                aiRepository.streamChatReply(
                    prompt = effectivePrompt,
                    persona = activePersona,
                    history = historySnapshot,
                    imageUri = imageToSend,
                    responseLength = preferencesState.value.responseLength,
                    language = activeLanguage
                ).collect { chunk ->
                    latestText = chunk
                    _streamState.value = ChatStreamState.Streaming(chunk)
                }

                if (latestText.isNotBlank()) {
                    val savedMsg = commitAssistantMessage(latestText, activeLanguage)
                    if (speakReplyAfter || preferencesState.value.autoReadAloud) {
                        speakMessage(savedMsg)
                    }
                }
                _streamState.value = ChatStreamState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _streamState.value = ChatStreamState.Error(
                    e.localizedMessage ?: "Unable to generate a response right now."
                )
            }
        }
    }

    fun toggleSpeak(message: ChatMessage) {
        speakMessage(message)
    }

    private fun speakMessage(message: ChatMessage) {
        val prefs = preferencesState.value
        speechHelper.toggleSpeak(
            messageId = message.id,
            text = message.content,
            language = message.language,
            accent = prefs.banglaAccent,
            voicePreset = prefs.preferredVoicePreset
        )
    }

    private fun commitAssistantMessage(content: String, language: AppLanguage): ChatMessage {
        val assistantMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.ASSISTANT,
            content = content,
            timestampMillis = System.currentTimeMillis(),
            persona = _selectedPersona.value,
            language = language
        )
        _messages.value = _messages.value + assistantMsg
        persistCurrentThread()
        return assistantMsg
    }

    private fun persistCurrentThread(customTitle: String? = null) {
        val currentMessages = _messages.value
        if (currentMessages.isEmpty()) return

        val existing = conversationStore.threads.value.firstOrNull { it.id == _activeThreadId.value }
        val derivedTitle = customTitle ?: existing?.title ?: currentMessages
            .firstOrNull { it.role == MessageRole.USER }
            ?.content
            ?.replace('\n', ' ')
            ?.take(42)
            ?.let { if (it.length == 42) "$it…" else it }
            ?: "Maya Conversation"

        val thread = ConversationThread(
            id = _activeThreadId.value,
            title = derivedTitle,
            persona = _selectedPersona.value,
            messages = currentMessages,
            updatedAtMillis = System.currentTimeMillis(),
            isPinned = existing?.isPinned ?: false
        )
        viewModelScope.launch {
            conversationStore.upsertThread(thread)
        }
    }

    class Factory(
        private val aiRepository: AiRepository,
        private val conversationStore: ConversationStore,
        private val preferencesRepository: PreferencesRepository,
        private val speechHelper: SpeechHelper,
        private val speechRecognizerHelper: SpeechRecognizerHelper
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(
                aiRepository = aiRepository,
                conversationStore = conversationStore,
                preferencesRepository = preferencesRepository,
                speechHelper = speechHelper,
                speechRecognizerHelper = speechRecognizerHelper
            ) as T
        }
    }
}
