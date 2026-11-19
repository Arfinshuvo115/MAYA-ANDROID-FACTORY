package com.example.ui.screens.missioncontrol

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Generates a responsive, self-contained HTML landing page with local preview and user-selected export. */
@Composable
fun WebsiteFactoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var siteName by remember { mutableStateOf("My Website") }
    var headline by remember { mutableStateOf("Build something remarkable.") }
    var description by remember { mutableStateOf("Introduce your project, explain its value, and give visitors a clear next step.") }
    var accent by remember { mutableStateOf("#6750A4") }
    var status by remember { mutableStateOf("Edit the content, preview the page, then export the HTML file.") }
    var htmlToExport by remember { mutableStateOf("") }

    fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    fun safeAccent(value: String): String =
        if (value.matches(Regex("^#[0-9a-fA-F]{6}$"))) value else "#6750A4"

    val html = remember(siteName, headline, description, accent) {
        val safeColor = safeAccent(accent)
        val safeTitle = escapeHtml(siteName.trim().ifBlank { "My Website" })
        val safeHeadline = escapeHtml(headline.trim().ifBlank { "Welcome" })
        val safeDescription = escapeHtml(description.trim())
        """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="$safeDescription">
<title>$safeTitle</title>
<style>
:root{color-scheme:light;font-family:Inter,system-ui,-apple-system,sans-serif}
*{box-sizing:border-box}body{margin:0;background:#f6f5fa;color:#201d28}
header{padding:22px max(6vw,20px);font-weight:700;background:#fff}
main{min-height:78vh;display:grid;place-items:center;padding:48px 20px}
.hero{max-width:760px;text-align:center;background:#fff;padding:clamp(28px,7vw,64px);border-radius:28px;box-shadow:0 18px 60px #21183a12}
h1{font-size:clamp(2.3rem,7vw,4.6rem);line-height:1.05;margin:0 0 22px}
p{font-size:1.15rem;line-height:1.7;color:#625d6b}
a{display:inline-block;margin-top:22px;padding:14px 24px;border-radius:999px;background:$safeColor;color:white;text-decoration:none;font-weight:700}
footer{text-align:center;padding:24px;color:#777}
</style>
</head>
<body>
<header>$safeTitle</header>
<main><section class="hero"><h1>$safeHeadline</h1><p>$safeDescription</p><a href="mailto:hello@example.com">Get in touch</a></section></main>
<footer>Created with Maya Website Factory</footer>
</body></html>"""
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri == null) {
            status = "Export cancelled."
        } else {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(htmlToExport)
                } ?: error("Could not open the selected document destination.")
            }
            status = if (saved.isSuccess) {
                "HTML website exported. Open the saved file in a browser or upload it to your hosting provider."
            } else {
                "Export failed: " + (saved.exceptionOrNull()?.localizedMessage ?: "unknown error")
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Text("Website Factory", style = MaterialTheme.typography.titleLarge)
        Text(
            "Create a responsive single-page HTML website, preview it on this device, and export the file to a location you choose. Hosting and domain deployment are not included.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(siteName, { siteName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Website / brand name") }, singleLine = true)
        OutlinedTextField(headline, { headline = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Main headline") }, minLines = 2)
        OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3)
        OutlinedTextField(accent, { accent = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Accent color (hex, e.g. #6750A4)") }, singleLine = true)
        Text("Live preview", style = MaterialTheme.typography.titleMedium)
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(420.dp),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    webViewClient = WebViewClient()
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        )
        Button(
            onClick = {
                htmlToExport = html
                val fileBase = siteName.trim().replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').ifBlank { "maya-website" }.take(48)
                exportLauncher.launch(fileBase + ".html")
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Export Website HTML") }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text(
            "Current scope: one self-contained landing page with responsive styling. No server-side code, database, contact-form processing, analytics, login, custom domain or automatic deployment is created.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
