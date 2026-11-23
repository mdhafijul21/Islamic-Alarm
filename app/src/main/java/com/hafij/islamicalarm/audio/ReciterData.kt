package com.hafij.islamicalarm.audio

data class Reciter(
    val id: String,
    val nameBn: String,
    val nameAr: String,
    val style: String,
    val baseUrl: String,
    val fallbackBaseUrl: String,
    val cdnBaseUrl: String = ""
) {
    fun getSurahAudioUrl(surahId: Int): String {
        val paddedId = String.format(java.util.Locale.US, "%03d", surahId)
        return "$baseUrl/$paddedId.mp3"
    }

    fun getSurahFallbackUrl(surahId: Int): String {
        val paddedId = String.format(java.util.Locale.US, "%03d", surahId)
        return if (fallbackBaseUrl.isNotBlank()) "$fallbackBaseUrl/$paddedId.mp3" else getSurahAudioUrl(surahId)
    }

    fun getAllPlaybackUrls(surahId: Int): List<String> {
        val urls = LinkedHashSet<String>()
        if (cdnBaseUrl.isNotBlank()) {
            urls.add("$cdnBaseUrl/$surahId.mp3")
        }
        urls.add(getSurahAudioUrl(surahId))
        if (fallbackBaseUrl.isNotBlank()) {
            urls.add(getSurahFallbackUrl(surahId))
        }
        return urls.toList()
    }
}

object ReciterData {
    val reciters = listOf(
        Reciter(
            id = "mishary",
            nameBn = "ক্বারী মিশারী রশিদ আল-আফাসি",
            nameAr = "مشاري راشد العفاسي",
            style = "মুরাত্তাল (হাফস)",
            baseUrl = "https://server8.mp3quran.net/afs",
            fallbackBaseUrl = "https://download.quranicaudio.com/quran/mishaari_raashid_al_3afaaseee",
            cdnBaseUrl = "https://cdn.islamic.network/quran/audio-surah/128/ar.alafasy"
        ),
        Reciter(
            id = "sudais",
            nameBn = "শায়খ আব্দুর রহমান আস-সুদাইস",
            nameAr = "عبد الرحمن السديس",
            style = "মসজিদুল হারাম, মক্কা",
            baseUrl = "https://server11.mp3quran.net/sds",
            fallbackBaseUrl = "https://download.quranicaudio.com/quran/abdurrahmaan_as-sudays",
            cdnBaseUrl = "https://cdn.islamic.network/quran/audio-surah/128/ar.abdurrahmaansudais"
        ),
        Reciter(
            id = "muaiqly",
            nameBn = "শায়খ মাহের আল-মুয়াইকলি",
            nameAr = "ماهر المعيقلي",
            style = "মসজিদুল হারাম, মক্কা",
            baseUrl = "https://server12.mp3quran.net/maher",
            fallbackBaseUrl = "https://download.quranicaudio.com/quran/maher_almu3aiqly/year1431",
            cdnBaseUrl = "https://cdn.islamic.network/quran/audio-surah/128/ar.mahermuaiqly"
        ),
        Reciter(
            id = "ghamdi",
            nameBn = "শায়খ সা'দ আল-গামদি",
            nameAr = "سعد الغامدي",
            style = "মুরাত্তাল",
            baseUrl = "https://server7.mp3quran.net/s_gmd",
            fallbackBaseUrl = "https://download.quranicaudio.com/quran/sa3d_al-ghaamidee",
            cdnBaseUrl = "https://cdn.islamic.network/quran/audio-surah/128/ar.saadalghamidi"
        ),
        Reciter(
            id = "basit",
            nameBn = "ক্বারী আব্দুল বাসিত আব্দুস সামাদ",
            nameAr = "عبد الباسط عبد الصمد",
            style = "মুরাত্তাল",
            baseUrl = "https://server7.mp3quran.net/basit",
            fallbackBaseUrl = "https://download.quranicaudio.com/quran/abdulbaset_mujawwad",
            cdnBaseUrl = "https://cdn.islamic.network/quran/audio-surah/128/ar.abdulbasitmurattal"
        )
    )

    fun getDefaultReciter(): Reciter = reciters[0]
}
