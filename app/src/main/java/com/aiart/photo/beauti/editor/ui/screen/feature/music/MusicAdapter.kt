package com.aiart.photo.beauti.editor.ui.screen.feature.music

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.data.model.TemplateModel
import com.aiart.photo.beauti.editor.databinding.ItemMusicTrackBinding

class MusicAdapter(
    private var tracks: List<TemplateModel>,
    private var initialSelectedTrackId: String? = null,
    private val onTrackSelected: (TemplateModel, Int) -> Unit,
    private val onPreviewClick: (TemplateModel, Int) -> Unit
) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    private var selectedTrackId: String? = initialSelectedTrackId
    private var currentlyPlayingIndex: Int = -1
    private var isPlaying: Boolean = false
    private var currentlyLoadingPreviewIndex: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = ItemMusicTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MusicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val track = tracks[position]
        val isSelected = track.id == selectedTrackId
        val isThisPlaying = (position == currentlyPlayingIndex && isPlaying)
        val isThisLoadingPreview = (position == currentlyLoadingPreviewIndex)
        holder.bind(track, isSelected, isThisPlaying, isThisLoadingPreview)
    }

    override fun getItemCount(): Int = tracks.size

    fun submitList(newList: List<TemplateModel>) {
        tracks = newList
        currentlyLoadingPreviewIndex = -1

        // Default to first track ONLY if no selection has ever been made
        if (selectedTrackId == null && newList.isNotEmpty()) {
            selectedTrackId = newList[0].id
        }
        notifyDataSetChanged()
    }

    fun setSelectedTrackId(id: String?) {
        if (selectedTrackId == id) return
        val oldIndex = tracks.indexOfFirst { it.id == selectedTrackId }
        selectedTrackId = id
        val newIndex = tracks.indexOfFirst { it.id == selectedTrackId }

        if (oldIndex != -1) notifyItemChanged(oldIndex)
        if (newIndex != -1 && newIndex != oldIndex) notifyItemChanged(newIndex)
    }

    fun getSelectedTrackId(): String? = selectedTrackId

    fun getTrackPosition(id: String?): Int {
        if (id == null) return -1
        return tracks.indexOfFirst { it.id == id }
    }

    fun getSelectedTrack(): TemplateModel? {
        return tracks.find { it.id == selectedTrackId }
            ?: (if (selectedTrackId == null) tracks.firstOrNull() else null)
    }

    fun setPreviewLoading(index: Int) {
        val prevPreview = currentlyLoadingPreviewIndex
        currentlyLoadingPreviewIndex = index
        if (prevPreview != -1 && prevPreview != index) notifyItemChanged(prevPreview)
        if (index != -1) notifyItemChanged(index)
    }

    fun clearAllLoading() {
        val prevPreview = currentlyLoadingPreviewIndex
        currentlyLoadingPreviewIndex = -1
        if (prevPreview != -1) notifyItemChanged(prevPreview)
    }

    fun setPlayingState(index: Int, playing: Boolean) {
        val prevIndex = currentlyPlayingIndex
        currentlyPlayingIndex = index
        isPlaying = playing
        currentlyLoadingPreviewIndex = -1

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
            isSelected: Boolean,
            isCurrentPlaying: Boolean,
            isLoadingPreview: Boolean
        ) {
            val context = binding.root.context

            // Title
            binding.tvTrackTitle.text = track.title
            binding.tvTrackTitle.isSelected = true

            // Category name is hidden on items per request
            binding.tvTrackArtist.visibility = View.GONE

            // Duration (hidden per request)
            binding.tvTrackDuration.visibility = View.GONE

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

            // Card background & border matching selection
            if (isSelected) {
                binding.cardTrack.setBackgroundResource(R.drawable.bg_item_language_selected)
                binding.cardTrack.strokeColor = Color.parseColor("#4D9678EB")
                binding.cardTrack.setCardBackgroundColor(Color.TRANSPARENT)
            } else {
                binding.cardTrack.setBackgroundResource(0)
                binding.cardTrack.setCardBackgroundColor(Color.WHITE)
                binding.cardTrack.strokeColor = Color.parseColor("#E8EEF8")
            }

            // Right Action Button (Checkmark vs Play/Pause)
            if (isLoadingPreview) {
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
                binding.ivPlayIcon.setImageResource(R.drawable.ic_play_small)
            }

            // Clicking action circle (checkmark or play)
            binding.flActionContainer.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    if (isSelected) {
                        onPreviewClick(track, pos)
                    } else {
                        setSelectedTrackId(track.id)
                        onTrackSelected(track, pos)
                    }
                }
            }

            // Clicking whole card selects track
            binding.cardTrack.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    setSelectedTrackId(track.id)
                    onTrackSelected(track, pos)
                }
            }
        }
    }
}
