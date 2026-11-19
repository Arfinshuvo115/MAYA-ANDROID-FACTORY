package com.example.ui.screens.missioncontrol

import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.missioncontrol.android.MayaAccessibilityService

@Composable
fun VisualAutomationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { mutableStateListOf<String>() }
    var selectedControl by remember { mutableStateOf<String?>(null) }
    var textToEnter by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Press Inspect to read visible controls from the foreground app.") }
    var websiteAddress by remember { mutableStateOf("") }
    var pendingWebsite by remember { mutableStateOf<String?>(null) }

    fun inspect() {
        val service = MayaAccessibilityService.active
        if (service == null) {
            controls.clear()
            status = "Maya Accessibility Service is not enabled. Enable it in Android Accessibility settings, then return here."
            return
        }
        controls.clear()
        controls.addAll(service.getVisibleControlLabels())
        status = if (controls.isEmpty()) {
            "No visible clickable or editable controls were found in the foreground app."
        } else {
            "Found ${controls.size} visible control labels. Select one to prepare an action."
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Column {
                Text("Visual Automation", style = MaterialTheme.typography.titleLarge)
                Text("User-approved Android accessibility actions", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            "This first connector reads the visible Android accessibility tree. It is not screenshot/OCR vision and does not run hidden background actions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Browser Workspace", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = websiteAddress,
            onValueChange = { websiteAddress = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Website URL") },
            placeholder = { Text("https://example.com") },
            singleLine = true
        )
        OutlinedButton(
            onClick = {
                val raw = websiteAddress.trim()
                val normalized = if (raw.startsWith("https://", true) || raw.startsWith("http://", true)) raw else "https://$raw"
                val parsed = runCatching { Uri.parse(normalized) }.getOrNull()
                if (parsed != null && parsed.scheme in listOf("https", "http") && !parsed.host.isNullOrBlank() && parsed.userInfo == null) {
                    pendingWebsite = parsed.toString()
                } else {
                    status = "Enter a valid website address. Only HTTP and HTTPS links are allowed."
                }
            },
            enabled = websiteAddress.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Review & Open Website") }
        Text(
            "The website opens in your chosen browser. Maya does not log in, submit forms, or navigate pages automatically. After opening, return here and use Inspect to review accessible browser controls.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { inspect() }, modifier = Modifier.fillMaxWidth()) {
            Text("Inspect Visible App Controls")
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        if (MayaAccessibilityService.active == null) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { status = "Could not open Android Accessibility settings: ${it.message}" }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Android Accessibility Settings") }
        }
        if (controls.isNotEmpty()) {
            Text("Visible controls · ${controls.size}", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(controls, key = { it }) { label ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedControl == label) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    selectedControl = label
                                    pendingAction = "tap"
                                }) { Text("Prepare Tap") }
                                OutlinedButton(onClick = {
                                    selectedControl = label
                                    textToEnter = ""
                                    pendingAction = "text"
                                }) { Text("Prepare Text Entry") }
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                "No controls listed yet. Open the target app, return to Maya, and press Inspect.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "Safety: Maya only acts after you select a specific visible label and confirm the exact action. The service does not record or upload screen content.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (pendingWebsite != null) {
        AlertDialog(
            onDismissRequest = { pendingWebsite = null },
            title = { Text("Open website?") },
            text = { Text("Open this address in your browser?\\n\\n$pendingWebsite") },
            confirmButton = {
                TextButton(onClick = {
                    val target = pendingWebsite.orEmpty()
                    val opened = runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                    }.isSuccess
                    status = if (opened) "Browser launch requested for $target." else "Could not open this website. Check that a browser is installed."
                    pendingWebsite = null
                }) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { pendingWebsite = null }) { Text("Cancel") } }
        )
    }

    if (pendingAction == "tap" && selectedControl != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("Confirm tap") },
            text = { Text("Tap the visible control labelled “$selectedControl” in the current foreground app?") },
            confirmButton = {
                TextButton(onClick = {
                    val label = selectedControl.orEmpty()
                    val result = MayaAccessibilityService.active?.tapVisibleText(label) == true
                    status = if (result) "Tap action sent to “$label”." else "Tap failed. The control may have changed or may no longer be visible."
                    pendingAction = null
                    inspect()
                }) { Text("Confirm Tap") }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } }
        )
    }

    if (pendingAction == "text" && selectedControl != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("Confirm text entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter text into the visible editable control “$selectedControl”.")
                    OutlinedTextField(
                        value = textToEnter,
                        onValueChange = { textToEnter = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Text to enter") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = textToEnter.isNotEmpty(),
                    onClick = {
                        val label = selectedControl.orEmpty()
                        val result = MayaAccessibilityService.active?.setTextInVisibleField(label, textToEnter) == true
                        status = if (result) "Text entry action sent to “$label”." else "Text entry failed. The field may have changed or may no longer be visible."
                        pendingAction = null
                        textToEnter = ""
                        inspect()
                    }
                ) { Text("Confirm Entry") }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } }
        )
    }
}
