package com.example.phortart_video_990.ui.screen.template

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.utils.TemplateViewsHelper
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.databinding.ItemTemplateCardBinding

class TemplateAdapter(
    private var items: List<TemplateModel>,
    private val onItemClick: (TemplateModel) -> Unit,
    private val onPlayClick: ((TemplateModel, Int) -> Unit)? = null
) : RecyclerView.Adapter<TemplateAdapter.TemplateViewHolder>() {

    private var playingPosition: Int = -1
    private var loadingPosition: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
        val binding = ItemTemplateCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TemplateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
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

    inner class TemplateViewHolder(private val binding: ItemTemplateCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateModel, position: Int) {
            binding.tvTemplateTitle.text = item.title
            binding.tvDuration.text = item.displayDuration
            binding.tvPhotoCount.text = item.displayPhotoCount
            binding.tvViews.text = item.displayViews

            val isPlaying = position == playingPosition
            val isLoading = position == loadingPosition

            binding.ivPlayIcon.setImageResource(
                if (isPlaying) R.drawable.ic_pause_small else R.drawable.ic_play_small
            )
            val playColor = if (isPlaying) {
                android.graphics.Color.parseColor("#4A6BF8")
            } else {
                android.graphics.Color.parseColor("#8E9BB0")
            }
            binding.ivPlayIcon.setColorFilter(playColor)
            binding.tvDuration.setTextColor(playColor)

            // Load thumbnail image using Coil with animated Shimmer light beam placeholder (NO static images as background)
            val shimmerPlaceholder = com.example.phortart_video_990.core.utils.ShimmerDrawable()
            val imageSource: Any = item.safeImageUrl?.takeIf { it.isNotBlank() }
                ?: (if (item.localImageRes != 0) item.localImageRes else "") ?: ""

            binding.ivTemplateThumb.load(imageSource) {
                crossfade(true)
                placeholder(shimmerPlaceholder)
                error(shimmerPlaceholder)
            }

            binding.llDurationBadge.setOnClickListener {
                if (onPlayClick != null) {
                    onPlayClick.invoke(item, position)
                } else {
                    onItemClick(item)
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
