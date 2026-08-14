package com.hafij.islamicalarm.amal

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyAmal(
    val dateKey: String, // format: yyyy-MM-dd
    var fajrStatus: Int = 0,    // 0: none, 1: jamat, 2: alone, 3: qaza
    var dhuhrStatus: Int = 0,
    var asrStatus: Int = 0,
    var maghribStatus: Int = 0,
    var ishaStatus: Int = 0,

    var tahajjud: Boolean = false,
    var ishraq: Boolean = false,
    var quranRecitation: Boolean = false,
    var morningEveningZikr: Boolean = false,
    var istighfarDurood: Boolean = false,
    var surahMulk: Boolean = false,
    var charity: Boolean = false
) {
    fun calculateScore(): Int {
        var score = 0
        // Prayers: Max 60%
        val prayerStatuses = listOf(fajrStatus, dhuhrStatus, asrStatus, maghribStatus, ishaStatus)
        prayerStatuses.forEach { status ->
            when (status) {
                1 -> score += 12 // Jamat
                2 -> score += 10 // Alone
                3 -> score += 6  // Qaza
            }
        }
        // Sunnah deeds: Max 40%
        if (tahajjud) score += 7
        if (ishraq) score += 5
        if (quranRecitation) score += 7
        if (morningEveningZikr) score += 7
        if (istighfarDurood) score += 5
        if (surahMulk) score += 5
        if (charity) score += 4

        return score.coerceAtMost(100)
    }
}

class AmalStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("amal_tracker_prefs", Context.MODE_PRIVATE)

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun getDateKey(date: Date = Date()): String {
            return dateFormat.format(date)
        }

        fun getDateKey(cal: Calendar): String {
            return dateFormat.format(cal.time)
        }
    }

    fun getAmalForDate(dateKey: String): DailyAmal {
        val jsonStr = prefs.getString("amal_$dateKey", null) ?: return DailyAmal(dateKey)
        return try {
            val json = JSONObject(jsonStr)
            DailyAmal(
                dateKey = dateKey,
                fajrStatus = json.optInt("fajr", 0),
                dhuhrStatus = json.optInt("dhuhr", 0),
                asrStatus = json.optInt("asr", 0),
                maghribStatus = json.optInt("maghrib", 0),
                ishaStatus = json.optInt("isha", 0),
                tahajjud = json.optBoolean("tahajjud", false),
                ishraq = json.optBoolean("ishraq", false),
                quranRecitation = json.optBoolean("quran", false),
                morningEveningZikr = json.optBoolean("zikr", false),
                istighfarDurood = json.optBoolean("istighfar", false),
                surahMulk = json.optBoolean("mulk", false),
                charity = json.optBoolean("charity", false)
            )
        } catch (e: Exception) {
            DailyAmal(dateKey)
        }
    }

    fun saveAmal(amal: DailyAmal) {
        val json = JSONObject().apply {
            put("fajr", amal.fajrStatus)
            put("dhuhr", amal.dhuhrStatus)
            put("asr", amal.asrStatus)
            put("maghrib", amal.maghribStatus)
            put("isha", amal.ishaStatus)
            put("tahajjud", amal.tahajjud)
            put("ishraq", amal.ishraq)
            put("quran", amal.quranRecitation)
            put("zikr", amal.morningEveningZikr)
            put("istighfar", amal.istighfarDurood)
            put("mulk", amal.surahMulk)
            put("charity", amal.charity)
        }
        prefs.edit().putString("amal_${amal.dateKey}", json.toString()).apply()
    }

    fun getStreak(): Int {
        var streak = 0
        val cal = Calendar.getInstance()
        for (i in 0..60) {
            val key = getDateKey(cal)
            val amal = getAmalForDate(key)
            if (amal.calculateScore() >= 40) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                if (i == 0) {
                    // Today might be in progress, check yesterday
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    continue
                }
                break
            }
        }
        return streak
    }
}
