package com.aiart.photo.beauti.editor.ui.screen.feature.memories

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.aiart.photo.beauti.editor.databinding.ItemSelectedPhotoGridBinding

class SelectedPhotosGridAdapter(
    private val photos: MutableList<Uri>,
    private val maxLimit: Int,
    private val onDeleteClick: (position: Int) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<SelectedPhotosGridAdapter.PhotoViewHolder>() {

    val hasAddSlot: Boolean
        get() = photos.size < maxLimit

    override fun getItemCount(): Int {
        return if (hasAddSlot) photos.size + 1 else photos.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemSelectedPhotoGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class PhotoViewHolder(
        private val binding: ItemSelectedPhotoGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(position: Int) {
            if (position < photos.size) {
                // Photo item
                binding.layoutPhotoContent.visibility = View.VISIBLE
                binding.layoutAddSlot.visibility = View.GONE

                val uri = photos[position]
                binding.tvOrderBadge.text = (position + 1).toString()
                binding.ivPhotoThumb.load(uri) {
                    size(240, 240)
                    scale(coil.size.Scale.FILL)
                    crossfade(false)
                }

                binding.btnDeletePhoto.setOnClickListener {
                    val currentPos = bindingAdapterPosition
                    if (currentPos in 0 until photos.size) {
                        onDeleteClick(currentPos)
                    }
                }
            } else {
                // Add more slot
                binding.layoutPhotoContent.visibility = View.GONE
                binding.layoutAddSlot.visibility = View.VISIBLE

                binding.layoutAddSlot.setOnClickListener {
                    onAddClick()
                }
            }
        }
    }
}
