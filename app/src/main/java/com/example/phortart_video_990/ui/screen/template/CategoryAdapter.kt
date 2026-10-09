package com.example.phortart_video_990.ui.screen.template

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.phortart_video_990.R
import com.example.phortart_video_990.databinding.ItemTemplateCategoryChipBinding

class CategoryAdapter(
    private val categories: List<String>,
    private var selectedCategory: String,
    private val onCategoryClick: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemTemplateCategoryChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    fun setSelected(cat: String) {
        val prev = categories.indexOf(selectedCategory)
        selectedCategory = cat
        val next = categories.indexOf(selectedCategory)
        if (prev != -1) notifyItemChanged(prev)
        if (next != -1) notifyItemChanged(next)
    }

    inner class CategoryViewHolder(private val binding: ItemTemplateCategoryChipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: String) {
            binding.tvCategoryName.text = category
            val isSelected = category == selectedCategory

            if (isSelected) {
                binding.tvCategoryName.setBackgroundResource(R.drawable.bg_category_chip_selected)
                binding.tvCategoryName.setTextColor(binding.root.context.getColor(R.color.white))
            } else {
                binding.tvCategoryName.setBackgroundResource(R.drawable.bg_category_chip_unselected)
                binding.tvCategoryName.setTextColor(binding.root.context.getColor(R.color.muted))
            }

            binding.root.setOnClickListener {
                if (category != selectedCategory) {
                    setSelected(category)
                    onCategoryClick(category)
                }
            }
        }
    }
}
