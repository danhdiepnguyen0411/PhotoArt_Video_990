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
    private val onSelectClick: (TemplateModel, Int) -> Unit
) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    private var currentlyPlayingIndex: Int = -1
    private var isPlaying: Boolean = false

    private var currentlyLoadingPreviewIndex: Int = -1
    private var currentlyLoadingSelectIndex: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = ItemMusicTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MusicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val isThisPlaying = (position == currentlyPlayingIndex && isPlaying)
        val isThisLoadingPreview = (position == currentlyLoadingPreviewIndex)
        val isThisLoadingSelect = (position == currentlyLoadingSelectIndex)
        holder.bind(tracks[position], isThisPlaying, isThisLoadingPreview, isThisLoadingSelect)
    }

    override fun getItemCount(): Int = tracks.size

    fun submitList(newList: List<TemplateModel>) {
        tracks = newList
        currentlyPlayingIndex = -1
        isPlaying = false
        currentlyLoadingPreviewIndex = -1
        currentlyLoadingSelectIndex = -1
        notifyDataSetChanged()
    }

    fun setPreviewLoading(index: Int) {
        val prevPreview = currentlyLoadingPreviewIndex
        val prevSelect = currentlyLoadingSelectIndex
        currentlyLoadingPreviewIndex = index
        currentlyLoadingSelectIndex = -1 // Chỉ cho phép 1 loading duy nhất trên toàn màn hình

        if (prevSelect != -1 && prevSelect != index) notifyItemChanged(prevSelect)
        if (prevPreview != -1 && prevPreview != index) notifyItemChanged(prevPreview)
        if (index != -1) notifyItemChanged(index)
    }

    fun setSelectLoading(index: Int) {
        val prevPreview = currentlyLoadingPreviewIndex
        val prevSelect = currentlyLoadingSelectIndex
        currentlyLoadingSelectIndex = index
        currentlyLoadingPreviewIndex = -1 // Chỉ cho phép 1 loading duy nhất trên toàn màn hình

        if (prevPreview != -1 && prevPreview != index) notifyItemChanged(prevPreview)
        if (prevSelect != -1 && prevSelect != index) notifyItemChanged(prevSelect)
        if (index != -1) notifyItemChanged(index)
    }

    fun clearAllLoading() {
        val prevPreview = currentlyLoadingPreviewIndex
        val prevSelect = currentlyLoadingSelectIndex
        currentlyLoadingPreviewIndex = -1
        currentlyLoadingSelectIndex = -1

        if (prevPreview != -1) notifyItemChanged(prevPreview)
        if (prevSelect != -1 && prevSelect != prevPreview) notifyItemChanged(prevSelect)
    }

    fun setPlayingState(index: Int, playing: Boolean) {
        val prevIndex = currentlyPlayingIndex
        currentlyPlayingIndex = index
        isPlaying = playing
        currentlyLoadingPreviewIndex = -1 // Reset loading khi đã chuyển sang playing

        if (prevIndex != -1 && prevIndex != index) {
            notifyItemChanged(prevIndex)
        }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    inner class MusicViewHolder(private val binding: ItemMusicTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            track: TemplateModel,
            isCurrentPlaying: Boolean,
            isLoadingPreview: Boolean,
            isLoadingSelect: Boolean
        ) {
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

            // Preview button UI (Play / Pause / Loading)
            if (isLoadingPreview) {
                binding.ivPlayIcon.visibility = android.view.View.GONE
                binding.pbPreviewLoading.visibility = android.view.View.VISIBLE
                binding.btnPlayPreview.setBackgroundResource(R.drawable.bg_category_chip_unselected)
            } else {
                binding.pbPreviewLoading.visibility = android.view.View.GONE
                binding.ivPlayIcon.visibility = android.view.View.VISIBLE
                if (isCurrentPlaying) {
                    binding.ivPlayIcon.setImageResource(R.drawable.ic_pause_small)
                    binding.btnPlayPreview.setBackgroundResource(R.drawable.bg_category_chip_selected)
                    binding.ivPlayIcon.setColorFilter(binding.root.context.getColor(R.color.white))
                } else {
                    binding.ivPlayIcon.setImageResource(R.drawable.ic_play_small)
                    binding.btnPlayPreview.setBackgroundResource(R.drawable.bg_category_chip_unselected)
                    binding.ivPlayIcon.setColorFilter(binding.root.context.getColor(R.color.navy))
                }
            }

            // Select button UI (Chọn / Loading)
            if (isLoadingSelect) {
                binding.btnSelectTrack.visibility = android.view.View.INVISIBLE
                binding.pbSelectLoading.visibility = android.view.View.VISIBLE
                binding.btnSelectContainer.isClickable = false
            } else {
                binding.btnSelectTrack.visibility = android.view.View.VISIBLE
                binding.pbSelectLoading.visibility = android.view.View.GONE
                binding.btnSelectContainer.isClickable = true
            }

            // Preview Play button
            binding.btnPlayPreview.setOnClickListener {
                if (!isLoadingPreview) {
                    onPreviewClick(track, bindingAdapterPosition)
                }
            }

            // Select button
            binding.btnSelectContainer.setOnClickListener {
                if (!isLoadingSelect) {
                    onSelectClick(track, bindingAdapterPosition)
                }
            }

            // Whole item click to select
            binding.root.setOnClickListener {
                if (!isLoadingSelect) {
                    onSelectClick(track, bindingAdapterPosition)
                }
            }
        }
    }
}
