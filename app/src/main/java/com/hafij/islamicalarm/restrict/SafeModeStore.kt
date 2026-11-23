package com.hafij.islamicalarm.restrict

import android.content.Context
import java.util.Locale

/** Safe mode on/off, DNS VPN / Incognito / SafeSearch flags, and the user's blocked words/domains. */
class SafeModeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    var isDnsVpnEnabled: Boolean
        get() = prefs.getBoolean(KEY_DNS_VPN_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DNS_VPN_ENABLED, value).apply()
        }

    var isIncognitoBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_INCOGNITO_BLOCK, true)
        set(value) {
            prefs.edit().putBoolean(KEY_INCOGNITO_BLOCK, value).apply()
        }

    var isSafeSearchEnabled: Boolean
        get() = prefs.getBoolean(KEY_SAFE_SEARCH, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SAFE_SEARCH, value).apply()
        }

    var dnsBlockedCount: Int
        get() = prefs.getInt(KEY_DNS_BLOCKED_COUNT, 0)
        private set(value) {
            prefs.edit().putInt(KEY_DNS_BLOCKED_COUNT, value).apply()
        }

    var lastBlockedDomain: String?
        get() = prefs.getString(KEY_LAST_BLOCKED_DOMAIN, null)
        private set(value) {
            prefs.edit().putString(KEY_LAST_BLOCKED_DOMAIN, value).apply()
        }

    fun recordDnsBlock(domain: String) {
        prefs.edit()
            .putInt(KEY_DNS_BLOCKED_COUNT, dnsBlockedCount + 1)
            .putString(KEY_LAST_BLOCKED_DOMAIN, domain)
            .apply()
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
        private const val KEY_DNS_VPN_ENABLED = "dns_vpn_enabled"
        private const val KEY_INCOGNITO_BLOCK = "incognito_block_enabled"
        private const val KEY_SAFE_SEARCH = "safe_search_enabled"
        private const val KEY_DNS_BLOCKED_COUNT = "dns_blocked_count"
        private const val KEY_LAST_BLOCKED_DOMAIN = "last_blocked_domain"
        private const val KEY_WORDS = "custom_words"
    }
}

/**
 * Built-in list of adult/NSFW video domains, TLDs, and phrases used by Safe mode
 * (both in Accessibility URL/player checking and in [SafeDnsFilter]).
 *
 * Matching rules:
 *  - [isSearchEngineUrl]: identifies search result pages (Google, Bing, DuckDuckGo, Yahoo, etc.)
 *    so searching with or without SafeSearch never triggers Safe Mode on search alone.
 *  - [matchesDomainHost]: checks bare DNS hostnames cleanly for the system-wide Local VPN DNS blocker.
 *  - [matchesSiteUrl]: checks browser URL bars / page hosts (ignoring search engine query URLs)
 *    so entering an adult/NSFW website after clicking a search result triggers Safe Mode.
 *  - [matches]: checks non-editable page/video titles and descriptions when a video player
 *    or adult/NSFW video context is active.
 */
object SafeWords {

    // ---------------------------------------------------------------- 1. Dedicated Adult TLDs
    private val adultTlds = listOf("xxx", "porn", "sex", "adult", "sexy", "webcam")

    // ---------------------------------------------------------------- 2. Known Adult/NSFW Video & Cam Domains (220+)
    private val exactDomainStems = listOf(
        "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "tube8",
        "spankbang", "brazzers", "chaturbate", "stripchat", "livejasmin", "onlyfans",
        "manyvids", "eporner", "tnaflix", "motherless", "rule34", "nhentai", "fapello",
        "hanime", "hentaihaven", "beeg", "txxx", "hclips", "hdzog", "drtuber",
        "nuvid", "sunporno", "vporn", "4tube", "porntube", "porn300", "porndig",
        "pornone", "pornhd", "porntrex", "porngo", "pornhat", "porn00", "hqporner",
        "ixxx", "thumbzilla", "gotporn", "alohatube", "empflix", "hellporno",
        "keezmovies", "extremetube", "cliphunter", "cam4", "bongacams", "myfreecams",
        "camsoda", "flirt4free", "imlive", "streamate", "xfantasy", "fansly",
        "coomer", "kemono", "simpcity", "thothub", "erome", "bunkr", "redgifs",
        "scrolller", "hitomi", "e-hentai", "exhentai", "tsumino", "fakku",
        "3hentai", "asmhentai", "simply-hentai", "missav", "jable", "javhd",
        "javmost", "javlibrary", "supjav", "av01", "123av", "netflav", "tktube",
        "youjizz", "jizzbunker", "fuq", "sexvid", "xbabe", "xfreesex", "xxxvideos",
        "heavy-r", "efukt", "kaotic", "boyfriendtv", "gaymaletube", "xgroovy",
        "definebabe", "babesource", "metart", "joymii", "nubiles", "nubilefilms",
        "teamskeet", "bangbros", "realitykings", "naughtyamerica", "evilangel",
        "vixen", "blacked", "tushy", "deeper", "dorcelclub", "mofos", "fakehub",
        "adulttime", "fetlife", "literotica", "xozilla", "iceporncasting",
        "daftsex", "sxyprn", "yespornplease", "biqle", "veporn", "shooshtime",
        "pervertslut", "desixnxx", "indianpornvideos", "desipapa", "masalahub",
        "fsiblog", "kamababa", "antarvasna", "xmoviesforyou", "watchporn",
        "fullporner", "freeomovie", "pandamovies", "netfapx", "pmvhaven",
        "tubeperverzija", "vxxx", "zbporn", "pornfidelity", "brattysis",
        "familystrokes", "twistys", "babes", "digitalplayground", "wicked",
        "vivid", "kink", "slayed", "letsdoeit", "sexyhub", "fakehostel",
        "publicagent", "faketaxi", "bang", "scoreland", "playboyplus",
        "penthouse", "hustler", "xart", "hegre", "tushyraw", "blackedraw",
        "pornbb", "pornktube", "pornrabbit", "pornid", "pornrox", "pornburst",
        "pornwhite", "pornflip", "porntube3", "pornv", "xxxbunker", "xxxymovies",
        "xxnx", "xvideo", "xnxx2", "xnxx3", "xvideos2", "xhamster2",
        "spankwire", "slutload", "fapdu", "fapvid", "fapster", "ashemaletube",
        "sexu", "tubegalore", "tubepornclassic", "porntop", "pornmaki", "hdporncomics",
        "multporn", "luscious", "gelbooru", "danbooru", "e621", "sankakucomplex",
        "hentaifox", "imhentai", "pururin", "hentai2read", "doujindesu",
        // Bangla / Banglish / Desi / South-Asian adult sites & mirrors
        "banglasex", "banglachoti", "banglachotigolpo", "chotigolpo", "panugolpo",
        "banglaxxx", "banglaporn", "bdsex", "dhakasex", "kolkatasex", "bengalisex",
        "boudisex", "boudichoti", "desibhabhi", "desisex", "desimms", "desixxx",
        "desijugar", "antarvasnaclips", "auntysex", "bhabhisex", "mallusex",
        "tamilsex", "tamilxxx", "hindisex", "hindiporn", "xxvideo", "xxxbp",
        "xxxbazar", "sextube", "hdsex", "freesex", "freeporn", "sexvideos",
        "viralkand", "mmsbee", "sexkahaani", "chodachudi", "banglasexy",
        "xhamster19", "xhamster42", "xhamsterdesi", "xnxx1", "xvideos3", "xxxtik",
        "desikahani", "choti", "bfvideo", "xbf"
    )

    private val exactHostDomains = listOf(
        "porn.com", "sex.com", "fux.com", "ok.xxx", "nsfw.xxx", "fap.bar"
    )

    private val adultUrlPathPatterns = listOf(
        "/r/nsfw", "/r/gonewild", "/r/realgirls", "/r/porn", "/r/hentai",
        "/tag/nsfw", "/tags/nsfw", "/nsfw/",
        "bangla-sex", "bangla_sex", "banglasex",
        "bangla-choti", "bangla_choti", "banglachoti", "choti-golpo", "chotigolpo",
        "bangla-porn", "bangla-xxx", "bangladeshi-sex", "dhaka-sex", "kolkata-sex",
        "boudi-sex", "bhabhi-sex", "hot-bhabhi", "desi-sex", "desi-porn",
        "desi-mms", "viral-mms", "indian-sex", "indian-porn", "hindi-sex",
        "nude-video", "sex-video", "sexy-video", "porn-video", "xxx-video",
        "chodachudi", "blue-film", "bf-video",
        "সেক্স-ভিডিও", "সেক্স_ভিডিও", "বাংলা-সেক্স", "বাংলা-চটি", "চটি-গল্প",
        "পর্ন-ভিডিও", "পর্নো", "অশ্লীল-ভিডিও", "নগ্ন-ভিডিও", "বৌদি-সেক্স"
    )

    // ---------------------------------------------------------------- 3. Substring stems
    private val englishSubstrings = listOf(
        "porn", "hentai", "xvideos", "xnxx", "xhamster"
    )

    // ---------------------------------------------------------------- 4. Whole words (video context)
    private val wholeWords = listOf(
        "xxx", "nsfw", "nude", "nudes", "nudity", "naked", "erotic", "erotica",
        "boobs", "pussy", "milf", "blowjob", "creampie", "bdsm", "handjob",
        "uncensored", "18+", "r18", "chudai", "chodachudi"
    )

    // ---------------------------------------------------------------- 5. Phrases
    private val englishPhrases = listOf(
        "adult video", "adult videos", "adult content", "adult site", "adult website",
        "adult entertainment", "adult only", "adults only", "mature content",
        "age restricted", "18 plus video", "18 plus videos", "18 plus site",
        "18+ channel", "nsfw channel", "x rated", "sex video", "sex videos",
        "sexual video", "sexy video", "sexy videos", "sexy dance", "hot bhabhi",
        "bhabhi video", "desi mms", "viral mms", "mms video", "mms leak",
        "sex clip", "sex clips", "nude clip", "adult clip", "18+ clip",
        "full link video", "viral link video", "onlyfans leak", "leaked nudes",
        "nude leak", "sex story", "sex stories", "sex photo", "sex photos", "sex chat",
        "sex movie", "sex movies", "xxx video", "xxx videos", "blue film", "bf video",
        "explicit video", "explicit content", "erotic video", "uncensored video",
        "uncensored jav", "jav hd", "nude photo", "nude photos", "nude video", "nude videos",
        "naked girl", "naked girls", "cam show", "webcam show", "live cam", "cam girl",
        "hot video", "leaked video", "private video", "nsfw video", "nsfw content",
        "bangla sex", "bangla porn", "indian porn", "desi porn", "hindi porn",
        "desi sex", "hot sex", "stepmom", "stepsis"
    )

    // ---------------------------------------------------------------- 6. Banglish
    private val banglish = listOf(
        "oshleel", "oshlin", "nogno", "nognota", "nognovideo",
        "jouno video", "jouno uttejak", "boro der video",
        "bangla choti", "choti golpo", "chotigolpo", "panu golpo",
        "chudai", "chodachudi", "choda", "desiporn", "desisex", "desixxx",
        "bangla hot video", "bangla sexy", "bangla sex", "bangla xxx", "bangla mms",
        "bangladesh sex", "bd sex", "dhaka sex", "kolkata sex", "bengali sex",
        "boudi sex", "boudisex", "bhabhi sex", "desibhabhi", "aunty sex",
        "viral sex", "viral mms", "viral link", "sexi video", "sexclip",
        "bangla viral video", "magi sex", "kamasutra"
    )

    // ---------------------------------------------------------------- 7. Bangla
    private val bangla = listOf(
        "পর্ন", "পর্নো", "পর্নোগ্রাফি", "পর্ণ ভিডিও", "পর্ণগ্রাফি", "পর্ণগ্রাফী",
        "অশ্লীল", "অশ্লীল ভিডিও", "অশ্লীল ছবি", "অশালীন",
        "নগ্ন", "নগ্নতা", "নগ্ন ছবি", "নগ্ন ভিডিও", "ন্যুড", "এক্সএক্সএক্স", "এক্স ভিডিও",
        "যৌন ভিডিও", "যৌন উত্তেজক", "সেক্স ভিডিও", "সেক্সি ভিডিও", "হট ভিডিও",
        "বাংলা সেক্স", "বাংলা চটি", "পানু গল্প", "চটি গল্প",
        "বৌদি সেক্স", "দেশি সেক্স", "আন্টি সেক্স", "চোদা", "চোদাচুদি",
        "সেক্স গল্প", "সেক্স ছবি", "সেক্স চ্যাট", "ভাইরাল লিংক", "ভাইরাল ভিডিও ১৮",
        "প্রাপ্তবয়স্ক ভিডিও", "প্রাপ্তবয়স্ক সাইট",
        "আপত্তিকর ভিডিও", "আপত্তিকর ছবি",
        "গোপন ভিডিও", "ফাঁস হওয়া ভিডিও", "ব্লু ফিল্ম"
    )

    // ---------------------------------------------------------------- 8. Raw text
    private val rawTerms = listOf(
        "18+ video", "18+ videos", "18+ site", "18+ website", "18+ content",
        "18+ movie", "18+ movies", "18+ link", "#nsfw", "#adult", "#18+",
        "#xxx", "#porn", "#sex", "#hentai", "#choti", "#banglasex"
    )

    private val separators = Regex("[^\\p{L}\\p{M}\\p{N}]+")
    private val hostRegex = Regex("^(?:https?://)?(?:www\\.)?([^/?#:\\s]+)(.*)$")

    private fun normalize(raw: String): String =
        " " + raw.lowercase(Locale.ROOT).replace(separators, " ").trim() + " "

    fun matches(raw: String, custom: Collection<String> = emptyList()): Boolean {
        if (raw.length < 3) return false

        val lower = raw.lowercase(Locale.ROOT)
        for (term in rawTerms) {
            if (lower.contains(term)) return true
        }

        val text = normalize(raw)
        for (term in exactDomainStems) if (text.contains(" $term ")) return true
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

    private val explicitMirrorStems = listOf(
        "porn", "hentai", "xvideos", "xnxx", "xhamster", "redtube", "youporn",
        "spankbang", "brazzers", "chaturbate", "stripchat", "livejasmin", "onlyfans",
        "eporner", "tnaflix", "motherless", "rule34", "fapello", "missav", "youjizz",
        "xgroovy", "bangbros", "naughtyamerica", "evilangel", "fansly", "bongacams",
        "myfreecams", "camsoda", "xxxbunker", "xxnx", "desixnxx", "javmost", "javlibrary",
        "supjav", "hanime", "redgifs", "erome", "txxx", "vxxx", "tube8",
        "banglasex", "banglachoti", "chotigolpo", "panugolpo", "boudisex",
        "desisex", "desixxx", "desimms", "indiansex", "hindisex", "bhabhisex",
        "auntysex", "kolkatasex", "dhakasex", "bengalisex", "bdsex", "antarvasna",
        "kamababa", "fsiblog", "masalahub", "desipapa", "chodachudi"
    )

    private fun decodeUrlSafely(raw: String): String {
        return try {
            java.net.URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
    }

    /**
     * Returns true when [rawUrl] is a search engine results or home/query page (including SafeSearch).
     * Searching itself never triggers Safe Mode — only clicking through onto an adult site or video does.
     */
    fun isSearchEngineUrl(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        val trimmed = rawUrl.trim().lowercase(Locale.ROOT)
        if (trimmed.contains(' ') && !trimmed.startsWith("http")) return false
        val match = hostRegex.find(trimmed) ?: return false
        val host = match.groupValues[1]
        val rest = match.groupValues[2]
        if (!host.contains('.')) return false

        return (host.contains("google.") && (rest.startsWith("/search") || rest == "/" || rest.isEmpty() || rest.contains("safe=active"))) ||
            (host.contains("bing.com") && (rest.startsWith("/search") || rest.startsWith("/videos/search") || rest.startsWith("/images/search"))) ||
            (host.contains("duckduckgo.com") && (rest.startsWith("/?") || rest == "/" || rest.isEmpty())) ||
            (host.contains("search.yahoo.com")) ||
            (host.contains("yandex.") && rest.startsWith("/search")) ||
            (host.contains("ecosia.org") && rest.startsWith("/search")) ||
            (host.contains("youtube.com") && rest.startsWith("/results"))
    }

    /** Checks a bare DNS domain name (used by [SafeDnsFilter]). Never blocks search engines. */
    fun matchesDomainHost(rawHost: String, custom: Collection<String> = emptyList()): Boolean {
        val cleanHost = rawHost.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (!cleanHost.contains('.')) return false

        // Keep standard search engines reachable so users can search with SafeSearch enforced upstream
        if (cleanHost == "google.com" || cleanHost.endsWith(".google.com") ||
            cleanHost == "bing.com" || cleanHost.endsWith(".bing.com") ||
            cleanHost == "duckduckgo.com" || cleanHost.endsWith(".duckduckgo.com") ||
            cleanHost == "yahoo.com" || cleanHost.endsWith(".yahoo.com")
        ) {
            return false
        }

        if (adultTlds.any { tld -> cleanHost.endsWith(".$tld") }) return true
        if (exactHostDomains.any { dom -> cleanHost == dom || cleanHost.endsWith(".$dom") }) return true

        val hostLabels = cleanHost.split('.')
        if (exactDomainStems.any { stem -> hostLabels.any { it == stem } }) return true
        if (explicitMirrorStems.any { stem -> hostLabels.any { it.contains(stem) } }) return true

        for (c in custom) {
            val clean = c.trim().lowercase(Locale.ROOT)
            if (clean.length >= 3 && cleanHost.contains(clean)) return true
        }
        return false
    }

    /**
     * Checks whether a visible browser URL represents an actual adult/NSFW website
     * (excluding search engine result URLs like google.com/search?q=adult).
     */
    fun matchesSiteUrl(rawUrl: String, custom: Collection<String> = emptyList()): Boolean {
        if (rawUrl.isBlank()) return false
        if (isSearchEngineUrl(rawUrl)) return false

        val trimmed = rawUrl.trim().lowercase(Locale.ROOT)
        if (trimmed.contains(' ') && !trimmed.startsWith("http")) return false

        val match = hostRegex.find(trimmed) ?: return false
        val host = match.groupValues[1]
        val rest = match.groupValues[2]

        if (!host.contains('.')) return false

        val decodedHost = decodeUrlSafely(host).lowercase(Locale.ROOT)
        val decodedRest = decodeUrlSafely(rest).lowercase(Locale.ROOT)

        if (matchesDomainHost(decodedHost, custom)) return true

        // Explicit NSFW / Bangla / Banglish adult path slug on non-search websites
        if (adultUrlPathPatterns.any { decodedRest.contains(it) }) return true

        // User custom blocked keywords in path/URL
        for (c in custom) {
            val clean = c.trim().lowercase(Locale.ROOT)
            if (clean.length >= 3 && decodedRest.contains(clean)) {
                return true
            }
        }
        return false
    }
}
