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
 *  - Searching in SafeSearch / Google / Bing never triggers Safe Mode prematurely;
 *    only after clicking a result and opening an adult/NSFW domain or video player
 *    does Safe Mode activate, blur, and return Back.
 *  - When Incognito Block is enabled, opening an Incognito/Private browser tab triggers
 *    Safe Mode and exits Incognito immediately.
 *  - Launching a video player / media viewer (Telegram PhotoViewer, VidMate, SnapTube,
 *    MX Player, VLC, HTML5 video) with adult/NSFW or user-blocked keywords triggers blur + Back.
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
    // Safe Mode: triggers on Incognito tabs (when enabled), clicking into
    // an adult/NSFW domain, or launching an adult video player. Never
    // triggers prematurely on Search / SafeSearch result pages.
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

            val violation = detectSafeViolation(pkg)
            if (violation != ViolationType.NONE) {
                onSafeViolation(pkg, violation)
            }
        }
        pendingScanRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    internal enum class ViolationType {
        NONE,
        ADULT_SITE_OR_VIDEO,
        INCOGNITO
    }

    private fun detectSafeViolation(pkg: String): ViolationType {
        val custom = safeStore.getCustomWords()
        val roots = collectPackageRoots(pkg)
        if (roots.isEmpty()) return ViolationType.NONE

        val isMediaApp = isMediaOrSocialVideoApp(pkg)
        val isBrowser = isBrowserApp(pkg)
        val incognitoEnabled = safeStore.isIncognitoBlockEnabled

        var anyIncognito = false
        var anySearchResultsPage = false
        var anyAdultUrl = false
        var anyVideoPlayer = false
        var anyBlockedText = false

        for (r in roots) {
            val analysis = inspectScreenTree(
                root = r,
                custom = custom,
                isMediaApp = isMediaApp,
                isBrowser = isBrowser,
                checkIncognito = incognitoEnabled
            )
            if (analysis.hasIncognitoUi) anyIncognito = true
            if (analysis.hasSearchResultsUrl) anySearchResultsPage = true
            if (analysis.hasAdultUrl) anyAdultUrl = true
            if (analysis.hasVideoPlayer) anyVideoPlayer = true
            if (analysis.hasBlockedText) anyBlockedText = true
        }

        if (anyIncognito) return ViolationType.INCOGNITO
        // If user is currently viewing a Search / SafeSearch results page and hasn't clicked
        // through to an adult website or started an embedded video player, do NOT block yet.
        if (anySearchResultsPage && !anyAdultUrl && !anyVideoPlayer) {
            return ViolationType.NONE
        }
        if (anyAdultUrl || (anyVideoPlayer && anyBlockedText)) {
            return ViolationType.ADULT_SITE_OR_VIDEO
        }
        return ViolationType.NONE
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
        val analysis = inspectScreenTree(
            root = root,
            custom = custom,
            isMediaApp = isMediaOrSocialVideoApp(pkg),
            isBrowser = isBrowserApp(pkg),
            checkIncognito = safeStore.isIncognitoBlockEnabled
        )
        if (analysis.hasIncognitoUi) return true
        if (analysis.hasSearchResultsUrl && !analysis.hasAdultUrl && !analysis.hasVideoPlayer) {
            return false
        }
        return analysis.hasAdultUrl || (analysis.hasVideoPlayer && analysis.hasBlockedText)
    }

    private fun isMediaOrSocialVideoApp(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return MEDIA_VIDEO_PACKAGE_HINTS.any { lower.contains(it) }
    }

    private fun isBrowserApp(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return BROWSER_PACKAGE_HINTS.any { lower.contains(it) }
    }

    private data class ScreenAnalysis(
        val hasVideoPlayer: Boolean,
        val hasAdultUrl: Boolean,
        val hasSearchResultsUrl: Boolean,
        val hasBlockedText: Boolean,
        val hasIncognitoUi: Boolean
    )

    private fun inspectScreenTree(
        root: AccessibilityNodeInfo,
        custom: Collection<String>,
        isMediaApp: Boolean,
        isBrowser: Boolean,
        checkIncognito: Boolean
    ): ScreenAnalysis {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var hasVideoPlayer = false
        var hasSeekBar = false
        var hasDurationPairText = false
        var hasAdultUrl = false
        var hasSearchResultsUrl = false
        var hasSafeNonAdultUrlBar = false
        var hasBlockedText = false
        var hasIncognitoUi = false
        var menuClusterHits = 0
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

            val isMenuNode = isBrowser && (
                viewId.contains("menu_item") ||
                    viewId.contains("app_menu") ||
                    viewId.contains("overflow_menu")
                )
            if (isBrowser && !node.isEditable && textLower in BROWSER_MENU_CLUSTER_ITEMS) {
                menuClusterHits++
            }

            // 0. Incognito / Private browsing check (inside browser packages, never on 3-dot menu popups)
            if (checkIncognito && isBrowser && !hasIncognitoUi && !isMenuNode) {
                if (INCOGNITO_ID_HINTS.any { viewId.contains(it) } ||
                    (!node.isEditable && INCOGNITO_TEXT_HINTS.any {
                        textLower.contains(it) || descLower.contains(it)
                    })
                ) {
                    hasIncognitoUi = true
                }
            }

            // 1. Detect genuine video player / media viewer
            // NOTE: Browsers (especially Chrome on ColorOS/Realme/OPPO) use SurfaceView/TextureView
            // for normal webpage compositing, so NEVER treat SurfaceView/TextureView as a video
            // player inside browsers.
            if (!hasVideoPlayer) {
                if (VIDEO_CLASS_HINTS.any { clsLower.contains(it) } || roleDesc == "video") {
                    hasVideoPlayer = true
                } else if (!isBrowser && (clsLower.endsWith(".surfaceview") || clsLower.endsWith(".textureview"))) {
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

            if (!hasDurationPairText && DURATION_PAIR_REGEX.containsMatchIn(text)) {
                hasDurationPairText = true
            }

            // 2. Address bar & WebView URL check
            // Inside standard browsers, ONLY trust actual URL bar nodes (never webpage body snippets).
            val isUrlBar = URL_BAR_ID_HINTS.any { viewId.contains(it) }
            if (isUrlBar && !node.isFocused) {
                val urlCandidate = if (text.isNotBlank()) text else desc
                if (urlCandidate.isNotBlank()) {
                    if (SafeWords.isSearchEngineUrl(urlCandidate)) {
                        hasSearchResultsUrl = true
                    } else if (!hasAdultUrl && SafeWords.matchesSiteUrl(urlCandidate, custom)) {
                        hasAdultUrl = true
                    } else if (looksLikeUrlText(urlCandidate)) {
                        hasSafeNonAdultUrlBar = true
                    }
                }
            } else if (!isBrowser && !hasAdultUrl && !hasSearchResultsUrl && !node.isEditable && text.length in 5..350 && looksLikeUrlText(text)) {
                node.getBoundsInScreen(rect)
                val topLimit = if (isMediaApp) 720 else 440
                if ((isMediaApp || rect.top in 0..topLimit) &&
                    !SafeWords.isSearchEngineUrl(text) &&
                    SafeWords.matchesSiteUrl(text, custom)
                ) {
                    hasAdultUrl = true
                }
            }

            // 3. Non-password, non-editable rendered text / description check
            if (!hasBlockedText && !node.isPassword && !node.isEditable && !isMenuNode) {
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

        // Browser 3-dot overflow menu popup must never trigger Incognito or Safe Mode false positives
        if (isBrowser && menuClusterHits >= 2) {
            hasIncognitoUi = false
        }

        // If the browser's own URL bar says we're on a search results page (Google / Bing / SafeSearch),
        // snippet URLs shown inside search results must NOT mark the page as an adult site until clicked.
        if (hasSearchResultsUrl) {
            hasAdultUrl = false
        }

        // Safe non-adult websites (e.g. aistudio.google.com, docs, news) with text discussing keywords
        // must not falsely close the browser unless a genuine adult URL is loaded.
        if (isBrowser && hasSafeNonAdultUrlBar && !hasAdultUrl) {
            hasBlockedText = false
        }

        if (!hasVideoPlayer && ((hasSeekBar && hasDurationPairText) || (isMediaApp && DURATION_PAIR_REGEX.containsMatchIn(root.text?.toString().orEmpty())))) {
            hasVideoPlayer = true
        }
        // In dedicated video/chat apps, a seekbar or playback duration pair (00:12 / 05:30) indicates an active player
        if (!hasVideoPlayer && isMediaApp && hasDurationPairText) {
            hasVideoPlayer = true
        }

        return ScreenAnalysis(
            hasVideoPlayer = hasVideoPlayer,
            hasAdultUrl = hasAdultUrl,
            hasSearchResultsUrl = hasSearchResultsUrl,
            hasBlockedText = hasBlockedText,
            hasIncognitoUi = hasIncognitoUi
        )
    }

    private fun looksLikeUrlText(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.contains(' ')) return false
        return t.startsWith("http://") ||
            t.startsWith("https://") ||
            DOMAIN_LIKE_REGEX.containsMatchIn(t)
    }

    private fun onSafeViolation(pkg: String, reason: ViolationType) {
        lastSafeAt = SystemClock.elapsedRealtime()
        pendingScanRunnable?.let { handler.removeCallbacks(it) }
        pendingScanRunnable = null

        if (Settings.canDrawOverlays(this)) {
            showBlurOverlay(reason)
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({
                if (safeStore.isEnabled && detectSafeViolation(pkg) != ViolationType.NONE) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }, SECOND_BACK_CHECK_MS)
            handler.postDelayed({
                if (safeStore.isEnabled && detectSafeViolation(pkg) != ViolationType.NONE) {
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

    private fun showBlurOverlay(reason: ViolationType) {
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
                text = if (reason == ViolationType.INCOGNITO) {
                    "ইনকগনিটো ব্লক সক্রিয়"
                } else {
                    "সেফ মোড সক্রিয়"
                }
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, (10 * density).toInt(), 0, (6 * density).toInt())
            }
            val tvSubtitle = TextView(this).apply {
                text = if (reason == ViolationType.INCOGNITO) {
                    "গোপন/ইনকগনিটো ট্যাব শনাক্ত হওয়ায় স্ক্রিন ব্লার করে ফিরে যাওয়া হচ্ছে..."
                } else {
                    "অনুপযুক্ত সাইট বা ভিডিও শনাক্ত হওয়ায় স্ক্রিন ব্লার করে ফিরে যাওয়া হচ্ছে..."
                }
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

        private val DURATION_PAIR_REGEX = Regex("\\b\\d{1,2}:\\d{2}\\s*/\\s*\\d{1,2}:\\d{2}\\b")
        private val VIDEO_EXT_REGEX = Regex("\\.(mp4|mkv|webm|avi|mov|flv|m3u8)\\b")
        private val DOMAIN_LIKE_REGEX = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+(/.*)?$")

        private val BROWSER_PACKAGE_HINTS = listOf(
            "chrome", "browser", "firefox", "fenix", "brave", "opera",
            "emmx", "duckduckgo", "kiwi", "sbrowser", "ucmobile", "vivaldi",
            "yandex", "heytap", "coloros.browser", "mi.globalbrowser", "mint"
        )

        private val INCOGNITO_ID_HINTS = listOf(
            "incognito_icon", "incognito_ntp", "close_all_incognito",
            "incognito_mode", "private_browsing_indicator", "secret_mode_indicator"
        )

        // Never match menu-button labels ("New Incognito tab", "New InPrivate tab");
        // match ONLY active incognito/private tab banners and headers.
        private val INCOGNITO_TEXT_HINTS = listOf(
            "you've gone incognito", "you’ve gone incognito",
            "you're browsing privately", "close all incognito tabs", "close incognito tabs",
            "inprivate browsing", "private browsing mode",
            "you are in private mode", "secret mode turned on",
            "আপনি ইনকগনিটোতে আছেন", "ইনকগনিটো মোড চালু"
        )

        private val BROWSER_MENU_CLUSTER_ITEMS = setOf(
            "new tab", "bookmarks", "history", "downloads", "desktop site", "settings",
            "নতুন ট্যাব", "বুকমার্ক", "ইতিহাস", "ডাউনলোড", "সেটিংস"
        )

        private val MEDIA_VIDEO_PACKAGE_HINTS = listOf(
            "vidmate", "snaptube", "dayuwuxian", "telegram", "nekogram",
            "ayugram", "terabox", "dubox", "mxtech", "videolan", "vlc",
            "playit", "xplayer", "reddit", "twitter", "ucmobile"
        )

        private val VIDEO_CLASS_HINTS = listOf(
            ".videoview", "videoplayer", "playerview", "exoplayer", "mediacontroller",
            "ijk", "txcloud", "photoviewer",
            "videotimeline", "seekbarview", "webvideo"
        )

        private val VIDEO_ID_HINTS = listOf(
            "video_player", "player_view", "exoplayer", "exo_", "ijk",
            "video_container", "player_container",
            "html5_video", "yt_player", "jwplayer", "jw-", "videojs",
            "vjs-", "plyr", "fluid_video", "brightcove", "bitmovin", "shaka"
        )

        private val VIDEO_LABEL_HINTS = listOf(
            "video player", "play video", "pause video", "mute video"
        )

        private val URL_BAR_ID_HINTS = listOf(
            "url_bar", "url_bar_title", "address", "omnibox", "location_bar",
            "location_bar_edit_text", "mozac_browser_toolbar_url_view",
            "url_field", "address_bar", "search_box_text",
            "web_url", "et_url", "domain", "action_bar_title"
        )
    }
}
