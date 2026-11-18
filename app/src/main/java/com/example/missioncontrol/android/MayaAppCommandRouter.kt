package com.example.missioncontrol.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.example.MainActivity

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
     * Returns null when the phrase is not an app-launch command.
     * Otherwise returns a user-facing result, including not-installed failures.
     */
    fun execute(command: String): String? {
        val normalized = command.trim().replace(Regex("\\s+"), " ")
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
                if (normalized.any { it in '\\u0980'..'\\u09FF' }) "মায়া Coding Agent খুলছি।"
                else "Opening Maya Android IDE Coding Agent."
            } catch (_: Exception) {
                if (normalized.any { it in '\\u0980'..'\\u09FF' }) "মায়া Coding Agent খোলা যায়নি।"
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
