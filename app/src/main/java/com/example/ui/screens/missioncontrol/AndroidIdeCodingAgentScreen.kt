package com.example.ui.screens.missioncontrol

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.example.missioncontrol.android.TermuxBuildBridge
import com.example.missioncontrol.android.TermuxBuildState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.IOException


private data class ProjectSourceEntry(val name: String, val relativePath: String, val uri: Uri, val isDirectory: Boolean)

private data class BuildDiagnosticLocation(
    val path: String,
    val line: Int,
    val column: Int?,
    val message: String
)

private fun findBuildDiagnostic(output: String): BuildDiagnosticLocation? {
    val locationPattern = Regex("""(?:^|[\s(])((?:[A-Za-z]:)?[^\s():]+?\.(?:kt|java|xml|kts|gradle)):(\d+)(?::(\d+))?""")
    for (line in output.lineSequence()) {
        val match = locationPattern.find(line) ?: continue
        val rawPath = match.groupValues[1].removePrefix("file://").replace('\\', '/')
        return BuildDiagnosticLocation(
            path = rawPath,
            line = match.groupValues[2].toIntOrNull() ?: 1,
            column = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull(),
            message = line.trim().take(500)
        )
    }
    return null
}

private fun readProjectTree(context: Context, treeUri: Uri, maxEntries: Int = 250): List<ProjectSourceEntry> {
    val entries = mutableListOf<ProjectSourceEntry>()
    val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    val pending = ArrayDeque<Pair<String, String>>()
    pending.add(rootId to "")
    var visitedDirectories = 0
    while (pending.isNotEmpty() && entries.size < maxEntries && visitedDirectories < 40) {
        val (parentId, parentPath) = pending.removeFirst()
        visitedDirectories++
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val cursor = context.contentResolver.query(childrenUri, projection, null, null, null) ?: continue
        cursor.use {
            val idColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (it.moveToNext() && entries.size < maxEntries) {
                if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) continue
                val id = it.getString(idColumn) ?: continue
                val name = it.getString(nameColumn) ?: continue
                val mime = it.getString(mimeColumn) ?: "application/octet-stream"
                val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val relativePath = if (parentPath.isBlank()) name else "$parentPath/$name"
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                entries += ProjectSourceEntry(name, relativePath, childUri, isDirectory)
                if (isDirectory && pending.size < 40) pending.add(id to relativePath)
            }
        }
    }
    return entries.sortedWith(compareBy<ProjectSourceEntry> { !it.isDirectory }.thenBy { it.relativePath.lowercase() })
}

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
    val latestBuildOutput by TermuxBuildState.latest.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var projectTreeUri by rememberSaveable { mutableStateOf<String?>(null) }
    val projectFiles = remember { mutableStateListOf<ProjectSourceEntry>() }
    var fileUri by rememberSaveable { mutableStateOf<String?>(null) }
    var fileName by rememberSaveable { mutableStateOf("") }
    var codeText by rememberSaveable { mutableStateOf("") }
    var newFileName by rememberSaveable { mutableStateOf("MainActivity.kt") }
    var changeRequest by rememberSaveable { mutableStateOf("") }
    var isGenerating by rememberSaveable { mutableStateOf(false) }
    var patchText by rememberSaveable { mutableStateOf("") }
    var patchFileName by rememberSaveable { mutableStateOf("") }
    var visibleControlText by rememberSaveable { mutableStateOf("") }
    var visibleFieldLabel by rememberSaveable { mutableStateOf("") }
    var visibleFieldValue by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("Choose your Android project folder to begin.") }
    var detectedErrorFile by rememberSaveable { mutableStateOf("") }
    var detectedErrorLine by rememberSaveable { mutableStateOf(0) }

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
            runCatching { projectFiles.clear(); projectFiles.addAll(readProjectTree(context, uri)) }
            status = "Project folder connected. Found " + projectFiles.size + " entries."
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


    fun openDetectedBuildErrorFile() {
        val tree = projectTreeUri?.let(Uri::parse)
        val diagnosticPath = detectedErrorFile.replace('\\\\', '/').trim()
        if (tree == null || diagnosticPath.isBlank()) {
            status = "Choose the project folder and capture a build diagnostic first."
            return
        }
        val match = projectFiles.firstOrNull { entry ->
            !entry.isDirectory && (
                diagnosticPath.endsWith(entry.relativePath, ignoreCase = true) ||
                entry.relativePath.endsWith(diagnosticPath.substringAfterLast('/'), ignoreCase = true)
            )
        }
        if (match == null) {
            status = "Build reported $diagnosticPath, but that file is not in the indexed project tree. Refresh the tree or open it with the file picker."
            return
        }
        runCatching {
            val source = context.contentResolver.openInputStream(match.uri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IOException("Could not read the diagnostic file.")
            if (source.length > 200000) throw IOException("Diagnostic file exceeds the 200 KB editor limit.")
            codeText = source
            fileUri = match.uri.toString()
            fileName = match.relativePath
            status = "Opened ${match.relativePath} at reported line $detectedErrorLine. Review that line before asking Maya to edit."
        }.onFailure { status = "Could not open diagnostic file: " + (it.localizedMessage ?: "unknown error") }
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
                Text("2. Project file explorer", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val tree = projectTreeUri?.let(Uri::parse)
                        if (tree == null) status = "Choose a project folder first."
                        else runCatching {
                            projectFiles.clear()
                            projectFiles.addAll(readProjectTree(context, tree))
                            status = "Refreshed project index: " + projectFiles.size + " entries."
                        }.onFailure { status = "Project scan failed: " + (it.localizedMessage ?: "unknown error") }
                    }, enabled = projectTreeUri != null) { Text("Refresh file tree") }
                    Text("${projectFiles.size} files/folders", style = MaterialTheme.typography.labelMedium)
                }
                if (projectFiles.isEmpty()) {
                    Text("No indexed files yet. Connect a project folder and refresh.", style = MaterialTheme.typography.bodySmall)
                } else {
                    projectFiles.take(250).forEach { entry ->
                        OutlinedButton(onClick = {
                            if (entry.isDirectory) status = "Folder: " + entry.relativePath
                            else runCatching {
                                val source = context.contentResolver.openInputStream(entry.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                                    ?: throw IOException("Could not read the selected file.")
                                if (source.length > 200000) throw IOException("File exceeds the 200 KB safe editor limit.")
                                codeText = source
                                fileUri = entry.uri.toString()
                                fileName = entry.relativePath
                                status = "Opened " + entry.relativePath
                            }.onFailure { status = "Open failed: " + (it.localizedMessage ?: "unknown error") }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (entry.isDirectory) "📁 " else "📄 ") + entry.relativePath)
                        }
                    }
                }
            }
        }


        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Gradle debug build via Termux", style = MaterialTheme.typography.titleMedium)
                val buildRequest = projectTreeUri?.let(Uri::parse)?.let(TermuxBuildBridge::resolveSharedStorageProject)
                Text(
                    when {
                        projectTreeUri == null -> "Choose a project folder first."
                        buildRequest == null -> "The selected folder is not a supported shared-storage path. Termux cannot access app-private IDE folders through this bridge."
                        !TermuxBuildBridge.hasTermux(context) -> "Install Termux to enable local Gradle execution."
                        !TermuxBuildBridge.hasRunCommandPermission(context) -> "Grant MAYA the 'Run commands in Termux environment' permission in App Info. Termux must also have allow-external-apps=true."
                        else -> "Ready to request ./gradlew assembleDebug in: " + buildRequest.workingDirectory
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val request = buildRequest
                        when {
                            request == null -> status = "Select a project folder under shared internal storage (primary storage) first."
                            !TermuxBuildBridge.hasTermux(context) -> status = "Termux is not installed or not visible to MAYA."
                            !TermuxBuildBridge.hasRunCommandPermission(context) -> {
                                status = "Opening MAYA App Info. Grant 'Run commands in Termux environment', then return."
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                            }
                            else -> {
                                TermuxBuildBridge.requestDebugBuild(context, request)
                                    .onSuccess { status = "Build command sent to Termux. Check the Termux terminal for the actual Gradle result." }
                                    .onFailure { status = "Termux did not accept the build request: " + (it.localizedMessage ?: "unknown error") }
                            }
                        }
                    },
                    enabled = projectTreeUri != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Run Gradle assembleDebug in Termux") }
                OutlinedButton(
                    onClick = {
                        when {
                            !TermuxBuildBridge.hasTermux(context) -> status = "Termux is not installed."
                            !TermuxBuildBridge.hasRunCommandPermission(context) -> {
                                status = "Grant MAYA the Termux Run Command permission in App Info, then retry."
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                            }
                            else -> TermuxBuildBridge.requestLogcatSnapshot(context)
                                .onSuccess { status = "Logcat snapshot requested. Waiting for Termux output." }
                                .onFailure { status = "Logcat request failed: " + (it.localizedMessage ?: "unknown error") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Capture available Android Logcat") }
                Text(
                    "Prerequisites: Termux installed, Termux Settings > allow-external-apps=true, Termux storage permission plus termux-setup-storage for shared files, Android SDK/JDK configured in Termux for Gradle, and Android Settings > MAYA > Additional permissions > Run commands in Termux environment. Android may restrict which system logs Termux can read. Build execution is user-triggered; MAYA reports the actual callback result.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                latestBuildOutput?.let { output ->
                    Text(
                        if (output.succeeded) output.commandLabel + ": SUCCESS"
                        else output.commandLabel + ": FAILED (exit " + output.exitCode + ")",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (output.succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    if (output.errorMessage.isNotBlank()) Text(output.errorMessage, style = MaterialTheme.typography.bodySmall)
                    val diagnostics = (output.stderr + "\n" + output.stdout).trim().takeLast(7000)
                    val buildLocation = findBuildDiagnostic(output.stderr + "\n" + output.stdout)
                    if (buildLocation != null) {
                        detectedErrorFile = buildLocation.path
                        detectedErrorLine = buildLocation.line
                        Text("Detected source location: ${buildLocation.path}:${buildLocation.line}" +
                            (buildLocation.column?.let { ":$it" } ?: ""), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                        Text(buildLocation.message, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = ::openDetectedBuildErrorFile,
                            enabled = projectTreeUri != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open detected error file") }
                    } else {
                        Text("No source file and line number could be extracted from this output. Use the diagnostics below to locate the failing task.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (diagnostics.isNotBlank()) {
                        Text(diagnostics, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!output.succeeded || output.commandLabel.contains("Logcat", ignoreCase = true)) {
                        OutlinedButton(
                            onClick = {
                                changeRequest = "Analyze this real Gradle build failure and fix the currently opened file only. Build exit code: " +
                                    output.exitCode + "\nTermux error: " + output.errorMessage +
                                    "\nBuild output:\n" + diagnostics
                                status = if (fileUri == null) "Open the source file identified by the build error, then ask Maya to edit it."
                                    else "Build diagnostics added to Maya's coding request. Review the target file, then ask Maya to edit."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Send build diagnostics to Maya") }
                    }
                } ?: Text("No Termux build result has been received in this app session yet.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("4. Create or open source code", style = MaterialTheme.typography.titleMedium)
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
                Text("5. Whole-project change planner", style = MaterialTheme.typography.titleMedium)
                Text("Maya reads a bounded set of source files and prepares a unified patch. The patch is saved as a separate file; original source files are not changed until you review and explicitly apply it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        if (changeRequest.isBlank()) status = "Describe the project-wide change first."
                        else if (projectFiles.isEmpty()) status = "Connect and index the project folder first."
                        else if (isGenerating) status = "Maya is already preparing a code change."
                        else {
                            isGenerating = true
                            status = "Maya is reading project source files and planning a patch."
                            coroutineScope.launch {
                                try {
                                    val projectContext = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val candidates = projectFiles.filter { entry ->
                                            !entry.isDirectory &&
                                                entry.relativePath.substringAfterLast('.', "").lowercase() in setOf("kt","java","xml","kts","gradle","properties","pro","md") &&
                                                entry.relativePath.split('/').none { it in setOf("build",".gradle",".git") }
                                        }.take(12)
                                        val combined = StringBuilder()
                                        candidates.forEach { entry ->
                                            if (combined.length < 52000) {
                                                val source = context.contentResolver.openInputStream(entry.uri)
                                                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                                                val remaining = 52000 - combined.length
                                                if (source.isNotBlank() && remaining > 0) {
                                                    combined.append("\n\n===== FILE: ").append(entry.relativePath).append(" =====\n")
                                                    combined.append(source.take(remaining.coerceAtLeast(0)))
                                                }
                                            }
                                        }
                                        combined.toString()
                                    }
                                    if (projectContext.isBlank()) {
                                        status = "No supported text source files were found in the indexed project."
                                    } else {
                                        val prompt = "You are Maya, a senior Android engineer. User request: " + changeRequest +
                                            "\nProject source context (bounded sample):\n" + projectContext +
                                            "\nReturn ONLY a valid unified git patch. Start each file with 'diff --git a/path b/path'. " +
                                            "Use project-relative paths, no absolute paths, no markdown fences, no explanation. " +
                                            "Preserve unrelated features. Do not add or delete files unless essential. If context is insufficient, return exactly NO_PATCH: followed by a short reason."
                                        val generated = StringBuilder()
                                        aiRepository.streamChatReply(
                                            prompt, MayaPersona.CODER, emptyList(), null,
                                            ResponseLength.DETAILED,
                                            if (changeRequest.any { it.code in 2432..2559 }) AppLanguage.BANGLA else AppLanguage.ENGLISH
                                        ).collect { chunk -> generated.clear(); generated.append(chunk) }
                                        val candidatePatch = generated.toString().trim()
                                        if (!candidatePatch.startsWith("diff --git ")) {
                                            status = candidatePatch.take(500).ifBlank { "Maya did not return a patch." }
                                        } else {
                                            val tree = projectTreeUri?.let(Uri::parse)
                                                ?: throw IOException("Project folder permission expired.")
                                            val rootId = DocumentsContract.getTreeDocumentId(tree)
                                            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
                                            val newPatchName = "maya-ai-change-" + System.currentTimeMillis() + ".patch"
                                            val patchUri = DocumentsContract.createDocument(
                                                context.contentResolver, parent, "text/x-diff", newPatchName
                                            ) ?: throw IOException("Could not create patch file in the project folder.")
                                            context.contentResolver.openOutputStream(patchUri, "wt")?.use {
                                                it.write(candidatePatch.toByteArray(Charsets.UTF_8))
                                            } ?: throw IOException("Could not write the patch file.")
                                            patchText = candidatePatch
                                            patchFileName = newPatchName
                                            status = "Patch saved as " + newPatchName + ". Review the patch below before applying."
                                        }
                                    }
                                } catch (error: Exception) {
                                    status = "Project patch generation failed: " + (error.localizedMessage ?: "unknown error")
                                } finally {
                                    isGenerating = false
                                }
                            }
                        }
                    },
                    enabled = !isGenerating && projectFiles.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isGenerating) "Maya is preparing patch…" else "Ask Maya to plan project-wide changes") }
                OutlinedTextField(
                    value = changeRequest,
                    onValueChange = { changeRequest = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Project-wide change request") },
                    minLines = 2
                )
                if (patchText.isNotBlank()) {
                    Text("Patch preview: " + patchFileName, style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = patchText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                        label = { Text("Review unified patch") },
                        minLines = 8
                    )
                    val patchBuildRequest = projectTreeUri?.let(Uri::parse)?.let(TermuxBuildBridge::resolveSharedStorageProject)
                    Button(
                        onClick = {
                            when {
                                patchBuildRequest == null -> status = "Select a shared-storage project folder to apply patches through Termux."
                                !TermuxBuildBridge.hasTermux(context) -> status = "Termux is not installed."
                                !TermuxBuildBridge.hasRunCommandPermission(context) -> {
                                    status = "Grant MAYA the Termux Run Command permission in App Info, then retry."
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                                }
                                else -> TermuxBuildBridge.requestApplyReviewedPatch(context, patchBuildRequest, patchFileName)
                                    .onSuccess { status = "Patch check and apply command sent. Wait for the Termux result before generating another patch." }
                                    .onFailure { status = "Patch apply request failed: " + (it.localizedMessage ?: "unknown error") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Apply reviewed patch with Termux") }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("6. Maya single-file code assistant", style = MaterialTheme.typography.titleMedium)
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
                                aiRepository.streamChatReply(prompt, MayaPersona.CODER, emptyList(), null, ResponseLength.DETAILED, if (request.any { it.code in 2432..2559 }) AppLanguage.BANGLA else AppLanguage.ENGLISH).collect { chunk -> generated.clear(); generated.append(chunk) }
                                if (generated.isNotBlank()) { codeText = generated.toString().trim(); status = "AI edit prepared. Review it, then tap Save changes." }
                                else status = "AI returned no code; editor unchanged."
                            } catch (error: Exception) { status = "AI edit failed: " + (error.localizedMessage ?: "unknown error") }
                            finally { isGenerating = false }
                        }
                    }
                }, enabled = !isGenerating, modifier = Modifier.fillMaxWidth()) { Text(if (isGenerating) "Maya is coding..." else "Ask Maya to edit this file") }
                Text("AI edits only the unsaved editor buffer. Nothing is written until you review and tap Save changes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("7. Approved IDE screen controls", style = MaterialTheme.typography.titleMedium)
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
                Text("8. Code editor", style = MaterialTheme.typography.titleMedium)
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
            "Current stage: bounded project indexing, single-file editing, reviewed multi-file patches, Termux Gradle/Logcat handoff, and automatic source path:line extraction from build output. When a matching file exists in the indexed tree, Maya can open it directly. Full IDE-specific navigation, private IDE storage access and unrestricted repository context are not available.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
