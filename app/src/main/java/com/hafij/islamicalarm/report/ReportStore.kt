package com.hafij.islamicalarm.report

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ReportItem(
    val id: String,
    val type: String,
    val message: String,
    val createdAt: Long,
    val device: String,
    val appVersion: String,
    val synced: Boolean
)

/**
 * Keeps user reports on the phone.
 *
 * Later, when the admin server (Firebase) is connected, a sync step can read [pending],
 * upload each item (id, type, message, createdAt, device, appVersion) and then call [markSynced].
 */
class ReportStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<ReportItem> {
        val list = mutableListOf<ReportItem>()
        try {
            val arr = JSONArray(prefs.getString(KEY_REPORTS, "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    ReportItem(
                        id = o.optString("id"),
                        type = o.optString("type", TYPE_PROBLEM),
                        message = o.optString("message"),
                        createdAt = o.optLong("createdAt"),
                        device = o.optString("device"),
                        appVersion = o.optString("appVersion"),
                        synced = o.optBoolean("synced", false)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.createdAt }
    }

    fun pending(): List<ReportItem> = getAll().filter { !it.synced }

    fun add(type: String, message: String, device: String, appVersion: String): ReportItem {
        val item = ReportItem(
            id = UUID.randomUUID().toString(),
            type = type,
            message = message,
            createdAt = System.currentTimeMillis(),
            device = device,
            appVersion = appVersion,
            synced = false
        )
        save(getAll() + item)
        return item
    }

    fun markSynced(id: String) {
        save(getAll().map { if (it.id == id) it.copy(synced = true) else it })
    }

    fun delete(id: String) {
        save(getAll().filterNot { it.id == id })
    }

    private fun save(items: List<ReportItem>) {
        val arr = JSONArray()
        for (r in items) {
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("type", r.type)
                    put("message", r.message)
                    put("createdAt", r.createdAt)
                    put("device", r.device)
                    put("appVersion", r.appVersion)
                    put("synced", r.synced)
                }
            )
        }
        prefs.edit().putString(KEY_REPORTS, arr.toString()).apply()
    }

    companion object {
        const val TYPE_PROBLEM = "problem"
        const val TYPE_SUGGESTION = "suggestion"
        private const val PREF_NAME = "islamic_alarm_reports"
        private const val KEY_REPORTS = "reports_json"
    }
}
