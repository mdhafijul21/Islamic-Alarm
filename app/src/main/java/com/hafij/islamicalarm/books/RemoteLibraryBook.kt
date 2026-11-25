package com.hafij.islamicalarm.books

import android.content.Context
import java.io.File

/**
 * Unified library book model backed by real free public repositories:
 *  - Internet Archive Bangla Islamic Archive (azharmea 560+ books, IslamiBoi)
 *  - Internet Archive Islamic Stories, Sahaba & Seerah collections (AboutHistoryStoryInIslam, StoriesOfTheProphetsForChildren)
 *  - Internet Archive Qawmi Madrasah collection (archive.org/metadata/kawmi)
 *  - Internet Archive AdvancedSearch & OpenLibrary API (openlibrary.org/search.json)
 *  - Islamic.app public classical library API (api.islamic.app)
 */
data class RemoteLibraryBook(
    val slug: String,
    val titleAr: String,
    val titleBn: String,
    val titleEn: String,
    val authorNameBn: String,
    val authorNameEn: String,
    val genre: String,
    val pages: Int,
    val hasPdf: Boolean,
    val jamatCode: String = JAMAT_GENERAL_BANGLA,
    val pdfUrl: String = "",
    val coverUrl: String = "",
    val sourceName: String = "Internet Archive"
) {
    fun resolvedPdfUrl(): String {
        if (pdfUrl.isNotBlank()) return pdfUrl
        return "https://api.islamic.app/v1/library/books/$slug/file?format=pdf"
    }

    fun safeFileKey(): String =
        slug.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(90)

    /** Permanent offline file on user's Download shelf. */
    fun localFile(context: Context): File {
        val dir = File(context.filesDir, "library_pdfs")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${safeFileKey()}.pdf")
    }

    /** Temporary cache file used when user chooses "পড়ুন (অনলাইন)" without saving offline. */
    fun cacheFile(context: Context): File {
        val dir = File(context.cacheDir, "library_online_cache")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${safeFileKey()}.pdf")
    }

    fun isDownloaded(context: Context): Boolean {
        val f = localFile(context)
        return f.exists() && f.length() > 0
    }

    fun deleteLocalFile(context: Context): Boolean {
        val f = localFile(context)
        return if (f.exists()) f.delete() else false
    }

    companion object {
        const val CAT_STORIES = "STORIES"
        const val CAT_SAHABA_HISTORY = "HISTORY"
        const val CAT_SEERAH_TAFSIR = "SEERAH_TAFSIR"

        const val JAMAT_DAWRA = "D"
        const val JAMAT_MISHKAT = "MT"
        const val JAMAT_JALALAIN = "J"
        const val JAMAT_SHARHE_BEKAYA = "S"
        const val JAMAT_SHARHE_JAMI = "SJ"
        const val JAMAT_KAFIYA = "K"
        const val JAMAT_HEDAYATUNNAHU = "H"
        const val JAMAT_NAHBEMIR = "N"
        const val JAMAT_MIZAN = "MN"
        const val JAMAT_TAISIR = "T"
        const val JAMAT_MAKTAB = "M"
        const val JAMAT_GENERAL_BANGLA = "BANGLA"
        const val JAMAT_CLASSICAL_AR = "ISLAMIC_APP"
    }
}
