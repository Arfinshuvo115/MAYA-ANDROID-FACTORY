package com.example.missioncontrol

/** Static, conservative review of unified patches. This is not a semantic security proof. */
object MissionPatchRiskAnalyzer {
    data class FileChange(
        val path: String,
        val addedLines: Int,
        val removedLines: Int,
        val isNewFile: Boolean,
        val isDeletedFile: Boolean,
        val riskFlags: List<String>
    )

    data class Review(
        val files: List<FileChange>,
        val riskLevel: String,
        val findings: List<String>
    ) {
        fun toMarkdown(): String = buildString {
            appendLine("Patch change review")
            appendLine("Overall static risk: $riskLevel")
            appendLine("Files changed: ${files.size}")
            appendLine("Added lines: ${files.sumOf { it.addedLines }} · Removed lines: ${files.sumOf { it.removedLines }}")
            files.forEach { file ->
                appendLine()
                appendLine("- ${file.path}: +${file.addedLines} / -${file.removedLines}")
                if (file.isNewFile) appendLine("  - New file")
                if (file.isDeletedFile) appendLine("  - File deletion")
                file.riskFlags.forEach { appendLine("  - Risk flag: $it") }
            }
            if (findings.isNotEmpty()) {
                appendLine()
                appendLine("Review findings")
                findings.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("Static pre-apply review only. It does not prove the patch compiles, is behaviorally correct, or is secure. Review the full diff and require git apply --check before applying.")
        }
    }

    fun analyze(patch: String): Review {
        val lines = patch.lineSequence().toList()
        val starts = lines.indices.filter { lines[it].startsWith("diff --git ") }
        val changes = starts.mapIndexed { index, start ->
            val end = starts.getOrNull(index + 1) ?: lines.size
            val section = lines.subList(start, end)
            val header = Regex("""^diff --git a/(.+) b/(.+)$""").matchEntire(lines[start])
            val oldPath = header?.groupValues?.get(1).orEmpty()
            val path = header?.groupValues?.get(2).orEmpty()
            val added = section.count { it.startsWith("+") && !it.startsWith("+++") }
            val removed = section.count { it.startsWith("-") && !it.startsWith("---") }
            val newFile = section.any { it == "--- /dev/null" }
            val deletedFile = section.any { it == "+++ /dev/null" }
            val flags = mutableListOf<String>()
            val lowerPath = path.lowercase()
            if (newFile) flags += "Adds a new file"
            if (deletedFile) flags += "Deletes an existing file"
            if (lowerPath.endsWith("androidmanifest.xml")) flags += "Android manifest or component/permission behavior may change"
            if (lowerPath.contains("build.gradle") || lowerPath.endsWith(".gradle.kts") ||
                lowerPath.contains("libs.versions.toml") || lowerPath.endsWith("settings.gradle")) {
                flags += "Build configuration or dependency graph may change"
            }
            if (lowerPath.contains("proguard") || lowerPath.contains("network_security_config") ||
                lowerPath.contains("keystore") || lowerPath.contains("credential") || lowerPath.contains("secret")) {
                flags += "Security, network trust, or credential-related file"
            }
            val changedText = section.joinToString("\n")
            val patterns = listOf(
                "uses-permission" to "Android permission declaration",
                "android:exported" to "Component exported state",
                "allowbackup" to "Backup/data exposure setting",
                "usescleartexttraffic" to "Cleartext network traffic policy",
                "http://" to "Non-TLS HTTP URL",
                "api_key" to "Possible API-key field or identifier",
                "password" to "Password-related code or configuration",
                "webview" to "WebView behavior or security boundary",
                "accessibilityservice" to "Accessibility-service capability",
                "pendingintent" to "PendingIntent behavior"
            )
            patterns.forEach { (needle, label) ->
                if (changedText.contains(needle, ignoreCase = true)) flags += label
            }
            if (added + removed > 250) flags += "Large change: more than 250 changed lines"
            FileChange(path.ifBlank { oldPath }, added, removed, newFile, deletedFile, flags.distinct())
        }
        val allFlags = changes.flatMap { it.riskFlags }
        val findings = mutableListOf<String>()
        if (changes.size > 5) findings += "Patch spans more than five files; review cross-file interactions."
        if (changes.any { it.isDeletedFile }) findings += "At least one file is deleted; verify no feature or resource is orphaned."
        if (changes.any { it.riskFlags.isNotEmpty() }) findings += "One or more changes touch sensitive Android, build, security, permission, or network behavior."
        if (changes.sumOf { it.addedLines + it.removedLines } > 500) findings += "Large patch; split into smaller independently reviewable changes where possible."
        val level = when {
            allFlags.any { it.contains("Security") || it.contains("permission") || it.contains("exported") || it.contains("credential") || it.contains("trust") } -> "HIGH"
            allFlags.isNotEmpty() -> "MEDIUM"
            changes.sumOf { it.addedLines + it.removedLines } > 100 -> "MEDIUM"
            else -> "LOW (static indicators only)"
        }
        if (changes.isEmpty()) findings += "No diff sections were recognized."
        return Review(changes, level, findings)
    }

    /** Compares current SAF-read file contents with the pre-apply snapshot. */
    fun compareAfterApply(before: Map<String, String>, after: Map<String, String>): String = buildString {
        appendLine("Post-apply source comparison")
        before.toSortedMap().forEach { (path, oldText) ->
            val newText = after[path]
            if (newText == null) {
                appendLine("- $path: could not be re-read; comparison unavailable")
            } else if (oldText == newText) {
                appendLine("- $path: unchanged")
            } else {
                val oldLines = oldText.lines()
                val newLines = newText.lines()
                val removed = oldLines.count { it !in newLines }
                val added = newLines.count { it !in oldLines }
                appendLine("- $path: content changed (approx. +$added / -$removed lines)")
            }
        }
        appendLine("This is a bounded text comparison, not a semantic diff or build verification.")
    }
}
