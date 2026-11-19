package com.example.ui.screens.missioncontrol

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.missioncontrol.MissionQueueRepository
import com.example.missioncontrol.MissionTask

@Composable
fun MissionQueueScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { MissionQueueRepository(context) }
    val tasks = remember { mutableStateListOf<MissionTask>() }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        tasks.clear()
        tasks.addAll(repository.getAll())
    }

    LaunchedEffect(repository) { refresh() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Mission Center", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Persistent device-local mission queue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mission title") },
            placeholder = { Text("e.g. Review my Android project") },
            singleLine = true
        )
        OutlinedTextField(
            value = details,
            onValueChange = { details = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mission details (optional)") },
            minLines = 2
        )
        Button(
            onClick = {
                runCatching { repository.add(title, details) }
                    .onSuccess {
                        title = ""
                        details = ""
                        error = null
                        refresh()
                    }
                    .onFailure { error = it.message ?: "Could not save mission." }
            },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add to Mission Queue") }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Text("Saved missions · ${tasks.size}", style = MaterialTheme.typography.titleMedium)
        if (tasks.isEmpty()) {
            Text(
                "No missions yet. Add a mission above. Items are saved on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(task.title, style = MaterialTheme.typography.titleSmall)
                            if (task.details.isNotBlank()) {
                                Text(task.details, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "Status: ${task.status}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (task.status) {
                                    MissionQueueRepository.STATUS_QUEUED,
                                    MissionQueueRepository.STATUS_PAUSED -> Button(onClick = {
                                        repository.setStatus(task.id, MissionQueueRepository.STATUS_ACTIVE)
                                        refresh()
                                    }) { Text("Start") }
                                    MissionQueueRepository.STATUS_ACTIVE -> OutlinedButton(onClick = {
                                        repository.setStatus(task.id, MissionQueueRepository.STATUS_PAUSED)
                                        refresh()
                                    }) { Text("Pause") }
                                }
                                if (task.status != MissionQueueRepository.STATUS_COMPLETED) {
                                    OutlinedButton(onClick = {
                                        repository.setStatus(task.id, MissionQueueRepository.STATUS_COMPLETED)
                                        refresh()
                                    }) { Text("Complete") }
                                } else {
                                    OutlinedButton(onClick = {
                                        repository.setStatus(task.id, MissionQueueRepository.STATUS_QUEUED)
                                        refresh()
                                    }) { Text("Reopen") }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            "Honest status: this queue saves and tracks missions locally. Starting a mission changes its local status only; it does not yet launch an AI agent or remote workflow.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
