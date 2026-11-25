package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.hafij.islamicalarm.LockScreenActivity
import com.hafij.islamicalarm.MainActivity
import com.hafij.islamicalarm.PrayerLockOverlayService
import com.hafij.islamicalarm.books.BookData
import com.hafij.islamicalarm.books.BookReaderActivity
import com.hafij.islamicalarm.quran.OfflineArabicQuranActivity
import com.hafij.islamicalarm.quran.QuranRepository
import com.hafij.islamicalarm.quran.SurahDetailActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testBookReaderActivity() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), BookReaderActivity::class.java).apply {
            putExtra("EXTRA_BOOK_ID", BookData.allBooks.first().id)
        }
        val controller = Robolectric.buildActivity(BookReaderActivity::class.java, intent)
        controller.create().start().resume()
        assertNotNull(controller.get())
    }

    @Test
    fun testMainActivity() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.create().start().resume()
        assertNotNull(controller.get())
    }

    @Test
    fun testSurahDetailActivity() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SurahDetailActivity::class.java).apply {
            putExtra("EXTRA_SURAH_ID", 1)
        }
        val controller = Robolectric.buildActivity(SurahDetailActivity::class.java, intent)
        controller.create().start().resume()
        assertNotNull(controller.get())
    }

    @Test
    fun testOfflineArabicQuranDatasetCompleteness() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QuranRepository.ensureOfflineArabicLoaded(context)

        var totalAyahs = 0
        for (surah in QuranRepository.surahList) {
            val ayahs = QuranRepository.getOfflineArabicSurahAyahs(context, surah.id)
            assertEquals("Surah ${surah.id} ayah count mismatch", surah.totalAyahs, ayahs.size)
            assertEquals("Surah ${surah.id} start page mismatch", surah.startPage, ayahs.first().page)
            assertEquals("Surah ${surah.id} para mismatch", surah.paraNumber, ayahs.first().juz)
            totalAyahs += ayahs.size
        }
        assertEquals(114, QuranRepository.surahList.size)
        assertEquals(30, QuranRepository.paraList.size)
        assertEquals(6236, totalAyahs)

        val summaries = QuranRepository.getAllOfflinePageSummaries(context)
        assertEquals(604, summaries.size)
        for (p in 1..604) {
            val pageData = QuranRepository.getOfflineMushafPage(context, p)
            assertTrue("Page $p has no ayahs", pageData.ayahs.isNotEmpty())
        }
        assertEquals(121, QuranRepository.getStartPageForPara(7))
        assertEquals(201, QuranRepository.getStartPageForPara(11))
        assertEquals(582, QuranRepository.getStartPageForPara(30))
    }

    @Test
    fun testOfflineMeaningQuranCompleteness() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var totalAyahs = 0
        for (surah in QuranRepository.surahList) {
            val ayahs = QuranRepository.fetchSurahAyahs(context, surah.id)
            assertEquals("Surah ${surah.id} ayah count mismatch", surah.totalAyahs, ayahs.size)
            for (ayah in ayahs) {
                assertTrue("Surah ${surah.id}:${ayah.numberInSurah} Arabic empty", ayah.textArabic.isNotBlank())
                assertTrue("Surah ${surah.id}:${ayah.numberInSurah} Bangla empty", ayah.textBangla.isNotBlank())
                assertTrue(
                    "Surah ${surah.id}:${ayah.numberInSurah} must not require internet",
                    !ayah.textBangla.contains("ইন্টারনেট সংযোগ")
                )
            }
            totalAyahs += ayahs.size
        }
        assertEquals(6236, totalAyahs)
    }

    @Test
    fun testHafezi15LineQuranCompleteness() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QuranRepository.ensureHafeziLoaded(context)

        val summaries = QuranRepository.getAllHafeziPageSummaries(context)
        assertEquals(610, summaries.size)

        // Verify opening pages & standard 15-line structure for pages 3..610
        val page1 = QuranRepository.getHafeziMushafPage(context, 1)
        assertEquals(9, page1.lines.size) // Surah 1 header + Bismillah ayah 1 + 7 lines
        val page2 = QuranRepository.getHafeziMushafPage(context, 2)
        assertEquals(9, page2.lines.size) // Surah 2 header + Bismillah + 7 lines (Ayah 1..4)

        for (p in 3..610) {
            val page = QuranRepository.getHafeziMushafPage(context, p)
            assertEquals("Page $p must have 15 lines", 15, page.lines.size)
        }

        // Verify Page 3 ends at Ayah 15 (matching 15-line Emdadia Hafezi print)
        val page3 = QuranRepository.getHafeziMushafPage(context, 3)
        val page3Last = page3.lines.last() as QuranRepository.HafeziLine.VerseLine
        assertTrue(page3Last.textWithTokens.contains("﴿١٥﴾"))

        // Verify key Hafezi Para & Surah start pages
        assertEquals(1, QuranRepository.getHafeziStartPageForPara(1))
        assertEquals(22, QuranRepository.getHafeziStartPageForPara(2))
        assertEquals(122, QuranRepository.getHafeziStartPageForPara(7))
        assertEquals(586, QuranRepository.getHafeziStartPageForPara(30))
        assertEquals(610, QuranRepository.getHafeziStartPageForSurah(114))
    }

    @Test
    fun testOfflineArabicQuranActivity() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val cases = listOf(
            Triple(OfflineArabicQuranActivity.TAB_SURAH, "সুরা সমূহ", android.view.View.GONE),
            Triple(OfflineArabicQuranActivity.TAB_PARA, "পারা সমূহ", android.view.View.GONE),
            Triple(OfflineArabicQuranActivity.TAB_PAGE, "পৃষ্ঠা", android.view.View.VISIBLE)
        )
        for ((tab, expectedTitle, expectedJumpBtnVisibility) in cases) {
            val intent = Intent(ctx, OfflineArabicQuranActivity::class.java).apply {
                putExtra(OfflineArabicQuranActivity.EXTRA_INITIAL_TAB, tab)
                putExtra(OfflineArabicQuranActivity.EXTRA_SINGLE_CATEGORY, true)
            }
            val controller = Robolectric.buildActivity(OfflineArabicQuranActivity::class.java, intent)
            val activity = controller.create().start().resume().get()
            assertNotNull(activity)
            val toolbar = activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(
                com.hafij.islamicalarm.R.id.toolbarOfflineQuran
            )
            val tabLayout = activity.findViewById<com.google.android.material.tabs.TabLayout>(
                com.hafij.islamicalarm.R.id.tabLayoutOfflineQuran
            )
            val jumpBtn = activity.findViewById<android.view.View>(
                com.hafij.islamicalarm.R.id.btnQuickJumpPage
            )
            assertEquals(expectedTitle, toolbar.title?.toString())
            assertEquals(android.view.View.GONE, tabLayout.visibility)
            assertEquals(expectedJumpBtnVisibility, jumpBtn.visibility)
        }
    }

    @Test
    fun testLockScreenActivity() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), LockScreenActivity::class.java).apply {
            putExtra("ALARM_ID", "test-alarm")
            putExtra("LOCK_DURATION_MINUTES", 5)
            putExtra("LABEL", "ফজর সালাত")
        }
        val controller = Robolectric.buildActivity(LockScreenActivity::class.java, intent)
        controller.create().start().resume()
        assertNotNull(controller.get())
    }

    @Test
    fun testPrayerLockOverlayServiceIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = PrayerLockOverlayService.createStartIntent(
            context = context,
            alarmId = "fajr-1",
            lockDurationMinutes = 15,
            label = "ফজর সালাত",
            fromAlarm = true
        )
        assertEquals("fajr-1", intent.getStringExtra(PrayerLockOverlayService.EXTRA_ALARM_ID))
        assertEquals(15, intent.getIntExtra(PrayerLockOverlayService.EXTRA_LOCK_DURATION_MINUTES, 0))
        val serviceController = Robolectric.buildService(PrayerLockOverlayService::class.java, intent)
        serviceController.create()
        assertNotNull(serviceController.get())
        serviceController.destroy()
    }

    @Test
    fun testHomeAndAlarmPermissionSplitAndBlocking() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val homeIds = com.hafij.islamicalarm.permissions.PermissionHelper.homeSteps(ctx).map { it.id }
        val alarmIds = com.hafij.islamicalarm.permissions.PermissionHelper.alarmSteps(ctx).map { it.id }

        assertEquals(
            listOf(
                com.hafij.islamicalarm.permissions.PermissionHelper.ID_LOCATION,
                com.hafij.islamicalarm.permissions.PermissionHelper.ID_NOTIFICATIONS,
                com.hafij.islamicalarm.permissions.PermissionHelper.ID_PHONE
            ),
            homeIds
        )
        assertTrue(alarmIds.contains(com.hafij.islamicalarm.permissions.PermissionHelper.ID_OVERLAY))
        assertTrue(alarmIds.contains(com.hafij.islamicalarm.permissions.PermissionHelper.ID_EXACT_ALARM))
        assertTrue(alarmIds.contains(com.hafij.islamicalarm.permissions.PermissionHelper.ID_FULL_SCREEN))
        assertTrue(alarmIds.contains(com.hafij.islamicalarm.permissions.PermissionHelper.ID_BATTERY))
        assertTrue(alarmIds.contains(com.hafij.islamicalarm.permissions.PermissionHelper.ID_DND))
        assertTrue(alarmIds.contains(com.hafij.islamicalarm.permissions.PermissionHelper.ID_ACCESSIBILITY))
        assertTrue(homeIds.intersect(alarmIds.toSet()).isEmpty())

        // Entering Alarm tab with missing permissions shows banner and blocks setting quick alarm
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().start().resume().get()
        val bottomBar = activity.findViewById<com.hafij.islamicalarm.MainBottomBar>(
            com.hafij.islamicalarm.R.id.bottomNavigation
        )
        bottomBar.selectedItemId = com.hafij.islamicalarm.R.id.nav_alarm

        val banner = activity.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.cardAlarmPermissionBanner
        )
        assertEquals(android.view.View.VISIBLE, banner.visibility)

        val alarmStore = com.hafij.islamicalarm.data.AlarmStore(activity)
        val countBefore = alarmStore.getAlarms().size
        activity.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.btnAlarmFajr).performClick()
        assertEquals(countBefore, alarmStore.getAlarms().size)
    }

    @Test
    fun testSingleRestrictSlotSafeVideoAndTasbihButton() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        // 1. Only 1 Restrict slot exists
        val slots = com.hafij.islamicalarm.restrict.RestrictStore(ctx).getSlots()
        assertEquals(1, slots.size)

        // 2. All permissions clearly state what they do & how to grant
        val allPerms = com.hafij.islamicalarm.permissions.PermissionHelper.allSteps +
            com.hafij.islamicalarm.permissions.PermissionHelper.screenPinStep
        for (step in allPerms) {
            assertTrue("Missing what-it-does in ${step.id}", step.why.contains("কী কাজ করবে:"))
            assertTrue("Missing how-to-grant in ${step.id}", step.howTo.contains("কীভাবে দেবেন:"))
        }

        // 3. SafeWords does not treat search engine queries as direct adult sites
        assertTrue(
            !com.hafij.islamicalarm.restrict.SafeWords.matchesSiteUrl(
                "https://www.google.com/search?q=nsfw+adult"
            )
        )
        assertTrue(
            com.hafij.islamicalarm.restrict.SafeWords.matchesSiteUrl(
                "https://www.pornhub.com/view_video.php"
            )
        )

        // 4. Safe Mode does not trigger on text/search alone without a video player node
        val serviceController = Robolectric.buildService(
            com.hafij.islamicalarm.restrict.RestrictAccessibilityService::class.java
        )
        val service = serviceController.create().get()
        val searchNode = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            className = "android.widget.TextView"
            text = "nsfw adult search results"
        }
        assertTrue(!service.shouldTriggerSafeVideoBlock(searchNode))
        searchNode.recycle()
        serviceController.destroy()

        // 5. Tasbih tab has the visible change-tasbih button
        val mainController = Robolectric.buildActivity(MainActivity::class.java)
        val mainActivity = mainController.create().start().resume().get()
        mainActivity.findViewById<com.hafij.islamicalarm.MainBottomBar>(
            com.hafij.islamicalarm.R.id.bottomNavigation
        ).selectedItemId = com.hafij.islamicalarm.R.id.nav_tasbih
        mainActivity.supportFragmentManager.executePendingTransactions()
        val changeBtn = mainActivity.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.btnChangeTasbih
        )
        assertNotNull(changeBtn)
        assertEquals(android.view.View.VISIBLE, changeBtn.visibility)
    }

    @Test
    fun testAutoAlarmScheduleAndExpandedSafeMode() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        // 1. Thursday morning -> 1:20 PM (13:20)
        val thuCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 19, 10, 0, 0) // Thursday
        }
        val thuTarget = com.hafij.islamicalarm.auto.AutoAlarmManager.computeNextDhuhrOrJumuahTarget(thuCal)
        val thuOut = java.util.Calendar.getInstance().apply { timeInMillis = thuTarget.triggerAtMillis }
        assertEquals(java.util.Calendar.THURSDAY, thuOut.get(java.util.Calendar.DAY_OF_WEEK))
        assertEquals(13, thuOut.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(20, thuOut.get(java.util.Calendar.MINUTE))

        // 2. Thursday 2:00 PM -> Friday 12:45 PM
        val thuLate = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 19, 14, 0, 0) // Thursday after 1:20 PM
        }
        val friTarget = com.hafij.islamicalarm.auto.AutoAlarmManager.computeNextDhuhrOrJumuahTarget(thuLate)
        val friOut = java.util.Calendar.getInstance().apply { timeInMillis = friTarget.triggerAtMillis }
        assertEquals(java.util.Calendar.FRIDAY, friOut.get(java.util.Calendar.DAY_OF_WEEK))
        assertEquals(12, friOut.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(45, friOut.get(java.util.Calendar.MINUTE))
        assertTrue(friTarget.isFriday)

        // 3. Auto Mode Maghrib target matches district's Maghrib prayer time and rolls to tomorrow after Maghrib passes
        val district = com.hafij.islamicalarm.prayertimes.LocationData.getDefaultDistrict()
        val sch = com.hafij.islamicalarm.prayertimes.PrayerTimeCalculator.calculate(thuCal, district)
        val beforeMaghrib = java.util.Calendar.getInstance().apply {
            timeInMillis = sch.maghribCal.timeInMillis - 10 * 60_000L
        }
        val maghribToday = com.hafij.islamicalarm.auto.AutoAlarmManager.computeNextMaghribTarget(
            district,
            beforeMaghrib
        )
        assertEquals(sch.maghribCal.get(java.util.Calendar.HOUR_OF_DAY), java.util.Calendar.getInstance().apply { timeInMillis = maghribToday.triggerAtMillis }.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(sch.maghribCal.get(java.util.Calendar.MINUTE), java.util.Calendar.getInstance().apply { timeInMillis = maghribToday.triggerAtMillis }.get(java.util.Calendar.MINUTE))

        val afterMaghrib = java.util.Calendar.getInstance().apply {
            timeInMillis = sch.maghribCal.timeInMillis + 10 * 60_000L
        }
        val maghribTomorrow = com.hafij.islamicalarm.auto.AutoAlarmManager.computeNextMaghribTarget(
            district,
            afterMaghrib
        )
        assertTrue(maghribTomorrow.triggerAtMillis > afterMaghrib.timeInMillis)
        assertEquals(
            java.util.Calendar.FRIDAY,
            java.util.Calendar.getInstance().apply { timeInMillis = maghribTomorrow.triggerAtMillis }.get(java.util.Calendar.DAY_OF_WEEK)
        )

        // 4. Expanded adult sites & adult TLDs trigger immediately, while search URLs do not
        assertTrue(com.hafij.islamicalarm.restrict.SafeWords.matchesSiteUrl("https://www.xvideos.com/video123"))
        assertTrue(com.hafij.islamicalarm.restrict.SafeWords.matchesSiteUrl("https://example.xxx/watch"))
        assertTrue(com.hafij.islamicalarm.restrict.SafeWords.matchesSiteUrl("https://www.reddit.com/r/nsfw/"))
        assertTrue(!com.hafij.islamicalarm.restrict.SafeWords.matchesSiteUrl("https://bangladesh.gov.bd/"))
        assertTrue(!com.hafij.islamicalarm.restrict.SafeWords.matchesSiteUrl("https://campus.example.edu/video"))

        // Unfocused browser URL bar pointing to adult domain triggers Safe Mode
        val serviceCtrl = Robolectric.buildService(
            com.hafij.islamicalarm.restrict.RestrictAccessibilityService::class.java
        )
        val service = serviceCtrl.create().get()
        val urlNode = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            className = "android.widget.EditText"
            viewIdResourceName = "com.android.chrome:id/url_bar"
            isFocused = false
            text = "missav.ws/en/video"
        }
        assertTrue(service.shouldTriggerSafeVideoBlock(urlNode))
        urlNode.recycle()
        serviceCtrl.destroy()

        // 5. Bottom tab renamed to "সেফ মোড", Waqt & Auto Mode use SwitchCompat controls
        val mainController = Robolectric.buildActivity(MainActivity::class.java)
        val mainActivity = mainController.create().start().resume().get()
        val navBar = mainActivity.findViewById<com.hafij.islamicalarm.MainBottomBar>(
            com.hafij.islamicalarm.R.id.bottomNavigation
        )
        fun collectNavLabels(v: android.view.View): List<String> = when (v) {
            is android.widget.TextView -> listOf(v.text.toString())
            is android.view.ViewGroup -> (0 until v.childCount).flatMap { collectNavLabels(v.getChildAt(it)) }
            else -> emptyList()
        }
        val navLabels = collectNavLabels(navBar)
        assertTrue(navLabels.contains("সেফ মোড"))
        assertTrue(!navLabels.contains("এলার্ম"))

        val autoSwitch = mainActivity.findViewById<androidx.appcompat.widget.SwitchCompat>(
            com.hafij.islamicalarm.R.id.switchAutoAlarm
        )
        val fajrSwitch = mainActivity.findViewById<androidx.appcompat.widget.SwitchCompat>(
            com.hafij.islamicalarm.R.id.btnAlarmFajr
        )
        assertNotNull(autoSwitch)
        assertNotNull(fajrSwitch)
        assertEquals(20, com.hafij.islamicalarm.auto.AutoAlarmManager.AUTO_DURATION_MINUTES)
        assertNotNull(ctx)
    }

    @Test
    fun testVidmateTelegramSafeModeAndQuranAudioMove() {
        val mainController = Robolectric.buildActivity(MainActivity::class.java)
        val mainActivity = mainController.create().start().resume().get()

        // 1. Homepage: separate tvHeaderLocation, Audio Quran & Audio Azan cards removed; btnSelectDistrict shows full location
        assertEquals(null, mainActivity.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.btnSelectDistrict
        )?.let { btn ->
            val text = (btn as android.widget.TextView).text.toString()
            assertTrue(text.contains("ঢাকা"))
            null
        })
        val pkgName = mainActivity.packageName
        val res = mainActivity.resources
        assertEquals(0, res.getIdentifier("tvHeaderLocation", "id", pkgName))
        assertEquals(0, res.getIdentifier("cardFeatureAzan", "id", pkgName))
        assertEquals(0, res.getIdentifier("cardFeatureAudioQuran", "id", pkgName))

        // 2. Quran tab: cardCatLatest removed, cardCatAudioQuran added
        mainActivity.findViewById<com.hafij.islamicalarm.MainBottomBar>(
            com.hafij.islamicalarm.R.id.bottomNavigation
        ).selectedItemId = com.hafij.islamicalarm.R.id.nav_quran
        mainActivity.supportFragmentManager.executePendingTransactions()
        assertEquals(0, res.getIdentifier("cardCatLatest", "id", pkgName))
        val quranAudioCard = mainActivity.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.cardCatAudioQuran
        )
        assertNotNull(quranAudioCard)
        assertEquals(android.view.View.VISIBLE, quranAudioCard.visibility)

        // 3. Safe Mode catches VidMate & Telegram adult videos while ignoring benign chats
        val serviceCtrl = Robolectric.buildService(
            com.hafij.islamicalarm.restrict.RestrictAccessibilityService::class.java
        )
        val service = serviceCtrl.create().get()

        // Telegram PhotoViewer video node with NSFW caption triggers
        val tgPlayer = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "org.telegram.messenger"
            className = "org.telegram.ui.Components.VideoPlayer"
            text = "00:12 / 02:30 viral leak 18+ clip nsfw video"
        }
        assertTrue(service.shouldTriggerSafeVideoBlock(tgPlayer))

        val tgBenignRoot = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "org.telegram.messenger"
            className = "android.widget.FrameLayout"
            text = "Assalamu Alaikum brother"
        }
        assertTrue(!service.shouldTriggerSafeVideoBlock(tgBenignRoot))

        // Verify VidMate adult WebView URL triggers immediately
        val vidmateNode = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.nemo.vidmate"
            className = "android.widget.EditText"
            viewIdResourceName = "com.nemo.vidmate:id/web_url"
            isFocused = false
            text = "https://www.xnxx.com/video-abc/test"
        }
        assertTrue(service.shouldTriggerSafeVideoBlock(vidmateNode))

        // Cleanup accessibility nodes
        tgPlayer.recycle()
        tgBenignRoot.recycle()
        vidmateNode.recycle()
        serviceCtrl.destroy()
    }

    @Test
    fun testBanglaLinksTahajjudSettingsThreeCategoriesLocationAndSilent() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        // 1. Safe Mode Bangla/Banglish adult links & encoded URLs trigger, while search & news do not
        assertTrue(com.hafij.islamicalarm.restrict.SafeWords.matchesSiteUrl("https://banglachoti.example.com/video"))
        assertTrue(com.hafij.islamicalarm.restrict.SafeWords.matchesSiteUrl("https://example.org/bangla-sex-video/12"))
        assertTrue(
            com.hafij.islamicalarm.restrict.SafeWords.matchesSiteUrl(
                "https://example.org/%E0%A6%B8%E0%A7%87%E0%A6%95%E0%A7%8D%E0%A6%B8-%E0%A6%AD%E0%A6%BF%E0%A6%A1%E0%A6%BF%E0%A6%93/watch"
            )
        )
        assertTrue(com.hafij.islamicalarm.restrict.SafeWords.matches("bangla hot video boudi sex"))
        assertTrue(!com.hafij.islamicalarm.restrict.SafeWords.matchesSiteUrl("https://www.google.com/search?q=bangla+sex"))
        assertTrue(!com.hafij.islamicalarm.restrict.SafeWords.matchesSiteUrl("https://www.prothomalo.com/bangladesh"))

        // 2. Tahajjud Calculator alarm option is removed
        val tahajjudActivity = Robolectric.buildActivity(
            com.hafij.islamicalarm.tahajjud.TahajjudCalculatorActivity::class.java
        ).create().start().resume().get()
        assertEquals(0, ctx.resources.getIdentifier("btnSetTahajjudAlarm", "id", ctx.packageName))
        assertNotNull(tahajjudActivity)

        // 3. Homepage: recognizable Location button, prompt hint visible when not enabled/set, Auto Silent active & hidden
        val mainActivity = Robolectric.buildActivity(MainActivity::class.java)
            .create().start().resume().get()
        val locBtnText = mainActivity.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.btnSelectDistrict
        ).text.toString()
        assertTrue(!locBtnText.contains("লোকেশন"))
        assertTrue(locBtnText.contains("ঢাকা"))

        val hintView = mainActivity.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvLocationPromptHint
        )
        assertEquals(android.view.View.VISIBLE, hintView.visibility)
        assertTrue(hintView.text.toString().contains("সঠিক সময়ের জন্য GPS বাটন ক্লিক করুন"))

        val cardAutoSilent = mainActivity.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.cardAutoSilent
        )
        assertEquals(android.view.View.GONE, cardAutoSilent.visibility)
        assertTrue(com.hafij.islamicalarm.silent.AutoSilentManager.isAutoSilentEnabled(ctx))

        // 4. Settings: clicking any of the 3 options opens inside its full page, with no back button on top-level menu
        val navBar = mainActivity.findViewById<com.hafij.islamicalarm.MainBottomBar>(
            com.hafij.islamicalarm.R.id.bottomNavigation
        )
        navBar.selectedItemId = com.hafij.islamicalarm.R.id.nav_settings
        mainActivity.supportFragmentManager.executePendingTransactions()

        val settingsToolbar = mainActivity.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            com.hafij.islamicalarm.R.id.toolbarSettings
        )
        val settingsMenu = mainActivity.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.layoutSettingsMenu)
        val settingsDetail = mainActivity.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.layoutSettingsDetail)
        val catPermsHeader = mainActivity.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.rowCatPermissionsHeader)
        val catGuideHeader = mainActivity.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.rowCatGuideHeader)
        val catReportHeader = mainActivity.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.rowCatReportHeader)
        val permsBody = mainActivity.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.layoutCatPermissionsBody)
        val guideBody = mainActivity.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.layoutCatGuideBody)
        val reportBody = mainActivity.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.layoutCatReportBody)
        val settingsFrag = mainActivity.supportFragmentManager.findFragmentByTag("settings")
            as com.hafij.islamicalarm.settings.SettingsFragment

        assertEquals(null, settingsToolbar.navigationIcon)
        assertEquals(android.view.View.VISIBLE, settingsMenu.visibility)
        assertEquals(android.view.View.GONE, settingsDetail.visibility)

        catPermsHeader.performClick()
        assertEquals(android.view.View.GONE, settingsMenu.visibility)
        assertEquals(android.view.View.VISIBLE, settingsDetail.visibility)
        assertEquals(android.view.View.VISIBLE, permsBody.visibility)
        assertEquals(android.view.View.GONE, guideBody.visibility)
        assertNotNull(settingsToolbar.navigationIcon)

        assertTrue(settingsFrag.handleBack())
        assertEquals(android.view.View.VISIBLE, settingsMenu.visibility)
        assertEquals(android.view.View.GONE, settingsDetail.visibility)
        assertEquals(null, settingsToolbar.navigationIcon)

        catGuideHeader.performClick()
        assertEquals(android.view.View.GONE, settingsMenu.visibility)
        assertEquals(android.view.View.VISIBLE, guideBody.visibility)
        assertTrue(settingsFrag.handleBack())

        catReportHeader.performClick()
        assertEquals(android.view.View.GONE, settingsMenu.visibility)
        assertEquals(android.view.View.VISIBLE, reportBody.visibility)
    }

    @Test
    fun testSplashDuroodLauncherAndTimeBasedHeaderArt() {
        // 1. Splash screen shows short Durood (Arabic + Bengali pronunciation) without app name text
        val splash = Robolectric.buildActivity(com.hafij.islamicalarm.SplashActivity::class.java)
            .create().start().resume().get()
        val arabic = splash.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvSplashDuroodArabic
        ).text.toString()
        val pron = splash.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvSplashDuroodPronunciation
        ).text.toString()
        val shortLine = splash.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvSplashDuroodShort
        ).text.toString()

        assertTrue(arabic.contains("صَلِّ"))
        assertTrue(pron.contains("আল্লাহুম্মা"))
        assertTrue(!arabic.contains("ইসলামিক এলার্ম") && !pron.contains("ইসলামিক এলার্ম") && !shortLine.contains("ইসলামিক এলার্ম"))
        assertTrue(!arabic.contains("Islamic Alarm") && !pron.contains("Islamic Alarm") && !shortLine.contains("Islamic Alarm"))

        // 2. Time-based header selects all 5 crisp waqt artwork resources
        val mainActivity = Robolectric.buildActivity(MainActivity::class.java)
            .create().start().resume().get()
        val district = com.hafij.islamicalarm.prayertimes.LocationData.getDefaultDistrict()
        val cal = java.util.Calendar.getInstance()
        val sch = com.hafij.islamicalarm.prayertimes.PrayerTimeCalculator.calculate(cal, district)

        fun calAt(millis: Long) = java.util.Calendar.getInstance().apply { timeInMillis = millis }

        assertEquals(
            com.hafij.islamicalarm.R.drawable.img_header_fajr,
            mainActivity.resolveHeaderImageRes(calAt(sch.fajrCal.timeInMillis + 60_000L), sch)
        )
        assertEquals(
            com.hafij.islamicalarm.R.drawable.img_header_day,
            mainActivity.resolveHeaderImageRes(calAt(sch.dhuhrCal.timeInMillis + 60_000L), sch)
        )
        assertEquals(
            com.hafij.islamicalarm.R.drawable.img_header_asr,
            mainActivity.resolveHeaderImageRes(calAt(sch.asrCal.timeInMillis + 60_000L), sch)
        )
        assertEquals(
            com.hafij.islamicalarm.R.drawable.img_header_maghrib,
            mainActivity.resolveHeaderImageRes(calAt(sch.maghribCal.timeInMillis + 60_000L), sch)
        )
        assertEquals(
            com.hafij.islamicalarm.R.drawable.img_header_night,
            mainActivity.resolveHeaderImageRes(calAt(sch.ishaCal.timeInMillis + 60_000L), sch)
        )

        // 3. Enlarged adaptive emblem resource loads properly
        assertNotNull(
            androidx.core.content.ContextCompat.getDrawable(
                splash,
                com.hafij.islamicalarm.R.drawable.ic_launcher_emblem
            )
        )

        // 4. Tasbih and Duas main screens have no unnecessary top-left back icon
        val navBar = mainActivity.findViewById<com.hafij.islamicalarm.MainBottomBar>(
            com.hafij.islamicalarm.R.id.bottomNavigation
        )
        navBar.selectedItemId = com.hafij.islamicalarm.R.id.nav_tasbih
        mainActivity.supportFragmentManager.executePendingTransactions()
        val tasbihToolbar = mainActivity.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            com.hafij.islamicalarm.R.id.toolbarTasbih
        )
        assertEquals(null, tasbihToolbar.navigationIcon)

        navBar.selectedItemId = com.hafij.islamicalarm.R.id.nav_duas
        mainActivity.supportFragmentManager.executePendingTransactions()
        val duasToolbar = mainActivity.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            com.hafij.islamicalarm.R.id.toolbarDuas
        )
        assertEquals(null, duasToolbar.navigationIcon)

        // 5. Meaning Quran category removes "অফলাইন" text and all inline audio UI
        navBar.selectedItemId = com.hafij.islamicalarm.R.id.nav_quran
        mainActivity.supportFragmentManager.executePendingTransactions()
        val meaningCard = mainActivity.findViewById<android.view.ViewGroup>(
            com.hafij.islamicalarm.R.id.cardCatMeaning
        )
        fun collectTexts(v: android.view.View): List<String> = when (v) {
            is android.widget.TextView -> listOf(v.text.toString())
            is android.view.ViewGroup -> (0 until v.childCount).flatMap { collectTexts(v.getChildAt(it)) }
            else -> emptyList()
        }
        val meaningTexts = collectTexts(meaningCard)
        assertTrue(meaningTexts.none { it.contains("অফলাইন") })
        assertEquals(0, mainActivity.resources.getIdentifier("cardAudioPlayer", "id", mainActivity.packageName))
        assertEquals(0, mainActivity.resources.getIdentifier("btnPlayAudio", "id", mainActivity.packageName))
        assertEquals(0, mainActivity.resources.getIdentifier("btnPlaySurahAudio", "id", mainActivity.packageName))
    }

    @Test
    fun testHafeziPerParaSearchAudioRenameAndDuasCategoryGrid() { runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        QuranRepository.ensureHafeziLoaded(ctx)

        // 1. Per-Para page range and 1..lastPage mapping in QuranRepository
        assertEquals(1..21, QuranRepository.getHafeziParaPageRange(1))
        assertEquals(21, QuranRepository.getHafeziParaTotalPages(1))
        assertEquals(1, QuranRepository.getHafeziPageInPara(1))
        assertEquals(21, QuranRepository.getHafeziPageInPara(21))

        assertEquals(63..81, QuranRepository.getHafeziParaPageRange(4))
        assertEquals(19, QuranRepository.getHafeziParaTotalPages(4))
        assertEquals(16, QuranRepository.getHafeziPageInPara(78))

        assertEquals(586..610, QuranRepository.getHafeziParaPageRange(30))
        assertEquals(25, QuranRepository.getHafeziParaTotalPages(30))
        assertEquals(25, QuranRepository.getHafeziPageInPara(610))

        // 2. Search-Only "সার্চ" mode: no pre-filled list, only Para + Page search controls
        val searchIntent = Intent(ctx, OfflineArabicQuranActivity::class.java).apply {
            putExtra(OfflineArabicQuranActivity.EXTRA_PARA_PAGE_SEARCH, true)
        }
        val offlineAct = Robolectric.buildActivity(OfflineArabicQuranActivity::class.java, searchIntent)
            .create().start().resume().get()
        val toolbar = offlineAct.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            com.hafij.islamicalarm.R.id.toolbarOfflineQuran
        )
        val searchPanel = offlineAct.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.cardParaPageSearchPanel
        )
        val indexListFrame = offlineAct.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.layoutIndexListFrame
        )
        val standardSearchBar = offlineAct.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.cardStandardSearchBar
        )
        val rvIndex = offlineAct.findViewById<androidx.recyclerview.widget.RecyclerView>(
            com.hafij.islamicalarm.R.id.rvOfflineIndex
        )
        val deadline = System.currentTimeMillis() + 3000L
        while (offlineAct.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.pbOfflineLoading).visibility == android.view.View.VISIBLE &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(25)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }

        assertEquals("সার্চ", toolbar.title?.toString())
        assertEquals(android.view.View.VISIBLE, searchPanel.visibility)
        assertEquals(android.view.View.GONE, indexListFrame.visibility)
        assertEquals(android.view.View.GONE, standardSearchBar.visibility)
        assertEquals(0, rvIndex.adapter?.itemCount ?: 0)

        // Enter Para 4 and Page 16 in the search-only fields and open Mushaf
        val etParaNum = offlineAct.findViewById<android.widget.EditText>(
            com.hafij.islamicalarm.R.id.etSearchParaNumber
        )
        val etParaPage = offlineAct.findViewById<android.widget.EditText>(
            com.hafij.islamicalarm.R.id.etSearchParaPageNumber
        )
        val tvRangeHint = offlineAct.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvSearchParaRangeHint
        )
        val btnSearchGo = offlineAct.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.btnExecuteParaPageSearch
        )
        etParaNum.setText("4")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(tvRangeHint.text.toString().contains("পারা ৪"))
        assertTrue(tvRangeHint.text.toString().contains("১৯"))

        etParaPage.setText("16")
        btnSearchGo.performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue(toolbar.subtitle?.toString().orEmpty().contains("পারা ৪ • পৃষ্ঠা ১৬ / ১৯"))
        assertTrue(toolbar.subtitle?.toString().orEmpty().contains("মোট পৃষ্ঠা ৭৮/৬১০"))
        val footerText = offlineAct.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvMushafPageFooter
        ).text.toString()
        assertEquals("পারা ৪ : পৃষ্ঠা ১৬/১৯", footerText)
        assertTrue(!footerText.contains("মোট"))

        // Font zoom controls & bottom hint must be removed
        assertEquals(0, ctx.resources.getIdentifier("btnMushafFontSmaller", "id", ctx.packageName))
        assertEquals(0, ctx.resources.getIdentifier("btnMushafFontLarger", "id", ctx.packageName))
        assertEquals(0, ctx.resources.getIdentifier("tvMushafBottomLineHint", "id", ctx.packageName))

        // Verify ViewPager2 Mushaf is RTL (left-to-right page advance) and renders top-center ( ০৪ : ১৬ ), মোট পৃষ্ঠা, ﴿ ١٦ / ١٩ ﴾
        val vpMushaf = offlineAct.findViewById<androidx.viewpager2.widget.ViewPager2>(
            com.hafij.islamicalarm.R.id.vpMushafPages
        )
        vpMushaf.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
        )
        vpMushaf.layout(0, 0, 1080, 1920)
        assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, vpMushaf.layoutDirection)
        val vpInnerRv = vpMushaf.getChildAt(0) as androidx.recyclerview.widget.RecyclerView
        vpInnerRv.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
        )
        vpInnerRv.layout(0, 0, 1080, 1920)
        assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, vpInnerRv.layoutDirection)
        val mushafHolder = vpInnerRv.findViewHolderForAdapterPosition(77) // global page 78
        assertNotNull(mushafHolder)
        val topMeta = mushafHolder!!.itemView.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvMushafTopMeta
        )
        val topTotalPage = mushafHolder.itemView.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvMushafTopTotalPage
        )
        val bottomPage = mushafHolder.itemView.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvMushafBottomPage
        )
        val linesContainer = mushafHolder.itemView.findViewById<android.view.ViewGroup>(
            com.hafij.islamicalarm.R.id.llMushafPageContent
        )
        assertEquals("( ০৪ : ১৬ )", topMeta.text.toString())
        assertEquals("মোট পৃষ্ঠা ৭৮/৬১০", topTotalPage.text.toString())
        assertEquals("﴿ ١٦ / ١٩ ﴾", bottomPage.text.toString())
        assertTrue("Expected 15 Mushaf rows plus rule lines", linesContainer.childCount >= 15)

        val allMedallions = mutableListOf<OfflineArabicQuranActivity.AyahMedallionSpan>()
        fun collectMedallions(v: android.view.View) {
            if (v is android.widget.TextView) {
                val spanned = v.text as? android.text.Spanned ?: return
                allMedallions.addAll(
                    spanned.getSpans(0, spanned.length, OfflineArabicQuranActivity.AyahMedallionSpan::class.java)
                )
            } else if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) collectMedallions(v.getChildAt(i))
            }
        }
        collectMedallions(linesContainer)
        assertTrue("Mushaf page 78 should render Ayah medallion spans", allMedallions.isNotEmpty())
        assertTrue(
            "All Ayah medallion spans must carry visible non-empty Extended Arabic-Indic or fallback digits",
            allMedallions.all { it.displayDigits.isNotBlank() && it.displayDigits.all { ch -> ch in '\u06F0'..'\u06F9' || ch in '\u0660'..'\u0669' } }
        )

        // 3. Quran Dashboard cards ("পৃষ্ঠা", "সার্চ", "অডিও") & Duas category grid (no "সবগুলো")
        val mainAct = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val navBar = mainAct.findViewById<com.hafij.islamicalarm.MainBottomBar>(
            com.hafij.islamicalarm.R.id.bottomNavigation
        )
        fun collectTexts(v: android.view.View): List<String> = when (v) {
            is android.widget.TextView -> listOf(v.text.toString())
            is android.view.ViewGroup -> (0 until v.childCount).flatMap { collectTexts(v.getChildAt(it)) }
            else -> emptyList()
        }

        navBar.selectedItemId = com.hafij.islamicalarm.R.id.nav_quran
        mainAct.supportFragmentManager.executePendingTransactions()
        val pageCard = mainAct.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.cardCatSearch)
        val searchCard = mainAct.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.cardCatParaSearch)
        val audioCard = mainAct.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.cardCatAudioQuran)
        assertEquals(android.view.View.VISIBLE, pageCard.visibility)
        assertEquals(android.view.View.VISIBLE, searchCard.visibility)
        assertEquals(android.view.View.VISIBLE, audioCard.visibility)
        assertTrue(collectTexts(pageCard).contains("পৃষ্ঠা"))
        assertTrue(collectTexts(pageCard).none { it.contains("সার্চ") })
        assertTrue(collectTexts(searchCard).contains("সার্চ"))
        assertTrue(collectTexts(audioCard).contains("অডিও"))
        assertTrue(collectTexts(audioCard).none { it.contains("কুরআন") })

        navBar.selectedItemId = com.hafij.islamicalarm.R.id.nav_duas
        mainAct.supportFragmentManager.executePendingTransactions()
        assertTrue(!com.hafij.islamicalarm.duas.DuaData.getCategories.contains(com.hafij.islamicalarm.duas.DuaData.CATEGORY_ALL))
        val rvDuaCats = mainAct.findViewById<androidx.recyclerview.widget.RecyclerView>(
            com.hafij.islamicalarm.R.id.rvDuaCategories
        )
        rvDuaCats.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
        )
        rvDuaCats.layout(0, 0, 1080, 1920)
        val visibleDuaTexts = collectTexts(rvDuaCats)
        assertTrue(visibleDuaTexts.none { it == "সবগুলো" || it == "(সবগুলো)" })
        assertTrue(visibleDuaTexts.contains("পছন্দের আমল"))

        // 4. AudioQuranActivity renamed to "অডিও", multi-mirror CDN fast URLs, and foreground media notification (Prev | Play/Pause | Next)
        val reciterUrls = com.hafij.islamicalarm.audio.ReciterData.getDefaultReciter().getAllPlaybackUrls(1)
        assertTrue(reciterUrls.size >= 3)
        assertTrue(reciterUrls.first().contains("cdn.islamic.network"))

        val playingNotif = com.hafij.islamicalarm.audio.QuranAudioService.buildPlaybackNotification(
            context = ctx,
            state = com.hafij.islamicalarm.audio.QuranAudioState(
                surah = QuranRepository.surahList.first(),
                reciter = com.hafij.islamicalarm.audio.ReciterData.getDefaultReciter(),
                isPlaying = true
            )
        )
        assertEquals(3, playingNotif.actions?.size)
        assertEquals("আগের সুরা", playingNotif.actions!![0].title.toString())
        assertEquals("বিরতি", playingNotif.actions!![1].title.toString())
        assertEquals("পরের সুরা", playingNotif.actions!![2].title.toString())

        val pausedNotif = com.hafij.islamicalarm.audio.QuranAudioService.buildPlaybackNotification(
            context = ctx,
            state = com.hafij.islamicalarm.audio.QuranAudioState(
                surah = QuranRepository.surahList.first(),
                reciter = com.hafij.islamicalarm.audio.ReciterData.getDefaultReciter(),
                isPlaying = false
            )
        )
        assertEquals("চালু করুন", pausedNotif.actions!![1].title.toString())

        val audioCtrl = Robolectric.buildActivity(com.hafij.islamicalarm.audio.AudioQuranActivity::class.java)
            .create().start().resume()
        val audioAct = audioCtrl.get()
        val audioToolbar = audioAct.findViewById<androidx.appcompat.widget.Toolbar>(
            com.hafij.islamicalarm.R.id.toolbarAudioQuran
        )
        val downloadAllBtn = audioAct.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.btnDownloadAllOffline
        )
        assertEquals("অডিও", audioToolbar.title?.toString())
        assertEquals(android.view.View.VISIBLE, downloadAllBtn.visibility)
        audioCtrl.pause().stop().destroy()
    }
    }

    @Test
    fun testGpsHintNotificationsWidgetPinFortyHadithPuritySalahAndCompactDuas() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        // 1. Homepage top-right notification icon & GPS prompt text
        val mainActivity = Robolectric.buildActivity(MainActivity::class.java)
            .create().start().resume().get()
        val hintText = mainActivity.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvLocationPromptHint
        ).text.toString()
        assertEquals("📍 সঠিক সময়ের জন্য GPS বাটন ক্লিক করুন", hintText)

        val notifBtn = mainActivity.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.btnHomeNotifications
        )
        assertEquals(android.view.View.VISIBLE, notifBtn.visibility)

        // 2. Auto Islamic occasion detection (Jumuah, Ayyam-e-Beed, Shab-e-Barat, Shab-e-Qadr, Eid-ul-Fitr, Eid-ul-Azha)
        fun findDateMatching(
            predicate: (java.util.Calendar, com.hafij.islamicalarm.prayertimes.HijriDate) -> Boolean
        ): java.util.Calendar {
            val c = java.util.Calendar.getInstance()
            c.set(2026, java.util.Calendar.JANUARY, 1, 9, 0, 0)
            for (i in 0..400) {
                val hijri = com.hafij.islamicalarm.prayertimes.HijriCalendarHelper.getHijriDate(c)
                if (predicate(c, hijri)) return c.clone() as java.util.Calendar
                c.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            error("No date matched in 400 days")
        }

        val friday = findDateMatching { c, _ -> c.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY }
        val ayyamBeed = findDateMatching { _, h -> h.day == 14 }
        val shabBarat = findDateMatching { _, h -> h.month == 8 && h.day == 15 }
        val shabQadr = findDateMatching { _, h -> h.month == 9 && h.day == 27 }
        val eidFitr = findDateMatching { _, h -> h.month == 10 && h.day == 1 }
        val eidAzha = findDateMatching { _, h -> h.month == 12 && h.day == 10 }

        assertTrue(com.hafij.islamicalarm.notifications.IslamicOccasionNotifier.matchingOccasionsFor(friday).any { it.id == "jumuah" })
        assertTrue(com.hafij.islamicalarm.notifications.IslamicOccasionNotifier.matchingOccasionsFor(ayyamBeed).any { it.id == "ayyam_al_beed" })
        assertTrue(com.hafij.islamicalarm.notifications.IslamicOccasionNotifier.matchingOccasionsFor(shabBarat).any { it.id == "shab_e_barat" })
        assertTrue(com.hafij.islamicalarm.notifications.IslamicOccasionNotifier.matchingOccasionsFor(shabQadr).any { it.id == "shab_e_qadr" })
        assertTrue(com.hafij.islamicalarm.notifications.IslamicOccasionNotifier.matchingOccasionsFor(eidFitr).any { it.id == "eid_ul_fitr" })
        assertTrue(com.hafij.islamicalarm.notifications.IslamicOccasionNotifier.matchingOccasionsFor(eidAzha).any { it.id == "eid_ul_azha" })

        val recorded = com.hafij.islamicalarm.notifications.IslamicOccasionNotifier.evaluateAndNotifyToday(ctx, shabQadr)
        assertTrue(recorded.isNotEmpty())
        assertTrue(recorded.all { it.fazilatBn.isNotBlank() && it.hadithBangla.isNotBlank() && it.hadithReference.isNotBlank() })
        assertTrue(com.hafij.islamicalarm.notifications.IslamicOccasionNotifier.getUnreadCount(ctx) >= 1)

        val notifActCtrl = Robolectric.buildActivity(
            com.hafij.islamicalarm.notifications.IslamicNotificationsActivity::class.java
        ).create().start().resume()
        val notifAct = notifActCtrl.get()
        val inboxLayout = notifAct.findViewById<android.view.ViewGroup>(
            com.hafij.islamicalarm.R.id.layoutInboxNotifications
        )
        assertTrue(inboxLayout.childCount >= 1)
        notifActCtrl.pause().stop().destroy()

        // 3. Duas categories: 40 short Hadiths, Purity & Wudu-Ghusl, Salah rules; no entry count; no eye icon
        val categories = com.hafij.islamicalarm.duas.DuaData.getCategories
        assertEquals(com.hafij.islamicalarm.duas.DuaData.CATEGORY_FORTY_HADITH, categories[0])
        assertEquals(com.hafij.islamicalarm.duas.DuaData.CATEGORY_PURITY, categories[1])
        assertEquals(com.hafij.islamicalarm.duas.DuaData.CATEGORY_SALAH_RULES, categories[2])

        val fortyHadiths = com.hafij.islamicalarm.duas.DuaData.getDuasAndHadiths.filter {
            it.category == com.hafij.islamicalarm.duas.DuaData.CATEGORY_FORTY_HADITH
        }
        assertEquals(40, fortyHadiths.size)
        assertTrue(fortyHadiths.all { it.arabic.isNotBlank() && it.translation.isNotBlank() && it.reference.isNotBlank() })

        val purityItems = com.hafij.islamicalarm.duas.DuaData.getDuasAndHadiths.filter {
            it.category == com.hafij.islamicalarm.duas.DuaData.CATEGORY_PURITY
        }
        assertTrue(purityItems.any { it.title.contains("ইস্তিঞ্জা") })
        assertTrue(purityItems.any { it.title.contains("ওজু") && it.title.contains("ফরজ") })
        assertTrue(purityItems.any { it.title.contains("গোসল") })
        assertTrue(purityItems.any { it.title.contains("ওজু ভেঙে") })

        val salahItems = com.hafij.islamicalarm.duas.DuaData.getDuasAndHadiths.filter {
            it.category == com.hafij.islamicalarm.duas.DuaData.CATEGORY_SALAH_RULES
        }
        assertTrue(salahItems.any { it.title.contains("পদ্ধতি") })
        assertTrue(salahItems.any { it.title.contains("ফরজ") })
        assertTrue(salahItems.any { it.title.contains("ওয়াজিব") })
        assertTrue(salahItems.any { it.title.contains("সুন্নাত") })
        assertTrue(salahItems.any { it.title.contains("মাকরুহ") })
        assertTrue(salahItems.any { it.title.contains("নামাজ ভেঙে") })

        assertEquals(0, ctx.resources.getIdentifier("btnOpenDuaDetail", "id", ctx.packageName))
        assertEquals(0, ctx.resources.getIdentifier("tvCategoryCount", "id", ctx.packageName))

        val navBar = mainActivity.findViewById<com.hafij.islamicalarm.MainBottomBar>(
            com.hafij.islamicalarm.R.id.bottomNavigation
        )
        navBar.selectedItemId = com.hafij.islamicalarm.R.id.nav_duas
        mainActivity.supportFragmentManager.executePendingTransactions()
        val rvDuaCats = mainActivity.findViewById<androidx.recyclerview.widget.RecyclerView>(
            com.hafij.islamicalarm.R.id.rvDuaCategories
        )
        rvDuaCats.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
        )
        rvDuaCats.layout(0, 0, 1080, 1920)
        fun collectTexts(v: android.view.View): List<String> = when (v) {
            is android.widget.TextView -> listOf(v.text.toString())
            is android.view.ViewGroup -> (0 until v.childCount).flatMap { collectTexts(v.getChildAt(it)) }
            else -> emptyList()
        }
        val catTexts = collectTexts(rvDuaCats)
        assertTrue(catTexts.none { it.contains("এন্ট্রি") })

        // 4. Settings "Add to Home" widget button & widget update
        navBar.selectedItemId = com.hafij.islamicalarm.R.id.nav_settings
        mainActivity.supportFragmentManager.executePendingTransactions()
        val addToHomeBtn = mainActivity.findViewById<android.widget.Button>(
            com.hafij.islamicalarm.R.id.btnSettingsAddToHome
        )
        assertEquals(android.view.View.VISIBLE, addToHomeBtn.visibility)
        assertEquals("Add to Home", addToHomeBtn.text.toString())

        val awm = android.appwidget.AppWidgetManager.getInstance(ctx)
        val widgetId = org.robolectric.Shadows.shadowOf(awm).createWidget(
            com.hafij.islamicalarm.widget.IslamicAlarmWidget::class.java,
            com.hafij.islamicalarm.R.layout.widget_islamic_alarm
        )
        com.hafij.islamicalarm.widget.IslamicAlarmWidget.updateAppWidget(ctx, awm, widgetId)
        val widgetView = org.robolectric.Shadows.shadowOf(awm).getViewFor(widgetId)
        val widgetLocation = widgetView.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvWidgetLocation
        ).text.toString()
        val widgetNext = widgetView.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvWidgetNextPrayer
        ).text.toString()
        val widgetChrono = widgetView.findViewById<android.widget.Chronometer>(
            com.hafij.islamicalarm.R.id.chronometerWidgetCountdown
        )
        val widgetCard = widgetView.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.widgetRoot
        )
        assertEquals(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            widgetCard.layoutParams.height
        )
        assertTrue(widgetLocation.contains("ঢাকা"))
        assertTrue(widgetNext.startsWith("পরবর্তী নামাজ:"))
        assertNotNull(widgetChrono)
    }

    @Test
    fun testDnsVpnIncognitoSafeSearchSearchClickThroughAndNotices() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        fun buildIpv4UdpDnsQuery(domain: String): ByteArray {
            val labels = domain.split('.')
            val qname = java.io.ByteArrayOutputStream()
            for (lbl in labels) {
                val b = lbl.toByteArray(Charsets.US_ASCII)
                qname.write(b.size)
                qname.write(b)
            }
            qname.write(0)
            val dnsLen = 12 + qname.size() + 4
            val udpLen = 8 + dnsLen
            val totalLen = 20 + udpLen
            val pkt = ByteArray(totalLen)
            pkt[0] = 0x45.toByte() // IPv4, IHL=5
            pkt[2] = ((totalLen ushr 8) and 0xFF).toByte()
            pkt[3] = (totalLen and 0xFF).toByte()
            pkt[9] = 17 // UDP
            // Src 10.120.0.1 -> Dst 10.120.0.2
            pkt[12] = 10; pkt[13] = 120; pkt[14] = 0; pkt[15] = 1
            pkt[16] = 10; pkt[17] = 120; pkt[18] = 0; pkt[19] = 2
            // UDP ports 54321 -> 53
            pkt[20] = 0xD4.toByte(); pkt[21] = 0x31
            pkt[22] = 0; pkt[23] = 53
            pkt[24] = ((udpLen ushr 8) and 0xFF).toByte()
            pkt[25] = (udpLen and 0xFF).toByte()
            // DNS Header: id=0x1234, QR=0, QDCOUNT=1
            pkt[28] = 0x12; pkt[29] = 0x34
            pkt[30] = 0x01; pkt[31] = 0x00
            pkt[32] = 0x00; pkt[33] = 0x01
            val qBytes = qname.toByteArray()
            System.arraycopy(qBytes, 0, pkt, 40, qBytes.size)
            val tail = 40 + qBytes.size
            pkt[tail + 1] = 1 // QTYPE A
            pkt[tail + 3] = 1 // QCLASS IN
            return pkt
        }

        // 1. System-wide DNS filter blocks adult domains (NXDOMAIN) and allows search engines
        val adultQueryPkt = buildIpv4UdpDnsQuery("www.pornhub.com")
        val parsedAdult = com.hafij.islamicalarm.restrict.SafeDnsFilter.parseDnsQueryPacket(
            adultQueryPkt,
            adultQueryPkt.size
        )
        assertNotNull(parsedAdult)
        assertEquals("www.pornhub.com", parsedAdult!!.queryDomain)
        assertTrue(com.hafij.islamicalarm.restrict.SafeDnsFilter.isDomainBlocked(parsedAdult.queryDomain))
        val nxPacket = com.hafij.islamicalarm.restrict.SafeDnsFilter.buildBlockedDnsResponsePacket(
            adultQueryPkt,
            parsedAdult
        )
        assertTrue((nxPacket[30].toInt() and 0x80) != 0) // QR = 1 (Response)
        assertEquals(3, nxPacket[31].toInt() and 0x0F)   // RCODE = 3 (NXDOMAIN)

        val googleQueryPkt = buildIpv4UdpDnsQuery("www.google.com")
        val parsedGoogle = com.hafij.islamicalarm.restrict.SafeDnsFilter.parseDnsQueryPacket(
            googleQueryPkt,
            googleQueryPkt.size
        )
        assertNotNull(parsedGoogle)
        assertTrue(!com.hafij.islamicalarm.restrict.SafeDnsFilter.isDomainBlocked(parsedGoogle!!.queryDomain))

        // 2. Search-safe click-through & Incognito block in RestrictAccessibilityService
        val serviceCtrl = Robolectric.buildService(
            com.hafij.islamicalarm.restrict.RestrictAccessibilityService::class.java
        )
        val service = serviceCtrl.create().get()

        // Search results page with adult snippet URL must NOT block before clicking
        val searchRoot = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.FrameLayout"
        }
        val searchUrlBar = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.EditText"
            viewIdResourceName = "com.android.chrome:id/url_bar"
            isFocused = false
            text = "https://www.google.com/search?q=adult+video&safe=active"
        }
        val snippetUrl = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.TextView"
            text = "https://www.pornhub.com"
        }
        org.robolectric.Shadows.shadowOf(searchRoot).addChild(searchUrlBar)
        org.robolectric.Shadows.shadowOf(searchRoot).addChild(snippetUrl)
        assertTrue(!service.shouldTriggerSafeVideoBlock(searchRoot))

        // Clicking through onto the adult website itself MUST trigger Safe Mode
        val clickedSiteNode = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.EditText"
            viewIdResourceName = "com.android.chrome:id/url_bar"
            isFocused = false
            text = "https://www.pornhub.com/watch"
        }
        assertTrue(service.shouldTriggerSafeVideoBlock(clickedSiteNode))

        // Opening Incognito tab in Chrome MUST trigger Incognito block
        val incognitoNode = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.TextView"
            text = "You've gone incognito"
        }
        assertTrue(service.shouldTriggerSafeVideoBlock(incognitoNode))

        searchUrlBar.recycle()
        snippetUrl.recycle()
        searchRoot.recycle()
        clickedSiteNode.recycle()
        incognitoNode.recycle()
        serviceCtrl.destroy()

        // 3. Safe Mode UI, DNS VPN + Incognito switches, and Turn-Off Notice Dialog ("ওকে")
        val mainActivity = Robolectric.buildActivity(MainActivity::class.java)
            .create().start().resume().get()
        mainActivity.findViewById<com.hafij.islamicalarm.MainBottomBar>(
            com.hafij.islamicalarm.R.id.bottomNavigation
        ).selectedItemId = com.hafij.islamicalarm.R.id.nav_alarm

        mainActivity.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.tabSafeMode).performClick()
        val switchSafe = mainActivity.findViewById<androidx.appcompat.widget.SwitchCompat>(
            com.hafij.islamicalarm.R.id.switchSafeMode
        )
        val switchDns = mainActivity.findViewById<androidx.appcompat.widget.SwitchCompat>(
            com.hafij.islamicalarm.R.id.switchSafeDnsVpn
        )
        val switchIncognito = mainActivity.findViewById<androidx.appcompat.widget.SwitchCompat>(
            com.hafij.islamicalarm.R.id.switchIncognitoBlock
        )
        assertNotNull(switchDns)
        assertNotNull(switchIncognito)
        assertTrue(switchIncognito.isChecked)

        // Turn Safe Mode ON then click to turn OFF -> Notice Dialog appears and "ওকে" turns it OFF
        switchSafe.performClick()
        val safeStore = com.hafij.islamicalarm.restrict.SafeModeStore(ctx)
        assertTrue(safeStore.isEnabled)
        switchSafe.performClick()
        val safeOffDialog = org.robolectric.shadows.ShadowAlertDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertTrue(safeOffDialog.isShowing)
        val okSafeBtn = safeOffDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
        assertEquals("ওকে", okSafeBtn.text.toString())
        assertTrue(safeStore.isEnabled) // still ON until "ওকে" clicked
        okSafeBtn.performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(!safeStore.isEnabled)
        assertTrue(!safeStore.isDnsVpnEnabled)

        // 4. Auto Mode Notice Dialog on switch click + "ওকে" button + cancel behavior
        mainActivity.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.tabAlarmMode).performClick()
        val switchAuto = mainActivity.findViewById<androidx.appcompat.widget.SwitchCompat>(
            com.hafij.islamicalarm.R.id.switchAutoAlarm
        )
        switchAuto.performClick()
        val autoDialog = org.robolectric.shadows.ShadowAlertDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertTrue(autoDialog.isShowing)
        assertEquals("ওকে", autoDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).text.toString())
        autoDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(!com.hafij.islamicalarm.auto.AutoAlarmManager.isAutoModeEnabled(ctx))

        // 5. Asr & Isha 15-minute Azan listening windows + vocal detection
        assertEquals(15, com.hafij.islamicalarm.auto.AutoAlarmManager.AZAN_DURATION_MINUTES)
        val district = com.hafij.islamicalarm.prayertimes.LocationData.getDefaultDistrict()
        val nowCal = java.util.Calendar.getInstance()
        val sch = com.hafij.islamicalarm.prayertimes.PrayerTimeCalculator.calculate(nowCal, district)
        fun calAt(ms: Long) = java.util.Calendar.getInstance().apply { timeInMillis = ms }

        assertEquals(
            "asr",
            com.hafij.islamicalarm.auto.AutoAlarmManager.findListeningWaqtWindow(
                calAt(sch.asrCal.timeInMillis + 60_000L),
                district
            )?.waqtKey
        )
        assertEquals(
            "isha",
            com.hafij.islamicalarm.auto.AutoAlarmManager.findListeningWaqtWindow(
                calAt(sch.ishaCal.timeInMillis + 60_000L),
                district
            )?.waqtKey
        )
        assertEquals(
            null,
            com.hafij.islamicalarm.auto.AutoAlarmManager.findListeningWaqtWindow(
                calAt(sch.maghribCal.timeInMillis + 60_000L),
                district
            )
        )
        assertEquals(
            null,
            com.hafij.islamicalarm.auto.AutoAlarmManager.findListeningWaqtWindow(
                calAt(sch.fajrCal.timeInMillis + 60_000L),
                district
            )
        )

        val vocalWave = ShortArray(4000) { i ->
            (3000.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 350.0 * i / 16000.0)).toInt().toShort()
        }
        val silenceWave = ShortArray(4000) { 0 }
        assertTrue(com.hafij.islamicalarm.auto.AzanListenerService.isAzanFrameLikelyVocal(vocalWave, vocalWave.size))
        assertTrue(!com.hafij.islamicalarm.auto.AzanListenerService.isAzanFrameLikelyVocal(silenceWave, silenceWave.size))
    }

    @Test
    fun testCompactBannerLibraryApisSafeModeFixesSilentVpnAndCleanQuran(): Unit = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        // 1. Compact Home banner (~30% reduced)
        val mainActivity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val headerTitle = mainActivity.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvHeaderTitle
        )
        val headerContainer = mainActivity.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.headerArtContainer
        )
        assertTrue(headerTitle.textSize <= 18f * ctx.resources.displayMetrics.scaledDensity + 1f)
        assertTrue(headerContainer.paddingTop <= (10 * ctx.resources.displayMetrics.density).toInt() + 1)

        // 2. Clean Quran "অর্থ সহ" (quran-simple without Uthmani ٱ/۟/ۭ marks + clean Unicode Bengali + @font/amiri)
        val surah1 = QuranRepository.fetchSurahAyahs(ctx, 1)
        assertEquals(7, surah1.size)
        for (ayah in surah1) {
            assertTrue("Must not contain Uthmani Alef Wasla", !ayah.textArabic.contains('\u0671'))
            assertTrue("Must not contain Uthmani small high rounded zero", !ayah.textArabic.contains('\u06DF'))
            assertTrue("Must not contain Uthmani small low meem", !ayah.textArabic.contains('\u06ED'))
            assertTrue("Must not contain broken Bijoy glyph", !ayah.textBangla.contains("ো"))
        }

        // 3. Safe Mode: Chrome normal page (aistudio.google.com) with SurfaceView & stray text MUST NOT trigger,
        //    and Chrome 3-dot overflow menu MUST NOT trigger Incognito Block, while real Incognito tab DOES.
        val serviceCtrl = Robolectric.buildService(
            com.hafij.islamicalarm.restrict.RestrictAccessibilityService::class.java
        )
        val service = serviceCtrl.create().get()

        val chromeRoot = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.FrameLayout"
        }
        val aiStudioUrlBar = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.EditText"
            viewIdResourceName = "com.android.chrome:id/url_bar"
            isFocused = false
            text = "aistudio.google.com/apps/drive/123"
        }
        val chromeSurface = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.view.SurfaceView"
        }
        val pageBodyMention = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.TextView"
            text = "Safe Mode fixes adult blocking and fullscreen video player false positives"
        }
        org.robolectric.Shadows.shadowOf(chromeRoot).addChild(aiStudioUrlBar)
        org.robolectric.Shadows.shadowOf(chromeRoot).addChild(chromeSurface)
        org.robolectric.Shadows.shadowOf(chromeRoot).addChild(pageBodyMention)
        assertTrue(!service.shouldTriggerSafeVideoBlock(chromeRoot))

        // Chrome 3-dot overflow menu popup MUST NOT trigger Incognito Block
        val menuRoot = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.ListView"
        }
        val menuNewTab = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.TextView"
            viewIdResourceName = "com.android.chrome:id/menu_item_text"
            text = "New tab"
        }
        val menuNewIncognito = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.TextView"
            viewIdResourceName = "com.android.chrome:id/menu_item_text"
            text = "New Incognito tab"
        }
        val menuBookmarks = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.TextView"
            viewIdResourceName = "com.android.chrome:id/menu_item_text"
            text = "Bookmarks"
        }
        org.robolectric.Shadows.shadowOf(menuRoot).addChild(menuNewTab)
        org.robolectric.Shadows.shadowOf(menuRoot).addChild(menuNewIncognito)
        org.robolectric.Shadows.shadowOf(menuRoot).addChild(menuBookmarks)
        assertTrue(!service.shouldTriggerSafeVideoBlock(menuRoot))

        // Real Incognito tab view MUST still trigger Incognito Block
        val realIncognito = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.chrome"
            className = "android.widget.TextView"
            text = "You've gone incognito"
        }
        assertTrue(service.shouldTriggerSafeVideoBlock(realIncognito))

        aiStudioUrlBar.recycle()
        chromeSurface.recycle()
        pageBodyMention.recycle()
        chromeRoot.recycle()
        menuNewTab.recycle()
        menuNewIncognito.recycle()
        menuBookmarks.recycle()
        menuRoot.recycle()
        realIncognito.recycle()
        serviceCtrl.destroy()

        // 4. SafeDnsVpnService silent start posts NO app foreground notification
        val safeStore = com.hafij.islamicalarm.restrict.SafeModeStore(ctx)
        safeStore.isEnabled = true
        safeStore.isDnsVpnEnabled = true
        val silentVpnIntent = Intent(ctx, com.hafij.islamicalarm.restrict.SafeDnsVpnService::class.java).apply {
            action = com.hafij.islamicalarm.restrict.SafeDnsVpnService.ACTION_START
            putExtra(com.hafij.islamicalarm.restrict.SafeDnsVpnService.EXTRA_FOREGROUND_REQUIRED, false)
        }
        val vpnSvcCtrl = Robolectric.buildService(
            com.hafij.islamicalarm.restrict.SafeDnsVpnService::class.java,
            silentVpnIntent
        ).create().startCommand(0, 1)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val postedNotifs = org.robolectric.Shadows.shadowOf(nm).allNotifications
        assertTrue(postedNotifs.none { it.extras.getString(android.app.Notification.EXTRA_TITLE)?.contains("সেফ DNS") == true })
        vpnSvcCtrl.destroy()

        // 5. Kitab & Library multi-source APIs (Stories, Sahaba/History, Seerah/Tafsir, Qawmi, OpenLibrary, Islamic.app) & UI
        val azharmeaSample = """
            {"result":[
              {"name":"Sat Juboker Golpo.pdf"},
              {"name":"Hayatus Sahabah Vol 1.pdf"},
              {"name":"Arrahikulmakhtum Bangla.pdf"},
              {"name":"1.pdf"}
            ]}
        """.trimIndent()
        val parsedBanglaArchive = com.hafij.islamicalarm.books.LibraryApiClient.parseArchiveCollectionFilesJson(
            identifier = "azharmea-www.eelm.weebly.com-skype-id-azharmea",
            jsonStr = azharmeaSample,
            defaultJamatCode = com.hafij.islamicalarm.books.RemoteLibraryBook.JAMAT_GENERAL_BANGLA,
            sourceLabel = "Bangla Islamic Archive",
            smartClassify = true
        )
        assertEquals(3, parsedBanglaArchive.size)
        assertEquals(com.hafij.islamicalarm.books.RemoteLibraryBook.CAT_STORIES, parsedBanglaArchive[0].jamatCode)
        assertTrue(parsedBanglaArchive[0].titleBn.contains("সাত যুবকের গল্প"))
        assertEquals(com.hafij.islamicalarm.books.RemoteLibraryBook.CAT_SAHABA_HISTORY, parsedBanglaArchive[1].jamatCode)
        assertEquals(com.hafij.islamicalarm.books.RemoteLibraryBook.CAT_SEERAH_TAFSIR, parsedBanglaArchive[2].jamatCode)

        val openLibSample = """
            {"docs":[
              {"title":"Stories of the Prophets","ia":["storiesofprophet00ibnk"],"author_name":["Ibn Kathir"]},
              {"title":"Book without IA","author_name":["Unknown"]}
            ]}
        """.trimIndent()
        val parsedOpenLib = com.hafij.islamicalarm.books.LibraryApiClient.parseOpenLibraryDocs(openLibSample)
        assertEquals(1, parsedOpenLib.size)
        assertEquals("storiesofprophet00ibnk", parsedOpenLib[0].iaIdentifier)

        val kawmiSample = """
            {"result":[
              {"name":"D-Sahih-Al-Bukhari-1.pdf"},
              {"name":"T-Beheshte-Jewor.pdf"},
              {"name":"K-Kuduri.pdf"},
              {"name":"D-Sahih-Al-Bukhari-1_text.pdf"}
            ]}
        """.trimIndent()
        val parsedKawmi = com.hafij.islamicalarm.books.LibraryApiClient.parseArchiveKawmiFilesJson(kawmiSample)
        assertEquals(3, parsedKawmi.size)
        assertEquals(com.hafij.islamicalarm.books.RemoteLibraryBook.JAMAT_DAWRA, parsedKawmi[0].jamatCode)
        assertEquals(com.hafij.islamicalarm.books.RemoteLibraryBook.JAMAT_TAISIR, parsedKawmi[1].jamatCode)
        assertEquals(com.hafij.islamicalarm.books.RemoteLibraryBook.JAMAT_KAFIYA, parsedKawmi[2].jamatCode)
        assertTrue(parsedKawmi[1].titleBn.contains("বেহেশতী জেওর"))

        val searchSample = """
            {"response":{"docs":[
              {"identifier":"tafsir-jalalain-bn","title":"Tafsir Jalalain Bangla","creator":"Maulana"}
            ]}}
        """.trimIndent()
        val parsedSearchDocs = com.hafij.islamicalarm.books.LibraryApiClient.parseArchiveSearchDocs(searchSample)
        assertEquals(1, parsedSearchDocs.size)
        assertEquals("tafsir-jalalain-bn", parsedSearchDocs[0].identifier)

        val islamicAppSample = """
            {"data":{"books":[
              {"slug":"riyad-salihin","has_pdf":true,"title_ar":"رياض الصالحين","title_en":"Riyad as-Salihin","title_i18n":{"bn":"রিয়াদুস সালেহীন"},"pages":520}
            ]}}
        """.trimIndent()
        val parsedClassical = com.hafij.islamicalarm.books.LibraryApiClient.parseIslamicAppBooksJson(islamicAppSample)
        assertEquals(1, parsedClassical.size)
        assertEquals("রিয়াদুস সালেহীন", parsedClassical[0].titleBn)
        assertEquals(com.hafij.islamicalarm.books.RemoteLibraryBook.JAMAT_CLASSICAL_AR, parsedClassical[0].jamatCode)

        // Ensure zero "বেফাক" references in JamatCatalog or Activity header
        assertTrue(
            com.hafij.islamicalarm.books.JamatCatalog.allCategories.none {
                it.titleBn.contains("বেফাক") || it.subtitleBn.contains("বেফাক")
            }
        )

        // Populate catalog cache & verify IslamicLibraryActivity 2-col category grid -> 3-col book shelf & 5-tab bar
        com.hafij.islamicalarm.books.LibraryPrefsStore(ctx).saveCatalogCache(
            parsedBanglaArchive + parsedKawmi + parsedClassical
        )
        val libCtrl = Robolectric.buildActivity(
            com.hafij.islamicalarm.books.IslamicLibraryActivity::class.java
        ).create().start().resume()
        val libAct = libCtrl.get()
        val headerLibraryTitle = libAct.findViewById<android.widget.TextView>(
            com.hafij.islamicalarm.R.id.tvLibraryHeaderTitle
        )
        assertEquals("ইসলামিক ও কিতাব লাইব্রেরী", headerLibraryTitle.text.toString())
        assertEquals(0, ctx.resources.getIdentifier("cardLibNavHome", "id", ctx.packageName))

        val rvJamat = libAct.findViewById<androidx.recyclerview.widget.RecyclerView>(
            com.hafij.islamicalarm.R.id.rvJamatCategories
        )
        val rvBooks = libAct.findViewById<androidx.recyclerview.widget.RecyclerView>(
            com.hafij.islamicalarm.R.id.rvRemoteBooks
        )
        val layoutInfo = libAct.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.layoutLibraryInfo
        )
        assertEquals(android.view.View.VISIBLE, rvJamat.visibility)
        assertEquals(2, (rvJamat.layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanCount)
        assertEquals(3, (rvBooks.layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanCount)
        assertEquals(com.hafij.islamicalarm.books.JamatCatalog.allCategories.size, rvJamat.adapter?.itemCount)

        // Measure/layout Category RecyclerView & click first category ("ইসলামিক গল্প ও কাহিনি")
        rvJamat.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
        )
        rvJamat.layout(0, 0, 1080, 1920)
        rvJamat.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals(android.view.View.GONE, rvJamat.visibility)
        assertEquals(android.view.View.VISIBLE, rvBooks.visibility)
        assertEquals(1, rvBooks.adapter?.itemCount)

        // Switch to Info tab, then Back to Home tab & verify "জামাত: দাওরা" at position 4
        libAct.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.btnLibNavInfo).performClick()
        assertEquals(android.view.View.VISIBLE, layoutInfo.visibility)
        libAct.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.btnLibNavHome).performClick()
        assertEquals(android.view.View.VISIBLE, rvJamat.visibility)
        rvJamat.findViewHolderForAdapterPosition(4)!!.itemView.performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(1, rvBooks.adapter?.itemCount)

        libCtrl.pause().stop().destroy()
    }

    @Test
    fun testExactGpsBangladeshLocationsMushafFullPageSettingsShareAndAmalExpansion() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        // 1. All 8 Divisions, all 64 Districts, and every District has Upazilas/Thanas
        val divisions = com.hafij.islamicalarm.prayertimes.LocationData.divisions
        val districts = com.hafij.islamicalarm.prayertimes.LocationData.districts
        val thanas = com.hafij.islamicalarm.prayertimes.LocationData.thanas
        assertEquals(8, divisions.size)
        assertEquals(64, districts.size)
        assertTrue("Expected full Bangladesh thanas/upazilas >= 490, found ${thanas.size}", thanas.size >= 490)
        val districtNames = districts.map { it.nameBn }.toSet()
        for (d in districts) {
            assertTrue(
                "District ${d.nameBn} missing thanas/upazilas",
                thanas.any { it.parentBn == d.nameBn }
            )
        }
        assertTrue(thanas.all { it.parentBn in districtNames })

        // 2. LocationHelper exact-spot extraction (Nominatim zoom=18 + Geocoder + freshness check)
        val nominatimSample = """
            {
              "name": "বাড্ডা জামে মসজিদ",
              "address": {
                "road": "স্বাধীনতা সরণি",
                "neighbourhood": "উত্তর বাড্ডা",
                "suburb": "বাড্ডা থানা",
                "city": "ঢাকা",
                "state": "ঢাকা বিভাগ",
                "country": "বাংলাদেশ"
              }
            }
        """.trimIndent()
        val parsedSpot = com.hafij.islamicalarm.prayertimes.LocationHelper.extractExactPlaceFromNominatimJson(nominatimSample)
        assertNotNull(parsedSpot)
        assertEquals("উত্তর বাড্ডা", parsedSpot!!.spotBn)
        assertEquals("ঢাকা", parsedSpot.parentBn)

        val customGps = com.hafij.islamicalarm.prayertimes.District(
            nameEn = "Uttar Badda",
            nameBn = "উত্তর বাড্ডা",
            divisionBn = "ঢাকা",
            lat = 23.7806,
            lng = 90.4267,
            type = com.hafij.islamicalarm.prayertimes.LocationType.CUSTOM_GPS,
            parentBn = "ঢাকা"
        )
        assertEquals("📍 উত্তর বাড্ডা, ঢাকা", customGps.displayName)

        val addr = android.location.Address(java.util.Locale("bn", "BD")).apply {
            thoroughfare = "কলেজ রোড"
            subLocality = "ধানমন্ডি"
            locality = "ঢাকা"
            subAdminArea = "ঢাকা"
            countryName = "বাংলাদেশ"
        }
        val parsedGeo = com.hafij.islamicalarm.prayertimes.LocationHelper.extractExactPlaceFromAddresses(listOf(addr))
        assertNotNull(parsedGeo)
        assertEquals("কলেজ রোড", parsedGeo!!.spotBn)

        val staleLoc = android.location.Location("gps").apply {
            time = System.currentTimeMillis() - 10 * 60 * 1000L
            accuracy = 15f
        }
        val coarseLoc = android.location.Location("network").apply {
            time = System.currentTimeMillis()
            accuracy = 180f
        }
        val freshExactLoc = android.location.Location("gps").apply {
            time = System.currentTimeMillis()
            accuracy = 12f
        }
        assertTrue(!com.hafij.islamicalarm.prayertimes.LocationHelper.isRecentAndPrecise(staleLoc))
        assertTrue(!com.hafij.islamicalarm.prayertimes.LocationHelper.isRecentAndPrecise(coarseLoc))
        assertTrue(com.hafij.islamicalarm.prayertimes.LocationHelper.isRecentAndPrecise(freshExactLoc))

        // 3. Mushaf Page 1, Page 2, Page 610 full-page rendering (no mid-page blank cut)
        val offlineActCtrl = Robolectric.buildActivity(
            com.hafij.islamicalarm.quran.OfflineArabicQuranActivity::class.java
        ).create().start().resume()
        val offlineAct = offlineActCtrl.get()
        val pager = offlineAct.findViewById<androidx.viewpager2.widget.ViewPager2>(
            com.hafij.islamicalarm.R.id.vpMushafPages
        )
        val pagerRecycler = pager.getChildAt(0) as androidx.recyclerview.widget.RecyclerView
        fun bindAndMeasurePage(pageOneBased: Int): android.widget.LinearLayout {
            val vh = pager.adapter!!.createViewHolder(pagerRecycler, 0)
            @Suppress("UNCHECKED_CAST")
            (pager.adapter as androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>)
                .onBindViewHolder(vh, pageOneBased - 1)
            vh.itemView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
            )
            vh.itemView.layout(0, 0, 1080, 1920)
            return vh.itemView.findViewById(com.hafij.islamicalarm.R.id.llMushafPageContent)
        }
        fun collectTexts(v: android.view.View): List<String> = when (v) {
            is android.widget.TextView -> listOf(v.text.toString())
            is android.view.ViewGroup -> (0 until v.childCount).flatMap { collectTexts(v.getChildAt(it)) }
            else -> emptyList()
        }

        val page1Container = bindAndMeasurePage(1)
        val page2Container = bindAndMeasurePage(2)
        val page610Container = bindAndMeasurePage(610)
        // Page 1 & 2 fill the frame with positive row heights and no empty spacer dead weight
        assertTrue(page1Container.height > 1000)
        assertTrue(page2Container.height > 1000)
        // Page 610 collapses 6 trailing BlankLines into a single Khatam-ul-Quran closing panel
        val page610Texts = collectTexts(page610Container).joinToString("\n")
        assertTrue(page610Texts.contains("خَتْمُ الْقُرْآنِ الْكَرِيمِ"))
        assertTrue(page610Texts.contains("আল-কুরআনুল কারীম সমাপ্ত"))
        offlineActCtrl.pause().stop().destroy()

        // 4. Settings Share button + Dynamic Admin JSON link update
        val mainCtrl = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val mainAct = mainCtrl.get()
        val navBar = mainAct.findViewById<com.hafij.islamicalarm.MainBottomBar>(
            com.hafij.islamicalarm.R.id.bottomNavigation
        )
        navBar.selectedItemId = com.hafij.islamicalarm.R.id.nav_settings
        mainAct.supportFragmentManager.executePendingTransactions()

        val shareBtn = mainAct.findViewById<android.view.View>(com.hafij.islamicalarm.R.id.btnSettingsShareApp)
        assertEquals(android.view.View.VISIBLE, shareBtn.visibility)
        val adminJson = """
            {
              "share_url": "https://islamic-alarm.example.com/download-v2",
              "share_title": "ইসলামিক এলার্ম আপডেট",
              "share_message": "অ্যাডমিন প্যানেল থেকে যুক্ত নতুন লিংক:"
            }
        """.trimIndent()
        assertTrue(com.hafij.islamicalarm.settings.AppShareConfigStore.applyRemoteConfigJson(ctx, adminJson))
        val shareText = com.hafij.islamicalarm.settings.AppShareConfigStore.buildShareText(ctx)
        assertTrue(shareText.contains("https://islamic-alarm.example.com/download-v2"))
        assertTrue(shareText.contains("অ্যাডমিন প্যানেল থেকে যুক্ত নতুন লিংক:"))

        // 5. Bottom Bar "আমল" label + Every Dua/Amal category populated & expanded
        val navTexts = collectTexts(navBar)
        assertTrue(navTexts.contains("আমল"))
        assertTrue(navTexts.none { it == "দোয়া" || it == "দোয়া" })

        val allAmalItems = com.hafij.islamicalarm.duas.DuaData.getDuasAndHadiths
        val categories = com.hafij.islamicalarm.duas.DuaData.getCategories
        for (cat in categories) {
            val count = allAmalItems.count { it.category == cat }
            assertTrue("Category '$cat' should have multiple items, found $count", count >= 2)
        }

        mainCtrl.pause().stop().destroy()
    }
}
