package com.hafij.islamicalarm.books

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityIslamicLibraryBinding
import kotlinx.coroutines.launch
import java.util.Locale

class IslamicLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIslamicLibraryBinding
    private lateinit var bookAdapter: BookAdapter
    private lateinit var remoteAdapter: RemoteLibraryAdapter
    private var selectedCategory = BookData.CAT_ALL
    private var searchQuery = ""

    private var remoteBooks: List<RemoteLibraryBook> = emptyList()
    private var remoteBooksLoaded = false
    private val downloadingSlugs = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIslamicLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarLibrary.setNavigationOnClickListener {
            finish()
        }

        setupRecyclerView()
        setupCategories()
        setupSearch()
        filterBooks()

        setupRemoteLibrary()
        setupTabs()
    }

    private fun setupRecyclerView() {
        bookAdapter = BookAdapter(BookData.allBooks) { selectedBook ->
            val intent = Intent(this, BookReaderActivity::class.java).apply {
                putExtra("EXTRA_BOOK_ID", selectedBook.id)
            }
            startActivity(intent)
        }
        binding.rvBooks.layoutManager = LinearLayoutManager(this)
        binding.rvBooks.adapter = bookAdapter
    }

    private fun setupCategories() {
        BookData.categories.forEachIndexed { index, cat ->
            val chip = Chip(this).apply {
                text = cat
                isCheckable = true
                isChecked = (index == 0)
                setChipBackgroundColorResource(R.color.surface)
                setTextColor(resources.getColor(R.color.text_primary, theme))
            }
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedCategory = cat
                    filterBooks()
                }
            }
            binding.chipGroupBookCategories.addView(chip)
        }
    }

    private fun setupSearch() {
        binding.etSearchBooks.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString().trim()
                filterBooks()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnOnlineSearchLibrary.setOnClickListener {
            val query = if (searchQuery.isNotBlank()) {
                "$searchQuery ইসলাম কিতাব হাদিস bangla"
            } else {
                "সহিহ হাদিস কিতাব ও ইসলামিক বই pdf bangla"
            }
            val encodedQuery = Uri.encode(query)
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encodedQuery"))
            try {
                startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "ব্রাউজার খোলা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterBooks() {
        val allList = BookData.allBooks
        val queryLower = searchQuery.lowercase(Locale.ROOT)

        val filtered = allList.filter { book ->
            val matchesCategory = (selectedCategory == BookData.CAT_ALL || book.category == selectedCategory)
            val matchesSearch = queryLower.isEmpty() ||
                    book.titleBn.lowercase(Locale.ROOT).contains(queryLower) ||
                    book.titleAr.lowercase(Locale.ROOT).contains(queryLower) ||
                    book.authorBn.lowercase(Locale.ROOT).contains(queryLower) ||
                    book.summaryBn.lowercase(Locale.ROOT).contains(queryLower) ||
                    book.chapters.any { ch ->
                        ch.titleBn.lowercase(Locale.ROOT).contains(queryLower) ||
                        ch.contentBn.lowercase(Locale.ROOT).contains(queryLower) ||
                        ch.explanationBn.lowercase(Locale.ROOT).contains(queryLower) ||
                        ch.reference.lowercase(Locale.ROOT).contains(queryLower)
                    }

            matchesCategory && matchesSearch
        }

        bookAdapter.updateList(filtered)
    }

    private fun setupTabs() {
        binding.btnTabLocalBooks.setOnClickListener { showLocalTab() }
        binding.btnTabOnlineLibrary.setOnClickListener { showOnlineTab() }
    }

    private fun showLocalTab() {
        binding.btnTabLocalBooks.setBackgroundColor(resources.getColor(R.color.secondary, theme))
        binding.btnTabLocalBooks.setTextColor(resources.getColor(R.color.primary, theme))
        binding.btnTabOnlineLibrary.setBackgroundColor(resources.getColor(R.color.surface_container, theme))
        binding.btnTabOnlineLibrary.setTextColor(resources.getColor(R.color.text_primary, theme))

        binding.rvBooks.visibility = View.VISIBLE
        binding.rvRemoteBooks.visibility = View.GONE
        binding.pbRemoteBooksLoading.visibility = View.GONE
    }

    private fun showOnlineTab() {
        binding.btnTabOnlineLibrary.setBackgroundColor(resources.getColor(R.color.secondary, theme))
        binding.btnTabOnlineLibrary.setTextColor(resources.getColor(R.color.primary, theme))
        binding.btnTabLocalBooks.setBackgroundColor(resources.getColor(R.color.surface_container, theme))
        binding.btnTabLocalBooks.setTextColor(resources.getColor(R.color.text_primary, theme))

        binding.rvBooks.visibility = View.GONE
        binding.rvRemoteBooks.visibility = View.VISIBLE

        if (!remoteBooksLoaded) {
            loadRemoteCatalog()
        }
    }

    private fun setupRemoteLibrary() {
        remoteAdapter = RemoteLibraryAdapter(
            books = emptyList(),
            isDownloaded = { it.isDownloaded(this) },
            isDownloading = { downloadingSlugs.contains(it.slug) },
            onDownloadClick = { book -> downloadBook(book) },
            onOpenClick = { book -> openPdf(book) }
        )
        binding.rvRemoteBooks.layoutManager = LinearLayoutManager(this)
        binding.rvRemoteBooks.adapter = remoteAdapter
    }

    private fun loadRemoteCatalog() {
        binding.pbRemoteBooksLoading.visibility = View.VISIBLE
        binding.rvRemoteBooks.visibility = View.GONE

        lifecycleScope.launch {
            val books = LibraryApiClient.fetchBooks(limit = 40)
            remoteBooks = books
            remoteBooksLoaded = true
            remoteAdapter.updateBooks(books)

            binding.pbRemoteBooksLoading.visibility = View.GONE
            binding.rvRemoteBooks.visibility = View.VISIBLE

            if (books.isEmpty()) {
                Toast.makeText(
                    this@IslamicLibraryActivity,
                    "লাইব্রেরি থেকে বই আনা যায়নি, ইন্টারনেট সংযোগ চেক করুন",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun downloadBook(book: RemoteLibraryBook) {
        downloadingSlugs.add(book.slug)
        remoteAdapter.updateBooks(remoteBooks)

        lifecycleScope.launch {
            val success = LibraryApiClient.downloadBookPdf(this@IslamicLibraryActivity, book)
            downloadingSlugs.remove(book.slug)
            remoteAdapter.updateBooks(remoteBooks)

            if (success) {
                Toast.makeText(this@IslamicLibraryActivity, "ডাউনলোড সম্পন্ন — এখন অফলাইনে পড়া যাবে", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@IslamicLibraryActivity, "ডাউনলোড ব্যর্থ হয়েছে, আবার চেষ্টা করুন", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openPdf(book: RemoteLibraryBook) {
        val file = book.localFile(this)
        if (!file.exists()) {
            Toast.makeText(this, "ফাইলটি খুঁজে পাওয়া যায়নি, আবার ডাউনলোড করুন", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, PdfReaderActivity::class.java).apply {
            putExtra("EXTRA_PDF_PATH", file.absolutePath)
            putExtra("EXTRA_PDF_TITLE", book.titleBn.ifBlank { book.titleEn })
        }
        startActivity(intent)
    }
}
