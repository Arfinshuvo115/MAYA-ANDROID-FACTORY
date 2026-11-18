package com.example.ui.screens.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.R
import com.example.domain.model.AiWorkflowTool
import com.example.domain.model.StudioResult
import com.example.domain.model.StudioRunState
import com.example.ui.components.JarvisHudCard
import com.example.ui.components.JarvisSectionHeader

@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel,
    onContinueInChat: (title: String, markdown: String) -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTool by viewModel.selectedTool.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val attachedImageUri by viewModel.attachedImageUri.collectAsStateWithLifecycle()
    val runState by viewModel.runState.collectAsStateWithLifecycle()

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachImage(uri.toString())
        }
    }

    val hudChipShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp, topEnd = 3.dp, bottomStart = 3.dp)

    Column(modifier = modifier.fillMaxSize()) {
        JarvisSectionHeader(
            telemetryCode = "STUDIO // WORKFLOW MATRIX",
            title = stringResource(R.string.studio_title),
            subtitle = stringResource(R.string.studio_subtitle),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AiWorkflowTool.entries) { tool ->
                FilterChip(
                    selected = tool == selectedTool,
                    onClick = { viewModel.selectTool(tool) },
                    shape = hudChipShape,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tool_chip_${tool.name.lowercase()}"),
                    label = { Text(tool.title) },
                    leadingIcon = {
                        Icon(
                            imageVector = tool.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWide = maxWidth >= 800.dp
            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        StudioInputCard(
                            selectedTool = selectedTool,
                            inputText = inputText,
                            attachedImageUri = attachedImageUri,
                            isRunning = runState is StudioRunState.Running,
                            onInputChange = viewModel::updateInputText,
                            onPrefillSample = viewModel::prefillSample,
                            onPickPhoto = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onRun = viewModel::runWorkflow
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            StudioOutputPanel(
                                runState = runState,
                                onCopy = { md ->
                                    clipboard.setText(AnnotatedString(md))
                                    onShowSnackbar(context.getString(R.string.chat_copied))
                                },
                                onContinueInChat = onContinueInChat
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        StudioInputCard(
                            selectedTool = selectedTool,
                            inputText = inputText,
                            attachedImageUri = attachedImageUri,
                            isRunning = runState is StudioRunState.Running,
                            onInputChange = viewModel::updateInputText,
                            onPrefillSample = viewModel::prefillSample,
                            onPickPhoto = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onRun = viewModel::runWorkflow
                        )
                    }
                    item {
                        StudioOutputPanel(
                            runState = runState,
                            onCopy = { md ->
                                clipboard.setText(AnnotatedString(md))
                                onShowSnackbar(context.getString(R.string.chat_copied))
                            },
                            onContinueInChat = onContinueInChat
                        )
                    }
                }
            }
        }
    }
}

private fun AiWorkflowTool.icon(): ImageVector = when (this) {
    AiWorkflowTool.SUMMARIZER -> Icons.Filled.Summarize
    AiWorkflowTool.CODE_COPILOT -> Icons.Filled.Code
    AiWorkflowTool.COPY_FORGE -> Icons.Filled.Create
    AiWorkflowTool.VISION_INSPECTOR -> Icons.Filled.ImageSearch
}

@Composable
private fun StudioInputCard(
    selectedTool: AiWorkflowTool,
    inputText: String,
    attachedImageUri: String?,
    isRunning: Boolean,
    onInputChange: (String) -> Unit,
    onPrefillSample: () -> Unit,
    onPickPhoto: () -> Unit,
    onRun: () -> Unit
) {
    val hudShape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp, topEnd = 4.dp, bottomStart = 4.dp)

    JarvisHudCard(
        telemetryTag = "MODULE // ${selectedTool.name}"
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = selectedTool.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = selectedTool.title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            TextButton(
                onClick = onPrefillSample,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("prefill_sample_button")
            ) {
                Text(stringResource(R.string.studio_prefill))
            }
        }

        Text(
            text = selectedTool.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AnimatedVisibility(visible = selectedTool.requiresImageOptional) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickPhoto,
                    shape = hudShape,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("studio_pick_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.studio_pick_photo))
                }

                if (!attachedImageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = attachedImageUri,
                        contentDescription = "Selected photo for Vision Inspector",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(hudShape)
                    )
                }
            }
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            label = { Text(selectedTool.inputLabel) },
            placeholder = { Text(stringResource(R.string.studio_input_hint)) },
            minLines = 3,
            maxLines = 6,
            shape = hudShape,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("studio_prompt_input")
        )

        Button(
            onClick = onRun,
            enabled = !isRunning,
            shape = hudShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("run_workflow_button")
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.studio_running))
            } else {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.studio_run))
            }
        }
    }
}

@Composable
private fun StudioOutputPanel(
    runState: StudioRunState,
    onCopy: (String) -> Unit,
    onContinueInChat: (title: String, markdown: String) -> Unit
) {
    val hudShape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp, topEnd = 4.dp, bottomStart = 4.dp)

    when (runState) {
        StudioRunState.Idle -> {
            JarvisHudCard(
                telemetryTag = "OUTPUT // STANDBY",
                accentColor = MaterialTheme.colorScheme.tertiary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Ready to synthesize",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Choose a workflow above, enter your prompt or tap Try Sample, and run Maya Studio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        StudioRunState.Running -> {
            JarvisHudCard(
                telemetryTag = "OUTPUT // EXECUTING"
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = "Generating structured output…",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        is StudioRunState.Error -> {
            Surface(
                shape = hudShape,
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = runState.message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        is StudioRunState.Success -> {
            val result: StudioResult = runState.result
            JarvisHudCard(
                telemetryTag = "OUTPUT // SYNTHESIZED",
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag("studio_result_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result.outputTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onCopy(result.rawMarkdown) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("studio_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.chat_copy)
                        )
                    }
                }

                result.sections.forEach { section ->
                    Surface(
                        shape = hudShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = section.heading,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = section.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onContinueInChat(result.outputTitle, result.rawMarkdown) },
                    shape = hudShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("studio_continue_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.studio_send_to_chat))
                }
            }
        }
    }
}
