package com.swan1127.repland

import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

/** Native input must target the dialog, not an Activity that is still finishing its launch. */
internal object NativeSheetTestInput {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    fun awaitFocusedDialog(rule: ComposeTestRule, visibleTitle: String) {
        rule.waitForIdle()
        // AccessibilityNodeInfo.window is null unless interactive-window retrieval is enabled.
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        rule.waitUntil(10_000) {
            val root = automation.rootInActiveWindow
            if (root == null) false else {
                // Compose virtual nodes are traversable but its provider need not implement text search.
                fun containsTitle(node: AccessibilityNodeInfo): Boolean {
                    if (node.text?.toString()?.contains(visibleTitle) == true) return true
                    for (index in 0 until node.childCount) {
                        val child = node.getChild(index) ?: continue
                        try { if (containsTitle(child)) return true } finally { child.recycle() }
                    }
                    return false
                }
                try { root.window?.isFocused == true && containsTitle(root) }
                finally { root.recycle() }
            }
        }
    }

    fun back(rule: ComposeTestRule, visibleTitle: String) {
        awaitFocusedDialog(rule, visibleTitle)
        shell("input keyevent 4")
    }
}
