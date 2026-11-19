package com.example.ui.screens.missioncontrol

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiRepository
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

private data class MissionPage(val id: String, val title: String, val group: String, val purpose: String)

private val missionControlPages = listOf(
    MissionPage("dashboard", "Dashboard", "COMMAND", "Overview of system status, mission activity and agent workload"),
    MissionPage("features", "Feature Hub", "COMMAND", "Directory of all available Mission Control capabilities"),
    MissionPage("maya", "Maya Chat", "COMMAND", "Open the existing Maya chat assistant"),
    MissionPage("commander", "Mission Commander", "COMMAND", "Execute supported mission creation and specialist assignment commands"),
    MissionPage("missions", "Mission Center", "COMMAND", "Create, inspect, pause and resume missions"),
    MissionPage("agents", "Agent Workforce", "COMMAND", "Specialist agent roster, assignments and status"),
    MissionPage("agentflow", "Workflow Execution", "COMMAND", "Run queued specialist assignments sequentially with saved handoffs"),
    MissionPage("office", "3D AI Agent Office", "COMMAND", "Agent workspace and activity visualization"),
    MissionPage("workspace", "Workspace", "BUILD & CREATE", "Project workspace and working files"),
    MissionPage("deliverables", "My Work & Results", "BUILD & CREATE", "Completed work, deliverables and review history"),
    MissionPage("appfactory", "Android App Factory", "BUILD & CREATE", "Android project creation and build management"),
    MissionPage("termux", "Termux Build Center", "BUILD & CREATE", "Local Termux bridge and Android build commands"),
    MissionPage("webfactory", "Website Factory", "BUILD & CREATE", "Website project generation and management"),
    MissionPage("studio", "Media Studio", "BUILD & CREATE", "Image, audio and video creation tools"),
    MissionPage("production", "Production Studio", "BUILD & CREATE", "Media production pipelines and generated assets"),
    MissionPage("vision", "Maya Vision", "AUTOMATION & INTELLIGENCE", "Choose photos for Gemini image understanding and visual analysis"),
    MissionPage("automation", "Visual Automation", "AUTOMATION & INTELLIGENCE", "Visual workflows and browser automation"),
    MissionPage("voice", "Voice Center", "AUTOMATION & INTELLIGENCE", "Speech input, multilingual responses and voice output"),
    MissionPage("socialcontrol", "Voice & App Control", "AUTOMATION & INTELLIGENCE", "Voice-triggered Android app launch and approved device actions"),
    MissionPage("memory", "Mission Memory", "AUTOMATION & INTELLIGENCE", "Long-term memory and mission context"),
    MissionPage("sources", "Source Intelligence", "AUTOMATION & INTELLIGENCE", "Research sources, evidence and source analysis"),
    MissionPage("marketplaces", "Freelance & Amazon Hub", "AUTOMATION & INTELLIGENCE", "Freelance opportunities, proposals and Amazon workflows"),
    MissionPage("wallet", "Wallet & Withdrawals", "AUTOMATION & INTELLIGENCE", "Earnings ledger, receipts and withdrawal records"),
    MissionPage("analytics", "Analytics", "AUTOMATION & INTELLIGENCE", "Mission, agent and project performance metrics"),
    MissionPage("brain", "AI Brain & Models", "CONTROL", "AI providers, model selection and connection status"),
    MissionPage("security", "Security Center", "CONTROL", "Authentication, permissions and security controls"),
    MissionPage("settings", "Settings", "CONTROL", "Application preferences and integration settings"),
    MissionPage("systemhealth", "System Health", "CONTROL", "Service health, diagnostics and connection checks")
)

@Composable
fun MissionControlScreen(
    aiRepository: AiRepository,
    initialPageRequestId: Int = 0,
    onOpenChat: () -> Unit = {},
    onOpenVoice: () -> Unit = {},
    onOpenTools: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedPageId by remember(initialPageRequestId) { mutableStateOf(if (initialPageRequestId > 0) "appfactory" else "dashboard") }
    var searchQuery by remember { mutableStateOf("") }
    val selectedPage = missionControlPages.first { it.id == selectedPageId }
    if (selectedPageId == "appfactory") {
        AndroidIdeCodingAgentScreen(aiRepository = aiRepository, onBack = { selectedPageId = "dashboard" })
        return
    }
    if (selectedPageId == "commander") {
        MissionCommanderScreen(onBack = { selectedPageId = "dashboard" })
        return
    }
    if (selectedPageId == "agentflow") {
        MissionWorkflowExecutionScreen(onBack = { selectedPageId = "dashboard" })
        return
    }
    if (selectedPageId == "missions") {
        MissionQueueScreen(onBack = { selectedPageId = "dashboard" })
        return
    }
    if (selectedPageId == "agents") {
        MissionAgentWorkforceScreen(onBack = { selectedPageId = "dashboard" })
        return
    }
    if (selectedPageId == "wallet") {
        WalletLedgerScreen(onBack = { selectedPageId = "dashboard" })
        return
    }
    if (selectedPageId == "webfactory") {
        WebsiteFactoryScreen(onBack = { selectedPageId = "dashboard" })
        return
    }
    if (selectedPageId == "vision") {
        MayaVisionScreen(aiRepository = aiRepository, onBack = { selectedPageId = "dashboard" })
        return
    }
    if (selectedPageId == "automation") {
        VisualAutomationScreen(onBack = { selectedPageId = "dashboard" })
        return
    }
    if (selectedPageId == "sources") {
        WebResearchScreen(onBack = { selectedPageId = "dashboard" })
        return
    }
    val query = searchQuery.trim()
    val filteredPages = remember(query) {
        if (query.isBlank()) missionControlPages
        else missionControlPages.filter { page ->
            page.title.contains(query, ignoreCase = true) ||
                page.group.contains(query, ignoreCase = true) ||
                page.purpose.contains(query, ignoreCase = true) ||
                page.id.contains(query, ignoreCase = true)
        }
    }
    val groupedPages = filteredPages.groupBy { it.group }.toList()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Mission Control tabs • ${missionControlPages.size}", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search Mission Control features") },
                placeholder = { Text("Search voice, automation, wallet, agents…") },
                singleLine = true
            )
            Text(
                "${filteredPages.size} of ${missionControlPages.size} tabs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (filteredPages.isEmpty()) {
            item {
                Text(
                    "No matching Mission Control feature. Try another keyword.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(groupedPages) { (group, pages) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                pages.forEach { page ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (page.id) {
                                    "maya" -> onOpenChat()
                                    "voice", "socialcontrol" -> onOpenVoice()
                                    "studio", "production" -> onOpenTools()
                                    "deliverables", "memory" -> onOpenHistory()
                                    "settings", "brain", "security" -> onOpenSettings()
                                    "appfactory", "termux", "workspace", "systemhealth" -> selectedPageId = "appfactory"
                                    else -> selectedPageId = page.id
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedPageId == page.id) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(page.title, style = MaterialTheme.typography.titleSmall)
                                Text(page.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Selected tab: ${selectedPage.title}", style = MaterialTheme.typography.titleMedium)
                    Text(selectedPage.purpose, style = MaterialTheme.typography.bodyMedium)
                    Text("Navigation and screen catalogue transferred. This tab's full data/actions are being ported separately; no live operation is claimed until its API and permissions are connected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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
