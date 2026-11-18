package com.example.ui.screens.missioncontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.JarvisSectionHeader

private data class AgentRole(val name: String, val responsibility: String)

private val missionControlAgents = listOf(
    AgentRole("Planner Agent", "Plans phases, dependencies and priorities"),
    AgentRole("Researcher Agent", "Research and fact analysis"),
    AgentRole("Developer Agent", "Application and software development"),
    AgentRole("Backend Agent", "Backend, API and database engineering"),
    AgentRole("UI/UX Designer Agent", "User experience and visual design"),
    AgentRole("QA Tester Agent", "Quality assurance and testing"),
    AgentRole("Reviewer Agent", "Independent review and delivery acceptance"),
    AgentRole("Integration Agent", "API and workflow integrations"),
    AgentRole("Freelance Job Hunter", "Freelance marketplace opportunity research"),
    AgentRole("Freelance Market Analyst", "Demand and pricing analysis"),
    AgentRole("Client Proposal Agent", "Client proposals and bids"),
    AgentRole("Freelance Work Executor", "Approved freelance project execution"),
    AgentRole("Client Delivery & QA", "Client delivery quality checks"),
    AgentRole("Earnings & Invoice Agent", "Earnings and invoice tracking"),
    AgentRole("Amazon Associates Agent", "Affiliate marketing workflows"),
    AgentRole("Amazon Seller Services Agent", "Amazon Seller Central workflows")
)

private val missionControlModules = listOf(
    "Commander & mission queue",
    "Agent workflows & handoffs",
    "Browser automation",
    "Vision analysis",
    "Image, audio & video studio",
    "Android project builder",
    "Website project builder",
    "Long-term memory",
    "Wallet & payment ledger",
    "Freelance marketplace hub",
    "Notifications & office routines",
    "n8n workflow integration"
)

@Composable
fun MissionControlScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            JarvisSectionHeader(
                telemetryCode = "MISSION CONTROL // WORKFORCE",
                title = "Maya Mission Control",
                subtitle = "Agent roster and capability map inside Maya Android Factory"
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connection not authenticated", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The 16 roles below are the Mission Control roster catalogue. Live agent status, mission submission and results are not yet connected. Mission Control's endpoints require an authenticated admin session; this screen does not pretend a local list is live server data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Text("16 specialist agents", style = MaterialTheme.typography.titleLarge)
        }
        items(missionControlAgents) { agent ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(agent.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            agent.responsibility,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Text("Mission Control modules", style = MaterialTheme.typography.titleLarge)
        }
        items(missionControlModules) { module ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Text(module, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Text(
                "Integration status: catalogue UI added. Authenticated API connection, live agent execution, media providers, marketplace accounts and end-to-end tests remain pending.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
