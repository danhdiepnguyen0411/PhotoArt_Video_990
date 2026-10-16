package com.aiart.photo.beauti.editor.ui.screen.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.data.model.HistoryItemModel
import com.aiart.photo.beauti.editor.databinding.ItemHistoryGridSquareBinding
import com.aiart.photo.beauti.editor.databinding.ItemHistoryRowBinding

class HistoryAdapter(
    private var items: List<HistoryItemModel>,
    private val onItemClick: (HistoryItemModel) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_ROW = 0
        const val VIEW_TYPE_GRID = 1
    }

    private var isGridMode: Boolean = false

    fun setGridMode(grid: Boolean) {
        if (isGridMode != grid) {
            isGridMode = grid
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_GRID) {
            val binding = ItemHistoryGridSquareBinding.inflate(inflater, parent, false)
            GridViewHolder(binding)
        } else {
            val binding = ItemHistoryRowBinding.inflate(inflater, parent, false)
            RowViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is GridViewHolder) {
            holder.bind(item)
        } else if (holder is RowViewHolder) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<HistoryItemModel>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class GridViewHolder(private val binding: ItemHistoryGridSquareBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItemModel) {
            val rawSource: Any? = item.imageUrl ?: item.imageUri ?: if (item.imageRes != 0) item.imageRes else null
            val source: Any? = when {
                rawSource is String && rawSource.startsWith("/") -> java.io.File(rawSource)
                rawSource is String && rawSource.startsWith("content://") -> android.net.Uri.parse(rawSource)
                else -> rawSource
            }

            if (source != null) {
                binding.ivHistoryThumb.load(source) {
                    crossfade(true)
                    placeholder(R.drawable.bg_card_white)
                    error(R.drawable.bg_card_white)
                }
            } else {
                binding.ivHistoryThumb.setImageResource(R.drawable.bg_card_white)
            }

            binding.tvHistoryDate.text = item.getLocalizedGridDate(binding.root.context)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    inner class RowViewHolder(private val binding: ItemHistoryRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItemModel) {
            val rawSource: Any? = item.imageUrl ?: item.imageUri ?: if (item.imageRes != 0) item.imageRes else null
            val source: Any? = when {
                rawSource is String && rawSource.startsWith("/") -> java.io.File(rawSource)
                rawSource is String && rawSource.startsWith("content://") -> android.net.Uri.parse(rawSource)
                else -> rawSource
            }

            if (source != null) {
                binding.ivHistoryThumb.load(source) {
                    crossfade(true)
                    placeholder(R.drawable.bg_card_white)
                    error(R.drawable.bg_card_white)
                }
            } else {
                binding.ivHistoryThumb.setImageResource(R.drawable.bg_card_white)
            }

            binding.tvHistoryTitle.text = item.getLocalizedTitle(binding.root.context)
            binding.tvHistoryDate.text = item.getLocalizedFullDate(binding.root.context)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
