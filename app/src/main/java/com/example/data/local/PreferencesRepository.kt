package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppLanguage
import com.example.domain.model.BanglaAccent
import com.example.domain.model.GeminiVoicePreset
import com.example.domain.model.MayaPersona
import com.example.domain.model.ResponseLength
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.mayaDataStore: DataStore<Preferences> by preferencesDataStore(name = "maya_prefs")
private val apiCredentialMutex = Mutex()

class PreferencesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.mayaDataStore
    private val secureCredentialStore = SecureCredentialStore(appContext)
    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            defaultPersona = prefs[KEY_PERSONA]?.let { name ->
                MayaPersona.entries.firstOrNull { it.name == name }
            } ?: MayaPersona.ASSISTANT,
            themeMode = prefs[KEY_THEME]?.let { name ->
                ThemeMode.entries.firstOrNull { it.name == name }
            } ?: ThemeMode.DARK,
            responseLength = prefs[KEY_RESPONSE_LENGTH]?.let { name ->
                ResponseLength.entries.firstOrNull { it.name == name }
            } ?: ResponseLength.BALANCED,
            autoReadAloud = prefs[KEY_AUTO_READ] ?: false,
            preferredLanguage = prefs[KEY_LANGUAGE]?.let { name ->
                AppLanguage.entries.firstOrNull { it.name == name }
            } ?: AppLanguage.BANGLA,
            banglaAccent = prefs[KEY_BANGLA_ACCENT]?.let { name ->
                BanglaAccent.entries.firstOrNull { it.name == name }
            } ?: BanglaAccent.BN_BD,
            preferredVoicePreset = prefs[KEY_VOICE_PRESET]?.let { name ->
                GeminiVoicePreset.entries.firstOrNull { it.name == name }
            } ?: GeminiVoicePreset.SULAFAT,
            geminiApiKey = readSecureCredential(GEMINI_CREDENTIAL_ID),
            tavilyApiKey = readSecureCredential(TAVILY_CREDENTIAL_ID),
            geminiModelId = prefs[KEY_GEMINI_MODEL]?.takeIf { it.isNotBlank() }
                ?: UserPreferences.DEFAULT_GEMINI_MODEL
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Migrates legacy plaintext API keys. Each value is encrypted before the
     * corresponding DataStore field is removed. If encryption fails, the
     * plaintext value remains available for a later retry.
     */
    suspend fun migrateLegacyApiKeys() = withContext(Dispatchers.IO) {
        apiCredentialMutex.withLock {
            val legacy = dataStore.data.first()
            val legacyGemini = legacy[KEY_GEMINI_API_KEY].orEmpty().trim()
            val legacyTavily = legacy[KEY_TAVILY_API_KEY].orEmpty().trim()

            if (legacyGemini.isNotEmpty()) {
                val encryptedValue = runCatching { secureCredentialStore.get(GEMINI_CREDENTIAL_ID) }.getOrNull()
                if (encryptedValue.isNullOrEmpty()) secureCredentialStore.put(GEMINI_CREDENTIAL_ID, legacyGemini)
            }
            if (legacyTavily.isNotEmpty()) {
                val encryptedValue = runCatching { secureCredentialStore.get(TAVILY_CREDENTIAL_ID) }.getOrNull()
                if (encryptedValue.isNullOrEmpty()) secureCredentialStore.put(TAVILY_CREDENTIAL_ID, legacyTavily)
            }

            dataStore.edit { prefs ->
                prefs.remove(KEY_GEMINI_API_KEY)
                prefs.remove(KEY_TAVILY_API_KEY)
            }
        }
    }

    suspend fun setDefaultPersona(persona: MayaPersona) {
        dataStore.edit { it[KEY_PERSONA] = persona.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setResponseLength(length: ResponseLength) {
        dataStore.edit { it[KEY_RESPONSE_LENGTH] = length.name }
    }

    suspend fun setAutoReadAloud(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_READ] = enabled }
    }

    suspend fun setPreferredLanguage(language: AppLanguage) {
        dataStore.edit { it[KEY_LANGUAGE] = language.name }
    }

    suspend fun setBanglaAccent(accent: BanglaAccent) {
        dataStore.edit { it[KEY_BANGLA_ACCENT] = accent.name }
    }

    suspend fun setPreferredVoicePreset(voice: GeminiVoicePreset) {
        dataStore.edit { it[KEY_VOICE_PRESET] = voice.name }
    }

    suspend fun setGeminiApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        val cleaned = apiKey.trim()
        apiCredentialMutex.withLock {
            if (cleaned.isEmpty()) secureCredentialStore.remove(GEMINI_CREDENTIAL_ID)
            else secureCredentialStore.put(GEMINI_CREDENTIAL_ID, cleaned)
            dataStore.edit { it.remove(KEY_GEMINI_API_KEY) }
        }
    }

    suspend fun clearGeminiApiKey() = setGeminiApiKey("")

    suspend fun setTavilyApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        val cleaned = apiKey.trim()
        apiCredentialMutex.withLock {
            if (cleaned.isEmpty()) secureCredentialStore.remove(TAVILY_CREDENTIAL_ID)
            else secureCredentialStore.put(TAVILY_CREDENTIAL_ID, cleaned)
            dataStore.edit { it.remove(KEY_TAVILY_API_KEY) }
        }
    }

    suspend fun clearTavilyApiKey() = setTavilyApiKey("")

    private fun readSecureCredential(credentialId: String): String =
        runCatching { secureCredentialStore.get(credentialId) }.getOrNull().orEmpty()

    suspend fun setGeminiModelId(modelId: String) {
        dataStore.edit { it[KEY_GEMINI_MODEL] = modelId }
    }

    private companion object {
        const val GEMINI_CREDENTIAL_ID = "gemini_api_key"
        const val TAVILY_CREDENTIAL_ID = "tavily_api_key"

        val KEY_PERSONA = stringPreferencesKey("default_persona")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_RESPONSE_LENGTH = stringPreferencesKey("response_length")
        val KEY_AUTO_READ = booleanPreferencesKey("auto_read_aloud")
        val KEY_LANGUAGE = stringPreferencesKey("preferred_language")
        val KEY_BANGLA_ACCENT = stringPreferencesKey("bangla_accent")
        val KEY_VOICE_PRESET = stringPreferencesKey("preferred_voice_preset")
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model_id")
        val KEY_TAVILY_API_KEY = stringPreferencesKey("tavily_api_key")
    }
}
