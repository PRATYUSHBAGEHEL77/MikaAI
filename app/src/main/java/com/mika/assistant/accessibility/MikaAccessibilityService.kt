package com.mika.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executor

class MikaAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun readVisibleTree(maxNodes: Int = 120): String {
        val out = StringBuilder()
        var count = 0
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || count >= maxNodes) return
            count++
            val text = node.text?.toString()?.take(120).orEmpty()
            val desc = node.contentDescription?.toString()?.take(120).orEmpty()
            val id = node.viewIdResourceName.orEmpty()
            if (text.isNotBlank() || desc.isNotBlank() || id.isNotBlank()) {
                out.append("  ".repeat(depth)).append("text=").append(text)
                    .append(" desc=").append(desc).append(" id=").append(id)
                    .append(" clickable=").append(node.isClickable).append('\n')
            }
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        visit(rootInActiveWindow, 0)
        return out.toString().ifBlank { "No accessible UI nodes are available." }
    }

    fun tapNode(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findAccessibilityNodeInfosByText(label).firstOrNull() ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun tap(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 350): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun scrollForward(): Boolean = rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun screenshot(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) { callback(null); return }
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                result.hardwareBuffer.close()
                callback(bitmap?.copy(Bitmap.Config.ARGB_8888, false))
            }
            override fun onFailure(errorCode: Int) { callback(null) }
        })
    }
}
