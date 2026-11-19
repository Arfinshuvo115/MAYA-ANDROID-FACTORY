package com.example.ui.screens.missioncontrol

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.missioncontrol.MissionAgentPipeline
import com.example.missioncontrol.MissionAgentRoster
import com.example.missioncontrol.MissionQueueRepository
import com.example.missioncontrol.MissionTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Device-local workforce view. Role status is based on callbacks from the real
 * sequential Gemini pipeline; it is not a remote server presence indicator.
 */
@Composable
fun MissionAgentWorkforceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { MissionQueueRepository(context) }
    val pipeline = remember(context) { MissionAgentPipeline(context) }
    val tasks = remember { mutableStateListOf<MissionTask>() }
    val agentStates = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var activeAgentId by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("No agent run started in this screen.") }
    var error by remember { mutableStateOf<String?>(null) }

    fun refreshTasks() {
        tasks.clear()
        tasks.addAll(repository.getAll())
        if (selectedTaskId == null || tasks.none { it.id == selectedTaskId }) {
            selectedTaskId = tasks.firstOrNull { it.status != MissionQueueRepository.STATUS_COMPLETED }?.id
                ?: tasks.firstOrNull()?.id
        }
    }

    LaunchedEffect(repository) { refreshTasks() }
    val selectedTask = tasks.firstOrNull { it.id == selectedTaskId }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Agent Workforce", style = MaterialTheme.typography.titleLarge)
                Text("16 sequential specialist roles · device-local mission queue", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(
            "This screen reports role progress from the configured Gemini pipeline. It does not show remote agent presence or 16 independent AI services.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Select a saved mission", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = ::refreshTasks, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh mission queue")
        }
        if (tasks.isEmpty()) {
            Text("No saved mission found. Create one in Mission Center first.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                modifier = Modifier.weight(0.8f, fill = false).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !running) {
                            selectedTaskId = task.id
                            error = null
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (task.id == selectedTaskId) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleSmall)
                            Text("Mission status: ${task.status}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        selectedTask?.let { task ->
            Text("Selected: ${task.title}", style = MaterialTheme.typography.titleSmall)
            Button(
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    running = true
                    error = null
                    agentStates.clear()
                    MissionAgentRoster.agents.forEach { agentStates[it.id] = "WAITING" }
                    progress = "Starting 16-role pipeline…"
                    activeAgentId = null
                    scope.launch {
                        repository.setStatus(task.id, MissionQueueRepository.STATUS_ACTIVE)
                        refreshTasks()
                        try {
                            val result = pipeline.run(
                                task = task,
                                onStageStarted = { agent, index, total ->
                                    activeAgentId = agent.id
                                    agentStates[agent.id] = "RUNNING"
                                    progress = "Running ${index}/${total}: ${agent.displayName}"
                                },
                                onStageCompleted = { agent, index, total, _ ->
                                    agentStates[agent.id] = "COMPLETED"
                                    progress = "Completed ${index}/${total}: ${agent.displayName}"
                                }
                            )
                            repository.setReport(task.id, result.toMarkdown())
                            repository.setStatus(task.id, MissionQueueRepository.STATUS_COMPLETED)
                            progress = "All ${result.stages.size}/${MissionAgentRoster.agents.size} specialist passes completed. Report saved locally."
                            activeAgentId = null
                        } catch (cancelled: CancellationException) {
                            repository.setStatus(task.id, MissionQueueRepository.STATUS_PAUSED)
                            progress = "Run cancelled; mission paused."
                            throw cancelled
                        } catch (failure: Exception) {
                            activeAgentId?.let { agentStates[it] = "FAILED" }
                            repository.setStatus(task.id, MissionQueueRepository.STATUS_PAUSED)
                            error = failure.localizedMessage ?: "Agent pipeline failed."
                            progress = "Pipeline stopped. Mission paused; completed role outputs were not represented as a completed mission."
                        } finally {
                            running = false
                            activeAgentId = null
                            refreshTasks()
                        }
                    }
                }
            ) {
                Text(if (running) "Running specialist roles…" else "Run 16 Agents on Selected Mission")
            }
        }

        Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

        Text("Specialist role status", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(MissionAgentRoster.agents, key = { it.id }) { agent ->
                val state = agentStates[agent.id] ?: "WAITING"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(agent.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(state, style = MaterialTheme.typography.labelMedium,
                                color = when (state) {
                                    "COMPLETED" -> MaterialTheme.colorScheme.primary
                                    "FAILED" -> MaterialTheme.colorScheme.error
                                    "RUNNING" -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                })
                        }
                        Text(agent.responsibility, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
