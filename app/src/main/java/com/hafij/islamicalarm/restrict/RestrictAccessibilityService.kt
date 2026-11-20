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
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTextChanged(event)
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
    // Safe mode: stops adult searches. Only text fields (search boxes, address bars)
    // are checked; password fields are skipped; nothing is stored or sent anywhere.
    // ------------------------------------------------------------------

    private fun handleTextChanged(event: AccessibilityEvent) {
        if (!safeStore.isEnabled) return
        if (event.packageName?.toString() == packageName) return
        if (event.isPassword) return
        val text = event.text?.joinToString(" ") { it?.toString() ?: "" } ?: return
        if (SafeWords.matches(text, safeStore.getCustomWords())) {
            onSafeViolation(event.source)
        }
    }

    private fun handleContentChanged(event: AccessibilityEvent) {
        if (!safeStore.isEnabled) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in ignored) return

        val tick = SystemClock.elapsedRealtime()
        if (tick - lastScanAt < 800L) return
        lastScanAt = tick

        val root = rootInActiveWindow ?: return
        val hit = findBlockedField(root)
        if (hit != null) {
            onSafeViolation(hit)
        }
    }

    private fun findBlockedField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val custom = safeStore.getCustomWords()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_SCAN_NODES) {
            val node = queue.removeFirst()
            visited++
            if (!node.isPassword && isTextField(node)) {
                val text = node.text?.toString()
                if (!text.isNullOrEmpty() && SafeWords.matches(text, custom)) {
                    return node
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.add(child)
            }
        }
        return null
    }

    private fun isTextField(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) return true
        val cls = node.className?.toString() ?: ""
        if (cls.contains("EditText")) return true
        val id = node.viewIdResourceName?.lowercase() ?: return false
        return id.contains("url") || id.contains("search") || id.contains("omnibox") || id.contains("query")
    }

    private fun onSafeViolation(node: AccessibilityNodeInfo?) {
        val tick = SystemClock.elapsedRealtime()
        if (tick - lastSafeAt < 1500L) return
        lastSafeAt = tick

        // Clear the offending text so the search is not run again when the user returns.
        try {
            if (node != null) {
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
        private const val MAX_SCAN_NODES = 300
    }
}
