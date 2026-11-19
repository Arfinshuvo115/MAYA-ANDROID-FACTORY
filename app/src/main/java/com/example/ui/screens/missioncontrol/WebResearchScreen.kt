package com.example.ui.screens.missioncontrol

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.local.PreferencesRepository
import com.example.missioncontrol.TavilyResearchClient
import com.example.missioncontrol.WebResearchResult
import kotlinx.coroutines.launch

@Composable
fun WebResearchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferencesRepository = remember(context) { PreferencesRepository(context) }
    val preferences by preferencesRepository.preferences.collectAsState(initial = com.example.domain.model.UserPreferences())
    val client = remember { TavilyResearchClient() }
    val scope = rememberCoroutineScope()
    var keyDraft by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WebResearchResult>>(emptyList()) }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Column {
                Text("Web Research", style = MaterialTheme.typography.titleLarge)
                Text("Live search through Tavily API", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            if (preferences.tavilyApiKey.isNotBlank()) "Tavily key saved on this device"
            else "Tavily API key not configured",
            style = MaterialTheme.typography.labelMedium,
            color = if (preferences.tavilyApiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tavily API Key") },
            placeholder = { Text("Paste your Tavily API key") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        preferencesRepository.setTavilyApiKey(keyDraft)
                        keyDraft = ""
                        error = null
                    }
                },
                enabled = keyDraft.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("Save Key") }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        preferencesRepository.setTavilyApiKey("")
                        keyDraft = ""
                        error = null
                    }
                },
                enabled = preferences.tavilyApiKey.isNotBlank()
            ) { Text("Clear Key") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Research question") },
            placeholder = { Text("What do you want Maya to research?") },
            minLines = 2
        )
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    answer = ""
                    results = emptyList()
                    client.search(preferences.tavilyApiKey, query).fold(
                        onSuccess = { found ->
                            results = found
                            answer = if (found.isEmpty()) "Search completed, but Tavily returned no usable results."
                            else "Live search returned ${found.size} results. Review the source links and snippets below."
                        },
                        onFailure = { failure -> error = failure.localizedMessage ?: "Search failed." }
                    )
                    busy = false
                }
            },
            enabled = !busy && query.isNotBlank() && preferences.tavilyApiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Searching…" else "Search the Web") }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (answer.isNotBlank()) Text(answer, style = MaterialTheme.typography.bodyMedium)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results, key = { it.url }) { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(result.title, style = MaterialTheme.typography.titleSmall)
                        Text(result.content, style = MaterialTheme.typography.bodySmall)
                        Text(result.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url)))
                            }.onFailure { error = "Could not open source link: ${it.localizedMessage}" }
                        }) { Text("Open Source") }
                    }
                }
            }
        }
        Text(
            "Search is live only when a valid Tavily API key is saved. Results are source snippets, not verified facts; open and assess sources before relying on them. The key is stored in app DataStore on this device, not encrypted by this feature.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
