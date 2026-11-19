package com.example.data.ai

import android.content.Context
import com.example.data.local.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ActiveAiProvider(val providerId: String, val modelId: String)

data class AiProviderDescriptor(
    val id: String,
    val displayName: String,
    val description: String,
    val integrationStatus: String
)

/**
 * Stores optional provider credentials encrypted with Android Keystore.
 * Presence in this vault does not mean a provider adapter is implemented or active.
 */
class ProviderCredentialVault(context: Context) {
    private val appContext = context.applicationContext
    private val store = SecureCredentialStore(appContext)
    private val routingPreferences = appContext.getSharedPreferences("maya_active_ai_provider", Context.MODE_PRIVATE)

    suspend fun save(providerId: String, apiKey: String) = withContext(Dispatchers.IO) {
        require(providerId in SUPPORTED_PROVIDER_IDS) { "Unsupported AI provider." }
        val cleaned = apiKey.trim()
        require(cleaned.isNotEmpty()) { "Enter an API key before saving." }
        store.put(credentialId(providerId), cleaned)
    }

    suspend fun get(providerId: String): String? = withContext(Dispatchers.IO) {
        require(providerId in SUPPORTED_PROVIDER_IDS) { "Unsupported AI provider." }
        store.get(credentialId(providerId))?.takeIf { it.isNotBlank() }
    }

    suspend fun setActiveProvider(providerId: String, modelId: String) = withContext(Dispatchers.IO) {
        require(providerId in RUNTIME_PROVIDER_IDS) { "This provider cannot be selected for AI text routing." }
        require(modelId.isNotBlank()) { "Model ID is required." }
        require(!store.get(credentialId(providerId)).isNullOrBlank()) { "Save this provider's API key before selecting it." }
        check(routingPreferences.edit()
            .putString(KEY_ACTIVE_PROVIDER, providerId)
            .putString(KEY_ACTIVE_MODEL, modelId.trim().take(160))
            .commit()) { "Could not save active provider selection." }
    }

    suspend fun getActiveProvider(): ActiveAiProvider? = withContext(Dispatchers.IO) {
        val providerId = routingPreferences.getString(KEY_ACTIVE_PROVIDER, null) ?: return@withContext null
        val modelId = routingPreferences.getString(KEY_ACTIVE_MODEL, null).orEmpty()
        if (providerId !in RUNTIME_PROVIDER_IDS || modelId.isBlank()) return@withContext null
        if (runCatching { store.get(credentialId(providerId)) }.getOrNull().isNullOrBlank()) return@withContext null
        ActiveAiProvider(providerId, modelId)
    }

    suspend fun clearActiveProvider() = withContext(Dispatchers.IO) {
        check(routingPreferences.edit().remove(KEY_ACTIVE_PROVIDER).remove(KEY_ACTIVE_MODEL).commit()) {
            "Could not clear active provider selection."
        }
    }

    suspend fun hasKey(providerId: String): Boolean = withContext(Dispatchers.IO) {
        require(providerId in SUPPORTED_PROVIDER_IDS) { "Unsupported AI provider." }
        !store.get(credentialId(providerId)).isNullOrBlank()
    }

    suspend fun remove(providerId: String) = withContext(Dispatchers.IO) {
        require(providerId in SUPPORTED_PROVIDER_IDS) { "Unsupported AI provider." }
        store.remove(credentialId(providerId))
    }

    private fun credentialId(providerId: String) = "provider_$providerId"

    companion object {
        val providers = listOf(
            AiProviderDescriptor("openai", "OpenAI", "OpenAI Chat Completions API.", "Runtime adapter available"),
            AiProviderDescriptor("anthropic", "Anthropic", "Anthropic Messages API.", "Runtime adapter available"),
            AiProviderDescriptor("openrouter", "OpenRouter", "OpenAI-compatible routed model API.", "Runtime adapter available"),
            AiProviderDescriptor("groq", "Groq", "Groq OpenAI-compatible API.", "Runtime adapter available"),
            AiProviderDescriptor("tavily", "Tavily Search", "Live research search credential storage.", "Existing research client uses its own configured key")
        )
        private val SUPPORTED_PROVIDER_IDS = providers.map { it.id }.toSet()
        private val RUNTIME_PROVIDER_IDS = setOf("openai", "anthropic", "openrouter", "groq")
        private const val KEY_ACTIVE_PROVIDER = "active_provider_id"
        private const val KEY_ACTIVE_MODEL = "active_model_id"
    }
}
