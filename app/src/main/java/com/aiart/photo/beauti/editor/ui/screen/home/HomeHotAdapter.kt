package com.aiart.photo.beauti.editor.ui.screen.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.utils.ShimmerDrawable
import com.aiart.photo.beauti.editor.data.model.TemplateModel
import com.aiart.photo.beauti.editor.databinding.ItemHomeHotCardBinding

class HomeHotAdapter(
    private var items: List<TemplateModel>,
    private val onItemClick: (TemplateModel) -> Unit,
    private val onPlayClick: ((TemplateModel, Int) -> Unit)? = null
) : RecyclerView.Adapter<HomeHotAdapter.HotViewHolder>() {

    private var playingPosition: Int = -1
    private var loadingPosition: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HotViewHolder {
        val binding = ItemHomeHotCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HotViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newList: List<TemplateModel>) {
        items = newList
        notifyDataSetChanged()
    }

    fun setPlayingState(position: Int, isPlaying: Boolean) {
        val old = playingPosition
        playingPosition = if (isPlaying) position else -1
        loadingPosition = -1
        if (old != -1 && old < items.size) notifyItemChanged(old)
        if (position != -1 && position < items.size) notifyItemChanged(position)
    }

    fun setLoadingState(position: Int) {
        val old = loadingPosition
        loadingPosition = position
        if (old != -1 && old < items.size) notifyItemChanged(old)
        if (position != -1 && position < items.size) notifyItemChanged(position)
    }

    inner class HotViewHolder(private val binding: ItemHomeHotCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateModel, position: Int) {
            binding.tvTemplateTitle.text = item.title
            binding.tvViews.text = item.displayViews

            // Image Thumbnail with shimmer loading light beam (NO static template images as placeholder)
            val shimmerPlaceholder = ShimmerDrawable()
            val imageSource: Any = item.safeImageUrl?.takeIf { it.isNotBlank() }
                ?: item.localImageRes
                ?: ""

            binding.ivTemplateThumb.load(imageSource) {
                crossfade(true)
                placeholder(shimmerPlaceholder)
                error(shimmerPlaceholder)
            }

            binding.pbLoading.visibility = View.GONE
            binding.ivPlayIcon.visibility = View.GONE

            binding.ivPlayIcon.isClickable = false
            binding.ivPlayIcon.isFocusable = false

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
