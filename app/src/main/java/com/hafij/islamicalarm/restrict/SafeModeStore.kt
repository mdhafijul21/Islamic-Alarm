package com.hafij.islamicalarm.restrict

import android.content.Context
import java.util.Locale

/** Safe mode on/off and the user's own blocked words. */
class SafeModeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun getCustomWords(): List<String> {
        val raw = prefs.getString(KEY_WORDS, "") ?: ""
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun setCustomWords(words: List<String>) {
        val clean = words.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        prefs.edit().putString(KEY_WORDS, clean.joinToString("\n")).apply()
    }

    companion object {
        private const val PREF_NAME = "safe_mode_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_WORDS = "custom_words"
    }
}

/**
 * Built-in list of adult search words and sites used by Safe mode.
 * It is never complete: the user can add more words in the Safe mode tab.
 *
 * How the lists are matched (text is lower-cased and punctuation becomes spaces):
 *  - [sites], [englishSubstrings], [banglish], [bangla]: found anywhere in the text.
 *  - [wholeWords]: only as a complete word ("naked eye" is a word match, "snaked" is not).
 *  - [englishPhrases]: must start at a word boundary ("shot video" does not match "hot video").
 *  - [rawTerms]: matched on the original text before punctuation is removed (for "18+").
 */
object SafeWords {

    // ---------------------------------------------------------------- 1. Adult sites
    private val sites = listOf(
        "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "tube8",
        "spankbang", "brazzers", "chaturbate", "stripchat", "livejasmin", "onlyfans",
        "manyvids", "eporner", "tnaflix", "motherless", "rule34", "nhentai", "fapello"
    )

    // ---------------------------------------------------------------- 2. English: found anywhere
    // "porn" also catches porno, pornography, pornstar, porn video ...
    private val englishSubstrings = listOf(
        "porn", "hentai"
    )

    // ---------------------------------------------------------------- 3. English: whole word only
    private val wholeWords = listOf(
        "xxx", "nsfw", "nude", "nudes", "nudity", "naked", "erotic", "erotica",
        "boobs", "pussy", "milf", "blowjob", "creampie", "bdsm", "handjob"
    )

    // ---------------------------------------------------------------- 4. English: phrases
    private val englishPhrases = listOf(
        "adult video", "adult videos", "adult content", "adult site", "adult website",
        "adult entertainment", "18 plus video", "18 plus videos", "18 plus site",
        "x rated", "sex video", "sex videos", "sexual video", "sexy video", "sexy videos",
        "sex story", "sex stories", "sex photo", "sex photos", "sex chat",
        "sex movie", "sex movies", "xxx video", "xxx videos", "blue film", "bf video",
        "explicit video", "explicit content", "erotic video", "uncensored video",
        "nude photo", "nude photos", "nude video", "nude videos",
        "naked girl", "naked girls", "cam show", "webcam show",
        "hot video", "leaked video", "private video"
    )

    // ---------------------------------------------------------------- 5. Banglish (Bangla in English letters)
    private val banglish = listOf(
        "oshleel", "oshlin", "nogno", "nognota", "nognovideo",
        "jouno video", "jouno uttejak", "boro der video",
        "bangla choti", "choti golpo"
    )

    // ---------------------------------------------------------------- 6. Bangla
    private val bangla = listOf(
        "পর্ন", "পর্নো", "পর্নোগ্রাফি", "পর্ণ ভিডিও", "পর্ণগ্রাফি", "পর্ণগ্রাফী",
        "অশ্লীল", "অশ্লীল ভিডিও", "অশ্লীল ছবি", "অশালীন",
        "নগ্ন", "নগ্নতা", "নগ্ন ছবি", "নগ্ন ভিডিও", "ন্যুড", "এক্সএক্সএক্স",
        "যৌন ভিডিও", "যৌন উত্তেজক", "সেক্স ভিডিও", "সেক্স গল্প", "সেক্স ছবি", "সেক্স চ্যাট",
        "প্রাপ্তবয়স্ক ভিডিও", "প্রাপ্তবয়স্ক সাইট",
        "আপত্তিকর ভিডিও", "আপত্তিকর ছবি",
        "গোপন ভিডিও", "ফাঁস হওয়া ভিডিও", "ব্লু ফিল্ম", "চটি গল্প"
    )

    // ---------------------------------------------------------------- 7. Raw text (before punctuation is removed)
    private val rawTerms = listOf(
        "18+ video", "18+ videos", "18+ site", "18+ website", "18+ content", "18+ movie", "18+ movies"
    )

    private val separators = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    private fun normalize(raw: String): String =
        " " + raw.lowercase(Locale.ROOT).replace(separators, " ").trim() + " "

    fun matches(raw: String, custom: Collection<String>): Boolean {
        if (raw.length < 3) return false

        val lower = raw.lowercase(Locale.ROOT)
        for (term in rawTerms) {
            if (lower.contains(term)) return true
        }

        val text = normalize(raw)
        for (term in sites) if (text.contains(term)) return true
        for (term in englishSubstrings) if (text.contains(term)) return true
        for (term in banglish) if (text.contains(term)) return true
        for (term in bangla) if (text.contains(term)) return true
        for (word in wholeWords) if (text.contains(" $word ")) return true
        for (phrase in englishPhrases) if (text.contains(" $phrase")) return true

        for (c in custom) {
            val cn = normalize(c).trim()
            if (cn.isNotEmpty() && text.contains(cn)) return true
        }
        return false
    }
}
