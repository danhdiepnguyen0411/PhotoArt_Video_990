package com.example.phortart_video_990.ui.screen.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.databinding.ItemHomeMusicCardBinding

class HomeMusicAdapter(
    private var items: List<TemplateModel>,
    private val onTrackClick: (TemplateModel) -> Unit,
    private val onPlayClick: (TemplateModel, Int) -> Unit
) : RecyclerView.Adapter<HomeMusicAdapter.MusicViewHolder>() {

    private var playingPosition: Int = -1
    private var loadingPosition: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = ItemHomeMusicCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MusicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
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

    inner class MusicViewHolder(private val binding: ItemHomeMusicCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateModel, position: Int) {
            binding.tvMusicTitle.text = item.title

            val cat = item.category.replace("MUSIC_", "").ifBlank { "HOT" }
            binding.tvCategoryTag.text = cat
            binding.tvMusicSubtitle.text = "${item.displayDuration} • Chạm để chọn"

            val coverSource = item.safeImageUrl
            if (!coverSource.isNullOrBlank()) {
                binding.ivMusicCover.load(coverSource) {
                    crossfade(true)
                    placeholder(R.drawable.bg_orange_music_icon)
                    error(R.drawable.bg_orange_music_icon)
                }
            } else {
                binding.ivMusicCover.setImageResource(R.drawable.bg_orange_music_icon)
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

            binding.flPlayButton.setOnClickListener {
                onPlayClick(item, position)
            }

            binding.root.setOnClickListener {
                onTrackClick(item)
            }
        }
    }
}
