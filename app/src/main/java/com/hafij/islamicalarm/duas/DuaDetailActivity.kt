package com.hafij.islamicalarm.duas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hafij.islamicalarm.databinding.ActivityDuaDetailBinding

class DuaDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "dua_id"
        const val EXTRA_TITLE = "dua_title"
        const val EXTRA_CATEGORY = "dua_category"
        const val EXTRA_ARABIC = "dua_arabic"
        const val EXTRA_TRANSLITERATION = "dua_transliteration"
        const val EXTRA_TRANSLATION = "dua_translation"
        const val EXTRA_REFERENCE = "dua_reference"
        const val EXTRA_WHEN = "dua_when"
        const val EXTRA_COUNT = "dua_count"
        const val EXTRA_VIRTUE = "dua_virtue"
        const val EXTRA_GRADE = "dua_grade"
        const val EXTRA_TYPE = "dua_type"
        const val EXTRA_URL = "dua_url"
    }

    private lateinit var binding: ActivityDuaDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDuaDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarDuaDetail.setNavigationOnClickListener { finish() }

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
        val arabic = intent.getStringExtra(EXTRA_ARABIC).orEmpty()
        val transliteration = intent.getStringExtra(EXTRA_TRANSLITERATION).orEmpty()
        val translation = intent.getStringExtra(EXTRA_TRANSLATION).orEmpty()
        val reference = intent.getStringExtra(EXTRA_REFERENCE).orEmpty()
        val whenToRead = intent.getStringExtra(EXTRA_WHEN).orEmpty()
        val count = intent.getStringExtra(EXTRA_COUNT).orEmpty()
        val virtue = intent.getStringExtra(EXTRA_VIRTUE).orEmpty()
        val grade = intent.getStringExtra(EXTRA_GRADE).orEmpty()
        val contentType = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()

        binding.toolbarDuaDetail.title =
            if (contentType == "HADITH") "হাদিসের বিস্তারিত" else "দোয়ার বিস্তারিত"
        binding.tvDetailTitle.text = title
        binding.tvDetailCategory.text = category
        setOptional(binding.tvDetailArabic, arabic)
        setOptional(binding.tvDetailTransliteration, if (transliteration.isBlank()) "" else "উচ্চারণ\n$transliteration")
        setOptional(binding.tvDetailTranslation, if (translation.isBlank()) "" else "অর্থ / বিস্তারিত\n$translation")
        setOptional(binding.tvDetailWhen, if (whenToRead.isBlank()) "" else "কখন পড়বেন: $whenToRead")
        setOptional(binding.tvDetailCount, if (count.isBlank()) "" else "সংখ্যা / পদ্ধতি: $count")
        setOptional(binding.tvDetailVirtue, if (virtue.isBlank()) "" else "ফজিলত / নোট\n$virtue")
        setOptional(binding.tvDetailGrade, if (grade.isBlank()) "" else "বর্ণনার মান: $grade")
        setOptional(binding.tvDetailReference, if (reference.isBlank()) "" else "রেফারেন্স: $reference")
        setOptional(binding.tvDetailUrl, url)

        val duaId = intent.getIntExtra(EXTRA_ID, -1)
        val favoritePrefs = getSharedPreferences("dua_favorites", MODE_PRIVATE)
        val savedIds = favoritePrefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        fun refreshFavoriteLabel() {
            binding.btnDetailFavorite.text =
                if (duaId.toString() in savedIds) "পছন্দের তালিকা থেকে সরান" else "পছন্দের তালিকায় রাখুন"
        }
        refreshFavoriteLabel()
        binding.btnDetailFavorite.setOnClickListener {
            if (duaId >= 0) {
                if (duaId.toString() in savedIds) savedIds.remove(duaId.toString())
                else savedIds.add(duaId.toString())
                favoritePrefs.edit().putStringSet("ids", savedIds.toSet()).apply()
                refreshFavoriteLabel()
                Toast.makeText(
                    this,
                    if (duaId.toString() in savedIds) "পছন্দের তালিকায় যোগ হয়েছে" else "পছন্দের তালিকা থেকে সরানো হয়েছে",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val shareText = buildString {
            append(title).append("\n").append(category).append("\n\n")
            if (arabic.isNotBlank()) append(arabic).append("\n\n")
            if (transliteration.isNotBlank()) append("উচ্চারণ: ").append(transliteration).append("\n")
            if (translation.isNotBlank()) append("অর্থ: ").append(translation).append("\n")
            if (whenToRead.isNotBlank()) append("কখন: ").append(whenToRead).append("\n")
            if (count.isNotBlank()) append("সংখ্যা / পদ্ধতি: ").append(count).append("\n")
            if (virtue.isNotBlank()) append("ফজিলত: ").append(virtue).append("\n")
            if (grade.isNotBlank()) append("মান: ").append(grade).append("\n")
            if (reference.isNotBlank()) append("রেফারেন্স: ").append(reference).append("\n")
            if (url.isNotBlank()) append(url).append("\n")
            append("\n— Islamic Alarm")
        }

        binding.btnDetailCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Dua", shareText))
            Toast.makeText(this, "কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
        }
        binding.btnDetailShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(shareIntent, "শেয়ার করুন"))
        }
    }

    private fun setOptional(view: TextView, value: String) {
        if (value.isBlank()) {
            view.visibility = android.view.View.GONE
        } else {
            view.visibility = android.view.View.VISIBLE
            view.text = value
        }
    }
}
