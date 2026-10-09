package com.example.phortart_video_990.ui.screen.feature.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.databinding.ItemMusicTrackBinding

class MusicAdapter(
    private var tracks: List<TemplateModel>,
    private val onPreviewClick: (TemplateModel, Int) -> Unit,
    private val onSelectClick: (TemplateModel) -> Unit
) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    private var currentlyPlayingIndex: Int = -1
    private var isPlaying: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = ItemMusicTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MusicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val isThisPlaying = (position == currentlyPlayingIndex && isPlaying)
        holder.bind(tracks[position], isThisPlaying)
    }

    override fun getItemCount(): Int = tracks.size

    fun submitList(newList: List<TemplateModel>) {
        tracks = newList
        currentlyPlayingIndex = -1
        isPlaying = false
        notifyDataSetChanged()
    }

    fun setPlayingState(index: Int, playing: Boolean) {
        val prevIndex = currentlyPlayingIndex
        currentlyPlayingIndex = index
        isPlaying = playing

        if (prevIndex != -1 && prevIndex != index) {
            notifyItemChanged(prevIndex)
        }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    inner class MusicViewHolder(private val binding: ItemMusicTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(track: TemplateModel, isCurrentPlaying: Boolean) {
            // Enable marquee text scrolling
            binding.tvTrackTitle.text = track.title
            binding.tvTrackTitle.isSelected = true

            // Category tag (strip MUSIC_ prefix)
            val cleanCategory = if (track.category.startsWith("MUSIC_", ignoreCase = true)) {
                track.category.substring(6)
            } else {
                track.category.ifBlank { "Nhạc AI" }
            }
            binding.tvTrackCategory.text = cleanCategory

            binding.tvTrackDuration.text = track.duration.ifBlank { "0:30" }

            // Load Cover Artwork
            if (!track.safeImageUrl.isNullOrBlank()) {
                binding.ivTrackCover.load(track.safeImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_func_music)
                    error(R.drawable.ic_func_music)
                }
            } else {
                binding.ivTrackCover.setImageResource(R.drawable.ic_func_music)
            }

            // Play/Pause icon
            if (isCurrentPlaying) {
                binding.ivPlayIcon.setImageResource(R.drawable.ic_pause_small)
                binding.btnPlayPreview.setBackgroundResource(R.drawable.bg_category_chip_selected)
                binding.ivPlayIcon.setColorFilter(binding.root.context.getColor(R.color.white))
            } else {
                binding.ivPlayIcon.setImageResource(R.drawable.ic_play_small)
                binding.btnPlayPreview.setBackgroundResource(R.drawable.bg_category_chip_unselected)
                binding.ivPlayIcon.setColorFilter(binding.root.context.getColor(R.color.navy))
            }

            // Preview Play button
            binding.btnPlayPreview.setOnClickListener {
                onPreviewClick(track, bindingAdapterPosition)
            }

            // Select button
            binding.btnSelectTrack.setOnClickListener {
                onSelectClick(track)
            }

            // Whole item click to select
            binding.root.setOnClickListener {
                onSelectClick(track)
            }
        }
    }
}
