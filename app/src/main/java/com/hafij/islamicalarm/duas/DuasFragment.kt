package com.hafij.islamicalarm.duas

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityDuasBinding

class DuasFragment : Fragment() {

    private lateinit var binding: ActivityDuasBinding
    private lateinit var duaAdapter: DuaAdapter
    private lateinit var categoryAdapter: DuaCategoryAdapter
    private var selectedCategory = DuaData.CATEGORY_ALL
    private var searchQuery = ""
    private val favoritesCategory = "পছন্দের দোয়া"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val themed = inflater.cloneInContext(
            android.view.ContextThemeWrapper(requireContext(), R.style.Theme_IslamicAlarm_Duas)
        )
        binding = ActivityDuasBinding.inflate(themed, container, false)
        return binding.root
    }

    /**
     * Back hierarchy:
     * detail Activity -> previous dua list (handled by Android finish),
     * dua list -> category grid,
     * category grid -> app home (handled by MainActivity).
     */
    fun handleBack(): Boolean {
        if (!::binding.isInitialized) return false
        if (binding.rvDuas.visibility == View.VISIBLE) {
            selectedCategory = DuaData.CATEGORY_ALL
            searchQuery = ""
            if (binding.etSearchDua.text.isNotEmpty()) {
                binding.etSearchDua.setText("")
            } else {
                showCategories()
            }
            return true
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarDuas.setNavigationOnClickListener {
            if (!handleBack()) {
                (activity as? com.hafij.islamicalarm.MainActivity)?.goHome()
            }
        }

        setupRecyclerViews()
        setupCategories()
        setupSearch()
        showCategories()
    }

    override fun onResume() {
        super.onResume()
        if (::categoryAdapter.isInitialized) {
            setupCategories()
            if (binding.rvDuas.visibility == View.VISIBLE) filterList()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && ::categoryAdapter.isInitialized) {
            setupCategories()
            if (binding.rvDuas.visibility == View.VISIBLE) filterList()
        }
    }

    private fun setupRecyclerViews() {
        duaAdapter = DuaAdapter()
        binding.rvDuas.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDuas.adapter = duaAdapter

        categoryAdapter = DuaCategoryAdapter { category ->
            selectedCategory = category
            showDuaList()
            filterList()
        }
        binding.rvDuaCategories.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvDuaCategories.adapter = categoryAdapter
    }

    private fun setupCategories() {
        val allDuas = DuaData.getDuasAndHadiths
        val categories = DuaData.getCategories.map { category ->
            val count = if (category == DuaData.CATEGORY_ALL) {
                allDuas.size
            } else {
                allDuas.count { it.category == category }
            }
            DuaCategory(category, count, DuaCategoryArtwork.iconFor(category))
        }.toMutableList()

        val favoriteIds = requireContext()
            .getSharedPreferences("dua_favorites", Context.MODE_PRIVATE)
            .getStringSet("ids", emptySet()) ?: emptySet()

        categories.add(
            1,
            DuaCategory(
                favoritesCategory,
                favoriteIds.count { id -> allDuas.any { it.id.toString() == id } },
                DuaCategoryArtwork.iconFor(favoritesCategory)
            )
        )
        categoryAdapter.submitList(categories)
    }

    private fun setupSearch() {
        binding.etSearchDua.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                if (searchQuery.isNotEmpty() && binding.rvDuas.visibility != View.VISIBLE) {
                    selectedCategory = DuaData.CATEGORY_ALL
                    showDuaList()
                } else if (searchQuery.isEmpty() && selectedCategory == DuaData.CATEGORY_ALL) {
                    showCategories()
                    return
                }
                filterList()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun showCategories() {
        binding.rvDuaCategories.visibility = View.VISIBLE
        binding.rvDuas.visibility = View.GONE
        binding.tvDuaEmpty.visibility = View.GONE
        binding.tvDuaSectionTitle.text = "বিষয় অনুযায়ী দোয়া ও আমল"
        binding.tvDuaSectionSubtitle.text = "আপনার প্রয়োজনীয় বিভাগটি বেছে নিন"
        binding.toolbarDuas.title = "মাসনুন দোয়া ও আমল"
        binding.etSearchDua.hint = "দোয়া বা বিষয় খুঁজুন..."
        binding.duaHomeScroll.smoothScrollTo(0, 0)
    }

    private fun showDuaList() {
        binding.rvDuaCategories.visibility = View.GONE
        binding.rvDuas.visibility = View.VISIBLE
        binding.tvDuaEmpty.visibility = View.GONE
        binding.tvDuaSectionTitle.text =
            if (selectedCategory == DuaData.CATEGORY_ALL) "সব দোয়া ও আমল" else selectedCategory
        binding.tvDuaSectionSubtitle.text = "যেকোনো এন্ট্রিতে চাপ দিয়ে বিস্তারিত পড়ুন"
        binding.toolbarDuas.title =
            if (selectedCategory == DuaData.CATEGORY_ALL) "সব দোয়া ও আমল" else selectedCategory
        binding.duaHomeScroll.smoothScrollTo(0, 0)
    }

    private fun filterList() {
        val favoriteIds = requireContext()
            .getSharedPreferences("dua_favorites", Context.MODE_PRIVATE)
            .getStringSet("ids", emptySet()) ?: emptySet()

        val filtered = DuaData.getDuasAndHadiths.filter { item ->
            val matchesCategory = when (selectedCategory) {
                DuaData.CATEGORY_ALL -> true
                favoritesCategory -> item.id.toString() in favoriteIds
                else -> item.category == selectedCategory
            }
            val matchesSearch = searchQuery.isEmpty() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true) ||
                item.arabic.contains(searchQuery, ignoreCase = true) ||
                item.transliteration.contains(searchQuery, ignoreCase = true) ||
                item.translation.contains(searchQuery, ignoreCase = true) ||
                item.reference.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }

        duaAdapter.submitList(filtered)
        binding.tvDuaEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.tvDuaEmpty.text = when {
            selectedCategory == favoritesCategory && filtered.isEmpty() ->
                "এখনো কোনো দোয়া পছন্দের তালিকায় যোগ করা হয়নি।"
            else -> "কোনো দোয়া পাওয়া যায়নি। অন্য শব্দ দিয়ে খুঁজে দেখুন।"
        }
    }
}
