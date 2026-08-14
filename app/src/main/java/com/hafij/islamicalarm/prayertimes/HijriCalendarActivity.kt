package com.hafij.islamicalarm.prayertimes

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityHijriCalendarBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HijriCalendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHijriCalendarBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHijriCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarHijri.setNavigationOnClickListener {
            finish()
        }

        val now = Calendar.getInstance()
        val hijriDate = HijriCalendarHelper.getHijriDate(now)

        binding.tvHijriDateMain.text = hijriDate.formattedBn

        val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("bn", "BD"))
        binding.tvGregorianDateMain.text = PrayerTimeCalculator.toBengaliDigits(sdf.format(now.time))

        populateIslamicEvents()
    }

    private fun populateIslamicEvents() {
        val events = HijriCalendarHelper.ISLAMIC_EVENTS
        val inflater = LayoutInflater.from(this)

        events.forEach { event ->
            val card = MaterialCardView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
                setCardBackgroundColor(resources.getColor(R.color.surface, theme))
                radius = 28f
                cardElevation = 2f
                strokeWidth = 2
                setStrokeColor(resources.getColor(R.color.border, theme))
            }

            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(32, 28, 32, 28)
            }

            val title = TextView(this).apply {
                text = event.nameBn
                setTextColor(resources.getColor(R.color.secondary, theme))
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val desc = TextView(this).apply {
                text = event.descriptionBn
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                textSize = 13f
                setLineSpacing(6f, 1f)
                setPadding(0, 8, 0, 0)
            }

            layout.addView(title)
            layout.addView(desc)
            card.addView(layout)
            binding.containerIslamicEvents.addView(card)
        }
    }
}
