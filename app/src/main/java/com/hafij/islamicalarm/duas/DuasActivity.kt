package com.hafij.islamicalarm.duas

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityDuasBinding

class DuasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDuasBinding
    private lateinit var duaAdapter: DuaAdapter
    private var selectedCategory = DuaData.CATEGORY_ALL
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDuasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarDuas.setNavigationOnClickListener {
            finish()
        }

        setupRecyclerView()
        setupCategories()
        setupSearch()
        filterList()
    }

    private fun setupRecyclerView() {
        duaAdapter = DuaAdapter(this)
        binding.rvDuas.layoutManager = LinearLayoutManager(this)
        binding.rvDuas.adapter = duaAdapter
    }

    private fun setupCategories() {
        val categories = DuaData.getCategories
        categories.forEachIndexed { index, cat ->
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
                    filterList()
                }
            }
            binding.chipGroupDuaCategories.addView(chip)
        }
    }

    private fun setupSearch() {
        binding.etSearchDua.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString().trim()
                filterList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterList() {
        val allList = DuaData.getDuasAndHadiths
        val filtered = allList.filter { item ->
            val matchesCategory = (selectedCategory == DuaData.CATEGORY_ALL || item.category == selectedCategory)
            val matchesSearch = searchQuery.isEmpty() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.arabic.contains(searchQuery, ignoreCase = true) ||
                    item.transliteration.contains(searchQuery, ignoreCase = true) ||
                    item.translation.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
        duaAdapter.submitList(filtered)
    }
}
