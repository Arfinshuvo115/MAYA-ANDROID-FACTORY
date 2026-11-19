package com.example.ui.screens.missioncontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.missioncontrol.android.MayaAppCommandRouter

/** Explicit command console for supported local Mission Queue operations and app launches. */
@Composable
fun MissionCommanderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val router = remember(context) { MayaAppCommandRouter(context) }
    var command by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
