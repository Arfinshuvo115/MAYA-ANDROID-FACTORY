package com.example.ui.screens.missioncontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.data.ai.ProviderCredentialVault
import kotlinx.coroutines.launch

/** Securely stores optional provider keys; does not claim unimplemented adapters are connected. */
@Composable
fun MissionAiProvidersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vault = remember(context) { ProviderCredentialVault(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val providers = ProviderCredentialVault.providers
    var selectedId by rememberSaveable { mutableStateOf(providers.first().id) }
    var menuExpanded by remember { mutableStateOf(false) }
    var keyDraft by rememberSaveable { mutableStateOf("") }
    var savedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf("Credentials are encrypted locally with Android Keystore.") }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selected = providers.first { it.id == selectedId }

    fun refresh() {
        scope.launch {
            savedIds = providers.filter { provider -> runCatching { vault.hasKey(provider.id) }.getOrDefault(false) }
                .map { it.id }.toSet()
        }
    }

    LaunchedEffect(vault) { refresh() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("AI Provider Vault", style = MaterialTheme.typography.titleLarge)
                Text("Encrypted provider credential storage", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            "Save optional API keys for future provider adapters. Keys are encrypted on this device. A saved key does not mean that provider is connected to chat, routing, voice or agent execution. Gemini remains configured through the existing Settings screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Provider: ${selected.displayName}")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName + if (provider.id in savedIds) " · Saved" else " · Not saved") },
                    onClick = { selectedId = provider.id; keyDraft = ""; menuExpanded = false; status = provider.integrationStatus }
                )
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selected.displayName, style = MaterialTheme.typography.titleMedium)
                Text(selected.description, style = MaterialTheme.typography.bodySmall)
                Text(selected.integrationStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(
                    if (selected.id in savedIds) "Encrypted credential: saved" else "Encrypted credential: not saved",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected.id in savedIds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key") },
                    placeholder = { Text("Paste provider API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { vault.save(selected.id, keyDraft) }
                                .onSuccess {
                                    keyDraft = ""
                                    status = "${selected.displayName} key encrypted and saved on this device."
                                    refresh()
                                }
                                .onFailure { status = it.localizedMessage ?: "Could not save credential." }
                            busy = false
                        }
                    },
                    enabled = !busy && keyDraft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Saving…" else "Save encrypted key") }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !busy && selected.id in savedIds,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete saved key") }
            }
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text(
            "Provider activation requires a reviewed API adapter, request/response handling, timeout and rate-limit policy, error mapping, and tests. No automatic fallback or provider switching occurs yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.displayName} key?") },
            text = { Text("This removes the encrypted credential from this device. It does not affect any provider account.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    busy = true
                    scope.launch {
                        runCatching { vault.remove(selected.id) }
                            .onSuccess { status = "${selected.displayName} credential removed."; refresh() }
                            .onFailure { status = it.localizedMessage ?: "Could not remove credential." }
                        busy = false
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
