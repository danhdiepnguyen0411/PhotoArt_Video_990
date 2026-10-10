package com.example.phortart_video_990.ui.screen.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.databinding.ItemHistoryRowBinding

class HistoryAdapter(
    private var items: List<HistoryItemModel>,
    private val onItemClick: (HistoryItemModel) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.RowViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemHistoryRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<HistoryItemModel>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class RowViewHolder(private val binding: ItemHistoryRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItemModel) {
            val source: Any? = item.imageUri ?: item.imageUrl ?: if (item.imageRes != 0) item.imageRes else null

            if (source != null) {
                binding.ivHistoryThumb.load(source) {
                    crossfade(true)
                    placeholder(R.drawable.bg_card_white)
                    error(R.drawable.bg_card_white)
                }
            } else {
                binding.ivHistoryThumb.setImageResource(R.drawable.bg_card_white)
            }

            val isVideo = item.type.equals("Gom ảnh", ignoreCase = true) ||
                    item.type.equals("Mẫu AI", ignoreCase = true) ||
                    item.imageUri?.endsWith(".mp4", ignoreCase = true) == true

            binding.ivVideoBadge.visibility = if (isVideo) android.view.View.VISIBLE else android.view.View.GONE

            binding.tvHistoryTitle.text = item.title
            binding.tvHistoryDate.text = item.date
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
