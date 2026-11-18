package com.example.ui.screens.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.BanglaAccent
import com.example.domain.model.GeminiVoicePreset
import com.example.domain.model.VoiceTone
import com.example.domain.model.VoiceUiState
import com.example.ui.components.ArcReactorCore
import com.example.ui.components.JarvisHudCard
import com.example.ui.components.JarvisSectionHeader
import com.example.ui.components.JarvisStatusPill
import com.example.ui.components.MayaFormattedResponse
import com.example.ui.components.ReactorMode
import com.example.util.SpeechToTextState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceScreen(
    viewModel: VoiceViewModel,
    modifier: Modifier = Modifier
) {
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val selectedVoice by viewModel.selectedVoice.collectAsStateWithLifecycle()
    val selectedTone by viewModel.selectedTone.collectAsStateWithLifecycle()
    val selectedAccent by viewModel.selectedAccent.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val speakingId by viewModel.speakingMessageId.collectAsStateWithLifecycle()
    val banglaReady by viewModel.banglaVoiceAvailable.collectAsStateWithLifecycle()
    val sttState by viewModel.speechToTextState.collectAsStateWithLifecycle()
    val lastSpokenQuery by viewModel.lastSpokenQuery.collectAsStateWithLifecycle()
    val voiceReplyText by viewModel.voiceReplyText.collectAsStateWithLifecycle()
    val isReplying by viewModel.isReplying.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInteraction()
        } else {
            viewModel.reportMicPermissionDenied()
        }
    }

    fun onTalkPressed() {
        if (sttState is SpeechToTextState.Listening) {
            viewModel.stopVoiceListening()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startVoiceInteraction()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val isSpeaking = speakingId == VoiceViewModel.VOICE_STUDIO_UTTERANCE_ID ||
        speakingId == VoiceViewModel.VOICE_INTERACTION_UTTERANCE_ID

    val sttRmsDb = (sttState as? SpeechToTextState.Listening)?.rmsDb ?: 0f
    val reactorMode = when {
        sttState is SpeechToTextState.Listening -> ReactorMode.LISTENING
        isReplying || uiState is VoiceUiState.Preparing -> ReactorMode.PROCESSING
        isSpeaking -> ReactorMode.SPEAKING
        else -> ReactorMode.STANDBY
    }

    val hudShape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp, topEnd = 4.dp, bottomStart = 4.dp)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                JarvisSectionHeader(
                    telemetryCode = "AUDIO.MATRIX // BN-BD & EN",
                    title = stringResource(R.string.voice_title),
                    subtitle = stringResource(R.string.voice_subtitle)
                )

                // Real-Time Speech-to-Text Voice Interaction HUD Core
                JarvisHudCard(
                    telemetryTag = "VOICE.LINK // REAL-TIME STT & BANGLA TTS",
                    accentColor = if (sttState is SpeechToTextState.Listening) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ArcReactorCore(
                            mode = reactorMode,
                            rmsDb = sttRmsDb,
                            size = 148.dp,
                            onClick = ::onTalkPressed,
                            contentDescriptionText = "Tap JARVIS Arc Reactor to trigger real-time voice command"
                        )

                        JarvisStatusPill(
                            text = when (val currentStt = sttState) {
                                is SpeechToTextState.Listening -> "RX.ACTIVE // ${currentStt.languageTag.uppercase()}"
                                else -> when (reactorMode) {
                                    ReactorMode.PROCESSING -> "CORE // ANALYZING SPEECH"
                                    ReactorMode.SPEAKING -> "TX.AUDIO // SPEAKING BANGLA"
                                    else -> "CORE.READY // TAP REACTOR OR BUTTON"
                                }
                            },
                            accentColor = if (reactorMode == ReactorMode.LISTENING) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    Text(
                        text = stringResource(R.string.stt_talk_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = ::onTalkPressed,
                            shape = hudShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (sttState is SpeechToTextState.Listening) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                contentColor = if (sttState is SpeechToTextState.Listening) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("voice_tab_talk_button")
                        ) {
                            Icon(
                                imageVector = if (sttState is SpeechToTextState.Listening) {
                                    Icons.Filled.Stop
                                } else {
                                    Icons.Filled.Mic
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (sttState is SpeechToTextState.Listening) {
                                        R.string.stt_mic_stop
                                    } else {
                                        R.string.stt_talk_button
                                    }
                                )
                            )
                        }

                        if (isSpeaking) {
                            FilledTonalButton(
                                onClick = viewModel::stopSpeaking,
                                shape = hudShape,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("voice_tab_stop_tts_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.voice_stop_button))
                            }
                        }
                    }

                    when (val currentStt = sttState) {
                        is SpeechToTextState.Listening -> {
                            LinearProgressIndicator(
                                progress = { (currentStt.rmsDb / 10f).coerceIn(0.08f, 1f) },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Text(
                                text = currentStt.partialTranscript.ifBlank {
                                    stringResource(R.string.stt_listening_hint, currentStt.languageTag)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        is SpeechToTextState.Error -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = currentStt.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = viewModel::clearSpeechError,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        SpeechToTextState.Idle -> Unit
                    }

                    if (!lastSpokenQuery.isNullOrBlank()) {
                        Surface(
                            shape = hudShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "OPR.VOICE // \"$lastSpokenQuery\"",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                if (isReplying && voiceReplyText.isNullOrBlank()) {
                                    Text(
                                        text = "J.A.R.V.I.S. // Preparing response…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (!voiceReplyText.isNullOrBlank()) {
                                    MayaFormattedResponse(rawText = voiceReplyText.orEmpty())
                                }
                            }
                        }
                    }
                }

                // Quick Bangla Phrases
                Text(
                    text = "PRESETS // " + stringResource(R.string.voice_presets_label).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.presets) { preset ->
                        SuggestionChip(
                            onClick = { viewModel.applyPreset(preset) },
                            shape = hudShape,
                            label = { Text(preset.label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("bangla_preset_${preset.tone.name.lowercase()}")
                        )
                    }
                }

                // Bangla Text-to-Speech HUD Module
                JarvisHudCard(
                    telemetryTag = "SYNTH.MODULE // BANGLA TTS"
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = viewModel::updateInputText,
                        label = { Text(stringResource(R.string.voice_input_label)) },
                        placeholder = { Text(stringResource(R.string.voice_input_hint)) },
                        minLines = 3,
                        maxLines = 6,
                        shape = hudShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_bangla_input")
                    )

                    Text(
                        text = stringResource(R.string.voice_select_accent),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BanglaAccent.entries.forEach { accent ->
                            FilterChip(
                                selected = selectedAccent == accent,
                                onClick = { viewModel.selectAccent(accent) },
                                shape = hudShape,
                                label = { Text(accent.label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("accent_chip_${accent.name.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.voice_select_persona),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GeminiVoicePreset.entries.forEach { voice ->
                            FilterChip(
                                selected = selectedVoice == voice,
                                onClick = { viewModel.selectVoice(voice) },
                                shape = hudShape,
                                label = { Text("${voice.voiceName} · ${voice.trait}") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("gemini_voice_chip_${voice.voiceName.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.voice_select_tone),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VoiceTone.entries.forEach { tone ->
                            FilterChip(
                                selected = selectedTone == tone,
                                onClick = { viewModel.selectTone(tone) },
                                shape = hudShape,
                                label = { Text(tone.label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("tone_chip_${tone.name.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = stringResource(
                            if (banglaReady) R.string.voice_device_tts_ready
                            else R.string.voice_device_tts_missing
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (banglaReady) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )

                    Button(
                        onClick = viewModel::speakOrStop,
                        enabled = uiState !is VoiceUiState.Preparing,
                        shape = hudShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSpeaking) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("voice_speak_button")
                    ) {
                        when {
                            uiState is VoiceUiState.Preparing -> {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Preparing Bangla Voice…")
                            }
                            isSpeaking -> {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.voice_stop_button))
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.voice_speak_button))
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = isSpeaking || uiState is VoiceUiState.Ready || uiState is VoiceUiState.Error) {
                    when (val state = uiState) {
                        is VoiceUiState.Error -> {
                            Surface(
                                shape = hudShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                        is VoiceUiState.Ready -> {
                            val result = state.result
                            JarvisHudCard(
                                telemetryTag = "TELEMETRY // ACTIVE STREAM",
                                modifier = Modifier.testTag("voice_synthesis_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${result.voice.voiceName} (${result.voice.genderLabel}) · ${result.accent.bcp47}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (isSpeaking) {
                                    VoiceEqualizerRow()
                                }

                                Text(
                                    text = "Directive: ${result.tone.promptDirective}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = result.engineLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = result.voiceNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceEqualizerRow() {
    val transition = rememberInfiniteTransition(label = "voice_eq")
    val bars = listOf(0.35f, 0.9f, 0.55f, 1f, 0.45f, 0.8f, 0.4f, 0.72f, 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEachIndexed { index, target ->
            val factor by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = target,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 360 + index * 60),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height((26 * factor).dp)
                    .clip(CutCornerShape(2.dp))
                    .background(
                        if (index % 3 == 0) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
            )
        }
    }
}
