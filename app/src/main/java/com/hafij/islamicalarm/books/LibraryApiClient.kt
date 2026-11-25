package com.hafij.islamicalarm.books

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Multi-source free Islamic Books, Stories, History & Madrasah Kitab API client:
 *  1. Internet Archive Bangla Islamic Archive (azharmea 560+ books, stories, Sahaba, tafsir & IslamiBoi)
 *  2. Internet Archive Islamic Stories, Sahaba & Seerah (AboutHistoryStoryInIslam, StoriesOfTheProphetsForChildren)
 *  3. Internet Archive Qawmi Madrasah Collection (archive.org/metadata/kawmi/files)
 *  4. OpenLibrary Search API (openlibrary.org/search.json -> Archive PDF resolution)
 *  5. Internet Archive AdvancedSearch & Metadata API (archive.org/advancedsearch.php)
 *  6. Islamic.app Public Classical Library API (api.islamic.app/v1/library/books)
 */
object LibraryApiClient {

    private const val ISLAMIC_APP_BASE = "https://api.islamic.app/v1/library"
    private const val IA_METADATA_KAWMI = "https://archive.org/metadata/kawmi/files"
    private const val IA_AZHARMEA_ID = "azharmea-www.eelm.weebly.com-skype-id-azharmea"
    private const val IA_STORIES_HISTORY_ID = "AboutHistoryStoryInIslam"
    private const val IA_PROPHET_STORIES_ID = "StoriesOfTheProphetsForChildren"

    suspend fun fetchUnifiedCatalog(context: Context): List<RemoteLibraryBook> =
        withContext(Dispatchers.IO) {
            val all = mutableListOf<RemoteLibraryBook>()
            coroutineScope {
                val banglaMegaDef = async {
                    fetchArchiveCollection(
                        identifier = IA_AZHARMEA_ID,
                        defaultJamatCode = RemoteLibraryBook.JAMAT_GENERAL_BANGLA,
                        sourceLabel = "Bangla Islamic Archive",
                        smartClassify = true
                    )
                }
                val storyHistoryDef = async {
                    fetchArchiveCollection(
                        identifier = IA_STORIES_HISTORY_ID,
                        defaultJamatCode = RemoteLibraryBook.CAT_SAHABA_HISTORY,
                        sourceLabel = "History & Stories Archive",
                        smartClassify = true
                    )
                }
                val prophetStoriesDef = async {
                    fetchArchiveCollection(
                        identifier = IA_PROPHET_STORIES_ID,
                        defaultJamatCode = RemoteLibraryBook.CAT_STORIES,
                        sourceLabel = "Prophet Stories",
                        smartClassify = false
                    )
                }
                val kawmiDef = async { fetchQawmiCollection() }
                val islamicBoiDef = async {
                    fetchArchiveCollection(
                        identifier = "IslamiBoi",
                        defaultJamatCode = RemoteLibraryBook.JAMAT_GENERAL_BANGLA,
                        sourceLabel = "IslamiBoi Archive",
                        smartClassify = true
                    )
                }
                val openLibraryDef = async {
                    fetchOpenLibraryBooks("islamic stories prophets seerah", limit = 6)
                }
                val islamicAppDef = async { fetchBooks(limit = 35) }
                val archiveHighlightDef = async {
                    searchArchiveBooks(
                        query = "(jalalain OR mareful OR tirmiji OR bukhari OR golpo OR kahini) AND (bangla OR bengali)",
                        rows = 12
                    )
                }

                // Order so stories, history, tafsir, Bangla books & Madrasah kitabs all populate cleanly
                all.addAll(prophetStoriesDef.await())
                all.addAll(banglaMegaDef.await())
                all.addAll(storyHistoryDef.await())
                all.addAll(kawmiDef.await())
                all.addAll(islamicBoiDef.await())
                all.addAll(archiveHighlightDef.await())
                all.addAll(openLibraryDef.await())
                all.addAll(islamicAppDef.await())
            }

            val deduped = all.distinctBy { it.slug }
            if (deduped.isNotEmpty()) {
                LibraryPrefsStore(context).saveCatalogCache(deduped)
            }
            deduped
        }

    suspend fun fetchQawmiCollection(): List<RemoteLibraryBook> = withContext(Dispatchers.IO) {
        try {
            val jsonStr = httpGet(IA_METADATA_KAWMI, timeoutMs = 12000) ?: return@withContext emptyList()
            parseArchiveKawmiFilesJson(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    internal fun parseArchiveKawmiFilesJson(jsonStr: String): List<RemoteLibraryBook> {
        val root = JSONObject(jsonStr)
        val files = root.optJSONArray("result") ?: return emptyList()
        val out = mutableListOf<RemoteLibraryBook>()

        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val name = f.optString("name", "")
            if (!name.lowercase(Locale.ROOT).endsWith(".pdf") || name.endsWith("_text.pdf")) continue

            val jamatCode = mapKawmiFilenameToJamat(name)
            val displayName = formatKitabDisplayTitle(name)
            val encodedFile = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
            val pdfUrl = "https://archive.org/download/kawmi/$encodedFile"

            out.add(
                RemoteLibraryBook(
                    slug = "kawmi_${name.removeSuffix(".pdf")}",
                    titleAr = "",
                    titleBn = displayName,
                    titleEn = displayName,
                    authorNameBn = "মাদরাসা পাঠ্যকিতাব",
                    authorNameEn = "Madrasah Kitab",
                    genre = jamatLabelForCode(jamatCode),
                    pages = 0,
                    hasPdf = true,
                    jamatCode = jamatCode,
                    pdfUrl = pdfUrl,
                    coverUrl = "",
                    sourceName = "Kitab Archive"
                )
            )
        }
        return out
    }

    suspend fun fetchArchiveCollection(
        identifier: String,
        defaultJamatCode: String,
        sourceLabel: String,
        smartClassify: Boolean = false
    ): List<RemoteLibraryBook> = withContext(Dispatchers.IO) {
        try {
            val jsonStr = httpGet("https://archive.org/metadata/$identifier/files", timeoutMs = 14000)
                ?: return@withContext emptyList()
            parseArchiveCollectionFilesJson(identifier, jsonStr, defaultJamatCode, sourceLabel, smartClassify)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    internal fun parseArchiveCollectionFilesJson(
        identifier: String,
        jsonStr: String,
        defaultJamatCode: String,
        sourceLabel: String,
        smartClassify: Boolean = false
    ): List<RemoteLibraryBook> {
        val root = JSONObject(jsonStr)
        val files = root.optJSONArray("result") ?: return emptyList()
        val out = mutableListOf<RemoteLibraryBook>()

        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val name = f.optString("name", "")
            if (!name.lowercase(Locale.ROOT).endsWith(".pdf") || name.endsWith("_text.pdf")) continue
            if (name == "1.pdf") continue

            val display = formatKitabDisplayTitle(name)
            val categoryCode = if (smartClassify) {
                classifyTopicCategory(name, defaultJamatCode)
            } else {
                defaultJamatCode
            }
            val encodedFile = name.split('/').joinToString("/") { seg ->
                URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
            }
            out.add(
                RemoteLibraryBook(
                    slug = "${identifier}_${name.removeSuffix(".pdf")}",
                    titleAr = "",
                    titleBn = display,
                    titleEn = display,
                    authorNameBn = "ইসলামিক ই-বুক",
                    authorNameEn = sourceLabel,
                    genre = jamatLabelForCode(categoryCode),
                    pages = 0,
                    hasPdf = true,
                    jamatCode = categoryCode,
                    pdfUrl = "https://archive.org/download/$identifier/$encodedFile",
                    coverUrl = "",
                    sourceName = sourceLabel
                )
            )
        }
        return out
    }

    /**
     * Fetches books from OpenLibrary Search API that have readable Internet Archive (`ia`) PDF editions.
     */
    suspend fun fetchOpenLibraryBooks(query: String, limit: Int = 8): List<RemoteLibraryBook> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val url = "https://openlibrary.org/search.json?q=$encoded&limit=$limit"
                val jsonStr = httpGet(url, timeoutMs = 9000) ?: return@withContext emptyList()
                val items = parseOpenLibraryDocs(jsonStr)
                coroutineScope {
                    items.map { item ->
                        async {
                            resolveSingleArchiveItemPdf(
                                identifier = item.iaIdentifier,
                                title = item.title,
                                creator = item.author,
                                fallbackCategory = RemoteLibraryBook.JAMAT_CLASSICAL_AR,
                                sourceNameOverride = "OpenLibrary API"
                            )
                        }
                    }.mapNotNull { it.await() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    internal data class OpenLibraryItem(val iaIdentifier: String, val title: String, val author: String)

    internal fun parseOpenLibraryDocs(jsonStr: String): List<OpenLibraryItem> {
        val docs = JSONObject(jsonStr).optJSONArray("docs") ?: return emptyList()
        val out = mutableListOf<OpenLibraryItem>()
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val iaArr = d.optJSONArray("ia") ?: continue
            if (iaArr.length() == 0) continue
            val iaId = iaArr.optString(0, "")
            if (iaId.isBlank()) continue
            val title = d.optString("title", iaId)
            val authorArr = d.optJSONArray("author_name")
            val author = if (authorArr != null && authorArr.length() > 0) authorArr.optString(0, "") else ""
            out.add(OpenLibraryItem(iaId, title, author))
        }
        return out
    }

    /**
     * Searches Internet Archive free books & resolves real downloadable PDF links via /metadata/{id}/files.
     */
    suspend fun searchArchiveBooks(query: String, rows: Int = 15): List<RemoteLibraryBook> =
        withContext(Dispatchers.IO) {
            try {
                val cleanQ = query.trim()
                if (cleanQ.isBlank()) return@withContext emptyList()
                val fullQ = "($cleanQ) AND (islam OR quran OR hadith OR tafsir OR seerah OR story OR golpo OR kahini OR fiqh OR bangla) AND mediatype:texts"
                val encoded = URLEncoder.encode(fullQ, "UTF-8")
                val searchUrl =
                    "https://archive.org/advancedsearch.php?q=$encoded" +
                        "&fl[]=identifier,title,creator&sort[]=downloads+desc&rows=$rows&output=json"
                val searchJson = httpGet(searchUrl, timeoutMs = 10000) ?: return@withContext emptyList()
                val docs = parseArchiveSearchDocs(searchJson)

                coroutineScope {
                    docs.map { doc ->
                        async {
                            resolveSingleArchiveItemPdf(doc.identifier, doc.title, doc.creator)
                        }
                    }.mapNotNull { it.await() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    internal data class ArchiveSearchDoc(val identifier: String, val title: String, val creator: String)

    internal fun parseArchiveSearchDocs(jsonStr: String): List<ArchiveSearchDoc> {
        val root = JSONObject(jsonStr)
        val docs = root.optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        val list = mutableListOf<ArchiveSearchDoc>()
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val id = d.optString("identifier", "")
            if (id.isBlank() || id == "kawmi" || id == "IslamiBoi" || id == IA_AZHARMEA_ID) continue
            val title = d.optString("title", id)
            val creator = d.optString("creator", "")
            list.add(ArchiveSearchDoc(id, title, creator))
        }
        return list
    }

    private fun resolveSingleArchiveItemPdf(
        identifier: String,
        title: String,
        creator: String,
        fallbackCategory: String? = null,
        sourceNameOverride: String = "Internet Archive"
    ): RemoteLibraryBook? {
        return try {
            val metaJson = httpGet("https://archive.org/metadata/$identifier/files", timeoutMs = 7000)
                ?: return null
            val files = JSONObject(metaJson).optJSONArray("result") ?: return null
            var bestPdf: String? = null
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val name = f.optString("name", "")
                if (name.lowercase(Locale.ROOT).endsWith(".pdf") && !name.endsWith("_text.pdf")) {
                    bestPdf = name
                    break
                }
            }
            if (bestPdf == null) return null
            val encodedFile = bestPdf.split('/').joinToString("/") { seg ->
                URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
            }
            val jamatCode = fallbackCategory ?: classifyTopicCategory(
                "$identifier $title",
                RemoteLibraryBook.JAMAT_GENERAL_BANGLA
            )

            RemoteLibraryBook(
                slug = "ia_$identifier",
                titleAr = "",
                titleBn = title.trim(),
                titleEn = title.trim(),
                authorNameBn = creator.ifBlank { sourceNameOverride },
                authorNameEn = creator.ifBlank { sourceNameOverride },
                genre = jamatLabelForCode(jamatCode),
                pages = 0,
                hasPdf = true,
                jamatCode = jamatCode,
                pdfUrl = "https://archive.org/download/$identifier/$encodedFile",
                coverUrl = "https://archive.org/services/img/$identifier",
                sourceName = sourceNameOverride
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches classical-Islamic library books from api.islamic.app.
     */
    suspend fun fetchBooks(genre: String? = null, limit: Int = 30, offset: Int = 0): List<RemoteLibraryBook> =
        withContext(Dispatchers.IO) {
            try {
                var url = "$ISLAMIC_APP_BASE/books?limit=$limit&offset=$offset"
                if (!genre.isNullOrBlank()) url += "&genre=$genre"

                val jsonStr = httpGet(url, timeoutMs = 8000) ?: return@withContext emptyList()
                parseIslamicAppBooksJson(jsonStr)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    internal fun parseIslamicAppBooksJson(jsonStr: String): List<RemoteLibraryBook> {
        val root = JSONObject(jsonStr)
        val payload = root.optJSONObject("data") ?: root
        val booksArray: JSONArray = payload.optJSONArray("books") ?: JSONArray()

        val result = mutableListOf<RemoteLibraryBook>()
        for (i in 0 until booksArray.length()) {
            val b = booksArray.getJSONObject(i)
            if (!b.optBoolean("has_pdf", false)) continue

            val slug = b.optString("slug", "")
            val titleI18n = b.optJSONObject("title_i18n")
            val titleBn = titleI18n?.optString("bn", "") ?: ""
            val titleAr = b.optString("title_ar", "")
            val titleEn = b.optString("title_en", titleAr)

            result.add(
                RemoteLibraryBook(
                    slug = slug,
                    titleAr = titleAr,
                    titleBn = titleBn.ifBlank { titleEn.ifBlank { titleAr } },
                    titleEn = titleEn,
                    authorNameBn = "",
                    authorNameEn = b.optString("author_name_en", b.optString("author_name_ar", "")),
                    genre = b.optString("genre", "ক্লাসিক্যাল কিতাব"),
                    pages = b.optInt("pages", 0),
                    hasPdf = true,
                    jamatCode = RemoteLibraryBook.JAMAT_CLASSICAL_AR,
                    pdfUrl = "$ISLAMIC_APP_BASE/books/$slug/file?format=pdf",
                    coverUrl = "",
                    sourceName = "api.islamic.app"
                )
            )
        }
        return result
    }

    /**
     * Downloads a book's PDF either to persistent offline storage (`toCacheOnly = false`)
     * or to temporary online reader cache (`toCacheOnly = true`).
     */
    suspend fun downloadBookPdf(
        context: Context,
        book: RemoteLibraryBook,
        toCacheOnly: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        try {
            val targetFile = if (toCacheOnly) book.cacheFile(context) else book.localFile(context)
            if (targetFile.exists() && targetFile.length() > 1024L) {
                if (!toCacheOnly) LibraryPrefsStore(context).saveBookMetadata(book)
                return@withContext targetFile
            }

            val conn = (URL(book.resolvedPdfUrl()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 45000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "IslamicAlarm-App/1.0")
                requestMethod = "GET"
            }

            if (conn.responseCode !in 200..299) return@withContext null

            val tempFile = File(targetFile.parentFile, "${book.safeFileKey()}.pdf.tmp")
            conn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.length() <= 0L) {
                tempFile.delete()
                return@withContext null
            }

            if (targetFile.exists()) targetFile.delete()
            if (tempFile.renameTo(targetFile)) {
                if (!toCacheOnly) {
                    LibraryPrefsStore(context).saveBookMetadata(book)
                }
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    internal fun classifyTopicCategory(rawText: String, fallback: String): String {
        val lower = rawText.lowercase(Locale.ROOT)
        return when {
            lower.contains("golpo") || lower.contains("kahini") || lower.contains("story") ||
                lower.contains("stories") || lower.contains("hekayat") || lower.contains("hekaete") ||
                lower.contains("qasas") || lower.contains("kissa") || lower.contains("tale") ||
                lower.contains("গল্প") || lower.contains("কাহিনি") ->
                RemoteLibraryBook.CAT_STORIES

            lower.contains("sahaba") || lower.contains("jibon") || lower.contains("jiboni") ||
                lower.contains("ashabe") || lower.contains("history") || lower.contains("itihas") ||
                lower.contains("tarikh") || lower.contains("khulafa") || lower.contains("abu bakr") ||
                lower.contains("umar") || lower.contains("aisha") || lower.contains("fatima") ||
                lower.contains("জীবনী") || lower.contains("ইতিহাস") ->
                RemoteLibraryBook.CAT_SAHABA_HISTORY

            lower.contains("sirat") || lower.contains("seerat") || lower.contains("sira") ||
                lower.contains("arrahik") || lower.contains("raheeq") || lower.contains("makhtum") ||
                lower.contains("nabawiyya") || lower.contains("nabijeer") || lower.contains("tafsir") ||
                lower.contains("jalalain") || lower.contains("mareful") || lower.contains("ibn kathir") ||
                lower.contains("miraj") || lower.contains("সিরাত") || lower.contains("তাফসীর") ->
                RemoteLibraryBook.CAT_SEERAH_TAFSIR

            else -> fallback
        }
    }

    internal fun mapKawmiFilenameToJamat(filename: String): String {
        val upperPrefix = filename.substringBefore('-', "").uppercase(Locale.ROOT)
        val lowerName = filename.lowercase(Locale.ROOT)
        return when (upperPrefix) {
            "D" -> RemoteLibraryBook.JAMAT_DAWRA
            "MT" -> RemoteLibraryBook.JAMAT_MISHKAT
            "J" -> RemoteLibraryBook.JAMAT_JALALAIN
            "S" -> if (lowerName.contains("shorhe-jami") || lowerName.contains("makamate") || lowerName.contains("takrib")) {
                RemoteLibraryBook.JAMAT_SHARHE_JAMI
            } else {
                RemoteLibraryBook.JAMAT_SHARHE_BEKAYA
            }
            "K" -> RemoteLibraryBook.JAMAT_KAFIYA
            "H" -> RemoteLibraryBook.JAMAT_HEDAYATUNNAHU
            "N" -> RemoteLibraryBook.JAMAT_NAHBEMIR
            "MN" -> RemoteLibraryBook.JAMAT_MIZAN
            "T", "M" -> RemoteLibraryBook.JAMAT_TAISIR
            else -> RemoteLibraryBook.JAMAT_GENERAL_BANGLA
        }
    }

    internal fun formatKitabDisplayTitle(rawFilename: String): String {
        val base = rawFilename.substringAfterLast('/')
            .removeSuffix(".pdf")
            .replace("[Almodina.com]", "", ignoreCase = true)
            .replace(Regex("^(D|MT|MN|J|S|K|H|N|T|M)-"), "")
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        val key = base.lowercase(Locale.ROOT)
        for ((pattern, bangla) in KNOWN_KITAB_BANGLA_NAMES) {
            if (key.contains(pattern)) return "$bangla ($base)"
        }
        return base.ifBlank { rawFilename }
    }

    private fun jamatLabelForCode(code: String): String =
        JamatCatalog.allCategories.find { it.code == code }?.titleBn ?: "ইসলামিক কিতাব"

    private fun httpGet(url: String, timeoutMs: Int): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "IslamicAlarm-App/1.0")
            requestMethod = "GET"
        }
        return if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            null
        }
    }

    private val KNOWN_KITAB_BANGLA_NAMES = listOf(
        "beheshte jewor" to "বেহেশতী জেওর",
        "beheshte gahour" to "বেহেশতী গাওহার",
        "taleemul islam" to "তালীমুল ইসলাম",
        "karima" to "কারীমা",
        "jamalul quran" to "জামালুল কুরআন",
        "hekaete latif" to "হেকায়াতে লতীফ",
        "sat juboker golpo" to "সাত যুবকের গল্প",
        "hayatus sahabah" to "হায়াতুস সাহাবা",
        "ashabe rasuler jibonkotha" to "আসহাবে রাসূলের জীবনকথা",
        "nuzhatul kari" to "নুযহাতুল কারী",
        "urdo ke dosore" to "উর্দু কি দুছরী কিতাব",
        "befaq srf" to "সরফ কিতাব (মাদরাসা)",
        "ilmus sorof" to "ইলমুস সরফ",
        "ilmus sigah" to "ইলমুস সীগাহ",
        "hedaetun naho" to "হেদায়াতুন্নাহু",
        "nurul izah" to "নূরুল ইযাহ",
        "kafia" to "কাফিয়া",
        "kuduri" to "মুখতাসারুল কুদূরী",
        "shorhe bekaya" to "শরহে বেকায়া",
        "shorhe jami" to "শরহে জামি",
        "makamate hariri" to "মাকামাতে হারীরী",
        "hidayah" to "আল-হিদায়া",
        "mishkat" to "মেশকাতুল মাসাবীহ",
        "akidatut tahabi" to "আকীদাতুত তাহাবী",
        "bukhari" to "সহিহ বুখারী শরীফ",
        "muslim" to "সহিহ মুসলিম শরীফ",
        "tirmiji" to "জামে তিরমিযী শরীফ",
        "abi daud" to "সুনানে আবু দাউদ",
        "nasai" to "সুনানে নাসাঈ",
        "ibne maja" to "সুনানে ইবনে মাজাহ",
        "arrahikulmakhtum" to "আর-রাহীকুল মাখতূম",
        "adabulmufrad" to "আল-আদাবুল মুফরাদ",
        "albidaya" to "আল-বিদায়া ওয়ান নিহায়া",
        "1000 sunan" to "১০০০ সুন্নাত",
        "the story of adam" to "হযরত আদম (আ.)-এর কাহিনি",
        "the story of nuh" to "হযরত নূহ (আ.)-এর কাহিনি",
        "the story of ibrahim" to "হযরত ইবরাহীম (আ.)-এর কাহিনি",
        "the story of musa" to "হযরত মূসা (আ.)-এর কাহিনি",
        "the story of eesa" to "হযরত ঈসা (আ.)-এর কাহিনি"
    )
}
