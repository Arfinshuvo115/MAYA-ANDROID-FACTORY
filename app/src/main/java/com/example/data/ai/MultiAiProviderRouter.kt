package com.example.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ProviderTextResult(
    val providerId: String,
    val modelId: String,
    val text: String
)

/**
 * Direct runtime adapters for OpenAI, Anthropic, OpenRouter and Groq.
 * Credentials are loaded from the encrypted local vault and never included in errors.
 */
class MultiAiProviderRouter(
    private val credentialVault: ProviderCredentialVault
) {
    suspend fun generateText(
        providerId: String,
        modelId: String,
        prompt: String,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS
    ): Result<ProviderTextResult> = withContext(Dispatchers.IO) {
        val safeProvider = providerId.trim().lowercase()
        val safeModel = modelId.trim()
        val safePrompt = prompt.trim()
        val config = runCatching { ProviderRuntimePolicy.resolve(safeProvider, safeModel, timeoutMillis) }
            .getOrElse { return@withContext Result.failure(it) }
        if (safePrompt.isBlank()) return@withContext Result.failure(IllegalArgumentException("Enter a prompt."))
        val apiKey = runCatching { credentialVault.get(safeProvider) }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException("No saved encrypted key for this provider."))
        var connection: HttpURLConnection? = null
        try {
            val anthropic = config.usesAnthropicMessagesApi
            val payload = if (anthropic) {
                JSONObject()
                    .put("model", safeModel)
                    .put("max_tokens", 1024)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", safePrompt)))
            } else {
                JSONObject()
                    .put("model", safeModel)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", safePrompt)))
                    .put("max_tokens", 1024)
            }
            connection = (URL(config.endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = config.timeoutMillis
                readTimeout = config.timeoutMillis
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (anthropic) {
                    setRequestProperty("x-api-key", apiKey)
                    setRequestProperty("anthropic-version", ANTHROPIC_API_VERSION)
                } else {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    val error = JSONObject(body).optJSONObject("error")
                    error?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: JSONObject(body).optString("message").takeIf { it.isNotBlank() }
                }.getOrNull() ?: "Provider request failed."
                return@withContext Result.failure(IllegalStateException("$safeProvider returned HTTP $status: ${message.take(500)}"))
            }
            val text = if (anthropic) {
                val content = JSONObject(body).optJSONArray("content")
                (0 until (content?.length() ?: 0)).mapNotNull { index ->
                    content?.optJSONObject(index)?.takeIf { it.optString("type") == "text" }?.optString("text")
                }.joinToString("\n").trim()
            } else {
                JSONObject(body).optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim().orEmpty()
            }
            if (text.isBlank()) {
                Result.failure(IllegalStateException("$safeProvider returned an empty text response."))
            } else {
                Result.success(ProviderTextResult(safeProvider, safeModel, text))
            }
        } catch (failure: Exception) {
            Result.failure(IllegalStateException("$safeProvider request failed: ${failure.localizedMessage ?: "network error"}"))
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun verifyConnection(providerId: String, modelId: String): Result<String> {
        return generateText(providerId, modelId, "Reply with exactly: MAYA_PROVIDER_OK", 20_000)
            .map { "${it.providerId} connected · ${it.modelId} · response received" }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 45_000
        private const val ANTHROPIC_API_VERSION = "2023-06-01"
        val RUNTIME_PROVIDERS = ProviderRuntimePolicy.supportedProviderIds
    }
}
