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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.missioncontrol.MissionWorkflowDefinition
import com.example.missioncontrol.MissionWorkflowDefinitionRepository

/** Creates and persists ordered workflow drafts; execution is deliberately not implied. */
@Composable
fun MissionWorkflowBuilderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { MissionWorkflowDefinitionRepository(context) }
    val workflows = remember { mutableStateListOf<MissionWorkflowDefinition>() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var workflowName by remember { mutableStateOf("") }
    var stepTitle by remember { mutableStateOf("") }
    var stepInstruction by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(MissionWorkflowDefinitionRepository.TYPE_AI_TASK) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Create a workflow draft, then add ordered steps.") }

    fun refresh() {
        workflows.clear()
        workflows.addAll(repository.getAll())
        if (selectedId == null || workflows.none { it.id == selectedId }) selectedId = workflows.firstOrNull()?.id
    }

    LaunchedEffect(repository) { refresh() }
    val selected = workflows.firstOrNull { it.id == selectedId }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Visual Workflow Builder", style = MaterialTheme.typography.titleLarge)
                Text("Local draft editor · ordered steps", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            "Build and save a reusable workflow outline. Drafts are stored on this device. They do not yet execute AI, browser, notification or Android actions; each step type is a definition until its executor is connected.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(value = workflowName, onValueChange = { workflowName = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("New workflow name") }, singleLine = true)
        Button(
            onClick = {
                runCatching { repository.create(workflowName) }
                    .onSuccess { created ->
                        workflowName = ""; error = null; message = "Workflow draft saved."
                        refresh(); selectedId = created.id
                    }
                    .onFailure { error = it.localizedMessage ?: "Could not create workflow." }
            },
            enabled = workflowName.isNotBlank(), modifier = Modifier.fillMaxWidth()
        ) { Text("Create workflow draft") }

        if (workflows.isNotEmpty()) {
            Text("Saved workflows (${workflows.size})", style = MaterialTheme.typography.titleMedium)
            workflows.forEach { workflow ->
                OutlinedButton(onClick = { selectedId = workflow.id; error = null }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (workflow.id == selectedId) "✓ " else "") + workflow.name + " · " + workflow.steps.size + " steps")
                }
            }
        }

        if (selected != null) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selected.name, style = MaterialTheme.typography.titleMedium)
                    Text("DRAFT · ${selected.steps.size}/40 steps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Step type: ${selectedType.replace('_', ' ')}")
                    }
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        listOf(
                            MissionWorkflowDefinitionRepository.TYPE_TRIGGER,
                            MissionWorkflowDefinitionRepository.TYPE_AI_TASK,
                            MissionWorkflowDefinitionRepository.TYPE_CONDITION,
                            MissionWorkflowDefinitionRepository.TYPE_APPROVAL,
                            MissionWorkflowDefinitionRepository.TYPE_NOTIFICATION
                        ).forEach { type ->
                            DropdownMenuItem(text = { Text(type.replace('_', ' ')) }, onClick = { selectedType = type; typeMenuExpanded = false })
                        }
                    }
                    OutlinedTextField(value = stepTitle, onValueChange = { stepTitle = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Step title") }, singleLine = true)
                    OutlinedTextField(value = stepInstruction, onValueChange = { stepInstruction = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Step instructions / configuration") }, minLines = 2)
                    Button(
                        onClick = {
                            runCatching { repository.addStep(selected.id, selectedType, stepTitle, stepInstruction) }
                                .onSuccess { updated ->
                                    stepTitle = ""; stepInstruction = ""; error = null; message = "Step saved to workflow draft."
                                    refresh(); selectedId = updated.id
                                }
                                .onFailure { error = it.localizedMessage ?: "Could not add workflow step." }
                        },
                        enabled = stepTitle.isNotBlank() && selected.steps.size < 40, modifier = Modifier.fillMaxWidth()
                    ) { Text("Add step") }
                    if (selected.steps.isEmpty()) Text("No steps yet. Add a trigger, AI task, condition, approval or notification step.")
                    selected.steps.forEachIndexed { index, step ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${index + 1}. ${step.title}", style = MaterialTheme.typography.titleSmall)
                                Text(step.type.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                if (step.instruction.isNotBlank()) Text(step.instruction, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = {
                                    repository.removeStep(selected.id, step.id); refresh(); selectedId = selected.id
                                }) { Text("Remove step") }
                            }
                        }
                    }
                }
            }
        } else Text("Create or select a workflow to add steps.")

        Text(message, style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
