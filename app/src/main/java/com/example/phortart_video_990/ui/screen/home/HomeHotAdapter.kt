package com.example.phortart_video_990.ui.screen.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.utils.ShimmerDrawable
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.databinding.ItemHomeHotCardBinding

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
            binding.tvDuration.text = item.displayDuration
            binding.tvPhotoCount.text = item.displayPhotoCount

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

            val isPlaying = position == playingPosition
            val isLoading = position == loadingPosition

            if (isLoading) {
                binding.pbLoading.visibility = View.VISIBLE
                binding.ivPlayIcon.visibility = View.GONE
            } else {
                binding.pbLoading.visibility = View.GONE
                binding.ivPlayIcon.visibility = View.VISIBLE
                binding.ivPlayIcon.setImageResource(
                    if (isPlaying) R.drawable.ic_pause_small else R.drawable.ic_play_small
                )
            }

            binding.ivPlayIcon.setOnClickListener {
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
