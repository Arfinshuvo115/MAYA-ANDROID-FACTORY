package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiRestClient
import com.example.data.local.ConversationStore
import com.example.data.local.PreferencesRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.BanglaAccent
import com.example.domain.model.GeminiKeyStatus
import com.example.domain.model.GeminiVoicePreset
import com.example.domain.model.MayaPersona
import com.example.domain.model.ResponseLength
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val conversationStore: ConversationStore,
    private val geminiRestClient: GeminiRestClient
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences()
    )

    private val _keyStatus = MutableStateFlow<GeminiKeyStatus>(GeminiKeyStatus.Idle)
    val keyStatus: StateFlow<GeminiKeyStatus> = _keyStatus.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                preferencesRepository.migrateLegacyApiKeys()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _keyStatus.value = GeminiKeyStatus.Invalid(
                    "Secure API key migration failed. Existing legacy settings were preserved; retry after checking device security."
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDefaultPersona(persona: MayaPersona) {
        viewModelScope.launch { preferencesRepository.setDefaultPersona(persona) }
    }

    fun setResponseLength(length: ResponseLength) {
        viewModelScope.launch { preferencesRepository.setResponseLength(length) }
    }

    fun setAutoReadAloud(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoReadAloud(enabled) }
    }

    fun setPreferredLanguage(language: AppLanguage) {
        viewModelScope.launch { preferencesRepository.setPreferredLanguage(language) }
    }

    fun setBanglaAccent(accent: BanglaAccent) {
        viewModelScope.launch { preferencesRepository.setBanglaAccent(accent) }
    }

    fun setPreferredVoicePreset(voice: GeminiVoicePreset) {
        viewModelScope.launch { preferencesRepository.setPreferredVoicePreset(voice) }
    }

    fun setGeminiModelId(modelId: String) {
        _keyStatus.value = GeminiKeyStatus.Idle
        viewModelScope.launch { preferencesRepository.setGeminiModelId(modelId) }
    }

    fun saveGeminiApiKey(rawKey: String, onSaved: (Boolean) -> Unit) {
        val cleaned = rawKey.trim()
        viewModelScope.launch {
            try {
                preferencesRepository.setGeminiApiKey(cleaned)
                _keyStatus.value = if (cleaned.isEmpty()) {
                    GeminiKeyStatus.Idle
                } else {
                    GeminiKeyStatus.Valid("Saved securely on this device (${preferences.value.geminiModelId})")
                }
                onSaved(cleaned.isNotEmpty())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _keyStatus.value = GeminiKeyStatus.Invalid("Could not securely save the Gemini API key. Check device security and try again.")
            }
        }
    }

    fun verifyGeminiApiKey(rawKey: String) {
        val cleaned = rawKey.trim()
        if (cleaned.isEmpty()) {
            _keyStatus.value = GeminiKeyStatus.Invalid("Enter a Gemini API key first.")
            return
        }
        viewModelScope.launch {
            _keyStatus.value = GeminiKeyStatus.Testing
            val modelId = preferences.value.geminiModelId
            val result = geminiRestClient.verifyApiKey(cleaned, modelId)
            result.fold(
                onSuccess = { msg ->
                    try {
                        preferencesRepository.setGeminiApiKey(cleaned)
                        _keyStatus.value = GeminiKeyStatus.Valid("$msg · saved securely")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        _keyStatus.value = GeminiKeyStatus.Invalid(
                            "Gemini key verified, but secure storage failed. The key was not saved."
                        )
                    }
                },
                onFailure = { err ->
                    _keyStatus.value = GeminiKeyStatus.Invalid(
                        err.localizedMessage ?: "Verification failed."
                    )
                }
            )
        }
    }

    fun clearGeminiApiKey(onCleared: () -> Unit) {
        viewModelScope.launch {
            try {
                preferencesRepository.clearGeminiApiKey()
                _keyStatus.value = GeminiKeyStatus.Idle
                onCleared()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _keyStatus.value = GeminiKeyStatus.Invalid("Could not remove the encrypted Gemini API key. Try again.")
            }
        }
    }

    fun resetKeyStatus() {
        if (_keyStatus.value !is GeminiKeyStatus.Testing) {
            _keyStatus.value = GeminiKeyStatus.Idle
        }
    }

    fun clearAllHistory(onComplete: () -> Unit) {
        viewModelScope.launch {
            conversationStore.clearAll()
            onComplete()
        }
    }

    class Factory(
        private val preferencesRepository: PreferencesRepository,
        private val conversationStore: ConversationStore,
        private val geminiRestClient: GeminiRestClient
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesRepository, conversationStore, geminiRestClient) as T
        }
    }
}
