package com.example.missioncontrol.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.example.MainActivity
import com.example.missioncontrol.MissionQueueRepository
import com.example.missioncontrol.MissionAgentRoster
import com.example.missioncontrol.MissionAgentTaskRepository

/**
 * Small, explicit voice-command router for launching supported apps.
 * It never clicks, types, sends messages, or changes device settings.
 */
class MayaAppCommandRouter(context: Context) {
    companion object { const val ACTION_OPEN_ANDROID_IDE = "com.example.maya.OPEN_ANDROID_IDE" }

    private val appContext = context.applicationContext

    private data class AppTarget(
        val displayName: String,
        val packageName: String,
        val patterns: List<Regex>
    )

    private val targets = listOf(
        AppTarget("YouTube", "com.google.android.youtube", listOf(
            Regex("^(open|launch|start)\\s+youtube$", RegexOption.IGNORE_CASE),
            Regex("^(ইউটিউব|youtube)\\s*(খোলো|চালু করো|ওপেন করো)$", RegexOption.IGNORE_CASE)
        )),
        AppTarget("TikTok", "com.zhiliaoapp.musically", listOf(
            Regex("^(open|launch|start)\\s+tiktok$", RegexOption.IGNORE_CASE),
            Regex("^টিকটক\\s*(খোলো|চালু করো|ওপেন করো)$", RegexOption.IGNORE_CASE)
        )),
        AppTarget("Facebook", "com.facebook.katana", listOf(
            Regex("^(open|launch|start)\\s+facebook$", RegexOption.IGNORE_CASE),
            Regex("^ফেসবুক\\s*(খোলো|চালু করো|ওপেন করো)$", RegexOption.IGNORE_CASE)
        )),
        AppTarget("Instagram", "com.instagram.android", listOf(
            Regex("^(open|launch|start)\\s+instagram$", RegexOption.IGNORE_CASE),
            Regex("^ইনস্টাগ্রাম\\s*(খোলো|চালু করো|ওপেন করো)$", RegexOption.IGNORE_CASE)
        )),
        AppTarget("Gmail", "com.google.android.gm", listOf(
            Regex("^(open|launch|start)\\s+gmail$", RegexOption.IGNORE_CASE),
            Regex("^জিমেইল\\s*(খোলো|চালু করো|ওপেন করো)$", RegexOption.IGNORE_CASE)
        )),
        AppTarget("Google Maps", "com.google.android.apps.maps", listOf(
            Regex("^(open|launch|start)\\s+(google\\s+)?maps$", RegexOption.IGNORE_CASE),
            Regex("^(গুগল ম্যাপস|ম্যাপস)\\s*(খোলো|চালু করো|ওপেন করো)$", RegexOption.IGNORE_CASE)
        ))
    )

    /**
     * Converts an explicit voice phrase into a device-local mission queue item.
     * The mission is saved only; it is not executed until the user starts it in Mission Center.
     */
    private fun createMissionFromCommand(normalized: String): String? {
        val english = Regex("^(?:create|add|save)\\s+(?:a\\s+)?mission\\s*[:,-]?\\s*(.+)$", RegexOption.IGNORE_CASE)
        val bangla = Regex("^(?:মিশন\\s*(?:তৈরি করো|বানাও|যোগ করো|সেভ করো))\\s*[:,-]?\\s*(.+)$")
        val payload = english.matchEntire(normalized)?.groupValues?.get(1)
            ?: bangla.matchEntire(normalized)?.groupValues?.get(1)
            ?: return null
        val parts = payload.split(Regex("\\s+(?:details?|বিস্তারিত)\\s*[:,-]?\\s*", RegexOption.IGNORE_CASE), limit = 2)
        val title = parts.first().trim().take(160)
        if (title.isBlank()) return "Please say a mission title after 'create mission'."
        val details = parts.getOrNull(1)?.trim()?.take(4000).orEmpty()
        return try {
            val saved = MissionQueueRepository(appContext).add(title, details)
            if (normalized.any { it in '\\u0980'..'\\u09FF' }) {
                "মিশন '${saved.title}' Mission Queue-তে সংরক্ষণ করেছি। চালু করতে Mission Center খুলে Run 16 Agents চাপুন।"
            } else {
                "Saved mission '${saved.title}' to the Mission Queue. Open Mission Center and tap Run 16 Agents when you are ready."
            }
        } catch (failure: Exception) {
            if (normalized.any { it in '\\u0980'..'\\u09FF' }) {
                "মিশন সংরক্ষণ করা যায়নি: ${failure.localizedMessage ?: "অজানা সমস্যা"}"
            } else {
                "Could not save the mission: ${failure.localizedMessage ?: "unknown error"}"
            }
        }
    }


    /**
     * Saves an explicit voice-requested specialist assignment to the local queue.
     * It never invokes Gemini or starts the assigned task.
     */
    private fun assignAgentTaskFromCommand(normalized: String): String? {
        val english = Regex(
            "^(?:assign|give)\\s+(.+?)\\s+to\\s+mission\\s+(.+?)\\s*[:,-]\\s*(.+)$",
            RegexOption.IGNORE_CASE
        )
        val bangla = Regex(
            "^এজেন্ট\\s+(.+?)\\s+কে\\s+মিশন\\s+(.+?)\\s+এ\\s+কাজ\\s+দাও\\s*[:,-]?\\s*(.+)$"
        )
        val match = english.matchEntire(normalized) ?: bangla.matchEntire(normalized) ?: return null
        val agentName = match.groupValues[1].trim()
        val missionTitle = match.groupValues[2].trim().trim('"', '\\'')
        val taskPayload = match.groupValues[3].trim()
        if (agentName.isBlank() || missionTitle.isBlank() || taskPayload.isBlank()) {
            return "Please include an agent, an exact mission title, and the task."
        }

        val agent = MissionAgentRoster.agents.firstOrNull {
            it.id.equals(agentName, ignoreCase = true) ||
                it.displayName.equals(agentName, ignoreCase = true)
        } ?: return "I couldn't match '$agentName' to one of the 16 specialist agents. Say the agent's exact name."

        val matchingMissions = MissionQueueRepository(appContext).getAll().filter {
            it.title.equals(missionTitle, ignoreCase = true)
        }
        if (matchingMissions.isEmpty()) {
            return if (normalized.any { it.code in 0x0980..0x09FF }) {
                "এই নামে কোনো Mission পাওয়া যায়নি। আগে Mission Center-এ '$missionTitle' তৈরি করুন।"
            } else {
                "No mission named '$missionTitle' was found. Create it in Mission Center first."
            }
        }
        if (matchingMissions.size > 1) {
            return "More than one mission has that exact title. Rename one mission in Mission Center, then try again."
        }

        val parts = taskPayload.split(
            Regex("\\s+(?:details?|বিস্তারিত)\\s*[:,-]?\\s*", RegexOption.IGNORE_CASE),
            limit = 2
        )
        val taskTitle = parts.first().trim().take(180)
        val instructions = parts.getOrNull(1)?.trim()?.take(5000).orEmpty()
        if (taskTitle.isBlank()) return "Please say the task after the mission title."

        return try {
            MissionAgentTaskRepository(appContext).create(
                missionId = matchingMissions.single().id,
                title = taskTitle,
                instructions = instructions,
                agentId = agent.id
            )
            if (normalized.any { it.code in 0x0980..0x09FF }) {
                "বস, '$taskTitle' কাজটি \${agent.displayName}-কে বরাদ্দ করে Queue-তে রেখেছি। চালু করিনি। Task Coordination খুলে Run চাপলে কাজ শুরু হবে।"
            } else {
                "Assigned '$taskTitle' to \${agent.displayName} and saved it in the queue. It has not been started. Open Task Coordination and press Run when ready."
            }
        } catch (failure: Exception) {
            if (normalized.any { it.code in 0x0980..0x09FF }) {
                "Agent assignment সংরক্ষণ করা যায়নি: \${failure.localizedMessage ?: "অজানা সমস্যা"}"
            } else {
                "Could not save the agent assignment: \${failure.localizedMessage ?: "unknown error"}"
            }
        }
    }

    /**
     * Returns null when the phrase is not an app-launch command.
     * Otherwise returns a user-facing result, including not-installed failures.
     */
    fun execute(command: String): String? {
        val normalized = command.trim().replace(Regex("\\s+"), " ")
        assignAgentTaskFromCommand(normalized)?.let { return it }
        createMissionFromCommand(normalized)?.let { return it }
        val openInstalledIde = listOf(
            Regex("^(open|launch|start)\\s+(android studio|android ide|androidide|codeassist|aide)$", RegexOption.IGNORE_CASE),
            Regex("^(অ্যান্ড্রয়েড স্টুডিও|অ্যান্ড্রয়েড আইডিই|কোডঅ্যাসিস্ট)\\s*(খোলো|চালু করো|ওপেন করো)$", RegexOption.IGNORE_CASE)
        ).any { it.matches(normalized) }
        if (openInstalledIde) {
            val packages = listOf("com.m.androidide", "com.itsaky.androidide", "com.tyron.code", "com.aide.ui")
            val launchIntent = packages.firstNotNullOfOrNull { appContext.packageManager.getLaunchIntentForPackage(it) }
            return if (launchIntent == null) {
                if (normalized.any { it.code in 2432..2559 }) "এই ফোনে সমর্থিত Android IDE ইনস্টল করা নেই।"
                else "No supported Android IDE is installed on this phone."
            } else {
                try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(launchIntent)
                    if (normalized.any { it.code in 2432..2559 }) "Android IDE খুলছি।"
                    else "Opening your installed Android IDE."
                } catch (_: Exception) {
                    if (normalized.any { it.code in 2432..2559 }) "Android IDE খোলা যায়নি।"
                    else "Could not open the installed Android IDE."
                }
            }
        }
        val openCodingAgent = listOf(
            Regex("^(open|launch|start)\\s+(maya\\s+)?(android\\s+)?(ide|coding agent|app factory|android app factory)$", RegexOption.IGNORE_CASE),
            Regex("^(মায়া কোডিং এজেন্ট|অ্যান্ড্রয়েড কোডিং এজেন্ট|অ্যাপ ফ্যাক্টরি)\\s*(খোলো|চালু করো|ওপেন করো)$", RegexOption.IGNORE_CASE)
        ).any { it.matches(normalized) }
        if (openCodingAgent) {
            return try {
                val intent = Intent(appContext, MainActivity::class.java).apply {
                    action = ACTION_OPEN_ANDROID_IDE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                appContext.startActivity(intent)
                if (normalized.any { it.code in 2432..2559 }) "মায়া Coding Agent খুলছি।"
                else "Opening Maya Android IDE Coding Agent."
            } catch (_: Exception) {
                if (normalized.any { it.code in 2432..2559 }) "মায়া Coding Agent খোলা যায়নি।"
                else "Could not open Maya Android IDE Coding Agent."
            }
        }
        val target = targets.firstOrNull { app -> app.patterns.any { it.matches(normalized) } }
            ?: return null

        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(target.packageName)
        if (launchIntent == null) {
            return if (normalized.any { it in '\u0980'..'\u09FF' }) {
                "${target.displayName} অ্যাপটি এই ফোনে ইনস্টল করা নেই।"
            } else {
                "${target.displayName} is not installed on this phone."
            }
        }

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(launchIntent)
            if (normalized.any { it in '\u0980'..'\u09FF' }) {
                "${target.displayName} খুলছি।"
            } else {
                "Opening ${target.displayName}."
            }
        } catch (_: ActivityNotFoundException) {
            if (normalized.any { it in '\u0980'..'\u09FF' }) {
                "${target.displayName} খোলা যায়নি।"
            } else {
                "Could not open ${target.displayName}."
            }
        } catch (_: SecurityException) {
            if (normalized.any { it in '\u0980'..'\u09FF' }) {
                "${target.displayName} খোলার অনুমতি পাওয়া যায়নি।"
            } else {
                "Permission denied while opening ${target.displayName}."
            }
        }
    }
}
