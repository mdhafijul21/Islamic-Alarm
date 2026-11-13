package com.hafij.islamicalarm.duas

/**
 * Unique, offline category symbols for the Dua library.
 *
 * These are text glyphs rather than downloaded photographs. They work offline
 * and do not add image/font dependencies to the application.
 */
object DuaCategoryArtwork {
    fun iconFor(category: String): String = when (category) {
        DuaData.CATEGORY_ALL -> "📚"
        DuaData.CATEGORY_MORNING_EVENING -> "🌅"
        DuaData.CATEGORY_AFTER_FARD -> "🤲"
        DuaData.CATEGORY_IN_PRAYER -> "🧎"
        DuaData.CATEGORY_DAILY -> "☀️"
        DuaData.CATEGORY_SLEEP -> "🌙"
        DuaData.CATEGORY_WAKE -> "🌤️"
        DuaData.CATEGORY_HOME -> "🏡"
        DuaData.CATEGORY_TOILET -> "🚪"
        DuaData.CATEGORY_MOSQUE -> "🕌"
        DuaData.CATEGORY_FOOD -> "🍽️"
        DuaData.CATEGORY_WUDU -> "💧"
        DuaData.CATEGORY_PARENTS -> "👨‍👩‍👧"
        DuaData.CATEGORY_FAMILY -> "👪"
        DuaData.CATEGORY_RABBANA -> "📖"
        DuaData.CATEGORY_PROPHETS -> "🌿"
        DuaData.CATEGORY_TAWBAH -> "🕊️"
        DuaData.CATEGORY_AKHIRA -> "✨"
        DuaData.CATEGORY_DISTRESS -> "🌧️"
        DuaData.CATEGORY_ILLNESS -> "🌱"
        DuaData.CATEGORY_DEBT -> "🧾"
        DuaData.CATEGORY_TRAVEL -> "🧳"
        DuaData.CATEGORY_WEATHER -> "⛅"
        DuaData.CATEGORY_FRIDAY -> "🗓️"
        DuaData.CATEGORY_NAFL -> "🌼"
        DuaData.CATEGORY_TAHAJJUD -> "🌌"
        DuaData.CATEGORY_ISTIKHARA -> "🧭"
        DuaData.CATEGORY_FASTING -> "🥣"
        DuaData.CATEGORY_RAMADAN -> "🏮"
        DuaData.CATEGORY_HAJJ -> "🕋"
        DuaData.CATEGORY_DECEASED -> "🤍"
        DuaData.CATEGORY_HADITH -> "📜"
        DuaData.CATEGORY_CHARITY -> "💝"
        "পছন্দের দোয়া" -> "⭐"
        else -> "📿"
    }
}
