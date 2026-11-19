package com.example.util

import android.content.Context
import android.media.MediaPlayer
import java.io.File
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.domain.model.AppLanguage
import com.example.domain.model.BanglaAccent
import com.example.domain.model.GeminiVoicePreset
import com.example.domain.model.VoiceTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class SpeechHelper(context: Context) {

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private val _banglaVoiceAvailable = MutableStateFlow(false)
    val banglaVoiceAvailable: StateFlow<Boolean> = _banglaVoiceAvailable.asStateFlow()

    private var isReady = false
    private var tts: TextToSpeech? = null
    private var audioPlayer: MediaPlayer? = null
    private var activeAudioFile: File? = null
    private val appContext = context.applicationContext

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                _banglaVoiceAvailable.value = checkBanglaAvailability()
            }
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    _speakingMessageId.value = null
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _speakingMessageId.value = null
                }
            })
        }
    }

    private fun checkBanglaAvailability(): Boolean {
        val engine = tts ?: return false
        val bd = engine.isLanguageAvailable(Locale("bn", "BD"))
        val ind = engine.isLanguageAvailable(Locale("bn", "IN"))
        val generic = engine.isLanguageAvailable(Locale("bn"))
        return bd >= TextToSpeech.LANG_AVAILABLE ||
            ind >= TextToSpeech.LANG_AVAILABLE ||
            generic >= TextToSpeech.LANG_AVAILABLE
    }

    fun toggleSpeak(
        messageId: String,
        text: String,
        language: AppLanguage = AppLanguage.ENGLISH,
        accent: BanglaAccent = BanglaAccent.BN_BD,
        voicePreset: GeminiVoicePreset = GeminiVoicePreset.SULAFAT,
        tone: VoiceTone = VoiceTone.WARM
    ) {
        if (_speakingMessageId.value == messageId) {
            stop()
            return
        }
        if (!isReady) return

        val containsBengali = text.any { it in '\u0980'..'\u09FF' }
        val useBangla = containsBengali || language == AppLanguage.BANGLA

        val targetLocale = if (useBangla) {
            val preferred = Locale(accent.lang, accent.country)
            val fallback = if (accent == BanglaAccent.BN_BD) Locale("bn", "IN") else Locale("bn", "BD")
            when {
                (tts?.isLanguageAvailable(preferred) ?: -1) >= TextToSpeech.LANG_AVAILABLE -> preferred
                (tts?.isLanguageAvailable(fallback) ?: -1) >= TextToSpeech.LANG_AVAILABLE -> fallback
                (tts?.isLanguageAvailable(Locale("bn")) ?: -1) >= TextToSpeech.LANG_AVAILABLE -> Locale("bn")
                else -> preferred
            }
        } else {
            Locale.US
        }

        tts?.language = targetLocale

        val toneRateMultiplier = when (tone) {
            VoiceTone.WARM -> 1.0f
            VoiceTone.CHEERFUL -> 1.06f
            VoiceTone.CALM -> 0.88f
            VoiceTone.INFORMATIVE -> 1.02f
        }
        tts?.setPitch(voicePreset.pitch)
        tts?.setSpeechRate((voicePreset.rate * toneRateMultiplier).coerceIn(0.6f, 1.5f))

        val cleanText = text
            .replace(Regex("<!--\\s*thought[^>]*-->.*?<!--\\s*/thought\\s*-->", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("[#*`>]+"), " ")
            .replace("---", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleanText.isBlank()) return

        _speakingMessageId.value = messageId
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }


    fun playGeminiAudio(audioBytes: ByteArray, messageId: String) {
        stop()
        val audioFile = File(appContext.cacheDir, "maya_tts_${UUID.randomUUID()}.wav")
        activeAudioFile = audioFile
        runCatching {
            require(audioBytes.isNotEmpty()) { "Gemini returned an empty audio payload." }
            audioFile.writeBytes(audioBytes)
            val player = MediaPlayer()
            audioPlayer = player
            player.setDataSource(audioFile.absolutePath)
            player.setOnPreparedListener {
                _speakingMessageId.value = messageId
                it.start()
            }
            player.setOnCompletionListener {
                _speakingMessageId.value = null
                it.release()
                if (audioPlayer === it) audioPlayer = null
                deleteAudioFile(audioFile)
            }
            player.setOnErrorListener { mp, _, _ ->
                _speakingMessageId.value = null
                mp.release()
                if (audioPlayer === mp) audioPlayer = null
                deleteAudioFile(audioFile)
                true
            }
            player.prepareAsync()
        }.onFailure {
            audioPlayer?.release()
            audioPlayer = null
            deleteAudioFile(audioFile)
            _speakingMessageId.value = null
        }
    }

    private fun deleteAudioFile(file: File) {
        if (activeAudioFile == file) activeAudioFile = null
        runCatching { file.delete() }
    }

    fun stop() {
        tts?.stop()
        val player = audioPlayer
        audioPlayer = null
        runCatching { player?.stop() }
        player?.release()
        activeAudioFile?.let(::deleteAudioFile)
        _speakingMessageId.value = null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
