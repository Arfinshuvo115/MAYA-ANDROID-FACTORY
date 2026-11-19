package com.example.data.ai

/**
 * Pure provider routing policy kept separate from Android networking for unit testing.
 * This chooses an endpoint only; it never performs fallback or retries against another provider.
 */
data class ProviderRuntimeConfig(
    val providerId: String,
    val endpoint: String,
    val usesAnthropicMessagesApi: Boolean,
    val timeoutMillis: Int
)

object ProviderRuntimePolicy {
    const val MIN_TIMEOUT_MILLIS = 5_000
    const val MAX_TIMEOUT_MILLIS = 60_000

    private val endpoints = mapOf(
        "openai" to "https://api.openai.com/v1/chat/completions",
        "openrouter" to "https://openrouter.ai/api/v1/chat/completions",
        "groq" to "https://api.groq.com/openai/v1/chat/completions",
        "anthropic" to "https://api.anthropic.com/v1/messages"
    )

    val supportedProviderIds: Set<String> get() = endpoints.keys

    fun resolve(providerId: String, modelId: String, timeoutMillis: Int): ProviderRuntimeConfig {
        val provider = providerId.trim().lowercase()
        require(provider in endpoints) { "Provider has no text-generation adapter." }
        require(modelId.isNotBlank()) { "Model ID is required." }
        return ProviderRuntimeConfig(
            providerId = provider,
            endpoint = endpoints.getValue(provider),
            usesAnthropicMessagesApi = provider == "anthropic",
            timeoutMillis = timeoutMillis.coerceIn(MIN_TIMEOUT_MILLIS, MAX_TIMEOUT_MILLIS)
        )
    }
}
