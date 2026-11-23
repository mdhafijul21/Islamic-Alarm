package com.hafij.islamicalarm.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityIslamicNotificationsBinding

class IslamicNotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIslamicNotificationsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIslamicNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarNotifications.setNavigationOnClickListener { finish() }

        IslamicOccasionNotifier.evaluateAndNotifyToday(this)

        binding.btnMarkAllNotificationsRead.setOnClickListener {
            IslamicOccasionNotifier.markAllRead(this)
            renderScreen()
        }

        renderScreen()
    }

    override fun onStop() {
        super.onStop()
        // Mark notifications read once the user has opened and viewed the screen
        IslamicOccasionNotifier.markAllRead(this)
    }

    private fun renderScreen() {
        val inflater = LayoutInflater.from(this)
        val inbox = IslamicOccasionNotifier.getInboxRecords(this)

        binding.layoutInboxNotifications.removeAllViews()
        if (inbox.isEmpty()) {
            binding.cardNotificationsEmpty.visibility = View.VISIBLE
            binding.btnMarkAllNotificationsRead.visibility = View.GONE
        } else {
            binding.cardNotificationsEmpty.visibility = View.GONE
            binding.btnMarkAllNotificationsRead.visibility =
                if (inbox.any { !it.isRead }) View.VISIBLE else View.GONE

            for (record in inbox) {
                val card = inflater.inflate(
                    R.layout.item_islamic_notification,
                    binding.layoutInboxNotifications,
                    false
                ) as MaterialCardView
                card.findViewById<TextView>(R.id.tvNotifBadge).text =
                    "${record.occasionBadgeBn} • ${record.hijriDateBn}"
                val unreadTag = card.findViewById<TextView>(R.id.tvNotifUnreadTag)
                unreadTag.visibility = if (record.isRead) View.GONE else View.VISIBLE
                if (!record.isRead) {
                    card.strokeColor = ContextCompat.getColor(this, R.color.secondary)
                    card.strokeWidth = 3
                }
                bindCommonFields(
                    card = card,
                    title = record.titleBn,
                    fazilat = record.fazilatBn,
                    hadithArabic = record.hadithArabic,
                    hadithBangla = record.hadithBangla,
                    reference = record.hadithReference,
                    amal = record.recommendedAmalBn
                )
                card.setOnClickListener {
                    IslamicOccasionNotifier.markRead(this, record.recordId)
                    renderScreen()
                }
                binding.layoutInboxNotifications.addView(card)
            }
        }

        binding.layoutAllSpecialOccasions.removeAllViews()
        for (occasion in IslamicOccasionNotifier.SPECIAL_OCCASIONS) {
            val card = inflater.inflate(
                R.layout.item_islamic_notification,
                binding.layoutAllSpecialOccasions,
                false
            ) as MaterialCardView
            card.findViewById<TextView>(R.id.tvNotifBadge).text = occasion.occasionBadgeBn
            card.findViewById<TextView>(R.id.tvNotifUnreadTag).visibility = View.GONE
            bindCommonFields(
                card = card,
                title = occasion.titleBn,
                fazilat = occasion.fazilatBn,
                hadithArabic = occasion.hadithArabic,
                hadithBangla = occasion.hadithBangla,
                reference = occasion.hadithReference,
                amal = occasion.recommendedAmalBn
            )
            binding.layoutAllSpecialOccasions.addView(card)
        }
    }

    private fun bindCommonFields(
        card: View,
        title: String,
        fazilat: String,
        hadithArabic: String,
        hadithBangla: String,
        reference: String,
        amal: String
    ) {
        card.findViewById<TextView>(R.id.tvNotifTitle).text = title
        card.findViewById<TextView>(R.id.tvNotifFazilat).text = "✨ ফজিলত: $fazilat"
        val tvArabic = card.findViewById<TextView>(R.id.tvNotifHadithArabic)
        if (hadithArabic.isBlank()) {
            tvArabic.visibility = View.GONE
        } else {
            tvArabic.visibility = View.VISIBLE
            tvArabic.text = hadithArabic
        }
        card.findViewById<TextView>(R.id.tvNotifHadithBangla).text = "📖 হাদিস: $hadithBangla"
        card.findViewById<TextView>(R.id.tvNotifReference).text = "📚 দলিল/সূত্র: $reference"
        card.findViewById<TextView>(R.id.tvNotifAmal).text = "🤲 করণীয় আমল: $amal"
    }
}
