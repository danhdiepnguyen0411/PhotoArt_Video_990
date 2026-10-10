package com.example.phortart_video_990.ui.screen.feature.memories

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.databinding.ItemResultPhotoStripBinding

class ResultPhotoStripAdapter(
    private var photos: List<String>,
    private var selectedIndex: Int = 0,
    private val onPhotoClick: (Int) -> Unit
) : RecyclerView.Adapter<ResultPhotoStripAdapter.PhotoViewHolder>() {

    fun updateData(newPhotos: List<String>, newSelectedIndex: Int = 0) {
        photos = newPhotos
        selectedIndex = newSelectedIndex
        notifyDataSetChanged()
    }

    fun setSelectedIndex(index: Int) {
        if (index != selectedIndex && index in photos.indices) {
            val old = selectedIndex
            selectedIndex = index
            notifyItemChanged(old)
            notifyItemChanged(selectedIndex)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemResultPhotoStripBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position], position)
    }

    override fun getItemCount(): Int = photos.size

    inner class PhotoViewHolder(private val binding: ItemResultPhotoStripBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(photoUriString: String, position: Int) {
            binding.tvOrderNumber.text = "${position + 1}"

            // Highlight border if selected
            if (position == selectedIndex) {
                binding.cardPhotoItem.strokeColor = 0xFF4A65ED.toInt()
                binding.cardPhotoItem.strokeWidth = (2.2 * itemView.resources.displayMetrics.density).toInt()
            } else {
                binding.cardPhotoItem.strokeColor = 0x00000000
                binding.cardPhotoItem.strokeWidth = 0
            }

            // Hide transition divider on the last item
            binding.ivTransitionIcon.visibility = if (position < photos.lastIndex) View.VISIBLE else View.GONE

            binding.ivPhotoThumbnail.load(Uri.parse(photoUriString)) {
                crossfade(true)
                placeholder(R.drawable.bg_card_white)
            }

            binding.cardPhotoItem.setOnClickListener {
                setSelectedIndex(position)
                onPhotoClick(position)
            }
        }
    }
}
