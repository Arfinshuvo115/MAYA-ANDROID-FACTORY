package com.example.ui.screens.missioncontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.missioncontrol.MissionAgentMemoryItem
import com.example.missioncontrol.MissionAgentMemoryRepository
import com.example.missioncontrol.MissionQueueRepository

/** Read and explicitly delete mission-scoped handoff memories saved on this device. */
@Composable
fun MissionMemoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val missionsRepository = remember(context) { MissionQueueRepository(context) }
    val memoryRepository = remember(context) { MissionAgentMemoryRepository(context) }
    val missions = remember { mutableStateListOf<com.example.missioncontrol.MissionTask>() }
    val memories = remember { mutableStateListOf<MissionAgentMemoryItem>() }
    var selectedMissionId by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    fun refresh() {
        missions.clear()
        missions.addAll(missionsRepository.getAll())
        if (selectedMissionId == null || missions.none { it.id == selectedMissionId }) selectedMissionId = missions.firstOrNull()?.id
        memories.clear()
        selectedMissionId?.let { memories.addAll(memoryRepository.getForMission(it)) }
    }

    LaunchedEffect(memoryRepository) { refresh() }
    LaunchedEffect(selectedMissionId) {
        memories.clear()
        selectedMissionId?.let { memories.addAll(memoryRepository.getForMission(it)) }
    }
    val selectedMission = missions.firstOrNull { it.id == selectedMissionId }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text("Mission Memory", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "This screen shows only completed specialist handoffs stored locally for each mission. It is not encrypted cloud memory or a general personal-memory system.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (missions.isEmpty()) {
            Text("No missions have been saved yet.")
        } else {
            OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedMission?.title ?: "Choose mission")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                missions.forEach { mission ->
                    DropdownMenuItem(
                        text = { Text(mission.title) },
                        onClick = { selectedMissionId = mission.id; menuExpanded = false }
                    )
                }
            }
        }
        Text("Saved handoffs: ${memories.size}", style = MaterialTheme.typography.titleMedium)
        if (memories.isEmpty() && selectedMission != null) Text("No completed handoffs are saved for this mission.")
        memories.forEach { memory ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(memory.sourceTaskTitle, style = MaterialTheme.typography.titleSmall)
                    Text("Agent: ${memory.sourceAgentId} · ${memory.tags.joinToString()}", style = MaterialTheme.typography.labelSmall)
                    Text(memory.content, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        memoryRepository.remove(memory.id, memory.missionId)
                        refresh()
                    }) { Text("Delete this memory") }
                }
            }
        }
    }
}
