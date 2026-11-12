package com.hafij.islamicalarm

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hafij.islamicalarm.duas.DuasActivity
import com.hafij.islamicalarm.quran.QuranActivity
import com.hafij.islamicalarm.tasbih.TasbihActivity

/**
 * Makes the bottom navigation bar work on every main page
 * (Quran, Tasbih, Duas) exactly like it does on Home / Alarm.
 */
object BottomNavHelper {

    const val EXTRA_OPEN_TAB = "open_tab"
    const val TAB_HOME = "home"
    const val TAB_ALARM = "alarm"

    fun setup(activity: Activity, nav: BottomNavigationView, selectedId: Int) {
        // Highlight the current page (set before the listener so it does not fire)
        nav.selectedItemId = selectedId

        nav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedId) return@setOnItemSelectedListener true
            when (item.itemId) {
                R.id.nav_prayer_times -> goMain(activity, TAB_HOME)
                R.id.nav_alarm -> goMain(activity, TAB_ALARM)
                R.id.nav_quran -> switchTo(activity, QuranActivity::class.java)
                R.id.nav_tasbih -> switchTo(activity, TasbihActivity::class.java)
                R.id.nav_duas -> switchTo(activity, DuasActivity::class.java)
            }
            true
        }
        nav.setOnItemReselectedListener { /* already here */ }
    }

    private fun goMain(activity: Activity, tab: String) {
        val intent = Intent(activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(EXTRA_OPEN_TAB, tab)
        }
        // CLEAR_TOP already removes the current page, no finish() needed
        activity.startActivity(intent)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }

    private fun switchTo(activity: Activity, target: Class<out Activity>) {
        val intent = Intent(activity, target).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        activity.startActivity(intent)
        // Keep the old page visible until the new one is drawn on top (prevents flicker)
        activity.window.decorView.postDelayed({ activity.finish() }, 350)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }
}
