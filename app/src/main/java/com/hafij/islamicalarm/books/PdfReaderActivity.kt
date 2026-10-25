package com.hafij.islamicalarm.books

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hafij.islamicalarm.databinding.ActivityPdfReaderBinding
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

        val title = intent.getStringExtra("EXTRA_PDF_TITLE") ?: "কিতাব"
        val filePath = intent.getStringExtra("EXTRA_PDF_PATH")

        binding.toolbarPdfReader.title = title
        binding.toolbarPdfReader.setNavigationOnClickListener { finish() }

        if (filePath == null || !File(filePath).exists()) {
            Toast.makeText(this, "PDF ফাইলটি খুঁজে পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            fileDescriptor = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            showPage(0)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "PDF খুলতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnPdfPrevPage.setOnClickListener {
            if (currentPageIndex > 0) showPage(currentPageIndex - 1)
        }
        binding.btnPdfNextPage.setOnClickListener {
            val total = pdfRenderer?.pageCount ?: 0
            if (currentPageIndex < total - 1) showPage(currentPageIndex + 1)
        }
    }

    private fun showPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (index < 0 || index >= renderer.pageCount) return

        val page = renderer.openPage(index)
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
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
}
