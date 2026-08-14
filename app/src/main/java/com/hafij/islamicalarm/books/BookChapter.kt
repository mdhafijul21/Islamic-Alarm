package com.hafij.islamicalarm.books

data class BookChapter(
    val chapterNo: Int,
    val titleBn: String,
    val titleAr: String = "",
    val contentBn: String,
    val arabicText: String = "",
    val explanationBn: String = "",
    val reference: String = ""
)
