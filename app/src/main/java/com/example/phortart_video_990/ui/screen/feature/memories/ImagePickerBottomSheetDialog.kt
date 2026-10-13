package com.example.phortart_video_990.ui.screen.feature.memories

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import com.example.phortart_video_990.R
import com.example.phortart_video_990.databinding.LayoutBottomSheetImagePickerBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImagePickerBottomSheetDialog(
    context: Context,
    private val initialSelectedPhotos: List<Uri>,
    private val maxLimit: Int,
    private val onPhotosSelected: (List<Uri>) -> Unit
) : BottomSheetDialog(context, R.style.CustomBottomSheetDialogTheme) {

    private lateinit var binding: LayoutBottomSheetImagePickerBinding
    private var adapter: ImagePickerAdapter? = null
    private val currentSelected = initialSelectedPhotos.toMutableList()
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LayoutBottomSheetImagePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomSheetBehavior()
        setupViews()
        loadGalleryPhotos()
    }

    private fun setupBottomSheetBehavior() {
        val bottomSheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let { sheet ->
            val behavior = BottomSheetBehavior.from(sheet)
            val screenHeight = context.resources.displayMetrics.heightPixels
            sheet.layoutParams.height = (screenHeight * 0.85).toInt()
            behavior.peekHeight = (screenHeight * 0.85).toInt()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    private fun setupViews() {
        updateSelectionStatus()

        binding.btnClosePicker.setOnClickListener {
            dismiss()
        }

        binding.btnConfirmPicker.setOnClickListener {
            val selected = adapter?.selectedPhotos ?: currentSelected
            if (selected.isNotEmpty()) {
                onPhotosSelected(selected)
                dismiss()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_select_at_least_1_photo),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateSelectionStatus() {
        val count = currentSelected.size
        binding.tvPickerCount.text = context.getString(
            R.string.selected_photos_count_format,
            count,
            maxLimit
        )

        if (count > 0) {
            binding.btnConfirmPicker.isEnabled = true
            binding.btnConfirmPicker.alpha = 1.0f
            binding.btnConfirmPicker.text = context.getString(
                R.string.btn_picker_confirm,
                count,
                maxLimit
            )
        } else {
            binding.btnConfirmPicker.isEnabled = false
            binding.btnConfirmPicker.alpha = 0.5f
            binding.btnConfirmPicker.text = context.getString(R.string.btn_confirm)
        }
    }

    private fun loadGalleryPhotos() {
        binding.pbLoadingPhotos.visibility = View.VISIBLE
        binding.tvEmptyPhotos.visibility = View.GONE

        loadJob?.cancel()
        loadJob = CoroutineScope(Dispatchers.Main).launch {
            val photos = queryDevicePhotos()
            if (!isShowing) return@launch

            binding.pbLoadingPhotos.visibility = View.GONE

            if (photos.isEmpty()) {
                binding.tvEmptyPhotos.visibility = View.VISIBLE
            } else {
                binding.tvEmptyPhotos.visibility = View.GONE
                adapter = ImagePickerAdapter(
                    allPhotos = photos,
                    selectedPhotos = currentSelected,
                    maxLimit = maxLimit,
                    onSelectionChanged = {
                        updateSelectionStatus()
                    },
                    onMaxLimitReached = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_max_photos_limit, maxLimit),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
                binding.rvPickerPhotos.apply {
                    setHasFixedSize(true)
                    setItemViewCacheSize(20)
                    layoutManager = GridLayoutManager(context, 3)
                    adapter = this@ImagePickerBottomSheetDialog.adapter
                }
            }
        }
    }

    private suspend fun queryDevicePhotos(): List<Uri> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Uri>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val maxFetchCount = 350

        try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val queryArgs = Bundle().apply {
                    putInt(ContentResolver.QUERY_ARG_LIMIT, maxFetchCount)
                    putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(MediaStore.Images.Media.DATE_ADDED)
                    )
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                }
                context.contentResolver.query(queryUri, projection, queryArgs, null)
            } else {
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $maxFetchCount"
                context.contentResolver.query(
                    queryUri,
                    projection,
                    null,
                    null,
                    sortOrder
                )
            }

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext() && result.size < maxFetchCount) {
                    val id = c.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(queryUri, id)
                    result.add(contentUri)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    override fun onStop() {
        loadJob?.cancel()
        loadJob = null
        super.onStop()
    }
}
