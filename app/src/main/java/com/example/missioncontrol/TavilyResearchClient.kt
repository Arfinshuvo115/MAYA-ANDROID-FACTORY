package com.example.missioncontrol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

data class WebResearchResult(
    val title: String,
    val url: String,
    val content: String,
    val score: Double?
)

class TavilyResearchClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(apiKey: String, query: String): Result<List<WebResearchResult>> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val key = apiKey.trim()
            val cleanQuery = query.trim()
            if (key.isBlank()) return@withContext Result.failure(
                IllegalStateException("Tavily API key is not configured. Add it in Research settings.")
            )
            if (cleanQuery.isBlank() || cleanQuery.length > 1000) return@withContext Result.failure(
                IllegalArgumentException("Enter a search query (up to 1,000 characters).")
            )
            var connection: HttpURLConnection? = null
            try {
                val payload = buildJsonObject {
                    put("api_key", key)
                    put("query", cleanQuery)
                    put("search_depth", "basic")
                    put("topic", "general")
                    put("max_results", 5)
                    put("include_answer", true)
                    put("include_raw_content", false)
                }
                connection = (URL("https://api.tavily.com/search").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val message = runCatching {
                        json.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                    return@withContext Result.failure(
                        IllegalStateException(message ?: "Tavily Search failed with HTTP $code.")
                    )
                }
                val root = json.parseToJsonElement(body).jsonObject
                val results = root["results"]?.jsonArray.orEmpty().mapNotNull { element ->
                    val item = element.jsonObject
                    val title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val url = item["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val content = item["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val score = item["score"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    if (url.startsWith("https://") && title.isNotBlank()) {
                        WebResearchResult(title, url, content, score)
                    } else null
                }
                Result.success(results)
            } catch (failure: Exception) {
                Result.failure(IllegalStateException(failure.localizedMessage ?: "Tavily search request failed."))
            } finally {
                connection?.disconnect()
            }
        }
}
