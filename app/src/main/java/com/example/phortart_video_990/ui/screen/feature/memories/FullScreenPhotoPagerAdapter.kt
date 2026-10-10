package com.example.phortart_video_990.ui.screen.feature.memories

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.databinding.ItemViewerPhotoPageBinding

class FullScreenPhotoPagerAdapter(
    private val photos: List<Uri>
) : RecyclerView.Adapter<FullScreenPhotoPagerAdapter.PhotoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemViewerPhotoPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size

    inner class PhotoViewHolder(
        private val binding: ItemViewerPhotoPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri) {
            binding.ivFullPhoto.load(uri) {
                crossfade(true)
            }
        }
    }
}
