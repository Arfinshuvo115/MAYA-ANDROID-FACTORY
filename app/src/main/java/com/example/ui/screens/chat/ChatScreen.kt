package com.example.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatStreamState
import com.example.domain.model.MayaPersona
import com.example.domain.model.QuickStarter
import com.example.ui.components.ArcReactorCore
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.JarvisStatusPill
import com.example.ui.components.MayaPersonaChipRow
import com.example.ui.components.ReactorMode
import com.example.ui.components.StreamingBubble
import com.example.ui.components.icon
import com.example.util.SpeechToTextState

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val selectedPersona by viewModel.selectedPersona.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val attachedImageUri by viewModel.attachedImageUri.collectAsStateWithLifecycle()
    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val speakingMessageId by viewModel.speakingMessageId.collectAsStateWithLifecycle()
    val sttState by viewModel.speechToTextState.collectAsStateWithLifecycle()

    val clipboard = LocalClipboardManager.current
    val chatCopiedMessage = stringResource(R.string.chat_copied)
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachImage(uri.toString())
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceListening()
        } else {
            viewModel.reportMicPermissionDenied()
        }
    }

    fun onMicPressed() {
        if (sttState is SpeechToTextState.Listening) {
            viewModel.stopVoiceListening()
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.startVoiceListening()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val isGenerating = streamState is ChatStreamState.Thinking || streamState is ChatStreamState.Streaming
    val sttRmsDb = (sttState as? SpeechToTextState.Listening)?.rmsDb ?: 0f
    val reactorMode = when {
        sttState is SpeechToTextState.Listening -> ReactorMode.LISTENING
        isGenerating -> ReactorMode.PROCESSING
        speakingMessageId != null -> ReactorMode.SPEAKING
        else -> ReactorMode.STANDBY
    }

    LaunchedEffect(messages.size, streamState) {
        val totalItems = messages.size + if (isGenerating) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        ChatHeaderBar(
            selectedPersona = selectedPersona,
            selectedLanguage = selectedLanguage,
            reactorMode = reactorMode,
            sttRmsDb = sttRmsDb,
            hasMessages = messages.isNotEmpty(),
            onReactorClick = ::onMicPressed,
            onToggleLanguage = viewModel::toggleLanguage,
            onNewChat = viewModel::startNewChat
        )

        MayaPersonaChipRow(
            selectedPersona = selectedPersona,
            onSelectPersona = viewModel::selectPersona,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && !isGenerating) {
                ChatWelcomeState(
                    starters = viewModel.quickStarters,
                    reactorMode = reactorMode,
                    sttRmsDb = sttRmsDb,
                    onReactorClick = ::onMicPressed,
                    onSelectStarter = { starter ->
                        viewModel.sendMessage(
                            overridePrompt = starter.prompt,
                            overridePersona = starter.persona,
                            overrideLanguage = starter.language
                        )
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { msg: ChatMessage ->
                        ChatMessageBubble(
                            message = msg,
                            isSpeaking = speakingMessageId == msg.id,
                            onCopy = { text ->
                                clipboard.setText(AnnotatedString(text))
                                onShowSnackbar(chatCopiedMessage)
                            },
                            onToggleSpeak = viewModel::toggleSpeak
                        )
                    }

                    if (streamState is ChatStreamState.Thinking) {
                        item(key = "streaming_thinking") {
                            StreamingBubble(
                                partialText = null,
                                persona = selectedPersona
                            )
                        }
                    } else if (streamState is ChatStreamState.Streaming) {
                        item(key = "streaming_text") {
                            StreamingBubble(
                                partialText = (streamState as ChatStreamState.Streaming).partialText,
                                persona = selectedPersona
                            )
                        }
                    } else if (streamState is ChatStreamState.Error) {
                        item(key = "stream_error") {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = CutCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = (streamState as ChatStreamState.Error).message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Real-Time Speech-to-Text Overlay Banner
        AnimatedVisibility(visible = sttState !is SpeechToTextState.Idle) {
            when (val currentStt = sttState) {
                is SpeechToTextState.Listening -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                        shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp, topEnd = 4.dp, bottomStart = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("stt_listening_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "AUDIO.RX // " + stringResource(R.string.stt_listening_hint, currentStt.languageTag),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                IconButton(
                                    onClick = viewModel::cancelVoiceListening,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("stt_cancel_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.action_cancel),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { (currentStt.rmsDb / 10f).coerceIn(0.08f, 1f) },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            if (currentStt.partialTranscript.isNotBlank()) {
                                Text(
                                    text = currentStt.partialTranscript,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                is SpeechToTextState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CutCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("stt_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentStt.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = viewModel::clearSpeechError,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                SpeechToTextState.Idle -> Unit
            }
        }

        ChatComposerBar(
            composerText = composerText,
            selectedLanguage = selectedLanguage,
            attachedImageUri = attachedImageUri,
            isGenerating = isGenerating,
            isListening = sttState is SpeechToTextState.Listening,
            onTextChange = viewModel::updateComposerText,
            onPickImage = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveImage = { viewModel.attachImage(null) },
            onToggleMic = ::onMicPressed,
            onSend = { viewModel.sendMessage() },
            onStop = viewModel::stopGeneration
        )
    }
}

@Composable
private fun ChatHeaderBar(
    selectedPersona: MayaPersona,
    selectedLanguage: AppLanguage,
    reactorMode: ReactorMode,
    sttRmsDb: Float,
    hasMessages: Boolean,
    onReactorClick: () -> Unit,
    onToggleLanguage: () -> Unit,
    onNewChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            ArcReactorCore(
                mode = reactorMode,
                rmsDb = sttRmsDb,
                size = 44.dp,
                onClick = onReactorClick,
                contentDescriptionText = "Tap Arc Reactor for voice command"
            )
            Column {
                Text(
                    text = "MAYA // J.A.R.V.I.S.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "SYS.ONLINE · ${selectedPersona.displayName.uppercase()} · ${selectedPersona.badge.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(
                onClick = onToggleLanguage,
                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp, topEnd = 3.dp, bottomStart = 3.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("chat_language_toggle")
            ) {
                Icon(
                    imageVector = Icons.Filled.Translate,
                    contentDescription = "Switch language",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = selectedLanguage.shortTag,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            AnimatedVisibility(visible = hasMessages) {
                IconButton(
                    onClick = onNewChat,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddComment,
                        contentDescription = stringResource(R.string.chat_new_thread),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatWelcomeState(
    starters: List<QuickStarter>,
    reactorMode: ReactorMode,
    sttRmsDb: Float,
    onReactorClick: () -> Unit,
    onSelectStarter: (QuickStarter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .widthIn(max = 640.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp, topEnd = 6.dp, bottomStart = 6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(216.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_jarvis_hud),
                        contentDescription = "Iron Man JARVIS holographic tactical HUD",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x66030811),
                                        Color(0xE8030811)
                                    )
                                )
                            )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        ArcReactorCore(
                            mode = reactorMode,
                            rmsDb = sttRmsDb,
                            size = 96.dp,
                            onClick = onReactorClick,
                            contentDescriptionText = "Tap Arc Reactor core to speak with Maya",
                            modifier = Modifier.testTag("hero_arc_reactor_button")
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            JarvisStatusPill(
                                text = when (reactorMode) {
                                    ReactorMode.LISTENING -> "VOICE.LINK // LISTENING"
                                    ReactorMode.PROCESSING -> "CORE // COMPUTING"
                                    ReactorMode.SPEAKING -> "AUDIO.TX // ACTIVE"
                                    ReactorMode.STANDBY -> "MK.LXXXV // TAP CORE FOR VOICE"
                                },
                                accentColor = if (reactorMode == ReactorMode.LISTENING) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            Text(
                                text = stringResource(R.string.chat_welcome_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color(0xFFE3F6FF)
                            )
                            Text(
                                text = stringResource(R.string.chat_welcome_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB5D6EB),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "DIRECTIVES // " + stringResource(R.string.chat_starters_label).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(starters.chunked(2)) { rowStarters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowStarters.forEach { starter ->
                    Surface(
                        onClick = { onSelectStarter(starter) },
                        shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp, topEnd = 4.dp, bottomStart = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("starter_${starter.persona.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = starter.persona.icon(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = starter.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = starter.prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatComposerBar(
    composerText: String,
    selectedLanguage: AppLanguage,
    attachedImageUri: String?,
    isGenerating: Boolean,
    isListening: Boolean,
    onTextChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onToggleMic: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shape = CutCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            AnimatedVisibility(visible = !attachedImageUri.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box {
                        AsyncImage(
                            model = attachedImageUri,
                            contentDescription = "Selected photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CutCornerShape(10.dp))
                        )
                        FilledIconButton(
                            onClick = onRemoveImage,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .testTag("remove_attached_image_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.chat_remove_image),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "IMG.SCAN // Photo attached for Maya",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onPickImage,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("attach_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = stringResource(R.string.chat_attach_image),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = composerText,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            text = if (selectedLanguage == AppLanguage.BANGLA) {
                                "জার্ভিস মোডে বাংলায় বলুন বা লিখুন…"
                            } else {
                                stringResource(R.string.chat_input_hint)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    maxLines = 4,
                    shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp, topEnd = 4.dp, bottomStart = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!isGenerating) onSend() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input")
                )

                FilledIconButton(
                    onClick = onToggleMic,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isListening) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (isListening) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_mic_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = stringResource(
                            if (isListening) R.string.stt_mic_stop else R.string.stt_mic_start
                        )
                    )
                }

                FilledIconButton(
                    onClick = { if (isGenerating) onStop() else onSend() },
                    enabled = isGenerating || composerText.isNotBlank() || !attachedImageUri.isNullOrBlank(),
                    shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp, topEnd = 4.dp, bottomStart = 4.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isGenerating) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (isGenerating) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_button")
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send)
                    )
                }
            }
        }
    }
}
