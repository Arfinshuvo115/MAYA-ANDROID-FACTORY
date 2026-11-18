package com.example.ui.screens.missioncontrol

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.MayaPersona
import com.example.domain.model.ResponseLength
import com.example.missioncontrol.android.MayaAccessibilityService
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * First stage of Maya's on-device Android IDE bridge:
 * - user chooses a project folder through Android's Storage Access Framework;
 * - user can create/open/edit/save source files with the system document picker;
 * - Maya can launch a supported IDE already installed on the device.
 *
 * No broad storage permission, hidden package enumeration, or background file access.
 */
@Composable
fun AndroidIdeCodingAgentScreen(aiRepository: AiRepository, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var projectTreeUri by rememberSaveable { mutableStateOf<String?>(null) }
    var fileUri by rememberSaveable { mutableStateOf<String?>(null) }
    var fileName by rememberSaveable { mutableStateOf("") }
    var codeText by rememberSaveable { mutableStateOf("") }
    var newFileName by rememberSaveable { mutableStateOf("MainActivity.kt") }
    var changeRequest by rememberSaveable { mutableStateOf("") }
    var isGenerating by rememberSaveable { mutableStateOf(false) }
    var visibleControlText by rememberSaveable { mutableStateOf("") }
    var visibleFieldLabel by rememberSaveable { mutableStateOf("") }
    var visibleFieldValue by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("Choose your Android project folder to begin.") }

    val chooseFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            projectTreeUri = uri.toString()
            status = "Project folder connected. Maya can create files here and edit files you open."
        }
    }

    val openSourceFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            try {
                codeText = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                fileUri = uri.toString()
                fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Source file"
                status = "Opened $fileName. Edit the code and press Save changes."
            } catch (error: Exception) {
                status = "Could not read this file: ${error.localizedMessage ?: "unknown error"}"
            }
        }
    }

    val createSourceFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(codeText.toByteArray(Charsets.UTF_8))
                } ?: throw IOException("No writable output stream was provided.")
                fileUri = uri.toString()
                fileName = uri.lastPathSegment?.substringAfterLast('/') ?: newFileName
                status = "Created $fileName."
            } catch (error: Exception) {
                status = "Could not create file: ${error.localizedMessage ?: "unknown error"}"
            }
        }
    }

    fun saveCurrentFile() {
        val target = fileUri?.let(Uri::parse)
        if (target == null) {
            status = "Open a source file first, or create a new file."
            return
        }
        try {
            context.contentResolver.openOutputStream(target, "wt")?.use {
                it.write(codeText.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("No writable output stream was provided.")
            status = "Saved changes to $fileName."
        } catch (error: Exception) {
            status = "Save failed: ${error.localizedMessage ?: "unknown error"}"
        }
    }

    fun createInProjectFolder() {
        val tree = projectTreeUri?.let(Uri::parse)
        if (tree == null) {
            status = "Choose a project folder first."
            return
        }
        val cleanName = newFileName.trim()
        if (cleanName.isBlank() || cleanName.contains('/') || cleanName.contains('\\')) {
            status = "Enter a valid file name without folder separators."
            return
        }
        try {
            val documentId = DocumentsContract.getTreeDocumentId(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
            val created = DocumentsContract.createDocument(
                context.contentResolver,
                parent,
                "text/plain",
                cleanName
            ) ?: throw IOException("The selected folder did not create the file.")
            context.contentResolver.openOutputStream(created, "wt")?.use {
                it.write(codeText.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("No writable output stream was provided.")
            fileUri = created.toString()
            fileName = cleanName
            status = "Created $cleanName in the selected project folder."
        } catch (error: Exception) {
            status = "File creation failed: ${error.localizedMessage ?: "unknown error"}"
        }
    }

    fun launchInstalledIde() {
        val candidates = listOf(
            "com.m.androidide",
            "com.itsaky.androidide",
            "com.aide.ui",
            "com.tyron.code",
            "com.termux"
        )
        val match = candidates.firstNotNullOfOrNull { packageName ->
            context.packageManager.getLaunchIntentForPackage(packageName)
        }
        if (match == null) {
            status = "No supported IDE was found. Install/open your preferred Android IDE, then return here."
        } else {
            match.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(match)
            status = "Opened an installed development environment."
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back to Mission Control") }
        }
        Text("Android IDE Coding Agent", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Connect a project folder, open source files, edit code, create files and save changes. Maya uses Android's system document permission; she cannot silently browse the whole phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Connect project", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (projectTreeUri == null) "No project folder selected."
                    else "Project folder permission granted for this session: ${Uri.parse(projectTreeUri).lastPathSegment ?: "Selected folder"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { chooseFolder.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose Android project folder")
                }
                OutlinedButton(onClick = ::launchInstalledIde, modifier = Modifier.fillMaxWidth()) {
                    Text("Open installed Android IDE")
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Create or open source code", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("New file name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::createInProjectFolder, enabled = projectTreeUri != null) {
                        Text("Create in project")
                    }
                    OutlinedButton(onClick = { createSourceFile.launch(newFileName.trim().ifBlank { "NewFile.txt" }) }) {
                        Text("Create with picker")
                    }
                }
                OutlinedButton(
                    onClick = { openSourceFile.launch(arrayOf("text/*", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open existing source file")
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Maya AI code assistant", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = changeRequest, onValueChange = { changeRequest = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Describe the code change") }, minLines = 3)
                Button(onClick = {
                    val request = changeRequest.trim()
                    if (request.isBlank()) status = "Describe the code change first."
                    else if (fileUri == null) status = "Open the source file first."
                    else if (codeText.length > 60000) status = "File too large for one safe AI edit."
                    else {
                        isGenerating = true
                        val prompt = "You are Maya, a careful Android engineer. User request: " + request + "\nFile: " + fileName + "\nCurrent file:\n" + codeText + "\nReturn ONLY the complete revised file. No Markdown fences. Preserve package, imports, existing features and unrelated behavior. Make the smallest correct change. Never claim tests were run."
                        coroutineScope.launch {
                            val generated = StringBuilder()
                            try {
                                aiRepository.streamChatReply(prompt, MayaPersona.CODER, emptyList(), null, ResponseLength.DETAILED, if (request.any { it.code in 2432..2559 }) AppLanguage.BANGLA else AppLanguage.ENGLISH).collect { generated.append(it) }
                                if (generated.isNotBlank()) { codeText = generated.toString().trim(); status = "AI edit prepared. Review it, then tap Save changes." }
                                else status = "AI returned no code; editor unchanged."
                            } catch (error: Exception) { status = "AI edit failed: " + (error.localizedMessage ?: "unknown error") }
                            finally { isGenerating = false }
                        }
                    }
                }, enabled = !isGenerating, modifier = Modifier.fillMaxWidth()) { Text(if (isGenerating) "Maya is coding..." else "Ask Maya to edit this file") }
                Text("AI edits only the unsaved editor buffer. Nothing is written until you review and tap Save changes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("4. Approved IDE screen controls", style = MaterialTheme.typography.titleMedium)
                Text("When Maya Accessibility is enabled in Android Settings, these buttons can interact with the visible IDE screen. Each action requires your direct tap here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = visibleControlText, onValueChange = { visibleControlText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Visible button or control text") }, singleLine = true)
                OutlinedButton(onClick = {
                    val service = MayaAccessibilityService.active
                    status = when { service == null -> "Enable Maya Accessibility in Android Settings and return to the IDE first."
                        service.tapVisibleText(visibleControlText) -> "Tapped the visible control: " + visibleControlText
                        else -> "Could not find a visible clickable control with that text." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Tap visible IDE control") }
                OutlinedTextField(value = visibleFieldLabel, onValueChange = { visibleFieldLabel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Editor field label or hint") }, singleLine = true)
                OutlinedTextField(value = visibleFieldValue, onValueChange = { visibleFieldValue = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Text to enter") }, minLines = 2)
                Button(onClick = {
                    val service = MayaAccessibilityService.active
                    status = when { service == null -> "Enable Maya Accessibility in Android Settings and return to the IDE first."
                        service.setTextInVisibleField(visibleFieldLabel, visibleFieldValue) -> "Entered text into the matched visible field."
                        else -> "Could not find a matching visible editable field." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Enter text in visible IDE field") }
                Text("5. Code editor", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (fileUri == null) "No file open" else "Editing: $fileName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
                    label = { Text("Source code") },
                    placeholder = { Text("Open a Kotlin, Java, XML, Gradle or text file…") },
                    minLines = 12
                )
                Button(
                    onClick = ::saveCurrentFile,
                    enabled = fileUri != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save changes")
                }
            }
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Text(
            "Current stage: project-folder access, source editing, Gemini-backed single-file suggestions and user-triggered visible IDE control/text entry. Project-wide analysis, autonomous Gradle execution and Logcat repair remain future stages.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
