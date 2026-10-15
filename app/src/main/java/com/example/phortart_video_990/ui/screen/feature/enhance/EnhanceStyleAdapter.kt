package com.example.phortart_video_990.ui.screen.feature.enhance

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.phortart_video_990.databinding.ItemEnhanceStyleBinding

data class EnhanceStyleModel(
    val id: String,
    val title: String,
    val imageResId: Int,
    val prompt: String,
    val isHot: Boolean = false
) {
    fun getLocalizedTitle(context: android.content.Context): String {
        return when (id) {
            "cyber_move" -> context.getString(com.example.phortart_video_990.R.string.style_cyber_move)
            "street_vibes" -> context.getString(com.example.phortart_video_990.R.string.style_street_vibes)
            "urban_renaissance" -> context.getString(com.example.phortart_video_990.R.string.style_urban_renaissance)
            "dreamy" -> context.getString(com.example.phortart_video_990.R.string.style_dreamy)
            "vintage" -> context.getString(com.example.phortart_video_990.R.string.style_vintage)
            "cyberpunk" -> context.getString(com.example.phortart_video_990.R.string.style_cyberpunk)
            "nature" -> context.getString(com.example.phortart_video_990.R.string.style_nature)
            "portrait" -> context.getString(com.example.phortart_video_990.R.string.style_portrait)
            "fantasy" -> context.getString(com.example.phortart_video_990.R.string.style_fantasy)
            "selfie" -> context.getString(com.example.phortart_video_990.R.string.style_selfie)
            "anime" -> context.getString(com.example.phortart_video_990.R.string.style_anime)
            "neon_glow" -> context.getString(com.example.phortart_video_990.R.string.style_neon_glow)
            else -> title
        }
    }

    fun getLocalizedPrompt(context: android.content.Context): String {
        return when (id) {
            "cyber_move" -> context.getString(com.example.phortart_video_990.R.string.prompt_style_cyber_move)
            "street_vibes" -> context.getString(com.example.phortart_video_990.R.string.prompt_style_street_vibes)
            "urban_renaissance" -> context.getString(com.example.phortart_video_990.R.string.prompt_style_urban_renaissance)
            "dreamy" -> context.getString(com.example.phortart_video_990.R.string.prompt_style_dreamy)
            "vintage" -> context.getString(com.example.phortart_video_990.R.string.prompt_style_vintage)
            "cyberpunk" -> context.getString(com.example.phortart_video_990.R.string.prompt_style_cyberpunk)
            "nature" -> context.getString(com.example.phortart_video_990.R.string.prompt_style_nature)
            "portrait" -> context.getString(com.example.phortart_video_990.R.string.prompt_style_portrait)
            "fantasy" -> context.getString(com.example.phortart_video_990.R.string.prompt_style_fantasy)
            "selfie" -> context.getString(com.example.phortart_video_990.R.string.prompt_style_selfie)
            else -> prompt
        }
    }
}

class EnhanceStyleAdapter(
    private val items: List<EnhanceStyleModel>,
    private var selectedIndex: Int = -1,
    private val onStyleSelected: (EnhanceStyleModel) -> Unit
) : RecyclerView.Adapter<EnhanceStyleAdapter.StyleViewHolder>() {

    fun setSelectedIndex(index: Int) {
        if (selectedIndex == index) return
        val prevIndex = selectedIndex
        selectedIndex = index
        if (prevIndex in items.indices) notifyItemChanged(prevIndex)
        if (selectedIndex in items.indices) notifyItemChanged(selectedIndex)
    }

    fun getSelectedIndex(): Int = selectedIndex

    fun getSelectedItem(): EnhanceStyleModel? = items.getOrNull(selectedIndex)

    inner class StyleViewHolder(val binding: ItemEnhanceStyleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EnhanceStyleModel, isSelected: Boolean) {
            binding.ivStyleThumb.setImageResource(item.imageResId)
            binding.tvStyleTitle.text = item.getLocalizedTitle(binding.root.context)
            binding.layoutHotBadge.visibility = if (item.isHot) View.VISIBLE else View.GONE

            val density = binding.root.context.resources.displayMetrics.density
            if (isSelected) {
                binding.cardStyle.strokeWidth = (2.5f * density).toInt()
                binding.cardStyle.strokeColor = Color.parseColor("#7F8CF6")
            } else {
                binding.cardStyle.strokeWidth = 0
                binding.cardStyle.strokeColor = Color.TRANSPARENT
            }

            binding.cardStyle.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    setSelectedIndex(pos)
                    onStyleSelected(item)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StyleViewHolder {
        val binding = ItemEnhanceStyleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StyleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StyleViewHolder, position: Int) {
        holder.bind(items[position], position == selectedIndex)
    }

    override fun getItemCount(): Int = items.size
}
