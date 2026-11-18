package com.example.missioncontrol.android

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * User-enabled Android accessibility bridge.
 *
 * This service deliberately does not inspect, persist, or upload screen contents.
 * Actions are exposed only as explicit methods; callers must obtain user approval
 * before invoking an action.
 */
class MayaAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var active: MayaAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No background collection, screen recording, or content logging.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    /** Tap one exact, visible text match after the caller has obtained confirmation. */
    fun tapVisibleText(phrase: String): Boolean {
        val clean = phrase.trim()
        if (clean.isEmpty() || clean.length > 80) return false
        val root = rootInActiveWindow ?: return false
        return try {
            val target = root.findAccessibilityNodeInfosByText(clean)
                .firstOrNull { it.isVisibleToUser && it.isEnabled }
            var clickable: AccessibilityNodeInfo? = target
            while (clickable != null && !clickable.isClickable) {
                clickable = clickable.parent
            }
            clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        } finally {
            root.recycle()
        }
    }
}
