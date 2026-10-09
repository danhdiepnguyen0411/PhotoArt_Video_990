package com.example.phortart_video_990.ui.screen.feature.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.model.CategoryModel
import com.example.phortart_video_990.databinding.ItemMusicCategoryChipBinding

class MusicCategoryAdapter(
    private var categories: List<CategoryModel>,
    private var selectedCategoryCode: String,
    private val onCategoryClick: (CategoryModel) -> Unit
) : RecyclerView.Adapter<MusicCategoryAdapter.MusicCategoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicCategoryViewHolder {
        val binding = ItemMusicCategoryChipBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MusicCategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicCategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    fun submitList(newList: List<CategoryModel>) {
        categories = newList
        notifyDataSetChanged()
    }

    fun setSelected(catCode: String) {
        val prevIndex = categories.indexOfFirst { it.code.equals(selectedCategoryCode, ignoreCase = true) }
        selectedCategoryCode = catCode
        val nextIndex = categories.indexOfFirst { it.code.equals(selectedCategoryCode, ignoreCase = true) }

        if (prevIndex != -1) notifyItemChanged(prevIndex)
        if (nextIndex != -1) notifyItemChanged(nextIndex)
    }

    inner class MusicCategoryViewHolder(private val binding: ItemMusicCategoryChipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: CategoryModel) {
            val context = binding.root.context
            binding.tvCategoryName.text = category.displayName

            val isSelected = category.code.equals(selectedCategoryCode, ignoreCase = true) ||
                    (selectedCategoryCode == "ALL" && (category.code.equals("ALL", ignoreCase = true) || category.code.isBlank()))

            if (isSelected) {
                binding.llCategoryChip.setBackgroundResource(R.drawable.bg_category_chip_selected)
                binding.tvCategoryName.setTextColor(context.getColor(R.color.white))
                binding.ivMusicChipIcon.setColorFilter(context.getColor(R.color.white))
            } else {
                binding.llCategoryChip.setBackgroundResource(R.drawable.bg_category_chip_unselected)
                binding.tvCategoryName.setTextColor(context.getColor(R.color.muted))
                binding.ivMusicChipIcon.setColorFilter(context.getColor(R.color.muted))
            }

            binding.root.setOnClickListener {
                if (!category.code.equals(selectedCategoryCode, ignoreCase = true)) {
                    setSelected(category.code)
                    onCategoryClick(category)
                }
            }
        }
    }
}
