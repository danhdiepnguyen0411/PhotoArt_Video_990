package com.example.phortart_video_990.ui.screen.feature.memories

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.databinding.ItemViewerThumbnailBinding

class PhotoThumbnailsAdapter(
    private val photos: List<Uri>,
    private val onThumbnailClick: (Int) -> Unit
) : RecyclerView.Adapter<PhotoThumbnailsAdapter.ThumbnailViewHolder>() {

    var selectedIndex = 0

    fun setSelected(index: Int) {
        val oldIndex = selectedIndex
        selectedIndex = index
        notifyItemChanged(oldIndex)
        notifyItemChanged(selectedIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val binding = ItemViewerThumbnailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ThumbnailViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(photos[position], position)
    }

    override fun getItemCount(): Int = photos.size

    inner class ThumbnailViewHolder(
        private val binding: ItemViewerThumbnailBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri, position: Int) {
            val isSelected = position == selectedIndex
            binding.cardThumb.strokeWidth = if (isSelected) 6 else 0
            val strokeColor = ContextCompat.getColor(
                binding.root.context,
                if (isSelected) R.color.primary else android.R.color.transparent
            )
            binding.cardThumb.strokeColor = strokeColor
            binding.cardThumb.alpha = if (isSelected) 1.0f else 0.5f

            binding.ivThumbnail.load(uri) {
                crossfade(true)
            }

            binding.cardThumb.setOnClickListener {
                onThumbnailClick(position)
            }
        }
    }
}
