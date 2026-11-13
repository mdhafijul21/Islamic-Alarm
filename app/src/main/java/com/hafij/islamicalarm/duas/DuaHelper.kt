package com.hafij.islamicalarm.duas

/**
 * নতুন ডেটা ফাইলগুলোর (DuaFixes, DuaMore1, DuaMore2, DuaMore3) জন্য সাধারণ হেল্পার।
 * DuaItem-এর কনস্ট্রাক্টর ক্রম অপরিবর্তিত।
 */
internal fun entry(
    id: Int, title: String, category: String, arabic: String = "",
    transliteration: String = "", translation: String, reference: String,
    whenToRead: String = "", repeatCount: String = "", virtue: String = "",
    grade: String = "", contentType: String = "DUA", sourceUrl: String = ""
) = DuaItem(
    id, title, category, arabic, transliteration, translation, reference,
    whenToRead, repeatCount, virtue, grade, contentType, sourceUrl
)
