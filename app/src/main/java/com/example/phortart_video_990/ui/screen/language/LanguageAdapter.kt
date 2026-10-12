package com.example.phortart_video_990.ui.screen.language

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.model.LanguageModel
import com.example.phortart_video_990.databinding.ItemLanguageBinding

class LanguageAdapter(
    private val onItemSelected: (LanguageModel) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    private val items = mutableListOf<LanguageModel>()
    private var imageLoader: ImageLoader? = null

    private fun getImageLoader(context: Context): ImageLoader {
        return imageLoader ?: ImageLoader.Builder(context.applicationContext)
            .components {
                add(SvgDecoder.Factory())
            }
            .build().also { imageLoader = it }
    }

    fun submitList(newList: List<LanguageModel>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun getSelectedItem(): LanguageModel? {
        return items.find { it.isSelected }
    }

    fun getLanguageAt(position: Int): LanguageModel? {
        return if (position in items.indices) items[position] else null
    }

    fun selectLanguage(code: String) {
        val oldIndex = items.indexOfFirst { it.isSelected }
        val newIndex = items.indexOfFirst { it.code == code }
        if (oldIndex == newIndex) return
        if (oldIndex != -1) {
            items[oldIndex].isSelected = false
            notifyItemChanged(oldIndex)
        }
        if (newIndex != -1) {
            items[newIndex].isSelected = true
            notifyItemChanged(newIndex)
        }
    }

    /**
     * Cập nhật subtitle của các ngôn ngữ luôn bằng tiếng Anh,
     * bất kể ngôn ngữ nào đang được preview.
     */
    fun updateSubNames(context: Context, isRtl: Boolean) {
        // Tạo context với locale tiếng Anh cố định
        val enLocale = java.util.Locale.ENGLISH
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(enLocale)
        val enCtx = context.createConfigurationContext(config)

        items.forEach { item ->
            if (item.subResId != 0) {
                item.subName = enCtx.getString(item.subResId)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val binding = ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LanguageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class LanguageViewHolder(val binding: ItemLanguageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LanguageModel) {
            val context = binding.root.context
            val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL ||
                    context.resources.configuration.locale?.language?.lowercase() == "ar"
            val dir = if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

            binding.root.layoutDirection = dir
            binding.cardLanguage.layoutDirection = dir
            binding.tvLanguageName.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            binding.tvLanguageSub.textAlignment = View.TEXT_ALIGNMENT_VIEW_START

            // Load SVG flag from assets/flags using Coil SvgDecoder
            if (binding.imgFlag.tag != item.flagAsset) {
                binding.imgFlag.tag = item.flagAsset
                val request = ImageRequest.Builder(context)
                    .data("file:///android_asset/${item.flagAsset}")
                    .target(binding.imgFlag)
                    .build()
                getImageLoader(context).enqueue(request)
            }

            binding.tvLanguageName.text = item.name
            binding.tvLanguageSub.text = item.subName

            if (item.isSelected) {
                binding.cardLanguage.setBackgroundResource(R.drawable.bg_item_language_selected)
                binding.ivRadio.setImageResource(R.drawable.bg_radio_selected)
                binding.tvLanguageName.setTextColor(Color.parseColor("#5A2BD6"))
            } else {
                binding.cardLanguage.setBackgroundResource(R.drawable.bg_item_language)
                binding.ivRadio.setImageResource(R.drawable.bg_radio_unselected)
                binding.tvLanguageName.setTextColor(Color.parseColor("#2A34C9"))
            }

            binding.root.setOnClickListener {
                if (!item.isSelected) {
                    selectLanguage(item.code)
                    onItemSelected(item)
                }
            }
        }
    }
}
