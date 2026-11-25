package com.hafij.islamicalarm.hadith

data class HadithApiTarget(
    val collectionCode: String,
    val bookNameBn: String,
    val hadithNumber: Int,
    val topicBn: String,
    val gradeBn: String = "সহিহ"
)

data class DailyHadith(
    val id: String,
    val bookNameBn: String,
    val hadithNumberBn: String,
    val gradeBn: String,
    val topicBn: String,
    val arabicText: String,
    val bengaliText: String,
    val referenceBn: String,
    val isFromOnlineApi: Boolean,
    val dateKey: String
)
