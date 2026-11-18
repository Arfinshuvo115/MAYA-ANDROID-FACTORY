package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.R
import com.example.domain.model.ChatMessage
import com.example.domain.model.MayaPersona
import com.example.domain.model.MessageRole

fun MayaPersona.icon(): ImageVector = when (this) {
    MayaPersona.ASSISTANT -> Icons.Filled.AutoAwesome
    MayaPersona.CODER -> Icons.Filled.Code
    MayaPersona.CREATIVE -> Icons.Filled.Palette
    MayaPersona.ANALYST -> Icons.Filled.Insights
}

@Composable
fun MayaPersonaChipRow(
    selectedPersona: MayaPersona,
    onSelectPersona: (MayaPersona) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(MayaPersona.entries) { persona ->
            val selected = persona == selectedPersona
            FilterChip(
                selected = selected,
                onClick = { onSelectPersona(persona) },
                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp, topEnd = 3.dp, bottomStart = 3.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("persona_chip_${persona.name.lowercase()}"),
                label = {
                    Text(
                        text = persona.displayName,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = persona.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                )
            )
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isSpeaking: Boolean,
    onCopy: (String) -> Unit,
    onToggleSpeak: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val accentColor = if (isUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            ArcReactorCore(
                mode = if (isSpeaking) ReactorMode.SPEAKING else ReactorMode.STANDBY,
                size = 34.dp,
                contentDescriptionText = "JARVIS Maya core indicator",
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 540.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                JarvisStatusPill(
                    text = if (isUser) {
                        "OPR // INPUT"
                    } else {
                        "J.A.R.V.I.S. // ${message.persona.badge.uppercase()}"
                    },
                    accentColor = accentColor
                )
            }

            Surface(
                shape = if (isUser) {
                    CutCornerShape(topStart = 16.dp, bottomEnd = 4.dp, topEnd = 4.dp, bottomStart = 16.dp)
                } else {
                    CutCornerShape(topStart = 4.dp, bottomEnd = 16.dp, topEnd = 16.dp, bottomStart = 4.dp)
                },
                color = if (isUser) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.50f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (!message.imageUri.isNullOrBlank()) {
                        AsyncImage(
                            model = message.imageUri,
                            contentDescription = "Attached user photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(176.dp)
                                .clip(CutCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (isUser) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        MayaFormattedResponse(rawText = message.content)
                    }
                }
            }

            if (!isUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    IconButton(
                        onClick = { onCopy(message.content) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("copy_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.chat_copy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onToggleSpeak(message) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("speak_message_button")
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(
                                if (isSpeaking) R.string.chat_stop_speaking else R.string.chat_speak
                            ),
                            tint = if (isSpeaking) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingBubble(
    partialText: String?,
    persona: MayaPersona,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        ArcReactorCore(
            mode = ReactorMode.PROCESSING,
            size = 34.dp,
            contentDescriptionText = "JARVIS Maya computing",
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))

        Surface(
            shape = CutCornerShape(topStart = 4.dp, bottomEnd = 16.dp, topEnd = 16.dp, bottomStart = 4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            modifier = Modifier.widthIn(max = 540.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (partialText.isNullOrBlank()) {
                        "J.A.R.V.I.S. // COMPUTING (${persona.displayName.uppercase()})…"
                    } else {
                        "J.A.R.V.I.S. // STREAMING (${persona.displayName.uppercase()})"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (!partialText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = partialText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
