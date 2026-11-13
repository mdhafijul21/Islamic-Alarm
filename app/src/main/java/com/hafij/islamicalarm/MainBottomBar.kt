package com.hafij.islamicalarm

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Bottom bar with 6 tabs. Material's BottomNavigationView allows only 5 items
 * (it crashes with more), so this small bar offers the same API MainActivity uses:
 * [selectedItemId] and [setOnItemSelectedListener].
 */
class MainBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    class BarItem(val itemId: Int)

    private class Tab(val id: Int, val root: LinearLayout, val pill: FrameLayout, val label: TextView)

    private val tabs = mutableListOf<Tab>()
    private var listener: ((BarItem) -> Boolean)? = null
    private var currentId: Int = View.NO_ID

    var selectedItemId: Int
        get() = currentId
        set(value) {
            if (value == currentId) return
            val tab = tabs.firstOrNull { it.id == value } ?: return
            val accepted = listener?.invoke(BarItem(value)) ?: true
            if (accepted) {
                currentId = tab.id
                refreshVisuals()
            }
        }

    fun setOnItemSelectedListener(l: (BarItem) -> Boolean) {
        listener = l
    }

    init {
        orientation = HORIZONTAL
        val defs = listOf(
            Triple(R.id.nav_prayer_times, R.drawable.ic_nav_home, "হোম"),
            Triple(R.id.nav_alarm, R.drawable.ic_nav_alarm, "এলার্ম"),
            Triple(R.id.nav_quran, R.drawable.ic_nav_quran, "কুরআন"),
            Triple(R.id.nav_tasbih, R.drawable.ic_nav_tasbih, "তাসবিহ"),
            Triple(R.id.nav_duas, R.drawable.ic_nav_dua, "দোয়া"),
            Triple(R.id.nav_settings, R.drawable.ic_nav_settings, "সেটিংস")
        )
        for ((id, icon, text) in defs) {
            addTab(id, icon, text)
        }
        if (tabs.isNotEmpty()) {
            currentId = tabs[0].id
        }
        refreshVisuals()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun addTab(id: Int, iconRes: Int, title: String) {
        val root = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }

        val pill = FrameLayout(context).apply {
            layoutParams = LayoutParams(dp(52), dp(30))
        }
        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(ContextCompat.getColor(context, R.color.secondary))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
        }
        pill.addView(icon)

        val label = TextView(context).apply {
            this.text = title
            textSize = 11f
            maxLines = 1
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
        }

        root.addView(pill)
        root.addView(label)
        root.setOnClickListener { selectedItemId = id }
        addView(root)
        tabs.add(Tab(id, root, pill, label))
    }

    private fun refreshVisuals() {
        for (tab in tabs) {
            val selected = tab.id == currentId
            tab.pill.setBackgroundResource(if (selected) R.drawable.bg_nav_pill else 0)
            tab.label.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}
