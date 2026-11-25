package com.hafij.islamicalarm.books

data class JamatCategory(
    val code: String,
    val titleBn: String,
    val subtitleBn: String
)

object JamatCatalog {

    val allCategories = listOf(
        JamatCategory(RemoteLibraryBook.CAT_STORIES, "ইসলামিক গল্প ও কাহিনি", "নবী-রাসূল ও শিক্ষণীয় হেকায়াত"),
        JamatCategory(RemoteLibraryBook.CAT_SAHABA_HISTORY, "সাহাবা জীবনী ও ইতিহাস", "হায়াতুস সাহাবা, আসহাবে রাসূল"),
        JamatCategory(RemoteLibraryBook.CAT_SEERAH_TAFSIR, "সিরাত ও তাফসীর", "সীরাতুন্নবী ﷺ, জালালাইন, মারেফুল"),
        JamatCategory(RemoteLibraryBook.JAMAT_GENERAL_BANGLA, "ইসলামিক বই (বাংলা)", "হাদিস, ফিকহ ও মাসায়েল গ্রন্থ"),
        JamatCategory(RemoteLibraryBook.JAMAT_DAWRA, "জামাত: দাওরা", "বুখারী, মুসলিম, তিরমিযী, আবু দাউদ"),
        JamatCategory(RemoteLibraryBook.JAMAT_MISHKAT, "জামাত: মেশকাত", "মেশকাত শরীফ, হিদায়া ৩-৪, তাহাবী"),
        JamatCategory(RemoteLibraryBook.JAMAT_JALALAIN, "জামাত: জালালাইন", "জালালাইন, হিদায়া ১-২, নূরুল আনওয়ার"),
        JamatCategory(RemoteLibraryBook.JAMAT_SHARHE_BEKAYA, "জামাত: শরহে বেকায়া", "শরহে বেকায়া, আলফিয়াতুল হাদিস"),
        JamatCategory(RemoteLibraryBook.JAMAT_SHARHE_JAMI, "জামাত: শরহে জামি", "শরহে জামি, মাকামাতে হারীরী, আত-তাকরীব"),
        JamatCategory(RemoteLibraryBook.JAMAT_KAFIYA, "জামাত: কাফিয়া", "কাফিয়া, কুদূরী, দুরুসুল বালাগাত"),
        JamatCategory(RemoteLibraryBook.JAMAT_HEDAYATUNNAHU, "জামাত: হেদায়াতুন্নাহু", "হেদায়াতুন্নাহু, নূরুল ইযাহ, ইলমুস সীগাহ"),
        JamatCategory(RemoteLibraryBook.JAMAT_NAHBEMIR, "জামাত: নাহবেমীর", "সরফ কিতাব, ইলমুস সরফ, মা লা বুদ্দা"),
        JamatCategory(RemoteLibraryBook.JAMAT_MIZAN, "জামাত: মীযান", "মীযান ও মুনশাইব, এসো নাহু, তামরীন"),
        JamatCategory(RemoteLibraryBook.JAMAT_TAISIR, "জামাত: তাইসীর", "বেহেশতী জেওর, তালীমুল ইসলাম, কারীমা"),
        JamatCategory(RemoteLibraryBook.JAMAT_CLASSICAL_AR, "ওপেন ও ক্লাসিক্যাল লাইব্রেরি", "api.islamic.app ও OpenLibrary বই")
    )
}
