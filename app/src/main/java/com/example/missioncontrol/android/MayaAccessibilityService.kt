package com.example.missioncontrol.android

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
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

    /** Set text in a visible editable node matched by its accessible text or hint. */
    fun setTextInVisibleField(label: String, value: String): Boolean {
        val cleanLabel = label.trim()
        if (cleanLabel.isEmpty() || cleanLabel.length > 100 || value.length > 20000) return false
        val root = rootInActiveWindow ?: return false
        return try {
            val matches = root.findAccessibilityNodeInfosByText(cleanLabel)
            val target = matches.firstOrNull { it.isVisibleToUser && it.isEnabled && it.isEditable }
                ?: findEditableByHint(root, cleanLabel)
            target?.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }
            ) == true
        } finally {
            root.recycle()
        }
    }

    private fun findEditableByHint(node: AccessibilityNodeInfo, hint: String): AccessibilityNodeInfo? {
        if (node.isVisibleToUser && node.isEnabled && node.isEditable &&
            (node.hintText?.toString()?.contains(hint, ignoreCase = true) == true ||
             node.viewIdResourceName?.contains(hint, ignoreCase = true) == true)
        ) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findEditableByHint(child, hint)
            if (match != null) return match
        }
        return null
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
