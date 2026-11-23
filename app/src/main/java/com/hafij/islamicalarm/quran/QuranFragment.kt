package com.hafij.islamicalarm.quran

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.hafij.islamicalarm.databinding.ActivityQuranBinding

class QuranFragment : Fragment() {

    private lateinit var binding: ActivityQuranBinding
    private lateinit var surahAdapter: SurahAdapter
    private lateinit var paraAdapter: ParaAdapter

    private var activeTabIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivityQuranBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbarQuran.setNavigationOnClickListener {
            showDashboard()
        }

        // "অর্থ সহ" holds the Quran with Arabic + Bengali meaning
        binding.cardCatMeaning.setOnClickListener { showBrowser(0) }
        binding.cardCatSurah.setOnClickListener {
            openOfflineArabicQuran(
                initialTab = OfflineArabicQuranActivity.TAB_SURAH,
                singleCategory = true
            )
        }
        binding.cardCatPara.setOnClickListener {
            openOfflineArabicQuran(
                initialTab = OfflineArabicQuranActivity.TAB_PARA,
                singleCategory = true
            )
        }
        binding.cardCatSearch.setOnClickListener {
            openOfflineArabicQuran(
                initialTab = OfflineArabicQuranActivity.TAB_PAGE,
                singleCategory = true
            )
        }
        binding.cardCatAudioQuran.setOnClickListener {
            startActivity(
                Intent(
                    requireContext(),
                    com.hafij.islamicalarm.audio.AudioQuranActivity::class.java
                )
            )
        }
        binding.cardDashboardLatest.setOnClickListener { openLastRead() }
        binding.btnDashboardContinue.setOnClickListener { openLastRead() }

        setupSurahRecyclerView()
        setupParaRecyclerView()
        setupSearch()
        setupTabs()
        setupBookmarkCard()
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) updateLastReadCard()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) updateLastReadCard()
    }

    private fun setupBookmarkCard() {
        updateLastReadCard()
        binding.btnContinueLastRead.setOnClickListener { openLastRead() }
        binding.cardLastRead.setOnClickListener { openLastRead() }
    }

    private fun openLastRead() {
        val prefs = requireContext().getSharedPreferences("IslamicAlarmQuranPrefs", Context.MODE_PRIVATE)
        val lastSurahId = prefs.getInt("last_read_surah_id", 1)
        val intent = Intent(requireContext(), SurahDetailActivity::class.java).apply {
            putExtra("EXTRA_SURAH_ID", lastSurahId)
        }
        startActivity(intent)
    }

    private fun openOfflineArabicQuran(
        initialTab: Int = OfflineArabicQuranActivity.TAB_SURAH,
        openLastPage: Boolean = false,
        singleCategory: Boolean = false
    ) {
        val intent = Intent(requireContext(), OfflineArabicQuranActivity::class.java).apply {
            putExtra(OfflineArabicQuranActivity.EXTRA_INITIAL_TAB, initialTab)
            putExtra(OfflineArabicQuranActivity.EXTRA_OPEN_LAST_PAGE, openLastPage)
            putExtra(OfflineArabicQuranActivity.EXTRA_SINGLE_CATEGORY, singleCategory)
        }
        startActivity(intent)
    }

    fun handleBack(): Boolean {
        if (!::binding.isInitialized) return false
        if (binding.layoutQuranBrowser.visibility == View.VISIBLE) {
            showDashboard()
            return true
        }
        return false
    }

    private fun showDashboard() {
        binding.layoutQuranDashboard.visibility = View.VISIBLE
        binding.appBarLayout.visibility = View.GONE
        binding.layoutQuranBrowser.visibility = View.GONE
        updateLastReadCard()
    }

    private fun showBrowser(tabIndex: Int) {
        binding.layoutQuranDashboard.visibility = View.GONE
        binding.appBarLayout.visibility = View.VISIBLE
        binding.layoutQuranBrowser.visibility = View.VISIBLE
        binding.tabLayout.getTabAt(tabIndex)?.select()
    }

    private fun updateLastReadCard() {
        val prefs = requireContext().getSharedPreferences("IslamicAlarmQuranPrefs", Context.MODE_PRIVATE)
        val lastSurahId = prefs.getInt("last_read_surah_id", 1)
        val surah = QuranRepository.surahList.find { it.id == lastSurahId } ?: QuranRepository.surahList.first()
        binding.tvLastReadTitle.text = "সুরা ${surah.nameBangla} (${surah.nameArabic}) • ${toBengaliNumber(surah.totalAyahs)} আয়াত • ${surah.revelationType}"
        binding.tvDashboardLastReadTitle.text = "সুরা ${surah.nameBangla}"
        val lastPage = prefs.getInt("last_read_page", QuranRepository.getHafeziStartPageForSurah(surah.id))
            .coerceIn(1, QuranRepository.HAFEZI_TOTAL_PAGES)
        val juz = QuranRepository.getHafeziJuzForPage(lastPage)
        binding.tvDashboardLastReadMeta.text =
            "পারা ${toBengaliNumber(juz)} • পৃষ্ঠা ${toBengaliNumber(lastPage)}"
        binding.pbQuranProgress.progress = surah.id
        binding.tvDashboardCount.text = "${toBengaliNumber(surah.id)} / ${toBengaliNumber(114)}"
        binding.tvQuranProgressPercent.text =
            "${toBengaliNumber((surah.id * 100 / 114).coerceAtLeast(1))}%"
    }

    private fun setupSurahRecyclerView() {
        surahAdapter = SurahAdapter(
            surahList = QuranRepository.surahList,
            onReadPage = { surah ->
                val intent = Intent(requireContext(), SurahDetailActivity::class.java).apply {
                    putExtra("EXTRA_SURAH_ID", surah.id)
                }
                startActivity(intent)
            }
        )

        binding.rvSurahList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSurahList.adapter = surahAdapter
    }

    private fun setupParaRecyclerView() {
        paraAdapter = ParaAdapter(
            paraList = QuranRepository.paraList,
            onParaClick = { para ->
                val surah = QuranRepository.getSurahForPage(para.startPage)
                val intent = Intent(requireContext(), SurahDetailActivity::class.java).apply {
                    putExtra("EXTRA_SURAH_ID", surah.id)
                }
                startActivity(intent)
            }
        )

        binding.rvParaList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvParaList.adapter = paraAdapter
    }

    private fun setupSearch() {
        binding.etSearchSurah.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                performSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchSurah.text?.clear()
            hideKeyboard()
        }
    }

    private fun performSearch(query: String) {
        if (activeTabIndex == 0) {
            filterSurahList(query)
        } else {
            filterParaList(query)
        }
    }

    private fun filterSurahList(query: String) {
        if (query.isEmpty()) {
            surahAdapter.updateData(QuranRepository.surahList)
            return
        }

        val filtered = QuranRepository.surahList.filter { surah ->
            surah.nameBangla.contains(query, ignoreCase = true) ||
                    surah.nameEnglish.contains(query, ignoreCase = true) ||
                    surah.nameArabic.contains(query, ignoreCase = true) ||
                    surah.meaningBangla.contains(query, ignoreCase = true) ||
                    surah.id.toString() == query ||
                    surah.paraNumber.toString() == query ||
                    toBengaliNumber(surah.id) == query ||
                    toBengaliNumber(surah.paraNumber) == query
        }

        surahAdapter.updateData(filtered)
    }

    private fun filterParaList(query: String) {
        if (query.isEmpty()) {
            paraAdapter.updateData(QuranRepository.paraList)
            return
        }

        val filtered = QuranRepository.paraList.filter { para ->
            para.nameBangla.contains(query, ignoreCase = true) ||
                    para.nameArabic.contains(query, ignoreCase = true) ||
                    para.meaningBangla.contains(query, ignoreCase = true) ||
                    para.id.toString() == query ||
                    toBengaliNumber(para.id) == query
        }

        paraAdapter.updateData(filtered)
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                activeTabIndex = tab?.position ?: 0
                when (activeTabIndex) {
                    0 -> {
                        binding.layoutSurahTab.visibility = View.VISIBLE
                        binding.layoutParaTab.visibility = View.GONE
                        binding.etSearchSurah.hint = "সুরা বা নম্বর দিয়ে খুঁজুন..."
                    }
                    1 -> {
                        binding.layoutSurahTab.visibility = View.GONE
                        binding.layoutParaTab.visibility = View.VISIBLE
                        binding.etSearchSurah.hint = "পারার নাম বা নম্বর দিয়ে খুঁজুন..."
                    }
                }
                val currentQuery = binding.etSearchSurah.text?.toString()?.trim() ?: ""
                performSearch(currentQuery)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        activity?.currentFocus?.let {
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun toBengaliNumber(number: Int): String {
        val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val str = number.toString()
        val sb = StringBuilder()
        for (ch in str) {
            if (ch.isDigit()) {
                sb.append(banglaDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
