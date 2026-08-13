package com.hafij.islamicalarm.quran

data class Surah(
    val id: Int,
    val nameArabic: String,
    val nameEnglish: String,
    val nameBangla: String,
    val meaningBangla: String,
    val totalAyahs: Int,
    val revelationType: String, // "মক্কী" or "মাদানী"
    val paraNumber: Int,
    val startPage: Int
) {
    val audioUrl: String
        get() = "https://download.quranicaudio.com/quran/mishaari_raashid_al_3afaaseee/${String.format(java.util.Locale.US, "%03d", id)}.mp3"
}

data class Para(
    val id: Int,
    val nameArabic: String,
    val nameBangla: String,
    val meaningBangla: String,
    val startPage: Int
)

data class AyahItem(
    val surahNumber: Int,
    val numberInSurah: Int,
    val textArabic: String,
    val textBangla: String,
    val page: Int,
    val juz: Int
)

data class QuranPageData(
    val pageNumber: Int,
    val juzNumber: Int,
    val surahName: String,
    val lines: List<String>
)
