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
        val prevIndex = categories.indexOfFirst { it.code.equals(selectedCategoryCode, ignoreCase = true) }
        selectedCategoryCode = catCode
        val nextIndex = categories.indexOfFirst { it.code.equals(selectedCategoryCode, ignoreCase = true) }

        if (prevIndex != -1) notifyItemChanged(prevIndex)
        if (nextIndex != -1) notifyItemChanged(nextIndex)
    }

    inner class CategoryViewHolder(private val binding: ItemTemplateCategoryChipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: CategoryModel) {
            val displayName = getLocalizedCategoryName(binding.root.context, category)
            binding.tvCategoryName.text = displayName
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

    companion object {
        fun getLocalizedCategoryName(context: android.content.Context, category: CategoryModel): String {
            val code = category.code.trim().uppercase()
            val name = category.name.trim().uppercase()
            return when {
                code == "ALL" || name == "TẤT CẢ" || name == "ALL" || code.isBlank() ->
                    context.getString(R.string.template_cat_all)
                code.contains("TRANSITION") || name.contains("TRANSITION") ->
                    context.getString(R.string.dance_cat_transition)
                code.contains("LATIN") || name.contains("LATIN") ->
                    context.getString(R.string.dance_cat_latin)
                code.contains("TIKTOK") || name.contains("TIKTOK") ->
                    context.getString(R.string.dance_cat_tiktok)
                code.contains("KPOP") || name.contains("KPOP") || code.contains("K-POP") || name.contains("K-POP") ->
                    context.getString(R.string.dance_cat_kpop)
                code.contains("HIPHOP") || name.contains("HIPHOP") || code.contains("HIP HOP") || name.contains("HIP HOP") ->
                    context.getString(R.string.dance_cat_hiphop)
                code.contains("SHUFFLE") || name.contains("SHUFFLE") ->
                    context.getString(R.string.dance_cat_shuffle)
                code.contains("STREET") || name.contains("STREET") ->
                    context.getString(R.string.dance_cat_street)
                code.contains("WAACKING") || name.contains("WAACKING") ->
                    context.getString(R.string.dance_cat_waacking)
                code.contains("JAZZ") || name.contains("JAZZ") ->
                    context.getString(R.string.dance_cat_jazz)
                code.contains("HOT") || name.contains("HOT") ->
                    context.getString(R.string.dance_cat_hot)
                code.contains("ANIME") || name.contains("ANIME") ->
                    context.getString(R.string.template_cat_anime)
                code.contains("EFFECT") || name.contains("EFFECT") ->
                    context.getString(R.string.template_cat_effect)
                code.contains("PORTRAIT") || name.contains("PORTRAIT") ->
                    context.getString(R.string.template_cat_portrait)
                code.contains("FANTASY") || name.contains("FANTASY") ->
                    context.getString(R.string.template_cat_fantasy)
                code.contains("DANCE") || name.contains("DANCE") ->
                    context.getString(R.string.template_cat_dance)
                else -> category.displayName
            }
        }
    }
}
