package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SpeechToTextState {
    data object Idle : SpeechToTextState
    data class Listening(
        val partialTranscript: String = "",
        val rmsDb: Float = 0f,
        val languageTag: String = "bn-BD"
    ) : SpeechToTextState
    data class Error(val message: String) : SpeechToTextState
}

class SpeechRecognizerHelper(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var finalCallback: ((String) -> Unit)? = null
    private var activeLanguageTag: String = "bn-BD"

    private val _state = MutableStateFlow<SpeechToTextState>(SpeechToTextState.Idle)
    val state: StateFlow<SpeechToTextState> = _state.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening(
        languageTag: String,
        onFinalTranscript: (String) -> Unit
    ) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _state.value = SpeechToTextState.Error(
                    "Built-in Android speech recognition is unavailable on this device."
                )
                return@post
            }

            activeLanguageTag = languageTag
            finalCallback = onFinalTranscript

            ensureRecognizer()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            _state.value = SpeechToTextState.Listening(
                partialTranscript = "",
                rmsDb = 0f,
                languageTag = languageTag
            )

            try {
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                _state.value = SpeechToTextState.Error(
                    e.localizedMessage ?: "Unable to start speech recognizer."
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                recognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "stopListening ignored", e)
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            finalCallback = null
            try {
                recognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "cancel ignored", e)
            }
            _state.value = SpeechToTextState.Idle
        }
    }

    fun clearError() {
        if (_state.value is SpeechToTextState.Error) {
            _state.value = SpeechToTextState.Idle
        }
    }

    fun reportPermissionDenied() {
        _state.value = SpeechToTextState.Error(
            "Microphone permission denied. Grant audio permission to use real-time voice input."
        )
    }

    fun destroy() {
        mainHandler.post {
            finalCallback = null
            try {
                recognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "destroy ignored", e)
            }
            recognizer = null
            _state.value = SpeechToTextState.Idle
        }
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = SpeechToTextState.Listening(
                        partialTranscript = "",
                        rmsDb = 0f,
                        languageTag = activeLanguageTag
                    )
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    val current = _state.value
                    if (current is SpeechToTextState.Listening) {
                        _state.value = current.copy(rmsDb = rmsdB.coerceIn(0f, 12f))
                    }
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                        SpeechRecognizer.ERROR_CLIENT -> "Speech client error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission is required."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Speech service network unavailable."
                        SpeechRecognizer.ERROR_NO_MATCH ->
                            "Didn't catch any speech. Please try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Speech recognizer is busy. Please wait a moment."
                        SpeechRecognizer.ERROR_SERVER ->
                            "Speech recognition service error."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "No speech detected. Tap mic to try again."
                        else -> "Speech recognition error ($error)."
                    }
                    Log.w(TAG, "SpeechRecognizer error code=$error ($message)")
                    finalCallback = null
                    _state.value = SpeechToTextState.Error(message)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull()?.trim().orEmpty()
                    val callback = finalCallback
                    finalCallback = null
                    _state.value = SpeechToTextState.Idle
                    if (transcript.isNotBlank()) {
                        callback?.invoke(transcript)
                    } else {
                        _state.value = SpeechToTextState.Error("No speech recognized. Please try again.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim().orEmpty()
                    val current = _state.value
                    if (current is SpeechToTextState.Listening && partial.isNotBlank()) {
                        _state.value = current.copy(partialTranscript = partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private companion object {
        const val TAG = "SpeechRecognizerHelper"
    }
}
