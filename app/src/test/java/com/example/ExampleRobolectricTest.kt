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
            Triple(OfflineArabicQuranActivity.TAB_PAGE, "পৃষ্ঠা সার্চ", android.view.View.VISIBLE)
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
            com.hafij.islamicalarm.permissions.PermissionHelper.screenPinStep +
            com.hafij.islamicalarm.permissions.PermissionHelper.microphoneStep
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

        // 3. Time-based Azan listening window identifies Fajr/Maghrib by schedule
        val district = com.hafij.islamicalarm.prayertimes.LocationData.getDefaultDistrict()
        val sch = com.hafij.islamicalarm.prayertimes.PrayerTimeCalculator.calculate(thuCal, district)
        val maghribWindow = com.hafij.islamicalarm.auto.AutoAlarmManager.findListeningWaqtWindow(
            sch.maghribCal,
            district
        )
        assertNotNull(maghribWindow)
        assertEquals("maghrib", maghribWindow!!.waqtKey)

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

        // 5. Auto alarm button exists on MainActivity Alarm screen
        val mainController = Robolectric.buildActivity(MainActivity::class.java)
        val mainActivity = mainController.create().start().resume().get()
        val autoBtn = mainActivity.findViewById<android.view.View>(
            com.hafij.islamicalarm.R.id.btnAutoAlarm
        )
        assertNotNull(autoBtn)
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
        assertTrue(hintView.text.toString().contains("সঠিক সময়ের জন্য লোকেশন বাটন ক্লিক করুন"))

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
}
