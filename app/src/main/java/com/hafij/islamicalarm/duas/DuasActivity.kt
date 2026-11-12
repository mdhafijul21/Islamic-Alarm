package com.hafij.islamicalarm.duas

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityDuasBinding

class DuasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDuasBinding
    private lateinit var duaAdapter: DuaAdapter
    private lateinit var categoryAdapter: DuaCategoryAdapter
    private var selectedCategory = DuaData.CATEGORY_ALL
    private var searchQuery = ""
    private val favoritesCategory = "পছন্দের দোয়া"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDuasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarDuas.setNavigationOnClickListener {
            if (binding.rvDuas.visibility == android.view.View.VISIBLE) {
                selectedCategory = DuaData.CATEGORY_ALL
                if (binding.etSearchDua.text.isNotEmpty()) binding.etSearchDua.setText("")
                showCategories()
            } else {
                finish()
            }
        }

        setupRecyclerViews()
        setupCategories()
        setupSearch()
        showCategories()
    }

    override fun onResume() {
        super.onResume()
        if (::categoryAdapter.isInitialized) setupCategories()
    }

    private fun setupRecyclerViews() {
        duaAdapter = DuaAdapter()
        binding.rvDuas.layoutManager = LinearLayoutManager(this)
        binding.rvDuas.adapter = duaAdapter

        categoryAdapter = DuaCategoryAdapter { category ->
            selectedCategory = category
            showDuaList()
            filterList()
        }
        binding.rvDuaCategories.layoutManager = GridLayoutManager(this, 2)
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
            DuaCategory(category, count, imageForCategory(category))
        }.toMutableList()

        val favoriteIds = getSharedPreferences("dua_favorites", MODE_PRIVATE)
            .getStringSet("ids", emptySet()) ?: emptySet()
        categories.add(
            1,
            DuaCategory(favoritesCategory, favoriteIds.size, R.drawable.dua_cat_quran)
        )
        categoryAdapter.submitList(categories)
    }

    private fun setupSearch() {
        binding.etSearchDua.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                if (searchQuery.isNotEmpty() && binding.rvDuas.visibility != android.view.View.VISIBLE) {
                    selectedCategory = DuaData.CATEGORY_ALL
                    showDuaList()
                    filterList()
                } else if (searchQuery.isEmpty() && selectedCategory == DuaData.CATEGORY_ALL) {
                    showCategories()
                } else {
                    filterList()
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun showCategories() {
        binding.rvDuaCategories.visibility = android.view.View.VISIBLE
        binding.rvDuas.visibility = android.view.View.GONE
        binding.tvDuaEmpty.visibility = android.view.View.GONE
        binding.tvDuaSectionTitle.text = "বিষয় অনুযায়ী দোয়া"
        binding.tvDuaSectionSubtitle.text = "আপনার প্রয়োজনীয় বিভাগটি বেছে নিন"
        binding.toolbarDuas.title = "মাসনুন দোয়া ও হাদিস"
        binding.etSearchDua.setHint("দোয়া বা বিষয় খুঁজুন...")
        binding.duaHomeScroll.smoothScrollTo(0, 0)
    }

    private fun showDuaList() {
        binding.rvDuaCategories.visibility = android.view.View.GONE
        binding.rvDuas.visibility = android.view.View.VISIBLE
        binding.tvDuaEmpty.visibility = android.view.View.GONE
        binding.tvDuaSectionTitle.text =
            if (selectedCategory == DuaData.CATEGORY_ALL) "সব দোয়া ও হাদিস" else selectedCategory
        binding.tvDuaSectionSubtitle.text = "যেকোনো এন্ট্রিতে চাপ দিয়ে বিস্তারিত পড়ুন"
        binding.toolbarDuas.title =
            if (selectedCategory == DuaData.CATEGORY_ALL) "সব দোয়া ও হাদিস" else selectedCategory
        binding.duaHomeScroll.smoothScrollTo(0, 0)
    }

    private fun filterList() {
        val favoriteIds = getSharedPreferences("dua_favorites", MODE_PRIVATE)
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
        binding.tvDuaEmpty.visibility =
            if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvDuaEmpty.text = when {
            selectedCategory == favoritesCategory && filtered.isEmpty() ->
                "এখনো কোনো দোয়া পছন্দের তালিকায় যোগ করা হয়নি।"
            else -> "কোনো দোয়া পাওয়া যায়নি। অন্য শব্দ দিয়ে খুঁজে দেখুন।"
        }
    }

    private fun imageForCategory(category: String): Int = when (category) {
        DuaData.CATEGORY_ALL,
        DuaData.CATEGORY_MORNING_EVENING, DuaData.CATEGORY_AFTER_FARD,
        DuaData.CATEGORY_MOSQUE, DuaData.CATEGORY_FRIDAY -> R.drawable.dua_cat_mosque

        DuaData.CATEGORY_RABBANA, DuaData.CATEGORY_PROPHETS,
        DuaData.CATEGORY_TAWBAH, DuaData.CATEGORY_HADITH,
        DuaData.CATEGORY_PARENTS, DuaData.CATEGORY_FAMILY -> R.drawable.dua_cat_quran

        DuaData.CATEGORY_SLEEP, DuaData.CATEGORY_NAFL,
        DuaData.CATEGORY_TAHAJJUD, DuaData.CATEGORY_ISTIKHARA,
        DuaData.CATEGORY_IN_PRAYER, DuaData.CATEGORY_WUDU -> R.drawable.dua_cat_prayer

        DuaData.CATEGORY_FASTING, DuaData.CATEGORY_RAMADAN,
        DuaData.CATEGORY_HAJJ, DuaData.CATEGORY_CHARITY -> R.drawable.dua_cat_ramadan

        else -> R.drawable.dua_cat_prayer
    }
}
