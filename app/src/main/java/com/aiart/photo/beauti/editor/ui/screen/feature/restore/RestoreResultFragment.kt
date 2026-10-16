package com.aiart.photo.beauti.editor.ui.screen.feature.restore

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.base.BaseFragment
import com.aiart.photo.beauti.editor.core.utils.ImageCompressor
import com.aiart.photo.beauti.editor.data.model.HistoryItemModel
import com.aiart.photo.beauti.editor.data.repository.HistoryRepository
import com.aiart.photo.beauti.editor.data.repository.RestoreRepository
import com.aiart.photo.beauti.editor.databinding.FragmentRestoreResultBinding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RestoreResultFragment : BaseFragment<FragmentRestoreResultBinding>(FragmentRestoreResultBinding::inflate) {

    private val restoreRepository by lazy { RestoreRepository(requireContext()) }
    private val historyRepository by lazy { HistoryRepository(requireContext()) }

    private var originalUri: String? = null
    private var restoredUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        originalUri = arguments?.getString(KEY_ORIGINAL_URI)
        restoredUrl = arguments?.getString(KEY_RESTORED_URL)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            binding.btnSaveImage.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarInset + (24 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        // Tự động phát hiện tỉ lệ ảnh (ví dụ 16:9, 4:3, 1:1, 9:16) để khung và ảnh fit hoàn hảo
        val detectedSize = detectImageSize(requireContext(), restoredUrl ?: originalUri)
            ?: detectImageSize(requireContext(), originalUri)
        if (detectedSize != null) {
            adjustCardAspectRatio(detectedSize.first, detectedSize.second)
        }

        // Setup Before and After images
        originalUri?.let { uri ->
            binding.sliderView.setBeforeImage(uri, onSizeReady = { w, h ->
                adjustCardAspectRatio(w, h)
            })
        }
        restoredUrl?.let { url ->
            binding.sliderView.setAfterImage(url, onSizeReady = { w, h ->
                adjustCardAspectRatio(w, h)
            })
        }
    }

    private fun adjustCardAspectRatio(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || !isAdded || bindingOrNull == null) return
        val layout = binding.root as? ConstraintLayout ?: return
        val constraintSet = ConstraintSet()
        constraintSet.clone(layout)
        constraintSet.setDimensionRatio(binding.cardCompareContainer.id, "$width:$height")
        constraintSet.applyTo(layout)
    }

    private fun detectImageSize(context: Context, source: String?): Pair<Int, Int>? {
        if (source.isNullOrBlank()) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            var rotation = 0
            if (source.startsWith("content://")) {
                val uri = Uri.parse(source)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val exif = android.media.ExifInterface(input)
                        val orient = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                        rotation = when (orient) {
                            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                            else -> 0
                        }
                    }
                } catch (_: Exception) {}
            } else {
                val cleanPath = source.removePrefix("file://")
                val file = File(cleanPath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    rotation = ImageCompressor.getExifRotation(file)
                }
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                if (rotation == 90 || rotation == 270) {
                    Pair(options.outHeight, options.outWidth)
                } else {
                    Pair(options.outWidth, options.outHeight)
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    override fun initListener() {
        binding.header.setOnBackClickListener {
            findNavController().popBackStack()
        }

        binding.btnSaveImage.setOnClickListener {
            saveImageToDevice()
        }
    }

    private fun saveImageToDevice() {
        val targetSource = restoredUrl ?: originalUri
        if (targetSource.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.toast_no_image_to_save), Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnSaveImage.isEnabled = false
            val savedUri = restoreRepository.saveImageToGallery(targetSource)
            binding.btnSaveImage.isEnabled = true
            if (savedUri != null) {
                com.aiart.photo.beauti.editor.core.dialog.AppDialogHelper.showSaveSuccessDialog(requireActivity())
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_cannot_read_image), Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val KEY_ORIGINAL_URI = "key_original_uri"
        const val KEY_RESTORED_URL = "key_restored_url"
    }
}
