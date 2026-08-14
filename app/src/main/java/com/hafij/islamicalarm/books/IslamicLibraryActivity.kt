package com.hafij.islamicalarm.books

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityIslamicLibraryBinding
import java.util.Locale

class IslamicLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIslamicLibraryBinding
    private lateinit var bookAdapter: BookAdapter
    private var selectedCategory = BookData.CAT_ALL
    private var searchQuery = ""

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
}
