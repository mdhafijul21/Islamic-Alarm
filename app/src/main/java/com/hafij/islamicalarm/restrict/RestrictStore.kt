package com.hafij.islamicalarm.restrict

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * One restrict-mode slot: the chosen apps/browsers are blocked for [durationMinutes]
 * starting at [hour]:[minute] (every day, or once).
 */
data class RestrictSlot(
    val index: Int,
    val enabled: Boolean = false,
    val hour: Int = 0,
    val minute: Int = 0,
    val durationMinutes: Int = 30,
    val repeatDaily: Boolean = true,
    val packages: Set<String> = emptySet(),
    val onceStartMillis: Long = 0L
) {
    val isConfigured: Boolean get() = packages.isNotEmpty()

    private fun durationMillis(): Long = durationMinutes * 60_000L

    /** End time of the running block window, or 0 when this slot is not blocking right now. */
    fun activeWindowEnd(now: Long): Long {
        if (!enabled || packages.isEmpty() || durationMinutes <= 0) return 0L
        val dur = durationMillis()
        if (repeatDaily) {
            // today's window, and yesterday's one (it may run past midnight)
            for (offset in intArrayOf(0, -1)) {
                val start = startAt(now, hour, minute, offset)
                if (now >= start && now < start + dur) return start + dur
            }
            return 0L
        }
        return if (now >= onceStartMillis && now < onceStartMillis + dur) onceStartMillis + dur else 0L
    }

    /** Start time for a one-time slot: today, or tomorrow when today's window is already over. */
    fun nextOnceStart(now: Long): Long {
        var start = startAt(now, hour, minute, 0)
        if (start + durationMillis() <= now) {
            start = startAt(now, hour, minute, 1)
        }
        return start
    }

    /** A one-time slot whose window is over. */
    fun isExpiredOnce(now: Long): Boolean =
        !repeatDaily && enabled && onceStartMillis > 0L && now >= onceStartMillis + durationMillis()

    companion object {
        fun startAt(baseMillis: Long, hour: Int, minute: Int, dayOffset: Int): Long {
            val c = Calendar.getInstance()
            c.timeInMillis = baseMillis
            c.add(Calendar.DAY_OF_MONTH, dayOffset)
            c.set(Calendar.HOUR_OF_DAY, hour)
            c.set(Calendar.MINUTE, minute)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
    }
}

class RestrictStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private var cachedRaw: String? = null
    private var cachedSlots: List<RestrictSlot> = emptyList()

    /** Always returns [SLOT_COUNT] slots (unused ones are empty). */
    fun getSlots(): List<RestrictSlot> {
        val raw = prefs.getString(KEY_SLOTS, null)
        if (raw != null && raw == cachedRaw) return cachedSlots

        val result = MutableList(SLOT_COUNT) { RestrictSlot(it) }
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val idx = o.optInt("index", -1)
                    if (idx !in 0 until SLOT_COUNT) continue
                    val pkgs = mutableSetOf<String>()
                    val pa = o.optJSONArray("packages")
                    if (pa != null) {
                        for (j in 0 until pa.length()) pkgs.add(pa.getString(j))
                    }
                    result[idx] = RestrictSlot(
                        index = idx,
                        enabled = o.optBoolean("enabled", false),
                        hour = o.optInt("hour", 0),
                        minute = o.optInt("minute", 0),
                        durationMinutes = o.optInt("durationMinutes", 30),
                        repeatDaily = o.optBoolean("repeatDaily", true),
                        packages = pkgs,
                        onceStartMillis = o.optLong("onceStartMillis", 0L)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        cachedRaw = raw
        cachedSlots = result
        return result
    }

    fun saveSlot(slot: RestrictSlot) {
        if (slot.index !in 0 until SLOT_COUNT) return
        val slots = getSlots().toMutableList()
        slots[slot.index] = slot
        val arr = JSONArray()
        for (s in slots) {
            val pa = JSONArray()
            for (p in s.packages) pa.put(p)
            arr.put(
                JSONObject().apply {
                    put("index", s.index)
                    put("enabled", s.enabled)
                    put("hour", s.hour)
                    put("minute", s.minute)
                    put("durationMinutes", s.durationMinutes)
                    put("repeatDaily", s.repeatDaily)
                    put("packages", pa)
                    put("onceStartMillis", s.onceStartMillis)
                }
            )
        }
        prefs.edit().putString(KEY_SLOTS, arr.toString()).apply()
        cachedRaw = null
    }

    /** Switches off one-time slots whose window has passed. */
    fun normalizeExpired(now: Long = System.currentTimeMillis()) {
        for (s in getSlots()) {
            if (s.isExpiredOnce(now)) {
                saveSlot(s.copy(enabled = false))
            }
        }
    }

    /** Latest end time among running windows that block [pkg]; 0 when [pkg] is not blocked now. */
    fun blockEndFor(pkg: String, now: Long = System.currentTimeMillis()): Long {
        var end = 0L
        for (s in getSlots()) {
            if (pkg !in s.packages) continue
            val e = s.activeWindowEnd(now)
            if (e > end) end = e
        }
        return end
    }

    fun isBlocked(pkg: String, now: Long = System.currentTimeMillis()): Boolean =
        blockEndFor(pkg, now) > 0L

    companion object {
        const val SLOT_COUNT = 5
        private const val PREF_NAME = "restrict_mode_prefs"
        private const val KEY_SLOTS = "slots_json"
    }
}
