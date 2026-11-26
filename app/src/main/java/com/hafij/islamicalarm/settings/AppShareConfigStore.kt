package com.hafij.islamicalarm.settings

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppShareConfig(
    val shareTitle: String,
    val shareMessage: String,
    val shareUrl: String,
    val adminConfigUrl: String,
    val hasCustomAdminLink: Boolean,
    val updatedAtMillis: Long
)

/**
 * Manages the app share link & message shown by the Settings Share button.
 * Supports remote JSON updates from an Admin app/API so newly published links
 * automatically appear when users tap Share.
 */
object AppShareConfigStore {

    private const val PREFS_NAME = "app_share_config_prefs"
    private const val KEY_SHARE_TITLE = "share_title"
    private const val KEY_SHARE_MESSAGE = "share_message"
    private const val KEY_SHARE_URL = "share_url"
    private const val KEY_ADMIN_CONFIG_URL = "admin_config_url"
    private const val KEY_HAS_CUSTOM_LINK = "has_custom_admin_link"
    private const val KEY_UPDATED_AT = "updated_at_millis"

    private const val DEFAULT_TITLE = "ইসলামিক এলার্ম ও কুরআন"
    private const val DEFAULT_MESSAGE =
        "সঠিক নামাজের সময়সূচী, অটো এলার্ম, হাফেজি কুরআন, তাসবিহ ও দৈনন্দিন আমল পেতে এই অ্যাপটি ব্যবহার করুন:"

    fun defaultPlayStoreUrl(context: Context): String =
        "https://play.google.com/store/apps/details?id=${context.packageName}"

    fun getConfig(context: Context): AppShareConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fallbackUrl = defaultPlayStoreUrl(context)
        return AppShareConfig(
            shareTitle = prefs.getString(KEY_SHARE_TITLE, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_TITLE,
            shareMessage = prefs.getString(KEY_SHARE_MESSAGE, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MESSAGE,
            shareUrl = prefs.getString(KEY_SHARE_URL, null)?.takeIf { it.isNotBlank() } ?: fallbackUrl,
            adminConfigUrl = prefs.getString(KEY_ADMIN_CONFIG_URL, null)?.trim().orEmpty(),
            hasCustomAdminLink = prefs.getBoolean(KEY_HAS_CUSTOM_LINK, false),
            updatedAtMillis = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun saveCustomShareLink(
        context: Context,
        shareUrl: String,
        shareTitle: String? = null,
        shareMessage: String? = null
    ): Boolean {
        val cleanUrl = shareUrl.trim()
        if (!isValidHttpUrl(cleanUrl)) return false

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(KEY_SHARE_URL, cleanUrl)
            .putBoolean(KEY_HAS_CUSTOM_LINK, true)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())

        shareTitle?.trim()?.takeIf { it.isNotBlank() }?.let { editor.putString(KEY_SHARE_TITLE, it) }
        shareMessage?.trim()?.takeIf { it.isNotBlank() }?.let { editor.putString(KEY_SHARE_MESSAGE, it) }
        editor.apply()
        return true
    }

    fun setAdminConfigUrl(context: Context, endpointUrl: String) {
        val clean = endpointUrl.trim()
        if (clean.isNotEmpty() && !isValidHttpUrl(clean)) return
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ADMIN_CONFIG_URL, clean)
            .apply()
    }

    /**
     * Parses a JSON payload pushed or fetched from an Admin app/backend, e.g.:
     * { "share_url": "https://...", "share_title": "...", "share_message": "..." }
     */
    fun applyRemoteConfigJson(context: Context, jsonStr: String): Boolean {
        if (jsonStr.isBlank()) return false
        return try {
            val json = JSONObject(jsonStr)
            val url = listOf("share_url", "app_link", "download_link", "link", "url")
                .asSequence()
                .map { json.optString(it).trim() }
                .firstOrNull { isValidHttpUrl(it) }
                ?: return false

            val title = listOf("share_title", "title")
                .asSequence()
                .map { json.optString(it).trim() }
                .firstOrNull { it.isNotBlank() }

            val message = listOf("share_message", "message", "description")
                .asSequence()
                .map { json.optString(it).trim() }
                .firstOrNull { it.isNotBlank() }

            saveCustomShareLink(context, url, title, message)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun syncFromAdminEndpointIfConfigured(context: Context): Boolean = withContext(Dispatchers.IO) {
        val endpoint = getConfig(context).adminConfigUrl
        if (!isValidHttpUrl(endpoint)) return@withContext false

        var conn: HttpURLConnection? = null
        return@withContext try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) return@withContext false
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            applyRemoteConfigJson(context, body)
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    fun buildShareText(context: Context): String {
        val cfg = getConfig(context)
        return "${cfg.shareMessage}\n${cfg.shareUrl}"
    }

    fun buildShareIntent(context: Context): Intent {
        val cfg = getConfig(context)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, cfg.shareTitle)
            putExtra(Intent.EXTRA_TEXT, buildShareText(context))
        }
    }

    private fun isValidHttpUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("https://") || lower.startsWith("http://")
    }
}
