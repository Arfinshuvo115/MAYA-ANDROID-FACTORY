package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.BanglaAccent
import com.example.domain.model.GeminiKeyStatus
import com.example.domain.model.GeminiVoicePreset
import com.example.domain.model.MayaPersona
import com.example.domain.model.ResponseLength
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import com.example.ui.components.JarvisHudCard
import com.example.ui.components.JarvisSectionHeader
import com.example.ui.components.JarvisStatusPill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val keyStatus by viewModel.keyStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showClearDialog by remember { mutableStateOf(false) }
    var apiKeyDraft by rememberSaveable(prefs.geminiApiKey) { mutableStateOf(prefs.geminiApiKey) }
    var revealApiKey by rememberSaveable { mutableStateOf(false) }

    val hudShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp, topEnd = 3.dp, bottomStart = 3.dp)
    val hasActiveKey = prefs.geminiApiKey.isNotBlank()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                JarvisSectionHeader(
                    telemetryCode = "SYS.CORE // DIAGNOSTICS & CONFIG",
                    title = stringResource(R.string.settings_title),
                    subtitle = "Calibrate Gemini API key, J.A.R.V.I.S. HUD theme, Bangla audio, and persistence"
                )

                // Engine Status Card
                JarvisHudCard(
                    telemetryTag = if (hasActiveKey) {
                        "KERNEL // GEMINI CLOUD LINKED"
                    } else {
                        "KERNEL // ON-DEVICE LOCAL"
                    },
                    accentColor = if (hasActiveKey) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.settings_backend_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = if (hasActiveKey) {
                            stringResource(R.string.settings_backend_mode_gemini, prefs.geminiModelId)
                        } else {
                            stringResource(R.string.settings_backend_mode)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_backend_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Gemini API Key Configuration Card
                JarvisHudCard(
                    telemetryTag = "AUTH // GEMINI API KEY",
                    accentColor = MaterialTheme.colorScheme.primary
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
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.settings_gemini_key_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        JarvisStatusPill(
                            text = if (hasActiveKey) "KEY // ACTIVE" else "KEY // NOT SET",
                            accentColor = if (hasActiveKey) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_gemini_key_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = {
                            apiKeyDraft = it
                            viewModel.resetKeyStatus()
                        },
                        label = { Text(stringResource(R.string.settings_gemini_key_label)) },
                        placeholder = { Text(stringResource(R.string.settings_gemini_key_hint)) },
                        singleLine = true,
                        visualTransformation = if (revealApiKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = { revealApiKey = !revealApiKey },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("gemini_api_key_visibility_toggle")
                            ) {
                                Icon(
                                    imageVector = if (revealApiKey) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = if (revealApiKey) "Hide API key" else "Show API key"
                                )
                            }
                        },
                        shape = hudShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input")
                    )

                    Text(
                        text = stringResource(R.string.settings_gemini_model_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UserPreferences.AVAILABLE_GEMINI_MODELS.forEach { modelId ->
                            FilterChip(
                                selected = prefs.geminiModelId == modelId,
                                onClick = { viewModel.setGeminiModelId(modelId) },
                                shape = hudShape,
                                label = { Text(modelId) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("gemini_model_chip_$modelId")
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveGeminiApiKey(apiKeyDraft) { saved ->
                                    onShowSnackbar(
                                        context.getString(
                                            if (saved) R.string.settings_gemini_key_saved
                                            else R.string.settings_gemini_key_cleared
                                        )
                                    )
                                }
                            },
                            shape = hudShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_gemini_api_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_gemini_key_save))
                        }

                        OutlinedButton(
                            onClick = { viewModel.verifyGeminiApiKey(apiKeyDraft) },
                            enabled = keyStatus !is GeminiKeyStatus.Testing && apiKeyDraft.isNotBlank(),
                            shape = hudShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("verify_gemini_api_key_button")
                        ) {
                            if (keyStatus is GeminiKeyStatus.Testing) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing…")
                            } else {
                                Text(stringResource(R.string.settings_gemini_key_verify))
                            }
                        }
                    }

                    if (hasActiveKey || apiKeyDraft.isNotBlank()) {
                        TextButton(
                            onClick = {
                                apiKeyDraft = ""
                                viewModel.clearGeminiApiKey {
                                    onShowSnackbar(context.getString(R.string.settings_gemini_key_cleared))
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_gemini_api_key_button")
                        ) {
                            Text(stringResource(R.string.settings_gemini_key_clear))
                        }
                    }

                    when (val status = keyStatus) {
                        is GeminiKeyStatus.Valid -> {
                            JarvisStatusPill(
                                text = "OK // ${status.message}",
                                accentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                        is GeminiKeyStatus.Invalid -> {
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        GeminiKeyStatus.Testing, GeminiKeyStatus.Idle -> Unit
                    }
                }

                // Language & Bangla Voice
                SettingsSectionCard(
                    telemetryTag = "LOCALE // BANGLA ENGINE",
                    title = "Language & Bangla Voice"
                ) {
                    Text(
                        text = "Response Language",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = prefs.preferredLanguage == lang,
                                onClick = { viewModel.setPreferredLanguage(lang) },
                                shape = hudShape,
                                label = { Text(lang.label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_lang_chip_${lang.name.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.voice_select_accent),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BanglaAccent.entries.forEach { accent ->
                            FilterChip(
                                selected = prefs.banglaAccent == accent,
                                onClick = { viewModel.setBanglaAccent(accent) },
                                shape = hudShape,
                                label = { Text(accent.label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_accent_chip_${accent.name.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.voice_select_persona),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GeminiVoicePreset.entries.forEach { voice ->
                            FilterChip(
                                selected = prefs.preferredVoicePreset == voice,
                                onClick = { viewModel.setPreferredVoicePreset(voice) },
                                shape = hudShape,
                                label = { Text(voice.voiceName) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_voice_chip_${voice.voiceName.lowercase()}")
                            )
                        }
                    }
                }

                // Appearance
                SettingsSectionCard(
                    telemetryTag = "DISPLAY // HUD THEME",
                    title = stringResource(R.string.settings_appearance)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = prefs.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = hudShape,
                                label = { Text(mode.label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("theme_chip_${mode.name.lowercase()}")
                            )
                        }
                    }
                }

                // Default Persona
                SettingsSectionCard(
                    telemetryTag = "PERSONA // DEFAULT",
                    title = stringResource(R.string.settings_persona)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MayaPersona.entries.forEach { persona ->
                            FilterChip(
                                selected = prefs.defaultPersona == persona,
                                onClick = { viewModel.setDefaultPersona(persona) },
                                shape = hudShape,
                                label = { Text(persona.displayName) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("default_persona_chip_${persona.name.lowercase()}")
                            )
                        }
                    }
                }

                // Response Depth
                SettingsSectionCard(
                    telemetryTag = "DEPTH // OUTPUT",
                    title = stringResource(R.string.settings_response_length)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ResponseLength.entries.forEach { length ->
                            FilterChip(
                                selected = prefs.responseLength == length,
                                onClick = { viewModel.setResponseLength(length) },
                                shape = hudShape,
                                label = { Text(length.label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("length_chip_${length.name.lowercase()}")
                            )
                        }
                    }
                }

                // Voice & TTS
                SettingsSectionCard(
                    telemetryTag = "AUDIO // ACCESSIBILITY",
                    title = stringResource(R.string.settings_voice)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.settings_auto_speak),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.settings_auto_speak_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.autoReadAloud,
                            onCheckedChange = viewModel::setAutoReadAloud,
                            modifier = Modifier.testTag("auto_speak_switch")
                        )
                    }
                }

                // Local Data
                SettingsSectionCard(
                    telemetryTag = "STORAGE // VAULT",
                    title = stringResource(R.string.settings_data)
                ) {
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        shape = hudShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_clear_history))
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllHistory {
                            onShowSnackbar("Local history cleared")
                        }
                    },
                    modifier = Modifier.testTag("confirm_clear_history_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_clear),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    telemetryTag: String,
    title: String,
    content: @Composable () -> Unit
) {
    JarvisHudCard(telemetryTag = telemetryTag) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}
