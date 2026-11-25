package com.hafij.islamicalarm.prayertimes

import java.util.Calendar

data class BanglaDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val monthNameBn: String,
    val formattedBn: String
)

/**
 * Bangladesh's officially reformed Bangla solar calendar (Bangla Academy reform, in
 * effect since 2019): Pohela Boishakh is fixed on 14 April every year; Boishakh through
 * Ashwin (the first 6 months) have 31 days each, Kartik through Choitro (the last 6
 * months) have 30 days each, except Choitro gets 1 extra day (31) whenever the following
 * Gregorian February is a leap month — matching the current official civil calendar used
 * in Bangladesh, not the older pre-1987/pre-2019 version.
 */
object BanglaCalendarHelper {

    val BANGLA_MONTHS_BN = listOf(
        "বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র", "আশ্বিন",
        "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ", "ফাল্গুন", "চৈত্র"
    )

    val GREGORIAN_MONTHS_BN = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    private fun isGregorianLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    /** Bengali-numeral Gregorian date, e.g. "২৫ নভেম্বর". */
    fun formatGregorianBn(cal: Calendar): String {
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = GREGORIAN_MONTHS_BN[cal.get(Calendar.MONTH)]
        return "${PrayerTimeCalculator.toBengaliNumber(day)} $month"
    }

    fun getBanglaDate(cal: Calendar): BanglaDate {
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) // 0-based, Calendar.APRIL = 3
        val gDay = cal.get(Calendar.DAY_OF_MONTH)

        // Pohela Boishakh (14 April) of the Bangla year currently running.
        val onOrAfterPohela = gMonth > Calendar.APRIL || (gMonth == Calendar.APRIL && gDay >= 14)
        val pohelaGregorianYear = if (onOrAfterPohela) gYear else gYear - 1
        val banglaYear = if (onOrAfterPohela) gYear - 593 else gYear - 594

        val pohela = Calendar.getInstance().apply {
            clear()
            set(pohelaGregorianYear, Calendar.APRIL, 14)
        }
        val target = Calendar.getInstance().apply {
            clear()
            set(gYear, gMonth, gDay)
        }
        val daysSincePohela =
            ((target.timeInMillis - pohela.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()

        // Choitro (the 12th month) falls in March of (pohelaGregorianYear + 1); it gets a
        // 31st day whenever that calendar year's February is a leap month.
        val choitroLengthThisYear = if (isGregorianLeapYear(pohelaGregorianYear + 1)) 31 else 30
        val monthLengths = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, choitroLengthThisYear)

        var remaining = daysSincePohela.coerceAtLeast(0)
        var monthIndex = 0
        while (monthIndex < monthLengths.size - 1 && remaining >= monthLengths[monthIndex]) {
            remaining -= monthLengths[monthIndex]
            monthIndex++
        }
        val dayOfMonth = remaining + 1
        val monthName = BANGLA_MONTHS_BN[monthIndex]

        val formatted = "${PrayerTimeCalculator.toBengaliNumber(dayOfMonth)} $monthName, " +
            "${PrayerTimeCalculator.toBengaliNumber(banglaYear)} বঙ্গাব্দ"

        return BanglaDate(dayOfMonth, monthIndex + 1, banglaYear, monthName, formatted)
    }
}
