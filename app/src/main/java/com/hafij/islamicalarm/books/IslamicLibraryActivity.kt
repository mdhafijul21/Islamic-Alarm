package com.hafij.islamicalarm.books

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityIslamicLibraryBinding
import kotlinx.coroutines.launch
import java.util.Locale

class IslamicLibraryActivity : AppCompatActivity() {

    private enum class LibraryViewMode {
        HOME_CATEGORIES,
        JAMAT_BOOKS,
        SEARCH_RESULTS,
        DOWNLOADS,
        INFO,
        BOOKMARKS,
        FAVORITES
    }

    private lateinit var binding: ActivityIslamicLibraryBinding
    private lateinit var prefsStore: LibraryPrefsStore
    private lateinit var jamatAdapter: JamatCategoryAdapter
    private lateinit var booksGridAdapter: RemoteLibraryAdapter

    private var currentMode = LibraryViewMode.HOME_CATEGORIES
    private var selectedJamat: JamatCategory? = null
    private var searchQuery = ""

    private var catalogBooks: List<RemoteLibraryBook> = emptyList()
    private var liveArchiveSearchResults: List<RemoteLibraryBook> = emptyList()
    private var isCatalogLoading = false
    private val downloadingSlugs = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIslamicLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsStore = LibraryPrefsStore(this)

        setupRecyclerViews()
        setupSearch()
        setupBottomTabs()
        setupInfoBuiltInReader()
        setupBackNavigation()

        // Show cached books immediately while fetching latest from free public APIs
        val cached = prefsStore.loadCatalogCache()
        if (cached.isNotEmpty()) {
            catalogBooks = cached
        }
        renderCurrentView()
        refreshUnifiedCatalog()
    }

    private fun setupBackNavigation() {
        val handleBack = {
            if (currentMode == LibraryViewMode.JAMAT_BOOKS || currentMode == LibraryViewMode.SEARCH_RESULTS) {
                selectedJamat = null
                if (binding.etSearchBooks.text?.isNotBlank() == true) {
                    binding.etSearchBooks.setText("")
                }
                switchMode(LibraryViewMode.HOME_CATEGORIES)
            } else {
                finish()
            }
        }

        binding.btnLibraryBack.setOnClickListener { handleBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })
    }

    private fun setupRecyclerViews() {
        jamatAdapter = JamatCategoryAdapter(JamatCatalog.allCategories) { category ->
            selectedJamat = category
            switchMode(LibraryViewMode.JAMAT_BOOKS)
        }
        binding.rvJamatCategories.layoutManager = GridLayoutManager(this, 2)
        binding.rvJamatCategories.adapter = jamatAdapter

        booksGridAdapter = RemoteLibraryAdapter(
            books = emptyList(),
            scope = lifecycleScope,
            isDownloaded = { it.isDownloaded(this) },
            isDownloading = { downloadingSlugs.contains(it.slug) },
            showDeleteButton = { currentMode == LibraryViewMode.DOWNLOADS },
            onBookClick = { book -> showBookActionDialog(book) },
            onDeleteClick = { book -> confirmDeleteOfflineBook(book) }
        )
        binding.rvRemoteBooks.layoutManager = GridLayoutManager(this, 3)
        binding.rvRemoteBooks.adapter = booksGridAdapter
    }

    private fun setupSearch() {
        binding.etSearchBooks.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                if (searchQuery.isNotEmpty() &&
                    (currentMode == LibraryViewMode.HOME_CATEGORIES || currentMode == LibraryViewMode.INFO)
                ) {
                    currentMode = LibraryViewMode.SEARCH_RESULTS
                } else if (searchQuery.isEmpty() && currentMode == LibraryViewMode.SEARCH_RESULTS) {
                    liveArchiveSearchResults = emptyList()
                    currentMode = if (selectedJamat != null) LibraryViewMode.JAMAT_BOOKS else LibraryViewMode.HOME_CATEGORIES
                }
                renderCurrentView()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearchBooks.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                executeOnlineArchiveSearch()
                true
            } else {
                false
            }
        }

        binding.btnOnlineSearchLibrary.setOnClickListener {
            executeOnlineArchiveSearch()
        }
    }

    private fun setupBottomTabs() {
        binding.btnLibNavDownload.setOnClickListener { switchMode(LibraryViewMode.DOWNLOADS) }
        binding.btnLibNavInfo.setOnClickListener { switchMode(LibraryViewMode.INFO) }
        binding.btnLibNavHome.setOnClickListener {
            selectedJamat = null
            liveArchiveSearchResults = emptyList()
            switchMode(LibraryViewMode.HOME_CATEGORIES)
        }
        binding.btnLibNavBookmark.setOnClickListener { switchMode(LibraryViewMode.BOOKMARKS) }
        binding.btnLibNavFavorite.setOnClickListener { switchMode(LibraryViewMode.FAVORITES) }
    }

    private fun setupInfoBuiltInReader() {
        binding.btnOpenBuiltInBooks.setOnClickListener {
            val builtIn = BookData.allBooks
            val titles = builtIn.map { it.titleBn }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle("সংকলিত বাংলা কিতাব নির্বাচন করুন")
                .setItems(titles) { _, which ->
                    val picked = builtIn[which]
                    startActivity(
                        Intent(this, BookReaderActivity::class.java).apply {
                            putExtra("EXTRA_BOOK_ID", picked.id)
                        }
                    )
                }
                .setNegativeButton("বাতিল", null)
                .show()
        }
    }

    private fun refreshUnifiedCatalog() {
        isCatalogLoading = true
        if (currentMode != LibraryViewMode.HOME_CATEGORIES &&
            currentMode != LibraryViewMode.INFO &&
            catalogBooks.isEmpty()
        ) {
            binding.pbRemoteBooksLoading.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            val fetched = LibraryApiClient.fetchUnifiedCatalog(this@IslamicLibraryActivity)
            isCatalogLoading = false
            binding.pbRemoteBooksLoading.visibility = View.GONE
            if (fetched.isNotEmpty()) {
                catalogBooks = fetched
            }
            renderCurrentView()
        }
    }

    private fun executeOnlineArchiveSearch() {
        val q = binding.etSearchBooks.text?.toString()?.trim().orEmpty()
        if (q.isBlank()) {
            Toast.makeText(this, "সার্চ করার জন্য কিতাবের নাম লিখুন", Toast.LENGTH_SHORT).show()
            return
        }
        searchQuery = q
        currentMode = LibraryViewMode.SEARCH_RESULTS
        renderCurrentView()

        binding.pbRemoteBooksLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val archiveHits = LibraryApiClient.searchArchiveBooks(q, rows = 15)
            val openLibHits = LibraryApiClient.fetchOpenLibraryBooks(q, limit = 5)
            val remoteHits = (archiveHits + openLibHits).distinctBy { it.slug }
            binding.pbRemoteBooksLoading.visibility = View.GONE
            liveArchiveSearchResults = remoteHits
            if (remoteHits.isEmpty() && filteredCatalogMatches(q).isEmpty()) {
                Toast.makeText(
                    this@IslamicLibraryActivity,
                    "অনলাইনে '$q' নামে কোনো PDF পাওয়া যায়নি",
                    Toast.LENGTH_SHORT
                ).show()
            }
            renderCurrentView()
        }
    }

    private fun switchMode(mode: LibraryViewMode) {
        currentMode = mode
        renderCurrentView()
    }

    private fun renderCurrentView() {
        updateBottomBarSelection()

        val headerTitle = when (currentMode) {
            LibraryViewMode.HOME_CATEGORIES -> "ইসলামিক ও কিতাব লাইব্রেরী"
            LibraryViewMode.JAMAT_BOOKS -> selectedJamat?.titleBn ?: "কিতাব ও বই তালিকা"
            LibraryViewMode.SEARCH_RESULTS -> "অনলাইন সার্চ: $searchQuery"
            LibraryViewMode.DOWNLOADS -> "সংরক্ষিত কিতাব ও বই"
            LibraryViewMode.INFO -> "লাইব্রেরী তথ্য ও গল্প"
            LibraryViewMode.BOOKMARKS -> "বুকমার্ক করা বই"
            LibraryViewMode.FAVORITES -> "পছন্দের বই (Favorite)"
        }
        binding.tvLibraryHeaderTitle.text = headerTitle

        binding.rvJamatCategories.visibility = View.GONE
        binding.rvRemoteBooks.visibility = View.GONE
        binding.layoutLibraryInfo.visibility = View.GONE
        binding.tvLibraryEmptyState.visibility = View.GONE
        binding.pbRemoteBooksLoading.visibility = View.GONE

        when (currentMode) {
            LibraryViewMode.HOME_CATEGORIES -> {
                binding.rvJamatCategories.visibility = View.VISIBLE
            }

            LibraryViewMode.INFO -> {
                binding.layoutLibraryInfo.visibility = View.VISIBLE
            }

            else -> {
                val booksToShow = computeDisplayedBooks()
                booksGridAdapter.updateBooks(booksToShow)
                if (booksToShow.isEmpty()) {
                    if (isCatalogLoading) {
                        binding.pbRemoteBooksLoading.visibility = View.VISIBLE
                    } else {
                        binding.tvLibraryEmptyState.visibility = View.VISIBLE
                        binding.tvLibraryEmptyState.text = when (currentMode) {
                            LibraryViewMode.DOWNLOADS ->
                                "এখনো কোনো বই বা কিতাব অফলাইনে ডাউনলোড করা হয়নি।\nযেকোনো বইয়ে ট্যাপ করে ডাউনলোড করুন।"
                            LibraryViewMode.BOOKMARKS ->
                                "এখনো কোনো বই বুকমার্ক করা হয়নি।"
                            LibraryViewMode.FAVORITES ->
                                "এখনো কোনো বই ফেভারিট করা হয়নি।"
                            LibraryViewMode.SEARCH_RESULTS ->
                                "'$searchQuery' নামে বই মেলেনি।\nসার্চ আইকনে চাপ দিয়ে অনলাইন লাইব্রেরীতে খুঁজুন।"
                            else ->
                                "এই বিভাগে লোড হওয়া বই পাওয়া যায়নি।\nইন্টারনেট সংযোগ চেক করে আবার চেষ্টা করুন।"
                        }
                    }
                } else {
                    binding.rvRemoteBooks.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun computeDisplayedBooks(): List<RemoteLibraryBook> {
        val mergedKnownBooks = (prefsStore.loadSavedBooks() + catalogBooks + liveArchiveSearchResults)
            .distinctBy { it.slug }

        val baseList = when (currentMode) {
            LibraryViewMode.JAMAT_BOOKS -> {
                val code = selectedJamat?.code
                if (code == null) {
                    catalogBooks
                } else if (code == RemoteLibraryBook.JAMAT_TAISIR) {
                    catalogBooks.filter {
                        it.jamatCode == RemoteLibraryBook.JAMAT_TAISIR ||
                            it.jamatCode == RemoteLibraryBook.JAMAT_MAKTAB
                    }
                } else {
                    catalogBooks.filter { it.jamatCode == code }
                }
            }

            LibraryViewMode.SEARCH_RESULTS -> {
                (filteredCatalogMatches(searchQuery) + liveArchiveSearchResults).distinctBy { it.slug }
            }

            LibraryViewMode.DOWNLOADS -> {
                mergedKnownBooks.filter { it.isDownloaded(this) }
            }

            LibraryViewMode.BOOKMARKS -> {
                val bm = prefsStore.getBookmarks()
                mergedKnownBooks.filter { bm.contains(it.slug) }
            }

            LibraryViewMode.FAVORITES -> {
                val fav = prefsStore.getFavorites()
                mergedKnownBooks.filter { fav.contains(it.slug) }
            }

            else -> emptyList()
        }

        if (searchQuery.isBlank() || currentMode == LibraryViewMode.SEARCH_RESULTS) {
            return baseList
        }
        return filterBooksByQuery(baseList, searchQuery)
    }

    private fun filteredCatalogMatches(q: String): List<RemoteLibraryBook> {
        val pool = (catalogBooks + prefsStore.loadSavedBooks()).distinctBy { it.slug }
        return filterBooksByQuery(pool, q)
    }

    private fun filterBooksByQuery(list: List<RemoteLibraryBook>, q: String): List<RemoteLibraryBook> {
        val lower = q.lowercase(Locale.ROOT)
        return list.filter { b ->
            b.titleBn.lowercase(Locale.ROOT).contains(lower) ||
                b.titleEn.lowercase(Locale.ROOT).contains(lower) ||
                b.titleAr.lowercase(Locale.ROOT).contains(lower) ||
                b.authorNameBn.lowercase(Locale.ROOT).contains(lower) ||
                b.authorNameEn.lowercase(Locale.ROOT).contains(lower) ||
                b.genre.lowercase(Locale.ROOT).contains(lower)
        }
    }

    private fun updateBottomBarSelection() {
        val activeBg = R.drawable.bg_lib_nav_pill
        val activeColor = Color.parseColor("#0F4C5C")
        val inactiveColor = Color.parseColor("#4E6167")

        fun styleTab(
            bgView: View,
            iconView: android.widget.ImageView,
            textView: android.widget.TextView,
            selected: Boolean
        ) {
            bgView.setBackgroundResource(if (selected) activeBg else 0)
            iconView.setColorFilter(if (selected) activeColor else inactiveColor)
            textView.setTextColor(if (selected) activeColor else inactiveColor)
            textView.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }

        val homeSelected = currentMode == LibraryViewMode.HOME_CATEGORIES ||
            currentMode == LibraryViewMode.JAMAT_BOOKS ||
            currentMode == LibraryViewMode.SEARCH_RESULTS

        styleTab(binding.bgLibNavHome, binding.ivLibNavHome, binding.tvLibNavHome, homeSelected)
        styleTab(
            binding.bgLibNavDownload,
            binding.ivLibNavDownload,
            binding.tvLibNavDownload,
            currentMode == LibraryViewMode.DOWNLOADS
        )
        styleTab(
            binding.bgLibNavBookmark,
            binding.ivLibNavBookmark,
            binding.tvLibNavBookmark,
            currentMode == LibraryViewMode.BOOKMARKS
        )
        styleTab(
            binding.bgLibNavFavorite,
            binding.ivLibNavFavorite,
            binding.tvLibNavFavorite,
            currentMode == LibraryViewMode.FAVORITES
        )
        styleTab(
            binding.bgLibNavInfo,
            binding.ivLibNavInfo,
            binding.tvLibNavInfo,
            currentMode == LibraryViewMode.INFO
        )
    }

    private fun showBookActionDialog(book: RemoteLibraryBook) {
        val title = book.titleBn.ifBlank { book.titleEn.ifBlank { book.titleAr } }
        val isDownloaded = book.isDownloaded(this)
        val isBookmarked = prefsStore.isBookmarked(book.slug)
        val isFavorite = prefsStore.isFavorite(book.slug)

        val actions = mutableListOf<String>()
        actions.add(if (isDownloaded) "📖 অফলাইনে পড়ুন" else "📖 অনলাইনে পড়ুন (লাইভ PDF)")
        if (!isDownloaded) {
            actions.add("⬇ অফলাইনে ডাউনলোড করুন")
        } else {
            actions.add("🗑️ অফলাইন ফাইল মুছুন")
        }
        actions.add(if (isBookmarked) "🔖 বুকমার্ক সরান" else "🔖 বুকমার্ক করুন")
        actions.add(if (isFavorite) "💔 ফেভারিট থেকে সরান" else "❤️ ফেভারিট করুন")

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(actions.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        if (isDownloaded) {
                            openOfflinePdf(book)
                        } else {
                            openOnlinePdf(book)
                        }
                    }
                    1 -> {
                        if (!isDownloaded) {
                            downloadBookOffline(book)
                        } else {
                            confirmDeleteOfflineBook(book)
                        }
                    }
                    2 -> {
                        val added = prefsStore.toggleBookmark(book.slug)
                        prefsStore.saveBookMetadata(book)
                        Toast.makeText(
                            this,
                            if (added) "🔖 বুকমার্কে যোগ হয়েছে" else "বুকমার্ক সরানো হয়েছে",
                            Toast.LENGTH_SHORT
                        ).show()
                        renderCurrentView()
                    }
                    3 -> {
                        val added = prefsStore.toggleFavorite(book.slug)
                        prefsStore.saveBookMetadata(book)
                        Toast.makeText(
                            this,
                            if (added) "❤️ ফেভারিটে যোগ হয়েছে" else "ফেভারিট থেকে সরানো হয়েছে",
                            Toast.LENGTH_SHORT
                        ).show()
                        renderCurrentView()
                    }
                }
            }
            .setNegativeButton("বন্ধ করুন", null)
            .show()
    }

    private fun openOnlinePdf(book: RemoteLibraryBook) {
        val title = book.titleBn.ifBlank { book.titleEn.ifBlank { book.titleAr } }
        val intent = Intent(this, PdfReaderActivity::class.java).apply {
            putExtra(PdfReaderActivity.EXTRA_PDF_TITLE, title)
            putExtra(PdfReaderActivity.EXTRA_PDF_URL, book.resolvedPdfUrl())
            putExtra(PdfReaderActivity.EXTRA_PDF_SLUG, book.slug)
        }
        startActivity(intent)
    }

    private fun openOfflinePdf(book: RemoteLibraryBook) {
        val file = book.localFile(this)
        if (!file.exists()) {
            openOnlinePdf(book)
            return
        }
        val title = book.titleBn.ifBlank { book.titleEn.ifBlank { book.titleAr } }
        val intent = Intent(this, PdfReaderActivity::class.java).apply {
            putExtra(PdfReaderActivity.EXTRA_PDF_PATH, file.absolutePath)
            putExtra(PdfReaderActivity.EXTRA_PDF_TITLE, title)
        }
        startActivity(intent)
    }

    private fun downloadBookOffline(book: RemoteLibraryBook) {
        if (downloadingSlugs.contains(book.slug)) return
        downloadingSlugs.add(book.slug)
        renderCurrentView()
        Toast.makeText(this, "ডাউনলোড শুরু হয়েছে...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val file = LibraryApiClient.downloadBookPdf(
                context = this@IslamicLibraryActivity,
                book = book,
                toCacheOnly = false
            )
            downloadingSlugs.remove(book.slug)
            if (file != null && file.exists()) {
                Toast.makeText(
                    this@IslamicLibraryActivity,
                    "✅ ডাউনলোড সম্পন্ন — এখন অফলাইনে পড়া যাবে",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@IslamicLibraryActivity,
                    "ডাউনলোড ব্যর্থ হয়েছে, ইন্টারনেট সংযোগ চেক করুন",
                    Toast.LENGTH_SHORT
                ).show()
            }
            renderCurrentView()
        }
    }

    private fun confirmDeleteOfflineBook(book: RemoteLibraryBook) {
        val title = book.titleBn.ifBlank { book.titleEn }
        MaterialAlertDialogBuilder(this)
            .setTitle("অফলাইন কিতাব মুছুন")
            .setMessage("'$title' কি অফলাইন স্টোরেজ থেকে মুছে ফেলবেন?")
            .setPositiveButton("মুছুন") { _, _ ->
                book.deleteLocalFile(this)
                Toast.makeText(this, "অফলাইন কিতাব মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                renderCurrentView()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }
}
