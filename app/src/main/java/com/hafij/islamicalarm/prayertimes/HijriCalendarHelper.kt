package com.hafij.islamicalarm.prayertimes

import java.util.Calendar
import kotlin.math.floor

data class HijriDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val monthNameBn: String,
    val formattedBn: String
)

data class IslamicEvent(
    val nameBn: String,
    val hijriDay: Int,
    val hijriMonth: Int,
    val descriptionBn: String
)

object HijriCalendarHelper {

    val HIJRI_MONTHS_BN = listOf(
        "মুহাররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি",
        "জমাদিউল আউয়াল", "জমাদিউস সানি", "রজব", "শাবান",
        "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ"
    )

    val ISLAMIC_EVENTS = listOf(
        IslamicEvent("পবিত্র আশুরা (১০ মুহাররম)", 10, 1, "কারবালার ঐতিহাসিক শাহাদাত ও মুসা (আঃ)-এর নাজাতের দিন। রোজা রাখার বিশেষ ফজিলত।"),
        IslamicEvent("ঈদে মিলাদুন্নবী (সাঃ)", 12, 3, "সর্বশ্রেষ্ঠ নবী ও রাসূল হযরত মুহাম্মদ (সাঃ)-এর শুভাগমনের পবিত্র দিন।"),
        IslamicEvent("পবিত্র শবে মেরাজ (২৭ রজব)", 27, 7, "রাসূলুল্লাহ (সাঃ)-এর ঊর্ধ্বাকাশ ভ্রমণ ও পাঁচ ওয়াক্ত সালাত উপহার পাওয়ার রজনী।"),
        IslamicEvent("পবিত্র শবে বরাত (১৫ শাবান)", 15, 8, "লাইলাতুল বরাত বা বরকতময় ক্ষমার রাত ও বিশেষ ইবাদতের রজনী।"),
        IslamicEvent("রমজানুল মুবারক শুরু", 1, 9, "রহমত, মাগফিরাত ও নাজাতের মাস এবং সিয়াম সাধনার শুরু।"),
        IslamicEvent("ঐতিহাসিক বদর দিবস (১৭ রমজান)", 17, 9, "ইসলামের প্রথম হক ও বাতিলের চূড়ান্ত যুদ্ধের ঐতিহাসিক বিজয়ের দিন।"),
        IslamicEvent("লাইলাতুল কদর (২৭ রমজান)", 27, 9, "হাজার মাসের চেয়েও শ্রেষ্ঠ বরকতময় রজনী ও কুরআন অবতীর্ণের রাত।"),
        IslamicEvent("পবিত্র ঈদুল ফিতর (১ শাওয়াল)", 1, 10, "রমজানের সিয়াম সাধনা শেষে মুসলিম উম্মাহর মহান আনন্দ উৎসব।"),
        IslamicEvent("ইয়াওমে আরাফা ও হজ (৯ জিলহজ)", 9, 12, "বিশ্ব মুসলিমের ঐক্যের আরাফাত ময়দানে অবস্থানের দিন এবং রোজা রাখার অসীম সওয়াব।"),
        IslamicEvent("পবিত্র ঈদুল আজহা (১০ জিলহজ)", 10, 12, "আল্লাহর রাহে সর্বোচ্চ আত্মত্যাগ ও কুরবানির মহান উৎসব।"),
        IslamicEvent("আইয়ামে বীজের রোজা", 13, 0, "প্রতি হিজরি মাসের ১৩, ১৪ ও ১৫ তারিখ নফল রোজা রাখা সুন্নাত (সারা বছর রোজা রাখার সমান সওয়াব)।")
    )

    /**
     * Converts a Gregorian Calendar date to Hijri Date.
     * Offset adjustment accounts for moon sighting variance (default: 0 or +1 in BD).
     */
    fun getHijriDate(cal: Calendar, dayAdjustment: Int = 0): HijriDate {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)

        // Julian Day Number
        var jd = (1461 * (y + 4800 + (m - 14) / 12)) / 4 +
                (367 * (m - 2 - 12 * ((m - 14) / 12))) / 12 -
                (3 * ((y + 4900 + (m - 14) / 12) / 100)) / 4 +
                d - 32075 + dayAdjustment

        val l = jd - 1948440 + 10632
        val n = (l - 1) / 10631
        val l2 = l - 10631 * n + 354
        val j = ((10985 - l2) / 5316) * ((50 * l2) / 17719) + (l2 / 5670) * ((43 * l2) / 15238)
        val l3 = l2 - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
        val mHijri = ((24 * l3) / 709).toInt()
        val dHijri = (l3 - (709 * mHijri) / 24).toInt()
        val yHijri = (30 * n + j - 30).toInt()

        val safeMonth = mHijri.coerceIn(1, 12)
        val monthName = HIJRI_MONTHS_BN[safeMonth - 1]

        val formatted = "${PrayerTimeCalculator.toBengaliNumber(dHijri)} $monthName, ${PrayerTimeCalculator.toBengaliNumber(yHijri)} হিজরি"

        return HijriDate(dHijri, safeMonth, yHijri, monthName, formatted)
    }

    fun isAyyamAlBeed(hijriDay: Int): Boolean {
        return hijriDay in 13..15
    }
}
