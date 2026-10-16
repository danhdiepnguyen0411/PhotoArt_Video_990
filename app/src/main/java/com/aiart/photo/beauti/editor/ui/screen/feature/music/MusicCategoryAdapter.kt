package com.aiart.photo.beauti.editor.ui.screen.feature.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.data.model.CategoryModel
import com.aiart.photo.beauti.editor.databinding.ItemMusicCategoryChipBinding

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
            val displayName = com.aiart.photo.beauti.editor.ui.screen.template.CategoryAdapter.getLocalizedCategoryName(
                binding.root.context, category
            )
            binding.tvCategoryName.text = displayName

            val isSelected = category.code.equals(selectedCategoryCode, ignoreCase = true) ||
                    (selectedCategoryCode == "ALL" && (category.code.equals("ALL", ignoreCase = true) || category.code.isBlank()))

            if (isSelected) {
                binding.llCategoryChip.setBackgroundResource(R.drawable.bg_category_chip_selected)
                binding.tvCategoryName.setTextColor(android.graphics.Color.WHITE)
            } else {
                binding.llCategoryChip.setBackgroundResource(R.drawable.bg_category_chip_unselected)
                binding.tvCategoryName.setTextColor(android.graphics.Color.parseColor("#7A8FB8"))
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
