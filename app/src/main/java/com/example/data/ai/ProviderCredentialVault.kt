package com.example.data.ai

import android.content.Context
import com.example.data.local.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val store = SecureCredentialStore(context.applicationContext)

    suspend fun save(providerId: String, apiKey: String) = withContext(Dispatchers.IO) {
        require(providerId in SUPPORTED_PROVIDER_IDS) { "Unsupported AI provider." }
        val cleaned = apiKey.trim()
        require(cleaned.isNotEmpty()) { "Enter an API key before saving." }
        store.put(credentialId(providerId), cleaned)
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
            AiProviderDescriptor("openai", "OpenAI", "OpenAI API credential storage.", "Credential vault only · runtime adapter not connected"),
            AiProviderDescriptor("anthropic", "Anthropic", "Anthropic API credential storage.", "Credential vault only · runtime adapter not connected"),
            AiProviderDescriptor("openrouter", "OpenRouter", "OpenRouter API credential storage.", "Credential vault only · runtime adapter not connected"),
            AiProviderDescriptor("groq", "Groq", "Groq API credential storage.", "Credential vault only · runtime adapter not connected"),
            AiProviderDescriptor("tavily", "Tavily Search", "Live research search credential storage.", "Existing research client uses its own configured key")
        )
        private val SUPPORTED_PROVIDER_IDS = providers.map { it.id }.toSet()
    }
}
