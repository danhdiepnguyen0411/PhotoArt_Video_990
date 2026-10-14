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

    private var playingTrackId: String? = null
    private var loadingTrackId: String? = null

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

    fun setPlayingTrack(trackId: String?, isPlaying: Boolean) {
        val oldId = playingTrackId
        playingTrackId = if (isPlaying) trackId else null
        loadingTrackId = null
        if (!oldId.isNullOrEmpty()) {
            val oldPos = items.indexOfFirst { it.id == oldId }
            if (oldPos != -1) notifyItemChanged(oldPos)
        }
        if (!trackId.isNullOrEmpty()) {
            val newPos = items.indexOfFirst { it.id == trackId }
            if (newPos != -1) notifyItemChanged(newPos)
        }
    }

    fun setLoadingTrack(trackId: String?) {
        val oldId = loadingTrackId
        loadingTrackId = trackId
        if (!oldId.isNullOrEmpty()) {
            val oldPos = items.indexOfFirst { it.id == oldId }
            if (oldPos != -1) notifyItemChanged(oldPos)
        }
        if (!trackId.isNullOrEmpty()) {
            val newPos = items.indexOfFirst { it.id == trackId }
            if (newPos != -1) notifyItemChanged(newPos)
        }
    }

    fun setPlayingState(position: Int, isPlaying: Boolean) {
        val trackId = if (position in items.indices) items[position].id else null
        setPlayingTrack(trackId, isPlaying)
    }

    fun setLoadingState(position: Int) {
        val trackId = if (position in items.indices) items[position].id else null
        setLoadingTrack(trackId)
    }

    inner class TemplateViewHolder(private val binding: ItemTemplateCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateModel, position: Int) {
            binding.tvTemplateTitle.text = item.title
            binding.tvDuration.text = item.displayDuration
            binding.tvPhotoCount.text = item.displayPhotoCount
            binding.tvViews.text = item.displayViews

            val isPlaying = item.id == playingTrackId
            val isLoading = item.id == loadingTrackId

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
