package com.example.phortart_video_990.ui.screen.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.databinding.ItemHomeRecentHistoryCardBinding

class HomeRecentHistoryAdapter(
    private var items: List<HistoryItemModel>,
    private val onItemClick: (HistoryItemModel) -> Unit
) : RecyclerView.Adapter<HomeRecentHistoryAdapter.HistoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHomeRecentHistoryCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newList: List<HistoryItemModel>) {
        items = newList
        notifyDataSetChanged()
    }

    inner class HistoryViewHolder(private val binding: ItemHomeRecentHistoryCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItemModel) {
            binding.tvHistoryTitle.text = item.getLocalizedTitle(binding.root.context)
            binding.tvHistoryDate.text = item.displayDate

            val rawSource: Any? = item.imageUrl ?: item.imageUri ?: if (item.imageRes != 0) item.imageRes else null
            val source: Any? = when {
                rawSource is String && rawSource.startsWith("/") -> java.io.File(rawSource)
                rawSource is String && rawSource.startsWith("content://") -> android.net.Uri.parse(rawSource)
                else -> rawSource ?: R.drawable.bg_card_white
            }

            binding.ivHistoryThumb.load(source) {
                crossfade(true)
                placeholder(R.drawable.bg_card_white)
                error(R.drawable.bg_card_white)
            }

            val dur = item.displayDuration
            if (item.isVideo && dur.isNotBlank()) {
                binding.llDurationPill.visibility = View.VISIBLE
                binding.tvDuration.text = dur
            } else {
                binding.llDurationPill.visibility = View.GONE
            }
            binding.llPhotoCountPill.visibility = View.GONE
            binding.llPhotoOnlyPill.visibility = View.GONE

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
