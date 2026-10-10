package com.example.phortart_video_990.ui.screen.template

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.model.CategoryModel
import com.example.phortart_video_990.databinding.ItemTemplateCategoryChipBinding

class CategoryAdapter(
    private var categories: List<CategoryModel>,
    private var selectedCategoryCode: String,
    private val onCategoryClick: (CategoryModel) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemTemplateCategoryChipBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    fun submitList(newList: List<CategoryModel>) {
        categories = newList
        notifyDataSetChanged()
    }

    fun setSelected(catCode: String) {
        val prevIndex = categories.indexOfFirst { it.code == selectedCategoryCode }
        selectedCategoryCode = catCode
        val nextIndex = categories.indexOfFirst { it.code == selectedCategoryCode }

        if (prevIndex != -1) notifyItemChanged(prevIndex)
        if (nextIndex != -1) notifyItemChanged(nextIndex)
    }

    inner class CategoryViewHolder(private val binding: ItemTemplateCategoryChipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: CategoryModel) {
            binding.tvCategoryName.text = category.displayName
            val isSelected = category.code.equals(selectedCategoryCode, ignoreCase = true) ||
                    (selectedCategoryCode == "ALL" && (category.code.equals("ALL", ignoreCase = true) || category.code.isBlank()))

            if (isSelected) {
                binding.tvCategoryName.setBackgroundResource(R.drawable.bg_category_chip_selected)
                binding.tvCategoryName.setTextColor(Color.WHITE)
            } else {
                binding.tvCategoryName.setBackgroundResource(R.drawable.bg_category_chip_unselected)
                binding.tvCategoryName.setTextColor(Color.parseColor("#596579"))
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
