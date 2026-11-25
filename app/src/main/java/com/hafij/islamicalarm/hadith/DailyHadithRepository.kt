package com.hafij.islamicalarm.hadith

import android.content.Context
import com.hafij.islamicalarm.duas.DuaFortyHadith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Fetches and caches one verified authentic Hadith per calendar day using the public
 * fawazahmed0/hadith-api CDN (Bengali + Arabic Sahih collections), with instant offline fallback
 * backed by the curated authentic Hadith pool.
 */
object DailyHadithRepository {

    private const val PREFS_NAME = "daily_hadith_cache_v1"
    private const val KEY_DATE = "cached_date"
    private const val KEY_ID = "cached_id"
    private const val KEY_BOOK = "cached_book"
    private const val KEY_NUM_BN = "cached_num_bn"
    private const val KEY_GRADE = "cached_grade"
    private const val KEY_TOPIC = "cached_topic"
    private const val KEY_ARABIC = "cached_arabic"
    private const val KEY_BENGALI = "cached_bengali"
    private const val KEY_REF = "cached_ref"
    private const val KEY_ONLINE = "cached_online"

    private const val CDN_BASE = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    internal val apiTargets: List<HadithApiTarget> = listOf(
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 1, "নিয়তের বিশুদ্ধতা"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 10, "প্রকৃত মুসলিমের পরিচয়"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 13, "ভ্রাতৃত্ব ও ভালোবাসা"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 527, "সময়মতো সালাত আদায়"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 648, "জামাতে নামাজের ফজিলত"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 1417, "দান-সদকার বরকত"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 1901, "সিয়াম ও আত্মসংযম"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 2442, "বিপদে সাহায্য ও সহযোগিতা"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 5027, "কুরআন শেখা ও শেখানো"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 5641, "ধৈর্য ও গুনাহ মাফ"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 6018, "সদাচরণ ও প্রতিবেশীর হক"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 6035, "উত্তম চরিত্রের মর্যাদা"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 6094, "সত্যবাদিতার পুরস্কার"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 6116, "রাগ নিয়ন্ত্রণ"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 6137, "দয়া ও কোমলতা"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 6407, "আল্লাহর জিকিরের ফজিলত"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 6412, "সুস্থতা ও অবসরের মূল্য"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 6502, "আল্লাহর নৈকট্য ও নফল ইবাদত"),
        HadithApiTarget("bukhari", "সহিহুল বুখারী", 7563, "প্রিয় তাসবিহ: সুবহানাল্লাহি ওয়া বিহামদিহি"),
        HadithApiTarget("muslim", "সহিহ মুসলিম", 45, "মুমিনের ভালোবাসা"),
        HadithApiTarget("muslim", "সহিহ মুসলিম", 55, "কল্যাণকামিতা (নসিহত)"),
        HadithApiTarget("muslim", "সহিহ মুসলিম", 223, "পবিত্রতা ও আলহামদুলিল্লাহ"),
        HadithApiTarget("muslim", "সহিহ মুসলিম", 482, "সেজদায় দোয়ার গুরুত্ব"),
        HadithApiTarget("muslim", "সহিহ মুসলিম", 728, "১২ রাকাত সুন্নাত সালাত"),
        HadithApiTarget("muslim", "সহিহ মুসলিম", 1631, "সদকায়ে জারিয়া ও নেক সন্তান"),
        HadithApiTarget("muslim", "সহিহ মুসলিম", 2564, "অন্তর ও আমলের মূল্যায়ন"),
        HadithApiTarget("muslim", "সহিহ মুসলিম", 2699, "বিপদ দূর করা ও ইলম অন্বেষণ"),
        HadithApiTarget("muslim", "সহিহ মুসলিম", 2747, "তওবায় আল্লাহর সন্তুষ্টি"),
        HadithApiTarget("muslim", "সহিহ মুসলিম", 2999, "মুমিনের সকল অবস্থাই কল্যাণকর"),
        HadithApiTarget("tirmidhi", "জামে তিরমিযি", 1956, "হাসিমুখে কথা বলা সদকা", "হাসান সহিহ"),
        HadithApiTarget("tirmidhi", "জামে তিরমিযি", 1987, "তাকওয়া ও উত্তম চরিত্র", "হাসান সহিহ"),
        HadithApiTarget("tirmidhi", "জামে তিরমিযি", 2317, "অনর্থক কথা ও কাজ বর্জন", "সহিহ"),
        HadithApiTarget("tirmidhi", "জামে তিরমিযি", 2485, "আল্লাহর ওপর তাওয়াক্কুল", "হাসান সহিহ"),
        HadithApiTarget("tirmidhi", "জামে তিরমিযি", 2516, "সদা আল্লাহকে স্মরণ রাখা", "হাসান সহিহ"),
        HadithApiTarget("tirmidhi", "জামে তিরমিযি", 3377, "দোয়া ইবাদতের সারবস্তু", "সহিহ")
    )

    internal data class ParsedHadithPayload(
        val text: String,
        val hadithNumber: String,
        val grade: String
    )

    fun todayDateKey(nowMs: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(nowMs))

    internal fun daySlotIndex(dateKey: String, offset: Int, poolSize: Int): Int {
        if (poolSize <= 0) return 0
        val parts = dateKey.split("-")
        val daySeed = if (parts.size == 3) {
            val y = parts[0].toIntOrNull() ?: 2026
            val m = parts[1].toIntOrNull() ?: 1
            val d = parts[2].toIntOrNull() ?: 1
            y * 372 + m * 31 + d
        } else {
            abs(dateKey.hashCode())
        }
        return ((daySeed + offset) % poolSize + poolSize) % poolSize
    }

    internal fun selectTargetForDate(dateKey: String, offset: Int = 0): HadithApiTarget =
        apiTargets[daySlotIndex(dateKey, offset, apiTargets.size)]

    /**
     * Immediately returns either today's cached Hadith (when offset == 0) or a deterministic
     * authentic Hadith from the offline Sahih collection so the UI never waits blank.
     */
    fun getInstantHadithForDate(
        context: Context,
        dateKey: String = todayDateKey(),
        offset: Int = 0
    ): DailyHadith {
        if (offset == 0) {
            readCachedHadith(context, dateKey)?.let { return it }
        }
        return getOfflineFallbackHadith(dateKey, offset)
    }

    internal fun getOfflineFallbackHadith(dateKey: String, offset: Int = 0): DailyHadith {
        val pool = DuaFortyHadith.items
        val idx = daySlotIndex(dateKey, offset, pool.size)
        val item = pool[idx]
        val cleanTitle = item.title.replace(Regex("^[০-৯0-9]+\\.\\s*"), "").trim()
        val refParts = item.reference.split(";").firstOrNull()?.trim().orEmpty()
        return DailyHadith(
            id = "offline_${item.id}",
            bookNameBn = refParts.ifBlank { "সহিহ হাদিস সংকলন" },
            hadithNumberBn = toBanglaDigits((idx + 1).toString()),
            gradeBn = item.grade.ifBlank { "সহিহ" },
            topicBn = cleanTitle.ifBlank { "দৈনন্দিন জীবন ও আমল" },
            arabicText = item.arabic,
            bengaliText = item.translation,
            referenceBn = item.reference.ifBlank { "সহিহ হাদিস" },
            isFromOnlineApi = false,
            dateKey = dateKey
        )
    }

    /**
     * Fetches today's (or offset-selected) authentic Hadith from the jsDelivr Hadith API CDN
     * (Bengali + Arabic in parallel). Falls back cleanly to curated offline Sahih Hadith on failure.
     */
    suspend fun fetchOrGetDailyHadith(
        context: Context,
        dateKey: String = todayDateKey(),
        offset: Int = 0,
        forceNetwork: Boolean = false
    ): DailyHadith = withContext(Dispatchers.IO) {
        if (!forceNetwork && offset == 0) {
            readCachedHadith(context, dateKey)?.let { cached ->
                if (cached.isFromOnlineApi) return@withContext cached
            }
        }

        val target = selectTargetForDate(dateKey, offset)
        val bnUrl = "$CDN_BASE/ben-${target.collectionCode}/${target.hadithNumber}.min.json"
        val arUrl = "$CDN_BASE/ara-${target.collectionCode}/${target.hadithNumber}.min.json"

        val (bnJson, arJson) = try {
            coroutineScope {
                val bnDeferred = async { httpGet(bnUrl) }
                val arDeferred = async { httpGet(arUrl) }
                bnDeferred.await() to arDeferred.await()
            }
        } catch (_: Exception) {
            null to null
        }

        val bnParsed = bnJson?.let { parseHadithApiJson(it) }
        val arParsed = arJson?.let { parseHadithApiJson(it) }

        if (bnParsed != null && bnParsed.text.length >= 25) {
            val numberStr = bnParsed.hadithNumber.ifBlank { target.hadithNumber.toString() }
            val numBn = toBanglaDigits(numberStr)
            val resolvedGrade = bnParsed.grade.ifBlank { target.gradeBn }
            val result = DailyHadith(
                id = "${target.collectionCode}_${target.hadithNumber}",
                bookNameBn = target.bookNameBn,
                hadithNumberBn = numBn,
                gradeBn = resolvedGrade,
                topicBn = target.topicBn,
                arabicText = arParsed?.text.orEmpty(),
                bengaliText = bnParsed.text,
                referenceBn = "${target.bookNameBn} • হাদিস নং $numBn",
                isFromOnlineApi = true,
                dateKey = dateKey
            )
            if (offset == 0) {
                saveCachedHadith(context, result)
            }
            return@withContext result
        }

        val fallback = getOfflineFallbackHadith(dateKey, offset)
        if (offset == 0 && readCachedHadith(context, dateKey) == null) {
            saveCachedHadith(context, fallback)
        }
        fallback
    }

    internal fun parseHadithApiJson(rawJson: String): ParsedHadithPayload? {
        return try {
            val root = JSONObject(rawJson)
            val hadiths = root.optJSONArray("hadiths") ?: return null
            if (hadiths.length() == 0) return null
            val entry = hadiths.getJSONObject(0)
            val text = entry.optString("text", "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (text.isBlank()) return null

            val rawNum = entry.optString("hadithnumber", "").trim()
                .ifBlank { entry.optInt("hadithnumber", 0).takeIf { it > 0 }?.toString().orEmpty() }
                .removeSuffix(".0")

            var gradeBn = ""
            val grades = entry.optJSONArray("grades")
            if (grades != null && grades.length() > 0) {
                for (i in 0 until grades.length()) {
                    val gObj = grades.optJSONObject(i) ?: continue
                    val gRaw = gObj.optString("grade", "").trim()
                    if (gRaw.isNotBlank()) {
                        gradeBn = normalizeGradeToBangla(gRaw)
                        break
                    }
                }
            }

            ParsedHadithPayload(
                text = text,
                hadithNumber = rawNum,
                grade = gradeBn
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeGradeToBangla(raw: String): String {
        val lower = raw.lowercase(Locale.ENGLISH)
        return when {
            lower.contains("sahih") && lower.contains("hasan") -> "হাসান সহিহ"
            lower.contains("sahih") -> "সহিহ"
            lower.contains("hasan") -> "হাসান"
            else -> raw
        }
    }

    private fun httpGet(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readCachedHadith(context: Context, dateKey: String): DailyHadith? {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (sp.getString(KEY_DATE, null) != dateKey) return null
        val bengali = sp.getString(KEY_BENGALI, null)?.takeIf { it.isNotBlank() } ?: return null
        return DailyHadith(
            id = sp.getString(KEY_ID, "") ?: "",
            bookNameBn = sp.getString(KEY_BOOK, "সহিহুল বুখারী") ?: "সহিহুল বুখারী",
            hadithNumberBn = sp.getString(KEY_NUM_BN, "") ?: "",
            gradeBn = sp.getString(KEY_GRADE, "সহিহ") ?: "সহিহ",
            topicBn = sp.getString(KEY_TOPIC, "দৈনন্দিন বাণী") ?: "দৈনন্দিন বাণী",
            arabicText = sp.getString(KEY_ARABIC, "") ?: "",
            bengaliText = bengali,
            referenceBn = sp.getString(KEY_REF, "সহিহ হাদিস") ?: "সহিহ হাদিস",
            isFromOnlineApi = sp.getBoolean(KEY_ONLINE, false),
            dateKey = dateKey
        )
    }

    private fun saveCachedHadith(context: Context, hadith: DailyHadith) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DATE, hadith.dateKey)
            .putString(KEY_ID, hadith.id)
            .putString(KEY_BOOK, hadith.bookNameBn)
            .putString(KEY_NUM_BN, hadith.hadithNumberBn)
            .putString(KEY_GRADE, hadith.gradeBn)
            .putString(KEY_TOPIC, hadith.topicBn)
            .putString(KEY_ARABIC, hadith.arabicText)
            .putString(KEY_BENGALI, hadith.bengaliText)
            .putString(KEY_REF, hadith.referenceBn)
            .putBoolean(KEY_ONLINE, hadith.isFromOnlineApi)
            .apply()
    }

    fun formatShareText(hadith: DailyHadith): String = buildString {
        append("📜 আজকের সহিহ হাদিস")
        if (hadith.topicBn.isNotBlank()) append(" — ${hadith.topicBn}")
        append("\n\n")
        if (hadith.arabicText.isNotBlank()) {
            append(hadith.arabicText)
            append("\n\n")
        }
        append(hadith.bengaliText)
        append("\n\n— ")
        append(hadith.referenceBn)
        if (hadith.gradeBn.isNotBlank()) append(" (${hadith.gradeBn})")
    }

    fun toBanglaDigits(text: String): String {
        val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val out = StringBuilder(text.length)
        for (ch in text) {
            if (ch in '0'..'9') out.append(bnDigits[ch - '0'])
            else out.append(ch)
        }
        return out.toString()
    }
}
