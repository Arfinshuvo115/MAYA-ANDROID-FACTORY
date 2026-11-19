package com.example.ui.screens.missioncontrol

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.data.ai.AiRepository
import com.example.domain.model.AiWorkflowTool
import com.example.domain.model.ResponseLength
import kotlinx.coroutines.launch

/** User-selected image analysis through the existing Gemini multimodal Vision Inspector. */
@Composable
fun MayaVisionScreen(
    aiRepository: AiRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var prompt by remember { mutableStateOf("Describe the image, identify visible objects and text, and explain relevant details. Do not guess hidden or uncertain information.") }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
        result = null
        error = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onBack, enabled = !busy) { Text("Back") }
        Text("Maya Vision", style = MaterialTheme.typography.titleLarge)
        Text(
            "Choose a photo and ask Maya to inspect it using the configured Gemini multimodal model. The selected image is sent only when you press Analyze.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { imagePicker.launch("image/*") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (imageUri == null) "Choose Image" else "Choose Another Image") }

        imageUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = "Selected image for Maya Vision analysis",
                modifier = Modifier.fillMaxWidth()
            )
            Text("Selected image: " + (uri.lastPathSegment ?: "image"), style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("What should Maya inspect?") },
            minLines = 3,
            enabled = !busy
        )

        Button(
            onClick = {
                val selected = imageUri
                if (selected == null) {
                    error = "Choose an image first."
                } else if (prompt.isBlank()) {
                    error = "Enter an image-analysis instruction."
                } else {
                    busy = true
                    error = null
                    result = null
                    scope.launch {
                        try {
                            val analysis = aiRepository.runWorkflowTool(
                                tool = AiWorkflowTool.VISION_INSPECTOR,
                                prompt = prompt.trim(),
                                imageUri = selected.toString(),
                                responseLength = ResponseLength.DETAILED
                            )
                            result = analysis.rawMarkdown
                        } catch (failure: Exception) {
                            error = failure.localizedMessage ?: "Image analysis failed."
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            enabled = imageUri != null && prompt.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Analyzing image…" else "Analyze Image") }

        if (busy) Text("Waiting for the configured AI provider. Keep Maya open until the request finishes.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let { markdown ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Text(
                    markdown,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Text(
            "Requirements: a configured Gemini API key and a model that accepts image input. Without a key, the existing local fallback may return text-only guidance rather than real image understanding. Images are not stored in Mission Memory by this screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
