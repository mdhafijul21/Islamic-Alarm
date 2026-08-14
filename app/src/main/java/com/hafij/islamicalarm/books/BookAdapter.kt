package com.hafij.islamicalarm.books

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.databinding.ItemIslamicBookBinding

class BookAdapter(
    private var books: List<BookItem>,
    private val onBookClick: (BookItem) -> Unit
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    fun updateList(newList: List<BookItem>) {
        books = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemIslamicBookBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(books[position])
    }

    override fun getItemCount(): Int = books.size

    inner class BookViewHolder(private val binding: ItemIslamicBookBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(book: BookItem) {
            binding.tvBookTitleBn.text = book.titleBn
            binding.tvBookTitleAr.text = book.titleAr
            binding.tvBookCategoryBadge.text = book.category
            binding.tvBookAuthor.text = "লেখক: ${book.authorBn}"
            binding.tvBookSummary.text = book.summaryBn
            binding.tvBookChapterCount.text = "📖 ${book.chapters.size}টি নির্বাচিত অধ্যায় (অফলাইন)"

            binding.btnReadBook.setOnClickListener {
                onBookClick(book)
            }

            binding.root.setOnClickListener {
                onBookClick(book)
            }
        }
    }
}
