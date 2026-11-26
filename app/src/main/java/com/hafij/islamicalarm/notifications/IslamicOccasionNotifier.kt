package com.hafij.islamicalarm.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.prayertimes.HijriCalendarHelper
import com.hafij.islamicalarm.prayertimes.PrayerTimeCalculator
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class IslamicOccasion(
    val id: String,
    val titleBn: String,
    val occasionBadgeBn: String,
    val fazilatBn: String,
    val hadithArabic: String,
    val hadithBangla: String,
    val hadithReference: String,
    val recommendedAmalBn: String
)

data class OccasionNotificationRecord(
    val recordId: String,
    val occasionId: String,
    val titleBn: String,
    val occasionBadgeBn: String,
    val hijriDateBn: String,
    val fazilatBn: String,
    val hadithArabic: String,
    val hadithBangla: String,
    val hadithReference: String,
    val recommendedAmalBn: String,
    val createdAtMillis: Long,
    val isRead: Boolean
)

object IslamicOccasionNotifier {

    const val CHANNEL_ID = "islamic_occasions_channel"
    const val ACTION_CHECK_DAILY_OCCASION = "com.hafij.islamicalarm.ACTION_CHECK_DAILY_OCCASION"
    private const val PREFS_INBOX = "islamic_occasion_inbox_prefs"
    private const val KEY_RECORDS_JSON = "records_json"
    private const val KEY_NOTIFIED_DATE_KEYS = "notified_date_keys"
    private const val MAX_STORED_RECORDS = 40

    val SPECIAL_OCCASIONS: List<IslamicOccasion> = listOf(
        IslamicOccasion(
            id = "jumuah",
            titleBn = "আজ পবিত্র জুমার দিন",
            occasionBadgeBn = "সাপ্তাহিক ঈদ • প্রতি শুক্রবার",
            fazilatBn = "সপ্তাহের শ্রেষ্ঠ দিন জুমার দিন। এ দিনে গোসল করে আগেভাগে মসজিদে যাওয়া, সূরা কাহফ তিলাওয়াত, বেশি বেশি দরুদ পাঠ এবং আসর থেকে মাগরিব পর্যন্ত দোয়া কবুলের বিশেষ মুহূর্ত অন্বেষণের অশেষ ফজিলত রয়েছে।",
            hadithArabic = "خَيْرُ يَوْمٍ طَلَعَتْ عَلَيْهِ الشَّمْسُ يَوْمُ الْجُمُعَةِ، فِيهِ خُلِقَ آدَمُ، وَفِيهِ أُدْخِلَ الْجَنَّةَ، وَفِيهِ أُخْرِجَ مِنْهَا",
            hadithBangla = "রাসুলুল্লাহ ﷺ বলেছেন: সূর্য উদিত হয় এমন দিনগুলোর মধ্যে সর্বোত্তম দিন হলো জুমার দিন। এ দিনেই আদম (আ.)-কে সৃষ্টি করা হয়েছে, এ দিনেই তাঁকে জান্নাতে প্রবেশ করানো হয়েছে এবং এ দিনেই তাঁকে তা থেকে বের করা হয়েছে।",
            hadithReference = "সহিহ মুসলিম ৮৫৪; সূরা কাহফ: মুস্তাদরাক হাকিম ২/৩৯৯; দরুদ: আবু দাউদ ১০৪৭",
            recommendedAmalBn = "সূরা কাহফ পাঠ করুন, নবী ﷺ-এর ওপর বেশি দরুদ পড়ুন, গোসল ও পরিষ্কার পোশাকে আগেভাগে জুমার সালাতে উপস্থিত হোন এবং দোয়া করুন।"
        ),
        IslamicOccasion(
            id = "ayyam_al_beed",
            titleBn = "আজ আইয়ামে বীজ (নফল রোজার দিন)",
            occasionBadgeBn = "প্রতি হিজরি ১৩, ১৪ ও ১৫ তারিখ",
            fazilatBn = "প্রতি চান্দ্র মাসের ১৩, ১৪ ও ১৫ তারিখকে আইয়ামে বীজ বলা হয়। এ তিন দিন নফল রোজা রাখলে সারা বছর রোজা রাখার সমান সওয়াব পাওয়া যায় (কারণ প্রতিটি নেক কাজের সওয়াব দশগুণ)।",
            hadithArabic = "يَا أَبَا ذَرٍّ إِذَا صُمْتَ مِنَ الشَّهْرِ ثَلَاثَةَ أَيَّامٍ فَصُمْ ثَلَاثَ عَشْرَةَ وَأَرْبَعَ عَشْرَةَ وَخَمْسَ عَشْرَةَ",
            hadithBangla = "নবী ﷺ আবু জর (রা.)-কে বলেন: তুমি যদি প্রতি মাসে তিন দিন রোজা রাখতে চাও, তবে (চান্দ্র মাসের) ১৩, ১৪ ও ১৫ তারিখে রোজা রাখো।",
            hadithReference = "জামে তিরমিযি ৭৬১; সুনান নাসাঈ ২৪২৪; সহিহ বুখারি ১৯৭৯; সহিহ মুসলিম ১১৫৯",
            recommendedAmalBn = "আজ নফল রোজা রাখার নিয়ত করুন (সাহরির সময় পার না হয়ে থাকলে) এবং বেশি বেশি ইস্তিগফার ও তিলাওয়াত করুন।"
        ),
        IslamicOccasion(
            id = "shab_e_barat",
            titleBn = "পবিত্র শবে বরাত (নিসফে শাবান)",
            occasionBadgeBn = "১৪ শাবান সন্ধ্যা ও ১৫ শাবান",
            fazilatBn = "শাবান মাসের মধ্যরাত আল্লাহ তাআলার পক্ষ থেকে ক্ষমা ও রহমত লাভের বিশেষ উপলক্ষ। শিরক ও হিংসা-বিদ্বেষমুক্ত অন্তর নিয়ে তাওবা-ইস্তিগফার, নফল ইবাদত এবং ১৫ শাবানসহ শাবান মাসে নফল রোজা রাখার ফজিলত বর্ণিত রয়েছে।",
            hadithArabic = "يَطَّلِعُ اللَّهُ إِلَى خَلْقِهِ فِي لَيْلَةِ النِّصْفِ مِنْ شَعْبَانَ فَيَغْفِرُ لِجَمِيعِ خَلْقِهِ إِلَّا لِمُشْرِكٍ أَوْ مُشَاحِنٍ",
            hadithBangla = "রাসুলুল্লাহ ﷺ বলেছেন: আল্লাহ তাআলা শাবান মাসের মধ্যরাতে তাঁর সৃষ্টির প্রতি বিশেষ দৃষ্টি দেন এবং মুশরিক ও বিদ্বেষপোষণকারী ছাড়া সকল সৃষ্টিকে ক্ষমা করে দেন।",
            hadithReference = "সুনান ইবনে মাজাহ ১৩৯০; সহিহ ইবনে হিব্বান ৫৬৬৫; শাবানের রোজা: সহিহ মুসলিম ১১৫৬",
            recommendedAmalBn = "হিংসা-বিদ্বেষ ছেড়ে মানুষের সাথে সম্পর্ক ঠিক করুন, খাঁটি তাওবা ও ইস্তিগফার করুন এবং নফল সালাত ও রোজা পালন করুন।"
        ),
        IslamicOccasion(
            id = "shab_e_qadr",
            titleBn = "পবিত্র শবে কদর (লাইলাতুল কদর)",
            occasionBadgeBn = "রমজানের শেষ দশকের বিজোড় রাত (২১, ২৩, ২৫, ২৭ ও ২৯)",
            fazilatBn = "লাইলাতুল কদর এক হাজার মাসের (প্রায় ৮৩ বছর ৪ মাসের) ইবাদতের চেয়েও উত্তম। এ রাতে কুরআন নাজিল হয়েছে এবং ফজর পর্যন্ত শান্তি বর্ষিত হয়। ঈমান ও সওয়াবের আশায় এ রাতে ইবাদত করলে পূর্বের সগিরা গুনাহ মাফ হয়।",
            hadithArabic = "مَنْ قَامَ لَيْلَةَ الْقَدْرِ إِيمَانًا وَاحْتِسَابًا غُفِرَ لَهُ مَا تَقَدَّمَ مِنْ ذَنْبِهِ",
            hadithBangla = "রাসুলুল্লাহ ﷺ বলেছেন: যে ব্যক্তি ঈমান ও সওয়াবের আশায় লাইলাতুল কদরে (সালাত ও ইবাদতে) দাঁড়াবে, তার পূর্ববর্তী গুনাহসমূহ ক্ষমা করে দেওয়া হবে।",
            hadithReference = "কুরআন ৯৭:১–৫; সহিহ বুখারি ২০১৪, ২০১৭; দোয়া: জামে তিরমিযি ৩৫১৩",
            recommendedAmalBn = "রাতে বেশি করে পড়ুন: «اللَّهُمَّ إِنَّكَ عَفُوٌّ تُحِبُّ الْعَفْوَ فَاعْفُ عَنِّي» এবং কুরআন তিলাওয়াত ও নফল সালাতে মশগুল থাকুন।"
        ),
        IslamicOccasion(
            id = "eid_ul_fitr",
            titleBn = "ঈদ মোবারক! পবিত্র ঈদুল ফিতর",
            occasionBadgeBn = "১ শাওয়াল",
            fazilatBn = "দীর্ঘ এক মাস সিয়াম সাধনার পর আল্লাহর নিয়ামত ও ক্ষমার কৃতজ্ঞতা স্বরূপ পবিত্র ঈদুল ফিতর। ঈদের নামাজের আগে সদকাতুল ফিতর আদায় করা, গোসল করে উত্তম পোশাক পরা, বিজোড় সংখ্যক খেজুর খেয়ে ঈদগাহে যাওয়া এবং তাকবির পাঠ সুন্নাত।",
            hadithArabic = "أُمِرَ النَّاسُ أَنْ يُخْرِجُوا زَكَاةَ الْفِطْرِ قَبْلَ خُرُوجِ النَّاسِ إِلَى الصَّلَاةِ",
            hadithBangla = "রাসুলুল্লাহ ﷺ মানুষের ঈদের সালাতে বের হওয়ার পূর্বেই সদকাতুল ফিতর আদায় করার নির্দেশ দিয়েছেন (যাতে অভাবীরাও ঈদের আনন্দে শরিক হতে পারে)।",
            hadithReference = "সহিহ বুখারি ১৫০৩, ৯৫৬; সহিহ মুসলিম ৯৮৪",
            recommendedAmalBn = "ঈদের সালাতের আগে সদকাতুল ফিতর আদায় করুন, তাকবির বলুন এবং আত্মীয়-স্বজন ও দরিদ্রদের সাথে আনন্দ ভাগ করুন।"
        ),
        IslamicOccasion(
            id = "eid_ul_azha",
            titleBn = "ঈদ মোবারক! পবিত্র ঈদুল আজহা",
            occasionBadgeBn = "১০ জিলহজ",
            fazilatBn = "ইবরাহিম (আ.)-এর মহান আত্মত্যাগের স্মৃতিবিজড়িত পবিত্র ঈদুল আজহা। সামর্থ্যবানদের জন্য আল্লাহর সন্তুষ্টির উদ্দেশ্যে কুরবানি করা, তাশরিকের তাকবির পাঠ এবং গরিব-দুঃখী ও আত্মীয়দের মাঝে গোশত বণ্টন করা এ দিনের প্রধান ইবাদত।",
            hadithArabic = "إِنَّ أَوَّلَ مَا نَبْدَأُ بِهِ فِي يَوْمِنَا هَذَا أَنْ نُصَلِّيَ، ثُمَّ نَرْجِعَ فَنَنْحَرَ",
            hadithBangla = "রাসুলুল্লাহ ﷺ বলেছেন: আজকের দিনে আমাদের প্রথম কাজ হলো ঈদের সালাত আদায় করা, তারপর ফিরে এসে কুরবানি করা। যে এমন করবে সে আমাদের সুন্নাত অনুযায়ী কাজ করল।",
            hadithReference = "সহিহ বুখারি ৯৫৫, ৯৫১; সহিহ মুসলিম ১৯৬১",
            recommendedAmalBn = "না খেয়ে ঈদের সালাত আদায় করে ফিরে কুরবানি করুন এবং ১৩ জিলহজ আসর পর্যন্ত প্রতি ফরজ নামাজের পর তাকবিরে তাশরিক পড়ুন।"
        ),
        IslamicOccasion(
            id = "ashura",
            titleBn = "পবিত্র আশুরা (৯, ১০ ও ১১ মুহাররম)",
            occasionBadgeBn = "৯, ১০ ও ১১ মুহাররম",
            fazilatBn = "মুহাররম আল্লাহর মাস এবং ১০ মুহাররম আশুরার দিন। এ দিনে আল্লাহ মুসা (আ.)-কে ফিরআউনের কবল থেকে নাজাত দেন। আশুরার দিন (সাথে ৯ বা ১১ তারিখ মিলিয়ে) রোজা রাখলে পূর্ববর্তী এক বছরের সগিরা গুনাহ মাফ হয়।",
            hadithArabic = "صِيَامُ يَوْمِ عَاشُورَاءَ، أَحْتَسِبُ عَلَى اللَّهِ أَنْ يُكَفِّرَ السَّنَةَ الَّتِي قَبْلَهُ",
            hadithBangla = "রাসুলুল্লাহ ﷺ বলেছেন: আশুরার দিনের রোজার ব্যাপারে আমি আল্লাহর কাছে আশা করি, তিনি এর বিনিময়ে পূর্ববর্তী এক বছরের গুনাহ ক্ষমা করে দেবেন।",
            hadithReference = "সহিহ মুসলিম ১১৬২, ১১৩৪; সহিহ বুখারি ২০০৪",
            recommendedAmalBn = "১০ মুহাররম এবং এর সাথে ৯ বা ১১ মুহাররম নফল রোজা রাখুন ও তাওবা-ইস্তিগফার করুন।"
        ),
        IslamicOccasion(
            id = "shab_e_meraj",
            titleBn = "পবিত্র শবে মেরাজ ও সালাতের গুরুত্ব",
            occasionBadgeBn = "২৭ রজব",
            fazilatBn = "নবী করীম ﷺ-এর পবিত্র ইসরা ও মেরাজ স্মরণের দিন, যে সফরে উম্মতে মুহাম্মদির জন্য সরাসরি পাঁচ ওয়াক্ত ফরজ সালাতের উপহার নির্ধারিত হয়েছিল। পাঁচ ওয়াক্ত নামাজ জামাতের সাথে যত্নসহকারে আদায় করাই এর মূল শিক্ষা।",
            hadithArabic = "فُرضَتْ عَلَيَّ الصَّلَوَاتُ خَمْسِينَ، فَلَمْ أَزَلْ أَرْجِعُ إِلَيْهِ حَتَّى قَالَ: هِيَ خَمْسٌ وَهِيَ خَمْسُونَ",
            hadithBangla = "মেরাজের রাতে আল্লাহ তাআলা বলেন: সালাত আদায়ে পাঁচ ওয়াক্ত হলেও সওয়াবের দিক থেকে তা পঞ্চাশ ওয়াক্তের সমান।",
            hadithReference = "সহিহ বুখারি ৩৪৯; সহিহ মুসলিম ১৬২",
            recommendedAmalBn = "পাঁচ ওয়াক্ত নামাজ সময়মতো আদায়ের দৃঢ় সংকল্প করুন এবং আল্লাহর নিকট ক্ষমা ও হেদায়াত প্রার্থনা করুন।"
        ),
        IslamicOccasion(
            id = "ramadan_start",
            titleBn = "রামাদানুল মুবারক! পবিত্র রমজান শুরু",
            occasionBadgeBn = "১ রমজান",
            fazilatBn = "রহমত, মাগফিরাত ও নাজাতের মাস রমজান শুরু হয়েছে। এ মাসে জান্নাতের দরজাগুলো খুলে দেওয়া হয়, জাহান্নামের দরজা বন্ধ করা হয় এবং অবাধ্য শয়তানদের শৃঙ্খলিত করা হয়।",
            hadithArabic = "إِذَا جَاءَ رَمَضَانُ فُتِّحَتْ أَبْوَابُ الْجَنَّةِ، وَغُلِّقَتْ أَبْوَابُ النَّارِ، وَصُفِّدَتِ الشَّيَاطِينُ",
            hadithBangla = "রাসুলুল্লাহ ﷺ বলেছেন: যখন রমজান আসে, তখন জান্নাতের দরজাগুলো খুলে দেওয়া হয়, জাহান্নামের দরজাগুলো বন্ধ করে দেওয়া হয় এবং শয়তানদের শৃঙ্খলিত করা হয়।",
            hadithReference = "সহিহ বুখারি ১৮৯৯; সহিহ মুসলিম ১০৭৯",
            recommendedAmalBn = "রোজা, তারাবিহ, সাহরি, কুরআন খতম ও বেশি বেশি দান-সদকার পরিকল্পনা গ্রহণ করুন।"
        ),
        IslamicOccasion(
            id = "shawwal_six",
            titleBn = "শাওয়ালের ৬ রোজার সুবর্ণ সুযোগ",
            occasionBadgeBn = "২–৭ শাওয়াল",
            fazilatBn = "রমজানের ফরজ রোজার পর শাওয়াল মাসে ৬টি নফল রোজা রাখলে সারা বছর রোজা রাখার সওয়াব পাওয়া যায়।",
            hadithArabic = "مَنْ صَامَ رَمَضَانَ ثُمَّ أَتْبَعَهُ سِتًّا مِنْ شَوَّالٍ كَانَ كَصِيَامِ الدَّهْرِ",
            hadithBangla = "রাসুলুল্লাহ ﷺ বলেছেন: যে ব্যক্তি রমজানের রোজা রাখল এবং এরপর শাওয়ালের ছয়টি রোজা রাখল, সে যেন সারা বছরই রোজা রাখল।",
            hadithReference = "সহিহ মুসলিম ১১৬৪",
            recommendedAmalBn = "শাওয়াল মাসের মধ্যেই সুবিধামতো ৬টি নফল রোজা (টানা বা বিরতি দিয়ে) আদায় করে নিন।"
        ),
        IslamicOccasion(
            id = "dhul_hijjah_ten",
            titleBn = "জিলহজের বরকতময় প্রথম দশ দিন",
            occasionBadgeBn = "১–৮ জিলহজ",
            fazilatBn = "গোটা বছরের দিনগুলোর মধ্যে জিলহজ মাসের প্রথম দশ দিনের নেক আমল আল্লাহর কাছে সবচেয়ে বেশি প্রিয়। এ দিনগুলোতে তাকবির-তাহলিল, রোজা ও দান-সদকার বিপুল সওয়াব রয়েছে।",
            hadithArabic = "مَا مِنْ أَيَّامٍ الْعَمَلُ الصَّالِحُ فِيهِنَّ أَحَبُّ إِلَى اللَّهِ مِنْ هَذِهِ الْأَيَّامِ الْعَشْرِ",
            hadithBangla = "রাসুলুল্লাহ ﷺ বলেছেন: জিলহজের প্রথম দশ দিনের চেয়ে অন্য কোনো দিনের নেক আমল আল্লাহর কাছে এত প্রিয় নয়।",
            hadithReference = "সহিহ বুখারি ৯৬৯",
            recommendedAmalBn = "তাকবির (আল্লাহু আকবার, লা ইলাহা ইল্লাল্লাহ, আলহামদুলিল্লাহ) পাঠ করুন এবং নফল রোজা ও দান বাড়িয়ে দিন।"
        ),
        IslamicOccasion(
            id = "arafah",
            titleBn = "আজ পবিত্র ইয়াওমে আরাফা (৯ জিলহজ)",
            occasionBadgeBn = "৯ জিলহজ",
            fazilatBn = "হজে অবস্থানরত নন এমন মুসলিমদের জন্য আরাফার দিনের রোজা অত্যন্ত ফজিলতপূর্ণ। এ দিনের রোজায় আগের ও পরের মোট দুই বছরের সগিরা গুনাহ মাফ হয় এবং এ দিনে সবচেয়ে বেশি মানুষকে জাহান্নাম থেকে মুক্তি দেওয়া হয়।",
            hadithArabic = "صِيَامُ يَوْمِ عَرَفَةَ، أَحْتَسِبُ عَلَى اللَّهِ أَنْ يُكَفِّرَ السَّنَةَ الَّتِي قَبْلَهُ، وَالسَّنَةَ الَّتِي بَعْدَهُ",
            hadithBangla = "রাসুলুল্লাহ ﷺ বলেছেন: আরাফার দিনের রোজার ব্যাপারে আমি আল্লাহর কাছে আশা রাখি, তিনি এর বিনিময়ে পূর্বের এক বছর ও পরের এক বছরের গুনাহ মাফ করে দেবেন।",
            hadithReference = "সহিহ মুসলিম ১১৬২; দোয়া: জামে তিরমিযি ৩৫৮৫",
            recommendedAmalBn = "আরাফার দিনের রোজা রাখুন এবং «لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ...» দোয়াটি বেশি বেশি পড়ুন।"
        ),
        IslamicOccasion(
            id = "mon_thu_fast",
            titleBn = "সুন্নাত রোজা ও আমল পেশের দিন (সোম/বৃহস্পতি)",
            occasionBadgeBn = "সাপ্তাহিক সুন্নাত • প্রতি সোমবার ও বৃহস্পতিবার",
            fazilatBn = "সোমবার ও বৃহস্পতিবার বান্দার আমল আল্লাহর দরবারে পেশ করা হয়। রাসুলুল্লাহ ﷺ রোজা রাখা অবস্থায় তাঁর আমল পেশ হওয়া পছন্দ করতেন।",
            hadithArabic = "تُعْرَضُ الْأَعْمَالُ يَوْمَ الِاثْنَيْنِ وَالْخَمِيسِ، فَأُحِبُّ أَنْ يُعْرَضَ عَمَلِي وَأَنَا صَائِمٌ",
            hadithBangla = "রাসুলুল্লাহ ﷺ বলেছেন: প্রতি সোমবার ও বৃহস্পতিবার বান্দার আমল আল্লাহর কাছে পেশ করা হয়; তাই আমি পছন্দ করি রোজা রাখা অবস্থায় আমার আমল পেশ হোক।",
            hadithReference = "জামে তিরমিযি ৭৪৭; সহিহ মুসলিম ১১৬২",
            recommendedAmalBn = "সামর্থ্য থাকলে নফল রোজা রাখুন এবং জিকির, তাওবা ও সদকায় মনোযোগী হোন।"
        )
    )

    fun matchingOccasionsFor(cal: Calendar, dayAdjustment: Int = 0): List<IslamicOccasion> {
        val hijri = HijriCalendarHelper.getHijriDate(cal, dayAdjustment)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val matched = mutableListOf<IslamicOccasion>()

        fun addById(id: String) {
            SPECIAL_OCCASIONS.firstOrNull { it.id == id }?.let { matched.add(it) }
        }

        // Major Hijri occasions first so they take visual priority
        when {
            hijri.month == 10 && hijri.day == 1 -> addById("eid_ul_fitr")
            hijri.month == 12 && hijri.day == 10 -> addById("eid_ul_azha")
            hijri.month == 12 && hijri.day == 9 -> addById("arafah")
            hijri.month == 9 && hijri.day in listOf(21, 23, 25, 27, 29) -> addById("shab_e_qadr")
            hijri.month == 8 && hijri.day in listOf(14, 15) -> addById("shab_e_barat")
            hijri.month == 1 && hijri.day in listOf(9, 10, 11) -> addById("ashura")
            hijri.month == 7 && hijri.day == 27 -> addById("shab_e_meraj")
            hijri.month == 9 && hijri.day == 1 -> addById("ramadan_start")
            hijri.month == 10 && hijri.day in 2..7 -> addById("shawwal_six")
            hijri.month == 12 && hijri.day in 1..8 -> addById("dhul_hijjah_ten")
        }

        if (HijriCalendarHelper.isAyyamAlBeed(hijri.day)) {
            addById("ayyam_al_beed")
        }

        if (dayOfWeek == Calendar.FRIDAY) {
            addById("jumuah")
        } else if (dayOfWeek == Calendar.MONDAY || dayOfWeek == Calendar.THURSDAY) {
            addById("mon_thu_fast")
        }

        return matched
    }

    /**
     * Checks today's date against special Islamic occasions.
     * Saves any un-recorded occasion to the homepage notification inbox and posts a system
     * notification when POST_NOTIFICATIONS is granted.
     */
    fun evaluateAndNotifyToday(context: Context, now: Calendar = Calendar.getInstance()): List<OccasionNotificationRecord> {
        val occasions = matchingOccasionsFor(now)
        if (occasions.isEmpty()) return emptyList()

        val prefs = context.getSharedPreferences(PREFS_INBOX, Context.MODE_PRIVATE)
        val notifiedKeys = prefs.getStringSet(KEY_NOTIFIED_DATE_KEYS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val hijri = HijriCalendarHelper.getHijriDate(now)
        val dateStamp = "${now.get(Calendar.YEAR)}-${now.get(Calendar.DAY_OF_YEAR)}"

        val newlyCreated = mutableListOf<OccasionNotificationRecord>()
        val existingRecords = getInboxRecords(context).toMutableList()

        for (occasion in occasions) {
            val dateKey = "${dateStamp}_${occasion.id}"
            if (dateKey in notifiedKeys) continue

            val record = OccasionNotificationRecord(
                recordId = dateKey,
                occasionId = occasion.id,
                titleBn = occasion.titleBn,
                occasionBadgeBn = occasion.occasionBadgeBn,
                hijriDateBn = hijri.formattedBn,
                fazilatBn = occasion.fazilatBn,
                hadithArabic = occasion.hadithArabic,
                hadithBangla = occasion.hadithBangla,
                hadithReference = occasion.hadithReference,
                recommendedAmalBn = occasion.recommendedAmalBn,
                createdAtMillis = now.timeInMillis,
                isRead = false
            )
            existingRecords.add(0, record)
            notifiedKeys.add(dateKey)
            newlyCreated.add(record)
        }

        if (newlyCreated.isNotEmpty()) {
            saveRecords(context, existingRecords.take(MAX_STORED_RECORDS))
            // Trim old date keys to keep prefs tidy
            val trimmedKeys = notifiedKeys.filter { it.startsWith("${now.get(Calendar.YEAR)}-") }.toSet()
            prefs.edit().putStringSet(KEY_NOTIFIED_DATE_KEYS, trimmedKeys).apply()

            newlyCreated.forEachIndexed { idx, record ->
                postSystemNotification(context, record, 8800 + idx)
            }
        }

        return newlyCreated
    }

    /**
     * Delivers one verified daily Sahih Hadith per calendar day via the system notification shade
     * and saves it into the notification inbox so it appears in notifications instead of the homepage.
     */
    suspend fun evaluateAndNotifyDailyHadith(
        context: Context,
        now: Calendar = Calendar.getInstance(),
        allowNetwork: Boolean = true
    ): OccasionNotificationRecord? {
        val dateKey = com.hafij.islamicalarm.hadith.DailyHadithRepository.todayDateKey(now.timeInMillis)
        val notifiedKey = "${dateKey}_daily_hadith"
        val prefs = context.getSharedPreferences(PREFS_INBOX, Context.MODE_PRIVATE)
        val notifiedKeys = prefs.getStringSet(KEY_NOTIFIED_DATE_KEYS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val existingRecords = getInboxRecords(context).toMutableList()

        if (notifiedKey in notifiedKeys || existingRecords.any { it.recordId == notifiedKey }) {
            return null
        }

        val hadith = if (allowNetwork) {
            com.hafij.islamicalarm.hadith.DailyHadithRepository.fetchOrGetDailyHadith(
                context = context,
                dateKey = dateKey,
                offset = 0,
                forceNetwork = false
            )
        } else {
            com.hafij.islamicalarm.hadith.DailyHadithRepository.getInstantHadithForDate(
                context = context,
                dateKey = dateKey,
                offset = 0
            )
        }

        val hijri = HijriCalendarHelper.getHijriDate(now)
        val gradeText = hadith.gradeBn.ifBlank { "সহিহ" }
        val record = OccasionNotificationRecord(
            recordId = notifiedKey,
            occasionId = "daily_hadith",
            titleBn = "📜 আজকের সহিহ হাদিস: ${hadith.topicBn}",
            occasionBadgeBn = if (hadith.isFromOnlineApi) "দৈনিক বাণী • অনলাইন সহিহ" else "দৈনিক সহিহ হাদিস",
            hijriDateBn = hijri.formattedBn,
            fazilatBn = "বিষয়: ${hadith.topicBn} ($gradeText)",
            hadithArabic = hadith.arabicText,
            hadithBangla = hadith.bengaliText,
            hadithReference = hadith.referenceBn,
            recommendedAmalBn = "আজকের হাদিসটি অনুধাবন ও আমল করুন এবং অন্যদের সাথে শেয়ার করুন।",
            createdAtMillis = now.timeInMillis,
            isRead = false
        )

        existingRecords.add(0, record)
        saveRecords(context, existingRecords.take(MAX_STORED_RECORDS))
        notifiedKeys.add(notifiedKey)
        val yearPrefix = "${now.get(Calendar.YEAR)}-"
        val trimmedKeys = notifiedKeys.filter { it.startsWith(yearPrefix) }.toSet()
        prefs.edit().putStringSet(KEY_NOTIFIED_DATE_KEYS, trimmedKeys).apply()

        postSystemNotification(context, record, 8901)
        return record
    }

    fun getInboxRecords(context: Context): List<OccasionNotificationRecord> {
        val prefs = context.getSharedPreferences(PREFS_INBOX, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECORDS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        OccasionNotificationRecord(
                            recordId = o.optString("recordId"),
                            occasionId = o.optString("occasionId"),
                            titleBn = o.optString("titleBn"),
                            occasionBadgeBn = o.optString("occasionBadgeBn"),
                            hijriDateBn = o.optString("hijriDateBn"),
                            fazilatBn = o.optString("fazilatBn"),
                            hadithArabic = o.optString("hadithArabic"),
                            hadithBangla = o.optString("hadithBangla"),
                            hadithReference = o.optString("hadithReference"),
                            recommendedAmalBn = o.optString("recommendedAmalBn"),
                            createdAtMillis = o.optLong("createdAtMillis", 0L),
                            isRead = o.optBoolean("isRead", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getUnreadCount(context: Context): Int = getInboxRecords(context).count { !it.isRead }

    fun markAllRead(context: Context) {
        val updated = getInboxRecords(context).map { it.copy(isRead = true) }
        saveRecords(context, updated)
    }

    fun markRead(context: Context, recordId: String) {
        val updated = getInboxRecords(context).map {
            if (it.recordId == recordId) it.copy(isRead = true) else it
        }
        saveRecords(context, updated)
    }

    fun formatUnreadBadge(count: Int): String = PrayerTimeCalculator.toBengaliNumber(count)

    private fun saveRecords(context: Context, records: List<OccasionNotificationRecord>) {
        val arr = JSONArray()
        for (r in records) {
            val o = JSONObject().apply {
                put("recordId", r.recordId)
                put("occasionId", r.occasionId)
                put("titleBn", r.titleBn)
                put("occasionBadgeBn", r.occasionBadgeBn)
                put("hijriDateBn", r.hijriDateBn)
                put("fazilatBn", r.fazilatBn)
                put("hadithArabic", r.hadithArabic)
                put("hadithBangla", r.hadithBangla)
                put("hadithReference", r.hadithReference)
                put("recommendedAmalBn", r.recommendedAmalBn)
                put("createdAtMillis", r.createdAtMillis)
                put("isRead", r.isRead)
            }
            arr.put(o)
        }
        context.getSharedPreferences(PREFS_INBOX, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORDS_JSON, arr.toString())
            .apply()
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "বিশেষ ইসলামি দিবস ও হাদিস",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "জুমা, আইয়ামে বিজ, শবে বরাত, শবে কদর, ঈদ ও বিশেষ দিনের ফজিলত ও হাদিস"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun buildSystemNotification(
        context: Context,
        record: OccasionNotificationRecord
    ): android.app.Notification {
        ensureChannel(context)
        val openIntent = Intent(context, IslamicNotificationsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            record.recordId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isDailyHadith = record.occasionId == "daily_hadith"
        val fullBody = buildString {
            append("📅 হিজরি: ").append(record.hijriDateBn).append("\n\n")
            if (!isDailyHadith && record.fazilatBn.isNotBlank()) {
                append("✨ ফজিলত: ").append(record.fazilatBn).append("\n\n")
            }
            if (record.hadithArabic.isNotBlank()) {
                append(record.hadithArabic).append("\n\n")
            }
            append("📖 হাদিস: ").append(record.hadithBangla).append("\n")
            append("📚 সূত্র: ").append(record.hadithReference)
            if (!isDailyHadith && record.recommendedAmalBn.isNotBlank()) {
                append("\n\n🤲 আমল: ").append(record.recommendedAmalBn)
            }
        }

        val notifTitle = if (isDailyHadith) {
            "${record.titleBn} (${record.hijriDateBn})"
        } else {
            "${record.titleBn} (${record.hijriDateBn})"
        }
        val notifPreview = if (isDailyHadith) record.hadithBangla else record.fazilatBn

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications_24)
            .setContentTitle(notifTitle)
            .setContentText(notifPreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun postSystemNotification(
        context: Context,
        record: OccasionNotificationRecord,
        notificationId: Int
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            val notification = buildSystemNotification(context, record)
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    /**
     * Schedules a daily repeating check around 6:05 AM (and repeats daily) so special
     * Islamic days fire automatic notifications even if the user hasn't opened the app yet.
     */
    fun scheduleDailyCheck(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, IslamicOccasionReceiver::class.java).apply {
                action = ACTION_CHECK_DAILY_OCCASION
            }
            val pending = PendingIntent.getBroadcast(
                context,
                7701,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val firstTrigger = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 6)
                set(Calendar.MINUTE, 5)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }.timeInMillis

            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                firstTrigger,
                AlarmManager.INTERVAL_DAY,
                pending
            )
        } catch (_: Exception) {
        }
    }
}
