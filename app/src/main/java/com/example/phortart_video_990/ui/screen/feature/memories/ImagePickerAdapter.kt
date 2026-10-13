package com.example.phortart_video_990.ui.screen.feature.memories

import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.databinding.ItemPickerPhotoBinding

class ImagePickerAdapter(
    private val allPhotos: List<Uri>,
    val selectedPhotos: MutableList<Uri>,
    private val maxLimit: Int,
    private val onSelectionChanged: (List<Uri>) -> Unit,
    private val onMaxLimitReached: () -> Unit
) : RecyclerView.Adapter<ImagePickerAdapter.PickerViewHolder>() {

    override fun getItemCount(): Int = allPhotos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickerViewHolder {
        val binding = ItemPickerPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PickerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PickerViewHolder, position: Int) {
        holder.bind(allPhotos[position])
    }

    inner class PickerViewHolder(
        private val binding: ItemPickerPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri) {
            val context = binding.root.context
            val density = context.resources.displayMetrics.density

            // 1. Load image thumbnail with Coil
            binding.ivPickerThumb.load(uri) {
                size(240, 240)
                scale(coil.size.Scale.FILL)
                crossfade(false)
            }

            val selectedIndex = selectedPhotos.indexOf(uri)
            val isSelected = selectedIndex >= 0
            val isMaxReached = selectedPhotos.size >= maxLimit

            // 2. Display selection order or unselected circle
            if (isSelected) {
                binding.tvPickerOrderBadge.background = ContextCompat.getDrawable(context, R.drawable.bg_photo_order_badge)
                binding.tvPickerOrderBadge.text = (selectedIndex + 1).toString()
                binding.cardPickerBorder.strokeColor = Color.parseColor("#2563EB")
                binding.cardPickerBorder.strokeWidth = (2.5f * density).toInt()
            } else {
                binding.tvPickerOrderBadge.background = ContextCompat.getDrawable(context, R.drawable.bg_picker_unselected_circle)
                binding.tvPickerOrderBadge.text = ""
                binding.cardPickerBorder.strokeColor = Color.TRANSPARENT
                binding.cardPickerBorder.strokeWidth = 0
            }

            // 3. Dim unselected photos when max limit is reached
            if (isMaxReached && !isSelected) {
                binding.viewDimOverlay.visibility = View.VISIBLE
                binding.viewDimOverlay.alpha = 0.65f
                binding.cardPickerContainer.alpha = 0.38f
            } else {
                binding.viewDimOverlay.visibility = View.GONE
                binding.cardPickerContainer.alpha = 1.0f
            }

            // 4. Click listener to toggle selection
            binding.cardPickerContainer.setOnClickListener {
                val currentIdx = selectedPhotos.indexOf(uri)
                if (currentIdx >= 0) {
                    // Deselect photo
                    selectedPhotos.removeAt(currentIdx)
                    notifyDataSetChanged()
                    onSelectionChanged(selectedPhotos)
                } else {
                    // Try to select photo
                    if (selectedPhotos.size < maxLimit) {
                        selectedPhotos.add(uri)
                        notifyDataSetChanged()
                        onSelectionChanged(selectedPhotos)
                    } else {
                        onMaxLimitReached()
                    }
                }
            }
        }
    }
}
