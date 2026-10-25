package com.hafij.islamicalarm.books

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.databinding.ItemRemoteLibraryBookBinding

class RemoteLibraryAdapter(
    private var books: List<RemoteLibraryBook>,
    private val isDownloaded: (RemoteLibraryBook) -> Boolean,
    private val isDownloading: (RemoteLibraryBook) -> Boolean,
    private val onDownloadClick: (RemoteLibraryBook) -> Unit,
    private val onOpenClick: (RemoteLibraryBook) -> Unit
) : RecyclerView.Adapter<RemoteLibraryAdapter.BookViewHolder>() {

    fun updateBooks(newBooks: List<RemoteLibraryBook>) {
        books = newBooks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemRemoteLibraryBookBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        val b = holder.binding

        b.tvRemoteBookTitle.text = book.titleBn.ifBlank { book.titleEn }

        val authorText = book.authorNameEn.ifBlank { null }
        val pagesText = if (book.pages > 0) "${book.pages} পৃষ্ঠা" else null
        b.tvRemoteBookMeta.text = listOfNotNull(authorText, pagesText).joinToString(" • ")
            .ifBlank { book.genre }

        when {
            isDownloading(book) -> {
                b.btnRemoteBookAction.text = "⏳ নামছে..."
                b.btnRemoteBookAction.isEnabled = false
                b.btnRemoteBookAction.setOnClickListener(null)
            }
            isDownloaded(book) -> {
                b.btnRemoteBookAction.text = "📖 পড়ুন"
                b.btnRemoteBookAction.isEnabled = true
                b.btnRemoteBookAction.setOnClickListener { onOpenClick(book) }
            }
            else -> {
                b.btnRemoteBookAction.text = "⬇ ডাউনলোড"
                b.btnRemoteBookAction.isEnabled = true
                b.btnRemoteBookAction.setOnClickListener { onDownloadClick(book) }
            }
        }
    }

    override fun getItemCount(): Int = books.size

    class BookViewHolder(val binding: ItemRemoteLibraryBookBinding) : RecyclerView.ViewHolder(binding.root)
}
