package com.example.ui.screens.missioncontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.missioncontrol.MissionAgentTaskRepository
import com.example.missioncontrol.MissionQueueRepository

/** Metrics derived from actual local mission and assignment records only. */
@Composable
fun MissionAnalyticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val missionRepository = remember(context) { MissionQueueRepository(context) }
    val assignmentRepository = remember(context) { MissionAgentTaskRepository(context) }
    val missions = remember { mutableStateListOf<com.example.missioncontrol.MissionTask>() }
    var refreshedAt by remember { mutableStateOf("Not refreshed yet") }

    fun refresh() {
        missions.clear()
        missions.addAll(missionRepository.getAll())
        refreshedAt = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
    }

    LaunchedEffect(missionRepository) { refresh() }
    val assignments = missions.flatMap { assignmentRepository.getForMission(it.id) }
    val queued = assignments.count { it.status == MissionAgentTaskRepository.STATUS_QUEUED }
    val running = assignments.count { it.status == MissionAgentTaskRepository.STATUS_RUNNING }
    val completed = assignments.count { it.status == MissionAgentTaskRepository.STATUS_COMPLETED }
    val failed = assignments.count { it.status == MissionAgentTaskRepository.STATUS_FAILED }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text("Mission Analytics", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Metrics are calculated from local mission and agent-assignment records on this device. API usage, token cost, provider latency and remote worker metrics are not available here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = ::refresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh local metrics") }
        Text("Last refreshed: $refreshedAt", style = MaterialTheme.typography.labelSmall)
        MetricCard("Saved missions", missions.size.toString())
        MetricCard("Agent assignments", assignments.size.toString())
        MetricCard("Queued", queued.toString())
        MetricCard("Running", running.toString())
        MetricCard("Completed", completed.toString())
        MetricCard("Failed", failed.toString())
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
