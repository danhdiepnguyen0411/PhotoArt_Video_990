package com.example.phortart_video_990.ui.screen.feature.restore

import android.animation.ValueAnimator
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.data.repository.RestoreRepository
import com.example.phortart_video_990.databinding.FragmentRestoreLoadingBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.core.utils.safePopBackStack

class RestoreLoadingFragment : BaseFragment<FragmentRestoreLoadingBinding>(FragmentRestoreLoadingBinding::inflate) {

    private val restoreRepository by lazy { RestoreRepository(requireContext()) }
    private val historyRepository by lazy { HistoryRepository(requireContext()) }
    private var progressAnimator: ValueAnimator? = null
    private var finalAnimator: ValueAnimator? = null
    private var originalUriString: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        originalUriString = arguments?.getString(KEY_IMAGE_URI)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.btnBack.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarInset + (14 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        binding.progressCircle.setProgress(0f)
        binding.tvProgressPercent.text = "0%"

        startProgressSimulation()
        startSdkRestoreProcess()
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            progressAnimator?.cancel()
            finalAnimator?.cancel()
            if (isAdded) {
                findNavController().safePopBackStack()
            }
        }
    }

    private fun startProgressSimulation() {
        progressAnimator?.cancel()
        // Animate progress smoothly towards 92% while waiting for SDK response
        progressAnimator = ValueAnimator.ofFloat(0f, 92f).apply {
            duration = 4500
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                if (isAdded && bindingOrNull != null) {
                    val value = animator.animatedValue as Float
                    bindingOrNull?.progressCircle?.setProgress(value)
                    bindingOrNull?.tvProgressPercent?.text = "${value.toInt()}%"
                }
            }
            start()
        }
    }

    private fun startSdkRestoreProcess() {
        val uriStr = originalUriString
        if (uriStr.isNullOrBlank()) {
            if (isAdded && context != null) {
                Toast.makeText(requireContext(), getString(R.string.toast_image_not_found), Toast.LENGTH_SHORT).show()
            }
            if (isAdded) findNavController().safePopBackStack()
            return
        }

        val uri = Uri.parse(uriStr)

        viewLifecycleOwner.lifecycleScope.launch {
            val file = restoreRepository.createTempFileFromUri(uri)
            if (file == null) {
                if (isAdded && context != null) {
                    Toast.makeText(requireContext(), getString(R.string.toast_cannot_read_image), Toast.LENGTH_SHORT).show()
                }
                if (isAdded) findNavController().safePopBackStack()
                return@launch
            }

            // Call SDK processImageEditing
            val result = restoreRepository.restoreImage(file)
            if (!isAdded || view == null) return@launch

            if (result.isFailure) {
                progressAnimator?.cancel()
                finalAnimator?.cancel()
                val act = activity ?: return@launch
                com.example.phortart_video_990.core.dialog.AppDialogHelper.showRestoreErrorDialog(
                    activity = act,
                    onRetry = {
                        if (isAdded) {
                            startProgressSimulation()
                            startSdkRestoreProcess()
                        }
                    },
                    onCancel = {
                        if (isAdded) {
                            findNavController().safePopBackStack()
                        }
                    }
                )
                return@launch
            }
            val restoreData = result.getOrNull()

            val finalInputUrl = restoreData?.inputUrl?.takeIf { it.isNotBlank() } ?: file.absolutePath
            var finalOutputUrl = restoreData?.outputUrl?.takeIf { it.isNotBlank() } ?: file.absolutePath

            // Preload the restored image and verify that output orientation matches input orientation
            val ctx = context
            if (ctx != null) {
                try {
                    val imageLoader = coil.Coil.imageLoader(ctx)
                    val outRequest = coil.request.ImageRequest.Builder(ctx)
                        .data(finalOutputUrl)
                        .allowHardware(false)
                        .build()
                    val outResult = imageLoader.execute(outRequest)

                    // Kiểm tra tỷ lệ khung hình / hướng ảnh để đảm bảo ảnh kết quả không bao giờ bị lệch xoay 90 độ
                    if (outResult is coil.request.SuccessResult) {
                        val outBitmap = com.example.phortart_video_990.core.utils.MediaSaveUtils.drawableToBitmap(outResult.drawable)

                        val inputBounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath, inputBounds)
                        val isInputPortrait = inputBounds.outHeight > inputBounds.outWidth
                        val isOutputPortrait = outBitmap.height > outBitmap.width

                        if (isInputPortrait != isOutputPortrait && inputBounds.outWidth > 0 && inputBounds.outHeight > 0) {
                            android.util.Log.w("RestoreProcess", "Phát hiện ảnh kết quả lệch hướng xoay so với ảnh gốc! Tự động xoay chuẩn lại.")
                            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
                            val rotatedBmp = android.graphics.Bitmap.createBitmap(outBitmap, 0, 0, outBitmap.width, outBitmap.height, matrix, true)
                            val fixedFile = java.io.File(ctx.cacheDir, "restore_aligned_${System.currentTimeMillis()}.jpg")
                            java.io.FileOutputStream(fixedFile).use { fos ->
                                rotatedBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, fos)
                            }
                            finalOutputUrl = fixedFile.absolutePath
                            if (rotatedBmp != outBitmap) {
                                rotatedBmp.recycle()
                            }
                        }
                    }

                    if (finalInputUrl.startsWith("http://") || finalInputUrl.startsWith("https://")) {
                        val inRequest = coil.request.ImageRequest.Builder(ctx)
                            .data(finalInputUrl)
                            .build()
                        imageLoader.execute(inRequest)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Tự động lưu vào lịch sử ngay khi gen ra thành công
                try {
                    val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
                    val currentDate = dateFormat.format(Date())
                    val historyItem = HistoryItemModel(
                        type = "Khôi phục",
                        title = getString(R.string.func_02_restore),
                        date = currentDate,
                        imageUri = finalOutputUrl,
                        imageUrl = finalOutputUrl,
                        beforeUri = finalInputUrl
                    )
                    historyRepository.addHistoryItem(historyItem)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (!isAdded || bindingOrNull == null) return@launch

            // Finish progress smoothly to 100%
            progressAnimator?.cancel()
            val startProgress = bindingOrNull?.progressCircle?.getProgress() ?: 90f
            finalAnimator = ValueAnimator.ofFloat(startProgress, 100f).apply {
                duration = 350
                addUpdateListener { anim ->
                    if (isAdded && bindingOrNull != null) {
                        val v = anim.animatedValue as Float
                        bindingOrNull?.progressCircle?.setProgress(v)
                        bindingOrNull?.tvProgressPercent?.text = "${v.toInt()}%"
                    }
                }
            }
            finalAnimator?.start()

            delay(400)

            if (!isAdded) return@launch

            findNavController().navigateSafe(
                R.id.action_restoreLoadingFragment_to_restoreResultFragment,
                bundleOf(
                    RestoreResultFragment.KEY_ORIGINAL_URI to finalInputUrl,
                    RestoreResultFragment.KEY_RESTORED_URL to finalOutputUrl
                )
            )
        }
    }

    override fun onDestroyView() {
        progressAnimator?.cancel()
        finalAnimator?.cancel()
        super.onDestroyView()
    }

    companion object {
        const val KEY_IMAGE_URI = "key_image_uri"
    }
}
