package com.hafij.islamicalarm.prayertimes

import java.util.Calendar
import java.util.Locale
import kotlin.math.*

data class PrayerSchedule(
    val fajr: String,
    val sunrise: String,
    val ishraq: String,
    val dhuhr: String,
    val asr: String,
    val sunset: String,
    val maghrib: String,
    val isha: String,
    val midnight: String,
    val tahajjudBestStart: String,
    val sehriEnd: String,
    val iftarTime: String,

    val fajrCal: Calendar,
    val sunriseCal: Calendar,
    val dhuhrCal: Calendar,
    val asrCal: Calendar,
    val maghribCal: Calendar,
    val ishaCal: Calendar,
    val tahajjudStartCal: Calendar
)

data class NextPrayerInfo(
    val prayerNameBn: String,
    val prayerNameAr: String,
    val prayerTimeStr: String,
    val remainingMillis: Long,
    val isNextDay: Boolean = false
)

object PrayerTimeCalculator {

    // Converts English digits to Bengali digits
    fun toBengaliDigits(input: String): String {
        val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = StringBuilder()
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(bnDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun toBengaliNumber(num: Int): String {
        return toBengaliDigits(num.toString())
    }

    /**
     * Calculates prayer times using standard astronomical solar equations for Islamic prayer calculation anywhere in the world.
     */
    fun calculate(calendar: Calendar, district: District): PrayerSchedule {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val lat = district.lat
        val lng = district.lng
        val timeZone = district.timeZone

        // Julian Date
        val julianDate = getJulianDate(year, month, day) - lng / (15.0 * 24.0)

        // Sun's declination and equation of time
        val d = julianDate - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(Math.toRadians(g)) + 0.020 * sin(Math.toRadians(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = Math.toDegrees(atan2(cos(Math.toRadians(e)) * sin(Math.toRadians(l)), cos(Math.toRadians(l)))) / 15.0
        val declination = Math.toDegrees(asin(sin(Math.toRadians(e)) * sin(Math.toRadians(l))))
        val eqOfTime = q / 15.0 - fixHour(ra)

        // Solar Noon (Dhuhr)
        val noon = fixHour(12 + timeZone - lng / 15.0 - eqOfTime)

        // Sun angles by calculation method
        val method = district.method
        val fajrAngle = method.fajrAngle
        val ishaAngle = method.ishaAngle

        val fajrDiff = sunAngleTime(fajrAngle, lat, declination)
        val sunriseDiff = sunAngleTime(0.833, lat, declination) // Standard atmospheric refraction
        val sunsetDiff = sunAngleTime(0.833, lat, declination)
        val ishaDiff = if (ishaAngle > 0) sunAngleTime(ishaAngle, lat, declination) else 0.0

        // Asr (Hanafi juristic: shadow length = 2 * object length + shadow at noon)
        val asrShadowFactor = 2.0
        val asrAngle = -Math.toDegrees(atan(1.0 / (asrShadowFactor + tan(Math.toRadians(abs(lat - declination))))))
        val asrDiff = sunAngleTime(asrAngle, lat, declination)

        val fajrHour = noon - fajrDiff
        val sunriseHour = noon - sunriseDiff
        val dhuhrHour = noon
        val asrHour = noon + asrDiff
        val sunsetHour = noon + sunsetDiff
        val maghribHour = sunsetHour + (2.0 / 60.0) // 2 min safety margin
        val ishaHour = if (method.ishaMinutesAfterMaghrib > 0) {
            maghribHour + (method.ishaMinutesAfterMaghrib.toDouble() / 60.0)
        } else {
            noon + ishaDiff
        }

        // Calendar instances for exact comparisons
        val fajrCal = makeCalendar(calendar, fajrHour)
        val sunriseCal = makeCalendar(calendar, sunriseHour)
        val dhuhrCal = makeCalendar(calendar, dhuhrHour)
        val asrCal = makeCalendar(calendar, asrHour)
        val maghribCal = makeCalendar(calendar, maghribHour)
        val ishaCal = makeCalendar(calendar, ishaHour)

        // Ishraq: approx 15 minutes after sunrise
        val ishraqCal = (sunriseCal.clone() as Calendar).apply { add(Calendar.MINUTE, 15) }

        // Sehri End: 5 mins before Fajr start
        val sehriCal = (fajrCal.clone() as Calendar).apply { add(Calendar.MINUTE, -5) }

        // Tahajjud: Best in last 1/3rd of night (from Maghrib to next day's Fajr)
        // Night duration = (Next Fajr - Today Maghrib)
        val nextFajrCal = (fajrCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val nightDurationMillis = nextFajrCal.timeInMillis - maghribCal.timeInMillis
        val lastThirdStartMillis = maghribCal.timeInMillis + (nightDurationMillis * 2 / 3)
        val tahajjudStartCal = Calendar.getInstance().apply { timeInMillis = lastThirdStartMillis }
        val midnightMillis = maghribCal.timeInMillis + (nightDurationMillis / 2)
        val midnightCal = Calendar.getInstance().apply { timeInMillis = midnightMillis }

        return PrayerSchedule(
            fajr = formatTime(fajrCal),
            sunrise = formatTime(sunriseCal),
            ishraq = formatTime(ishraqCal),
            dhuhr = formatTime(dhuhrCal),
            asr = formatTime(asrCal),
            sunset = formatTime(makeCalendar(calendar, sunsetHour)),
            maghrib = formatTime(maghribCal),
            isha = formatTime(ishaCal),
            midnight = formatTime(midnightCal),
            tahajjudBestStart = formatTime(tahajjudStartCal),
            sehriEnd = formatTime(sehriCal),
            iftarTime = formatTime(maghribCal),
            fajrCal = fajrCal,
            sunriseCal = sunriseCal,
            dhuhrCal = dhuhrCal,
            asrCal = asrCal,
            maghribCal = maghribCal,
            ishaCal = ishaCal,
            tahajjudStartCal = tahajjudStartCal
        )
    }

    fun getNextPrayer(now: Calendar, schedule: PrayerSchedule): NextPrayerInfo {
        val nowMillis = now.timeInMillis

        return when {
            nowMillis < schedule.fajrCal.timeInMillis -> {
                NextPrayerInfo("ফজর", "الفجر", schedule.fajr, schedule.fajrCal.timeInMillis - nowMillis)
            }
            nowMillis < schedule.sunriseCal.timeInMillis -> {
                NextPrayerInfo("সূর্যোদয় (ফজর শেষ)", "الشروق", schedule.sunrise, schedule.sunriseCal.timeInMillis - nowMillis)
            }
            nowMillis < schedule.dhuhrCal.timeInMillis -> {
                NextPrayerInfo("যোহর", "الظهر", schedule.dhuhr, schedule.dhuhrCal.timeInMillis - nowMillis)
            }
            nowMillis < schedule.asrCal.timeInMillis -> {
                NextPrayerInfo("আসর", "العصر", schedule.asr, schedule.asrCal.timeInMillis - nowMillis)
            }
            nowMillis < schedule.maghribCal.timeInMillis -> {
                NextPrayerInfo("মাগরিব (ইফতার)", "المغرب", schedule.maghrib, schedule.maghribCal.timeInMillis - nowMillis)
            }
            nowMillis < schedule.ishaCal.timeInMillis -> {
                NextPrayerInfo("ইশা", "العشاء", schedule.isha, schedule.ishaCal.timeInMillis - nowMillis)
            }
            else -> {
                // Next day's Fajr
                val tomorrowFajr = (schedule.fajrCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                NextPrayerInfo("ফজর (আগামীকাল)", "الفجر", schedule.fajr, tomorrowFajr.timeInMillis - nowMillis, isNextDay = true)
            }
        }
    }

    fun getCurrentWaqtName(now: Calendar, schedule: PrayerSchedule): String {
        val nowMillis = now.timeInMillis
        return when {
            nowMillis in schedule.fajrCal.timeInMillis until schedule.sunriseCal.timeInMillis -> "ফজরের ওয়াক্ত চলছে"
            nowMillis in schedule.sunriseCal.timeInMillis until schedule.dhuhrCal.timeInMillis -> "ইশরাক / চাশত এর সময়"
            nowMillis in schedule.dhuhrCal.timeInMillis until schedule.asrCal.timeInMillis -> "যোহরের ওয়াক্ত চলছে"
            nowMillis in schedule.asrCal.timeInMillis until schedule.maghribCal.timeInMillis -> "আসরের ওয়াক্ত চলছে"
            nowMillis in schedule.maghribCal.timeInMillis until schedule.ishaCal.timeInMillis -> "মাগরিবের ওয়াক্ত চলছে"
            else -> "ইশার ওয়াক্ত / রাতের সময়"
        }
    }

    private fun formatTime(cal: Calendar): String {
        var hour = cal.get(Calendar.HOUR)
        if (hour == 0) hour = 12
        val minute = cal.get(Calendar.MINUTE)
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "ভোর/সকাল" else "দুপুর/রাত"
        
        val engTime = String.format(Locale.US, "%02d:%02d", hour, minute)
        return toBengaliDigits(engTime)
    }

    fun formatCountdown(remainingMillis: Long): String {
        val hours = remainingMillis / (1000 * 60 * 60)
        val minutes = (remainingMillis % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (remainingMillis % (1000 * 60)) / 1000

        val bnHours = toBengaliNumber(hours.toInt())
        val bnMinutes = toBengaliNumber(minutes.toInt())
        val bnSeconds = toBengaliNumber(seconds.toInt())

        return if (hours > 0) {
            "$bnHours ঘণ্টা $bnMinutes মিনিট $bnSeconds সেকেন্ড"
        } else {
            "$bnMinutes মিনিট $bnSeconds সেকেন্ড"
        }
    }

    private fun makeCalendar(base: Calendar, decimalHour: Double): Calendar {
        val cal = base.clone() as Calendar
        var hour = decimalHour.toInt()
        var min = ((decimalHour - hour) * 60.0).roundToInt()
        if (min == 60) {
            hour += 1
            min = 0
        }
        if (hour >= 24) {
            hour -= 24
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, min)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun sunAngleTime(angle: Double, lat: Double, declination: Double): Double {
        val radLat = Math.toRadians(lat)
        val radDec = Math.toRadians(declination)
        val radAngle = Math.toRadians(angle)
        val cosH = (sin(-radAngle) - sin(radLat) * sin(radDec)) / (cos(radLat) * cos(radDec))
        val clampedCosH = cosH.coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(clampedCosH)) / 15.0
    }

    private fun getJulianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun fixAngle(angle: Double): Double {
        var a = angle - 360.0 * floor(angle / 360.0)
        if (a < 0) a += 360.0
        return a
    }

    private fun fixHour(hour: Double): Double {
        var h = hour - 24.0 * floor(hour / 24.0)
        if (h < 0) h += 24.0
        return h
    }
}
