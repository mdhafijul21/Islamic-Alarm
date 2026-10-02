package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.hafij.islamicalarm.books.BookData
import com.hafij.islamicalarm.books.BookReaderActivity
import org.junit.Assert.assertNotNull
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
  fun testQuranActivity() {
    val controller = Robolectric.buildActivity(com.hafij.islamicalarm.quran.QuranActivity::class.java)
    controller.create().start().resume()
    assertNotNull(controller.get())
  }

  @Test
  fun testSurahDetailActivity() {
    val intent = Intent(ApplicationProvider.getApplicationContext(), com.hafij.islamicalarm.quran.SurahDetailActivity::class.java).apply {
      putExtra("EXTRA_SURAH_ID", 1)
    }
    val controller = Robolectric.buildActivity(com.hafij.islamicalarm.quran.SurahDetailActivity::class.java, intent)
    controller.create().start().resume()
    assertNotNull(controller.get())
  }
}
