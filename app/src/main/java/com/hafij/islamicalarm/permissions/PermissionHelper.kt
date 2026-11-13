package com.hafij.islamicalarm.permissions

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

data class PermStep(
    val id: String,
    val title: String,
    val why: String,
    val howTo: String
)

/**
 * One place that knows every permission the app needs: how to check it,
 * how to explain it, and which system screen to open to grant it.
 * Used by the start-up permission wizard (MainActivity) and the Settings page.
 */
object PermissionHelper {

    const val ID_LOCATION = "location"
    const val ID_NOTIFICATIONS = "notifications"
    const val ID_OVERLAY = "overlay"
    const val ID_EXACT_ALARM = "exact_alarm"
    const val ID_FULL_SCREEN = "full_screen"
    const val ID_BATTERY = "battery"
    const val ID_DND = "dnd"
    const val ID_PHONE = "phone"
    const val ID_AUTOSTART = "autostart"
    const val ID_SCREEN_PIN = "screen_pin"

    private const val PREFS = "perm_helper_prefs"
    private const val KEY_AUTOSTART_DONE = "autostart_done"

    private val autostartOems = listOf(
        "xiaomi", "redmi", "oppo", "vivo", "realme", "huawei", "honor", "asus", "letv"
    )

    val allSteps: List<PermStep> = listOf(
        PermStep(
            ID_LOCATION,
            "লোকেশন",
            "আপনার এলাকার সঠিক নামাজের সময় বের করার জন্য।",
            "আসা ডায়ালগে 'অ্যাপ ব্যবহারের সময়' বা 'Allow' চাপুন।"
        ),
        PermStep(
            ID_NOTIFICATIONS,
            "নোটিফিকেশন",
            "নামাজের সময় এলার্ম নোটিফিকেশন ও ফুলস্ক্রিন লক দেখানোর জন্য।",
            "আসা ডায়ালগে 'Allow' চাপুন। ডায়ালগ না এলে খোলা সেটিংসে নোটিফিকেশন চালু করুন।"
        ),
        PermStep(
            ID_OVERLAY,
            "অন্য অ্যাপের উপরে দেখানো",
            "ব্রাউজার বা অন্য যেকোনো অ্যাপ খোলা থাকলেও এলার্মের হার্ড লক স্ক্রিন সামনে আনার জন্য।",
            "সেটিংসে অ্যাপের সুইচ চালু করে ব্যাক বাটন চেপে অ্যাপে ফিরুন।"
        ),
        PermStep(
            ID_EXACT_ALARM,
            "সঠিক সময়ে এলার্ম (Alarms & reminders)",
            "ঠিক নির্দিষ্ট সময়ে এলার্ম বাজানোর জন্য।",
            "সেটিংসে অ্যাপের সুইচ চালু করে ব্যাক বাটন চেপে অ্যাপে ফিরুন।"
        ),
        PermStep(
            ID_FULL_SCREEN,
            "ফুল-স্ক্রিন নোটিফিকেশন",
            "ফোনের স্ক্রিন বন্ধ বা লক থাকলে এলার্ম স্ক্রিন জাগিয়ে দেখানোর জন্য (অ্যান্ড্রয়েড ১৪ ও তার উপরে)।",
            "সেটিংসে অ্যাপের সুইচ চালু করে ব্যাক বাটন চেপে অ্যাপে ফিরুন।"
        ),
        PermStep(
            ID_BATTERY,
            "ব্যাটারি অপ্টিমাইজেশন বন্ধ",
            "ফোন ব্যাকগ্রাউন্ডে অ্যাপ বন্ধ করে দিলে এলার্ম মিস হতে পারে, সেটা ঠেকানোর জন্য।",
            "আসা ডায়ালগে 'Allow' বা 'অনুমতি' চাপুন। সেটিংস খুললে অ্যাপটিকে 'Don't optimize' বা 'Unrestricted' করুন।"
        ),
        PermStep(
            ID_DND,
            "ডু নট ডিস্টার্ব অ্যাক্সেস",
            "এলার্মের সময় ফোন পুরো সাইলেন্ট করা ও পরে আগের অবস্থায় ফেরানোর (অটো সাইলেন্ট) জন্য। না দিলে ফোন শুধু ভাইব্রেট মোডে যাবে।",
            "সেটিংসে অ্যাপ খুঁজে সুইচ চালু করুন, তারপর ব্যাক চেপে অ্যাপে ফিরুন।"
        ),
        PermStep(
            ID_PHONE,
            "ফোন কল স্টেট",
            "এলার্ম চলার সময় কল এলে সেটা বুঝে কল রিসিভ করতে দেওয়ার জন্য।",
            "আসা ডায়ালগে 'Allow' চাপুন।"
        ),
        PermStep(
            ID_AUTOSTART,
            "অটোস্টার্ট (Auto launch)",
            "Realme, Oppo, Xiaomi ইত্যাদি ফোনে অ্যাপ বন্ধ থাকলে বা ফোন রিস্টার্টের পর এলার্ম চালু রাখার জন্য।",
            "খোলা পেজে অ্যাপের সুইচ চালু করুন। অ্যাপ এটা চালু আছে কিনা জানতে পারে না, তাই নিজে দেখে নিন। পেজ না খুললে Settings > Apps থেকে অ্যাপ খুঁজে 'Auto launch' চালু করুন।"
        )
    )

    val screenPinStep = PermStep(
        ID_SCREEN_PIN,
        "স্ক্রিন পিনিং (ঐচ্ছিক)",
        "চালু থাকলে এলার্মের লক আরও কঠোরভাবে স্ক্রিন আটকে রাখতে পারে। সাধারণ অ্যাপ এটা নিজে চালু করতে পারে না।",
        "সিকিউরিটি বা প্রাইভেসি সেটিংসে 'Screen pinning' খুঁজে চালু করুন (ফোনভেদে নাম ও জায়গা আলাদা)।"
    )

    fun applicableSteps(ctx: Context): List<PermStep> = allSteps.filter { isApplicable(it.id) }

    private fun isApplicable(id: String): Boolean = when (id) {
        ID_EXACT_ALARM -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ID_FULL_SCREEN -> Build.VERSION.SDK_INT >= 34
        ID_AUTOSTART -> isAutostartOem()
        else -> true
    }

    private fun isAutostartOem(): Boolean {
        val m = Build.MANUFACTURER.lowercase(Locale.ENGLISH)
        return autostartOems.any { m.contains(it) }
    }

    /** Permissions the app can not detect (autostart, screen pinning). */
    fun isDetectable(id: String): Boolean = id != ID_AUTOSTART && id != ID_SCREEN_PIN

    fun isGranted(ctx: Context, id: String): Boolean = when (id) {
        ID_LOCATION ->
            hasPerm(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPerm(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
        ID_NOTIFICATIONS -> NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        ID_OVERLAY -> Settings.canDrawOverlays(ctx)
        ID_EXACT_ALARM ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            } else {
                true
            }
        ID_FULL_SCREEN ->
            if (Build.VERSION.SDK_INT >= 34) {
                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()
            } else {
                true
            }
        ID_BATTERY ->
            (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(ctx.packageName)
        ID_DND ->
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .isNotificationPolicyAccessGranted
        ID_PHONE -> hasPerm(ctx, Manifest.permission.READ_PHONE_STATE)
        // Cannot be detected: treated as "done" once the user was shown the screen.
        ID_AUTOSTART ->
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTOSTART_DONE, false)
        else -> true
    }

    fun markAutostartDone(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTOSTART_DONE, true).apply()
    }

    private fun hasPerm(ctx: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    fun openAppDetails(activity: Activity) {
        try {
            activity.startActivity(appDetailsIntent(activity.packageName))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun appDetailsIntent(pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
        }

    /** Opens the system screen where [id] can be switched on. Falls back to the app-info page. */
    fun openSettings(activity: Activity, id: String) {
        val pkg = activity.packageName
        val uri = Uri.parse("package:$pkg")
        val intents = mutableListOf<Intent>()

        when (id) {
            ID_NOTIFICATIONS -> {
                intents.add(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                )
            }
            ID_OVERLAY -> intents.add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri))
            ID_EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    intents.add(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri))
                }
            }
            ID_FULL_SCREEN -> {
                if (Build.VERSION.SDK_INT >= 34) {
                    intents.add(Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", uri))
                }
            }
            ID_BATTERY -> {
                intents.add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri))
                intents.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            ID_DND -> intents.add(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            ID_AUTOSTART -> intents.addAll(autostartIntents())
            ID_SCREEN_PIN -> intents.add(Intent(Settings.ACTION_SECURITY_SETTINGS))
            else -> {}
        }
        intents.add(appDetailsIntent(pkg))

        for (intent in intents) {
            try {
                activity.startActivity(intent)
                return
            } catch (e: Exception) {
                // try the next one
            }
        }
    }

    private fun autostartIntents(): List<Intent> {
        val m = Build.MANUFACTURER.lowercase(Locale.ENGLISH)
        val list = mutableListOf<Intent>()
        when {
            m.contains("xiaomi") || m.contains("redmi") -> list.add(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            )
            m.contains("oppo") || m.contains("realme") -> {
                list.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                list.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
            }
            m.contains("vivo") -> {
                list.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))
                list.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
            }
            m.contains("huawei") || m.contains("honor") -> {
                list.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
                list.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
            }
            m.contains("asus") -> list.add(
                Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"))
            )
            m.contains("letv") -> list.add(
                Intent().setComponent(ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutostartManageActivity"))
            )
        }
        return list
    }
}
