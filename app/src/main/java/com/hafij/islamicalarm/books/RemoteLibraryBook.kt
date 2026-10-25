package com.hafij.islamicalarm.books

/**
 * A book from the islamic.app classical-library catalog
 * (https://docs.islamic.app/api-reference/library-books).
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
    val hasPdf: Boolean
) {
    /** Where this book's PDF is (or will be) stored locally once downloaded. */
    fun localFile(context: android.content.Context): java.io.File {
        val dir = java.io.File(context.filesDir, "library_pdfs")
        if (!dir.exists()) dir.mkdirs()
        return java.io.File(dir, "$slug.pdf")
    }

    fun isDownloaded(context: android.content.Context): Boolean =
        localFile(context).exists() && localFile(context).length() > 0
}
