package com.example.ui.screens.missioncontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.missioncontrol.android.MayaAppCommandRouter
import com.example.data.ai.ActiveAiProvider
import com.example.data.ai.MultiAiProviderRouter
import com.example.data.ai.ProviderCredentialVault
import kotlinx.coroutines.launch

/** Explicit command console for supported local Mission Queue operations and app launches. */
@Composable
fun MissionCommanderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val router = remember(context) { MayaAppCommandRouter(context) }
    val vault = remember(context) { ProviderCredentialVault(context) }
    val aiRouter = remember(vault) { MultiAiProviderRouter(vault) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var aiPrompt by remember { mutableStateOf("") }
    var aiResult by remember { mutableStateOf<String?>(null) }
    var aiStatus by remember { mutableStateOf("Loading active AI route…") }
    var activeProvider by remember { mutableStateOf<ActiveAiProvider?>(null) }
    var aiBusy by remember { mutableStateOf(false) }

    LaunchedEffect(vault) {
        activeProvider = runCatching { vault.getActiveProvider() }.getOrNull()
        aiStatus = activeProvider?.let { "Ready · ${it.providerId} · ${it.modelId}" } ?: "No active provider. Configure one in AI Providers first."
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Mission Commander", style = MaterialTheme.typography.titleLarge)
                Text("Explicit command entry · local actions", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(
            "Enter one supported command. Mission creation and specialist assignment are saved locally; they do not start Gemini or execute a workflow. App-launch commands open only the named installed app after you press Execute.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = command,
            onValueChange = { command = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Command") },
            placeholder = { Text("create mission My App details: Review architecture") },
            minLines = 2
        )

        Button(
            onClick = {
                error = null
                result = try {
                    router.execute(command.trim())
                        ?: "Command not recognized. Use one of the supported formats below."
                } catch (failure: Exception) {
                    error = failure.localizedMessage ?: "Command could not be executed."
                    null
                }
            },
            enabled = command.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Execute command") }

        Text("Central AI Commander", style = MaterialTheme.typography.titleMedium)
        Text(
            "Send a separate natural-language request to the explicitly selected provider. This does not execute Android actions or silently fall back to Gemini.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(aiStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = aiPrompt,
            onValueChange = { aiPrompt = it; aiResult = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask Maya AI") },
            placeholder = { Text("Analyze my app architecture and suggest improvements…") },
            minLines = 3
        )
        Button(
            onClick = {
                aiBusy = true
                aiResult = null
                scope.launch {
                    val route = activeProvider ?: runCatching { vault.getActiveProvider() }.getOrNull()
                    activeProvider = route
                    if (route == null) {
                        aiStatus = "No active provider. Configure one in AI Providers."
                    } else {
                        aiRouter.generateText(route.providerId, route.modelId, aiPrompt)
                            .onSuccess {
                                aiResult = it.text
                                aiStatus = "Response received · ${it.providerId} · ${it.modelId}"
                            }
                            .onFailure {
                                aiStatus = it.localizedMessage ?: "AI request failed."
                            }
                    }
                    aiBusy = false
                }
            },
            enabled = !aiBusy && aiPrompt.isNotBlank() && activeProvider != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (aiBusy) "Maya is thinking…" else "Send to active AI") }
        aiResult?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("AI response", style = MaterialTheme.typography.titleSmall)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        OutlinedButton(
            onClick = {
                activeProvider = null
                aiResult = null
                aiPrompt = ""
                scope.launch {
                    activeProvider = runCatching { vault.getActiveProvider() }.getOrNull()
                    aiStatus = activeProvider?.let { "Ready · ${it.providerId} · ${it.modelId}" }
                        ?: "No active provider. Configure one in AI Providers first."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Refresh active AI route") }

        OutlinedButton(
            onClick = { command = ""; result = null; error = null },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Clear") }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        result?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Command result", style = MaterialTheme.typography.titleSmall)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text("Supported mission commands", style = MaterialTheme.typography.titleMedium)
        Text("English: create mission <title> details: <description>", style = MaterialTheme.typography.bodySmall)
        Text("বাংলা: মিশন তৈরি করো <শিরোনাম> বিস্তারিত: <বিবরণ>", style = MaterialTheme.typography.bodySmall)
        Text(
            "English: assign <Agent Name> to mission <exact title>: <task>",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "বাংলা: এজেন্ট <Agent Name> কে মিশন <সঠিক শিরোনাম> এ কাজ দাও: <কাজ>",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Examples", style = MaterialTheme.typography.titleMedium)
        Text("create mission Maya App details: Audit navigation and settings", style = MaterialTheme.typography.bodySmall)
        Text("assign Android Specialist to mission Maya App: Inspect Android lifecycle risks", style = MaterialTheme.typography.bodySmall)
        Text(
            "Assignment commands require an existing mission with one exact matching title. The saved task remains QUEUED until you explicitly run it from Task Coordination or Workflow Execution.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
