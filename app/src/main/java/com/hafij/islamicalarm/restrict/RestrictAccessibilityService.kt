package com.hafij.islamicalarm.restrict

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Watches which app comes to the front (package name only, it never reads screen content).
 * When that app is blocked by an active restrict-mode slot, the break screen is shown on top of it.
 */
class RestrictAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { RestrictStore(applicationContext) }

    private var lastPackage: String? = null
    private var ignored: Set<String> = setOf("com.android.systemui", "android")
    private var lastBlockAt = 0L

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
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored) return
        lastPackage = pkg
        checkNow()
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
}
