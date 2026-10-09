package com.example.phortart_video_990.ui.screen.history

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.phortart_video_990.R
import com.example.phortart_video_990.databinding.ItemHistoryFilterChipBinding

class HistoryFilterAdapter(
    private val filterList: List<HistoryFilterItem>,
    private var selectedTitle: String,
    private val onFilterSelected: (String) -> Unit
) : RecyclerView.Adapter<HistoryFilterAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryFilterChipBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(filterList[position])
    }

    override fun getItemCount(): Int = filterList.size

    inner class ViewHolder(private val binding: ItemHistoryFilterChipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryFilterItem) {
            val isSelected = item.title == selectedTitle

            binding.tvChipTitle.text = item.title
            binding.ivChipIcon.setImageResource(item.iconRes)

            if (isSelected) {
                binding.llFilterChip.setBackgroundResource(R.drawable.bg_history_chip_selected)
                binding.tvChipTitle.setTextColor(Color.WHITE)
                binding.ivChipIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
            } else {
                binding.llFilterChip.setBackgroundResource(R.drawable.bg_history_chip_unselected)
                val unselectedColor = Color.parseColor("#5A6F90")
                binding.tvChipTitle.setTextColor(unselectedColor)
                binding.ivChipIcon.imageTintList = ColorStateList.valueOf(unselectedColor)
            }

            binding.root.setOnClickListener {
                if (selectedTitle != item.title) {
                    selectedTitle = item.title
                    notifyDataSetChanged()
                    onFilterSelected(item.title)
                }
            }
        }
    }
}
