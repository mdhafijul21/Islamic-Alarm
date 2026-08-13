package com.hafij.islamicalarm.data

import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val lockDurationMinutes: Int = 15,
    val label: String = "",
    val isRepeatDaily: Boolean = true,
    val isEnabled: Boolean = true
) {
    fun getFormattedTime(): String {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
        }
        val sdf = java.text.SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        return sdf.format(calendar.time)
    }

    fun getRepeatModeText(): String {
        return if (isRepeatDaily) "প্রতিদিন" else "একবার"
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("hour", hour)
            put("minute", minute)
            put("lockDurationMinutes", lockDurationMinutes)
            put("label", label)
            put("isRepeatDaily", isRepeatDaily)
            put("isEnabled", isEnabled)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AlarmItem {
            return AlarmItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                hour = json.getInt("hour"),
                minute = json.getInt("minute"),
                lockDurationMinutes = json.optInt("lockDurationMinutes", 15),
                label = json.optString("label", ""),
                isRepeatDaily = json.optBoolean("isRepeatDaily", true),
                isEnabled = json.optBoolean("isEnabled", true)
            )
        }
    }
}
