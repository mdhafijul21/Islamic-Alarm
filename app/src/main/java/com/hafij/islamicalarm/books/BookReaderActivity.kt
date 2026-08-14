package com.hafij.islamicalarm.books

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.hafij.islamicalarm.databinding.ActivityBookReaderBinding
import java.util.Locale

class BookReaderActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityBookReaderBinding
    private lateinit var chapterAdapter: ChapterAdapter
    private var currentBook: BookItem? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var currentFontDelta = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bookId = intent.getStringExtra("EXTRA_BOOK_ID")
        currentBook = BookData.allBooks.find { it.id == bookId } ?: BookData.allBooks.firstOrNull()

        if (currentBook == null) {
            Toast.makeText(this, "কিতাবের তথ্য পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        setupFontControls()
        setupOnlineSearch()

        tts = TextToSpeech(this, this)
    }

    private fun setupToolbar() {
        binding.toolbarReader.title = currentBook?.titleBn
        binding.toolbarReader.subtitle = "লেখক: ${currentBook?.authorBn}"
        binding.toolbarReader.setNavigationOnClickListener {
            finish()
        }
        binding.tvReaderTotalChapters.text = "📖 মোট ${currentBook?.chapters?.size ?: 0}টি নির্বাচিত অধ্যায়"
    }

    private fun setupRecyclerView() {
        chapterAdapter = ChapterAdapter(
            chapters = currentBook?.chapters ?: emptyList(),
            onTtsPlay = { textToSpeak ->
                speakText(textToSpeak)
            }
        )
        binding.rvBookChapters.layoutManager = LinearLayoutManager(this)
        binding.rvBookChapters.adapter = chapterAdapter
    }

    private fun setupFontControls() {
        binding.btnFontLarger.setOnClickListener {
            if (currentFontDelta < 8f) {
                currentFontDelta += 2f
                chapterAdapter.fontScaleDelta = currentFontDelta
                chapterAdapter.notifyDataSetChanged()
                binding.tvFontSizeIndicator.text = "A+"
            }
        }

        binding.btnFontSmaller.setOnClickListener {
            if (currentFontDelta > -4f) {
                currentFontDelta -= 2f
                chapterAdapter.fontScaleDelta = currentFontDelta
                chapterAdapter.notifyDataSetChanged()
                binding.tvFontSizeIndicator.text = if (currentFontDelta == 0f) "A" else "A-"
            }
        }
    }

    private fun setupOnlineSearch() {
        binding.btnOnlineReadMore.setOnClickListener {
            val query = currentBook?.onlineSearchQuery?.ifBlank { currentBook?.titleBn } ?: "islamic bangla book hadith"
            val encodedQuery = Uri.encode(query)
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encodedQuery"))
            try {
                startActivity(webIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "ব্রাউজার খোলা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun speakText(text: String) {
        if (!isTtsReady || tts == null) {
            Toast.makeText(this, "ভয়েস রিডার প্রস্তুত হচ্ছে...", Toast.LENGTH_SHORT).show()
            return
        }

        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "BOOK_READER_TTS")
        Toast.makeText(this, "অডিও পাঠ চালু হয়েছে...", Toast.LENGTH_SHORT).show()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("bn", "BD"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.ENGLISH)
            }
            isTtsReady = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
