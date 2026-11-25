package com.hafij.islamicalarm.books

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hafij.islamicalarm.databinding.ActivityPdfReaderBinding
import kotlinx.coroutines.launch
import java.io.File

class PdfReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfReaderBinding
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var currentPageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra(EXTRA_PDF_TITLE) ?: "কিতাব"
        val filePath = intent.getStringExtra(EXTRA_PDF_PATH)
        val pdfUrl = intent.getStringExtra(EXTRA_PDF_URL)
        val bookSlug = intent.getStringExtra(EXTRA_PDF_SLUG) ?: title.hashCode().toString()

        binding.toolbarPdfReader.title = title
        binding.toolbarPdfReader.setNavigationOnClickListener { finish() }

        binding.btnPdfPrevPage.setOnClickListener {
            if (currentPageIndex > 0) showPage(currentPageIndex - 1)
        }
        binding.btnPdfNextPage.setOnClickListener {
            val total = pdfRenderer?.pageCount ?: 0
            if (currentPageIndex < total - 1) showPage(currentPageIndex + 1)
        }

        val localFile = filePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
        if (localFile != null) {
            openLocalPdf(localFile)
        } else if (!pdfUrl.isNullOrBlank()) {
            loadOnlinePdf(title, bookSlug, pdfUrl)
        } else {
            Toast.makeText(this, "PDF ফাইলটি খুঁজে পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadOnlinePdf(title: String, slug: String, pdfUrl: String) {
        binding.layoutPdfLoading.visibility = View.VISIBLE
        binding.tvPdfLoadingStatus.text = "অনলাইন থেকে কিতাব লোড হচ্ছে..."
        binding.btnPdfPrevPage.isEnabled = false
        binding.btnPdfNextPage.isEnabled = false

        val onlineBook = RemoteLibraryBook(
            slug = slug,
            titleAr = "",
            titleBn = title,
            titleEn = title,
            authorNameBn = "",
            authorNameEn = "",
            genre = "",
            pages = 0,
            hasPdf = true,
            pdfUrl = pdfUrl
        )

        lifecycleScope.launch {
            val downloadedCache = LibraryApiClient.downloadBookPdf(
                context = this@PdfReaderActivity,
                book = onlineBook,
                toCacheOnly = true
            )
            binding.layoutPdfLoading.visibility = View.GONE

            if (downloadedCache != null && downloadedCache.exists()) {
                openLocalPdf(downloadedCache)
            } else {
                Toast.makeText(
                    this@PdfReaderActivity,
                    "অনলাইন থেকে PDF লোড করা যায়নি, ইন্টারনেট সংযোগ চেক করুন",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun openLocalPdf(file: File) {
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            showPage(0)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "PDF খুলতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (index < 0 || index >= renderer.pageCount) return

        val page = renderer.openPage(index)
        val scale = if (page.width in 1..700) 2 else 1
        val width = (page.width * scale).coerceAtMost(1600).coerceAtLeast(1)
        val height = (page.height * scale).coerceAtMost(2400).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        binding.ivPdfPage.setImageBitmap(bitmap)
        currentPageIndex = index
        binding.tvPdfPageIndicator.text = "পৃষ্ঠা ${index + 1} / ${renderer.pageCount}"

        binding.btnPdfPrevPage.isEnabled = index > 0
        binding.btnPdfNextPage.isEnabled = index < renderer.pageCount - 1
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val EXTRA_PDF_PATH = "EXTRA_PDF_PATH"
        const val EXTRA_PDF_TITLE = "EXTRA_PDF_TITLE"
        const val EXTRA_PDF_URL = "EXTRA_PDF_URL"
        const val EXTRA_PDF_SLUG = "EXTRA_PDF_SLUG"
    }
}
