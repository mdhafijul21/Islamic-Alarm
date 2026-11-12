package com.hafij.islamicalarm.duas

/**
 * One dua, dhikr, hadith, or practical worship guide shown in the Dua screen.
 * Extra fields have defaults so existing call sites remain source-compatible.
 */
data class DuaItem(
    val id: Int,
    val title: String,
    val category: String,
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val reference: String,
    val whenToRead: String = "",
    val repeatCount: String = "",
    val virtue: String = "",
    val grade: String = "",
    val contentType: String = "DUA",
    val sourceUrl: String = ""
)
