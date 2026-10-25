package com.hafij.islamicalarm.books

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the free, keyless islamic.app public API
 * (https://docs.islamic.app) to list and download books from its
 * classical-Islamic library (out-of-copyright works, full PDFs).
 */
object LibraryApiClient {

    private const val BASE_URL = "https://api.islamic.app/v1/library"

    /**
     * Fetches a page of the published book catalog, optionally filtered by genre.
     * Mirrors GET /v1/library/books.
     */
    suspend fun fetchBooks(genre: String? = null, limit: Int = 30, offset: Int = 0): List<RemoteLibraryBook> =
        withContext(Dispatchers.IO) {
            try {
                var url = "$BASE_URL/books?limit=$limit&offset=$offset"
                if (!genre.isNullOrBlank()) url += "&genre=$genre"

                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "IslamicAlarm-App/1.0")
                conn.requestMethod = "GET"

                if (conn.responseCode != 200) return@withContext emptyList()

                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                // Responses are wrapped as {code, status, data: {...}} since 2026-06-12;
                // fall back to the bare shape if "data" isn't present (older/legacy URL).
                val payload = root.optJSONObject("data") ?: root
                val booksArray: JSONArray = payload.optJSONArray("books") ?: JSONArray()

                val result = mutableListOf<RemoteLibraryBook>()
                for (i in 0 until booksArray.length()) {
                    val b = booksArray.getJSONObject(i)
                    if (!b.optBoolean("has_pdf", false)) continue

                    val titleI18n = b.optJSONObject("title_i18n")
                    val titleBn = titleI18n?.optString("bn", "") ?: ""
                    val titleAr = b.optString("title_ar", "")
                    val titleEn = b.optString("title_en", titleAr)

                    result.add(
                        RemoteLibraryBook(
                            slug = b.optString("slug", ""),
                            titleAr = titleAr,
                            titleBn = titleBn.ifBlank { titleEn.ifBlank { titleAr } },
                            titleEn = titleEn,
                            authorNameBn = "", // not provided per-locale by this endpoint
                            authorNameEn = b.optString("author_name_en", b.optString("author_name_ar", "")),
                            genre = b.optString("genre", ""),
                            pages = b.optInt("pages", 0),
                            hasPdf = true
                        )
                    )
                }
                result
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    /**
     * Downloads a book's PDF (GET /v1/library/books/{slug}/file?format=pdf) into the
     * app's private storage so it can be opened offline afterwards.
     * Returns true on success.
     */
    suspend fun downloadBookPdf(context: Context, book: RemoteLibraryBook): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/books/${book.slug}/file?format=pdf"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "IslamicAlarm-App/1.0")
                conn.requestMethod = "GET"

                if (conn.responseCode != 200) return@withContext false

                val targetFile = book.localFile(context)
                val tempFile = java.io.File(targetFile.parentFile, "${book.slug}.pdf.tmp")

                conn.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Only replace the real file once the download fully completed, so a
                // failed/partial download never leaves a broken PDF the reader can open.
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
}
