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
import com.hafij.islamicalarm.restrict.RestrictAccessibilityService
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
    const val ID_ACCESSIBILITY = "accessibility"
    const val ID_AUTOSTART = "autostart"
    const val ID_SCREEN_PIN = "screen_pin"
    const val ID_MICROPHONE = "microphone"

    private const val PREFS = "perm_helper_prefs"
    private const val KEY_AUTOSTART_DONE = "autostart_done"

    private val autostartOems = listOf(
        "xiaomi", "redmi", "oppo", "vivo", "realme", "huawei", "honor", "asus", "letv"
    )

    val allSteps: List<PermStep> = listOf(
        PermStep(
            ID_LOCATION,
            "লোকেশন",
            "কী কাজ করবে: আপনার বর্তমান এলাকার সঠিক নামাজ, সাহরি ও ইফতারের সময়সূচী স্বয়ংক্রিয়ভাবে নির্ণয় করবে।",
            "কীভাবে দেবেন: নিচের 'অনুমতি দিন' বাটনে চাপুন, তারপর পপ-আপ এলে 'While using the app' বা 'Allow' নির্বাচন করুন।"
        ),
        PermStep(
            ID_NOTIFICATIONS,
            "নোটিফিকেশন",
            "কী কাজ করবে: নামাজের সময় হলে নোটিফিকেশন, এলার্ম অ্যালার্ট এবং লক স্ক্রিনের তথ্য দেখাবে।",
            "কীভাবে দেবেন: নিচের 'অনুমতি দিন' বাটনে চেপে আসা পপ-আপে 'Allow' নির্বাচন করুন। পপ-আপ না এলে খোলা সেটিংস থেকে Notifications সুইচ চালু করে ব্যাক করুন।"
        ),
        PermStep(
            ID_OVERLAY,
            "অন্য অ্যাপের উপরে দেখানো (Overlay)",
            "কী কাজ করবে: ফোনে অন্য যেকোনো অ্যাপ, গেম বা ব্রাউজার খোলা থাকলেও নামাজের সময় সাথে সাথে লক স্ক্রিন সামনে দেখাবে এবং সেফ মোডে ভিডিও ব্লার করবে।",
            "কীভাবে দেবেন: 'অনুমতি দিন' চাপলে ফোনের সেটিংস খুলবে -> তালিকায় Islamic Alarm খুঁজে 'Allow display over other apps' সুইচ চালু করুন -> ব্যাক বাটনে অ্যাপে ফিরুন।"
        ),
        PermStep(
            ID_EXACT_ALARM,
            "সঠিক সময়ে এলার্ম (Alarms & reminders)",
            "কী কাজ করবে: ফোন স্লিপ মোডে থাকলেও ঠিক নির্দিষ্ট মিনিট ও সেকেন্ডে নামাজের এলার্ম ও লক চালু করবে।",
            "কীভাবে দেবেন: 'অনুমতি দিন' চাপলে ফোনের সেটিংস খুলবে -> 'Allow setting alarms and reminders' সুইচটি চালু করুন -> ব্যাক করে অ্যাপে ফিরুন।"
        ),
        PermStep(
            ID_FULL_SCREEN,
            "ফুল-স্ক্রিন নোটিফিকেশন",
            "কী কাজ করবে: ফোনের স্ক্রিন বন্ধ বা পিন/প্যাটার্ন লক অবস্থায় থাকলেও এলার্মের সময় ফুল-স্ক্রিন লক দেখাবে (Android 14 ও তার উপরে)।",
            "কীভাবে দেবেন: 'অনুমতি দিন' চাপলে সেটিংস খুলবে -> Islamic Alarm-এর জন্য 'Allow full screen notifications' সুইচ চালু করুন -> ব্যাক করে ফিরুন।"
        ),
        PermStep(
            ID_BATTERY,
            "ব্যাটারি অপ্টিমাইজেশন বন্ধ",
            "কী কাজ করবে: ফোন ব্যাকগ্রাউন্ডে অ্যাপটিকে জোর করে বন্ধ করা থেকে আটকাবে, যাতে কোনো ওয়াক্তের এলার্ম মিস না হয়।",
            "কীভাবে দেবেন: 'অনুমতি দিন' চাপলে ছোট পপ-আপ এলে 'Allow' চাপুন। আর সেটিংস পেজ খুললে Battery ব্যবহার 'Unrestricted' বা 'Don't optimize' নির্বাচন করে ব্যাক করুন।"
        ),
        PermStep(
            ID_DND,
            "ডু নট ডিস্টার্ব (DND) অ্যাক্সেস",
            "কী কাজ করবে: নামাজের সময় ফোন পুরোপুরি সাইলেন্ট করবে এবং নামাজ শেষে আবার আগের রিংগার মোডে ফিরিয়ে আনবে (অটো সাইলেন্ট)।",
            "কীভাবে দেবেন: 'অনুমতি দিন' চাপলে Do Not Disturb access পেজ খুলবে -> তালিকায় Islamic Alarm খুঁজে চাপুন ও 'Allow Do Not Disturb' সুইচ চালু করুন -> ব্যাক করুন।"
        ),
        PermStep(
            ID_ACCESSIBILITY,
            "অ্যাক্সেসিবিলিটি সার্ভিস (রেস্ট্রিক্ট ও সেফ মোড)",
            "কী কাজ করবে: রেস্ট্রিক্ট মোডে বাছাই করা অ্যাপ/ব্রাউজার লক করবে এবং সেফ মোডে নিষিদ্ধ ভিডিও শনাক্ত হলে স্ক্রিন ব্লার করে অটো ব্যাক করাবে। পাসওয়ার্ড বা ব্যক্তিগত লেখা সংগ্রহ করে না।",
            "কীভাবে দেবেন: 'অনুমতি দিন' চাপলে Accessibility পেজ খুলবে -> Downloaded apps থেকে 'Islamic Alarm' বেছে সুইচ চালু করুন। যদি সুইচ ধূসর বা বন্ধ থাকে: ফোনের Settings > Apps > Islamic Alarm > উপরের ⋮ মেনু > 'Allow restricted settings' দিয়ে আবার সুইচটি চালু করুন।"
        ),
        PermStep(
            ID_PHONE,
            "ফোন কল স্টেট",
            "কী কাজ করবে: নামাজের এলার্ম বা লক স্ক্রিন চলাকালীন জরুরি ফোন কল এলে তা শনাক্ত করে কল রিসিভ করার সুযোগ দেবে।",
            "কীভাবে দেবেন: নিচের 'অনুমতি দিন' বাটনে চাপুন এবং আসা পপ-আপে 'Allow' নির্বাচন করুন।"
        ),
        PermStep(
            ID_AUTOSTART,
            "অটোস্টার্ট (Auto launch)",
            "কী কাজ করবে: Xiaomi, Redmi, Oppo, Realme, Vivo, Huawei ইত্যাদি ফোনে অ্যাপ কেটে দিলে বা ফোন রিস্টার্টের পরও এলার্ম সার্ভিস সচল রাখবে।",
            "কীভাবে দেবেন: 'অনুমতি দিন' চাপলে ফোনের অটোস্টার্ট পেজ খুলবে -> Islamic Alarm-এর সুইচ চালু করে ব্যাক করুন। পেজ না খুললে Settings > Apps > Islamic Alarm > App info থেকে 'Auto launch' / 'Autostart' চালু করুন।"
        )
    )

    val screenPinStep = PermStep(
        ID_SCREEN_PIN,
        "স্ক্রিন পিনিং (ঐচ্ছিক)",
        "কী কাজ করবে: চালু থাকলে এলার্মের লক চলাকালীন নেভিগেশন বাটন দিয়ে অন্য অ্যাপে যাওয়া আরও কঠোরভাবে আটকে থাকবে।",
        "কীভাবে দেবেন: 'সেটিংস খুলুন' চাপুন -> ফোনের Security বা Privacy সেটিংসে 'App pinning' / 'Screen pinning' খুঁজে চালু করুন।"
    )

    val microphoneStep = PermStep(
        ID_MICROPHONE,
        "মাইক্রোফোন (আসর ও ইশা আজান শনাক্ত)",
        "কী কাজ করবে: অটো মোড চালু থাকলে আসর ও ইশার ওয়াক্তে আজান শোনা মাত্র ১৫ মিনিটের অটো এলার্ম চালু করবে। কোনো কথা বা কল সংরক্ষণ করে না।",
        "কীভাবে দেবেন: 'চালু করুন' বাটনে চেপে পপ-আপে 'While using the app' বা 'Allow' নির্বাচন করুন।"
    )

    fun applicableSteps(ctx: Context): List<PermStep> = allSteps.filter { isApplicable(it.id) }

    /**
     * Permissions that Android grants directly inside the app via standard runtime dialogs.
     * Kept on the Home screen startup wizard.
     */
    fun isInAppPermission(id: String): Boolean = when (id) {
        ID_LOCATION, ID_PHONE -> true
        ID_NOTIFICATIONS -> Build.VERSION.SDK_INT >= 33
        else -> false
    }

    /** In-app grantable permissions requested on the Home screen. */
    fun homeSteps(ctx: Context): List<PermStep> =
        applicableSteps(ctx).filter { isInAppPermission(it.id) }

    /** System Settings-based permissions requested in the Alarm option. */
    fun alarmSteps(ctx: Context): List<PermStep> =
        applicableSteps(ctx).filter { !isInAppPermission(it.id) }

    /** Missing Settings-based permissions required before any alarm can be set or enabled. */
    fun missingAlarmSteps(ctx: Context): List<PermStep> =
        alarmSteps(ctx).filter { !isGranted(ctx, it.id) }

    fun areAllAlarmPermissionsGranted(ctx: Context): Boolean =
        missingAlarmSteps(ctx).isEmpty()

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
        ID_MICROPHONE -> hasPerm(ctx, Manifest.permission.RECORD_AUDIO)
        ID_ACCESSIBILITY -> isAccessibilityEnabled(ctx)
        // Cannot be detected: treated as "done" once the user was shown the screen.
        ID_AUTOSTART ->
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTOSTART_DONE, false)
        else -> true
    }

    /** True when the restrict-mode accessibility service is switched on in system settings. */
    fun isAccessibilityEnabled(ctx: Context): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(ctx, RestrictAccessibilityService::class.java)
            enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        } catch (e: Exception) {
            false
        }
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
            ID_ACCESSIBILITY -> intents.add(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
