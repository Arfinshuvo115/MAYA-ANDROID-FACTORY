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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.missioncontrol.MissionAgentPipeline
import com.example.missioncontrol.MissionAgentRoster
import com.example.missioncontrol.MissionAgentTaskRepository
import com.example.missioncontrol.MissionAgentWorkItem
import com.example.missioncontrol.MissionQueueRepository
import com.example.missioncontrol.MissionTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * User-controlled task assignment board. Each item is executed only when its
 * Run button is pressed; completed outputs become context for later assignments.
 */
@Composable
fun MissionAgentTaskCoordinationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val missionRepository = remember(context) { MissionQueueRepository(context) }
    val assignmentRepository = remember(context) { MissionAgentTaskRepository(context) }
    val pipeline = remember(context) { MissionAgentPipeline(context) }
    val missions = remember { mutableStateListOf<MissionTask>() }
    val assignments = remember { mutableStateListOf<MissionAgentWorkItem>() }
    val scope = rememberCoroutineScope()

    var selectedMissionId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var selectedAgentId by remember { mutableStateOf(MissionAgentRoster.agents.first().id) }
    var agentMenuExpanded by remember { mutableStateOf(false) }
    var runningAssignmentId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Create a specialist assignment or run a queued task.") }
    var error by remember { mutableStateOf<String?>(null) }

    fun refreshMissions() {
        missions.clear()
        missions.addAll(missionRepository.getAll())
        if (selectedMissionId == null || missions.none { it.id == selectedMissionId }) {
            selectedMissionId = missions.firstOrNull()?.id
        }
    }

    fun refreshAssignments() {
        assignments.clear()
        selectedMissionId?.let { assignments.addAll(assignmentRepository.getForMission(it)) }
    }

    LaunchedEffect(missionRepository) {
        refreshMissions()
    }
    LaunchedEffect(selectedMissionId) {
        refreshAssignments()
    }

    val selectedMission = missions.firstOrNull { it.id == selectedMissionId }
    val selectedAgent = MissionAgentRoster.agents.firstOrNull { it.id == selectedAgentId }
        ?: MissionAgentRoster.agents.first()

    fun runAssignment(item: MissionAgentWorkItem) {
        val mission = missions.firstOrNull { it.id == item.missionId } ?: run {
            error = "The parent mission no longer exists."
            return
        }
        if (runningAssignmentId != null) return
        runningAssignmentId = item.id
        error = null
        message = "Starting ${MissionAgentRoster.agents.firstOrNull { it.id == item.agentId }?.displayName ?: item.agentId}…"
        assignmentRepository.setStatus(item.id, MissionAgentTaskRepository.STATUS_RUNNING)
        refreshAssignments()
        val previousHandoffs = assignmentRepository.previousHandoffs(item)
        scope.launch {
            try {
                val agent = MissionAgentRoster.agents.first { it.id == item.agentId }
                val output = pipeline.runAssignedTask(
                    mission = mission,
                    agent = agent,
                    assignmentTitle = item.title,
                    assignmentInstructions = item.instructions,
                    previousHandoff = previousHandoffs
                )
                assignmentRepository.complete(item.id, output)
                message = "Completed: ${agent.displayName}. Handoff saved for subsequent assignments."
            } catch (cancelled: CancellationException) {
                assignmentRepository.setStatus(item.id, MissionAgentTaskRepository.STATUS_QUEUED)
                message = "Assignment cancelled and returned to QUEUED."
                throw cancelled
            } catch (failure: Exception) {
                assignmentRepository.fail(item.id, failure.localizedMessage ?: "Assigned task failed.")
                error = failure.localizedMessage ?: "Assigned task failed."
                message = "Assignment failed. It can be retried after reviewing the error."
            } finally {
                runningAssignmentId = null
                refreshAssignments()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, enabled = runningAssignmentId == null) { Text("Back") }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Task Assignment & Coordination", style = MaterialTheme.typography.titleLarge)
                Text("Assign → execute → save handoff → continue", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(
            "Assignments are stored on this device. Tasks unlock in creation order: a later task waits until earlier assignments complete, so each specialist receives the completed handoffs before it runs. Each task runs only after you press Run; no remote worker or background execution is implied.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("1. Choose a mission", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = ::refreshMissions,
            enabled = runningAssignmentId == null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Refresh Mission Queue") }

        if (missions.isEmpty()) {
            Text("No saved mission. Create a mission in Mission Center first.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(0.65f, fill = false).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(missions, key = { it.id }) { mission ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = runningAssignmentId == null) {
                            selectedMissionId = mission.id
                            error = null
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (mission.id == selectedMissionId) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(mission.title, style = MaterialTheme.typography.titleSmall)
                            Text("Mission: ${mission.status}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (selectedMission != null) {
            Text("2. Create assignment", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(180) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Task title") },
                singleLine = true,
                enabled = runningAssignmentId == null
            )
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it.take(5000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Instructions / acceptance criteria (optional)") },
                minLines = 2,
                enabled = runningAssignmentId == null
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = runningAssignmentId == null,
                    onClick = { agentMenuExpanded = true }
                ) { Text("Assign to: ${selectedAgent.displayName}") }
                DropdownMenu(
                    expanded = agentMenuExpanded,
                    onDismissRequest = { agentMenuExpanded = false }
                ) {
                    MissionAgentRoster.agents.forEach { agent ->
                        DropdownMenuItem(
                            text = { Text(agent.displayName) },
                            onClick = {
                                selectedAgentId = agent.id
                                agentMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Button(
                enabled = runningAssignmentId == null && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    try {
                        assignmentRepository.create(
                            missionId = selectedMission.id,
                            title = title,
                            instructions = instructions,
                            agentId = selectedAgentId
                        )
                        title = ""
                        instructions = ""
                        error = null
                        message = "Assignment saved for ${selectedAgent.displayName}."
                        refreshAssignments()
                    } catch (failure: Exception) {
                        error = failure.localizedMessage ?: "Could not create assignment."
                    }
                }
            ) { Text("Save Agent Assignment") }

            Text("3. Assignment queue (${assignments.size})", style = MaterialTheme.typography.titleMedium)
            if (assignments.isEmpty()) {
                Text("No assignments for this mission yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(assignments, key = { it.id }) { item ->
                        val agent = MissionAgentRoster.agents.firstOrNull { it.id == item.agentId }
                        val earlierTasksCompleted = assignments.takeWhile { it.id != item.id }
                            .all { it.status == MissionAgentTaskRepository.STATUS_COMPLETED }
                        val canRun = earlierTasksCompleted &&
                            item.status != MissionAgentTaskRepository.STATUS_COMPLETED &&
                            item.status != MissionAgentTaskRepository.STATUS_RUNNING
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text(item.status, style = MaterialTheme.typography.labelMedium,
                                        color = when (item.status) {
                                            MissionAgentTaskRepository.STATUS_COMPLETED -> MaterialTheme.colorScheme.primary
                                            MissionAgentTaskRepository.STATUS_FAILED -> MaterialTheme.colorScheme.error
                                            MissionAgentTaskRepository.STATUS_RUNNING -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        })
                                }
                                Text("Assigned specialist: ${agent?.displayName ?: item.agentId}", style = MaterialTheme.typography.bodySmall)
                                if (item.instructions.isNotBlank()) {
                                    Text(item.instructions, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (item.lastError.isNotBlank()) {
                                    Text("Error: ${item.lastError}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                                if (item.handoff.isNotBlank()) {
                                    Text("Saved handoff: ${item.handoff.take(360)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = runningAssignmentId == null && canRun,
                                        onClick = {
                                            if (item.status == MissionAgentTaskRepository.STATUS_FAILED) {
                                                assignmentRepository.setStatus(item.id, MissionAgentTaskRepository.STATUS_QUEUED)
                                                refreshAssignments()
                                            }
                                            runAssignment(item.copy(status = MissionAgentTaskRepository.STATUS_QUEUED))
                                        }
                                    ) {
                                        Text(
                                            when {
                                                item.status == MissionAgentTaskRepository.STATUS_COMPLETED -> "Completed"
                                                item.status == MissionAgentTaskRepository.STATUS_RUNNING -> "Running"
                                                !earlierTasksCompleted -> "Waiting for earlier task"
                                                item.status == MissionAgentTaskRepository.STATUS_FAILED -> "Retry"
                                                else -> "Run assigned task"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
