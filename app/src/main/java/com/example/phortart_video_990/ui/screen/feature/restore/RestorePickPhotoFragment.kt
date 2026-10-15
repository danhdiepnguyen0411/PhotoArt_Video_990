package com.example.phortart_video_990.ui.screen.feature.restore

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.core.utils.safePopBackStack
import com.example.phortart_video_990.databinding.FragmentRestorePickPhotoBinding
import java.io.File
import java.io.FileOutputStream

class RestorePickPhotoFragment : BaseFragment<FragmentRestorePickPhotoBinding>(FragmentRestorePickPhotoBinding::inflate) {

    companion object {
        private const val SAVED_SELECTED_IMAGE_URI = "saved_restore_image_uri"
    }

    private var selectedImageUri: Uri? = null

    // Native Photo Picker launcher (Android 13+)
    private val pickVisualMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            handleSelectedPhoto(uri)
        }
    }

    // Fallback launcher for devices without Photo Picker
    private val fallbackGetContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleSelectedPhoto(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(SAVED_SELECTED_IMAGE_URI)?.let {
            selectedImageUri = Uri.parse(it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedImageUri?.let {
            outState.putString(SAVED_SELECTED_IMAGE_URI, it.toString())
        }
    }

    override fun initView() {
        binding.header.setTitleRes(R.string.restore_title)

        // Restore image uri if previously chosen
        val stateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        val restoredUriStr = stateHandle?.get<String>(SAVED_SELECTED_IMAGE_URI)
            ?: arguments?.getString(SAVED_SELECTED_IMAGE_URI)
        if (selectedImageUri == null && !restoredUriStr.isNullOrBlank()) {
            selectedImageUri = Uri.parse(restoredUriStr)
        }

        updateUi()
    }

    private fun copyUriToAppCache(context: Context, sourceUri: Uri): Uri {
        return try {
            val scheme = sourceUri.scheme
            if (scheme == "file") {
                val f = File(sourceUri.path ?: "")
                if (f.exists() && f.length() > 0) return sourceUri
            }
            val cacheDir = File(context.cacheDir, "restore_photos").apply { mkdirs() }
            val fileName = "restore_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            val targetFile = File(cacheDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                Uri.fromFile(targetFile)
            } else {
                sourceUri
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sourceUri
        }
    }

    private fun handleSelectedPhoto(uri: Uri) {
        val cachedUri = copyUriToAppCache(requireContext(), uri)
        selectedImageUri = cachedUri
        findNavController().currentBackStackEntry?.savedStateHandle?.set(SAVED_SELECTED_IMAGE_URI, cachedUri.toString())
        updateUi()
    }

    private fun updateUi() {
        val uri = selectedImageUri
        if (uri != null) {
            binding.llEmptyState.visibility = View.GONE
            binding.flSelectedPhotoContainer.visibility = View.VISIBLE
            binding.cardUploadContainer.setBackgroundResource(R.drawable.bg_dashed_photo_box)
            binding.ivSelectedPhoto.load(uri) {
                crossfade(true)
            }
        } else {
            binding.llEmptyState.visibility = View.VISIBLE
            binding.flSelectedPhotoContainer.visibility = View.GONE
            binding.cardUploadContainer.setBackgroundResource(R.drawable.bg_photo_box_empty)
        }
    }

    private fun launchPhotoPicker() {
        try {
            pickVisualMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (e: Exception) {
            try {
                fallbackGetContentLauncher.launch("image/*")
            } catch (ex: Exception) {
                Toast.makeText(requireContext(), getString(R.string.toast_cannot_open_gallery), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun initListener() {
        binding.header.setOnBackClickListener {
            if (isAdded) findNavController().safePopBackStack()
        }

        binding.cardUploadContainer.setOnClickListener {
            launchPhotoPicker()
        }

        binding.btnChangePhoto.setOnClickListener {
            launchPhotoPicker()
        }

        binding.btnStartRestore.setOnClickListener {
            val uri = selectedImageUri
            if (uri != null) {
                findNavController().navigateSafe(
                    R.id.action_restorePickPhotoFragment_to_restoreLoadingFragment,
                    bundleOf(RestoreLoadingFragment.KEY_IMAGE_URI to uri.toString())
                )
            } else {
                // If no photo selected yet, open picker directly
                launchPhotoPicker()
            }
        }
    }
}
