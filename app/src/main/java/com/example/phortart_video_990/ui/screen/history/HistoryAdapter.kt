package com.example.phortart_video_990.ui.screen.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.databinding.ItemHistoryGridBinding
import com.example.phortart_video_990.databinding.ItemHistoryRowBinding

class HistoryAdapter(
    private var items: List<HistoryItemModel>,
    private var isGridMode: Boolean = false,
    private val onItemClick: (HistoryItemModel) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ROW = 0
        private const val TYPE_GRID = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) TYPE_GRID else TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GRID) {
            val binding = ItemHistoryGridBinding.inflate(inflater, parent, false)
            GridViewHolder(binding)
        } else {
            val binding = ItemHistoryRowBinding.inflate(inflater, parent, false)
            RowViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is RowViewHolder) {
            holder.bind(item)
        } else if (holder is GridViewHolder) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<HistoryItemModel>, grid: Boolean) {
        items = newItems
        isGridMode = grid
        notifyDataSetChanged()
    }

    inner class RowViewHolder(private val binding: ItemHistoryRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItemModel) {
            binding.ivHistoryThumb.setImageResource(item.imageRes)
            binding.tvHistoryTitle.text = item.title
            binding.tvHistoryDate.text = item.date
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    inner class GridViewHolder(private val binding: ItemHistoryGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItemModel) {
            binding.ivGridThumb.setImageResource(item.imageRes)
            binding.tvGridTime.text = item.date.split("•").firstOrNull()?.trim() ?: item.date
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
