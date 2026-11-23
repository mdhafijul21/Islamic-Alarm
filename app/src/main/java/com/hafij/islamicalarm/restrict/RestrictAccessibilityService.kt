package com.hafij.islamicalarm.restrict

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Watches foreground packages for Restrict Mode and monitors browsers,
 * video downloaders (VidMate, SnapTube), Telegram, and video players for Safe Mode:
 *  - Entering any known adult/NSFW site (in browsers OR VidMate/SnapTube/Telegram WebViews)
 *    triggers Safe Mode immediately.
 *  - Launching a video player / media viewer (including Telegram PhotoViewer, VidMate,
 *    SnapTube, MX Player, VLC, HTML5 video) with adult/NSFW or user-blocked keywords
 *    triggers Safe Mode with full-screen blur + automatic Back/Home exit.
 *  - Plainly searching "adult" or "nsfw" on a search engine (without opening an adult URL
 *    or video) does NOT trigger Safe Mode.
 */
class RestrictAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { RestrictStore(applicationContext) }
    private val safeStore by lazy { SafeModeStore(applicationContext) }
    private val windowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var lastPackage: String? = null
    private var ignored: Set<String> = setOf("com.android.systemui", "android")
    private var lastBlockAt = 0L
    private var lastSafeAt = 0L
    private var pendingScanRunnable: Runnable? = null
    private var blurOverlayView: View? = null

    private val ticker = object : Runnable {
        override fun run() {
            checkNow()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ignored = buildIgnoredPackages()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg in ignored) return
                lastPackage = pkg
                checkNow()
                if (safeStore.isEnabled && pkg != packageName) {
                    scheduleSafeScan(pkg, STATE_SCAN_DELAY_MS, urgent = true)
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (!safeStore.isEnabled) return
                val pkg = event.packageName?.toString() ?: return
                if (pkg == packageName || pkg in ignored) return
                scheduleSafeScan(pkg, PAGE_SCAN_DELAY_MS, urgent = false)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(ticker)
        pendingScanRunnable?.let { handler.removeCallbacks(it) }
        removeBlurOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        pendingScanRunnable?.let { handler.removeCallbacks(it) }
        removeBlurOverlay()
        super.onDestroy()
    }

    private fun checkNow() {
        val pkg = lastPackage ?: return
        if (pkg == packageName) return

        val blockEnd = store.blockEndFor(pkg, System.currentTimeMillis())
        if (blockEnd <= 0L) return

        val tick = SystemClock.elapsedRealtime()
        if (tick - lastBlockAt < 700L) return
        lastBlockAt = tick

        lastPackage = packageName
        showBreakScreen(pkg)
    }

    private fun showBreakScreen(pkg: String) {
        try {
            val intent = Intent(this, RestrictBlockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(RestrictBlockActivity.EXTRA_PACKAGE, pkg)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            lastPackage = pkg
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    // ------------------------------------------------------------------
    // Safe Mode: triggers on entering an adult/NSFW domain OR when a video
    // player opens (browsers, VidMate, SnapTube, Telegram, TeraBox, players)
    // with adult/NSFW/blocked context. Never triggers just from plain search.
    // ------------------------------------------------------------------

    private fun scheduleSafeScan(pkg: String, delayMs: Long, urgent: Boolean) {
        if (urgent) {
            pendingScanRunnable?.let { handler.removeCallbacks(it) }
            pendingScanRunnable = null
        } else if (pendingScanRunnable != null) {
            return
        }

        val runnable = Runnable {
            pendingScanRunnable = null
            if (!safeStore.isEnabled) return@Runnable
            if (pkg == packageName || pkg in ignored) return@Runnable
            val tick = SystemClock.elapsedRealtime()
            if (tick - lastSafeAt < SAFE_COOLDOWN_MS) return@Runnable

            if (shouldTriggerSafeOnWindows(pkg)) {
                onSafeViolation(pkg)
            }
        }
        pendingScanRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun shouldTriggerSafeOnWindows(pkg: String): Boolean {
        val custom = safeStore.getCustomWords()
        val roots = collectPackageRoots(pkg)
        if (roots.isEmpty()) return false

        // Merge analysis across all interactive windows of this app (so e.g. Telegram's
        // floating PhotoViewer video window combines with captions or URL bars cleanly)
        var anyAdultUrl = false
        var anyVideoPlayer = false
        var anyBlockedText = false

        val isMediaApp = isMediaOrSocialVideoApp(pkg)
        for (r in roots) {
            val analysis = inspectScreenTree(r, custom, isMediaApp)
            if (analysis.hasAdultUrl) {
                anyAdultUrl = true
                break
            }
            if (analysis.hasVideoPlayer) anyVideoPlayer = true
            if (analysis.hasBlockedText) anyBlockedText = true
            if (anyVideoPlayer && anyBlockedText) break
        }

        return anyAdultUrl || (anyVideoPlayer && anyBlockedText)
    }

    private fun collectPackageRoots(pkg: String): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        try {
            val active = rootInActiveWindow
            if (active != null && active.packageName?.toString() == pkg) {
                list.add(active)
            }
            val appWindows = windows
            if (appWindows != null) {
                for (w in appWindows) {
                    val r = w.root ?: continue
                    if (r.packageName?.toString() == pkg && list.none { it == r }) {
                        list.add(r)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    internal fun shouldTriggerSafeVideoBlock(root: AccessibilityNodeInfo): Boolean {
        val custom = safeStore.getCustomWords()
        val pkg = root.packageName?.toString().orEmpty()
        val analysis = inspectScreenTree(root, custom, isMediaOrSocialVideoApp(pkg))
        return analysis.hasAdultUrl || (analysis.hasVideoPlayer && analysis.hasBlockedText)
    }

    private fun isMediaOrSocialVideoApp(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return MEDIA_VIDEO_PACKAGE_HINTS.any { lower.contains(it) }
    }

    private data class ScreenAnalysis(
        val hasVideoPlayer: Boolean,
        val hasAdultUrl: Boolean,
        val hasBlockedText: Boolean
    )

    private fun inspectScreenTree(
        root: AccessibilityNodeInfo,
        custom: Collection<String>,
        isMediaApp: Boolean
    ): ScreenAnalysis {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var hasVideoPlayer = false
        var hasSeekBar = false
        var hasTimestampText = false
        var hasAdultUrl = false
        var hasBlockedText = false
        val rect = Rect()

        while (queue.isNotEmpty() && visited < MAX_SCAN_NODES) {
            val node = queue.removeFirst()
            visited++

            val cls = node.className?.toString().orEmpty()
            val clsLower = cls.lowercase()
            val viewId = node.viewIdResourceName?.lowercase().orEmpty()
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            val textLower = text.lowercase()
            val descLower = desc.lowercase()
            val roleDesc = node.extras
                ?.getCharSequence("AccessibilityNodeInfo.roleDescription")
                ?.toString()
                ?.lowercase()
                .orEmpty()

            // 1. Detect video player / video surface / HTML5 video / Telegram PhotoViewer / VidMate / SnapTube player
            if (!hasVideoPlayer) {
                if (VIDEO_CLASS_HINTS.any { clsLower.contains(it) } || roleDesc == "video") {
                    hasVideoPlayer = true
                } else if (clsLower.endsWith(".surfaceview") || clsLower.endsWith(".textureview")) {
                    node.getBoundsInScreen(rect)
                    if (rect.width() >= 100 && rect.height() >= 70) {
                        hasVideoPlayer = true
                    }
                } else if (VIDEO_ID_HINTS.any { viewId.contains(it) }) {
                    hasVideoPlayer = true
                } else if (VIDEO_LABEL_HINTS.any { descLower.contains(it) || textLower.contains(it) }) {
                    hasVideoPlayer = true
                } else if (isMediaApp && VIDEO_EXT_REGEX.containsMatchIn(textLower)) {
                    hasVideoPlayer = true
                } else if (clsLower.endsWith(".seekbar") || clsLower.contains("seekbar")) {
                    hasSeekBar = true
                }
            }

            if (!hasTimestampText && TIMESTAMP_REGEX.containsMatchIn(text)) {
                hasTimestampText = true
            }

            // 2. Address bar & WebView URL check (browsers + VidMate / SnapTube / Telegram in-app browser)
            val isUrlBar = URL_BAR_ID_HINTS.any { viewId.contains(it) }
            if (!hasAdultUrl && text.isNotBlank()) {
                if (isUrlBar && !node.isFocused) {
                    if (SafeWords.matchesSiteUrl(text, custom)) {
                        hasAdultUrl = true
                    }
                } else if (!node.isEditable && text.length in 5..350 && looksLikeUrlText(text)) {
                    node.getBoundsInScreen(rect)
                    val topLimit = if (isMediaApp) 720 else 440
                    if ((isMediaApp || rect.top in 0..topLimit) && SafeWords.matchesSiteUrl(text, custom)) {
                        hasAdultUrl = true
                    }
                }
            }
            if (!hasAdultUrl && isUrlBar && !node.isFocused && desc.isNotBlank()) {
                if (SafeWords.matchesSiteUrl(desc, custom)) {
                    hasAdultUrl = true
                }
            }

            // 3. Non-password, non-editable rendered text / description / WebView / caption / title check
            if (!hasBlockedText && !node.isPassword && !node.isEditable) {
                if ((text.isNotBlank() && SafeWords.matches(text, custom)) ||
                    (desc.isNotBlank() && SafeWords.matches(desc, custom))
                ) {
                    hasBlockedText = true
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.add(child)
            }
        }

        if (!hasVideoPlayer && ((hasSeekBar && hasTimestampText) || (isMediaApp && DURATION_PAIR_REGEX.containsMatchIn(root.text?.toString().orEmpty())))) {
            hasVideoPlayer = true
        }
        // In dedicated video/chat apps, a seekbar or playback timestamp (e.g. 00:12 / 05:30) indicates an active player
        if (!hasVideoPlayer && isMediaApp && hasTimestampText) {
            hasVideoPlayer = true
        }

        return ScreenAnalysis(
            hasVideoPlayer = hasVideoPlayer,
            hasAdultUrl = hasAdultUrl,
            hasBlockedText = hasBlockedText
        )
    }

    private fun looksLikeUrlText(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.contains(' ')) return false
        return t.startsWith("http://") ||
            t.startsWith("https://") ||
            DOMAIN_LIKE_REGEX.containsMatchIn(t)
    }

    private fun onSafeViolation(pkg: String) {
        lastSafeAt = SystemClock.elapsedRealtime()
        pendingScanRunnable?.let { handler.removeCallbacks(it) }
        pendingScanRunnable = null

        if (Settings.canDrawOverlays(this)) {
            showBlurOverlay()
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({
                if (safeStore.isEnabled && shouldTriggerSafeOnWindows(pkg)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }, SECOND_BACK_CHECK_MS)
            handler.postDelayed({
                if (safeStore.isEnabled && shouldTriggerSafeOnWindows(pkg)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }, HOME_FALLBACK_CHECK_MS)
            handler.postDelayed({ removeBlurOverlay() }, BLUR_DURATION_MS)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
            try {
                val intent = Intent(this, SafeBlockActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun showBlurOverlay() {
        removeBlurOverlay()
        try {
            val density = resources.displayMetrics.density
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val pad = (28 * density).toInt()
                setPadding(pad, pad, pad, pad)
                val bgColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Color.argb(205, 10, 46, 35)
                } else {
                    Color.argb(242, 10, 46, 35)
                }
                setBackgroundColor(bgColor)
            }

            val tvIcon = TextView(this).apply {
                text = "🛡️"
                textSize = 48f
                gravity = Gravity.CENTER
            }
            val tvTitle = TextView(this).apply {
                text = "সেফ মোড সক্রিয়"
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, (10 * density).toInt(), 0, (6 * density).toInt())
            }
            val tvSubtitle = TextView(this).apply {
                text = "অনুপযুক্ত সাইট বা ভিডিও শনাক্ত হওয়ায় স্ক্রিন ব্লার করে ফিরে যাওয়া হচ্ছে..."
                textSize = 14f
                setTextColor(Color.argb(235, 220, 245, 235))
                gravity = Gravity.CENTER
            }
            container.addView(tvIcon)
            container.addView(tvTitle)
            container.addView(tvSubtitle)

            @Suppress("DEPRECATION")
            var layoutFlags = (
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                layoutFlags = layoutFlags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                layoutFlags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    blurBehindRadius = 64
                }
            }

            windowManager.addView(container, params)
            blurOverlayView = container
        } catch (e: Exception) {
            e.printStackTrace()
            blurOverlayView = null
        }
    }

    private fun removeBlurOverlay() {
        val view = blurOverlayView ?: return
        blurOverlayView = null
        try {
            if (view.isAttachedToWindow) {
                windowManager.removeViewImmediate(view)
            }
        } catch (e: Exception) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildIgnoredPackages(): Set<String> {
        val set = mutableSetOf("com.android.systemui", "android")
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            for (method in imm.enabledInputMethodList) {
                set.add(method.packageName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return set
    }

    companion object {
        private const val MAX_SCAN_NODES = 1400
        private const val STATE_SCAN_DELAY_MS = 60L
        private const val PAGE_SCAN_DELAY_MS = 140L
        private const val SAFE_COOLDOWN_MS = 1300L
        private const val SECOND_BACK_CHECK_MS = 320L
        private const val HOME_FALLBACK_CHECK_MS = 800L
        private const val BLUR_DURATION_MS = 1700L

        private val TIMESTAMP_REGEX = Regex("\\b\\d{1,2}:\\d{2}\\b")
        private val DURATION_PAIR_REGEX = Regex("\\b\\d{1,2}:\\d{2}\\s*/\\s*\\d{1,2}:\\d{2}\\b")
        private val VIDEO_EXT_REGEX = Regex("\\.(mp4|mkv|webm|avi|mov|flv|m3u8)\\b")
        private val DOMAIN_LIKE_REGEX = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+(/.*)?$")

        private val MEDIA_VIDEO_PACKAGE_HINTS = listOf(
            "vidmate", "snaptube", "dayuwuxian", "telegram", "nekogram",
            "ayugram", "terabox", "dubox", "mxtech", "videolan", "vlc",
            "playit", "xplayer", "reddit", "twitter", "ucmobile"
        )

        private val VIDEO_CLASS_HINTS = listOf(
            ".videoview", "videoplayer", "playerview", "exoplayer", "mediacontroller",
            "glsurfaceview", "ijk", "txcloud", "photoviewer",
            "videotimeline", "seekbarview", "webvideo"
        )

        private val VIDEO_ID_HINTS = listOf(
            "video_player", "player_view", "exoplayer", "exo_", "ijk",
            "media_controls", "video_container", "player_container",
            "html5_video", "yt_player", "jwplayer", "jw-", "videojs",
            "vjs-", "plyr", "fluid_video", "brightcove", "bitmovin", "shaka",
            "video_view", "play_pause", "fullscreen", "timeline", "seek_bar",
            "pip_button", "player_control"
        )

        private val VIDEO_LABEL_HINTS = listOf(
            "video player", "play video", "pause video",
            "exit full screen", "exit fullscreen", "enter full screen",
            "picture-in-picture", "seek slider", "playback speed", "mute video",
            "fast forward", "rewind"
        )

        private val URL_BAR_ID_HINTS = listOf(
            "url_bar", "url_bar_title", "address", "omnibox", "location_bar",
            "location_bar_edit_text", "mozac_browser_toolbar_url_view",
            "url_field", "address_bar", "search_box_text",
            "web_url", "et_url", "domain", "action_bar_title"
        )
    }
}
