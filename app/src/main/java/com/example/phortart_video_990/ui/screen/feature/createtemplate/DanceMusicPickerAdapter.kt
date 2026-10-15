package com.example.phortart_video_990.ui.screen.feature.createtemplate

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.databinding.ItemDanceMusicPickerBinding

class DanceMusicPickerAdapter(
    private var tracks: List<TemplateModel>,
    private var selectedTrackId: String? = null,
    private val onTrackSelected: (TemplateModel?) -> Unit,
    private val onPreviewClick: (TemplateModel) -> Unit
) : RecyclerView.Adapter<DanceMusicPickerAdapter.ViewHolder>() {

    private var playingTrackId: String? = null
    private var loadingTrackId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDanceMusicPickerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = tracks[position]
        val isSelected = track.id == selectedTrackId
        val isPlaying = track.id == playingTrackId
        val isLoading = track.id == loadingTrackId
        holder.bind(track, isSelected, isPlaying, isLoading)
    }

    override fun getItemCount(): Int = tracks.size

    fun submitList(newList: List<TemplateModel>) {
        tracks = newList
        notifyDataSetChanged()
    }

    fun setSelectedTrackId(id: String?) {
        if (selectedTrackId == id) return
        val oldIndex = tracks.indexOfFirst { it.id == selectedTrackId }
        selectedTrackId = id
        val newIndex = tracks.indexOfFirst { it.id == selectedTrackId }

        if (oldIndex != -1) notifyItemChanged(oldIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }

    fun getSelectedTrackId(): String? = selectedTrackId

    fun getSelectedTrack(): TemplateModel? {
        val curId = selectedTrackId ?: return null
        return tracks.find { it.id == curId }
    }

    fun setPlayingState(trackId: String?, isPlaying: Boolean) {
        val oldTrackId = playingTrackId
        playingTrackId = if (isPlaying) trackId else null
        loadingTrackId = null

        if (!oldTrackId.isNullOrEmpty()) {
            val oldPos = tracks.indexOfFirst { it.id == oldTrackId }
            if (oldPos != -1) notifyItemChanged(oldPos)
        }
        if (!trackId.isNullOrEmpty()) {
            val newPos = tracks.indexOfFirst { it.id == trackId }
            if (newPos != -1) notifyItemChanged(newPos)
        }
    }

    fun setLoadingState(trackId: String?) {
        val oldLoading = loadingTrackId
        loadingTrackId = trackId

        if (!oldLoading.isNullOrEmpty()) {
            val oldPos = tracks.indexOfFirst { it.id == oldLoading }
            if (oldPos != -1) notifyItemChanged(oldPos)
        }
        if (!trackId.isNullOrEmpty()) {
            val newPos = tracks.indexOfFirst { it.id == trackId }
            if (newPos != -1) notifyItemChanged(newPos)
        }
    }

    inner class ViewHolder(private val binding: ItemDanceMusicPickerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            track: TemplateModel,
            isSelected: Boolean,
            isPlaying: Boolean,
            isLoading: Boolean
        ) {
            // Track title
            binding.tvTrackTitle.text = track.title
            binding.tvTrackTitle.isSelected = true
            binding.tvTrackDuration.visibility = View.GONE

            // Cover Image
            if (!track.safeImageUrl.isNullOrBlank()) {
                binding.ivTrackCover.load(track.safeImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_func_music)
                    error(R.drawable.ic_func_music)
                }
            } else {
                binding.ivTrackCover.setImageResource(R.drawable.ic_func_music)
            }

            // Card background & stroke (identical to Select Music screen)
            if (isSelected) {
                binding.cardTrack.setBackgroundResource(R.drawable.bg_item_language_selected)
                binding.cardTrack.strokeColor = Color.parseColor("#4D9678EB")
                binding.cardTrack.setCardBackgroundColor(Color.TRANSPARENT)
            } else {
                binding.cardTrack.setBackgroundResource(0)
                binding.cardTrack.setCardBackgroundColor(Color.WHITE)
                binding.cardTrack.strokeColor = Color.parseColor("#E8EEF8")
            }

            // Waveform Visualizer: visible & oscillating when audio is playing!
            if (isPlaying) {
                binding.waveView.visibility = View.VISIBLE
                binding.waveView.setPlaying(true)
            } else {
                binding.waveView.setPlaying(false)
                binding.waveView.visibility = View.GONE
            }

            // Right Action Button (Checkmark when selected vs Play/Pause when unselected)
            if (isLoading) {
                binding.pbLoading.visibility = View.VISIBLE
                binding.ivPlayIcon.visibility = View.GONE
                binding.ivCheckIcon.visibility = View.GONE
                binding.flActionContainer.setBackgroundResource(R.drawable.bg_track_play_circle)
            } else if (isSelected) {
                binding.pbLoading.visibility = View.GONE
                binding.ivPlayIcon.visibility = View.GONE
                binding.ivCheckIcon.visibility = View.VISIBLE
                binding.flActionContainer.setBackgroundResource(R.drawable.bg_track_check_circle)
            } else {
                binding.pbLoading.visibility = View.GONE
                binding.ivCheckIcon.visibility = View.GONE
                binding.ivPlayIcon.visibility = View.VISIBLE
                binding.ivPlayIcon.setColorFilter(Color.parseColor("#6A4FE0"))
                binding.flActionContainer.setBackgroundResource(R.drawable.bg_track_play_circle)
                binding.ivPlayIcon.setImageResource(
                    if (isPlaying) R.drawable.ic_pause_small else R.drawable.ic_play_small
                )
            }

            // Click on right action container (checkmark or play button)
            binding.flActionContainer.setOnClickListener {
                if (isSelected) {
                    // Tapping selected track's checkmark deselects the track -> Reverts to template original audio
                    setSelectedTrackId(null)
                    onTrackSelected(null)
                } else {
                    // Tapping play on unselected track selects it and starts playing
                    setSelectedTrackId(track.id)
                    onTrackSelected(track)
                    onPreviewClick(track)
                }
            }

            // Click anywhere on card selects track or toggles preview if already selected
            binding.cardTrack.setOnClickListener {
                if (isSelected) {
                    onPreviewClick(track)
                } else {
                    setSelectedTrackId(track.id)
                    onTrackSelected(track)
                    onPreviewClick(track)
                }
            }
        }
    }
}
