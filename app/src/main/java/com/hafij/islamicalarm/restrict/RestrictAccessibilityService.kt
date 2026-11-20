package com.hafij.islamicalarm.restrict

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager

/**
 * Watches which app comes to the front (package name only, it never reads screen content).
 * When that app is blocked by an active restrict-mode slot, the break screen is shown on top of it.
 */
class RestrictAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { RestrictStore(applicationContext) }
    private val safeStore by lazy { SafeModeStore(applicationContext) }

    private var lastPackage: String? = null
    private var ignored: Set<String> = setOf("com.android.systemui", "android")
    private var lastBlockAt = 0L
    private var lastScanAt = 0L
    private var lastSafeAt = 0L
    private var navigationWatchUntil = 0L
    private var navigationPackage: String? = null
    private var pendingPageScan = false

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
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleViewClicked(event)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleContentChanged(event)
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(ticker)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    private fun checkNow() {
        val pkg = lastPackage ?: return
        if (pkg == packageName) return // our own screens are never blocked

        val blockEnd = store.blockEndFor(pkg, System.currentTimeMillis())
        if (blockEnd <= 0L) return

        val tick = SystemClock.elapsedRealtime()
        if (tick - lastBlockAt < 700L) return
        lastBlockAt = tick

        // The break screen is about to be the front window; do not fire again for the same app.
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
            // Could not show the break screen: send the user home and keep watching.
            lastPackage = pkg
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    // ------------------------------------------------------------------
    // Safe mode: typing alone never triggers the block screen. A click starts
    // a short navigation-watch window; after the page changes, visible page
    // text is inspected. Passwords and editable fields are never inspected.
    // ------------------------------------------------------------------

    private fun handleViewClicked(event: AccessibilityEvent) {
        if (!safeStore.isEnabled) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in ignored) return

        // Start watching the page after a user click (typically a search result).
        // Do not inspect the text field that was used to type the search.
        navigationPackage = pkg
        navigationWatchUntil = SystemClock.elapsedRealtime() + NAVIGATION_WATCH_MS
        schedulePageScan(pkg, PAGE_SCAN_DELAY_MS)
    }

    private fun handleContentChanged(event: AccessibilityEvent) {
        if (!safeStore.isEnabled) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in ignored) return
        if (pkg != navigationPackage) return
        if (SystemClock.elapsedRealtime() > navigationWatchUntil) {
            navigationPackage = null
            pendingPageScan = false
            return
        }

        // Browser pages often populate asynchronously after the result is tapped.
        schedulePageScan(pkg, PAGE_SCAN_DELAY_MS)
    }

    private fun schedulePageScan(pkg: String, delayMs: Long) {
        if (pendingPageScan) return
        pendingPageScan = true
        handler.postDelayed({
            pendingPageScan = false
            if (!safeStore.isEnabled) return@postDelayed
            if (pkg != navigationPackage) return@postDelayed
            if (SystemClock.elapsedRealtime() > navigationWatchUntil) return@postDelayed
            if (pkg == packageName || pkg in ignored) return@postDelayed

            val root = rootInActiveWindow ?: return@postDelayed
            if (root.packageName?.toString() != pkg) return@postDelayed
            if (pageContainsBlockedContent(root)) onSafeViolation()
        }, delayMs)
    }

    private fun pageContainsBlockedContent(root: AccessibilityNodeInfo): Boolean {
        val custom = safeStore.getCustomWords()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_SCAN_NODES) {
            val node = queue.removeFirst()
            visited++

            // Ignore password and editable nodes: searching/typing must not itself
            // activate Safe Mode. Only rendered page content is considered.
            if (!node.isPassword && !node.isEditable) {
                val text = node.text?.toString().orEmpty()
                val description = node.contentDescription?.toString().orEmpty()
                if ((text.isNotBlank() && SafeWords.matches(text, custom)) ||
                    (description.isNotBlank() && SafeWords.matches(description, custom))) {
                    return true
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.add(child)
            }
        }
        return false
    }

    private fun onSafeViolation() {
        val tick = SystemClock.elapsedRealtime()
        if (tick - lastSafeAt < 1500L) return
        lastSafeAt = tick
        navigationWatchUntil = 0L
        navigationPackage = null
        pendingPageScan = false

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
        private const val MAX_SCAN_NODES = 500
        private const val NAVIGATION_WATCH_MS = 15_000L
        private const val PAGE_SCAN_DELAY_MS = 900L
    }
}
