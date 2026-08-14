package com.hafij.islamicalarm.books

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.databinding.ItemBookChapterBinding

class ChapterAdapter(
    private var chapters: List<BookChapter>,
    private val onTtsPlay: (String) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder>() {

    var fontScaleDelta: Float = 0f

    fun updateList(newList: List<BookChapter>) {
        chapters = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val binding = ItemBookChapterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChapterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        holder.bind(chapters[position])
    }

    override fun getItemCount(): Int = chapters.size

    inner class ChapterViewHolder(private val binding: ItemBookChapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chapter: BookChapter) {
            val context = binding.root.context

            binding.tvChapterBadge.text = "পরিচ্ছেদ ${chapter.chapterNo}"
            binding.tvChapterTitleBn.text = chapter.titleBn

            // Dynamic font sizing
            val baseTitleSize = 17f + fontScaleDelta
            val baseContentSize = 15f + fontScaleDelta
            val baseArabicSize = 20f + fontScaleDelta

            binding.tvChapterTitleBn.textSize = baseTitleSize
            binding.tvChapterContentBn.textSize = baseContentSize
            binding.tvChapterArabic.textSize = baseArabicSize

            if (chapter.arabicText.isNotBlank()) {
                binding.tvChapterArabic.visibility = View.VISIBLE
                binding.tvChapterArabic.text = chapter.arabicText
            } else {
                binding.tvChapterArabic.visibility = View.GONE
            }

            binding.tvChapterContentBn.text = chapter.contentBn

            if (chapter.explanationBn.isNotBlank()) {
                binding.layoutExplanation.visibility = View.VISIBLE
                binding.tvChapterExplanation.text = chapter.explanationBn
            } else {
                binding.layoutExplanation.visibility = View.GONE
            }

            if (chapter.reference.isNotBlank()) {
                binding.tvChapterReference.visibility = View.VISIBLE
                binding.tvChapterReference.text = "সূত্র: ${chapter.reference}"
            } else {
                binding.tvChapterReference.visibility = View.GONE
            }

            // Copy Action
            binding.btnCopyChapter.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val textToCopy = buildString {
                    append(chapter.titleBn).append("\n\n")
                    if (chapter.arabicText.isNotBlank()) {
                        append(chapter.arabicText).append("\n\n")
                    }
                    append(chapter.contentBn).append("\n\n")
                    if (chapter.explanationBn.isNotBlank()) {
                        append("তাৎপর্য: ").append(chapter.explanationBn).append("\n\n")
                    }
                    if (chapter.reference.isNotBlank()) {
                        append("সূত্র: ").append(chapter.reference)
                    }
                }
                val clip = ClipData.newPlainText("Islamic Chapter", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "অনুলিপি (কপি) করা হয়েছে", Toast.LENGTH_SHORT).show()
            }

            // Share Action
            binding.btnShareChapter.apply {
                setOnClickListener {
                    val shareText = buildString {
                        append("📖 ").append(chapter.titleBn).append("\n\n")
                        if (chapter.arabicText.isNotBlank()) {
                            append(chapter.arabicText).append("\n\n")
                        }
                        append(chapter.contentBn).append("\n\n")
                        if (chapter.explanationBn.isNotBlank()) {
                            append("তাৎপর্য: ").append(chapter.explanationBn).append("\n\n")
                        }
                        if (chapter.reference.isNotBlank()) {
                            append("সূত্র: ").append(chapter.reference).append("\n\n")
                        }
                        append("— ইসলামিক এলার্ম ও লাইব্রেরি অ্যাপ")
                    }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "অধ্যায় শেয়ার করুন"))
                }
            }

            // TTS Audio Listen
            binding.btnTtsListen.setOnClickListener {
                val speakText = "${chapter.titleBn}। ${chapter.contentBn}। ${chapter.explanationBn}"
                onTtsPlay(speakText)
            }
        }
    }
}
