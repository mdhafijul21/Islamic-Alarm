package com.hafij.islamicalarm.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class AlarmStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getAlarms(): List<AlarmItem> {
        val jsonString = prefs.getString(KEY_ALARMS, "[]") ?: "[]"
        val alarms = mutableListOf<AlarmItem>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                alarms.add(AlarmItem.fromJson(jsonObject))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return alarms
    }

    fun saveAlarms(alarms: List<AlarmItem>) {
        val jsonArray = JSONArray()
        for (alarm in alarms) {
            jsonArray.put(alarm.toJson())
        }
        prefs.edit().putString(KEY_ALARMS, jsonArray.toString()).apply()
    }

    fun addAlarm(alarm: AlarmItem) {
        val alarms = getAlarms().toMutableList()
        alarms.add(alarm)
        saveAlarms(alarms)
    }

    fun updateAlarm(updatedAlarm: AlarmItem) {
        val alarms = getAlarms().toMutableList()
        val index = alarms.indexOfFirst { it.id == updatedAlarm.id }
        if (index != -1) {
            alarms[index] = updatedAlarm
            saveAlarms(alarms)
        }
    }

    fun deleteAlarm(alarmId: String) {
        val alarms = getAlarms().filterNot { it.id == alarmId }
        saveAlarms(alarms)
    }

    fun getAlarm(alarmId: String): AlarmItem? {
        return getAlarms().find { it.id == alarmId }
    }

    fun hasSeenAutostartDialog(): Boolean {
        return prefs.getBoolean(KEY_AUTOSTART_SHOWN, false)
    }

    fun setSeenAutostartDialog(seen: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOSTART_SHOWN, seen).apply()
    }

    companion object {
        private const val PREF_NAME = "islamic_alarm_prefs"
        private const val KEY_ALARMS = "alarms_json"
        private const val KEY_AUTOSTART_SHOWN = "autostart_dialog_shown"
    }
}
