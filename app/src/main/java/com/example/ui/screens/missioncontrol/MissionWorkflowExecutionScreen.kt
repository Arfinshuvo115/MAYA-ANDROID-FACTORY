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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.missioncontrol.MissionAgentMemoryRepository
import com.example.missioncontrol.MissionAgentPipeline
import com.example.missioncontrol.MissionAgentRoster
import com.example.missioncontrol.MissionAgentTaskRepository
import com.example.missioncontrol.MissionAgentWorkItem
import com.example.missioncontrol.MissionQueueRepository
import com.example.missioncontrol.MissionTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Explicit sequential execution for queued specialist assignments of one mission.
 * Stops at the first failed assignment and never starts background work.
 */
@Composable
fun MissionWorkflowExecutionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val missionRepository = remember(context) { MissionQueueRepository(context) }
    val assignmentRepository = remember(context) { MissionAgentTaskRepository(context) }
    val memoryRepository = remember(context) { MissionAgentMemoryRepository(context) }
    val pipeline = remember(context) { MissionAgentPipeline(context) }
    val missions = remember { mutableStateListOf<MissionTask>() }
    val assignments = remember { mutableStateListOf<MissionAgentWorkItem>() }
    val scope = rememberCoroutineScope()

    var selectedMissionId by remember { mutableStateOf<String?>(null) }
    var missionMenuExpanded by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var completedInRun by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("Choose a mission to review its queued specialist workflow.") }
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

    LaunchedEffect(missionRepository) { refreshMissions() }
    LaunchedEffect(selectedMissionId) { refreshAssignments() }

    val selectedMission = missions.firstOrNull { it.id == selectedMissionId }
    val queued = assignments.filter { it.status == MissionAgentTaskRepository.STATUS_QUEUED }
    val runningItem = assignments.firstOrNull { it.status == MissionAgentTaskRepository.STATUS_RUNNING }
    val completed = assignments.count { it.status == MissionAgentTaskRepository.STATUS_COMPLETED }
    val failed = assignments.count { it.status == MissionAgentTaskRepository.STATUS_FAILED }

    fun runWorkflow() {
        val mission = selectedMission ?: run {
            error = "Create or select a mission first."
            return
        }
        if (running) return
        val work = assignmentRepository.getForMission(mission.id)
            .filter { it.status == MissionAgentTaskRepository.STATUS_QUEUED }
            .sortedBy { it.createdAtMillis }
        if (work.isEmpty()) {
            message = "No QUEUED assignments. Add tasks in Task Assignment & Coordination; failed tasks can be retried there."
            return
        }
        error = null
        completedInRun = 0
        running = true
        message = "Starting sequential workflow: \${work.size} queued specialist task(s)."
        scope.launch {
            try {
                for (item in work) {
                    val agent = MissionAgentRoster.agents.firstOrNull { it.id == item.agentId }
                    if (agent == null) {
                        assignmentRepository.fail(item.id, "Assigned specialist is not in the current roster.")
                        error = "Unknown specialist for task: \${item.title}"
                        message = "Workflow stopped. Review the failed assignment before continuing."
                        break
                    }
                    assignmentRepository.setStatus(item.id, MissionAgentTaskRepository.STATUS_RUNNING)
                    refreshAssignments()
                    message = "Running \${agent.displayName}: \${item.title}"
                    try {
                        val output = pipeline.runAssignedTask(
                            mission = mission,
                            agent = agent,
                            assignmentTitle = item.title,
                            assignmentInstructions = item.instructions,
                            previousHandoff = assignmentRepository.previousHandoffs(item),
                            sharedMemoryContext = memoryRepository.contextFor(item)
                        )
                        check(assignmentRepository.complete(item.id, output)) {
                            "The task output was generated but its completion state could not be saved."
                        }
                        memoryRepository.rememberCompletedHandoff(item, output)
                        completedInRun += 1
                        refreshAssignments()
                        message = "Completed \${agent.displayName}. Handoff saved; continuing to the next queued specialist."
                    } catch (cancelled: CancellationException) {
                        assignmentRepository.setStatus(item.id, MissionAgentTaskRepository.STATUS_QUEUED)
                        refreshAssignments()
                        message = "Workflow cancelled. The current task was returned to QUEUED; completed handoffs remain saved."
                        throw cancelled
                    } catch (failure: Exception) {
                        assignmentRepository.fail(item.id, failure.localizedMessage ?: "Specialist task failed.")
                        error = failure.localizedMessage ?: "Specialist task failed."
                        message = "Workflow stopped at \${agent.displayName}. Later tasks remain QUEUED and were not started."
                        refreshAssignments()
                        break
                    }
                }
                if (error == null) {
                    message = "Workflow finished: $completedInRun of \${work.size} queued task(s) completed. Review each handoff below."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                running = false
                refreshAssignments()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, enabled = !running) { Text("Back") }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Workflow Execution", style = MaterialTheme.typography.titleLarge)
                Text("Sequential specialist queue · explicit run", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(
            "This workflow runs only QUEUED assignments for the selected mission, in creation order. Each successful handoff is saved and passed as context to later specialists. The workflow stops on the first failure; remaining tasks stay queued. No remote workers, background execution, file edits or external actions are implied.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedButton(
            onClick = { refreshMissions(); refreshAssignments() },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Refresh missions and assignments") }

        if (missions.isEmpty()) {
            Text("No mission found. Create one in Mission Center, then add specialist assignments.")
        } else {
            OutlinedButton(
                onClick = { missionMenuExpanded = true },
                enabled = !running,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Mission: \${selectedMission?.title ?: "Select mission"}") }
            DropdownMenu(
                expanded = missionMenuExpanded,
                onDismissRequest = { missionMenuExpanded = false }
            ) {
                missions.forEach { mission ->
                    DropdownMenuItem(
                        text = { Text(mission.title) },
                        onClick = {
                            selectedMissionId = mission.id
                            missionMenuExpanded = false
                            error = null
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Queued \${queued.size}", style = MaterialTheme.typography.labelMedium)
            Text("Running \${if (runningItem != null || running) 1 else 0}", style = MaterialTheme.typography.labelMedium)
            Text("Done $completed", style = MaterialTheme.typography.labelMedium)
            Text("Failed $failed", style = MaterialTheme.typography.labelMedium)
        }

        Button(
            onClick = ::runWorkflow,
            enabled = selectedMission != null && queued.isNotEmpty() && !running,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (running) "Workflow running…" else "Run queued workflow (\${queued.size})") }

        Text(message, style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Text("Assignment order and results", style = MaterialTheme.typography.titleMedium)
        if (assignments.isEmpty()) {
            Text("No assignments for this mission. Add them from Agent Workforce → Task Assignment & Coordination.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(assignments, key = { it.id }) { item ->
                    val agentName = MissionAgentRoster.agents.firstOrNull { it.id == item.agentId }?.displayName ?: item.agentId
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(agentName, style = MaterialTheme.typography.labelMedium)
                            Text("Status: \${item.status}", style = MaterialTheme.typography.bodySmall)
                            if (item.lastError.isNotBlank()) {
                                Text(item.lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            if (item.handoff.isNotBlank()) {
                                Text("Saved handoff", style = MaterialTheme.typography.labelMedium)
                                Text(item.handoff, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        Text(
            "Gemini API key is required to run specialist tasks. Each assignment uses the configured model; this is sequential role orchestration, not sixteen independent hosted agents.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
