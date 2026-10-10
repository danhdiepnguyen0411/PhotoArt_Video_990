package com.example.phortart_video_990.ui.screen.template

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.databinding.ItemTemplateCardBinding

class TemplateAdapter(
    private var items: List<TemplateModel>,
    private val onItemClick: (TemplateModel) -> Unit
) : RecyclerView.Adapter<TemplateAdapter.TemplateViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
        val binding = ItemTemplateCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TemplateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newList: List<TemplateModel>) {
        items = newList
        notifyDataSetChanged()
    }

    inner class TemplateViewHolder(private val binding: ItemTemplateCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateModel) {
            binding.tvTemplateTitle.text = item.title
            binding.tvDuration.text = item.duration
            binding.tvViews.text = item.views
            binding.tvPhotoCount.text = item.photoCount

            // Load thumbnail image using Coil
            val imageSource = item.safeImageUrl ?: item.localImageRes ?: R.drawable.template_trendy
            binding.ivTemplateThumb.load(imageSource) {
                crossfade(true)
                placeholder(R.drawable.template_trendy)
                error(item.localImageRes ?: R.drawable.template_trendy)
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
