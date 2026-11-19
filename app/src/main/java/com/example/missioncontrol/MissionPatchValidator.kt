package com.example.missioncontrol

/**
 * Conservative preflight for AI-generated unified patches.
 * This validates patch shape and path safety; git apply --check remains the authoritative
 * repository-context check immediately before the separately approved apply operation.
 */
object MissionPatchValidator {
    private val diffHeader = Regex("""^diff --git a/(.+) b/(.+)$""")
    private const val MAX_PATCH_CHARS = 262144
    private const val MAX_FILES = 12

    fun validate(patch: String): Result<Int> = runCatching {
        require(patch.isNotBlank()) { "Patch is empty." }
        require(patch.length <= MAX_PATCH_CHARS) { "Patch exceeds the 256 KB safety limit." }
        require('\u0000' !in patch) { "Patch contains a NUL character." }
        require(!patch.contains("GIT binary patch") && !patch.contains("Binary files ")) {
            "Binary patches are not accepted by the AI patch review flow."
        }

        val lines = patch.lineSequence().toList()
        val fileHeaders = lines.mapNotNull { line -> diffHeader.matchEntire(line)?.destructured?.let { it.component1() to it.component2() } }
        require(fileHeaders.isNotEmpty()) { "No valid 'diff --git a/path b/path' file header was found." }
        require(fileHeaders.size <= MAX_FILES) { "Patch touches more than $MAX_FILES files." }

        fileHeaders.forEach { (oldPath, newPath) ->
            validateRelativePath(oldPath)
            validateRelativePath(newPath)
        }

        val headerIndices = lines.indices.filter { diffHeader.matches(lines[it]) }
        headerIndices.forEachIndexed { index, start ->
            val end = headerIndices.getOrNull(index + 1) ?: lines.size
            val section = lines.subList(start, end)
            val hasFileMarkers = section.any { it.startsWith("--- ") } && section.any { it.startsWith("+++ ") }
            val hasHunk = section.any { it.startsWith("@@ ") }
            val isModeOnly = section.any { it.startsWith("old mode ") || it.startsWith("new mode ") }
            require(hasFileMarkers && (hasHunk || isModeOnly)) {
                "Patch section for ${fileHeaders[index].second} is missing file markers or a valid hunk."
            }
        }
        fileHeaders.size
    }

    private fun validateRelativePath(path: String) {
        require(path.isNotBlank() && path.length <= 240) { "Patch contains an empty or excessively long path." }
        require(!path.startsWith('/') && !path.startsWith('\\')) { "Absolute patch paths are not allowed." }
        require('\\' !in path) { "Patch paths must use forward slashes." }
        val segments = path.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Patch path contains an unsafe traversal or empty segment: $path"
        }
        require(!path.startsWith(".git/") && path != ".git") { "Patches may not modify Git metadata." }
    }
}
