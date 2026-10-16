package com.aiart.photo.beauti.editor.ui.screen.feature.memories

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.data.model.TemplateModel
import com.aiart.photo.beauti.editor.databinding.ItemMemoriesMusicBinding

class MemoriesMusicAdapter(
    private var tracks: List<TemplateModel>,
    private var selectedTrackId: String? = null,
    private val onTrackSelected: (TemplateModel) -> Unit,
    private val onPlayToggle: (TemplateModel) -> Unit
) : RecyclerView.Adapter<MemoriesMusicAdapter.ViewHolder>() {

    private var playingTrackId: String? = null
    private var loadingTrackId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMemoriesMusicBinding.inflate(
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
        tracks = ArrayList(newList)
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

    inner class ViewHolder(private val binding: ItemMemoriesMusicBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            track: TemplateModel,
            isSelected: Boolean,
            isPlaying: Boolean,
            isLoading: Boolean
        ) {
            binding.tvTitle.text = track.title

            if (!track.safeImageUrl.isNullOrBlank()) {
                binding.ivCover.load(track.safeImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_func_music)
                    error(R.drawable.ic_func_music)
                }
            } else {
                binding.ivCover.setImageResource(R.drawable.ic_func_music)
            }

            val purpleColor = Color.parseColor("#6F64EE")
            val whiteColor = Color.WHITE

            if (isSelected) {
                binding.rootContainer.setBackgroundResource(R.drawable.bg_item_memories_music_selected)
                binding.flPlayAction.setBackgroundResource(R.drawable.bg_memories_play_circle_selected)
                binding.ivPlayIcon.setColorFilter(whiteColor)
                binding.pbLoading.indeterminateTintList = ColorStateList.valueOf(whiteColor)
            } else {
                binding.rootContainer.setBackgroundResource(R.drawable.bg_item_memories_music_unselected)
                binding.flPlayAction.setBackgroundResource(R.drawable.bg_memories_play_circle_unselected)
                binding.ivPlayIcon.setColorFilter(purpleColor)
                binding.pbLoading.indeterminateTintList = ColorStateList.valueOf(purpleColor)
            }

            if (isLoading) {
                binding.pbLoading.visibility = View.VISIBLE
                binding.ivPlayIcon.visibility = View.GONE
            } else {
                binding.pbLoading.visibility = View.GONE
                binding.ivPlayIcon.visibility = View.VISIBLE
                if (isPlaying) {
                    binding.ivPlayIcon.setImageResource(R.drawable.ic_pause_small)
                } else {
                    binding.ivPlayIcon.setImageResource(R.drawable.ic_play_small)
                }
            }

            // Click row to select
            binding.root.setOnClickListener {
                onTrackSelected(track)
            }

            // Click play button to toggle preview
            binding.flPlayAction.setOnClickListener {
                onPlayToggle(track)
            }
        }
    }
}
