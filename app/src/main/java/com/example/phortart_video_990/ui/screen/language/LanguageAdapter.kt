package com.example.phortart_video_990.ui.screen.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.model.LanguageModel
import com.example.phortart_video_990.databinding.ItemLanguageBinding

class LanguageAdapter(
    private val items: List<LanguageModel>,
    private val onItemSelected: (LanguageModel) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    private var selectedPosition = items.indexOfFirst { it.isSelected }.let { if (it >= 0) it else 0 }

    init {
        if (items.isNotEmpty()) {
            items[selectedPosition].isSelected = true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val binding = ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LanguageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(items[position], position == selectedPosition)
    }

    override fun getItemCount(): Int = items.size

    fun getSelectedItem(): LanguageModel? {
        return if (selectedPosition in items.indices) items[selectedPosition] else null
    }

    inner class LanguageViewHolder(private val binding: ItemLanguageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LanguageModel, isSelected: Boolean) {
            binding.tvLanguageFlag.text = item.flag
            binding.tvLanguageName.text = item.name

            if (isSelected) {
                binding.cardLanguage.setBackgroundResource(R.drawable.bg_item_language_selected)
                binding.ivCheck.visibility = View.VISIBLE
            } else {
                binding.cardLanguage.setBackgroundResource(R.drawable.bg_item_language)
                binding.ivCheck.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val previousPosition = selectedPosition
                    selectedPosition = pos
                    items[previousPosition].isSelected = false
                    items[selectedPosition].isSelected = true
                    notifyItemChanged(previousPosition)
                    notifyItemChanged(selectedPosition)
                    onItemSelected(items[selectedPosition])
                }
            }
        }
    }
}
