package com.hafij.islamicalarm.quran

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.ScaleXSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityOfflineArabicQuranBinding
import com.hafij.islamicalarm.databinding.ItemMushafPageBinding
import com.hafij.islamicalarm.databinding.ItemOfflineQuranRowBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

/**
 * 100% Offline 15-Line Bangladeshi Hafezi Quran (Emdadia Layout: 114 Surahs, 30 Paras, 610 Pages).
 * Renders each page with full-width equal justified lines, circled Ayah number medallions,
 * top-center (Para : PageInPara) header, per-Para 1..lastPage numbering, and outer Ruku margin marks.
 */
class OfflineArabicQuranActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineArabicQuranBinding
    private lateinit var indexAdapter: OfflineIndexAdapter
    private lateinit var mushafAdapter: MushafPagerAdapter

    private var activeTab = TAB_SURAH
    private var isSingleCategoryMode = false
    private var isParaPageSearchMode = false
    private var selectedSearchPara: Int? = null
    private var currentPage = 1
    private var openedDirectlyToReader = false
    private var allPageSummaries: List<QuranRepository.OfflinePageSummary> = emptyList()
    private var mushafFontScale: Float = 1.0f
    private var quranTypeface: Typeface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfflineArabicQuranBinding.inflate(layoutInflater)
        setContentView(binding.root)

        quranTypeface = try {
            ResourcesCompat.getFont(this, R.font.al_qalam_quran_majeed_4)
        } catch (e: Exception) {
            try {
                ResourcesCompat.getFont(this, R.font.amiri)
            } catch (_: Exception) {
                null
            }
        }

        val prefs = getSharedPreferences(QURAN_PREFS, Context.MODE_PRIVATE)
        mushafFontScale = prefs.getFloat(KEY_HAFEZI_FONT_SCALE, 1.0f).coerceIn(0.80f, 1.30f)

        activeTab = intent.getIntExtra(EXTRA_INITIAL_TAB, TAB_SURAH).coerceIn(TAB_SURAH, TAB_PAGE)
        isSingleCategoryMode = intent.getBooleanExtra(EXTRA_SINGLE_CATEGORY, false)
        isParaPageSearchMode = intent.getBooleanExtra(EXTRA_PARA_PAGE_SEARCH, false)
        if (isParaPageSearchMode) {
            isSingleCategoryMode = true
        }
        openedDirectlyToReader = intent.getBooleanExtra(EXTRA_OPEN_LAST_PAGE, false)
        val initialPage = intent.getIntExtra(
            EXTRA_PAGE_NUMBER,
            prefs.getInt(KEY_LAST_OFFLINE_PAGE, 1)
        ).coerceIn(1, TOTAL_PAGES)

        setupToolbar()
        setupIndexList()
        setupSearchAndJump()
        setupTabs()
        setupMushafReader()
        setupBackNavigation()
        applyModeHeaderAndControls()

        binding.pbOfflineLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                QuranRepository.ensureHafeziLoaded(this@OfflineArabicQuranActivity)
            }
            allPageSummaries = QuranRepository.getAllHafeziPageSummaries(this@OfflineArabicQuranActivity)
            binding.pbOfflineLoading.visibility = View.GONE

            binding.tabLayoutOfflineQuran.getTabAt(activeTab)?.select()
            renderIndexList()

            if (openedDirectlyToReader) {
                openMushafAtPage(initialPage)
            } else {
                showIndexMode()
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbarOfflineQuran.setNavigationOnClickListener {
            handleBackAction()
        }
        binding.btnOpenIndexFromReader.setOnClickListener {
            openedDirectlyToReader = false
            showIndexMode()
        }
        binding.btnMushafFontLarger.setOnClickListener {
            if (mushafFontScale < 1.30f) {
                mushafFontScale = (mushafFontScale + 0.08f).coerceAtMost(1.30f)
                saveFontScale()
                mushafAdapter.updateFontScale(mushafFontScale)
            }
        }
        binding.btnMushafFontSmaller.setOnClickListener {
            if (mushafFontScale > 0.80f) {
                mushafFontScale = (mushafFontScale - 0.08f).coerceAtLeast(0.80f)
                saveFontScale()
                mushafAdapter.updateFontScale(mushafFontScale)
            }
        }
    }

    private fun saveFontScale() {
        getSharedPreferences(QURAN_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_HAFEZI_FONT_SCALE, mushafFontScale)
            .apply()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackAction()
            }
        })
    }

    private fun handleBackAction() {
        if (binding.layoutMushafReader.visibility == View.VISIBLE && !openedDirectlyToReader) {
            showIndexMode()
        } else {
            finish()
        }
    }

    private fun setupIndexList() {
        indexAdapter = OfflineIndexAdapter(quranTypeface) { row ->
            if (isParaPageSearchMode && selectedSearchPara == null && row.paraNumber != null) {
                selectedSearchPara = row.paraNumber
                binding.etOfflineSearch.text?.clear()
                applyModeHeaderAndControls()
                renderIndexList()
            } else {
                openMushafAtPage(row.targetPage)
            }
        }
        binding.rvOfflineIndex.layoutManager = LinearLayoutManager(this)
        binding.rvOfflineIndex.adapter = indexAdapter
    }

    private fun setupSearchAndJump() {
        binding.etOfflineSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim().orEmpty()
                binding.btnOfflineClearSearch.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                renderIndexList()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.etOfflineSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val typedNum = parseBengaliOrAsciiInt(binding.etOfflineSearch.text?.toString().orEmpty())
                if (isParaPageSearchMode) {
                    val activePara = selectedSearchPara
                    if (activePara == null && typedNum != null && typedNum in 1..30) {
                        selectedSearchPara = typedNum
                        binding.etOfflineSearch.text?.clear()
                        applyModeHeaderAndControls()
                        renderIndexList()
                        return@setOnEditorActionListener true
                    } else if (activePara != null && typedNum != null) {
                        val range = QuranRepository.getHafeziParaPageRange(activePara)
                        val paraTotal = range.last - range.first + 1
                        if (typedNum in 1..paraTotal) {
                            openMushafAtPage(range.first + typedNum - 1)
                            return@setOnEditorActionListener true
                        }
                    }
                } else if (activeTab == TAB_PAGE && typedNum != null && typedNum in 1..TOTAL_PAGES) {
                    openMushafAtPage(typedNum)
                    return@setOnEditorActionListener true
                }
            }
            false
        }

        binding.btnOfflineClearSearch.setOnClickListener {
            binding.etOfflineSearch.text?.clear()
        }

        setupParaPageSearchPanel()

        binding.btnQuickJumpPage.setOnClickListener {
            val typedNum = parseBengaliOrAsciiInt(binding.etOfflineSearch.text?.toString().orEmpty())
            if (typedNum != null && typedNum in 1..TOTAL_PAGES) {
                openMushafAtPage(typedNum)
            } else {
                showJumpPageDialog()
            }
        }
    }

    private fun setupParaPageSearchPanel() {
        binding.etSearchParaNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val paraNum = parseBengaliOrAsciiInt(s?.toString().orEmpty())
                updateParaPageSearchState(paraNum)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnPickSearchPara.setOnClickListener {
            showSelectParaPickerDialog()
        }

        binding.etSearchParaPageNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                executeParaPageSearch()
                true
            } else {
                false
            }
        }

        binding.btnExecuteParaPageSearch.setOnClickListener {
            executeParaPageSearch()
        }
    }

    private fun updateParaPageSearchState(paraNum: Int?) {
        if (paraNum != null && paraNum in 1..30) {
            selectedSearchPara = paraNum
            val para = QuranRepository.paraList.find { it.id == paraNum }
            val paraTotal = QuranRepository.getHafeziParaTotalPages(paraNum)
            val paraNameBn = para?.nameBangla.orEmpty()
            binding.tvSearchParaRangeHint.text =
                "পারা ${toBengaliNumber(paraNum)} ($paraNameBn) • পৃষ্ঠা ১ থেকে ${toBengaliNumber(paraTotal)}"
            binding.etSearchParaPageNumber.hint =
                "পৃষ্ঠা নম্বর (১ – ${toBengaliNumber(paraTotal)})"
        } else {
            selectedSearchPara = null
            binding.tvSearchParaRangeHint.text = "১ থেকে ৩০ এর মধ্যে পারা নম্বর লিখুন বা বাছাই করুন"
            binding.etSearchParaPageNumber.hint = "আগে পারা নম্বর দিন (১–৩০)"
        }
    }

    private fun showSelectParaPickerDialog() {
        val labels = QuranRepository.paraList.map { para ->
            val total = QuranRepository.getHafeziParaTotalPages(para.id)
            "পারা ${toBengaliNumber(para.id)} • ${para.nameBangla} (${toBengaliNumber(total)} পৃষ্ঠা)"
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("পারা নির্বাচন করুন (১–৩০)")
            .setItems(labels) { _, which ->
                val chosenPara = QuranRepository.paraList.getOrNull(which)?.id ?: return@setItems
                binding.etSearchParaNumber.setText(chosenPara.toString())
                binding.etSearchParaPageNumber.requestFocus()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun executeParaPageSearch() {
        val paraNum = parseBengaliOrAsciiInt(binding.etSearchParaNumber.text?.toString().orEmpty())
            ?: selectedSearchPara
        if (paraNum == null || paraNum !in 1..30) {
            Toast.makeText(this, "অনুগ্রহ করে ১ থেকে ৩০ এর মধ্যে পারা নম্বর দিন", Toast.LENGTH_SHORT).show()
            binding.etSearchParaNumber.requestFocus()
            return
        }
        val range = QuranRepository.getHafeziParaPageRange(paraNum)
        val paraTotal = range.last - range.first + 1
        val rawPageText = binding.etSearchParaPageNumber.text?.toString()?.trim().orEmpty()
        val pageInPara = if (rawPageText.isEmpty()) {
            1
        } else {
            parseBengaliOrAsciiInt(rawPageText)
        }
        if (pageInPara == null || pageInPara !in 1..paraTotal) {
            Toast.makeText(
                this,
                "পারা ${toBengaliNumber(paraNum)}-এ ১ থেকে ${toBengaliNumber(paraTotal)} এর মধ্যে পৃষ্ঠা দিন",
                Toast.LENGTH_SHORT
            ).show()
            binding.etSearchParaPageNumber.requestFocus()
            return
        }
        openMushafAtPage(range.first + pageInPara - 1)
    }

    private fun setupTabs() {
        binding.tabLayoutOfflineQuran.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                activeTab = tab?.position ?: TAB_SURAH
                applyModeHeaderAndControls()
                renderIndexList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun applyModeHeaderAndControls() {
        binding.tabLayoutOfflineQuran.visibility =
            if (isSingleCategoryMode || isParaPageSearchMode) View.GONE else View.VISIBLE

        if (isParaPageSearchMode) {
            binding.cardStandardSearchBar.visibility = View.GONE
            binding.layoutIndexListFrame.visibility = View.GONE
            binding.cardParaPageSearchPanel.visibility = View.VISIBLE
            updateParaPageSearchState(
                parseBengaliOrAsciiInt(binding.etSearchParaNumber.text?.toString().orEmpty()) ?: selectedSearchPara
            )
            if (binding.layoutMushafReader.visibility != View.VISIBLE) {
                binding.toolbarOfflineQuran.title = "সার্চ"
                binding.toolbarOfflineQuran.subtitle = "পারা ও পৃষ্ঠা লিখে খুঁজুন"
            }
            return
        }

        binding.cardStandardSearchBar.visibility = View.VISIBLE
        binding.layoutIndexListFrame.visibility = View.VISIBLE
        binding.cardParaPageSearchPanel.visibility = View.GONE

        binding.btnQuickJumpPage.visibility =
            if (!isSingleCategoryMode || activeTab == TAB_PAGE) View.VISIBLE else View.GONE
        binding.btnQuickJumpPage.text =
            if (isSingleCategoryMode && activeTab == TAB_PAGE) "পৃষ্ঠা খুলুন" else "পৃষ্ঠায় যান"

        binding.etOfflineSearch.hint = when (activeTab) {
            TAB_PARA -> "পারার নাম বা নম্বর (১–৩০) লিখুন..."
            TAB_PAGE -> "পৃষ্ঠা নম্বর (১–৬১০) বা সুরা লিখুন..."
            else -> "সুরার নাম বা নম্বর (১–১১৪) লিখুন..."
        }

        if (binding.layoutMushafReader.visibility != View.VISIBLE) {
            if (isSingleCategoryMode) {
                when (activeTab) {
                    TAB_PARA -> {
                        binding.toolbarOfflineQuran.title = "পারা সমূহ"
                        binding.toolbarOfflineQuran.subtitle = "৩০টি পারা • ১৫ লাইন হাফেজী (অফলাইন)"
                    }
                    TAB_PAGE -> {
                        binding.toolbarOfflineQuran.title = "পৃষ্ঠা"
                        binding.toolbarOfflineQuran.subtitle = "১–৬১০ পৃষ্ঠা • ১৫ লাইন হাফেজী"
                    }
                    else -> {
                        binding.toolbarOfflineQuran.title = "সুরা সমূহ"
                        binding.toolbarOfflineQuran.subtitle = "১১৪টি সুরা • ১৫ লাইন হাফেজী (আরবি)"
                    }
                }
            } else {
                binding.toolbarOfflineQuran.title = "১৫ লাইন হাফেজী কুরআন"
                binding.toolbarOfflineQuran.subtitle = "১১৪ সুরা • ৩০ পারা • ৬১০ পৃষ্ঠা (অফলাইন)"
            }
        }
    }

    private fun renderIndexList() {
        val rawQuery = binding.etOfflineSearch.text?.toString()?.trim().orEmpty()
        val queryNum = parseBengaliOrAsciiInt(rawQuery)

        if (isParaPageSearchMode) {
            indexAdapter.submitList(emptyList())
            binding.tvOfflineEmpty.visibility = View.GONE
            return
        }

        val rows: List<IndexRow> = when (activeTab) {
            TAB_PARA -> {
                QuranRepository.paraList.filter { para ->
                    rawQuery.isEmpty() ||
                        para.nameBangla.contains(rawQuery, ignoreCase = true) ||
                        para.nameArabic.contains(rawQuery, ignoreCase = true) ||
                        para.id == queryNum
                }.map { para ->
                    val exactPage = QuranRepository.getHafeziStartPageForPara(para.id)
                    val paraTotal = QuranRepository.getHafeziParaTotalPages(para.id)
                    val startSurah = QuranRepository.getHafeziSurahForPage(exactPage)
                    IndexRow(
                        numberLabel = toBengaliNumber(para.id),
                        titleBn = "পারা ${toBengaliNumber(para.id)} • ${para.nameBangla}",
                        meta = "শুরু: সুরা ${startSurah.nameBangla} • পৃষ্ঠা ১ – ${toBengaliNumber(paraTotal)}",
                        arabic = para.nameArabic,
                        targetPage = exactPage
                    )
                }
            }
            TAB_PAGE -> {
                val filteredPages = if (rawQuery.isEmpty()) {
                    allPageSummaries
                } else if (queryNum != null) {
                    val queryStr = queryNum.toString()
                    allPageSummaries.filter { summary ->
                        summary.pageNumber == queryNum ||
                            summary.pageNumber.toString().startsWith(queryStr)
                    }.sortedBy { if (it.pageNumber == queryNum) 0 else 1 }
                } else {
                    allPageSummaries.filter { summary ->
                        summary.firstSurah.nameBangla.contains(rawQuery, ignoreCase = true) ||
                            summary.firstSurah.nameEnglish.contains(rawQuery, ignoreCase = true) ||
                            summary.firstSurah.nameArabic.contains(rawQuery, ignoreCase = true)
                    }
                }
                filteredPages.map { summary ->
                    val pageInPara = QuranRepository.getHafeziPageInPara(summary.pageNumber)
                    val paraTotal = QuranRepository.getHafeziParaTotalPages(summary.juzNumber)
                    IndexRow(
                        numberLabel = toBengaliNumber(summary.pageNumber),
                        titleBn = "পৃষ্ঠা ${toBengaliNumber(summary.pageNumber)}",
                        meta = "পারা ${toBengaliNumber(summary.juzNumber)} (${toBengaliNumber(pageInPara)}/${toBengaliNumber(paraTotal)}) • সুরা ${summary.firstSurah.nameBangla}",
                        arabic = summary.firstSurah.nameArabic,
                        targetPage = summary.pageNumber
                    )
                }
            }
            else -> {
                QuranRepository.surahList.filter { surah ->
                    rawQuery.isEmpty() ||
                        surah.nameBangla.contains(rawQuery, ignoreCase = true) ||
                        surah.nameEnglish.contains(rawQuery, ignoreCase = true) ||
                        surah.nameArabic.contains(rawQuery, ignoreCase = true) ||
                        surah.id == queryNum
                }.map { surah ->
                    val startPage = QuranRepository.getHafeziStartPageForSurah(surah.id)
                    val juz = QuranRepository.getHafeziJuzForPage(startPage)
                    val pageInPara = QuranRepository.getHafeziPageInPara(startPage)
                    IndexRow(
                        numberLabel = toBengaliNumber(surah.id),
                        titleBn = "সুরা ${surah.nameBangla}",
                        meta = "${surah.revelationType} • ${toBengaliNumber(surah.totalAyahs)} আয়াত • পারা ${toBengaliNumber(juz)} • পৃষ্ঠা ${toBengaliNumber(pageInPara)}",
                        arabic = surah.nameArabic,
                        targetPage = startPage
                    )
                }
            }
        }

        indexAdapter.submitList(rows)
        binding.tvOfflineEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupMushafReader() {
        mushafAdapter = MushafPagerAdapter(this, quranTypeface, mushafFontScale)
        binding.vpMushafPages.adapter = mushafAdapter
        binding.vpMushafPages.offscreenPageLimit = 1
        binding.vpMushafPages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val page = (position + 1).coerceIn(1, TOTAL_PAGES)
                currentPage = page
                updateReaderPageMetadata(page)
                saveLastOfflinePage(page)
            }
        })

        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 1) {
                binding.vpMushafPages.setCurrentItem(currentPage - 2, true)
            }
        }

        binding.btnNextPage.setOnClickListener {
            if (currentPage < TOTAL_PAGES) {
                binding.vpMushafPages.setCurrentItem(currentPage, true)
            }
        }

        binding.tvMushafPageFooter.setOnClickListener {
            if (isParaPageSearchMode && selectedSearchPara != null) {
                showJumpParaPageDialog(selectedSearchPara!!)
            } else {
                showJumpPageDialog()
            }
        }
    }

    private fun showIndexMode() {
        binding.layoutOfflineIndex.visibility = View.VISIBLE
        binding.layoutMushafReader.visibility = View.GONE
        binding.layoutReaderToolbarActions.visibility = View.GONE
        applyModeHeaderAndControls()
        renderIndexList()
    }

    private fun openMushafAtPage(pageNumber: Int) {
        val safePage = pageNumber.coerceIn(1, TOTAL_PAGES)
        currentPage = safePage
        binding.layoutOfflineIndex.visibility = View.GONE
        binding.layoutMushafReader.visibility = View.VISIBLE
        binding.layoutReaderToolbarActions.visibility = View.VISIBLE

        binding.vpMushafPages.setCurrentItem(safePage - 1, false)
        updateReaderPageMetadata(safePage)
        saveLastOfflinePage(safePage)
    }

    private fun updateReaderPageMetadata(page: Int) {
        val summary = allPageSummaries.getOrNull(page - 1)
        val juz = summary?.juzNumber ?: QuranRepository.getHafeziJuzForPage(page)
        val surah = summary?.firstSurah ?: QuranRepository.getHafeziSurahForPage(page)
        val pageInPara = QuranRepository.getHafeziPageInPara(page)
        val paraTotal = QuranRepository.getHafeziParaTotalPages(juz)

        binding.toolbarOfflineQuran.title = "${surah.nameBangla} (${surah.nameArabic})"
        binding.toolbarOfflineQuran.subtitle =
            "পারা ${toBengaliNumber(juz)} • পৃষ্ঠা ${toBengaliNumber(pageInPara)} / ${toBengaliNumber(paraTotal)}"
        binding.tvMushafPageFooter.text =
            "পারা ${toBengaliNumber(juz)} : পৃষ্ঠা ${toBengaliNumber(pageInPara)}/${toBengaliNumber(paraTotal)} • মোট ${toBengaliNumber(page)}"
        binding.btnPrevPage.isEnabled = page > 1
        binding.btnNextPage.isEnabled = page < TOTAL_PAGES
    }

    private fun saveLastOfflinePage(page: Int) {
        val surah = allPageSummaries.getOrNull(page - 1)?.firstSurah
            ?: QuranRepository.getHafeziSurahForPage(page)
        getSharedPreferences(QURAN_PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LAST_OFFLINE_PAGE, page)
            .putInt("last_read_page", page)
            .putInt("last_read_surah_id", surah.id)
            .putString("last_read_surah_name", surah.nameBangla)
            .apply()
    }

    private fun showSelectParaDialog() {
        val input = EditText(this).apply {
            hint = "পারা নম্বর (১ – ৩০)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 24)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("পারা নির্বাচন করুন (১ – ৩০)")
            .setMessage("১ থেকে ৩০ এর মধ্যে পারা নম্বর লিখুন:")
            .setView(input)
            .setPositiveButton("পরবর্তী") { _, _ ->
                val para = parseBengaliOrAsciiInt(input.text?.toString().orEmpty())
                if (para != null && para in 1..30) {
                    selectedSearchPara = para
                    binding.etOfflineSearch.text?.clear()
                    applyModeHeaderAndControls()
                    renderIndexList()
                } else {
                    Toast.makeText(this, "অনুগ্রহ করে ১ থেকে ৩০ এর মধ্যে পারা নম্বর দিন", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun showJumpParaPageDialog(paraNumber: Int) {
        val range = QuranRepository.getHafeziParaPageRange(paraNumber)
        val paraTotal = range.last - range.first + 1
        val currentInPara = if (currentPage in range) currentPage - range.first + 1 else 1
        val input = EditText(this).apply {
            hint = "পৃষ্ঠা নম্বর (১ – ${toBengaliNumber(paraTotal)})"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentInPara.toString())
            setSelection(text.length)
            setPadding(48, 32, 48, 24)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("পারা ${toBengaliNumber(paraNumber)} • পৃষ্ঠায় যান (১ – ${toBengaliNumber(paraTotal)})")
            .setMessage("এই পারার ১ থেকে ${toBengaliNumber(paraTotal)} এর মধ্যে পৃষ্ঠা নম্বর লিখুন:")
            .setView(input)
            .setPositiveButton("পড়ুন") { _, _ ->
                val pageInPara = parseBengaliOrAsciiInt(input.text?.toString().orEmpty())
                if (pageInPara != null && pageInPara in 1..paraTotal) {
                    openMushafAtPage(range.first + pageInPara - 1)
                } else {
                    Toast.makeText(
                        this,
                        "অনুগ্রহ করে ১ থেকে ${toBengaliNumber(paraTotal)} এর মধ্যে পৃষ্ঠা নম্বর দিন",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun showJumpPageDialog() {
        val input = EditText(this).apply {
            hint = "পৃষ্ঠা নম্বর (১ – ৬১০)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentPage.toString())
            setSelection(text.length)
            setPadding(48, 32, 48, 24)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("পৃষ্ঠায় যান (১ – ৬১০)")
            .setMessage("১৫ লাইন হাফেজী কুরআনের ১ থেকে ৬১০ এর মধ্যে পৃষ্ঠা নম্বর লিখুন:")
            .setView(input)
            .setPositiveButton("পড়ুন") { _, _ ->
                val page = parseBengaliOrAsciiInt(input.text?.toString().orEmpty())
                if (page != null && page in 1..TOTAL_PAGES) {
                    openMushafAtPage(page)
                } else {
                    Toast.makeText(this, "অনুগ্রহ করে ১ থেকে ৬১০ এর মধ্যে পৃষ্ঠা নম্বর দিন", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun parseBengaliOrAsciiInt(raw: String): Int? {
        if (raw.isBlank()) return null
        val sb = StringBuilder()
        for (ch in raw.trim()) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                in '০'..'৯' -> sb.append(('0'.code + (ch.code - '০'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                else -> return null
            }
        }
        return sb.toString().toIntOrNull()
    }

    private fun toBengaliNumber(num: Int): String {
        val digits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = StringBuilder()
        for (ch in num.toString()) {
            if (ch.isDigit()) sb.append(digits[ch - '0']) else sb.append(ch)
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // Index RecyclerView Adapter
    // ------------------------------------------------------------------

    private data class IndexRow(
        val numberLabel: String,
        val titleBn: String,
        val meta: String,
        val arabic: String,
        val targetPage: Int,
        val paraNumber: Int? = null
    )

    private class OfflineIndexAdapter(
        private val quranTypeface: Typeface?,
        private val onClick: (IndexRow) -> Unit
    ) : RecyclerView.Adapter<OfflineIndexAdapter.RowViewHolder>() {

        private var rows: List<IndexRow> = emptyList()

        fun submitList(list: List<IndexRow>) {
            rows = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
            val b = ItemOfflineQuranRowBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return RowViewHolder(b)
        }

        override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
            holder.bind(rows[position])
        }

        override fun getItemCount(): Int = rows.size

        inner class RowViewHolder(private val b: ItemOfflineQuranRowBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(row: IndexRow) {
                b.tvRowNumber.text = row.numberLabel
                b.tvRowTitleBn.text = row.titleBn
                b.tvRowMeta.text = row.meta
                b.tvRowArabic.text = row.arabic
                quranTypeface?.let { b.tvRowArabic.typeface = it }
                b.root.setOnClickListener { onClick(row) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Ayah Circle Medallion Span (ensures every Ayah number is crisp and measurable)
    // ------------------------------------------------------------------

    internal class AyahMedallionSpan(
        digits: String,
        private val ringColor: Int,
        private val fillColor: Int,
        private val digitColor: Int,
        private val arabicTypeface: Typeface?
    ) : ReplacementSpan() {

        // Al-Qalam Quran Majeed 4 stores Arabic numerals in Extended Arabic-Indic (U+06F0..U+06F9);
        // U+0661..U+0669 in that font have empty glyf tables.
        val displayDigits: String = if (arabicTypeface != null) {
            toHafeziFontDigits(digits)
        } else {
            digits
        }

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = ringColor
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = digitColor
            textAlign = Paint.Align.LEFT
        }
        private val rect = RectF()
        private val textBounds = android.graphics.Rect()

        private fun prepareNumPaint(basePaint: Paint): String {
            var activeText = displayDigits
            var activeTypeface = arabicTypeface
            numPaint.textSize = (basePaint.textSize * 0.64f).coerceAtLeast(11f)
            if (activeTypeface != null) {
                numPaint.typeface = activeTypeface
                if (!numPaint.hasGlyph(activeText.take(1))) {
                    activeTypeface = Typeface.DEFAULT_BOLD
                    activeText = toStandardArabicDigits(activeText)
                }
            }
            if (activeTypeface == null) {
                numPaint.typeface = Typeface.DEFAULT_BOLD
                activeText = toStandardArabicDigits(activeText)
            }
            numPaint.getTextBounds(activeText, 0, activeText.length, textBounds)
            return activeText
        }

        private fun computeGeometry(paint: Paint): Triple<Float, Float, Float> {
            val baseD = (paint.textSize * 0.96f).coerceAtLeast(20f)
            val activeText = prepareNumPaint(paint)
            val numW = max(numPaint.measureText(activeText), textBounds.width().toFloat())
            val boxW = max(baseD, numW + baseD * 0.40f)
            val sidePad = (paint.textSize * 0.12f).coerceAtLeast(2f)
            return Triple(boxW, baseD, sidePad)
        }

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val (boxW, _, sidePad) = computeGeometry(paint)
            return ceil(boxW + sidePad * 2f).toInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val (boxW, boxH, sidePad) = computeGeometry(paint)
            val activeText = prepareNumPaint(paint)
            val cx = x + sidePad + boxW / 2f
            val cy = y - paint.textSize * 0.28f

            rect.set(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f)
            canvas.drawOval(rect, fillPaint)

            val strokeW = (paint.textSize * 0.065f).coerceAtLeast(1.2f)
            ringPaint.strokeWidth = strokeW
            canvas.drawOval(rect, ringPaint)

            val inset = strokeW * 1.9f
            rect.inset(inset, inset)
            ringPaint.strokeWidth = strokeW * 0.65f
            canvas.drawOval(rect, ringPaint)

            val textX = cx - textBounds.exactCenterX()
            val baselineY = cy - textBounds.exactCenterY()
            canvas.drawText(activeText, textX, baselineY, numPaint)
        }

        private fun toHafeziFontDigits(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when (ch) {
                    in '0'..'9' -> sb.append((0x06F0 + (ch - '0')).toChar())
                    in '\u0660'..'\u0669' -> sb.append((0x06F0 + (ch.code - 0x0660)).toChar())
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        private fun toStandardArabicDigits(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when (ch) {
                    in '0'..'9' -> sb.append((0x0660 + (ch - '0')).toChar())
                    in '\u06F0'..'\u06F9' -> sb.append((0x0660 + (ch.code - 0x06F0)).toChar())
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }
    }

    // ------------------------------------------------------------------
    // 610-Page 15-Line Bangladeshi Hafezi Mushaf ViewPager2 Adapter
    // ------------------------------------------------------------------

    private class MushafPagerAdapter(
        private val context: Context,
        private val quranTypeface: Typeface?,
        private var fontScale: Float
    ) : RecyclerView.Adapter<MushafPagerAdapter.PageViewHolder>() {

        fun updateFontScale(newScale: Float) {
            fontScale = newScale
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val b = ItemMushafPageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return PageViewHolder(b)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.bind(position + 1)
        }

        override fun getItemCount(): Int = TOTAL_PAGES

        private data class FormattedHafeziLine(
            val spannable: SpannableStringBuilder,
            val interWordSpaces: List<Int>
        )

        inner class PageViewHolder(private val b: ItemMushafPageBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(pageNumber: Int) {
                QuranRepository.ensureHafeziLoaded(context)
                val pageData = kotlinx.coroutines.runBlocking {
                    QuranRepository.getHafeziMushafPage(context, pageNumber)
                }

                val para = QuranRepository.paraList.find { it.id == pageData.juzNumber }
                val paraAr = para?.nameArabic ?: ""
                val pageInPara = QuranRepository.getHafeziPageInPara(pageNumber)
                val paraTotal = QuranRepository.getHafeziParaTotalPages(pageData.juzNumber)

                b.tvMushafTopSurah.text = toHafeziHeaderDigits("${pageData.primarySurah.nameArabic} ${QuranRepository.toArabicNumber(pageData.primarySurah.id)}")
                b.tvMushafTopMeta.text = "( ${toBnNumPadded(pageData.juzNumber)} : ${toBnNumPadded(pageInPara)} )"
                b.tvMushafTopJuz.text = toHafeziHeaderDigits("$paraAr ${QuranRepository.toArabicNumber(pageData.juzNumber)}")
                b.tvMushafBottomManzil.text = toHafeziHeaderDigits("منزل ${QuranRepository.toArabicNumber(pageData.manzilNumber)}")
                b.tvMushafBottomPage.text = "﴿ ${QuranRepository.toArabicNumber(pageInPara)} / ${QuranRepository.toArabicNumber(paraTotal)} ﴾"
                b.tvMushafBottomLineHint.text = "মোট পৃষ্ঠা ${toBnNum(pageNumber)}/${toBnNum(TOTAL_PAGES)}"

                quranTypeface?.let {
                    b.tvMushafTopSurah.typeface = it
                    b.tvMushafTopJuz.typeface = it
                    b.tvMushafBottomManzil.typeface = it
                }
                b.tvMushafBottomPage.typeface = Typeface.DEFAULT_BOLD

                renderHafeziLines(pageData)
            }

            private fun renderHafeziLines(pageData: QuranRepository.HafeziMushafPage) {
                val container = b.llMushafPageContent
                val marginContainer = b.llMushafMarginMarks
                container.removeAllViews()
                marginContainer.removeAllViews()

                val primaryColor = ContextCompat.getColor(context, R.color.dua_primary_dark)
                val medallionRingColor = ContextCompat.getColor(context, R.color.gold_accent)
                val medallionFillColor = ContextCompat.getColor(context, R.color.dua_soft_gold)
                val waqfColor = ContextCompat.getColor(context, R.color.dua_primary)
                val inkColor = 0xFF181714.toInt()
                val ruleColor = 0x337A6F5A
                val dp = context.resources.displayMetrics.density
                val isOpeningPage = pageData.pageNumber <= 2

                if (isOpeningPage) {
                    container.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.35f
                        )
                    })
                    marginContainer.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.35f
                        )
                    })
                } else {
                    container.addView(createRuleView(dp, ruleColor))
                }

                for (line in pageData.lines) {
                    val rowLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        val rowParams = if (isOpeningPage) {
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        } else {
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                0,
                                1f
                            )
                        }
                        layoutParams = rowParams
                        val minRowDp = if (isOpeningPage) 36 else 32
                        minimumHeight = (minRowDp * dp).toInt()
                        if (isOpeningPage) {
                            setPadding((10 * dp).toInt(), (3 * dp).toInt(), (10 * dp).toInt(), (3 * dp).toInt())
                        } else {
                            setPadding(0, (1 * dp).toInt(), 0, (1 * dp).toInt())
                        }
                    }

                    // Aligned outer gutter cell so Ruku/Sajdah badges never narrow inner Mushaf lines
                    val marginSlot = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        layoutParams = if (isOpeningPage) {
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                (36 * dp).toInt()
                            )
                        } else {
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                0,
                                1f
                            )
                        }
                    }
                    if (line is QuranRepository.HafeziLine.VerseLine && line.marginMark != null) {
                        marginSlot.addView(TextView(context).apply {
                            text = line.marginMark
                            setTextColor(primaryColor)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                            setTypeface(quranTypeface, Typeface.BOLD)
                            gravity = Gravity.CENTER
                        })
                    }
                    marginContainer.addView(marginSlot)

                    when (line) {
                        is QuranRepository.HafeziLine.SurahHeader -> {
                            val surah = QuranRepository.surahList.find { it.id == line.surahId }
                            val revAr = if (surah?.revelationType == "মাদানী") "مدنية" else "مكية"
                            val headerText = if (isOpeningPage) {
                                "(${QuranRepository.toArabicNumber(line.surahId)}) سورة ${surah?.nameArabic.orEmpty()} $revAr\nآياتها ${QuranRepository.toArabicNumber(line.totalAyahs)} • ركوعاتها ${QuranRepository.toArabicNumber(line.rukuCount)}"
                            } else {
                                "۞ سورة ${surah?.nameArabic.orEmpty()} $revAr • آياتها ${QuranRepository.toArabicNumber(line.totalAyahs)} ۞"
                            }
                            val banner = TextView(context).apply {
                                text = headerText
                                setTextColor(primaryColor)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isOpeningPage) 16.5f else 14.5f)
                                setTypeface(quranTypeface, Typeface.BOLD)
                                gravity = Gravity.CENTER
                                textDirection = View.TEXT_DIRECTION_RTL
                                setBackgroundResource(R.drawable.bg_quran_chip)
                                val pV = ((if (isOpeningPage) 8 else 2) * dp).toInt()
                                val pH = (8 * dp).toInt()
                                setPadding(pH, pV, pH, pV)
                                maxLines = if (isOpeningPage) 2 else 1
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                            }
                            rowLayout.addView(banner)
                        }

                        is QuranRepository.HafeziLine.Bismillah -> {
                            val bis = TextView(context).apply {
                                text = "بِسْمِ اللّٰهِ الرَّحْمٰنِ الرَّحِيْمِ"
                                setTextColor(inkColor)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, (20f * fontScale).coerceIn(15f, 25f))
                                quranTypeface?.let { typeface = it }
                                gravity = Gravity.CENTER
                                textDirection = View.TEXT_DIRECTION_RTL
                                maxLines = 1
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                            }
                            rowLayout.addView(bis)
                        }

                        is QuranRepository.HafeziLine.BlankLine -> {
                            rowLayout.addView(View(context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                                )
                            })
                        }

                        is QuranRepository.HafeziLine.VerseLine -> {
                            val formatted = buildFormattedLine(
                                rawTokensText = line.textWithTokens,
                                waqfColor = waqfColor,
                                ringColor = medallionRingColor,
                                fillColor = medallionFillColor,
                                digitColor = inkColor
                            )

                            val baseSp = if (isOpeningPage) 21.0f * fontScale else 20.0f * fontScale
                            val justifyThisLine = formatted.interWordSpaces.isNotEmpty()
                            val lineTv = TextView(context).apply {
                                text = formatted.spannable
                                setTextColor(inkColor)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp.coerceIn(14f, 27f))
                                quranTypeface?.let { typeface = it }
                                textDirection = View.TEXT_DIRECTION_RTL
                                gravity = if (justifyThisLine) {
                                    Gravity.CENTER_VERTICAL or Gravity.START
                                } else {
                                    Gravity.CENTER
                                }
                                maxLines = 1
                                includeFontPadding = false
                                setPadding((2 * dp).toInt(), 0, (2 * dp).toInt(), 0)
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                            }

                            fitSingleHafeziLine(
                                tv = lineTv,
                                baseSp = baseSp.coerceIn(14f, 27f),
                                spaceIndices = formatted.interWordSpaces,
                                justify = justifyThisLine
                            )

                            rowLayout.addView(lineTv)
                        }
                    }

                    container.addView(rowLayout)
                    if (!isOpeningPage || line is QuranRepository.HafeziLine.VerseLine) {
                        container.addView(createRuleView(dp, ruleColor))
                    }
                }

                if (isOpeningPage) {
                    container.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.35f
                        )
                    })
                    marginContainer.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.35f
                        )
                    })
                }
            }

            private fun createRuleView(dp: Float, color: Int): View {
                return View(context).apply {
                    setBackgroundColor(color)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        Math.max(1, (0.8f * dp).toInt())
                    )
                }
            }

            private fun buildFormattedLine(
                rawTokensText: String,
                waqfColor: Int,
                ringColor: Int,
                fillColor: Int,
                digitColor: Int
            ): FormattedHafeziLine {
                val ssb = SpannableStringBuilder()
                val wordSpaces = ArrayList<Int>()
                val allSpaces = ArrayList<Int>()
                val parts = rawTokensText.split(" ").filter { it.isNotEmpty() }

                for (part in parts) {
                    val isWaqf = part.startsWith("@")
                    val isAyahNum = part.startsWith("﴿")
                    if (ssb.isNotEmpty()) {
                        val spIdx = ssb.length
                        allSpaces.add(spIdx)
                        if (!isWaqf && !isAyahNum) {
                            wordSpaces.add(spIdx)
                        }
                        ssb.append(" ")
                    }

                    val start = ssb.length
                    if (isWaqf) {
                        val symbol = part.substring(1)
                        ssb.append(symbol)
                        ssb.setSpan(ForegroundColorSpan(waqfColor), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        ssb.setSpan(RelativeSizeSpan(0.66f), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else if (isAyahNum) {
                        val digits = part.removePrefix("﴿").removeSuffix("﴾")
                        ssb.append(part)
                        ssb.setSpan(
                            AyahMedallionSpan(
                                digits = digits,
                                ringColor = ringColor,
                                fillColor = fillColor,
                                digitColor = digitColor,
                                arabicTypeface = quranTypeface
                            ),
                            start,
                            ssb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } else {
                        ssb.append(part)
                    }
                }

                val justifySpaces = if (wordSpaces.size >= 2) wordSpaces else allSpaces
                return FormattedHafeziLine(ssb, justifySpaces)
            }

            private fun fitSingleHafeziLine(
                tv: TextView,
                baseSp: Float,
                spaceIndices: List<Int>,
                justify: Boolean
            ) {
                tv.addOnLayoutChangeListener { v, left, _, right, _, _, _, _, _ ->
                    val availW = (right - left - tv.paddingLeft - tv.paddingRight).toFloat()
                    if (availW <= 4f) return@addOnLayoutChangeListener

                    val tagKey = "${availW.toInt()}_${baseSp}_${tv.text.hashCode()}"
                    if (tv.tag == tagKey) return@addOnLayoutChangeListener
                    tv.tag = tagKey

                    val ssb = (tv.text as? SpannableStringBuilder)
                        ?: SpannableStringBuilder(tv.text)
                    val oldSpans = ssb.getSpans(0, ssb.length, ScaleXSpan::class.java)
                    for (sp in oldSpans) ssb.removeSpan(sp)

                    val metrics = v.resources.displayMetrics
                    val basePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, baseSp, metrics)
                    val minPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10.5f, metrics)

                    val paint = TextPaint(tv.paint)
                    paint.textSize = basePx
                    paint.textScaleX = 1.0f

                    var fittedPx = basePx
                    var measuredW = Layout.getDesiredWidth(ssb, paint)

                    if (measuredW > availW * 0.992f) {
                        fittedPx = (basePx * (availW * 0.99f / measuredW)).coerceAtLeast(minPx)
                        paint.textSize = fittedPx
                        measuredW = Layout.getDesiredWidth(ssb, paint)
                        if (measuredW > availW * 0.995f) {
                            fittedPx = (fittedPx * (availW * 0.99f / measuredW)).coerceAtLeast(minPx)
                            paint.textSize = fittedPx
                            measuredW = Layout.getDesiredWidth(ssb, paint)
                        }
                    }

                    var targetScaleX = 1.0f
                    if (justify && spaceIndices.isNotEmpty() && measuredW < availW * 0.992f) {
                        if (measuredW < availW * 0.72f) {
                            targetScaleX = (availW * 0.76f / measuredW).coerceIn(1.0f, 1.15f)
                            paint.textScaleX = targetScaleX
                            measuredW = Layout.getDesiredWidth(ssb, paint)
                        }

                        val remaining = availW * 0.995f - measuredW
                        if (remaining > 1f) {
                            val baseSpaceW = paint.measureText(" ").coerceAtLeast(1f)
                            val totalSpacesW = spaceIndices.size * baseSpaceW
                            val scale = (1f + remaining / totalSpacesW).coerceIn(1f, 14f)
                            for (idx in spaceIndices) {
                                if (idx in 0 until ssb.length) {
                                    ssb.setSpan(ScaleXSpan(scale), idx, idx + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                }
                            }
                            val finalW = Layout.getDesiredWidth(ssb, paint)
                            if (finalW > availW * 0.996f && scale > 1.01f) {
                                val excess = finalW - availW * 0.993f
                                val adjusted = (scale - excess / totalSpacesW).coerceAtLeast(1.0f)
                                for (sp in ssb.getSpans(0, ssb.length, ScaleXSpan::class.java)) {
                                    ssb.removeSpan(sp)
                                }
                                for (idx in spaceIndices) {
                                    if (idx in 0 until ssb.length) {
                                        ssb.setSpan(ScaleXSpan(adjusted), idx, idx + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    }
                                }
                            }
                        }
                    }

                    v.post {
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, fittedPx)
                        tv.textScaleX = targetScaleX
                        tv.text = ssb
                    }
                }
            }

            private fun toBnNumPadded(num: Int): String {
                val padded = num.toString().padStart(2, '0')
                val digits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
                val sb = StringBuilder()
                for (ch in padded) {
                    if (ch.isDigit()) sb.append(digits[ch - '0']) else sb.append(ch)
                }
                return sb.toString()
            }

            private fun toBnNum(num: Int): String {
                val digits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
                val sb = StringBuilder()
                for (ch in num.toString()) {
                    if (ch.isDigit()) sb.append(digits[ch - '0']) else sb.append(ch)
                }
                return sb.toString()
            }

            private fun toHafeziHeaderDigits(input: String): String {
                if (quranTypeface == null) return input
                val sb = StringBuilder(input.length)
                for (ch in input) {
                    if (ch in '\u0660'..'\u0669') {
                        sb.append((0x06F0 + (ch.code - 0x0660)).toChar())
                    } else {
                        sb.append(ch)
                    }
                }
                return sb.toString()
            }
        }
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
        const val EXTRA_SINGLE_CATEGORY = "extra_single_category"
        const val EXTRA_PARA_PAGE_SEARCH = "extra_para_page_search"
        const val EXTRA_OPEN_LAST_PAGE = "extra_open_last_page"
        const val EXTRA_PAGE_NUMBER = "extra_page_number"

        const val TAB_SURAH = 0
        const val TAB_PARA = 1
        const val TAB_PAGE = 2
        const val TOTAL_PAGES = QuranRepository.HAFEZI_TOTAL_PAGES

        private const val QURAN_PREFS = "IslamicAlarmQuranPrefs"
        private const val KEY_LAST_OFFLINE_PAGE = "last_offline_page"
        private const val KEY_HAFEZI_FONT_SCALE = "offline_hafezi_font_scale"
    }
}
