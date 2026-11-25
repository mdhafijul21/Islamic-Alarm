package com.hafij.islamicalarm.books

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.databinding.ItemLibraryBookGridBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class RemoteLibraryAdapter(
    private var books: List<RemoteLibraryBook>,
    private val scope: CoroutineScope,
    private val isDownloaded: (RemoteLibraryBook) -> Boolean,
    private val isDownloading: (RemoteLibraryBook) -> Boolean,
    private val showDeleteButton: () -> Boolean,
    private val onBookClick: (RemoteLibraryBook) -> Unit,
    private val onDeleteClick: (RemoteLibraryBook) -> Unit
) : RecyclerView.Adapter<RemoteLibraryAdapter.BookViewHolder>() {

    fun updateBooks(newBooks: List<RemoteLibraryBook>) {
        books = newBooks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemLibraryBookGridBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        val b = holder.binding
        val displayTitle = book.titleBn.ifBlank { book.titleEn.ifBlank { book.titleAr } }

        b.tvGridBookTitle.text = displayTitle
        b.tvCoverCenterTitle.text = displayTitle
        b.tvCoverBadge.text = shortJamatBadge(book.jamatCode)

        val downloading = isDownloading(book)
        val downloaded = isDownloaded(book)
        b.tvCoverSource.text = when {
            downloading -> "ডাউনলোড হচ্ছে..."
            downloaded -> "অফলাইন সংরক্ষিত"
            else -> book.sourceName.ifBlank { "PDF" }
        }

        val paletteColor = COVER_COLORS[abs(book.slug.hashCode()) % COVER_COLORS.size]
        b.frameBookCover.setBackgroundColor(paletteColor)

        b.badgeBookDownloaded.visibility = if (downloaded) View.VISIBLE else View.GONE
        b.btnDeleteDownloadedBook.visibility =
            if (showDeleteButton() && downloaded) View.VISIBLE else View.GONE

        b.btnDeleteDownloadedBook.setOnClickListener { onDeleteClick(book) }
        b.cardBookGridItem.setOnClickListener { onBookClick(book) }

        val coverUrl = book.coverUrl
        b.ivBookCover.tag = book.slug
        if (coverUrl.isBlank()) {
            b.ivBookCover.visibility = View.GONE
            b.ivBookCover.setImageDrawable(null)
            b.layoutGeneratedCover.visibility = View.VISIBLE
        } else {
            val cached = coverMemoryCache.get(coverUrl)
            if (cached != null && !cached.isRecycled) {
                b.ivBookCover.setImageBitmap(cached)
                b.ivBookCover.visibility = View.VISIBLE
                b.layoutGeneratedCover.visibility = View.GONE
            } else {
                b.ivBookCover.visibility = View.GONE
                b.ivBookCover.setImageDrawable(null)
                b.layoutGeneratedCover.visibility = View.VISIBLE

                scope.launch {
                    val bmp = fetchCoverBitmap(coverUrl)
                    if (bmp != null && b.ivBookCover.tag == book.slug) {
                        b.ivBookCover.setImageBitmap(bmp)
                        b.ivBookCover.visibility = View.VISIBLE
                        b.layoutGeneratedCover.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = books.size

    private suspend fun fetchCoverBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        coverMemoryCache.get(url)?.let { return@withContext it }
        if (failedCoverUrls.contains(url)) return@withContext null
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 7000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "IslamicAlarm-App/1.0")
            }
            if (conn.responseCode in 200..299) {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it, null, options) }
                if (bmp != null) {
                    coverMemoryCache.put(url, bmp)
                    return@withContext bmp
                }
            }
            failedCoverUrls.add(url)
            null
        } catch (_: Exception) {
            failedCoverUrls.add(url)
            null
        }
    }

    private fun shortJamatBadge(code: String): String = when (code) {
        RemoteLibraryBook.CAT_STORIES -> "গল্প ও কাহিনি"
        RemoteLibraryBook.CAT_SAHABA_HISTORY -> "জীবনী ও ইতিহাস"
        RemoteLibraryBook.CAT_SEERAH_TAFSIR -> "সিরাত ও তাফসীর"
        RemoteLibraryBook.JAMAT_DAWRA -> "দাওরা"
        RemoteLibraryBook.JAMAT_MISHKAT -> "মেশকাত"
        RemoteLibraryBook.JAMAT_JALALAIN -> "জালালাইন"
        RemoteLibraryBook.JAMAT_SHARHE_BEKAYA -> "শরহে বেকায়া"
        RemoteLibraryBook.JAMAT_SHARHE_JAMI -> "শরহে জামি"
        RemoteLibraryBook.JAMAT_KAFIYA -> "কাফিয়া"
        RemoteLibraryBook.JAMAT_HEDAYATUNNAHU -> "হেদায়াতুন্নাহু"
        RemoteLibraryBook.JAMAT_NAHBEMIR -> "নাহবেমীর"
        RemoteLibraryBook.JAMAT_MIZAN -> "মীযান"
        RemoteLibraryBook.JAMAT_TAISIR, RemoteLibraryBook.JAMAT_MAKTAB -> "তাইসীর"
        RemoteLibraryBook.JAMAT_CLASSICAL_AR -> "ওপেন/ক্লাসিক"
        else -> "ইসলামিক বই"
    }

    class BookViewHolder(val binding: ItemLibraryBookGridBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        private val COVER_COLORS = intArrayOf(
            Color.parseColor("#0F4C5C"),
            Color.parseColor("#6B2737"),
            Color.parseColor("#1D3557"),
            Color.parseColor("#8A5A17"),
            Color.parseColor("#3B2854"),
            Color.parseColor("#2A5C6A"),
            Color.parseColor("#4A3F35")
        )

        private val coverMemoryCache = LruCache<String, Bitmap>(60)
        private val failedCoverUrls = java.util.Collections.synchronizedSet(HashSet<String>())
    }
}
