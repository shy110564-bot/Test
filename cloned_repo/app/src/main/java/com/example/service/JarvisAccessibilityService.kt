package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

data class ScreenUiElement(
    val index: Int,
    val label: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val centerX: Int,
    val centerY: Int
)

data class ScreenInspectionReport(
    val activePackage: String,
    val visibleTexts: List<String>,
    val interactiveElements: List<ScreenUiElement>
)

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JarvisAccessService"

        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set

        @Volatile
        var isAiScreenShareActive: Boolean = false

        @Volatile
        var screenSharePartnerName: String = ""

        @Volatile
        var isScreenSharePrivacyApproved: Boolean = false

        @Volatile
        var latestScreenSummary: String = "Ready to view and control your screen"

        @Volatile
        var latestMediaProjectionBase64: String? = null

        fun isRunning(): Boolean = instance != null

        fun getScreenShareBannerText(): String {
            if (!isAiScreenShareActive) {
                return "Screen share band hai • Tap Share Screen or say 'Screen share karo Rahul ke saath'"
            }
            return if (screenSharePartnerName.isNotBlank()) {
                "Screen share ON hai, $screenSharePartnerName dekh rahe hain"
            } else {
                "Screen share ON hai • Live AI & Call Screen Share Active"
            }
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Jarvis Accessibility Screen Control Service connected")
    }

    private var lastA11yMessageKey = ""
    private var lastA11yMessageTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (isAiScreenShareActive) {
            val pkg = event.packageName?.toString() ?: ""
            if (pkg.isNotBlank() && !pkg.contains("systemui")) {
                val texts = collectVisibleScreenTexts().take(4)
                if (texts.isNotEmpty()) {
                    latestScreenSummary = if (screenSharePartnerName.isNotBlank()) {
                        "Screen share ON hai, $screenSharePartnerName dekh rahe hain • (${texts.take(2).joinToString(", ")})"
                    } else {
                        "Viewing ($pkg): ${texts.joinToString(" • ")}"
                    }
                }
            }
        }

        // Rule 5: "Notification aaye toh show karo, chhupao nahi"
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: ""
            val textList = event.text
            if (!textList.isNullOrEmpty() && !pkg.contains("systemui") && pkg != packageName) {
                val combined = textList.joinToString(" ").trim()
                val lower = combined.lowercase()
                if (combined.isNotBlank() &&
                    !lower.contains("checking for new messages") &&
                    !lower.contains("backup in progress") &&
                    !lower.contains("whatsapp web")
                ) {
                    val now = System.currentTimeMillis()
                    if (combined != lastA11yMessageKey || (now - lastA11yMessageTime) > 4000L) {
                        lastA11yMessageKey = combined
                        lastA11yMessageTime = now
                        val appLabel = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                        com.example.MainActivity.instance?.displayIncomingNotification(appLabel, "Notification", combined)
                        if ((pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") && !JarvisNotificationListenerService.isConnected) {
                            val announcement = "Jaan, WhatsApp par message aaya hai, $combined"
                            JarvisBackgroundService.instance?.speakAnnouncement(announcement)
                                ?: com.example.MainActivity.instance?.speakFromUI(announcement)
                        }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Jarvis Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "Jarvis Accessibility Service destroyed")
    }

    /**
     * Captures a live screenshot of the current screen as a Base64-encoded JPEG for Gemini AI Vision.
     * Uses Android 11+ AccessibilityService.takeScreenshot or falls back to MediaProjection frame.
     */
    suspend fun captureScreenBase64(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val captured = suspendCancellableCoroutine<String?> { cont ->
                try {
                    takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshot: ScreenshotResult) {
                                try {
                                    val hwBuffer = screenshot.hardwareBuffer
                                    val colorSpace = screenshot.colorSpace
                                    val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                                    hwBuffer.close()
                                    if (hwBitmap != null) {
                                        val softBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                        hwBitmap.recycle()
                                        val scaled = scaleBitmapDown(softBitmap, 720)
                                        val out = ByteArrayOutputStream()
                                        scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
                                        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                                        latestMediaProjectionBase64 = b64
                                        if (cont.isActive) cont.resume(b64)
                                    } else {
                                        if (cont.isActive) cont.resume(latestMediaProjectionBase64)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Screenshot encode error: ${e.message}")
                                    if (cont.isActive) cont.resume(latestMediaProjectionBase64)
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                Log.w(TAG, "takeScreenshot failed with code $errorCode")
                                if (cont.isActive) cont.resume(latestMediaProjectionBase64)
                            }
                        }
                    )
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(latestMediaProjectionBase64)
                }
            }
            if (!captured.isNullOrBlank()) return captured
        }
        return latestMediaProjectionBase64
    }

    private fun scaleBitmapDown(src: Bitmap, maxDimension: Int): Bitmap {
        val width = src.width
        val height = src.height
        if (width <= maxDimension && height <= maxDimension) return src
        val ratio = width.toFloat() / height.toFloat()
        val (newW, newH) = if (ratio > 1f) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    /**
     * Inspects the complete current screen hierarchy, returning active app package,
     * all readable texts, and all interactive elements with their screen (x, y) coordinates.
     */
    fun inspectFullScreenState(): ScreenInspectionReport {
        val root = rootInActiveWindow ?: return ScreenInspectionReport(
            activePackage = "unknown",
            visibleTexts = emptyList(),
            interactiveElements = emptyList()
        )
        val pkg = root.packageName?.toString() ?: "unknown"
        val texts = mutableListOf<String>()
        val elements = mutableListOf<ScreenUiElement>()
        collectFullNodesRecursive(root, texts, elements)

        val cleanTexts = texts.distinct().take(35)
        if (cleanTexts.isNotEmpty()) {
            latestScreenSummary = "App: ${pkg.substringAfterLast('.')} • ${cleanTexts.take(5).joinToString(", ")}"
        }
        return ScreenInspectionReport(
            activePackage = pkg,
            visibleTexts = cleanTexts,
            interactiveElements = elements.take(30)
        )
    }

    private fun collectFullNodesRecursive(
        node: AccessibilityNodeInfo,
        texts: MutableList<String>,
        elements: MutableList<ScreenUiElement>
    ) {
        val t = node.text?.toString()?.trim()
        val d = node.contentDescription?.toString()?.trim()
        val label = when {
            !t.isNullOrEmpty() && !d.isNullOrEmpty() && t != d -> "$t ($d)"
            !t.isNullOrEmpty() -> t
            !d.isNullOrEmpty() -> d
            else -> ""
        }

        if (!t.isNullOrEmpty() && t.length < 100) texts.add(t)
        if (!d.isNullOrEmpty() && d.length < 100 && d != t) texts.add(d)

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.isEmpty && rect.width() > 10 && rect.height() > 10) {
            if ((node.isClickable || node.isEditable) && label.isNotBlank()) {
                elements.add(
                    ScreenUiElement(
                        index = elements.size + 1,
                        label = label.take(70),
                        isClickable = node.isClickable,
                        isEditable = node.isEditable,
                        centerX = rect.centerX(),
                        centerY = rect.centerY()
                    )
                )
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectFullNodesRecursive(child, texts, elements)
        }
    }

    /**
     * Clicks the Nth clickable content item on screen (e.g. 1st video, 2nd result)
     */
    fun clickNthItemOnScreen(nth: Int): String? {
        val report = inspectFullScreenState()
        val candidates = report.interactiveElements.filter {
            it.isClickable && it.centerY > 180 && it.label.length > 3 &&
                !it.label.equals("Search", ignoreCase = true) &&
                !it.label.equals("Back", ignoreCase = true) &&
                !it.label.equals("Navigate up", ignoreCase = true)
        }
        val target = candidates.getOrNull((nth - 1).coerceAtLeast(0))
            ?: report.interactiveElements.getOrNull((nth - 1).coerceAtLeast(0))
            ?: return null

        clickAt(target.centerX.toFloat(), target.centerY.toFloat())
        return target.label
    }

    /**
     * Dispatches a tap gesture at the specified screen coordinates (x, y)
     */
    fun clickAt(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onComplete?.invoke(false)
            return false
        }

        val clickPath = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 55))
            .build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gesture click succeeded at ($x, $y)")
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture click cancelled at ($x, $y)")
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Searches for any UI element containing [text] and clicks it
     */
    fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return false

        val nodes = root.findAccessibilityNodeInfosByText(cleanText)
        if (!nodes.isNullOrEmpty()) {
            for (node in nodes) {
                if (performClickOnNode(node)) {
                    return true
                }
            }
        }

        val targetNode = findNodeRecursive(root, cleanText.lowercase())
        if (targetNode != null) {
            return performClickOnNode(targetNode)
        }

        return false
    }

    private fun findNodeRecursive(node: AccessibilityNodeInfo, targetLower: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (nodeText.contains(targetLower) || contentDesc.contains(targetLower)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeRecursive(child, targetLower)
            if (found != null) return found
        }
        return null
    }

    private fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) return true
        }

        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                val clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) return true
            }
            parent = parent.parent
            depth++
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.isEmpty) {
            val cx = rect.centerX().toFloat()
            val cy = rect.centerY().toFloat()
            return clickAt(cx, cy)
        }

        return false
    }

    /**
     * Vertical screen scroll (down = scroll down / swipe up)
     */
    fun scroll(down: Boolean = true): Boolean {
        val root = rootInActiveWindow
        if (root != null) {
            val scrollable = findScrollableNode(root)
            if (scrollable != null) {
                val action = if (down) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                if (scrollable.performAction(action)) {
                    return true
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val dm = resources.displayMetrics
            val startX = dm.widthPixels / 2f
            val startY = if (down) dm.heightPixels * 0.75f else dm.heightPixels * 0.25f
            val endY = if (down) dm.heightPixels * 0.25f else dm.heightPixels * 0.75f

            val swipePath = Path().apply {
                moveTo(startX, startY)
                lineTo(startX, endY)
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(swipePath, 0, 300))
                .build()

            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    fun clickNormalized(pctX: Float, pctY: Float): Boolean {
        val dm = resources.displayMetrics
        val x = (pctX.coerceIn(0f, 1f)) * dm.widthPixels
        val y = (pctY.coerceIn(0f, 1f)) * dm.heightPixels
        return clickAt(x, y)
    }

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 300): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val dm = resources.displayMetrics
        val p1 = Path().apply {
            moveTo(fromX * dm.widthPixels, fromY * dm.heightPixels)
            lineTo(toX * dm.widthPixels, toY * dm.heightPixels)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun collectVisibleScreenTexts(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<String>()
        collectTextsRecursive(root, results)
        return results.distinct().take(30)
    }

    private fun collectTextsRecursive(node: AccessibilityNodeInfo, list: MutableList<String>) {
        val t = node.text?.toString()?.trim()
        val d = node.contentDescription?.toString()?.trim()
        if (!t.isNullOrEmpty() && t.length < 80) list.add(t)
        if (!d.isNullOrEmpty() && d.length < 80 && d != t) list.add(d)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextsRecursive(child, list)
        }
    }

    fun clickCenter(): Boolean = clickNormalized(0.5f, 0.5f)

    /**
     * Types text into the currently focused or first available editable field on screen,
     * and optionally triggers Enter/Search action.
     */
    fun typeText(text: String, submitAfter: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: findEditableNode(root)

        if (targetNode != null) {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val setOk = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (submitAfter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                targetNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }
            if (setOk) return true
        }
        return false
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    fun doubleClickAt(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val clickPath = Path().apply { moveTo(x, y) }
        val stroke1 = GestureDescription.StrokeDescription(clickPath, 0, 40)
        val stroke2 = GestureDescription.StrokeDescription(clickPath, 100, 40)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun longPressAt(x: Float, y: Float, durationMs: Long = 1000): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val clickPath = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun smartClick(intent: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val clean = intent.trim().lowercase()

        if (clickByText(clean)) return true

        val candidateKeywords = when {
            clean in listOf("search", "find", "dhundo", "khojo") -> listOf("search", "find", "explore", "magnify", "go")
            clean in listOf("send", "bhejo", "submit", "enter") -> listOf("send", "submit", "post", "done", "ok", "forward")
            clean in listOf("play", "chalao", "bajao", "start", "video") -> listOf("play", "resume", "watch", "start")
            clean in listOf("pause", "roko", "thamo") -> listOf("pause", "stop", "hold")
            clean in listOf("close", "band karo", "cancel", "hatao") -> listOf("close", "cancel", "dismiss", "x", "no", "not now", "skip")
            clean in listOf("accept", "allow", "yes", "theek hai", "ha") -> listOf("allow", "agree", "accept", "continue", "ok", "yes", "confirm", "grant")
            clean in listOf("next", "aage", "forward") -> listOf("next", "continue", "forward", ">")
            clean in listOf("back", "piche") -> listOf("back", "previous", "<")
            clean in listOf("subscribe", "like", "share", "comment") -> listOf(clean)
            else -> listOf(clean)
        }

        for (kw in candidateKeywords) {
            val node = findNodeRecursive(root, kw)
            if (node != null && performClickOnNode(node)) {
                return true
            }
        }

        return false
    }

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun openPowerDialog(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    fun takeScreenshot(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    } else false
    fun lockScreen(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    } else false

    /**
     * Automatically taps the first YouTube search result after YouTube search page loads
     * when the user asks to play/show a video or trailer (e.g., "YouTube pe Shah Rukh Khan ka new movie trailer dikhao")
     */
    fun scheduleAutoPlayFirstYouTubeResult() {
        mainHandler.postDelayed({
            try {
                val clickedLabel = clickNthItemOnScreen(1)
                if (clickedLabel == null) {
                    clickNormalized(0.5f, 0.36f)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Auto-play first YouTube result fallback: ${e.message}")
            }
        }, 2300L)
    }

    /**
     * Automatically clicks the WhatsApp Send button after opening a chat with pre-filled text
     * (e.g., "WhatsApp pe Mummy ko message karo 'main aa raha hun'")
     */
    fun scheduleWhatsAppAutoSend() {
        mainHandler.postDelayed({
            try {
                if (!smartClick("Send")) {
                    clickByText("Send")
                }
            } catch (e: Exception) {
                Log.w(TAG, "WhatsApp auto-send fallback: ${e.message}")
            }
        }, 1800L)
    }

    /**
     * Automatically clicks the Reels tab after opening Instagram
     * ("Instagram pe Reels dekho" -> Instagram -> Reels tab)
     */
    fun scheduleInstagramReelsTab() {
        mainHandler.postDelayed({
            try {
                if (!clickByText("Reels")) {
                    // Bottom bar Reels icon position in Instagram
                    clickNormalized(0.7f, 0.94f)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Instagram Reels tab click fallback: ${e.message}")
            }
        }, 1900L)
    }

    /**
     * Automatically taps "Share screen" / "Start now" inside a video call when user asks
     * "Screen share karo [contact name] ke saath"
     */
    fun scheduleWhatsAppScreenShareClick() {
        mainHandler.postDelayed({
            try {
                smartClick("Video call")
                mainHandler.postDelayed({
                    if (!clickByText("Share screen") && !clickByText("Screen share")) {
                        smartClick("Share")
                    }
                }, 2200L)
            } catch (e: Exception) {
                Log.w(TAG, "WhatsApp screen share automation fallback: ${e.message}")
            }
        }, 1600L)
    }
}
