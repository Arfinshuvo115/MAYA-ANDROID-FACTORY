package com.example.ui.screens.missioncontrol

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Visual launcher for existing Mission Control modules.
 * The artwork is decorative; module actions continue through their existing screens.
 */
@Composable
fun MissionVisualDashboard(
    onOpenPage: (String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101B35)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.img_maya_hero),
                    contentDescription = "Maya AI futuristic hero artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xEE081326), Color(0x77202A55), Color(0x22101830))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "MAYA // AI COMMAND DECK",
                        color = Color(0xFF7DEBFF),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "One command center.\nConnected tools.",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Open a module to run its existing workflow",
                        color = Color(0xFFD7E4FF),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Text(
            "VISUAL MODULES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VisualModuleCard(
                icon = "🧠",
                title = "Maya AI Core",
                detail = "Chat assistant",
                accent = Color(0xFF4DD0E1),
                modifier = Modifier.weight(1f),
                onClick = onOpenChat
            )
            VisualModuleCard(
                icon = "🤖",
                title = "Agent Squad",
                detail = "Workforce pipeline",
                accent = Color(0xFF9C8CFF),
                modifier = Modifier.weight(1f),
                onClick = { onOpenPage("agents") }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VisualModuleCard(
                icon = "💻",
                title = "Coding Lab",
                detail = "Android project builder",
                accent = Color(0xFF64B5F6),
                modifier = Modifier.weight(1f),
                onClick = { onOpenPage("appfactory") }
            )
            VisualModuleCard(
                icon = "👁️",
                title = "Maya Vision",
                detail = "Image understanding",
                accent = Color(0xFF80CBC4),
                modifier = Modifier.weight(1f),
                onClick = { onOpenPage("vision") }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VisualModuleCard(
                icon = "⚙️",
                title = "Automation",
                detail = "Visual workflows",
                accent = Color(0xFFFFB74D),
                modifier = Modifier.weight(1f),
                onClick = { onOpenPage("automation") }
            )
            VisualModuleCard(
                icon = "🌐",
                title = "Website Factory",
                detail = "HTML builder",
                accent = Color(0xFFBA9CFF),
                modifier = Modifier.weight(1f),
                onClick = { onOpenPage("webfactory") }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VisualModuleCard(
                icon = "🎬",
                title = "Media Studio",
                detail = "Open available media tools",
                accent = Color(0xFFF48FB1),
                modifier = Modifier.weight(1f),
                onClick = onOpenTools
            )
            VisualModuleCard(
                icon = "🛡️",
                title = "Security & Settings",
                detail = "Keys and permissions",
                accent = Color(0xFFA5D6A7),
                modifier = Modifier.weight(1f),
                onClick = { onOpenPage("settings") }
            )
        }

        Text(
            "Visuals are launchers, not proof of live services. Each module reports its real connection, permission and execution state inside its own screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VisualModuleCard(
    icon: String,
    title: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(116.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, style = MaterialTheme.typography.titleLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "OPEN MODULE",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
