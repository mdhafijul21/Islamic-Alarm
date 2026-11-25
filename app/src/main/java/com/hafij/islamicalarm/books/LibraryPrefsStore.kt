package com.hafij.islamicalarm.books

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LibraryPrefsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appCtx = context.applicationContext

    fun isBookmarked(slug: String): Boolean = getBookmarks().contains(slug)

    fun toggleBookmark(slug: String): Boolean {
        val current = getBookmarks().toMutableSet()
        val added = if (current.contains(slug)) {
            current.remove(slug)
            false
        } else {
            current.add(slug)
            true
        }
        prefs.edit().putStringSet(KEY_BOOKMARKS, current).apply()
        return added
    }

    fun getBookmarks(): Set<String> =
        prefs.getStringSet(KEY_BOOKMARKS, emptySet())?.toSet() ?: emptySet()

    fun isFavorite(slug: String): Boolean = getFavorites().contains(slug)

    fun toggleFavorite(slug: String): Boolean {
        val current = getFavorites().toMutableSet()
        val added = if (current.contains(slug)) {
            current.remove(slug)
            false
        } else {
            current.add(slug)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return added
    }

    fun getFavorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    fun saveBookMetadata(book: RemoteLibraryBook) {
        val list = loadSavedBooks().toMutableList()
        val idx = list.indexOfFirst { it.slug == book.slug }
        if (idx >= 0) list[idx] = book else list.add(0, book)
        writeBookList(KEY_SAVED_METADATA_FILE, list)
    }

    fun loadSavedBooks(): List<RemoteLibraryBook> =
        readBookList(KEY_SAVED_METADATA_FILE)

    fun saveCatalogCache(books: List<RemoteLibraryBook>) {
        if (books.isEmpty()) return
        writeBookList(KEY_CATALOG_CACHE_FILE, books)
    }

    fun loadCatalogCache(): List<RemoteLibraryBook> =
        readBookList(KEY_CATALOG_CACHE_FILE)

    private fun writeBookList(fileName: String, books: List<RemoteLibraryBook>) {
        try {
            val arr = JSONArray()
            for (b in books) {
                arr.put(
                    JSONObject().apply {
                        put("slug", b.slug)
                        put("titleAr", b.titleAr)
                        put("titleBn", b.titleBn)
                        put("titleEn", b.titleEn)
                        put("authorNameBn", b.authorNameBn)
                        put("authorNameEn", b.authorNameEn)
                        put("genre", b.genre)
                        put("pages", b.pages)
                        put("hasPdf", b.hasPdf)
                        put("jamatCode", b.jamatCode)
                        put("pdfUrl", b.pdfUrl)
                        put("coverUrl", b.coverUrl)
                        put("sourceName", b.sourceName)
                    }
                )
            }
            java.io.File(appCtx.filesDir, fileName).writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun readBookList(fileName: String): List<RemoteLibraryBook> {
        return try {
            val f = java.io.File(appCtx.filesDir, fileName)
            if (!f.exists()) return emptyList()
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            val out = ArrayList<RemoteLibraryBook>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    RemoteLibraryBook(
                        slug = o.optString("slug"),
                        titleAr = o.optString("titleAr"),
                        titleBn = o.optString("titleBn"),
                        titleEn = o.optString("titleEn"),
                        authorNameBn = o.optString("authorNameBn"),
                        authorNameEn = o.optString("authorNameEn"),
                        genre = o.optString("genre"),
                        pages = o.optInt("pages", 0),
                        hasPdf = o.optBoolean("hasPdf", true),
                        jamatCode = o.optString("jamatCode", RemoteLibraryBook.JAMAT_GENERAL_BANGLA),
                        pdfUrl = o.optString("pdfUrl"),
                        coverUrl = o.optString("coverUrl"),
                        sourceName = o.optString("sourceName", "Internet Archive")
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "IslamicLibraryPrefs"
        private const val KEY_BOOKMARKS = "library_bookmarks"
        private const val KEY_FAVORITES = "library_favorites"
        private const val KEY_SAVED_METADATA_FILE = "library_saved_books_meta.json"
        private const val KEY_CATALOG_CACHE_FILE = "library_catalog_cache.json"
    }
}
