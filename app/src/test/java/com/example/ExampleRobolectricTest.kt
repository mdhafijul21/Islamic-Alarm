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
}
