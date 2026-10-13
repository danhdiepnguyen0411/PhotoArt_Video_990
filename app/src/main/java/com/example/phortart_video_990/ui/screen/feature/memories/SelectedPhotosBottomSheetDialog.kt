package com.example.phortart_video_990.ui.screen.feature.memories

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.phortart_video_990.R
import com.example.phortart_video_990.databinding.LayoutBottomSheetSelectedPhotosBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class SelectedPhotosBottomSheetDialog(
    context: Context,
    private val photos: MutableList<Uri>,
    private val maxLimit: Int,
    private val onPhotosChanged: () -> Unit,
    private val onAddMoreRequested: () -> Unit
) : BottomSheetDialog(context, R.style.CustomBottomSheetDialogTheme) {

    private lateinit var binding: LayoutBottomSheetSelectedPhotosBinding
    private lateinit var adapter: SelectedPhotosGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LayoutBottomSheetSelectedPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
    }

    private fun setupViews() {
        updateCountText()

        adapter = SelectedPhotosGridAdapter(
            photos = photos,
            maxLimit = maxLimit,
            onDeleteClick = { position ->
                if (position in 0 until photos.size) {
                    photos.removeAt(position)
                    updateCountText()
                    adapter.notifyDataSetChanged()
                    onPhotosChanged()
                    if (photos.isEmpty()) {
                        dismiss()
                    }
                }
            },
            onAddClick = {
                dismiss()
                onAddMoreRequested()
            }
        )

        binding.rvSelectedPhotos.layoutManager = GridLayoutManager(context, 3)
        binding.rvSelectedPhotos.adapter = adapter

        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }

        binding.btnDoneSheet.setOnClickListener {
            dismiss()
        }
    }

    private fun updateCountText() {
        binding.tvSheetCount.text = context.getString(
            R.string.selected_photos_count_format,
            photos.size,
            maxLimit
        )
    }
}
