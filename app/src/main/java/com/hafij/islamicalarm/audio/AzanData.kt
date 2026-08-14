package com.hafij.islamicalarm.audio

data class AzanItem(
    val id: String,
    val titleBn: String,
    val titleAr: String,
    val muazzinBn: String,
    val locationBn: String,
    val durationText: String,
    val audioUrl: String,
    val isFajrSpecial: Boolean = false,
    val descriptionBn: String
)

object AzanData {

    const val PREF_SELECTED_AZAN_ID = "selected_azan_id"
    const val PREF_AZAN_SOUND_ENABLED = "azan_sound_enabled"

    val azanList = listOf(
        AzanItem(
            id = "makkah_ali_mullah",
            titleBn = "পবিত্র মসজিদুল হারাম (মক্কা মুকাররমা)",
            titleAr = "أذان المسجد الحرام بمكة المكرمة",
            muazzinBn = "শায়খ আলী আহমদ মোল্লা (প্রধান মুয়াজ্জিন)",
            locationBn = "মক্কা মুকাররমা, সৌদি আরব",
            durationText = "৩:১৫ মিনিট",
            audioUrl = "https://media.sd.ma/assabile/adhan_3748/001.mp3",
            descriptionBn = "বিশ্বখ্যাত হৃদয়স্পর্শী ও সুমধুর ঐতিহ্যবাহী ক্বাবা শরীফের আজান।"
        ),
        AzanItem(
            id = "madinah_nabawi",
            titleBn = "পবিত্র মসজিদুন নববী (মদিনা মুনাওয়ারা)",
            titleAr = "أذان المسجد النبوي بالمدينة المنورة",
            muazzinBn = "মুয়াজ্জিন মসজিদুন নববী",
            locationBn = "মদিনা মুনাওয়ারা, সৌদি আরব",
            durationText = "৩:৩০ মিনিট",
            audioUrl = "https://media.sd.ma/assabile/adhan_3748/002.mp3",
            descriptionBn = "মদিনা শরীফের স্নিগ্ধ, প্রশান্তিময় ও ভাবগাম্ভীর্যপূর্ণ আজান ধ্বনি।"
        ),
        AzanItem(
            id = "fajr_special",
            titleBn = "ফজর সালাতের বিশেষ আজান",
            titleAr = "أذان الفجر (الصلاة خير من النوم)",
            muazzinBn = "শায়খ এনামুল্লাহ আল-হুসাইনী",
            locationBn = "হারামাইন শরিফাইন",
            durationText = "৩:৪৫ মিনিট",
            audioUrl = "https://media.sd.ma/assabile/adhan_3748/004.mp3",
            isFajrSpecial = true,
            descriptionBn = "‘আস-সালাতু খাইরুম মিনান নাওম’ (ঘুমের চেয়ে নামাজ উত্তম) সমৃদ্ধ ফজরের মধুর আজান।"
        ),
        AzanItem(
            id = "alaqsa_jerusalem",
            titleBn = "পবিত্র মসজিদুল আকসা (বায়তুল মুকাদ্দাস)",
            titleAr = "أذان المسجد الأقصى المبارك",
            muazzinBn = "মুয়াজ্জিন মসজিদুল আকসা",
            locationBn = "জেরুজালেম, ফিলিস্তিন",
            durationText = "৩:১০ মিনিট",
            audioUrl = "https://media.sd.ma/assabile/adhan_3748/003.mp3",
            descriptionBn = "প্রথম কিবলা ঐতিহাসিক মসজিদুল আকসার ঐতিহ্যবাহী আবেগময় আজান।"
        ),
        AzanItem(
            id = "egypt_husary",
            titleBn = "মিশরীয় ক্লাসিক সুরিল আজান",
            titleAr = "الأذان المصري - الشيخ محمود خليل الحصري",
            muazzinBn = "শায়খ মাহমুদ খলিল আল-হুসারী",
            locationBn = "কায়রো, মিশর",
            durationText = "৩:২০ মিনিট",
            audioUrl = "https://media.sd.ma/assabile/adhan_3748/005.mp3",
            descriptionBn = "কুরআনের বিশ্ববরণ্য ক্বারী মাহমুদ খলিল আল-হুসারীর অপূর্ব সুরের আজান।"
        ),
        AzanItem(
            id = "turkey_istanbul",
            titleBn = "ইস্তাম্বুল তুর্কি মাকাম আজান",
            titleAr = "الأذان التركي - إسطنبول",
            muazzinBn = "মুয়াজ্জিন সুলতান আহমদ মসজিদ",
            locationBn = "ইস্তাম্বুল, তুরস্ক",
            durationText = "৩:১৫ মিনিট",
            audioUrl = "https://media.sd.ma/assabile/adhan_3748/001.mp3",
            descriptionBn = "উসমানী ঐতিহ্যের হিজায ও সাবা মাকামের সুললিত আজান।"
        )
    )

    val azanPhrases = listOf(
        Pair("اللهُ أَكْبَرُ ، اللهُ أَكْبَرُ (২ বার)", "আল্লাহ সর্বশ্রেষ্ঠ, আল্লাহ সর্বশ্রেষ্ঠ"),
        Pair("أَشْهَدُ أَنْ لَا إِلَهَ إِلَّا اللهُ (২ বার)", "আমি সাক্ষ্য দিচ্ছি আল্লাহ ছাড়া কোনো সত্য মাবুদ নেই"),
        Pair("أَشْهَدُ أَنَّ مُحَمَّدًا رَسُولُ اللهِ (২ বার)", "আমি সাক্ষ্য দিচ্ছি মুহাম্মদ ﷺ আল্লাহর রাসূল"),
        Pair("حَيَّ عَلَى الصَّلَاةِ (২ বার)", "নামাজের দিকে এসো (উত্তর: লা হাওলা ওয়ালা কুওয়াতা ইল্লা বিল্লাহ)"),
        Pair("حَيَّ عَلَى الْفَلَاحِ (২ বার)", "কল্যাণ ও সাফল্যের দিকে এসো (উত্তর: লা হাওলা ওয়ালা কুওয়াতা ইল্লা বিল্লাহ)"),
        Pair("الصَّلَاةُ خَيْرٌ مِنَ النَّوْمِ (ফজরে ২ বার)", "ঘুমের চেয়ে সালাত উত্তম (উত্তর: সাদাক্বতা ওয়া বারারতা)"),
        Pair("اللهُ أَكْبَرُ ، اللهُ أَكْبَرُ", "আল্লাহ সর্বশ্রেষ্ঠ, আল্লাহ সর্বশ্রেষ্ঠ"),
        Pair("لَا إِلَهَ إِلَّا اللهُ", "আল্লাহ ছাড়া কোনো সত্য উপাস্য নেই")
    )

    const val AZAN_DUA_ARABIC = "اللَّهُمَّ رَبَّ هَذِهِ الدَّعْوَةِ التَّامَّةِ، وَالصَّلَاةِ الْقَائِمَةِ، آتِ مُحَمَّدًا الْوَسِيلَةَ وَالْفَضِيلَةَ، وَابْعَثْهُ مَقَامًا مَحْمُودًا الَّذِي وَعَدْتَهُ"
    const val AZAN_DUA_BANGLA_PRONUNCIATION = "আল্লাহুম্মা রব্বা হাযিহিদ দাওয়াতিত্ তাম্মাহ্, ওয়াস সালাতিল ক্বায়িমাহ্, আতি মুহাম্মাদানিল ওয়াসীলাতা ওয়াল ফাদ্বীলাহ্, ওয়াব‘আছহু মাক্বামাম মাহমূদানিল্লাযী ওয়া‘আদতাহ্।"
    const val AZAN_DUA_BANGLA_MEANING = "হে আল্লাহ! এই পরিপূর্ণ আহ্বান ও প্রতিষ্ঠিত সালাতের মহান প্রতিপালক! আমাদের নবী হযরত মুহাম্মদ ﷺ-কে দান করুন ‘ওয়াসীলা’ (জান্নাতের সর্বোচ্চ স্থান) ও পরম মর্যাদা এবং তাঁকে পৌঁছিয়ে দিন সেই প্রশংসিত স্থানে (‘মাকামে মাহমুদ’), যার ওয়াদা আপনি তাঁকে দিয়েছেন।"
    const val AZAN_DUA_FAZILAT = "সহিহ বুখারী: ৬১৪ (রাসূলুল্লাহ ﷺ বলেছেন: যে ব্যক্তি আজান শুনে এই দোয়া পাঠ করবে, কিয়ামতের দিন তার জন্য আমার শাফাআত অবধারিত হয়ে যাবে)।"
}
