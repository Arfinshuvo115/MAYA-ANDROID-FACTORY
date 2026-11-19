package com.example.missioncontrol

/**
 * Creates a reviewable regression checklist from a unified patch.
 * This planner generates recommendations only; it never executes tests.
 */
object MissionRegressionTestPlanner {
    data class TestCase(
        val id: String,
        val title: String,
        val area: String,
        val priority: String,
        val testType: String,
        val rationale: String,
        val steps: List<String>,
        val expectedResult: String,
        val evidence: String
    )

    data class Plan(
        val riskLevel: String,
        val changedFiles: List<String>,
        val cases: List<TestCase>,
        val notes: List<String>
    ) {
        fun toMarkdown(): String = buildString {
            appendLine("Regression Test Plan")
            appendLine("Static patch risk: $riskLevel")
            appendLine("Changed files recognized: ${changedFiles.size}")
            appendLine("Generated test cases: ${cases.size}")
            appendLine("Execution status: NOT RUN — this is a generated checklist, not test execution.")
            cases.forEach { test ->
                appendLine()
                appendLine("${test.id}. ${test.title} [${test.priority}]")
                appendLine("Area: ${test.area} · Type: ${test.testType} · Status: NOT RUN")
                appendLine("Why: ${test.rationale}")
                appendLine("Steps:")
                test.steps.forEachIndexed { index, step -> appendLine("  ${index + 1}. $step") }
                appendLine("Expected: ${test.expectedResult}")
                appendLine("Evidence: ${test.evidence}")
            }
            if (notes.isNotEmpty()) {
                appendLine()
                appendLine("Coverage notes")
                notes.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("Run only after reviewing the patch and obtaining explicit approval. A generated test plan does not establish that any test passed.")
        }
    }

    private data class AreaRule(
        val key: String,
        val title: String,
        val pattern: Regex,
        val rationale: String,
        val steps: List<String>,
        val expected: String,
        val evidence: String,
        val type: String = "Manual + targeted automated"
    )

    private val rules = listOf(
        AreaRule(
            "manifest", "Manifest, permissions and Android components",
            Regex("androidmanifest|uses-permission|exported|service|receiver|provider", RegexOption.IGNORE_CASE),
            "Manifest or component changes can affect app installation, permissions and component availability.",
            listOf("Install or upgrade the app on a clean test device.", "Exercise the affected permission or component flow.", "Verify denied permissions and disabled services fail safely."),
            "The app installs, requests only intended permissions, and components remain available only as intended.",
            "Install/upgrade result, permission prompts and relevant Logcat entries."
        ),
        AreaRule(
            "ui-navigation", "Compose UI and navigation",
            Regex("screens?/|screen\\.kt$|navigation|navgraph|route|theme|\\.xml$|strings", RegexOption.IGNORE_CASE),
            "UI, resource or route changes can hide controls, break navigation, or alter existing screens.",
            listOf("Launch the app and open the changed screen from its normal entry point.", "Tap each changed control and navigate forward and back.", "Rotate or recreate the screen when applicable."),
            "Changed controls render and respond; navigation returns correctly; existing controls remain present.",
            "Screen recording or screenshots, navigation results and UI test output."
        ),
        AreaRule(
            "storage", "Persistence, database and preferences",
            Regex("repository|datastore|preferences|database|dao|room|entity|migration|sharedpreferences", RegexOption.IGNORE_CASE),
            "Persistence changes can lose settings, corrupt records or break upgrades.",
            listOf("Save a representative setting or record.", "Force-stop and relaunch the app.", "Read, update and clear the value using the supported UI.", "If schema changed, upgrade a copy of existing test data."),
            "Data survives restart, updates correctly, and migrations preserve existing user data.",
            "Before/after stored values, migration logs and repository/database test output."
        ),
        AreaRule(
            "network-auth", "Network, API and authentication",
            Regex("api|network|http|client|retrofit|okhttp|auth|token|credential|secret|key|tavily|gemini", RegexOption.IGNORE_CASE),
            "Network and identity changes can break requests, leak credentials or mis-handle provider failures.",
            listOf("Test with a valid test credential and a successful response.", "Test missing/invalid credentials.", "Test timeout, offline and provider error responses.", "Confirm secrets are not printed in logs or UI diagnostics."),
            "Successful requests parse correctly; failures are explicit and recoverable; credentials remain protected.",
            "Redacted request/response evidence, error handling tests and log review."
        ),
        AreaRule(
            "voice-audio", "Voice, microphone and audio",
            Regex("voice|speech|audio|microphone|recognizer|tts|texttospeech|wake", RegexOption.IGNORE_CASE),
            "Voice changes can affect microphone permission, recognition, playback and lifecycle handling.",
            listOf("Grant and deny microphone permission in separate runs.", "Test recognition with a short supported phrase.", "Verify spoken response playback and stop/retry behavior.", "Navigate away and back to check resource cleanup."),
            "Permission states are handled clearly; recognition and playback work without stuck or duplicate sessions.",
            "Permission flow, recognition transcript, playback observation and audio-related Logcat."
        ),
        AreaRule(
            "mission-agents", "Mission Control queue and agent pipeline",
            Regex("mission|agent|pipeline|queue|planner|research|reviewer|workforce", RegexOption.IGNORE_CASE),
            "Mission orchestration changes can lose tasks, mis-order handoffs or falsely report completion.",
            listOf("Create a test mission and run the affected workflow.", "Verify each expected stage receives the prior handoff.", "Simulate missing credentials or a provider failure.", "Pause, resume and reopen the saved task."),
            "Stages and task states are accurate; failures remain visible and are never marked completed.",
            "Saved task history, stage report and provider failure trace."
        ),
        AreaRule(
            "ide-patch", "Project files, SAF, Termux and patch workflow",
            Regex("androidide|codingagent|termux|patch|saf|projectfile|sourceeditor|buildbridge|offline", RegexOption.IGNORE_CASE),
            "File and patch workflow changes can target the wrong file, lose edits or bypass explicit apply approval.",
            listOf("Connect a disposable test project through the folder picker.", "Open and save a source file, then reopen it.", "Preview a valid patch and a deliberately invalid patch.", "Verify apply remains an explicit user action and inspect the actual Termux callback."),
            "Only selected files are read or written; invalid patches are rejected; apply and build outcomes remain distinct.",
            "Selected file paths, patch preview, git apply --check output and Termux callback."
        ),
        AreaRule(
            "wallet", "Wallet ledger and currency records",
            Regex("wallet|ledger|currency|transaction", RegexOption.IGNORE_CASE),
            "Ledger changes can affect arithmetic, currency separation or persistence of financial records.",
            listOf("Add test income and withdrawal entries in one currency.", "Check net balance and a second currency independently.", "Edit or remove a test record if supported, then restart the app."),
            "Balances are calculated per currency and records persist without implying real transfers.",
            "Test ledger entries, calculated balances and persistence result."
        ),
        AreaRule(
            "research-browser", "Web research and browser workspace",
            Regex("tavily|research|browser|webview|url|websearch", RegexOption.IGNORE_CASE),
            "Research and browser changes can mishandle URLs, expose credentials or fabricate provider results.",
            listOf("Test a valid query with a configured test key.", "Test missing key, invalid key and offline network.", "Open a valid HTTPS result and reject malformed or credential-bearing URLs."),
            "Only provider-returned results are shown; failure states are explicit; unsafe URLs are rejected.",
            "Redacted provider result, error state and URL validation cases."
        ),
        AreaRule(
            "background", "Background jobs and recovery",
            Regex("jobscheduler|jobservice|worker|workmanager|boot|recovery|offlinequeue", RegexOption.IGNORE_CASE),
            "Background scheduling changes can duplicate work, strand tasks or lose recovery after process death.",
            listOf("Queue a supported task and observe its state transitions.", "Stop the app process while a task is pending where safe.", "Reopen the app and inspect recovery behavior.", "Verify retry limits and terminal failure state."),
            "Tasks are not duplicated, persisted states remain truthful, and bounded recovery behaves as designed.",
            "Queue history, scheduler logs and callback IDs."
        ),
        AreaRule(
            "build-config", "Gradle, dependencies and build configuration",
            Regex("build\\.gradle|settings\\.gradle|gradle\\.properties|libs\\.versions\\.toml|proguard|\\.kts$", RegexOption.IGNORE_CASE),
            "Build configuration changes can alter dependency resolution, variants, packaging or runtime compatibility.",
            listOf("Run the configured Gradle validation tasks after feature transfer is complete.", "Check dependency resolution and the intended build variant.", "Install the resulting artifact on a supported test device."),
            "The intended variant builds and installs with no unexpected dependency or packaging regression.",
            "Actual Gradle task output, dependency report and install result.",
            "Build/CI"
        ),
        AreaRule(
            "resources", "Resources, strings and themes",
            Regex("res/|drawable|mipmap|values|strings|colors|themes|fonts", RegexOption.IGNORE_CASE),
            "Resource changes can cause missing assets, localization regressions or theme crashes.",
            listOf("Open affected screens in each supported theme.", "Check changed strings in supported locales.", "Verify referenced images, icons and fonts render."),
            "Resources resolve at runtime and existing localized/theme variants remain intact.",
            "Screenshots for affected themes/locales and resource compilation output."
        )
    )

    fun generate(patch: String, risk: MissionPatchRiskAnalyzer.Review): Plan {
        val fileSections = parseSections(patch)
        val paths = risk.files.map { it.path }.filter { it.isNotBlank() }.distinct()
        val cases = mutableListOf<TestCase>()
        var sequence = 1

        fun add(title: String, area: String, priority: String, type: String, rationale: String,
                steps: List<String>, expected: String, evidence: String) {
            cases += TestCase(
                "RT-${sequence++.toString().padStart(2, '0')}", title, area, priority, type,
                rationale, steps, expected, evidence
            )
        }

        rules.forEach { rule ->
            val matched = fileSections.filter { section ->
                rule.pattern.containsMatchIn(section.path) ||
                    rule.pattern.containsMatchIn(section.addedText)
            }
            if (matched.isNotEmpty()) {
                val highRisk = risk.riskLevel.startsWith("HIGH", ignoreCase = true)
                add(
                    rule.title + " (${matched.size} related file(s))",
                    matched.joinToString { it.path }.take(700),
                    if (highRisk || matched.any { it.isDeleted }) "HIGH" else "MEDIUM",
                    rule.type,
                    rule.rationale,
                    rule.steps,
                    rule.expected,
                    rule.evidence
                )
            }
        }

        if (cases.isEmpty()) {
            add(
                "Changed-file focused review",
                paths.joinToString().ifBlank { "Unclassified patch" },
                "MEDIUM",
                "Manual",
                "The changed paths did not match a known feature area; inspect their callers and dependents before release.",
                listOf("Review every changed hunk.", "Identify direct callers, persisted data and user-visible behavior.", "Exercise the changed behavior and its failure path."),
                "The changed behavior works and no dependent feature is regressed.",
                "Reviewer checklist and focused manual test results."
            )
        }

        add(
            "Application launch and navigation smoke test",
            "App-wide",
            "HIGH",
            "Manual + instrumented",
            "Every patch should preserve app startup and access to existing destinations.",
            listOf("Install or upgrade the app.", "Launch and navigate to the changed destination.", "Return to the main screen and open at least one unaffected destination."),
            "The app launches without crash and both changed and unaffected destinations remain reachable.",
            "Launch result, crash-free navigation and instrumentation output."
        )
        add(
            "Existing-feature preservation check",
            "Cross-feature regression",
            "HIGH",
            "Manual",
            "The project requires additive changes; unrelated working features must remain available.",
            listOf("Compare the changed screen with its pre-patch feature list or baseline.", "Exercise adjacent workflows that share the changed classes or routes.", "Check that no settings, controls, assets or navigation entries disappeared."),
            "Unrelated features remain visible and behave as before.",
            "Before/after screenshots and a checked feature-preservation list."
        )

        val notes = mutableListOf<String>()
        if (risk.files.any { it.isDeleted }) notes += "Deleted files were detected. Verify all references, resources and feature entry points that depended on them."
        if (paths.size > 5) notes += "Patch spans more than five files; run cross-module integration checks."
        if (risk.riskLevel.startsWith("HIGH", ignoreCase = true)) notes += "Static patch risk is HIGH; complete security/permission review before applying."
        if (fileSections.any { it.addedText.contains("migration", ignoreCase = true) || it.path.contains("migration", ignoreCase = true) }) {
            notes += "A migration-related change may require upgrade tests using a copy of existing user data."
        }
        notes += "Test cases are recommendations generated from changed paths and added diff text; they may not discover every dependency."
        return Plan(risk.riskLevel, paths, cases, notes)
    }

    private data class PatchSection(val path: String, val addedText: String, val isDeleted: Boolean)

    private fun parseSections(patch: String): List<PatchSection> {
        val lines = patch.lineSequence().toList()
        val starts = lines.indices.filter { lines[it].startsWith("diff --git ") }
        return starts.mapIndexed { index, start ->
            val end = starts.getOrNull(index + 1) ?: lines.size
            val section = lines.subList(start, end)
            val header = Regex("""^diff --git a/(.+) b/(.+)$""").matchEntire(lines[start])
            val path = header?.groupValues?.get(2).orEmpty()
            val added = section.filter { it.startsWith("+") && !it.startsWith("+++") }
                .joinToString("\n") { it.drop(1) }
            PatchSection(
                path = path,
                addedText = added,
                isDeleted = section.any { it == "+++ /dev/null" }
            )
        }
    }
}
