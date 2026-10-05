package com.swan1127.repland

import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
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

    /** Native display coordinates, not Compose's clipped semantics bounds. */
    fun assertDialogWithinDisplay(rule: ComposeTestRule, evidenceName: String) {
        rule.waitForIdle()
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val root = requireNotNull(automation.rootInActiveWindow)
        val screenshot = requireNotNull(automation.takeScreenshot())
        try {
            val window = requireNotNull(root.window)
            val bounds = android.graphics.Rect().also(window::getBoundsInScreen)
            val display = android.graphics.Rect(0, 0, screenshot.width, screenshot.height)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            java.io.File(context.getExternalFilesDir(null), "$evidenceName.png").outputStream().use {
                check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            val contentSize = rule.onNodeWithTag("execution-session").fetchSemanticsNode().layoutInfo.coordinates.size
            val evidence = "focused=${window.isFocused}; display=$display; window=$bounds; measuredContent=$contentSize"
            java.io.File(context.getExternalFilesDir(null), "$evidenceName.txt").writeText(evidence)
            org.junit.Assert.assertTrue(evidence, window.isFocused && display.contains(bounds))
            org.junit.Assert.assertTrue("Content must fit native window: $evidence", contentSize.width <= bounds.width())
            org.junit.Assert.assertTrue("Content height must fit native window: $evidence", contentSize.height <= bounds.height())
        } finally { root.recycle(); screenshot.recycle() }
    }

    fun assertControlWithinWindow(rule: ComposeTestRule, tag: String) {
        rule.waitForIdle()
        val root = requireNotNull(automation.rootInActiveWindow)
        try {
            val bounds = android.graphics.Rect().also(requireNotNull(root.window)::getBoundsInScreen)
            val coordinates = rule.onNodeWithTag(tag).fetchSemanticsNode().layoutInfo.coordinates
            val origin = coordinates.localToWindow(Offset.Zero)
            val size = coordinates.size
            org.junit.Assert.assertTrue("$tag must be fully reachable: window=$bounds, origin=$origin, size=$size",
                origin.x >= 0 && origin.y >= 0 && origin.x + size.width <= bounds.width() + 1 &&
                    origin.y + size.height <= bounds.height() + 1)
        } finally { root.recycle() }
    }
}
