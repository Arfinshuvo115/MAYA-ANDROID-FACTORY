package com.example.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRuntimePolicyTest {
    @Test
    fun resolvesOpenAiCompatibleProvidersToTheirOwnEndpoints() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            ProviderRuntimePolicy.resolve("openai", "gpt-4o-mini", 30_000).endpoint
        )
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            ProviderRuntimePolicy.resolve("openrouter", "openai/gpt-4o-mini", 30_000).endpoint
        )
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            ProviderRuntimePolicy.resolve("groq", "llama-3.3-70b-versatile", 30_000).endpoint
        )
    }

    @Test
    fun resolvesAnthropicMessagesApiSeparately() {
        val config = ProviderRuntimePolicy.resolve("anthropic", "claude-model", 30_000)
        assertEquals("https://api.anthropic.com/v1/messages", config.endpoint)
        assertTrue(config.usesAnthropicMessagesApi)
    }

    @Test
    fun clampsNetworkTimeoutToSafeBounds() {
        assertEquals(
            ProviderRuntimePolicy.MIN_TIMEOUT_MILLIS,
            ProviderRuntimePolicy.resolve("openai", "model", 1).timeoutMillis
        )
        assertEquals(
            ProviderRuntimePolicy.MAX_TIMEOUT_MILLIS,
            ProviderRuntimePolicy.resolve("groq", "model", 120_000).timeoutMillis
        )
    }

    @Test
    fun rejectsSearchOnlyProviderAndBlankModel() {
        assertFalse("tavily" in ProviderRuntimePolicy.supportedProviderIds)
        runCatching { ProviderRuntimePolicy.resolve("tavily", "search", 10_000) }
            .onSuccess { throw AssertionError("Tavily must not be routed as a chat model.") }
        runCatching { ProviderRuntimePolicy.resolve("openai", " ", 10_000) }
            .onSuccess { throw AssertionError("Blank model IDs must be rejected.") }
    }
}
