package com.example.phortart_video_990.ui.screen.feature.memories

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.databinding.ItemSelectedMemoryPhotoBinding

class SelectedPhotosAdapter(
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<SelectedPhotosAdapter.PhotoViewHolder>() {

    private val photos = mutableListOf<Uri>()

    fun setPhotos(list: List<Uri>) {
        photos.clear()
        photos.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemSelectedMemoryPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position], position)
    }

    override fun getItemCount(): Int = photos.size

    inner class PhotoViewHolder(
        private val binding: ItemSelectedMemoryPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri, position: Int) {
            binding.tvImageIndex.text = (position + 1).toString()
            binding.ivSelectedImage.load(uri) {
                crossfade(true)
            }
            binding.btnRemoveImage.setOnClickListener {
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onRemoveClick(currentPos)
                }
            }
        }
    }
}
