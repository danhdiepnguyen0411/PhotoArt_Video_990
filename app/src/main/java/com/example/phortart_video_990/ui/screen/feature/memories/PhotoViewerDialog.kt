package com.example.phortart_video_990.ui.screen.feature.memories

import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.viewpager2.widget.ViewPager2
import com.example.phortart_video_990.R
import com.example.phortart_video_990.databinding.DialogPhotoViewerBinding

class PhotoViewerDialog(
    context: Context,
    private val photos: MutableList<Uri>,
    private var initialPosition: Int = 0,
    private val onPhotosChanged: () -> Unit,
    private val onAddMoreRequested: () -> Unit
) : Dialog(context, R.style.DialogFullScreen) {

    private lateinit var binding: DialogPhotoViewerBinding
    private lateinit var pagerAdapter: FullScreenPhotoPagerAdapter
    private lateinit var thumbnailsAdapter: PhotoThumbnailsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        setupViews()
    }

    private fun setupViews() {
        pagerAdapter = FullScreenPhotoPagerAdapter(photos)
        binding.vpPhotoViewer.adapter = pagerAdapter

        thumbnailsAdapter = PhotoThumbnailsAdapter(photos) { clickedPos ->
            binding.vpPhotoViewer.setCurrentItem(clickedPos, true)
        }
        binding.rvThumbnails.adapter = thumbnailsAdapter

        val startPos = initialPosition.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
        binding.vpPhotoViewer.setCurrentItem(startPos, false)
        thumbnailsAdapter.setSelected(startPos)
        updateCounter(startPos)

        binding.vpPhotoViewer.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                thumbnailsAdapter.setSelected(position)
                binding.rvThumbnails.smoothScrollToPosition(position)
                updateCounter(position)
            }
        })

        binding.btnViewerClose.setOnClickListener {
            dismiss()
        }

        binding.btnViewerDelete.setOnClickListener {
            val currentPos = binding.vpPhotoViewer.currentItem
            if (currentPos in 0 until photos.size) {
                photos.removeAt(currentPos)
                onPhotosChanged()
                if (photos.isEmpty()) {
                    dismiss()
                } else {
                    pagerAdapter = FullScreenPhotoPagerAdapter(photos)
                    binding.vpPhotoViewer.adapter = pagerAdapter

                    thumbnailsAdapter = PhotoThumbnailsAdapter(photos) { pos ->
                        binding.vpPhotoViewer.setCurrentItem(pos, true)
                    }
                    binding.rvThumbnails.adapter = thumbnailsAdapter

                    val newPos = currentPos.coerceAtMost(photos.size - 1)
                    binding.vpPhotoViewer.setCurrentItem(newPos, false)
                    thumbnailsAdapter.setSelected(newPos)
                    updateCounter(newPos)
                }
            }
        }

        binding.btnViewerAddMore.setOnClickListener {
            dismiss()
            onAddMoreRequested()
        }
    }

    private fun updateCounter(currentPosition: Int) {
        if (photos.isNotEmpty()) {
            binding.tvViewerCount.text = "${currentPosition + 1} / ${photos.size}"
        } else {
            binding.tvViewerCount.text = "0 / 0"
        }
    }
}
