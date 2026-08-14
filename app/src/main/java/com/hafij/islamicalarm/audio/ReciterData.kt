package com.hafij.islamicalarm.audio

data class Reciter(
    val id: String,
    val nameBn: String,
    val nameAr: String,
    val style: String,
    val baseUrl: String,
    val fallbackBaseUrl: String
) {
    fun getSurahAudioUrl(surahId: Int): String {
        val paddedId = String.format(java.util.Locale.US, "%03d", surahId)
        return "$baseUrl/$paddedId.mp3"
    }

    fun getSurahFallbackUrl(surahId: Int): String {
        val paddedId = String.format(java.util.Locale.US, "%03d", surahId)
        return if (fallbackBaseUrl.isNotBlank()) "$fallbackBaseUrl/$paddedId.mp3" else getSurahAudioUrl(surahId)
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
            fallbackBaseUrl = "https://download.quranicaudio.com/quran/mishaari_raashid_al_3afaaseee"
        ),
        Reciter(
            id = "sudais",
            nameBn = "শায়খ আব্দুর রহমান আস-সুদাইস",
            nameAr = "عبد الرحمن السديس",
            style = "মসজিদুল হারাম, মক্কা",
            baseUrl = "https://server11.mp3quran.net/sds",
            fallbackBaseUrl = "https://download.quranicaudio.com/quran/abdurrahmaan_as-sudays"
        ),
        Reciter(
            id = "muaiqly",
            nameBn = "শায়খ মাহের আল-মুয়াইকলি",
            nameAr = "ماهر المعيقلي",
            style = "মসজিদুল হারাম, মক্কা",
            baseUrl = "https://server12.mp3quran.net/maher",
            fallbackBaseUrl = "https://download.quranicaudio.com/quran/maher_almu3aiqly/year1431"
        ),
        Reciter(
            id = "ghamdi",
            nameBn = "শায়খ সা'দ আল-গামদি",
            nameAr = "سعد الغامدي",
            style = "মুরাত্তাল",
            baseUrl = "https://server7.mp3quran.net/s_gmd",
            fallbackBaseUrl = "https://download.quranicaudio.com/quran/sa3d_al-ghaamidee"
        ),
        Reciter(
            id = "basit",
            nameBn = "ক্বারী আব্দুল বাসিত আব্দুস সামাদ",
            nameAr = "عبد الباسط عبد الصمد",
            style = "মুরাত্তাল",
            baseUrl = "https://server7.mp3quran.net/basit",
            fallbackBaseUrl = "https://download.quranicaudio.com/quran/abdulbaset_mujawwad"
        )
    )

    fun getDefaultReciter(): Reciter = reciters[0]
}
