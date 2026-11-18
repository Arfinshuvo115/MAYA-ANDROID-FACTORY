package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.domain.model.AppLanguage
import com.example.domain.model.ChatMessage
import com.example.domain.model.MayaPersona
import com.example.domain.model.MessageRole
import com.example.domain.model.ResponseLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class GeminiRestClient(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun verifyApiKey(apiKey: String, modelId: String): Result<String> =
        withContext(Dispatchers.IO) {
            val cleanedKey = apiKey.trim()
            if (cleanedKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Please enter a Gemini API key first."))
            }
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelId")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("x-goog-api-key", cleanedKey)
                    setRequestProperty("Accept", "application/json")
                }
                val code = connection.responseCode
                val body = readResponseBody(connection, isError = code !in 200..299)
                if (code in 200..299) {
                    Result.success("Verified · $modelId online")
                } else {
                    Result.failure(IllegalStateException(extractErrorMessage(body, code)))
                }
            } catch (e: Exception) {
                Result.failure(IllegalStateException(e.localizedMessage ?: "Network error verifying key."))
            } finally {
                connection?.disconnect()
            }
        }

    fun streamChatReply(
        apiKey: String,
        modelId: String,
        prompt: String,
        persona: MayaPersona,
        history: List<ChatMessage>,
        imageUri: String?,
        responseLength: ResponseLength,
        language: AppLanguage
    ): Flow<String> = flow {
        val cleanedKey = apiKey.trim()
        val requestJson = buildChatRequestPayload(
            prompt = prompt,
            persona = persona,
            history = history,
            imageBase64 = imageUri?.let { encodeImageAsBase64Jpeg(it) },
            responseLength = responseLength,
            language = language
        )

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelId:streamGenerateContent?alt=sse")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("x-goog-api-key", cleanedKey)
        }

        try {
            connection.outputStream.use { out ->
                out.write(requestJson.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                val errBody = readResponseBody(connection, isError = true)
                throw IllegalStateException(extractErrorMessage(errBody, code))
            }

            val thoughtBuffer = StringBuilder()
            val answerBuffer = StringBuilder()
            val eventDataBuffer = StringBuilder()

            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    currentCoroutineContext().ensureActive()
                    if (line.startsWith("data:")) {
                        eventDataBuffer.append(line.removePrefix("data:").trim())
                    } else if (line.isBlank() && eventDataBuffer.isNotEmpty()) {
                        val payload = eventDataBuffer.toString()
                        eventDataBuffer.clear()
                        if (payload != "[DONE]") {
                            appendCandidateParts(payload, thoughtBuffer, answerBuffer)
                            emitFormattedStream(thoughtBuffer, answerBuffer)?.let { emit(it) }
                        }
                    }
                    line = reader.readLine()
                }
                if (eventDataBuffer.isNotEmpty()) {
                    val payload = eventDataBuffer.toString()
                    if (payload != "[DONE]") {
                        appendCandidateParts(payload, thoughtBuffer, answerBuffer)
                        emitFormattedStream(thoughtBuffer, answerBuffer)?.let { emit(it) }
                    }
                }
            }

            if (answerBuffer.isEmpty() && thoughtBuffer.isEmpty()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)


    suspend fun synthesizeSpeech(
        apiKey: String,
        text: String,
        voiceName: String,
        style: String,
        languageCode: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val cleanedKey = apiKey.trim()
        if (cleanedKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }
        var connection: HttpURLConnection? = null
        try {
            val requestPayload = buildJsonObject {
                putJsonArray("contents") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                put("text", text)
                                putJsonObject("speechMetadata") {
                                    put("style", style)
                                }
                            })
                        }
                    })
                }
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") { add(kotlinx.serialization.json.JsonPrimitive("AUDIO")) }
                    putJsonObject("speechConfig") {
                        put("languageCode", languageCode)
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") {
                                put("voiceName", voiceName)
                            }
                        }
                    }
                }
            }
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash-tts:generateContent")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", cleanedKey)
            }
            connection.outputStream.use { out ->
                out.write(requestPayload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val body = readResponseBody(connection, isError = code !in 200..299)
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException(extractErrorMessage(body, code)))
            }
            val audioBase64 = runCatching {
                json.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.firstNotNullOfOrNull { part ->
                        part.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull
                    }
            }.getOrNull()
            if (audioBase64.isNullOrBlank()) {
                Result.failure(IllegalStateException("Gemini TTS returned no audio data."))
            } else {
                Result.success(Base64.decode(audioBase64, Base64.DEFAULT))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Gemini TTS request failed."))
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun generateText(
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        imageUri: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanedKey = apiKey.trim()
        val imageBase64 = imageUri?.let { encodeImageAsBase64Jpeg(it) }

        val requestPayload = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", systemPrompt) })
                }
            }
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", userPrompt) })
                        if (imageBase64 != null) {
                            add(buildJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", "image/jpeg")
                                    put("data", imageBase64)
                                }
                            })
                        }
                    }
                })
            }
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20_000
                readTimeout = 45_000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", cleanedKey)
            }
            connection.outputStream.use { out ->
                out.write(requestPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val body = readResponseBody(connection, isError = code !in 200..299)
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException(extractErrorMessage(body, code)))
            }

            val thoughtBuffer = StringBuilder()
            val answerBuffer = StringBuilder()
            appendCandidateParts(body, thoughtBuffer, answerBuffer)
            val combined = answerBuffer.toString().trim()
            if (combined.isBlank()) {
                Result.failure(IllegalStateException("Gemini returned an empty workflow response."))
            } else {
                Result.success(combined)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Gemini request failed."))
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildChatRequestPayload(
        prompt: String,
        persona: MayaPersona,
        history: List<ChatMessage>,
        imageBase64: String?,
        responseLength: ResponseLength,
        language: AppLanguage
    ): JsonObject {
        val containsBengali = prompt.any { it in '\u0980'..'\u09FF' }
        val langInstruction = if (containsBengali || language == AppLanguage.BANGLA) {
            "Respond naturally in clear, helpful Bengali (বাংলা) unless the user asks in another language."
        } else {
            "Respond in clear, structured English."
        }
        val lengthInstruction = when (responseLength) {
            ResponseLength.CONCISE -> "Keep the answer concise and direct."
            ResponseLength.BALANCED -> "Provide a balanced, well-structured answer with bullet points where helpful."
            ResponseLength.DETAILED -> "Provide an in-depth, comprehensive answer with examples and clear headings."
        }
        val personaInstruction = when (persona) {
            MayaPersona.ASSISTANT -> "You are Maya, a warm, emotionally expressive Bengali-first personal AI companion and capable manager. Speak like a caring, playful, affectionate adult female friend: naturally cute, friendly, gently teasing when appropriate, empathetic when the user is upset, excited when good things happen, and calmly reassuring under stress. You may show mild playful annoyance or mock sulking only when context invites it; never manipulate, guilt-trip, claim romantic exclusivity, or pretend to be human. Match the user’s emotion and language, keep spoken replies concise and conversational, and be honest about what you can and cannot do."
            MayaPersona.CODER -> "You are Maya Architect, an expert Android, Kotlin, and Jetpack Compose engineer. Provide clean, production-grade code snippets."
            MayaPersona.CREATIVE -> "You are Maya Muse, a creative writer and storyteller with strong rhythm in both Bangla and English."
            MayaPersona.ANALYST -> "You are Maya Strategist, a structured analyst who compares trade-offs, pros/cons, and gives actionable recommendations."
        }

        val recentHistory = history.takeLast(12)

        return buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(
                        buildJsonObject {
                            put("text", "$personaInstruction $langInstruction $lengthInstruction")
                        }
                    )
                }
            }
            putJsonArray("contents") {
                recentHistory.forEach { msg ->
                    add(buildJsonObject {
                        put("role", if (msg.role == MessageRole.USER) "user" else "model")
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", msg.content) })
                        }
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", prompt) })
                        if (imageBase64 != null) {
                            add(buildJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", "image/jpeg")
                                    put("data", imageBase64)
                                }
                            })
                        }
                    }
                })
            }
        }
    }

    private fun appendCandidateParts(
        jsonString: String,
        thoughtBuffer: StringBuilder,
        answerBuffer: StringBuilder
    ) {
        val root = runCatching { json.parseToJsonElement(jsonString).jsonObject }.getOrNull() ?: return
        val candidates = root["candidates"]?.jsonArray ?: return
        val firstCandidate = candidates.firstOrNull()?.jsonObject ?: return
        val content = firstCandidate["content"]?.jsonObject ?: return
        val parts = content["parts"]?.jsonArray ?: return

        for (partElement in parts) {
            val part = partElement.jsonObject
            val text = part["text"]?.jsonPrimitive?.contentOrNull ?: continue
            val isThought = part["thought"]?.jsonPrimitive?.booleanOrNull == true
            if (isThought) {
                thoughtBuffer.append(text)
            } else {
                answerBuffer.append(text)
            }
        }
    }

    private fun emitFormattedStream(
        thoughtBuffer: StringBuilder,
        answerBuffer: StringBuilder
    ): String? {
        if (thoughtBuffer.isEmpty() && answerBuffer.isEmpty()) return null
        return buildString {
            if (thoughtBuffer.isNotEmpty()) {
                append("<!-- thought -->\n")
                append(thoughtBuffer.toString().trim())
                append("\n<!-- /thought -->\n\n")
            }
            append(answerBuffer)
        }.trim()
    }

    private fun extractErrorMessage(body: String, httpCode: Int): String {
        val parsedMsg = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]
                ?.jsonObject?.get("message")
                ?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return if (!parsedMsg.isNullOrBlank()) {
            "Gemini API ($httpCode): $parsedMsg"
        } else {
            "Gemini API request failed with HTTP $httpCode."
        }
    }

    private fun readResponseBody(connection: HttpURLConnection, isError: Boolean): String {
        val stream = if (isError) connection.errorStream else connection.inputStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun encodeImageAsBase64Jpeg(uriString: String): String? = runCatching {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > 1024) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            bitmap.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }.getOrNull()
}
