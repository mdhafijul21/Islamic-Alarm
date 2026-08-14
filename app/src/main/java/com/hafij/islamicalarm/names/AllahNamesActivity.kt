package com.hafij.islamicalarm.names

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.hafij.islamicalarm.databinding.ActivityAllahNamesBinding

class AllahNamesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllahNamesBinding
    private lateinit var adapter: AllahNameAdapter
    private var allNames = AllahNamesData.names

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllahNamesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarAllahNames.setNavigationOnClickListener {
            finish()
        }

        adapter = AllahNameAdapter(this)
        binding.rvAllahNames.layoutManager = LinearLayoutManager(this)
        binding.rvAllahNames.adapter = adapter
        adapter.submitList(allNames)

        binding.etSearchName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterNames(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterNames(query: String) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            adapter.submitList(allNames)
        } else {
            val filtered = allNames.filter {
                it.transliterationBn.lowercase().contains(q) ||
                it.meaningBn.lowercase().contains(q) ||
                it.arabic.contains(q) ||
                it.virtueBn.lowercase().contains(q)
            }
            adapter.submitList(filtered)
        }
    }
}
