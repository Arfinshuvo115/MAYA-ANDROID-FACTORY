package com.example.missioncontrol.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale

/** Deterministic on-device source audit. This is static analysis, not an LLM. */
object MayaOfflineProjectAuditor {
    private const val MAX_FILES = 250
    private const val MAX_FILE_BYTES = 200_000L
    private const val MAX_TOTAL_BYTES = 5_000_000L

    data class Report(val scannedFiles: Int, val findings: List<String>, val skippedFiles: Int) {
        fun asText(): String = buildString {
            appendLine("MAYA OFFLINE PROJECT AUDIT")
            appendLine("Scanned source files: $scannedFiles")
            appendLine("Skipped files: $skippedFiles")
            appendLine("Findings: ${findings.size}")
            findings.forEachIndexed { index, finding -> appendLine("${index + 1}. $finding") }
            if (findings.isEmpty()) appendLine("No configured rule matched. This is not proof that the project is bug-free.")
            appendLine("This report was generated locally using static rules; no AI model or network was used.")
        }
    }

    fun run(context: Context, treeUri: Uri): Report {
        val resolver = context.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val pending = ArrayDeque<Pair<String, String>>()
        pending.add(DocumentsContract.getTreeDocumentId(treeUri) to "")
        val findings = linkedSetOf<String>()
        var scanned = 0
        var skipped = 0
        var totalBytes = 0L
        var visitedDirectories = 0

        while (pending.isNotEmpty() && scanned + skipped < MAX_FILES && visitedDirectories < 40 && totalBytes < MAX_TOTAL_BYTES) {
            val (parentId, parentPath) = pending.removeFirst()
            visitedDirectories++
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val cursor = resolver.query(childrenUri, projection, null, null, null) ?: continue
            cursor.use {
                val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (it.moveToNext() && scanned + skipped < MAX_FILES && totalBytes < MAX_TOTAL_BYTES) {
                    if (idIndex < 0 || nameIndex < 0 || mimeIndex < 0) continue
                    val id = it.getString(idIndex) ?: continue
                    val name = it.getString(nameIndex) ?: continue
                    val mime = it.getString(mimeIndex).orEmpty()
                    val relative = if (parentPath.isBlank()) name else "$parentPath/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (pending.size < 40) pending.add(id to relative)
                        continue
                    }
                    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (extension !in setOf("kt", "java", "xml", "kts", "gradle", "properties", "json", "js", "ts", "tsx", "jsx", "py", "html", "css")) continue
                    try {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        val bytes = resolver.openInputStream(uri)?.use { input ->
                            val buffer = ByteArrayOutputStreamBounded(MAX_FILE_BYTES.toInt())
                            val chunk = ByteArray(8192)
                            while (true) {
                                val count = input.read(chunk)
                                if (count < 0) break
                                buffer.write(chunk, 0, count)
                            }
                            buffer.toByteArray()
                        } ?: throw IllegalStateException("Unreadable")
                        totalBytes += bytes.size
                        if (bytes.size >= MAX_FILE_BYTES) {
                            skipped++
                            continue
                        }
                        val source = bytes.toString(Charsets.UTF_8)
                        scanned++
                        inspect(relative, source, findings)
                    } catch (_: Exception) {
                        skipped++
                    }
                }
            }
        }
        return Report(scanned, findings.toList().take(100), skipped)
    }

    private fun inspect(path: String, source: String, findings: MutableSet<String>) {
        val lines = source.lines()
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            val number = index + 1
            if (Regex("""\\b(TODO|FIXME)\\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                findings.add("$path:$number contains a TODO/FIXME marker.")
            }
            if (Regex("""(?i)(api[_-]?key|secret|token)\\s*[:=]\\s*["'][A-Za-z0-9_./+=-]{16,}["']""").containsMatchIn(line)) {
                findings.add("$path:$number may contain a hard-coded credential. Value intentionally omitted; rotate it if real.")
            }
            if (Regex("""catch\\s*\\([^)]*\\)\\s*\\{\\s*}""").containsMatchIn(line)) {
                findings.add("$path:$number contains an empty catch block that may hide failures.")
            }
            if (line.contains("GlobalScope.launch")) {
                findings.add("$path:$number uses GlobalScope; lifecycle-safe coroutine scope may be preferable.")
            }
            if (line.contains("Thread.sleep(")) {
                findings.add("$path:$number uses Thread.sleep; it may block the calling thread.")
            }
        }
    }

    private class ByteArrayOutputStreamBounded(private val limit: Int) {
        private val output = java.io.ByteArrayOutputStream()
        fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (output.size() + length > limit) throw IllegalStateException("File exceeds audit limit")
            output.write(bytes, offset, length)
        }
        fun toByteArray(): ByteArray = output.toByteArray()
    }
}
