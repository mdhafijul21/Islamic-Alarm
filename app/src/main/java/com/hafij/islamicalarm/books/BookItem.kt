package com.hafij.islamicalarm.books

data class BookItem(
    val id: String,
    val titleBn: String,
    val titleAr: String,
    val authorBn: String,
    val category: String,
    val summaryBn: String,
    val totalChapters: Int,
    val isOfflineAvailable: Boolean = true,
    val chapters: List<BookChapter> = emptyList(),
    val onlineSearchQuery: String = ""
)
